# IronPulse — Gym Membership, QR Geofence Attendance, Transformation & Admin Platform

IronPulse is a comprehensive Android platform built with **Kotlin**, **Jetpack Compose**, and an offline-first **Room** database + authoritative API service layer (`/api/v1`) implementing all 93 features across Phases 0–15.

## Key Capabilities
1. **Strict Admin-Gated Payments (3 Channels)**: Online Gateway (with HMAC webhook deduplication), UPI/Bank Transfer Proof upload, and Staff-to-Admin Cash Receipt confirmation. No membership activates without Admin approval.
2. **Dual-Gate QR + GPS Attendance**: Validates `distance <= radius AND accuracy <= max_accuracy` (plus optional `strict_mode: distance + accuracy <= radius`), never inflating radius by accuracy. `OUTSIDE_GEOFENCE` rejections do not consume the 5/hour scan rate limit. Includes layered anomaly detection.
3. **Deterministic Streaks & Idempotent Rewards**: Excludes `holidays` and closed `location_schedules` days before walking `eligible_days`. Enforces `UNIQUE(user_id, membership_id, source, milestone_index)` so recomputations never duplicate or claw back rewards.
4. **3-Angle Transformations & 80% Coupon**: Mandatory front/side/back start and end photos stored with 5-minute TTL signed URLs (`Cache-Control: private, no-store`), interactive before/after slider, human-only Admin review, and single-use 80% renewal coupon issuance. Zero photos are ever sent to AI services.
5. **69 Complete Screens**: 37 Member App screens and 32 Admin/Staff Console screens with instant role switching for testing and inspection.

## Running Automated Tests (All 35 Mandatory Scenarios)
```bash
gradle :app:testDebugUnitTest
```
