# 93-Feature Live Checklist (`FEATURES.md`)

All 93 features across Phases 0–15 are implemented, wired, and verified:

## Phase 0 & 1: Core, Auth, RBAC & Audit (Features 1–10)
- [x] F01: Multi-tenant `gyms` & `gym_settings` configuration (`Asia/Kolkata`)
- [x] F02: Member OTP Send & Verify (6-digit, 5-min TTL, 5 attempts, 5/hr rate limit, Argon2id hash)
- [x] F03: Separate JWT audiences (`member`, `admin`, `staff`) + session rotation/revocation
- [x] F04: Admin/Staff Email + Argon2id Password + Mandatory TOTP MFA verification
- [x] F05: RBAC Permission Matrix (`Owner`, `Admin`, `Staff`) enforced on all endpoints
- [x] F06: Append-only `admin_permission_history` & `audit_logs` (INSERT/SELECT only)
- [x] F07: Idempotency-Key deduplication guard (`idempotency_keys`)
- [x] F08: Member Onboarding & Profile Management (`user_profiles`)
- [x] F09: Notification & Marketing Consent Preferences (`user_notification_preferences`)
- [x] F10: Role Switcher & Session Inspector for instant multi-role verification

## Phase 2: Plans & Pricing Visibility Engine (Features 11–18)
- [x] F11: Plans CRUD (`DAYS`, `WEEKS`, `CALENDAR_MONTHS`, `CALENDAR_YEARS`, `CUSTOM`)
- [x] F12: Plan Features ordering (`plan_features`)
- [x] F13: Dynamic Pricing Visibility Engine (`PUBLIC`, `LOGIN_REQUIRED`, `HIDDEN_CALL_TO_ENQUIRE`)
- [x] F14: Per-plan `pricing_visibility_override` enforcement (never hardcoded in frontend)
- [x] F15: Public Catalogue API (`/api/v1/public/plans`) hiding internal prices when restricted
- [x] F16: Plan Freeze & Streak Policy overrides
- [x] F17: Plan Archival (`archived_at`)
- [x] F18: Payment methods configuration per plan (`ONLINE`, `UPI_BANK`, `CASH`)

## Phase 3: Locations, Schedules, Holidays & Permanent QR (Features 19–25)
- [x] F19: Gym & Class Locations CRUD with GPS (`lat`, `lng`), `geofence_radius_m`, `max_gps_accuracy_m`
- [x] F20: Weekly `location_schedules` with open/close & attendance windows (`0–6` day of week)
- [x] F21: Derived closed days from `location_schedules`
- [x] F22: Gym-wide `holidays` CRUD with automatic retroactive streak recompute trigger
- [x] F23: Permanent opaque `qr_token` (`>= 32 chars`, unique) per location
- [x] F24: QR Regeneration with immediate revocation of old token + audit log
- [x] F25: Printable/Downloadable QR badge card in Admin Console

## Phase 4–6: Three-Channel Payments, Webhooks & Refunds (Features 26–41)
- [x] F26: Order creation (`new`, `renewal`, `upgrade`, `downgrade`) with `idempotency_key`
- [x] F27: Online Payment Initiation (`INITIATED` → `PENDING_GATEWAY`)
- [x] F28: Webhook processor with HMAC verification & `UNIQUE(gateway, gateway_event_id)`
- [x] F29: Gateway success transitions to `PENDING_ADMIN_APPROVAL` (never auto-activates membership)
- [x] F30: Gateway failure auto-fails before reaching Admin approval queue
- [x] F31: Amount mismatch detection → auto-flagged for investigation (`mark-investigation`)
- [x] F32: UPI / Bank Transfer submission with UTR reference & proof metadata (`bank_transfer_proofs`)
- [x] F33: Cash payment selection by member + Staff cash receipt recording (`cash_receipts`)
- [x] F34: Mandatory Admin confirmation for Staff-recorded cash entries (`confirm-cash`)
- [x] F35: Admin Payment Approval (`approve`) with idempotency guard
- [x] F36: Admin Payment Rejection (`reject`) & Request Proof (`request-proof`) with mandatory reason
- [x] F37: Append-only `payment_verifications` log with `before_state` and `after_state`
- [x] F38: Refund initiation & processing (`refunds`)
- [x] F39: Refund membership impact policies (`NONE` default, `CANCEL` membership, `EXTEND`)
- [x] F40: Admin Payment Approval Centre with 7 sub-tabs (Online, UPI, Cash, Pending, Approved, Rejected, Refunded)
- [x] F41: Member Payment Pending, Success, and Rejection screens

## Phase 7: Membership Lifecycle & Freeze Engine (Features 42–51)
- [x] F42: Membership activation strictly linked to `source_payment_id UNIQUE`
- [x] F43: Clamped calendar-month expiry calculation (`add_months_clamped` for leap years & month-ends)
- [x] F44: Renewal before expiry (`start_date = current_expiry + 1 day`)
- [x] F45: Renewal after expiry (`start_date = approval_date`)
- [x] F46: Membership Upgrade & Downgrade proration and parent linkage (`parent_membership_id`)
- [x] F47: Member Freeze Request & Cancel (`membership_freezes`)
- [x] F48: Admin Freeze Approval & Rejection with mandatory reason
- [x] F49: Freeze Resume extending `expiry_date` by exact frozen days
- [x] F50: Attendance blocked & streak protected while membership is `FROZEN`
- [x] F51: Append-only `membership_events` timeline

## Phase 8: QR Attendance, Dual-Gate Geofence & Anomalies (Features 52–61)
- [x] F52: QR Precheck & Scan endpoint (`/api/v1/attendance/scan`)
- [x] F53: Gate 1 GPS Accuracy check (`accuracy <= max_gps_accuracy_m`)
- [x] F54: Gate 2 Haversine distance check (`distance <= geofence_radius_m`, never `radius + accuracy`)
- [x] F55: Optional Admin `strict_mode` (`distance + accuracy <= radius`)
- [x] F56: Rate limit `5 scans/hour/member` where `OUTSIDE_GEOFENCE` does NOT consume quota
- [x] F57: Unique approved attendance per `(user_id, attendance_date, location_id)`
- [x] F58: Layered Anomaly Detection (>50km in 10 min, high accuracy variance, identical lat/lng across members)
- [x] F59: Admin Anomaly Review queue (`attendance_anomaly_flags`)
- [x] F60: Admin Attendance Correction (`is_correction = true`, mandatory reason, `attendance_adjustments`)
- [x] F61: Member Monthly Attendance Calendar & History views

## Phase 9: Deterministic Streak & Rewards Engine (Features 62–68)
- [x] F62: Configurable `streak_policy_config` (`CONSECUTIVE` & `FULL_ELIGIBLE` milestones)
- [x] F63: Deterministic `eligible_days` filtering out `holidays` and closed `location_schedules`
- [x] F64: Automatic streak recompute on scan, holiday addition, or admin attendance correction
- [x] F65: Idempotent reward issuance (`UNIQUE(user_id, membership_id, source, milestone_index)`)
- [x] F66: Automatic membership expiry extension (`+reward_days`) on streak milestone reward
- [x] F67: Safe retroactive holiday recompute (never claws back already-issued rewards)
- [x] F68: Explicit Admin "Reverse Reward" action (`-reward_days`, mandatory reason, audit-logged)

## Phase 10: Transformation Photos & 80% Coupon (Features 69–77)
- [x] F69: Separate versioned Privacy Consent & Marketing Consent toggles (`photo_consent`)
- [x] F70: Mandatory 3-angle Start Photos (`front`, `side`, `back`)
- [x] F71: Optional 3-angle Weekly Photos (`week_index`) with reminder nudges
- [x] F72: Mandatory 3-angle End Photos (`front`, `side`, `back`) + submission validation
- [x] F73: Interactive Before/After Comparison Slider (Member & Admin)
- [x] F74: Human-only Admin Transformation Review (`APPROVE`, `REJECT`, `REQUEST_RESUBMISSION`)
- [x] F75: Automatic 80% single-use Coupon issuance on `APPROVE` (`coupons`, `coupon_rules`)
- [x] F76: Transactional Checkout Coupon Validation & Redemption (`coupon_redemptions`, `coupon_id UNIQUE`)
- [x] F77: Private 5-min TTL Signed URL access (`Cache-Control: private, no-store`) + 7-day Photo Deletion SLA

## Phase 11–15: Gamification, Campaigns, AI Summary, Support & QA (Features 78–93)
- [x] F78: Configurable Badges & idempotent `member_badges` engine
- [x] F79: Monthly Leaderboard (`10 pts/day` + `2 pts/day` streak bonus) with 3-tier deterministic tie-breaker
- [x] F80: Member Leaderboard Opt-Out toggle (`user_leaderboard_preferences`)
- [x] F81: Referral attribution, self-referral blocking, and reward issuance (`7 days` or `25% coupon`)
- [x] F82: Expiry reminders (`7, 3, 1` days) & Inactive member recovery (`14` days)
- [x] F83: Campaign Builder, Delivery tracking (`campaign_deliveries`), and TRAI/DND marketing opt-out check
- [x] F84: Revenue & Cohort Retention Analytics + CSV Export generator
- [x] F85: Opt-in AI Summary (`1/week` rate limit, zero photo fields enforced by schema test, non-medical)
- [x] F86: Notification Center with `dedupe_key UNIQUE` and IST Quiet Hours (`22:00–08:00`)
- [x] F87: Member & Admin Support Tickets with signed attachment URLs (`support_tickets`)
- [x] F88: Account Deletion Request with `30-day` cooling period, active-membership guard, and PII redaction
- [x] F89: Complete 37 Member Screens (12 dimensions each, zero dead buttons)
- [x] F90: Complete 32 Admin/Staff Screens (responsive tables, confirmation modals, audit notices)
- [x] F91: Feature-flagged Website Catalogue Integration bridge
- [x] F92: Database Backup & Scratch Restoration Verification Drill
- [x] F93: Automated Test Suite covering all 35 mandatory scenarios + security & determinism tests
