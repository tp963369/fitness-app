# Database Migrations (`MIGRATIONS.md`)

All schema changes follow an additive, reversible migration strategy across Phases 1–13:

- **V001_identity_rbac**: `gyms`, `gym_settings`, `users`, `user_profiles`, `user_notification_preferences`, `otp_requests`, `sessions`, `admin_users`, `roles`, `permissions`, `role_permissions`, `admin_permission_history`, `audit_logs`, `idempotency_keys`.
- **V002_plans_catalogue**: `plans`, `plan_features` with pricing visibility and streak/freeze overrides.
- **V003_locations_schedules_qr**: `locations`, `location_schedules`, `holidays`, `qr_identifiers` (`qr_token >= 32 chars`).
- **V004_orders_payments_refunds**: `orders`, `payment_attempts`, `payment_verifications`, `payment_webhooks`, `bank_transfer_proofs`, `cash_receipts`, `refunds`.
- **V005_memberships_freezes**: `memberships` (`UNIQUE(source_payment_id)`), `membership_events`, `membership_freezes`.
- **V006_attendance_geofence_anomalies**: `attendance` (unique approved per user/date/location), `attendance_adjustments`, `location_verification_results`, `attendance_anomaly_flags`.
- **V007_streaks_rewards**: `streak_policy_config`, `streak_state`, `streak_events`, `rewards` (`UNIQUE(user_id, source, membership_id, milestone_index)`).
- **V008_transformations_coupons**: `photo_consent`, `progress_photos`, `photo_deletion_requests`, `transformation_submissions`, `transformation_reviews`, `coupon_rules`, `coupons`, `coupon_redemptions`.
- **V009_gamification_referrals_ops**: `badges`, `member_badges`, `leaderboard_scoring_rules`, `leaderboard_entries`, `user_leaderboard_preferences`, `referrals`, `referral_rewards`, `notifications`, `push_tokens`, `campaigns`, `campaign_deliveries`, `support_tickets`, `support_ticket_messages`, `deletion_requests`.

Each migration has a verified `UP` and reversible `DOWN` routine tested on a scratch database instance.
