package com.example.clms.auth;

import com.example.clms.user.User;
import com.example.clms.user.Role;
import com.example.clms.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String cleanEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmailIgnoreCase(cleanEmail)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // If the account exists and password matches, we authenticate the user.
        // Even if the UI sent a different role or the user forgot to switch tabs,
        // we log them in under their legitimate database role.
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                        .build()
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getLinkedinUrl(),
                user.getDepartment()
        );
    }

    @Transactional
    public AuthResponse employeeSignup(EmployeeSignupRequest request) {
        String cleanEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        if (userRepository.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Email already exists");
        }

        User user = User.builder()
                .fullName(request.fullName() != null ? request.fullName().trim() : "")
                .email(cleanEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.EMPLOYEE)
                .active(true)
                .linkedinUrl(request.linkedinUrl())
                .department(request.department())
                .build();

        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = UUID.randomUUID().toString();

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .user(savedUser)
                        .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                        .build()
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getLinkedinUrl(),
                savedUser.getDepartment()
        );
    }

    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken savedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (savedToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(savedToken);
            throw new RuntimeException("Refresh token expired");
        }

        User user = savedToken.getUser();
        String accessToken = jwtService.generateAccessToken(user);

        return new AuthResponse(
                accessToken,
                savedToken.getToken(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getLinkedinUrl(),
                user.getDepartment()
        );
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public AuthResponse getMeFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found"));
            if (!jwtService.isValid(token, user)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token invalid or expired");
            }
            return new AuthResponse(
                    token,
                    null,
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole(),
                    user.getLinkedinUrl(),
                    user.getDepartment()
            );
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token invalid or expired", e);
        }
    }

    @Transactional
    public AuthResponse updateProfileFromToken(String authHeader, String fullName, String linkedinUrl) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found"));
            if (!jwtService.isValid(token, user)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token invalid or expired");
            }
            if (fullName != null && !fullName.trim().isEmpty()) {
                user.setFullName(fullName);
            }
            user.setLinkedinUrl(linkedinUrl);
            User saved = userRepository.save(user);
            return new AuthResponse(
                    token,
                    null,
                    saved.getId(),
                    saved.getFullName(),
                    saved.getEmail(),
                    saved.getRole(),
                    saved.getLinkedinUrl(),
                    saved.getDepartment()
            );
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token invalid or expired", e);
        }
    }

    @Transactional
    public void updateFcmToken(String authHeader, String fcmToken) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found"));
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        } catch (Exception e) {
            System.err.println("[FCM] Failed to update FCM token: " + e.getMessage());
        }
    }
}
