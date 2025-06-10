# Liveness Detection Android App

A native Android application built with Kotlin and Jetpack Compose that performs real-time liveness detection using your deployed API service.

## Features

- **Native Android App**: Built with Kotlin and Jetpack Compose
- **Real-time Camera**: Uses front camera for 15-second liveness detection sessions
- **WebSocket Integration**: Real-time communication with your liveness detection API
- **Modern UI**: Beautiful Material Design 3 interface with animations
- **Live Statistics**: Real-time display of liveness scores, frame count, blinks, and FPS
- **Connection Status**: Visual indicator of API connection status
- **Recording Timer**: 15-second countdown timer during sessions

## Architecture

- **MVVM Pattern**: Clean architecture with ViewModel and StateFlow
- **Jetpack Compose**: Modern declarative UI framework
- **Camera2 API**: Advanced camera functionality
- **Retrofit**: HTTP client for API communication
- **OkHttp WebSocket**: Real-time WebSocket communication
- **Coroutines**: Asynchronous programming

## API Integration

The app connects to your deployed liveness detection service at:
- **Base URL**: `http://biosdk.credissuer.com:8001`
- **WebSocket**: `ws://biosdk.credissuer.com:8001/ws/process/{sessionId}`

## How It Works

1. **Start Session**: Calls `/start-session` API to get a session ID
2. **WebSocket Connection**: Connects to WebSocket endpoint for real-time communication
3. **Camera Capture**: Captures frames from front camera at 10 FPS
4. **Frame Processing**: Sends base64-encoded frames to your API via WebSocket
5. **Real-time Results**: Displays liveness scores, blink detection, and decisions
6. **Session Management**: Automatically stops after 15 seconds or manual stop

## Building the App

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9.20+

### Build Steps

1. **Open in Android Studio**:
   ```bash
   # Open the project folder in Android Studio
   ```

2. **Sync Gradle**:
   - Android Studio will automatically sync Gradle dependencies

3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on Device**:
   ```bash
   ./gradlew installDebug
   ```

### Build Variants

- **Debug**: Development build with logging
- **Release**: Production build (requires signing)

## Permissions

The app requires the following permissions:
- `CAMERA`: Access to device camera
- `INTERNET`: Network communication
- `ACCESS_NETWORK_STATE`: Check network connectivity

## Project Structure

```
app/src/main/java/com/biosdk/livenessdetection/
├── MainActivity.kt                 # Main activity
├── data/
│   ├── api/                       # API service interfaces
│   ├── models/                    # Data models
│   └── websocket/                 # WebSocket manager
├── ui/
│   ├── components/                # Reusable UI components
│   ├── screens/                   # Screen composables
│   └── theme/                     # App theming
└── viewmodel/                     # ViewModels
```

## Key Components

### CameraScreen
- Main UI with camera preview
- Real-time statistics display
- Recording controls and status

### WebSocketManager
- Handles WebSocket connections
- Frame transmission
- Real-time result processing

### CameraViewModel
- Manages app state
- Coordinates API calls
- Handles session lifecycle

## Testing

The app replicates the functionality of your `api1.py` script but with:
- Real camera input instead of video file
- Native Android UI
- Real-time processing
- Mobile-optimized performance

## Deployment

To create a release APK:

1. **Generate Signing Key**:
   ```bash
   keytool -genkey -v -keystore release-key.keystore -alias alias_name -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Install APK**:
   ```bash
   adb install app/build/outputs/apk/release/app-release.apk
   ```

## API Compatibility

This app is fully compatible with your existing API service and provides the same functionality as your Python test script, but with a native Android interface and real camera input.