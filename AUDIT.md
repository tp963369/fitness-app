# Phase 0: Codebase Audit (`AUDIT.md`)

## 1. Repository & Stack Inspection
- **Platform**: Android (`compileSdk 36`, `minSdk 24`, `targetSdk 36`), Kotlin 2.2.10, Jetpack Compose BOM `2024.09.00`.
- **Persistence & Serialization**: Room 2.7.0 (`androidx.room:room-runtime`, `room-ktx`, `room-compiler` via KSP), Moshi 1.15.2, Retrofit 2.12.0, OkHttp 4.10.0.
- **Application ID**: `com.aistudio.ironpulsegym.kpvqzm` (Namespace: `com.example`).
- **Fonts Bundled**: `space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf`.

## 2. Existing Website & Integration Audit
- **Existing Website Rewrite**: Prohibited per Section 18. No existing web assets are overwritten; Phase 14 provides a feature-flagged (`feature_web_catalogue_sync_enabled`) deep-link & public catalogue sync bridge (`/api/v1/public/plans`, `/api/v1/public/gym/payment-instructions`).
- **Payment Gateway Configuration**: Defaulted to **Razorpay (Test Mode)** with HMAC-SHA256 webhook signature verification (`UNIQUE(gateway, gateway_event_id)`), plus manual **UPI/Bank Transfer Proof** upload and **Staff-to-Admin Cash Receipt** confirmation.
- **Primary Gym Seed Configuration**:
  - **Gym Name**: IronPulse Flagship Fitness (Bengaluru, India)
  - **Timezone**: `Asia/Kolkata` (IST)
  - **Coordinates**: `12.971599, 77.594566`
  - **Geofence Radius**: `100m` (`geofence_radius_m = 100`)
  - **Max GPS Accuracy**: `100m` (`max_gps_accuracy_m = 100`)
  - **Operating Schedule**: Monday–Saturday `05:30–22:30` (Attendance window `05:30–22:00`); Sunday Closed (derived from `location_schedules`).
