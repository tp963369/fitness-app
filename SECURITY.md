# Security & Privacy Threat Model (`SECURITY.md`)

## 1. Authentication & Session Security
- **Members**: 6-digit OTP, 5-minute TTL, max 5 attempts per OTP, rate-limited to 5 OTP requests/hour/mobile, stored as Argon2id hash (`otp_requests`).
- **Admin & Staff**: Email + Argon2id password (`>= 12 chars`) + mandatory TOTP MFA (`admin_users`). Sessions enforce 8-hour absolute TTL and 30-minute idle timeout.
- **JWT Audience Isolation**: Tokens carry strict `aud` claims (`member`, `admin`, `staff`). Cross-audience calls return `403 Forbidden`.

## 2. QR + GPS Geofence Spoof-Resistance & Known Limits
- **Dual-Gate Enforcement**:
  - Gate 1: `gps_accuracy_m <= location.max_gps_accuracy_m` (default `100m`).
  - Gate 2: `haversine_distance_m <= location.geofence_radius_m` (default `100m`).
  - **Prohibited Math**: We NEVER compute `distance <= radius + accuracy`.
  - **Optional Strict Mode**: Requires `distance + accuracy <= radius`.
- **Layered Anti-Fraud Mitigations**:
  - 5 scans/hour rate limit (excluding `OUTSIDE_GEOFENCE` so legitimate members walking to the entrance can retry).
  - Automatic `attendance_anomaly_flags` generation for:
    1. Impossible travel (`> 50 km` from previous scan within `10 minutes`).
    2. Unusually high GPS accuracy variance across scans.
    3. Identical `lat/lng` coordinates across different members on the same day.
    4. Mock location provider telemetry flag.
- **Explicit Disclaimer**: *No client-reported GPS mechanism is 100% spoof-proof.* All documentation, code comments, and UI banners state this transparently.

## 3. Progress Photo Privacy & AI Isolation
- Stored in private bucket namespace with **5-minute TTL signed URLs** and `Cache-Control: private, no-store`.
- Direct unsigned bucket URLs or expired signed URLs return `403 Forbidden`.
- **Zero AI Exposure**: Progress photos are NEVER sent to any AI provider. Human admins review transformations via the side-by-side slider.

## 4. Append-Only Audit & Account Deletion (DPDP / TRAI Compliance)
- `audit_logs`, `payment_verifications`, `membership_events`, `streak_events`, `transformation_reviews`, `coupon_redemptions`, and `admin_permission_history` are strictly append-only.
- Account deletion enforces a 30-day cooling period (blocked if an active membership exists until admin confirmation or cooling completion), anonymises mobile numbers, purges PII and photos within 7 days, and retains redacted financial/audit records for 8 years.
