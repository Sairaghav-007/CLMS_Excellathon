import { v4 as uuidv4 } from "uuid";

// Fetch configurations from import.meta.env
const region = import.meta.env.VITE_AWS_REGION || "";
const accessKeyId = import.meta.env.VITE_AWS_ACCESS_KEY_ID || "";
const secretAccessKey = import.meta.env.VITE_AWS_SECRET_ACCESS_KEY || "";
const bucketName = import.meta.env.VITE_AWS_S3_BUCKET_NAME || "";
const cloudfrontDomain = import.meta.env.VITE_CLOUDFRONT_URL || "";

// Check if all S3 credentials are configured
export const isS3Configured = (): boolean => {
  return !!(region && accessKeyId && secretAccessKey && bucketName);
};

/**
 * Uploads a file to the backend local server storage (/api/hr/upload).
 */
export const uploadToBackend = async (
  file: File,
  onProgress?: (percentage: number) => void
): Promise<string> => {
  const formData = new FormData();
  formData.append("file", file);

  const { api } = await import("../../api/client");
  
  const response = await api.post("/hr/upload", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
    onUploadProgress: (progressEvent) => {
      if (progressEvent.total) {
        const percentage = Math.round((progressEvent.loaded / progressEvent.total) * 100);
        onProgress?.(percentage);
      }
    },
  });

  console.log("[LOCAL UPLOAD] Upload complete. Response URL:", response.data.url);
  return response.data.url;
};

/**
 * Uploads a file to Amazon S3. 
 * Automatically falls back to local backend API upload if S3 credentials are not configured
 * or if S3 upload fails due to AWS authentication / network / CORS issues.
 * 
 * @param file The file to upload (Video, PDF, or PPT)
 * @param onProgress Callback function to track upload progress percentage (0 - 100)
 * @returns A promise resolving to the URL of the uploaded file
 */
export const uploadFileToS3 = async (
  file: File,
  onProgress?: (percentage: number) => void
): Promise<string> => {
  const useLocal = import.meta.env.VITE_USE_LOCAL_STORAGE === "true";

  // If local storage is explicitly requested or S3 is not configured, upload to backend directly
  if (useLocal || !isS3Configured()) {
    console.log("[UPLOAD] Using local backend server storage API...");
    return await uploadToBackend(file, onProgress);
  }

  // Attempt AWS S3 upload with automatic fallback to local backend on failure
  try {
    const fileExtension = file.name.split(".").pop();
    const uniqueKey = `${Date.now()}-${uuidv4()}.${fileExtension}`;

    const { S3Client } = await import("@aws-sdk/client-s3");
    const { Upload } = await import("@aws-sdk/lib-storage");

    // Create S3 client
    const s3Client = new S3Client({
      region,
      credentials: {
        accessKeyId,
        secretAccessKey,
      },
      requestChecksumCalculation: "WHEN_REQUIRED",
    });

    const upload = new Upload({
      client: s3Client,
      params: {
        Bucket: bucketName,
        Key: uniqueKey,
        Body: file,
        ContentType: file.type || "application/octet-stream",
      },
    });

    upload.on("httpUploadProgress", (progress) => {
      if (progress.loaded !== undefined && progress.total !== undefined) {
        const percentage = Math.round((progress.loaded / progress.total) * 100);
        onProgress?.(percentage);
      }
    });

    await upload.done();
    const cleanCFUrl = cloudfrontDomain.replace(/^(https?:\/\/)?/, "");
    const uploadedUrl = `https://${cleanCFUrl}/${uniqueKey}`;
    console.log(`[S3 LIVE] Upload complete. Key: ${uniqueKey}. CloudFront URL: ${uploadedUrl}`);
    return uploadedUrl;
  } catch (error: any) {
    console.warn(
      `[AWS S3 Upload Failed: ${error?.message || error}]. Automatically falling back to local server storage...`
    );
    // Seamless fallback to backend local storage so upload never breaks
    return await uploadToBackend(file, onProgress);
  }
};

/**
 * Deletes a file from Amazon S3 based on its CloudFront URL.
 */
export const deleteFileFromS3 = async (url: string): Promise<void> => {
  if (!url) return;

  // Skip S3 deletion for local server uploads
  if (url.includes("/uploads/") || !isS3Configured()) {
    console.log("[DELETE] Skipping S3 deletion for local file:", url);
    return;
  }

  // Extract the S3 key from the URL
  const cleanCFUrl = cloudfrontDomain.replace(/^(https?:\/\/)?/, "").replace(/\/$/, "");
  if (!url.includes(cleanCFUrl)) {
    return;
  }

  const parts = url.split(cleanCFUrl + "/");
  if (parts.length < 2) return;
  const key = parts[1];

  try {
    const { S3Client, DeleteObjectCommand } = await import("@aws-sdk/client-s3");
    const s3Client = new S3Client({
      region,
      credentials: {
        accessKeyId,
        secretAccessKey,
      },
    });

    const command = new DeleteObjectCommand({
      Bucket: bucketName,
      Key: key,
    });

    await s3Client.send(command);
    console.log(`[S3 LIVE] Deleted successfully. Key: ${key}`);
  } catch (error) {
    console.error(`[S3 LIVE] Failed to delete key: ${key}`, error);
  }
};
