# PowerShell Script to Launch Full CLMS Stack
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "       CLMS - CORPORATE LEARNING MANAGEMENT SYSTEM LAUNCHER         " -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. Check PostgreSQL on 5434
Write-Host "[1/3] Checking PostgreSQL on port 5434..." -ForegroundColor Yellow
$pgTest = Test-NetConnection -ComputerName localhost -Port 5434 -WarningAction SilentlyContinue
if ($pgTest.TcpTestSucceeded) {
    Write-Host "[OK] PostgreSQL is active and listening on port 5434." -ForegroundColor Green
} else {
    Write-Host "[WARN] PostgreSQL is NOT detected on port 5434." -ForegroundColor Yellow
    Write-Host "Attempting to start PostgreSQL via Docker Compose..." -ForegroundColor Gray
    Start-Process -FilePath "docker" -ArgumentList "compose up -d postgres" -WorkingDirectory $scriptDir -NoNewWindow -Wait
    Start-Sleep -Seconds 3
    $pgTestRetry = Test-NetConnection -ComputerName localhost -Port 5434 -WarningAction SilentlyContinue
    if ($pgTestRetry.TcpTestSucceeded) {
        Write-Host "[OK] PostgreSQL container started on port 5434." -ForegroundColor Green
    } else {
        Write-Host "[NOTE] Ensure PostgreSQL is running on port 5434 before testing API requests." -ForegroundColor Red
    }
}
Write-Host ""

# 2. Launch Backend
Write-Host "[2/3] Launching Spring Boot Backend on port 8080..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k title CLMS Backend && .\mvnw.cmd spring-boot:run" -WorkingDirectory "$scriptDir\Backend"
Write-Host "[OK] Backend started in separate window." -ForegroundColor Green
Write-Host ""

# 3. Launch Frontend
Write-Host "[3/3] Launching React/Vite Frontend on port 5173..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k title CLMS Frontend && npm run dev" -WorkingDirectory "$scriptDir\FrontEnd"
Write-Host "[OK] Frontend started in separate window." -ForegroundColor Green
Write-Host ""

# 4. Open Browser
Write-Host "Opening http://localhost:5173 in default browser..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
Start-Process "http://localhost:5173"

Write-Host ""
Write-Host "====================================================================" -ForegroundColor Green
Write-Host "[SUCCESS] CLMS Full Stack is running!" -ForegroundColor Green
Write-Host "  - Frontend: http://localhost:5173" -ForegroundColor White
Write-Host "  - Backend:  http://localhost:8080" -ForegroundColor White
Write-Host "  - Database: localhost:5434/clms_db" -ForegroundColor White
Write-Host ""
Write-Host "Pre-seeded Demo Accounts (Password: Welcome@123):" -ForegroundColor Magenta
Write-Host "  - Admin:    admin@clms.com" -ForegroundColor White
Write-Host "  - HR:       hr@clms.com" -ForegroundColor White
Write-Host "  - Manager:  manager@clms.com" -ForegroundColor White
Write-Host "  - Employee: employee@clms.com" -ForegroundColor White
Write-Host "====================================================================" -ForegroundColor Green
