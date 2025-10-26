# CMT Cast Designer - Quick Setup Script

Write-Host "🏥 CMT Cast Designer Setup" -ForegroundColor Cyan
Write-Host "Setting up your medical leg measurement app..." -ForegroundColor White

# Check if Android Studio is likely installed
$androidStudioPaths = @(
    "${env:LOCALAPPDATA}\Google\AndroidStudio*",
    "${env:ProgramFiles}\Android\Android Studio*",
    "${env:ProgramFiles(x86)}\Android\Android Studio*"
)

$studioFound = $false
foreach ($path in $androidStudioPaths) {
    if (Test-Path $path) {
        $studioFound = $true
        Write-Host "✅ Android Studio found" -ForegroundColor Green
        break
    }
}

if (-not $studioFound) {
    Write-Host "❌ Android Studio not found" -ForegroundColor Red
    Write-Host "📥 Please download and install Android Studio first:" -ForegroundColor Yellow
    Write-Host "   https://developer.android.com/studio" -ForegroundColor Cyan
    Read-Host "Press Enter after installing Android Studio..."
}

# Check if we're in the right directory
if (-not (Test-Path "app\build.gradle")) {
    Write-Host "❌ Not in project directory" -ForegroundColor Red
    Write-Host "Please run this script from: C:\Users\mattc\deans leg" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Project structure verified" -ForegroundColor Green

# Check for connected Android devices
Write-Host "🔍 Checking for connected Android devices..." -ForegroundColor Cyan

$adbPath = ""
$possibleAdbPaths = @(
    "${env:LOCALAPPDATA}\Android\Sdk\platform-tools\adb.exe",
    "${env:ANDROID_HOME}\platform-tools\adb.exe",
    "${env:ANDROID_SDK_ROOT}\platform-tools\adb.exe"
)

foreach ($path in $possibleAdbPaths) {
    if (Test-Path $path) {
        $adbPath = $path
        break
    }
}

if ($adbPath) {
    Write-Host "✅ ADB found at: $adbPath" -ForegroundColor Green
    
    $devices = & $adbPath devices
    $connectedDevices = $devices | Where-Object { $_ -match "device$" }
    
    if ($connectedDevices) {
        Write-Host "✅ Android device(s) connected:" -ForegroundColor Green
        $connectedDevices | ForEach-Object { Write-Host "   $_" -ForegroundColor White }
    } else {
        Write-Host "⚠️  No Android devices detected" -ForegroundColor Yellow
        Write-Host "📱 To connect your Samsung S10:" -ForegroundColor Cyan
        Write-Host "   1. Enable Developer Options (Settings → About → Tap Build Number 7 times)" -ForegroundColor White
        Write-Host "   2. Enable USB Debugging (Settings → Developer Options)" -ForegroundColor White
        Write-Host "   3. Connect via USB cable" -ForegroundColor White
        Write-Host "   4. Allow USB debugging when prompted" -ForegroundColor White
    }
} else {
    Write-Host "⚠️  ADB not found (will be available after Android Studio setup)" -ForegroundColor Yellow
}

Write-Host "`n🚀 Next Steps:" -ForegroundColor Cyan
Write-Host "1. Open Android Studio" -ForegroundColor White
Write-Host "2. Choose 'Open an existing project'" -ForegroundColor White
Write-Host "3. Navigate to: $(Get-Location)" -ForegroundColor White
Write-Host "4. Wait for Gradle sync (may take 5-10 minutes)" -ForegroundColor White
Write-Host "5. Connect your Samsung S10 via USB" -ForegroundColor White
Write-Host "6. Click the green Run button ▶️" -ForegroundColor White

Write-Host "`n📱 Your S10 Specs:" -ForegroundColor Cyan
Write-Host "✅ Camera: 12MP (perfect for measurements)" -ForegroundColor Green
Write-Host "✅ Processor: Snapdragon 855/Exynos 9820 (handles OpenCV)" -ForegroundColor Green
Write-Host "✅ RAM: 8GB (excellent for image processing)" -ForegroundColor Green
Write-Host "✅ Android: 9+ supported (app needs Android 8+)" -ForegroundColor Green

Write-Host "`n🏥 Medical Features Ready:" -ForegroundColor Cyan
Write-Host "• Camera-based leg measurement with ruler reference" -ForegroundColor White
Write-Host "• Computer vision for accurate dimension extraction" -ForegroundColor White
Write-Host "• CMT-specific ankle support calculations" -ForegroundColor White
Write-Host "• Medical safety validation and disclaimers" -ForegroundColor White

Write-Host "`n🎯 Ready to build your CMT cast designer!" -ForegroundColor Green

# Offer to open Android Studio if available
$choice = Read-Host "`nWould you like to try opening Android Studio now? (y/n)"
if ($choice -eq 'y' -or $choice -eq 'Y') {
    Write-Host "🚀 Attempting to open Android Studio..." -ForegroundColor Cyan
    
    # Try to find and launch Android Studio
    $studioExe = Get-ChildItem -Path $env:LOCALAPPDATA, ${env:ProgramFiles}, ${env:ProgramFiles(x86)} -Recurse -Name "studio64.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    
    if ($studioExe) {
        $fullPath = (Get-ChildItem -Path "/" -Recurse -Name "studio64.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
        Start-Process $fullPath -ArgumentList (Get-Location)
        Write-Host "✅ Android Studio should be opening..." -ForegroundColor Green
    } else {
        Write-Host "❌ Could not locate Android Studio executable" -ForegroundColor Red
        Write-Host "Please open Android Studio manually" -ForegroundColor Yellow
    }
}