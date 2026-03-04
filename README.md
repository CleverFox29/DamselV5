# DamselV5 - Personal Safety & Emergency Response App

DamselV5 is a robust Android safety application designed to provide immediate assistance during emergencies. It integrates with external BLE (Bluetooth Low Energy) devices to act as a silent trigger for a multi-stage emergency response sequence.

## 🚀 Key Features

- **BLE Integration**: Seamlessly connect to hardware panic buttons to trigger alerts even when your phone is in your pocket.
- **10-Second Panic Timer**: A managed countdown that allows for accidental trigger cancellation directly from the app or notification bar.
- **Smart SMS Alerts**: Automatically sends emergency messages to a customizable list of contacts.
- **Real-Time Location Sharing**: SMS alerts include a high-accuracy Google Maps link. The app uses a "Fresh-First" strategy with a 5-second timeout and instant fallback to the last known location for maximum reliability.
- **Automatic Emergency Calling**: Initiates a direct phone call to your primary emergency contact immediately after sending SMS alerts.
- **Stealth Mode**: Automatically mutes all device volumes (Ringer, Media, Notifications) upon trigger to avoid attracting unwanted attention.
- **Live Notifications**: Features a real-time countdown in the notification bar, utilizing modern Android 16+ "Promoted Ongoing" update styles for high visibility.
- **Background Reliability**: Optimized with Foreground Services, WakeLocks, and specific permission handling to ensure it works even when the screen is off or the device is locked.

## 🎨 Design

- **Material 3 UI**: A modern, clean interface following the latest Google design standards.
- **Adaptive Theming**: 
  - **Light Mode**: Clean white backgrounds with high-contrast typography.
  - **Dark Mode**: True OLED black background for battery saving and low-light discretion.
- **Dynamic Feedback**: Haptic vibration feedback for every second of the countdown and visual sync between the app UI and background service.

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Database**: Room (for contact management)
- **Concurrency**: Kotlin Coroutines & Flow (StateFlow for reactive UI)
- **Location**: Google Play Services Location (FusedLocationProvider)
- **Bluetooth**: Android BLE API

## 📋 Requirements & Permissions

To ensure reliability in life-critical situations, the app requires the following:
- **Location**: Set to "Allow all the time" (required for screen-off location tracking).
- **Display over other apps**: Required to bypass background restrictions and initiate phone calls when the screen is locked.
- **SMS & Call**: Necessary to contact your emergency circle.
- **Bluetooth**: To maintain connection with your panic button hardware.

## 🏗 Setup & Installation

1. Clone the repository.
2. Sync the project with Gradle.
3. Build and deploy to an Android device (API 26+).
4. Follow the in-app prompts to grant necessary safety permissions.
5. Set a primary emergency contact and add numbers to your SMS alert list.

---
*Developed with a focus on reliability, discretion, and speed.*
