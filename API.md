# REST API Reference (`API.md`)

Base URL: `/api/v1`
All mutating endpoints for orders, payments, attendance, coupons, referrals, and streaks require an `Idempotency-Key` header.

## Public Endpoints
- `POST /api/v1/auth/otp/send`
- `POST /api/v1/auth/otp/verify`
- `GET  /api/v1/public/plans` (enforces `pricing_visibility`)
- `GET  /api/v1/public/plans/:id`
- `GET  /api/v1/public/gym/payment-instructions`
- `POST /api/v1/payments/webhook/:gateway` (HMAC verified, idempotent on `(gateway, gateway_event_id)`)

## Member Endpoints (`aud=member`)
- Profile & Dashboard: `GET /me/dashboard`, `GET/PUT /me/profile`, `POST /me/photo`, `PUT /me/notification-preferences`
- Membership & Freezes: `GET /me/membership`, `GET /me/memberships/history`, `POST /me/membership/freeze-request`, `POST /me/membership/freeze-cancel`
- Orders & Payments: `POST /orders`, `GET /orders/:id`, `POST /payments/online/initiate`, `POST /payments/upi-bank/submit`, `POST /payments/cash/select`, `GET /payments/:id/status`
- Attendance & Streaks: `GET /me/attendance`, `GET /me/attendance/calendar`, `GET /attendance/precheck`, `POST /attendance/scan`, `GET /me/streak`, `GET /me/rewards`
- Gamification & Referrals: `GET /me/badges`, `GET /leaderboard`, `PUT /me/leaderboard/opt-out`, `GET /me/referrals`, `POST /me/referrals/share`
- Transformation & Coupons: `POST /me/photos/signature`, `POST /me/photos/confirm`, `GET /me/photos/compare`, `POST /me/transformation/submit`, `GET /me/transformation/status`, `GET /me/coupons`, `POST /coupons/validate`
- Support & Privacy: `GET /me/notifications`, `PATCH /me/notifications/:id/read`, `POST /me/deletion-request`, `POST /me/deletion-cancel`, `GET/POST /support/tickets`, `POST /support/tickets/:id/messages`

## Admin & Staff Endpoints (`aud=admin` / `aud=staff`, RBAC Enforced)
- Auth & Overview: `POST /admin/auth/login`, `POST /admin/auth/totp/verify`, `GET /admin/overview`, `GET /admin/members`, `GET /admin/members/:id`
- Plans & Locations: `GET/POST/PUT /admin/plans`, `POST /admin/plans/:id/archive`, `GET/POST/PUT /admin/locations`, `GET /admin/locations/:id/qr`, `POST /admin/locations/:id/qr/regenerate`, `GET/PUT /admin/schedule`, `POST /admin/holidays`
- Payments & Refunds: `GET /admin/payments`, `POST /admin/payments/:id/approve`, `POST /admin/payments/:id/reject`, `POST /admin/payments/:id/request-proof`, `POST /admin/payments/:id/mark-investigation`, `POST /admin/payments/:id/record-cash`, `POST /admin/payments/:id/confirm-cash`, `POST /admin/payments/:id/refund`, `GET /admin/refunds`
- Attendance & Streaks: `GET /admin/attendance`, `POST /admin/attendance/correct`, `GET /admin/anomalies`, `POST /admin/anomalies/:id/review`, `GET /admin/streaks`, `POST /admin/streaks/:memberId/adjust`, `POST /admin/rewards/:id/reverse`, `GET/PUT /admin/rewards/rules`
- Transformations & Coupons: `GET /admin/transformations`, `POST /admin/transformations/:id/approve`, `POST /admin/transformations/:id/reject`, `POST /admin/transformations/:id/resubmit`, `GET/PUT /admin/coupons/rules`, `POST /admin/coupons/:id/revoke`
- Marketing, Analytics & Ops: `POST /admin/notifications/send`, `GET /admin/notifications/delivery-report`, `GET /admin/referrals`, `GET /admin/analytics/revenue`, `GET /admin/analytics/retention`, `GET /admin/exports/:type.csv`, `GET/PUT /admin/settings`, `GET/PUT /admin/roles`, `GET /admin/audit-logs`, `POST /admin/campaigns`, `GET /admin/campaigns/:id/delivery`, `GET /admin/support/tickets`, `POST /admin/support/tickets/:id/reply`, `POST /admin/support/tickets/:id/resolve`
