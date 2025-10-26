# CMT Leg Cast Design App

A medical Android application for measuring patient's legs using computer vision and designing custom 3D printed orthotic casts specifically for CMT (Charcot-Marie-Tooth) patients.

## Features

### Core Functionality
- **Camera-based Measurement**: Uses device camera with ruler reference for accurate leg measurements
- **Computer Vision**: OpenCV-powered ruler detection and dimension extraction
- **3D Reconstruction**: Generates 3D models from multiple 2D captures
- **Custom Cast Design**: Creates orthotic casts optimized for CMT ankle support
- **STL Export**: Generates 3D printing files with proper tolerances

### Medical Considerations
- **CMT-Specific Design**: Tailored for Charcot-Marie-Tooth ankle weakness and foot drop
- **Weight Distribution**: Optimized load distribution to reduce ankle strain
- **Safety Validation**: Built-in measurement validation and medical disclaimers
- **Comfort Features**: Proper padding and ergonomic design considerations

## Technology Stack

- **Android**: Native Android app (API 26+)
- **Camera2 API**: Advanced camera functionality for high-quality captures
- **OpenCV**: Computer vision library for image processing and measurements
- **Kotlin**: Primary development language
- **Material Design**: Modern, accessible user interface

## Project Structure

```
app/
├── src/main/java/com/medical/cmtcast/
│   ├── MainActivity.kt                 # Main app entry point
│   ├── camera/
│   │   └── CameraActivity.kt          # Camera capture interface
│   ├── measurement/
│   │   └── MeasurementActivity.kt     # Measurement processing
│   ├── vision/
│   │   └── RulerDetector.kt           # Computer vision algorithms
│   └── cast/
│       └── CastDesignActivity.kt      # 3D cast design (planned)
├── src/main/res/
│   ├── layout/                        # UI layouts
│   ├── values/                        # Colors, strings, themes
│   └── drawable/                      # Icons and graphics
└── build.gradle                       # Dependencies and build config
```

## Key Dependencies

- **AndroidX Camera**: Camera2 implementation for reliable photo capture
- **OpenCV Android**: Computer vision and image processing
- **Material Components**: Modern UI components
- **EasyPermissions**: Simplified permission handling

## Usage Workflow

1. **Permission Setup**: Request camera and storage permissions
2. **Measurement Capture**: 
   - Position standard ruler next to leg
   - Capture multiple angles (front, side, back, detail)
   - Real-time feedback on capture quality
3. **Computer Vision Processing**:
   - Detect ruler for scale reference
   - Extract leg measurements using image processing
   - Validate measurements for accuracy
4. **3D Cast Design**: Generate custom cast based on measurements (planned)
5. **Export**: Create STL files for 3D printing (planned)

## Medical Disclaimers

⚠️ **Important Medical Notice**
- This is a prototype medical device
- Always consult qualified medical professionals
- Not a substitute for professional medical advice
- Intended for research and development purposes

## Development Status

### Completed ✅
- [x] Android project structure and dependencies
- [x] Camera interface with multi-angle capture
- [x] Computer vision ruler detection
- [x] Basic measurement extraction
- [x] UI/UX foundation with Material Design

### In Progress 🚧
- [ ] 3D reconstruction from measurements
- [ ] Custom cast design algorithms
- [ ] STL file generation
- [ ] Advanced measurement validation

### Planned 📋
- [ ] Machine learning for improved accuracy
- [ ] Integration with 3D printing services
- [ ] Medical professional review interface
- [ ] Patient data management

## Installation

1. **Prerequisites**:
   - Android Studio Arctic Fox or later
   - Android SDK 26+
   - OpenCV Android SDK

2. **Setup**:
   ```bash
   git clone [repository-url]
   cd deans-leg
   # Open in Android Studio
   # Build and run on Android device
   ```

3. **Required Permissions**:
   - Camera access
   - Storage access (for saving measurements and images)

## Contributing

This is a medical research project. Contributions should follow medical software development best practices and include appropriate testing and validation.

## License

This project is for research and development purposes. Commercial use requires appropriate medical device certifications and approvals.

---

**Note**: This application is designed specifically for CMT (Charcot-Marie-Tooth) patients who experience ankle weakness and foot drop. The cast design aims to provide ankle support while allowing natural movement and weight distribution.