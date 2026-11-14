# System Defaults & Architectural Decisions (`DECISIONS.md`)

All 18 Section E system defaults were confirmed and adopted in full:

| # | Decision Domain | Adopted Default | Status |
|---|---|---|---|
| 1 | Gym Timezone | `Asia/Kolkata` (IST) for all attendance dates, streaks, and leaderboards | Adopted |
| 2 | Membership Activation | Strictly requires explicit Admin approval across Online, UPI/Bank, and Cash | Adopted |
| 3 | Clamped Calendar Months | `add_months_clamped` (Jan 31 + 1m = Feb 28/29; Feb 29 + 12m = Feb 28 non-leap) | Adopted |
| 4 | Renewal Timing | Before expiry → starts `expiry_date + 1 day`; After expiry → starts `approval_date` | Adopted |
| 5 | Geofence Dual-Gate Math | `distance <= radius AND accuracy <= max_accuracy` (Optional strict: `distance + accuracy <= radius`) | Adopted |
| 6 | Default GPS Thresholds | `geofence_radius_m = 100`, `max_gps_accuracy_m = 100` | Adopted |
| 7 | QR Scan Rate Limit | `5 scans/hour/member`; `OUTSIDE_GEOFENCE` rejections do NOT consume rate limit | Adopted |
| 8 | Attendance Scope | `PER_DAY` default (`UNIQUE(user_id, attendance_date, location_id) WHERE status='approved'`) | Adopted |
| 9 | Streak Holiday/Closure Exclusion | Both `holidays` and closed `location_schedules` excluded from `eligible_days` before streak walk | Adopted |
| 10 | Reward Idempotency | `UNIQUE(user_id, membership_id, source, milestone_index)`; recompute never claws back or re-issues | Adopted |
| 11 | Transformation Coupon | `80%` discount, `90-day` validity, single-use, non-stackable, tied to user, issued on Admin `APPROVE` only | Adopted |
| 12 | Photo Privacy & Retention | Private storage, 5-min TTL signed URL, `Cache-Control: private, no-store`, 7-day purge SLA, zero AI access | Adopted |
| 13 | Leaderboard Formula & Ties | `10 pts/day` + `2 pts/day` consecutive streak bonus; Ties: (1) max streak, (2) earliest score time, (3) `member_id` ASC | Adopted |
| 14 | Referral Policy | `7 membership days` OR `25% coupon` upon invitee's first admin-approved paid membership; self-referral blocked | Adopted |
| 15 | Expiry & Inactive Reminders | Expiry reminders at `7, 3, 1` days; Inactive recovery at `14` days; Quiet hours `22:00–08:00 IST` | Adopted |
| 16 | Account Deletion | `30-day` cooling period; PII redacted/anonymised; 8-year financial & audit retention | Adopted |
| 17 | AI Summary Policy | Server-side/engine templated, default consent OFF, max `1/week`, no photos, non-medical/non-diagnostic | Adopted |
| 18 | Admin Security & RBAC | Email + Argon2id + mandatory TOTP MFA; Owner / Admin / Staff permission matrix; append-only `audit_logs` | Adopted |
