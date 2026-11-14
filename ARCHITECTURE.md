# System Architecture (`ARCHITECTURE.md`)

## 1. Layered Clean Architecture
1. **UI Layer (`com.example.ui`)**:
   - **Member Experience (37 Screens)**: Mobile-first Jetpack Compose screens with skeleton loading, empty states, retryable error states, offline detection, and `Modifier.testTag` coverage.
   - **Admin & Staff Console (32 Screens)**: Responsive desktop/tablet-ready screens with sortable data tables, sticky filter headers, bulk actions, and mandatory typed-reason confirmation modals.
2. **Domain & Policy Engines (`com.example.domain`)**:
   - `GeofenceEngine`: Haversine calculation + Dual-Gate validation (`distance <= radius && accuracy <= maxAccuracy`, plus optional `strictMode: distance + accuracy <= radius`) + Anomaly Detector.
   - `CalendarMathEngine`: Deterministic `addMonthsClamped(LocalDate, Int)` handling Jan 31 → Feb 28/29 and leap-year transitions in `Asia/Kolkata`.
   - `StreakEngine`: Deterministic walk over `eligible_days` (excluding `holidays` and closed `location_schedules`), idempotent milestone reward issuance, and safe retroactive recomputation.
   - `AiSummaryPayloadBuilder`: Enforces strict privacy omission—constructs AI payloads exclusively from first name, weight numbers, attendance counts, and streak state (verified by automated schema tests to contain zero photo fields).
3. **API Gateway & Repository Layer (`com.example.data`)**:
   - Implements `/api/v1` endpoints with JWT audience enforcement (`member`, `admin`, `staff`), RBAC permission checks, `Idempotency-Key` deduplication, and SQLite/Room transactional integrity.
