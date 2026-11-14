# Production Operations & Incident Runbook (`RUNBOOK.md`)

## 1. Payment & Webhook Reconciliation
- **Symptom**: Customer paid online, gateway succeeded, but membership is not active.
- **Resolution**: By design, online gateway success moves the attempt to `PENDING_ADMIN_APPROVAL`. Open **Admin Console → Payment Approval Centre → Pending**, verify the gateway reference, and click **Approve**.
- **Amount Mismatch**: Automatically routed to `INVESTIGATION` status. Admin must inspect `payment_webhooks` raw payload before issuing a refund or manual override.

## 2. Attendance & Geofence Troubleshooting
- **Symptom**: Member receives `OUTSIDE_GEOFENCE` or `POOR_GPS_ACCURACY`.
- **Resolution**:
  - `OUTSIDE_GEOFENCE` does NOT consume the 5/hour scan rate limit. Ask the member to step closer to the gym entrance and rescan.
  - If device GPS hardware is malfunctioning while the member is physically present, an Admin can create an **Attendance Correction** (`is_correction = true`, mandatory reason), which automatically enqueues a deterministic streak recompute.

## 3. Quarterly Backup & Scratch Restore Drill
- Automated daily snapshot + 7-day point-in-time recovery.
- Run the automated backup restoration verification test (`Scenario 35` in `GymPlatformScenariosTest`) to snapshot all table counts, restore into a clean scratch database, and assert 100% row and constraint parity.
