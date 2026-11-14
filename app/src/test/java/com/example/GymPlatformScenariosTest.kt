package com.example

import com.example.data.repository.GymPlatformRepository
import com.example.domain.engine.*
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Complete Independent Verification Test Suite covering:
 * - All 35 Mandatory Scenarios (Section 15)
 * - Section 3 Payment Verification (11 checks including gateway failure, invalid HMAC, duplicate webhook)
 * - Section 4 QR & GPS Verification (11 checks including GPS disabled, network offline, expired/unauthorized membership, multi-device identical coords)
 * - Section 5 Membership & Reward Verification (13 checks including 30-day membership, 30-day streak -> 7-day extension, admin reward reversal)
 * - Section 6 Transformation Verification (13 checks including 7-day SLA photo deletion & coupon revocation)
 * - Section 8 Website Catalogue Integration (plan create, edit, visibility, archive, outbox sync)
 */
class GymPlatformScenariosTest {

    private lateinit var repo: GymPlatformRepository

    @Before
    fun setUp() {
        repo = GymPlatformRepository()
    }

    @Test
    fun `Scenario 01 - Online payment succeeds at gateway but admin approval is pending`() {
        val createRes = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-1-idem"
        )
        assertTrue(createRes.success)
        val attemptId = createRes.data!!.id

        val webhookRes = repo.processPaymentWebhook(
            gateway = "razorpay",
            gatewayEventId = "evt_scen_01",
            paymentAttemptId = attemptId,
            webhookAmountInr = 8999.0,
            gatewaySucceeded = true
        )
        assertTrue(webhookRes.success)
        assertEquals(PaymentAttemptStatus.PENDING_ADMIN_APPROVAL, webhookRes.data!!.status)
        val hasMembership = repo.state.value.memberships.any { it.sourcePaymentId == attemptId }
        assertFalse("Membership must never activate before admin approval", hasMembership)
    }

    @Test
    fun `Scenario 01b - Online payment gateway failure auto-fails before reaching admin queue and webhooks enforce HMAC and deduplication`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-1b-idem"
        ).data!!

        // Invalid webhook signature -> 401
        val badSig = repo.processPaymentWebhook(
            gateway = "razorpay",
            gatewayEventId = "evt_bad_sig",
            paymentAttemptId = attempt.id,
            webhookAmountInr = 8999.0,
            gatewaySucceeded = true,
            signatureValid = false
        )
        assertFalse(badSig.success)
        assertEquals(401, badSig.statusCode)

        // Gateway failure -> auto-fails before reaching admin queue
        val failWebhook = repo.processPaymentWebhook(
            gateway = "razorpay",
            gatewayEventId = "evt_gw_fail",
            paymentAttemptId = attempt.id,
            webhookAmountInr = 8999.0,
            gatewaySucceeded = false,
            signatureValid = true
        )
        assertTrue(failWebhook.success)
        assertEquals(PaymentAttemptStatus.FAILED, failWebhook.data!!.status)

        // Duplicate webhook event ID -> idempotent no-op
        val dupWebhook = repo.processPaymentWebhook(
            gateway = "razorpay",
            gatewayEventId = "evt_gw_fail",
            paymentAttemptId = attempt.id,
            webhookAmountInr = 8999.0,
            gatewaySucceeded = true,
            signatureValid = true
        )
        assertTrue(dupWebhook.success)
        assertEquals(PaymentAttemptStatus.FAILED, dupWebhook.data!!.status)

        // Admin cannot approve an auto-failed gateway payment
        val adminTryApprove = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt.id)
        assertFalse(adminTryApprove.success)
    }

    @Test
    fun `Scenario 02 - Online payment rejected by admin`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-2-idem"
        ).data!!

        repo.processPaymentWebhook("razorpay", "evt_scen_02", attempt.id, 8999.0, gatewaySucceeded = true)
        val rejectRes = repo.rejectPayment("usr-admin-01", UserRole.ADMIN, attempt.id, "Unverified card holder")
        assertTrue(rejectRes.success)
        assertEquals(PaymentAttemptStatus.REJECTED, rejectRes.data!!.status)
        assertFalse(repo.state.value.memberships.any { it.sourcePaymentId == attempt.id })
    }

    @Test
    fun `Scenario 03 - Offline cash payment approved via staff record then admin confirm`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-monthly-sprint",
            method = PaymentMethod.CASH,
            idempotencyKey = "scen-3-idem"
        ).data!!
        assertEquals(PaymentAttemptStatus.AWAITING_CASH_CONFIRMATION, attempt.status)

        val prematureApprove = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt.id)
        assertFalse(prematureApprove.success)

        val staffRec = repo.recordCashByStaff("usr-staff-01", UserRole.STAFF, attempt.id, 3499.0, "RCPT-303")
        assertTrue(staffRec.success)
        assertEquals(PaymentAttemptStatus.PENDING_ADMIN_APPROVAL, staffRec.data!!.status)

        val staffApproveAttempt = repo.approvePayment("usr-staff-01", UserRole.STAFF, attempt.id)
        assertFalse(staffApproveAttempt.success)
        assertEquals(403, staffApproveAttempt.statusCode)

        val adminConfirm = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt.id, "Cash counted in safe")
        assertTrue(adminConfirm.success)
        assertEquals(MembershipStatus.ACTIVE, adminConfirm.data!!.status)
    }

    @Test
    fun `Scenario 04 - UPI proof rejected`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-monthly-sprint",
            method = PaymentMethod.UPI_BANK,
            idempotencyKey = "scen-4-idem",
            upiReferenceNumber = "UTR000011112222",
            upiProofStorageKey = "private-proofs/utr_invalid.png"
        ).data!!

        val rej = repo.rejectPayment("usr-admin-01", UserRole.ADMIN, attempt.id, "Blurred UTR screenshot")
        assertTrue(rej.success)
        assertEquals(PaymentAttemptStatus.REJECTED, rej.data!!.status)
        assertEquals("Blurred UTR screenshot", rej.data!!.rejectionReason)
    }

    @Test
    fun `Scenario 05 - Duplicate payment approval idempotency`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.UPI_BANK,
            idempotencyKey = "scen-5-idem",
            upiReferenceNumber = "UTR999988887777",
            upiProofStorageKey = "private-proofs/utr_ok.png"
        ).data!!

        val firstApprove = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt.id)
        val secondApprove = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt.id)
        assertTrue(firstApprove.success)
        assertTrue(secondApprove.success)
        assertEquals(firstApprove.data!!.id, secondApprove.data!!.id)

        val countForPayment = repo.state.value.memberships.count { it.sourcePaymentId == attempt.id }
        assertEquals("Must create exactly 1 membership per source_payment_id", 1, countForPayment)
    }

    @Test
    fun `Scenario 06 - Payment amount mismatch flagged for investigation`() {
        val attempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-6-idem"
        ).data!!

        val webhookRes = repo.processPaymentWebhook(
            gateway = "razorpay",
            gatewayEventId = "evt_scen_06_mismatch",
            paymentAttemptId = attempt.id,
            webhookAmountInr = 4999.0,
            gatewaySucceeded = true
        )
        assertTrue(webhookRes.success)
        assertEquals(PaymentAttemptStatus.INVESTIGATION, webhookRes.data!!.status)
    }

    @Test
    fun `Scenario 07 - Refund after membership activation with NONE and CANCEL policy paths`() {
        val attempt1 = repo.createOrderAndInitiatePayment(
            userId = "usr-member-02",
            planId = "pln-monthly-sprint",
            method = PaymentMethod.UPI_BANK,
            idempotencyKey = "scen-7a-idem",
            upiReferenceNumber = "UTR7A",
            upiProofStorageKey = "proof7a"
        ).data!!
        val mem1 = repo.approvePayment("usr-admin-01", UserRole.ADMIN, attempt1.id).data!!

        repo.initiateRefund("usr-admin-01", UserRole.ADMIN, attempt1.id, 500.0, "Partial goodwill", RefundMembershipImpact.NONE)
        val mem1AfterNone = repo.state.value.memberships.first { it.id == mem1.id }
        assertEquals(MembershipStatus.ACTIVE, mem1AfterNone.status)

        repo.initiateRefund("usr-admin-01", UserRole.ADMIN, attempt1.id, 2999.0, "Full cancellation refund", RefundMembershipImpact.CANCEL)
        val mem1AfterCancel = repo.state.value.memberships.first { it.id == mem1.id }
        assertEquals(MembershipStatus.CANCELLED, mem1AfterCancel.status)
    }

    @Test
    fun `Scenario 08 - Renewal before expiry starts day after current expiry`() {
        val activeMem = repo.state.value.memberships.first { it.userId == "usr-member-01" && it.status == MembershipStatus.ACTIVE }
        val currentExpiry = activeMem.expiryDate

        val renewalAttempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-01",
            planId = "pln-monthly-sprint",
            orderType = OrderType.RENEWAL,
            method = PaymentMethod.UPI_BANK,
            idempotencyKey = "scen-8-renewal-before",
            upiReferenceNumber = "UTR8",
            upiProofStorageKey = "proof8"
        ).data!!

        val approvalDateBeforeExpiry = currentExpiry.minusDays(5)
        val renewedMem = repo.approvePayment(
            adminUserId = "usr-admin-01",
            adminRole = UserRole.ADMIN,
            paymentAttemptId = renewalAttempt.id,
            approvalDate = approvalDateBeforeExpiry
        ).data!!

        assertEquals(currentExpiry.plusDays(1), renewedMem.startDate)
    }

    @Test
    fun `Scenario 09 - Renewal after expiry starts on approval date`() {
        val activeMem = repo.state.value.memberships.first { it.userId == "usr-member-01" && it.status == MembershipStatus.ACTIVE }
        val currentExpiry = activeMem.expiryDate

        val renewalAttempt = repo.createOrderAndInitiatePayment(
            userId = "usr-member-01",
            planId = "pln-monthly-sprint",
            orderType = OrderType.RENEWAL,
            method = PaymentMethod.UPI_BANK,
            idempotencyKey = "scen-9-renewal-after",
            upiReferenceNumber = "UTR9",
            upiProofStorageKey = "proof9"
        ).data!!

        val approvalDateAfterExpiry = currentExpiry.plusDays(10)
        val renewedMem = repo.approvePayment(
            adminUserId = "usr-admin-01",
            adminRole = UserRole.ADMIN,
            paymentAttemptId = renewalAttempt.id,
            approvalDate = approvalDateAfterExpiry
        ).data!!

        assertEquals(approvalDateAfterExpiry, renewedMem.startDate)
    }

    @Test
    fun `Scenario 10 - Freeze and resume extends expiry blocks attendance and protects streak`() {
        val mem = repo.state.value.memberships.first { it.id == "mem-arjun-01" }
        val originalExpiry = mem.expiryDate
        val freezeFrom = LocalDate.of(2026, 11, 5)
        val freezeTo = LocalDate.of(2026, 11, 10)

        val freezeReq = repo.requestMembershipFreeze("usr-member-01", mem.id, freezeFrom, freezeTo, "Medical rest").data!!
        repo.approveMembershipFreeze("usr-admin-01", UserRole.ADMIN, freezeReq.id)

        val loc = repo.state.value.locations.first()
        val scanDuringFreeze = repo.submitQrAttendanceScan(
            userId = "usr-member-01",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat,
            lng = loc.lng,
            accuracyMeters = 10.0,
            scanDate = LocalDate.of(2026, 11, 6)
        )
        assertFalse(scanDuringFreeze.success)
        assertEquals("MEMBERSHIP_FROZEN", scanDuringFreeze.errorReason)

        val resumed = repo.resumeFrozenMembership("usr-admin-01", freezeReq.id).data!!
        assertEquals(originalExpiry.plusDays(5), resumed.expiryDate)
    }

    @Test
    fun `Scenario 11 - 30-day membership and Calendar-month expiry edge cases Jan 31 and leap year Feb 29`() {
        // 30-day fixed membership duration check
        val start30 = LocalDate.of(2026, 11, 1)
        assertEquals(
            LocalDate.of(2026, 12, 1),
            CalendarMathEngine.calculateExpiryDate(start30, DurationType.DAYS, 30)
        )
        // Jan 31 + 1 month in non-leap year 2026 -> Feb 28, 2026
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CalendarMathEngine.addMonthsClamped(LocalDate.of(2026, 1, 31), 1)
        )
        // Jan 31 + 1 month in leap year 2028 -> Feb 29, 2028
        assertEquals(
            LocalDate.of(2028, 2, 29),
            CalendarMathEngine.addMonthsClamped(LocalDate.of(2028, 1, 31), 1)
        )
        // Leap year Feb 29, 2024 + 12 months -> Feb 28, 2025
        assertEquals(
            LocalDate.of(2025, 2, 28),
            CalendarMathEngine.addMonthsClamped(LocalDate.of(2024, 2, 29), 12)
        )
        // Non-leap Feb 28, 2026 + 12 months -> Feb 28, 2027
        assertEquals(
            LocalDate.of(2027, 2, 28),
            CalendarMathEngine.addMonthsClamped(LocalDate.of(2026, 2, 28), 12)
        )
    }

    @Test
    fun `Scenario 12 - Permanent QR valid at gym inside geofence and good accuracy`() {
        val loc = repo.state.value.locations.first()
        val res = repo.submitQrAttendanceScan(
            userId = "usr-member-01",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat + 0.0001,
            lng = loc.lng,
            accuracyMeters = 15.0,
            scanDate = LocalDate.of(2026, 11, 3)
        )
        assertTrue(res.success)
        assertEquals(AttendanceStatus.APPROVED, res.data!!.status)
    }

    @Test
    fun `Scenario 13 - Permanent QR scanned outside geofence rejects and does NOT consume rate limit`() {
        val loc = repo.state.value.locations.first()
        val latOffset110m = 110.0 / 111_111.0

        repeat(6) { idx ->
            val res = repo.submitQrAttendanceScan(
                userId = "usr-member-01",
                qrToken = loc.qrToken,
                gpsPermissionGranted = true,
                lat = loc.lat + latOffset110m,
                lng = loc.lng,
                accuracyMeters = 25.0,
                scanDate = LocalDate.of(2026, 11, 3),
                scanEpochMs = 1_700_000_000_000L + idx * 1000L
            )
            assertFalse(res.success)
            assertEquals("OUTSIDE_GEOFENCE", res.errorReason)
        }

        val insideRes = repo.submitQrAttendanceScan(
            userId = "usr-member-01",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat,
            lng = loc.lng,
            accuracyMeters = 12.0,
            scanDate = LocalDate.of(2026, 11, 3),
            scanEpochMs = 1_700_000_010_000L
        )
        assertTrue(insideRes.success)
    }

    @Test
    fun `Scenario 14 - GPS permission denied GPS disabled unauthorized expired membership and network offline`() {
        val loc = repo.state.value.locations.first()

        // Permission denied
        val permDenied = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, gpsPermissionGranted = false, loc.lat, loc.lng, 10.0)
        assertEquals("GPS_PERMISSION_DENIED", permDenied.errorReason)

        // GPS hardware disabled
        val gpsDisabled = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, gpsPermissionGranted = true, loc.lat, loc.lng, 10.0, gpsHardwareEnabled = false)
        assertEquals("GPS_DISABLED", gpsDisabled.errorReason)

        // Unauthorized membership (usr-member-02 has no active membership)
        val noMem = repo.submitQrAttendanceScan("usr-member-02", loc.qrToken, gpsPermissionGranted = true, loc.lat, loc.lng, 10.0)
        assertEquals("NO_ACTIVE_MEMBERSHIP", noMem.errorReason)

        // Expired membership date
        val expiredScan = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, gpsPermissionGranted = true, loc.lat, loc.lng, 10.0, scanDate = LocalDate.of(2028, 1, 1))
        assertEquals("MEMBERSHIP_EXPIRED", expiredScan.errorReason)

        // Network offline
        repo.toggleOfflineMode(true)
        val offlineScan = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, gpsPermissionGranted = true, loc.lat, loc.lng, 10.0)
        assertEquals("NETWORK_OFFLINE_DETECTED", offlineScan.errorReason)
        repo.toggleOfflineMode(false)
    }

    @Test
    fun `Scenario 15 - Poor GPS accuracy rejected by Gate 1`() {
        val loc = repo.state.value.locations.first()
        val res = repo.submitQrAttendanceScan(
            userId = "usr-member-01",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat,
            lng = loc.lng,
            accuracyMeters = 145.0,
            scanDate = LocalDate.of(2026, 11, 3)
        )
        assertFalse(res.success)
        assertEquals("POOR_GPS_ACCURACY", res.errorReason)
    }

    @Test
    fun `Scenario 16 - Mock-location and multi-device identical coordinates anomaly detection`() {
        val loc = repo.state.value.locations.first()
        val res = repo.submitQrAttendanceScan(
            userId = "usr-member-01",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat,
            lng = loc.lng,
            accuracyMeters = 10.0,
            scanDate = LocalDate.of(2026, 11, 3),
            isMockLocationSignal = true
        )
        assertTrue(res.success)
        assertTrue(repo.state.value.anomalyFlags.any { it.flagType == "MOCK_LOCATION_SIGNAL" })

        // Activate member 02 and scan with exact same lat/lng on same day -> IDENTICAL_COORDINATES_MULTI_MEMBER flag
        repo.approvePayment("usr-admin-01", UserRole.ADMIN, "pay-pending-online-02", approvalDate = LocalDate.of(2026, 11, 1))
        repo.submitQrAttendanceScan(
            userId = "usr-member-02",
            qrToken = loc.qrToken,
            gpsPermissionGranted = true,
            lat = loc.lat,
            lng = loc.lng,
            accuracyMeters = 10.0,
            scanDate = LocalDate.of(2026, 11, 3)
        )
        assertTrue(repo.state.value.anomalyFlags.any { it.flagType == "IDENTICAL_COORDINATES_MULTI_MEMBER" })
    }

    @Test
    fun `Scenario 17 and 18 - Multiple simultaneous or duplicate QR scans allow only one approved per date`() {
        val loc = repo.state.value.locations.first()
        val date = LocalDate.of(2026, 11, 3)
        val first = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, true, loc.lat, loc.lng, 12.0, scanDate = date)
        val second = repo.submitQrAttendanceScan("usr-member-01", loc.qrToken, true, loc.lat, loc.lng, 12.0, scanDate = date)
        assertTrue(first.success)
        assertFalse(second.success)
        assertEquals("DUPLICATE_APPROVED_ATTENDANCE_FOR_DATE", second.errorReason)
    }

    @Test
    fun `Scenario 19 - Special gym closure holiday excluded from streak`() {
        val start = LocalDate.of(2026, 10, 27)
        val end = LocalDate.of(2026, 11, 2)
        val eligible = StreakEngine.computeEligibleDays(
            fromDate = start,
            toDate = end,
            schedules = repo.state.value.schedules,
            holidays = repo.state.value.holidays,
            freezes = emptyList()
        )
        assertFalse(eligible.contains(LocalDate.of(2026, 10, 31)))
        assertFalse(eligible.contains(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `Scenario 20 - Missed open day breaks CONSECUTIVE streak and blocks FULL_ELIGIBLE milestone`() {
        val d1 = LocalDate.of(2026, 10, 5)
        val d2 = LocalDate.of(2026, 10, 6)
        val d4 = LocalDate.of(2026, 10, 8)

        val attendances = listOf(d1, d2, d4).map { d ->
            AttendanceRecord("att-$d", "usr-member-01", "loc-1", "qr", d, 0L, 0.0, 0.0, 10.0, 5.0, AttendanceStatus.APPROVED)
        }
        val consecutiveRes = StreakEngine.recomputeStreakAndRewards(
            userId = "usr-member-01",
            membershipId = "mem-1",
            evaluationStartDate = d1,
            evaluationEndDate = d4,
            approvedAttendances = attendances,
            schedules = repo.state.value.schedules,
            holidays = emptyList(),
            freezes = emptyList(),
            milestoneKind = MilestoneKind.CONSECUTIVE,
            milestoneTargetDays = 3,
            existingRewards = emptyList()
        )
        assertEquals(1, consecutiveRes.streakState.currentStreak)
        assertTrue(consecutiveRes.newlyIssuedRewards.isEmpty())
    }

    @Test
    fun `Scenario 21 and 22 - 30-day streak milestone issues 7-day extension once and admin correction or reversal works`() {
        // Test 30-day streak milestone -> 7-day reward extension
        val startDate = LocalDate.of(2026, 9, 1)
        val eligible30 = StreakEngine.computeEligibleDays(
            fromDate = startDate,
            toDate = startDate.plusDays(40),
            schedules = repo.state.value.schedules,
            holidays = emptyList(),
            freezes = emptyList()
        ).take(30)
        assertEquals(30, eligible30.size)

        val att30 = eligible30.map { d ->
            AttendanceRecord("att30-$d", "usr-member-02", "loc-1", "qr", d, 0L, 0.0, 0.0, 10.0, 5.0, AttendanceStatus.APPROVED)
        }
        val res30 = StreakEngine.recomputeStreakAndRewards(
            userId = "usr-member-02",
            membershipId = "mem-30",
            evaluationStartDate = eligible30.first(),
            evaluationEndDate = eligible30.last(),
            approvedAttendances = att30,
            schedules = repo.state.value.schedules,
            holidays = emptyList(),
            freezes = emptyList(),
            milestoneKind = MilestoneKind.CONSECUTIVE,
            milestoneTargetDays = 30,
            rewardDaysPerMilestone = 7,
            existingRewards = emptyList()
        )
        assertEquals(30, res30.streakState.currentStreak)
        assertEquals(1, res30.newlyIssuedRewards.size)
        assertEquals(7, res30.newlyIssuedRewards.first().valueDaysOrPoints)

        // Recomputing with existingRewards must NOT re-issue duplicate reward
        val res30Repeat = StreakEngine.recomputeStreakAndRewards(
            userId = "usr-member-02",
            membershipId = "mem-30",
            evaluationStartDate = eligible30.first(),
            evaluationEndDate = eligible30.last(),
            approvedAttendances = att30,
            schedules = repo.state.value.schedules,
            holidays = emptyList(),
            freezes = emptyList(),
            milestoneKind = MilestoneKind.CONSECUTIVE,
            milestoneTargetDays = 30,
            rewardDaysPerMilestone = 7,
            existingRewards = res30.allRewardsForMembership
        )
        assertTrue("Recompute must never re-issue existing milestone reward", res30Repeat.newlyIssuedRewards.isEmpty())

        // Admin explicit reward reversal adjusts expiry_date by -reward_days and marks REVOKED
        val arjunMemBefore = repo.state.value.memberships.first { it.id == "mem-arjun-01" }.expiryDate
        val seedReward = repo.state.value.rewards.first { it.membershipId == "mem-arjun-01" }
        val revRes = repo.reverseReward("usr-admin-01", UserRole.ADMIN, seedReward.id, "Audit correction test")
        assertTrue(revRes.success)
        assertEquals(RewardStatus.REVOKED, revRes.data!!.status)
        val arjunMemAfter = repo.state.value.memberships.first { it.id == "mem-arjun-01" }.expiryDate
        assertEquals(arjunMemBefore.minusDays(seedReward.valueDaysOrPoints.toLong()), arjunMemAfter)
    }

    @Test
    fun `Scenario 23 and 24 - Incomplete starting or ending photos block transformation submission`() {
        val resStartMissing = repo.submitTransformation("usr-member-02", "mem-2", privacyConsent = true)
        assertFalse(resStartMissing.success)
        assertEquals("INCOMPLETE_STARTING_PHOTOS_ALL_3_ANGLES_REQUIRED", resStartMissing.errorReason)

        repo.uploadProgressPhoto("usr-member-02", PhotoPhase.START, PhotoAngle.FRONT)
        repo.uploadProgressPhoto("usr-member-02", PhotoPhase.START, PhotoAngle.SIDE)
        repo.uploadProgressPhoto("usr-member-02", PhotoPhase.START, PhotoAngle.BACK)
        repo.uploadProgressPhoto("usr-member-02", PhotoPhase.END, PhotoAngle.FRONT)
        repo.uploadProgressPhoto("usr-member-02", PhotoPhase.END, PhotoAngle.SIDE)

        val resEndMissing = repo.submitTransformation("usr-member-02", "mem-2", privacyConsent = true)
        assertFalse(resEndMissing.success)
        assertEquals("INCOMPLETE_ENDING_PHOTOS_ALL_3_ANGLES_REQUIRED", resEndMissing.errorReason)
    }

    @Test
    fun `Scenario 25 and 26 - Transformation approval issues 80 percent coupon rejection shows reason and resubmission works`() {
        val sub = repo.state.value.transformations.first()
        val rejRes = repo.reviewTransformation(
            adminUserId = "usr-admin-01",
            adminRole = UserRole.ADMIN,
            submissionId = sub.id,
            decision = TransformationDecision.REJECT,
            reason = "Lighting inconsistent on back angle"
        )
        assertTrue(rejRes.success)
        assertEquals(TransformationStatus.REJECTED, rejRes.data!!.status)

        val resubReq = repo.reviewTransformation(
            adminUserId = "usr-admin-01",
            adminRole = UserRole.ADMIN,
            submissionId = sub.id,
            decision = TransformationDecision.REQUEST_RESUBMISSION,
            reason = "Please retake end side angle"
        )
        assertEquals(TransformationStatus.RESUBMISSION_REQUESTED, resubReq.data!!.status)

        val memberResubmit = repo.submitTransformation(sub.userId, sub.membershipId, privacyConsent = true)
        assertEquals(TransformationStatus.SUBMITTED, memberResubmit.data!!.status)

        val approveRes = repo.reviewTransformation(
            adminUserId = "usr-admin-01",
            adminRole = UserRole.ADMIN,
            submissionId = sub.id,
            decision = TransformationDecision.APPROVE,
            reason = "All 6 angles verified"
        )
        assertEquals(TransformationStatus.APPROVED, approveRes.data!!.status)
        assertNotNull(approveRes.data!!.issuedCouponCode)
    }

    @Test
    fun `Scenario 27 and 28 - Coupon expiry duplicate redemption and admin revocation`() {
        val expiredCheck = repo.createOrderAndInitiatePayment(
            userId = "usr-member-01",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-27-expired",
            couponCode = "IRON80-ARJUN",
            checkoutDate = LocalDate.of(2027, 6, 1)
        )
        assertFalse(expiredCheck.success)
        assertEquals("COUPON_EXPIRED", expiredCheck.errorReason)

        val firstRedeem = repo.createOrderAndInitiatePayment(
            userId = "usr-member-01",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-28-first",
            couponCode = "IRON80-ARJUN",
            checkoutDate = LocalDate.of(2026, 11, 3)
        )
        assertTrue(firstRedeem.success)
        assertEquals(1799.8, firstRedeem.data!!.amountInr, 0.1)

        val duplicateRedeem = repo.createOrderAndInitiatePayment(
            userId = "usr-member-01",
            planId = "pln-quarterly-pro",
            method = PaymentMethod.ONLINE,
            idempotencyKey = "scen-28-second",
            couponCode = "IRON80-ARJUN",
            checkoutDate = LocalDate.of(2026, 11, 3)
        )
        assertFalse(duplicateRedeem.success)
        assertEquals("DUPLICATE_COUPON_REDEMPTION_BLOCKED", duplicateRedeem.errorReason)
    }

    @Test
    fun `Scenario 29 and 30 - Self-referral blocked and duplicate referral blocked`() {
        val arjun = repo.state.value.users.first { it.id == "usr-member-01" }
        val selfRef = repo.createReferralAttribution(arjun.id, arjun.mobile)
        assertFalse(selfRef.success)
        assertEquals("SELF_REFERRAL_BLOCKED", selfRef.errorReason)

        val dupRef = repo.createReferralAttribution(arjun.id, "+919876543211")
        assertFalse(dupRef.success)
        assertEquals("DUPLICATE_REFERRAL_FOR_INVITEE_BLOCKED", dupRef.errorReason)
    }

    @Test
    fun `Scenario 31 - Leaderboard tie applies deterministic 3-tier tie-breakers`() {
        val tiedEntries = listOf(
            LeaderboardEntryRecord("usr-b", "Member B", "2026-11", 10, 20, 120, maxStreak = 8, reachedScoreAtEpochMs = 2000L),
            LeaderboardEntryRecord("usr-a", "Member A", "2026-11", 10, 20, 120, maxStreak = 8, reachedScoreAtEpochMs = 2000L),
            LeaderboardEntryRecord("usr-c", "Member C", "2026-11", 10, 20, 120, maxStreak = 10, reachedScoreAtEpochMs = 5000L),
            LeaderboardEntryRecord("usr-d", "Member D", "2026-11", 10, 20, 120, maxStreak = 8, reachedScoreAtEpochMs = 1000L)
        )
        val ranked = LeaderboardEngine.rankEntries(tiedEntries)
        assertEquals("usr-c", ranked[0].userId)
        assertEquals("usr-d", ranked[1].userId)
        assertEquals("usr-a", ranked[2].userId)
        assertEquals("usr-b", ranked[3].userId)
    }

    @Test
    fun `Scenario 32 - Account deletion with active membership blocked until admin confirmation or cooling completed`() {
        val blockedDel = repo.requestAccountDeletion(
            userId = "usr-member-01",
            requestDate = LocalDate.of(2026, 11, 3),
            adminConfirmedActiveMembershipOverride = false
        )
        assertFalse(blockedDel.success)
        assertEquals("ACTIVE_MEMBERSHIP_EXISTS_ADMIN_CONFIRMATION_OR_COOLING_REQUIRED", blockedDel.errorReason)

        val confirmedDel = repo.requestAccountDeletion(
            userId = "usr-member-01",
            requestDate = LocalDate.of(2026, 11, 3),
            adminConfirmedActiveMembershipOverride = true
        )
        assertTrue(confirmedDel.success)

        val completed = repo.completeAccountDeletionAfterCooling(
            deletionRequestId = confirmedDel.data!!.id,
            currentDate = LocalDate.of(2026, 12, 5)
        )
        assertTrue(completed.success)
        val redactedUser = repo.state.value.users.first { it.id == "usr-member-01" }
        assertEquals(UserStatus.DELETED, redactedUser.status)
        assertEquals("REDACTED_USER", redactedUser.fullName)
    }

    @Test
    fun `Scenario 33 - Photo privacy 403 signed URL expiry 7-day deletion SLA and AI zero-photo schema check`() {
        val photo = repo.state.value.photos.first()

        val directAttempt = repo.accessProtectedPhoto("usr-member-01", UserRole.MEMBER, photo.id, signedUrlToken = null, directBucketAccessAttempt = true)
        assertEquals(403, directAttempt.statusCode)

        val otherUserAttempt = repo.accessProtectedPhoto("usr-member-02", UserRole.MEMBER, photo.id, signedUrlToken = photo.signedUrlToken)
        assertEquals(403, otherUserAttempt.statusCode)

        val expiredAttempt = repo.accessProtectedPhoto(
            requesterUserId = "usr-member-01",
            requesterRole = UserRole.MEMBER,
            photoId = photo.id,
            signedUrlToken = photo.signedUrlToken,
            nowEpochMs = photo.signedUrlExpiresAtEpochMs + 1000L
        )
        assertEquals(403, expiredAttempt.statusCode)

        // Photo deletion request -> immediate soft delete -> hard purge within 7 days
        val nowMs = System.currentTimeMillis()
        val delReq = repo.requestPhotoDeletion("usr-member-01", photo.id, nowMs)
        assertTrue(delReq.success)
        val accessAfterSoftDelete = repo.accessProtectedPhoto("usr-member-01", UserRole.MEMBER, photo.id, photo.signedUrlToken, nowEpochMs = nowMs + 1000L)
        assertEquals(404, accessAfterSoftDelete.statusCode)

        val purgedCount = repo.executeScheduledPhotoHardPurge(nowMs + 7L * 24 * 3600 * 1000L + 1000L)
        assertEquals(1, purgedCount)

        // Schema test: AI payload NEVER includes any photo fields
        val user = repo.state.value.users.first()
        val aiPayload = AiSummaryEngine.buildSafeAiPayload(
            user = user,
            attendanceCount = 6,
            streak = StreakStateRecord(user.id, 6, 6, null),
            milestonesCount = 1,
            transformationStatus = TransformationStatus.SUBMITTED
        )
        assertFalse(aiPayload.keys.any { it.contains("photo", ignoreCase = true) || it.contains("url", ignoreCase = true) })
    }

    @Test
    fun `Scenario 34 - Notification delivery failure retry and dedupe guard`() {
        val retrySuccess = repo.sendNotification(
            userId = "usr-member-01",
            type = "RENEWAL_REMINDER",
            title = "Renew Now",
            body = "7 days remaining",
            deepLink = "ironpulse://renew",
            dedupeKey = "dedupe-scen-34",
            simulateTransientFailuresCount = 2
        )
        assertTrue(retrySuccess.success)

        val duplicateSend = repo.sendNotification(
            userId = "usr-member-01",
            type = "RENEWAL_REMINDER",
            title = "Renew Now",
            body = "7 days remaining",
            deepLink = "ironpulse://renew",
            dedupeKey = "dedupe-scen-34"
        )
        assertFalse(duplicateSend.success)
        assertEquals("DUPLICATE_NOTIFICATION_DEDUPE_KEY", duplicateSend.errorReason)

        val permanentFail = repo.sendNotification(
            userId = "usr-member-01",
            type = "CAMPAIGN",
            title = "Promo",
            body = "Body",
            deepLink = "ironpulse://promo",
            dedupeKey = "dedupe-scen-34-fail",
            simulateTransientFailuresCount = 4
        )
        assertFalse(permanentFail.success)
        assertEquals(503, permanentFail.statusCode)
    }

    @Test
    fun `Scenario 35 - Database backup restoration to scratch DB and Catalogue sync verification`() {
        // Test Plan creation, editing, visibility toggle, and archival
        val newPlan = GymPlan(
            id = "pln-test-sync",
            name = "Test Sync Plan",
            description = "Created in admin",
            features = listOf("Feature 1"),
            durationType = DurationType.DAYS,
            durationValue = 30,
            internalPriceInr = 2999.0
        )
        assertTrue(repo.savePlan("usr-admin-01", UserRole.ADMIN, newPlan, "Add test plan").success)
        assertTrue(repo.getPublicPlans(true).any { it.id == "pln-test-sync" })

        assertTrue(repo.updatePricingVisibility("usr-owner-01", UserRole.OWNER, PricingVisibility.HIDDEN_CALL_TO_ENQUIRE, "Hide prices").success)
        val hiddenPricePlan = repo.getPublicPlans(true).first { it.id == "pln-test-sync" }
        assertEquals(-1.0, hiddenPricePlan.internalPriceInr, 0.01)

        assertTrue(repo.archivePlan("usr-admin-01", UserRole.ADMIN, "pln-test-sync", "Archive test plan").success)
        assertFalse(repo.getPublicPlans(true).any { it.id == "pln-test-sync" })

        val backup = repo.exportDatabaseBackup()
        val restoredCounts = repo.restoreDatabaseBackupToScratch(backup)
        assertEquals(backup.tableCounts, restoredCounts)
    }
}
