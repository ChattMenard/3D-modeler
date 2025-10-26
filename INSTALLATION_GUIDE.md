# Getting the CMT Cast Designer App Running on Your Samsung Galaxy S10

## Prerequisites

### 1. Install Android Studio
- Download from: https://developer.android.com/studio
- Install with default settings
- Make sure Android SDK is included

### 2. Prepare Your Samsung Galaxy S10

#### Enable Developer Options:
1. Go to **Settings** → **About phone**
2. Tap **Build number** 7 times rapidly
3. You'll see "Developer mode enabled"
4. Go back to **Settings** → **Developer options**
5. Enable **USB debugging**
6. Enable **Install via USB** (if available)

#### Connect to Computer:
1. Use a USB cable to connect S10 to your computer
2. On your phone, when prompted, select **"File Transfer"** or **"MTP"**
3. Allow USB debugging when prompted (check "Always allow from this computer")

## Building and Installing the App

### Method 1: Using Android Studio (Recommended)

1. **Open the Project:**
   ```
   - Launch Android Studio
   - Choose "Open an existing project"
   - Navigate to: C:\Users\mattc\deans leg
   - Click "OK"
   ```

2. **Wait for Setup:**
   - Android Studio will sync the project
   - Download required dependencies (may take 5-10 minutes first time)
   - Wait for "Gradle sync finished" message

3. **Verify Device Connection:**
   - Look for your S10 in the device dropdown (top toolbar)
   - Should show something like "Samsung SM-G973F" or similar

4. **Build and Run:**
   - Click the green "Run" button (▶️) or press Shift+F10
   - App will build and install automatically on your S10

### Method 2: Direct APK Install (Alternative)

If you want to build an APK file to install directly:

1. **Generate APK:**
   ```
   - In Android Studio: Build → Build Bundle(s)/APK(s) → Build APK(s)
   - Wait for build to complete
   - Click "locate" when build finishes
   ```

2. **Transfer APK:**
   - Copy the APK file to your S10 (via USB, cloud, etc.)
   - On your S10, enable "Install unknown apps" for your file manager
   - Open the APK file and install

## Troubleshooting Common Issues

### If Device Not Detected:
```powershell
# Run this in your terminal to check device connection:
adb devices
```
- Should show your S10 listed
- If not, try different USB cable or port
- Restart adb: `adb kill-server` then `adb start-server`

### If Build Fails:
- **Missing SDK:** Android Studio will prompt to install missing components
- **OpenCV Issues:** The app includes OpenCV as a dependency, should work automatically
- **Gradle Issues:** Try "File → Invalidate Caches and Restart"

### First Run Permissions:
When you first open the app on your S10:
1. Grant **Camera** permission when prompted
2. Grant **Storage** permission when prompted
3. If permissions are denied, go to Settings → Apps → CMT Cast Designer → Permissions

## What You'll See on Your S10

1. **Main Screen:** 
   - App title and description
   - "Start Leg Measurement" button
   - Medical disclaimer

2. **Camera Interface:**
   - Full-screen camera view
   - Step-by-step capture guidance
   - Progress indicator for 4 required angles

3. **Measurement Analysis:**
   - Processing status
   - Extracted measurements
   - Validation results

## Performance on Galaxy S10

Your S10 is perfect for this app:
- ✅ **Camera:** 12MP main camera with excellent quality
- ✅ **Processing:** Snapdragon 855/Exynos 9820 handles OpenCV well
- ✅ **RAM:** 8GB RAM sufficient for image processing
- ✅ **Android:** Supports Android 9+ (app requires Android 8+)

## Quick Start Commands

Open PowerShell in the project directory and run:

```powershell
# Check if your S10 is connected
adb devices

# Install the app directly (if you have an APK)
adb install app-debug.apk

# Launch the app
adb shell am start -n com.medical.cmtcast/.MainActivity
```

## Expected First Use Experience

1. **Launch app** → Grant permissions
2. **Take photos** → Position ruler, capture 4 angles
3. **View results** → See extracted measurements
4. **Medical validation** → Check measurement accuracy

The app should work immediately on your S10! The computer vision will detect rulers and extract leg measurements in real-time.

Need help with any specific step?