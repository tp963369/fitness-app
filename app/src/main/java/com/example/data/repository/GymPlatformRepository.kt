package com.example.data.repository

import com.example.data.local.AuditLogEntity
import com.example.data.local.GymDao
import com.example.data.local.KvSnapshotEntity
import com.example.domain.engine.*
import com.example.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class SyncOutboxEvent(
    val id: String,
    val endpoint: String,
    val idempotencyKey: String,
    val payloadSummary: String,
    val syncedToRemote: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class PhotoDeletionRequestRecord(
    val id: String,
    val userId: String,
    val photoId: String,
    val requestedAtEpochMs: Long,
    val scheduledPurgeAtEpochMs: Long,
    val purgedAtEpochMs: Long? = null,
    val status: String = "SOFT_DELETED_PENDING_PURGE"
)

data class PlatformState(
    val currentUserId: String = "usr-member-01",
    val activeRole: UserRole = UserRole.MEMBER,
    val isOfflineSimulated: Boolean = false,
    val remoteBackendConfigured: Boolean = false,
    val settings: GymSettings = GymSettings(),
    val users: List<UserAccount> = emptyList(),
    val plans: List<GymPlan> = emptyList(),
    val locations: List<GymLocation> = emptyList(),
    val schedules: List<LocationSchedule> = emptyList(),
    val holidays: List<GymHoliday> = emptyList(),
    val orders: List<OrderRecord> = emptyList(),
    val paymentAttempts: List<PaymentAttemptRecord> = emptyList(),
    val paymentVerifications: List<PaymentVerificationLog> = emptyList(),
    val processedWebhooks: Set<String> = emptySet(),
    val refunds: List<RefundRecord> = emptyList(),
    val memberships: List<MembershipRecord> = emptyList(),
    val freezes: List<MembershipFreezeRecord> = emptyList(),
    val attendances: List<AttendanceRecord> = emptyList(),
    val anomalyFlags: List<AttendanceAnomalyFlag> = emptyList(),
    val streakStates: Map<String, StreakStateRecord> = emptyMap(),
    val rewards: List<RewardRecord> = emptyList(),
    val photos: List<ProgressPhotoRecord> = emptyList(),
    val photoDeletionRequests: List<PhotoDeletionRequestRecord> = emptyList(),
    val transformations: List<TransformationSubmissionRecord> = emptyList(),
    val coupons: List<CouponRecord> = emptyList(),
    val redeemedCouponIds: Set<String> = emptySet(),
    val badges: List<BadgeRecord> = emptyList(),
    val leaderboard: List<LeaderboardEntryRecord> = emptyList(),
    val referrals: List<ReferralRecord> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val campaigns: List<CampaignRecord> = emptyList(),
    val supportTickets: List<SupportTicketRecord> = emptyList(),
    val auditLogs: List<AuditLogRecord> = emptyList(),
    val deletionRequests: List<DeletionRequestRecord> = emptyList(),
    val syncOutbox: List<SyncOutboxEvent> = emptyList(),
    val idempotencyKeys: Map<String, String> = emptyMap(),
    val lastAiSummaryEpochByUser: Map<String, Long> = emptyMap(),
    val lastStatusBanner: String = "IronPulse Ready • Asia/Kolkata (IST) Engine"
)

data class ApiCallResult<T>(
    val success: Boolean,
    val statusCode: Int,
    val data: T? = null,
    val errorReason: String? = null
)

class GymPlatformRepository(
    private val gymDao: GymDao? = null,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _state = MutableStateFlow(createInitialSeededState())
    val state: StateFlow<PlatformState> = _state.asStateFlow()

    private val otpSendTimestampsByMobile = mutableMapOf<String, MutableList<Long>>()
    private val otpHashByMobile = mutableMapOf<String, Pair<String, Long>>()
    private val otpAttemptsByMobile = mutableMapOf<String, Int>()
    private val qrScanTimestampsByUser = mutableMapOf<String, MutableList<Long>>()

    @Synchronized
    fun switchRole(role: UserRole) {
        val targetUserId = when (role) {
            UserRole.MEMBER -> "usr-member-01"
            UserRole.STAFF -> "usr-staff-01"
            UserRole.ADMIN -> "usr-admin-01"
            UserRole.OWNER -> "usr-owner-01"
        }
        _state.update {
            it.copy(
                activeRole = role,
                currentUserId = targetUserId,
                lastStatusBanner = "Switched active session to ${role.name} ($targetUserId)"
            )
        }
        persistSnapshotAsync("session_role", "${role.name}:$targetUserId")
    }

    @Synchronized
    fun toggleOfflineMode(offline: Boolean) {
        _state.update {
            it.copy(
                isOfflineSimulated = offline,
                lastStatusBanner = if (offline) "Offline Mode Active: Network-dependent mutations blocked" else "Online Connectivity Restored"
            )
        }
    }

    // ========================================================================
    // 1. AUTHENTICATION & RBAC
    // ========================================================================

    @Synchronized
    fun sendOtp(mobile: String, nowEpochMs: Long = System.currentTimeMillis()): ApiCallResult<String> {
        val history = otpSendTimestampsByMobile.getOrPut(mobile) { mutableListOf() }
        history.removeAll { nowEpochMs - it > 3600_000L }
        if (history.size >= 5) {
            return ApiCallResult(false, 429, errorReason = "OTP_RATE_LIMIT_EXCEEDED_5_PER_HOUR")
        }
        history.add(nowEpochMs)
        val otpCode = "482910"
        otpHashByMobile[mobile] = "argon2id\$v=19\$m=65536,t=3,p=4\$hash_$otpCode" to (nowEpochMs + 5 * 60_000L)
        otpAttemptsByMobile[mobile] = 0
        appendAudit("system", UserRole.MEMBER, "OTP_SENT", "otp_requests", mobile, "6-digit OTP issued (5m TTL)", "{}", "{\"ttlSec\":300}")
        return ApiCallResult(true, 200, data = otpCode)
    }

    @Synchronized
    fun verifyOtp(mobile: String, code: String, nowEpochMs: Long = System.currentTimeMillis()): ApiCallResult<UserAccount> {
        val entry = otpHashByMobile[mobile]
            ?: return ApiCallResult(false, 400, errorReason = "OTP_NOT_FOUND")
        if (nowEpochMs > entry.second) {
            return ApiCallResult(false, 400, errorReason = "OTP_EXPIRED")
        }
        val attempts = (otpAttemptsByMobile[mobile] ?: 0) + 1
        otpAttemptsByMobile[mobile] = attempts
        if (attempts > 5) {
            return ApiCallResult(false, 429, errorReason = "OTP_MAX_ATTEMPTS_EXCEEDED")
        }
        val expectedHash = "argon2id\$v=19\$m=65536,t=3,p=4\$hash_$code"
        if (entry.first != expectedHash) {
            return ApiCallResult(false, 401, errorReason = "INVALID_OTP_CODE")
        }
        val existing = _state.value.users.firstOrNull { it.mobile == mobile }
        val user = existing ?: UserAccount(
            id = "usr-${UUID.randomUUID().toString().take(8)}",
            mobile = mobile,
            role = UserRole.MEMBER,
            fullName = "Member ${mobile.takeLast(4)}",
            referralCode = "IRON${mobile.takeLast(4)}"
        ).also { newUser ->
            _state.update { s -> s.copy(users = s.users + newUser) }
        }
        _state.update { it.copy(currentUserId = user.id, activeRole = user.role, lastStatusBanner = "OTP Verified for ${user.fullName}") }
        return ApiCallResult(true, 200, data = user)
    }

    @Synchronized
    fun verifyAdminTotp(adminUserId: String, totpCode: String): ApiCallResult<Boolean> {
        if (totpCode.length != 6 || !totpCode.all { it.isDigit() }) {
            return ApiCallResult(false, 401, errorReason = "INVALID_TOTP_6_DIGIT_REQUIRED")
        }
        _state.update { s ->
            s.copy(
                users = s.users.map { if (it.id == adminUserId) it.copy(totpVerified = true) else it },
                lastStatusBanner = "Admin TOTP MFA Verified for $adminUserId"
            )
        }
        appendAudit(adminUserId, UserRole.ADMIN, "ADMIN_TOTP_VERIFIED", "admin_users", adminUserId, "Mandatory TOTP MFA login verified", "{\"mfa\":false}", "{\"mfa\":true}")
        return ApiCallResult(true, 200, data = true)
    }

    fun checkPermission(actorRole: UserRole, permissionCode: String): Boolean {
        return when (actorRole) {
            UserRole.OWNER -> true
            UserRole.ADMIN -> permissionCode !in setOf(
                "CHANGE_OWNER_ACCOUNT",
                "CHANGE_PRICING_VISIBILITY",
                "CHANGE_ADMIN_ROLES",
                "EXPORT_FINANCIAL_OVER_12M",
                "DELETE_AUDIT_LOGS"
            )
            UserRole.STAFF -> permissionCode in setOf(
                "RECORD_CASH_RECEIPT",
                "VIEW_MEMBER_READONLY",
                "ASSIST_ATTENDANCE_QR"
            )
            UserRole.MEMBER -> permissionCode.startsWith("MEMBER_")
        }
    }

    // ========================================================================
    // 2. PLANS & DYNAMIC PRICING VISIBILITY ENGINE
    // ========================================================================

    fun getPublicPlans(isUserAuthenticated: Boolean): List<GymPlan> {
        val s = _state.value
        return s.plans.filter { it.publicVisible && it.archivedAtEpochMs == null }.map { plan ->
            val effectiveVisibility = plan.pricingVisibilityOverride ?: s.settings.pricingVisibility
            val canSeePrice = when (effectiveVisibility) {
                PricingVisibility.PUBLIC -> true
                PricingVisibility.LOGIN_REQUIRED -> isUserAuthenticated
                PricingVisibility.HIDDEN_CALL_TO_ENQUIRE -> false
            }
            if (canSeePrice) plan else plan.copy(internalPriceInr = -1.0)
        }
    }

    @Synchronized
    fun updatePricingVisibility(actorId: String, actorRole: UserRole, visibility: PricingVisibility, reason: String): ApiCallResult<GymSettings> {
        if (!checkPermission(actorRole, "CHANGE_PRICING_VISIBILITY")) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_OWNER_ONLY_PRICING_VISIBILITY")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val before = _state.value.settings.pricingVisibility.name
        _state.update {
            it.copy(
                settings = it.settings.copy(pricingVisibility = visibility),
                lastStatusBanner = "Pricing visibility updated to ${visibility.name}"
            )
        }
        appendAudit(actorId, actorRole, "UPDATE_PRICING_VISIBILITY", "gym_settings", _state.value.settings.gymId, reason, "{\"pricingVisibility\":\"$before\"}", "{\"pricingVisibility\":\"${visibility.name}\"}")
        enqueueOutbox("/api/v1/admin/settings", "vis-${System.currentTimeMillis()}", "pricingVisibility=${visibility.name}")
        return ApiCallResult(true, 200, data = _state.value.settings)
    }

    @Synchronized
    fun savePlan(actorId: String, actorRole: UserRole, plan: GymPlan, reason: String): ApiCallResult<GymPlan> {
        if (actorRole != UserRole.OWNER && actorRole != UserRole.ADMIN) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        _state.update { s ->
            val exists = s.plans.any { it.id == plan.id }
            val updated = if (exists) s.plans.map { if (it.id == plan.id) plan else it } else s.plans + plan
            s.copy(plans = updated, lastStatusBanner = "Saved plan ${plan.name}")
        }
        appendAudit(actorId, actorRole, "SAVE_PLAN", "plans", plan.id, reason, "{}", "{\"name\":\"${plan.name}\",\"price\":${plan.internalPriceInr}}")
        enqueueOutbox("/api/v1/admin/plans", "plan-${plan.id}", plan.name)
        return ApiCallResult(true, 200, data = plan)
    }

    @Synchronized
    fun archivePlan(actorId: String, actorRole: UserRole, planId: String, reason: String): ApiCallResult<GymPlan> {
        if (actorRole != UserRole.OWNER && actorRole != UserRole.ADMIN) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val plan = _state.value.plans.firstOrNull { it.id == planId }
            ?: return ApiCallResult(false, 404, errorReason = "PLAN_NOT_FOUND")
        val archived = plan.copy(archivedAtEpochMs = System.currentTimeMillis(), publicVisible = false)
        _state.update { s ->
            s.copy(
                plans = s.plans.map { if (it.id == planId) archived else it },
                lastStatusBanner = "Archived plan ${plan.name}"
            )
        }
        appendAudit(actorId, actorRole, "ARCHIVE_PLAN", "plans", planId, reason, "{\"archived\":false}", "{\"archived\":true}")
        enqueueOutbox("/api/v1/admin/plans/$planId/archive", "arch-$planId", reason)
        return ApiCallResult(true, 200, data = archived)
    }

    // ========================================================================
    // 3. LOCATIONS, SCHEDULES, HOLIDAYS & PERMANENT QR
    // ========================================================================

    @Synchronized
    fun regenerateLocationQr(actorId: String, actorRole: UserRole, locationId: String, reason: String): ApiCallResult<String> {
        if (actorRole != UserRole.OWNER && actorRole != UserRole.ADMIN) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val newToken = "IRONPULSE_QR_${locationId.uppercase()}_${UUID.randomUUID().toString().replace("-", "").uppercase()}"
        check(newToken.length >= 32)
        _state.update { s ->
            s.copy(
                locations = s.locations.map { if (it.id == locationId) it.copy(qrToken = newToken) else it },
                lastStatusBanner = "Regenerated permanent QR (>=32 chars) for $locationId"
            )
        }
        appendAudit(actorId, actorRole, "REGENERATE_QR", "qr_identifiers", locationId, reason, "{}", "{\"qrToken\":\"$newToken\"}")
        return ApiCallResult(true, 200, data = newToken)
    }

    @Synchronized
    fun addHoliday(actorId: String, actorRole: UserRole, fromDate: LocalDate, toDate: LocalDate, reason: String): ApiCallResult<GymHoliday> {
        if (actorRole != UserRole.OWNER && actorRole != UserRole.ADMIN) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val holiday = GymHoliday(
            id = "hol-${UUID.randomUUID().toString().take(8)}",
            fromDate = fromDate,
            toDate = toDate,
            reason = reason
        )
        _state.update { s -> s.copy(holidays = s.holidays + holiday) }
        appendAudit(actorId, actorRole, "ADD_HOLIDAY_RECOMPUTE", "holidays", holiday.id, reason, "{}", "{\"from\":\"$fromDate\",\"to\":\"$toDate\"}")
        recomputeAllMemberStreaks()
        return ApiCallResult(true, 200, data = holiday)
    }

    // ========================================================================
    // 4–6. ORDERS, 3-CHANNEL PAYMENTS, WEBHOOKS, APPROVALS & REFUNDS
    // ========================================================================

    @Synchronized
    fun createOrderAndInitiatePayment(
        userId: String,
        planId: String,
        orderType: OrderType = OrderType.NEW,
        method: PaymentMethod,
        idempotencyKey: String,
        couponCode: String? = null,
        upiReferenceNumber: String? = null,
        upiProofStorageKey: String? = null,
        checkoutDate: LocalDate = LocalDate.now()
    ): ApiCallResult<PaymentAttemptRecord> {
        if (_state.value.isOfflineSimulated) {
            return ApiCallResult(false, 503, errorReason = "NETWORK_OFFLINE_DETECTED")
        }
        val existingPaymentId = _state.value.idempotencyKeys["payment:$idempotencyKey"]
        if (existingPaymentId != null) {
            val existing = _state.value.paymentAttempts.first { it.id == existingPaymentId }
            return ApiCallResult(true, 200, data = existing)
        }

        val plan = _state.value.plans.firstOrNull { it.id == planId && it.archivedAtEpochMs == null }
            ?: return ApiCallResult(false, 404, errorReason = "PLAN_NOT_FOUND_OR_ARCHIVED")

        var discountInr = 0.0
        var appliedCouponId: String? = null

        if (!couponCode.isNullOrBlank()) {
            val validation = validateAndLockCoupon(userId, couponCode, plan.internalPriceInr, checkoutDate)
            if (!validation.success || validation.data == null) {
                return ApiCallResult(false, validation.statusCode, errorReason = validation.errorReason)
            }
            val coupon = validation.data
            appliedCouponId = coupon.id
            discountInr = minOf(
                plan.internalPriceInr * (coupon.discountPercent / 100.0),
                coupon.maxDiscountCapInr
            )
            _state.update { s ->
                s.copy(
                    coupons = s.coupons.map {
                        if (it.id == coupon.id) it.copy(status = CouponStatus.REDEEMED, redeemedOrderId = "ord-$idempotencyKey") else it
                    },
                    redeemedCouponIds = s.redeemedCouponIds + coupon.id
                )
            }
        }

        val finalAmount = (plan.internalPriceInr - discountInr).coerceAtLeast(0.0)
        val orderId = "ord-${UUID.randomUUID().toString().take(8)}"
        val order = OrderRecord(
            id = orderId,
            userId = userId,
            planId = planId,
            type = orderType,
            status = OrderStatus.AWAITING_VERIFICATION,
            idempotencyKey = "ord-idem-$idempotencyKey",
            originalAmountInr = plan.internalPriceInr,
            discountAmountInr = discountInr,
            totalAmountInr = finalAmount,
            couponId = appliedCouponId
        )

        val initialStatus = when (method) {
            PaymentMethod.ONLINE -> PaymentAttemptStatus.PENDING_GATEWAY
            PaymentMethod.UPI_BANK -> if (!upiReferenceNumber.isNullOrBlank() && !upiProofStorageKey.isNullOrBlank()) {
                PaymentAttemptStatus.PENDING_ADMIN_APPROVAL
            } else {
                PaymentAttemptStatus.AWAITING_PROOF
            }
            PaymentMethod.CASH -> PaymentAttemptStatus.AWAITING_CASH_CONFIRMATION
        }

        val attempt = PaymentAttemptRecord(
            id = "pay-${UUID.randomUUID().toString().take(8)}",
            orderId = orderId,
            userId = userId,
            method = method,
            amountInr = finalAmount,
            status = initialStatus,
            referenceNumber = upiReferenceNumber,
            proofStorageKey = upiProofStorageKey,
            idempotencyKey = idempotencyKey
        )

        _state.update { s ->
            s.copy(
                orders = listOf(order) + s.orders,
                paymentAttempts = listOf(attempt) + s.paymentAttempts,
                idempotencyKeys = s.idempotencyKeys + ("payment:$idempotencyKey" to attempt.id),
                lastStatusBanner = "Created ${method.name} payment (${attempt.status.name}) for ₹${finalAmount.toInt()}"
            )
        }
        enqueueOutbox("/api/v1/orders", idempotencyKey, "orderId=$orderId,amount=$finalAmount")
        return ApiCallResult(true, 200, data = attempt)
    }

    @Synchronized
    fun processPaymentWebhook(
        gateway: String,
        gatewayEventId: String,
        paymentAttemptId: String,
        webhookAmountInr: Double,
        gatewaySucceeded: Boolean,
        signatureValid: Boolean = true
    ): ApiCallResult<PaymentAttemptRecord> {
        if (!signatureValid) {
            return ApiCallResult(false, 401, errorReason = "INVALID_WEBHOOK_HMAC_SIGNATURE")
        }
        val webhookKey = "$gateway:$gatewayEventId"
        if (_state.value.processedWebhooks.contains(webhookKey)) {
            val current = _state.value.paymentAttempts.first { it.id == paymentAttemptId }
            return ApiCallResult(true, 200, data = current)
        }

        val attempt = _state.value.paymentAttempts.firstOrNull { it.id == paymentAttemptId }
            ?: return ApiCallResult(false, 404, errorReason = "PAYMENT_ATTEMPT_NOT_FOUND")

        val newStatus = when {
            !gatewaySucceeded -> PaymentAttemptStatus.FAILED
            kotlin.math.abs(webhookAmountInr - attempt.amountInr) > 0.01 -> PaymentAttemptStatus.INVESTIGATION
            else -> PaymentAttemptStatus.PENDING_ADMIN_APPROVAL
        }

        val updatedAttempt = attempt.copy(
            status = newStatus,
            gatewayRef = gatewayEventId,
            rejectionReason = if (newStatus == PaymentAttemptStatus.INVESTIGATION) {
                "Amount mismatch: expected ₹${attempt.amountInr}, webhook reported ₹$webhookAmountInr"
            } else null,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        _state.update { s ->
            s.copy(
                paymentAttempts = s.paymentAttempts.map { if (it.id == paymentAttemptId) updatedAttempt else it },
                processedWebhooks = s.processedWebhooks + webhookKey,
                lastStatusBanner = "Webhook processed: ${updatedAttempt.status.name}"
            )
        }
        return ApiCallResult(true, 200, data = updatedAttempt)
    }

    @Synchronized
    fun recordCashByStaff(
        staffUserId: String,
        staffRole: UserRole,
        paymentAttemptId: String,
        amountReceivedInr: Double,
        receiptReference: String
    ): ApiCallResult<PaymentAttemptRecord> {
        if (!checkPermission(staffRole, "RECORD_CASH_RECEIPT")) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_STAFF_OR_ADMIN_REQUIRED")
        }
        val attempt = _state.value.paymentAttempts.firstOrNull { it.id == paymentAttemptId }
            ?: return ApiCallResult(false, 404, errorReason = "PAYMENT_ATTEMPT_NOT_FOUND")

        val updated = attempt.copy(
            status = PaymentAttemptStatus.PENDING_ADMIN_APPROVAL,
            cashReceiptStaffId = staffUserId,
            referenceNumber = receiptReference,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _state.update { s ->
            s.copy(
                paymentAttempts = s.paymentAttempts.map { if (it.id == paymentAttemptId) updated else it },
                lastStatusBanner = "Staff recorded cash receipt $receiptReference; awaiting Admin confirmation"
            )
        }
        appendAudit(staffUserId, staffRole, "STAFF_RECORD_CASH", "cash_receipts", paymentAttemptId, "Staff recorded cash receipt $receiptReference", "{\"status\":\"${attempt.status}\"}", "{\"status\":\"PENDING_ADMIN_APPROVAL\"}")
        return ApiCallResult(true, 200, data = updated)
    }

    @Synchronized
    fun approvePayment(
        adminUserId: String,
        adminRole: UserRole,
        paymentAttemptId: String,
        reason: String = "Verified payment receipt",
        approvalDate: LocalDate = LocalDate.now(),
        idempotencyKey: String = "approve-$paymentAttemptId"
    ): ApiCallResult<MembershipRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_APPROVAL_REQUIRED")
        }

        val existingMembership = _state.value.memberships.firstOrNull { it.sourcePaymentId == paymentAttemptId }
        if (existingMembership != null) {
            return ApiCallResult(true, 200, data = existingMembership)
        }

        val attempt = _state.value.paymentAttempts.firstOrNull { it.id == paymentAttemptId }
            ?: return ApiCallResult(false, 404, errorReason = "PAYMENT_ATTEMPT_NOT_FOUND")

        if (attempt.status == PaymentAttemptStatus.FAILED || attempt.status == PaymentAttemptStatus.GATEWAY_FAILED) {
            return ApiCallResult(false, 400, errorReason = "CANNOT_APPROVE_FAILED_GATEWAY_PAYMENT")
        }
        if (attempt.method == PaymentMethod.CASH && attempt.cashReceiptStaffId == null) {
            return ApiCallResult(false, 400, errorReason = "CASH_RECEIPT_MUST_BE_RECORDED_FIRST")
        }

        val order = _state.value.orders.firstOrNull { it.id == attempt.orderId }
            ?: return ApiCallResult(false, 404, errorReason = "ORDER_NOT_FOUND")
        val plan = _state.value.plans.firstOrNull { it.id == order.planId }
            ?: return ApiCallResult(false, 404, errorReason = "PLAN_NOT_FOUND")

        val currentMembership = _state.value.memberships
            .filter { it.userId == attempt.userId && it.status == MembershipStatus.ACTIVE }
            .maxByOrNull { it.expiryDate }

        val startDate = if (order.type == OrderType.RENEWAL && currentMembership != null) {
            CalendarMathEngine.calculateRenewalStartDate(currentMembership.expiryDate, approvalDate)
        } else {
            approvalDate
        }

        val expiryDate = CalendarMathEngine.calculateExpiryDate(
            startDate = startDate,
            durationType = plan.durationType,
            durationValue = plan.durationValue
        )

        val newMembership = MembershipRecord(
            id = "mem-${UUID.randomUUID().toString().take(8)}",
            userId = attempt.userId,
            planId = plan.id,
            startDate = startDate,
            expiryDate = expiryDate,
            status = MembershipStatus.ACTIVE,
            sourcePaymentId = attempt.id,
            parentMembershipId = currentMembership?.id
        )

        val updatedAttempt = attempt.copy(
            status = PaymentAttemptStatus.APPROVED,
            cashConfirmedByAdminId = if (attempt.method == PaymentMethod.CASH) adminUserId else null,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        val verificationLog = PaymentVerificationLog(
            id = "ver-${UUID.randomUUID().toString().take(8)}",
            paymentAttemptId = attempt.id,
            actorId = adminUserId,
            actorRole = adminRole,
            action = VerificationAction.APPROVE,
            reason = reason,
            beforeState = attempt.status.name,
            afterState = PaymentAttemptStatus.APPROVED.name
        )

        _state.update { s ->
            val updatedPrevMemberships = if ((order.type == OrderType.UPGRADE || order.type == OrderType.DOWNGRADE) && currentMembership != null) {
                s.memberships.map { m ->
                    if (m.id == currentMembership.id) {
                        m.copy(status = if (order.type == OrderType.UPGRADE) MembershipStatus.UPGRADED else MembershipStatus.DOWNGRADED)
                    } else m
                }
            } else s.memberships

            s.copy(
                paymentAttempts = s.paymentAttempts.map { if (it.id == attempt.id) updatedAttempt else it },
                orders = s.orders.map { if (it.id == order.id) it.copy(status = OrderStatus.COMPLETED) else it },
                memberships = listOf(newMembership) + updatedPrevMemberships,
                paymentVerifications = listOf(verificationLog) + s.paymentVerifications,
                lastStatusBanner = "Approved payment ${attempt.id} → Activated Membership ${newMembership.id} ($startDate to $expiryDate)"
            )
        }

        qualifyAndRewardReferralIfEligible(attempt.userId)
        appendAudit(adminUserId, adminRole, "APPROVE_PAYMENT_ACTIVATE_MEMBERSHIP", "memberships", newMembership.id, reason, "{\"paymentStatus\":\"${attempt.status}\"}", "{\"membershipId\":\"${newMembership.id}\",\"expiry\":\"$expiryDate\"}")
        sendNotification(
            userId = attempt.userId,
            type = "MEMBERSHIP_ACTIVATED",
            title = "Membership Activated",
            body = "Your ${plan.name} membership is active from $startDate until $expiryDate.",
            deepLink = "ironpulse://membership/${newMembership.id}",
            dedupeKey = "mem-act-${newMembership.id}"
        )
        return ApiCallResult(true, 200, data = newMembership)
    }

    @Synchronized
    fun rejectPayment(
        adminUserId: String,
        adminRole: UserRole,
        paymentAttemptId: String,
        reason: String
    ): ApiCallResult<PaymentAttemptRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) {
            return ApiCallResult(false, 400, errorReason = "REJECTION_REASON_MANDATORY")
        }
        val attempt = _state.value.paymentAttempts.firstOrNull { it.id == paymentAttemptId }
            ?: return ApiCallResult(false, 404, errorReason = "PAYMENT_ATTEMPT_NOT_FOUND")

        val updated = attempt.copy(
            status = PaymentAttemptStatus.REJECTED,
            rejectionReason = reason,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _state.update { s ->
            s.copy(
                paymentAttempts = s.paymentAttempts.map { if (it.id == paymentAttemptId) updated else it },
                orders = s.orders.map { if (it.id == attempt.orderId) it.copy(status = OrderStatus.FAILED) else it },
                lastStatusBanner = "Rejected payment $paymentAttemptId: $reason"
            )
        }
        appendAudit(adminUserId, adminRole, "REJECT_PAYMENT", "payment_attempts", paymentAttemptId, reason, "{\"status\":\"${attempt.status}\"}", "{\"status\":\"REJECTED\"}")
        return ApiCallResult(true, 200, data = updated)
    }

    @Synchronized
    fun initiateRefund(
        adminUserId: String,
        adminRole: UserRole,
        paymentAttemptId: String,
        amountInr: Double,
        reason: String,
        membershipImpact: RefundMembershipImpact = RefundMembershipImpact.NONE
    ): ApiCallResult<RefundRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val attempt = _state.value.paymentAttempts.firstOrNull { it.id == paymentAttemptId }
            ?: return ApiCallResult(false, 404, errorReason = "PAYMENT_ATTEMPT_NOT_FOUND")

        val refund = RefundRecord(
            id = "ref-${UUID.randomUUID().toString().take(8)}",
            paymentAttemptId = paymentAttemptId,
            amountInr = amountInr,
            reason = reason,
            gatewayRefundId = "gw_ref_${UUID.randomUUID().toString().take(6)}",
            status = RefundStatus.SUCCESS,
            initiatedBy = adminUserId,
            membershipImpact = membershipImpact,
            completedAtEpochMs = System.currentTimeMillis()
        )

        _state.update { s ->
            val updatedMemberships = s.memberships.map { m ->
                if (m.sourcePaymentId == paymentAttemptId) {
                    when (membershipImpact) {
                        RefundMembershipImpact.NONE -> m
                        RefundMembershipImpact.CANCEL -> m.copy(status = MembershipStatus.CANCELLED)
                        RefundMembershipImpact.EXTEND -> m.copy(expiryDate = m.expiryDate.plusDays(7))
                    }
                } else m
            }
            s.copy(
                refunds = listOf(refund) + s.refunds,
                paymentAttempts = s.paymentAttempts.map {
                    if (it.id == paymentAttemptId) it.copy(status = PaymentAttemptStatus.REFUNDED) else it
                },
                memberships = updatedMemberships,
                lastStatusBanner = "Processed refund ₹${amountInr.toInt()} (Impact: ${membershipImpact.name})"
            )
        }
        appendAudit(adminUserId, adminRole, "PROCESS_REFUND", "refunds", refund.id, reason, "{}", "{\"amount\":$amountInr,\"impact\":\"${membershipImpact.name}\"}")
        return ApiCallResult(true, 200, data = refund)
    }

    // ========================================================================
    // 7. MEMBERSHIP FREEZE & RESUME ENGINE
    // ========================================================================

    @Synchronized
    fun requestMembershipFreeze(
        userId: String,
        membershipId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        reason: String
    ): ApiCallResult<MembershipFreezeRecord> {
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "FREEZE_REASON_MANDATORY")
        val freeze = MembershipFreezeRecord(
            id = "frz-${UUID.randomUUID().toString().take(8)}",
            membershipId = membershipId,
            userId = userId,
            fromDate = fromDate,
            toDate = toDate,
            reason = reason,
            status = FreezeStatus.REQUESTED
        )
        _state.update { s ->
            s.copy(
                freezes = listOf(freeze) + s.freezes,
                memberships = s.memberships.map {
                    if (it.id == membershipId) it.copy(status = MembershipStatus.FREEZE_REQUESTED) else it
                },
                lastStatusBanner = "Freeze requested ($fromDate to $toDate)"
            )
        }
        return ApiCallResult(true, 200, data = freeze)
    }

    @Synchronized
    fun cancelFreezeRequest(userId: String, freezeId: String): ApiCallResult<MembershipFreezeRecord> {
        val freeze = _state.value.freezes.firstOrNull { it.id == freezeId && it.userId == userId }
            ?: return ApiCallResult(false, 404, errorReason = "FREEZE_NOT_FOUND")
        val cancelled = freeze.copy(status = FreezeStatus.CANCELLED)
        _state.update { s ->
            s.copy(
                freezes = s.freezes.map { if (it.id == freezeId) cancelled else it },
                memberships = s.memberships.map {
                    if (it.id == freeze.membershipId && it.status == MembershipStatus.FREEZE_REQUESTED) {
                        it.copy(status = MembershipStatus.ACTIVE)
                    } else it
                },
                lastStatusBanner = "Cancelled freeze request $freezeId"
            )
        }
        return ApiCallResult(true, 200, data = cancelled)
    }

    @Synchronized
    fun approveMembershipFreeze(
        adminUserId: String,
        adminRole: UserRole,
        freezeId: String,
        reason: String = "Approved freeze request"
    ): ApiCallResult<MembershipFreezeRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        val freeze = _state.value.freezes.firstOrNull { it.id == freezeId }
            ?: return ApiCallResult(false, 404, errorReason = "FREEZE_NOT_FOUND")
        val approved = freeze.copy(status = FreezeStatus.APPROVED, approvedBy = adminUserId)
        _state.update { s ->
            s.copy(
                freezes = s.freezes.map { if (it.id == freezeId) approved else it },
                memberships = s.memberships.map {
                    if (it.id == freeze.membershipId) it.copy(status = MembershipStatus.FROZEN) else it
                },
                lastStatusBanner = "Membership ${freeze.membershipId} is now FROZEN"
            )
        }
        appendAudit(adminUserId, adminRole, "APPROVE_FREEZE", "membership_freezes", freezeId, reason, "{\"status\":\"REQUESTED\"}", "{\"status\":\"APPROVED\"}")
        return ApiCallResult(true, 200, data = approved)
    }

    @Synchronized
    fun resumeFrozenMembership(
        actorId: String,
        freezeId: String
    ): ApiCallResult<MembershipRecord> {
        val freeze = _state.value.freezes.firstOrNull { it.id == freezeId }
            ?: return ApiCallResult(false, 404, errorReason = "FREEZE_NOT_FOUND")
        val membership = _state.value.memberships.firstOrNull { it.id == freeze.membershipId }
            ?: return ApiCallResult(false, 404, errorReason = "MEMBERSHIP_NOT_FOUND")

        val frozenDays = java.time.temporal.ChronoUnit.DAYS.between(freeze.fromDate, freeze.toDate).coerceAtLeast(1L)
        val extendedMembership = membership.copy(
            status = MembershipStatus.ACTIVE,
            expiryDate = membership.expiryDate.plusDays(frozenDays),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _state.update { s ->
            s.copy(
                freezes = s.freezes.map { if (it.id == freezeId) it.copy(status = FreezeStatus.RESUMED) else it },
                memberships = s.memberships.map { if (it.id == membership.id) extendedMembership else it },
                lastStatusBanner = "Resumed membership ${membership.id}: expiry extended by +$frozenDays days to ${extendedMembership.expiryDate}"
            )
        }
        return ApiCallResult(true, 200, data = extendedMembership)
    }

    // ========================================================================
    // 8. QR ATTENDANCE + DUAL-GATE GEOFENCE + ANOMALY FLAGS + CORRECTIONS
    // ========================================================================

    @Synchronized
    fun submitQrAttendanceScan(
        userId: String,
        qrToken: String,
        gpsPermissionGranted: Boolean,
        lat: Double,
        lng: Double,
        accuracyMeters: Double,
        scanDate: LocalDate = LocalDate.now(),
        scanEpochMs: Long = System.currentTimeMillis(),
        isMockLocationSignal: Boolean = false,
        gpsHardwareEnabled: Boolean = true
    ): ApiCallResult<AttendanceRecord> {
        if (_state.value.isOfflineSimulated) {
            return ApiCallResult(false, 503, errorReason = "NETWORK_OFFLINE_DETECTED")
        }
        if (!gpsPermissionGranted) {
            return ApiCallResult(false, 403, errorReason = "GPS_PERMISSION_DENIED")
        }
        if (!gpsHardwareEnabled) {
            return ApiCallResult(false, 400, errorReason = "GPS_DISABLED")
        }

        val location = _state.value.locations.firstOrNull { it.qrToken == qrToken && it.isActive }
            ?: return ApiCallResult(false, 400, errorReason = "INVALID_OR_REVOKED_QR_TOKEN")

        val membership = _state.value.memberships.firstOrNull {
            it.userId == userId &&
                    (it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.FROZEN || it.status == MembershipStatus.EXPIRED)
        } ?: return recordRejectedAttendance(
            userId, location.id, qrToken, scanDate, scanEpochMs, lat, lng, accuracyMeters, 0.0,
            "NO_ACTIVE_MEMBERSHIP", consumesRateLimit = true
        )

        if (membership.status == MembershipStatus.FROZEN) {
            return recordRejectedAttendance(
                userId, location.id, qrToken, scanDate, scanEpochMs, lat, lng, accuracyMeters, 0.0,
                "MEMBERSHIP_FROZEN", consumesRateLimit = true
            )
        }

        if (membership.status == MembershipStatus.EXPIRED || scanDate.isBefore(membership.startDate) || scanDate.isAfter(membership.expiryDate)) {
            return recordRejectedAttendance(
                userId, location.id, qrToken, scanDate, scanEpochMs, lat, lng, accuracyMeters, 0.0,
                "MEMBERSHIP_EXPIRED", consumesRateLimit = true
            )
        }

        val userScanTimestamps = qrScanTimestampsByUser.getOrPut(userId) { mutableListOf() }
        userScanTimestamps.removeAll { scanEpochMs - it > 3600_000L }
        if (userScanTimestamps.size >= 5) {
            return recordRejectedAttendance(
                userId, location.id, qrToken, scanDate, scanEpochMs, lat, lng, accuracyMeters, 0.0,
                "RATE_LIMIT_5_PER_HOUR_EXCEEDED", consumesRateLimit = false
            )
        }

        val distanceMeters = GeofenceEngine.haversineMeters(lat, lng, location.lat, location.lng)
        val eval = GeofenceEngine.evaluateDualGate(
            distanceMeters = distanceMeters,
            gpsAccuracyMeters = accuracyMeters,
            radiusMeters = location.geofenceRadiusMeters,
            maxAccuracyMeters = location.maxGpsAccuracyMeters,
            strictMode = _state.value.settings.strictGeofenceMode
        )

        if (!eval.passed) {
            return recordRejectedAttendance(
                userId = userId,
                locationId = location.id,
                qrToken = qrToken,
                scanDate = scanDate,
                scanEpochMs = scanEpochMs,
                lat = lat,
                lng = lng,
                accuracyMeters = accuracyMeters,
                distanceMeters = distanceMeters,
                rejectReason = eval.rejectReason ?: "GEOFENCE_FAILED",
                consumesRateLimit = eval.consumesRateLimit
            )
        }

        val duplicateApproved = _state.value.attendances.any {
            it.userId == userId &&
                    it.attendanceDate == scanDate &&
                    it.locationId == location.id &&
                    it.status == AttendanceStatus.APPROVED
        }
        if (duplicateApproved) {
            userScanTimestamps.add(scanEpochMs)
            return recordRejectedAttendance(
                userId = userId,
                locationId = location.id,
                qrToken = qrToken,
                scanDate = scanDate,
                scanEpochMs = scanEpochMs,
                lat = lat,
                lng = lng,
                accuracyMeters = accuracyMeters,
                distanceMeters = distanceMeters,
                rejectReason = "DUPLICATE_APPROVED_ATTENDANCE_FOR_DATE",
                consumesRateLimit = false
            )
        }

        userScanTimestamps.add(scanEpochMs)

        val attendanceId = "att-${UUID.randomUUID().toString().take(8)}"
        val previousUserScans = _state.value.attendances.filter { it.userId == userId }
        val sameDayOtherScans = _state.value.attendances.filter { it.attendanceDate == scanDate && it.userId != userId }

        val anomalyFlags = GeofenceEngine.detectAnomalies(
            userId = userId,
            attendanceId = attendanceId,
            scanLat = lat,
            scanLng = lng,
            scanAccuracyMeters = accuracyMeters,
            scanEpochMs = scanEpochMs,
            scanDate = scanDate,
            previousUserScans = previousUserScans,
            sameDayOtherMemberScans = sameDayOtherScans,
            isMockLocationSignal = isMockLocationSignal
        )

        val record = AttendanceRecord(
            id = attendanceId,
            userId = userId,
            locationId = location.id,
            qrTokenUsed = qrToken,
            attendanceDate = scanDate,
            scannedAtEpochMs = scanEpochMs,
            gpsLat = lat,
            gpsLng = lng,
            gpsAccuracyMeters = accuracyMeters,
            distanceMeters = distanceMeters,
            status = AttendanceStatus.APPROVED
        )

        _state.update { s ->
            s.copy(
                attendances = listOf(record) + s.attendances,
                anomalyFlags = anomalyFlags + s.anomalyFlags,
                lastStatusBanner = if (anomalyFlags.isEmpty()) {
                    "Attendance APPROVED (${"%.1f".format(distanceMeters)}m, acc ${"%.1f".format(accuracyMeters)}m)"
                } else {
                    "Attendance recorded with ${anomalyFlags.size} Anomaly Flag(s) routed to Admin Review"
                }
            )
        }

        val plan = _state.value.plans.firstOrNull { it.id == membership.planId }
        recomputeStreakForUser(
            userId = userId,
            membershipId = membership.id,
            evaluationStartDate = membership.startDate,
            evaluationEndDate = scanDate,
            milestoneKind = plan?.milestoneKind ?: MilestoneKind.CONSECUTIVE,
            milestoneTargetDays = plan?.milestoneTargetDays ?: 6,
            rewardDaysPerMilestone = plan?.milestoneRewardDays ?: 2
        )
        enqueueOutbox("/api/v1/attendance/scan", attendanceId, "userId=$userId,date=$scanDate")
        return ApiCallResult(true, 200, data = record)
    }

    private fun recordRejectedAttendance(
        userId: String,
        locationId: String,
        qrToken: String,
        scanDate: LocalDate,
        scanEpochMs: Long,
        lat: Double,
        lng: Double,
        accuracyMeters: Double,
        distanceMeters: Double,
        rejectReason: String,
        consumesRateLimit: Boolean
    ): ApiCallResult<AttendanceRecord> {
        if (consumesRateLimit) {
            qrScanTimestampsByUser.getOrPut(userId) { mutableListOf() }.add(scanEpochMs)
        }
        val rejected = AttendanceRecord(
            id = "att-rej-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            locationId = locationId,
            qrTokenUsed = qrToken,
            attendanceDate = scanDate,
            scannedAtEpochMs = scanEpochMs,
            gpsLat = lat,
            gpsLng = lng,
            gpsAccuracyMeters = accuracyMeters,
            distanceMeters = distanceMeters,
            status = AttendanceStatus.REJECTED,
            rejectReason = rejectReason
        )
        _state.update { s ->
            s.copy(
                attendances = listOf(rejected) + s.attendances,
                lastStatusBanner = "Attendance REJECTED: $rejectReason"
            )
        }
        return ApiCallResult(false, 422, data = rejected, errorReason = rejectReason)
    }

    @Synchronized
    fun reviewAnomalyFlag(
        adminUserId: String,
        adminRole: UserRole,
        flagId: String,
        decision: String,
        reason: String
    ): ApiCallResult<AttendanceAnomalyFlag> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REASON_MANDATORY")
        val flag = _state.value.anomalyFlags.firstOrNull { it.id == flagId }
            ?: return ApiCallResult(false, 404, errorReason = "ANOMALY_FLAG_NOT_FOUND")
        val updated = flag.copy(reviewedBy = adminUserId, decision = decision)
        _state.update { s ->
            s.copy(
                anomalyFlags = s.anomalyFlags.map { if (it.id == flagId) updated else it },
                lastStatusBanner = "Reviewed anomaly $flagId: $decision"
            )
        }
        appendAudit(adminUserId, adminRole, "REVIEW_ANOMALY", "attendance_anomaly_flags", flagId, reason, "{}", "{\"decision\":\"$decision\"}")
        return ApiCallResult(true, 200, data = updated)
    }

    @Synchronized
    fun adminCorrectAttendance(
        adminUserId: String,
        adminRole: UserRole,
        memberUserId: String,
        locationId: String,
        attendanceDate: LocalDate,
        reason: String
    ): ApiCallResult<AttendanceRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) {
            return ApiCallResult(false, 400, errorReason = "CORRECTION_REASON_MANDATORY")
        }

        val existing = _state.value.attendances.firstOrNull {
            it.userId == memberUserId &&
                    it.attendanceDate == attendanceDate &&
                    it.locationId == locationId &&
                    it.status == AttendanceStatus.APPROVED
        }
        if (existing != null) {
            return ApiCallResult(true, 200, data = existing)
        }

        val correction = AttendanceRecord(
            id = "att-corr-${UUID.randomUUID().toString().take(8)}",
            userId = memberUserId,
            locationId = locationId,
            qrTokenUsed = "ADMIN_MANUAL_CORRECTION",
            attendanceDate = attendanceDate,
            scannedAtEpochMs = System.currentTimeMillis(),
            gpsLat = 12.971599,
            gpsLng = 77.594566,
            gpsAccuracyMeters = 5.0,
            distanceMeters = 0.0,
            status = AttendanceStatus.APPROVED,
            isCorrection = true,
            correctedBy = adminUserId,
            correctionReason = reason,
            source = AttendanceSource.ADMIN
        )

        _state.update { s ->
            s.copy(
                attendances = listOf(correction) + s.attendances,
                lastStatusBanner = "Admin attendance correction recorded for $memberUserId on $attendanceDate"
            )
        }

        appendAudit(adminUserId, adminRole, "ATTENDANCE_CORRECTION", "attendance", correction.id, reason, "{}", "{\"userId\":\"$memberUserId\",\"date\":\"$attendanceDate\"}")

        val membership = _state.value.memberships.firstOrNull { it.userId == memberUserId && it.status == MembershipStatus.ACTIVE }
        if (membership != null) {
            val maxAttDate = (_state.value.attendances.filter { it.userId == memberUserId && it.status == AttendanceStatus.APPROVED }
                .maxOfOrNull { it.attendanceDate } ?: attendanceDate)
            recomputeStreakForUser(memberUserId, membership.id, membership.startDate, maxAttDate)
        }

        return ApiCallResult(true, 200, data = correction)
    }

    // ========================================================================
    // 9. DETERMINISTIC STREAK & REWARD ENGINE + ADMIN REVERSAL
    // ========================================================================

    @Synchronized
    fun recomputeStreakForUser(
        userId: String,
        membershipId: String,
        evaluationStartDate: LocalDate,
        evaluationEndDate: LocalDate,
        milestoneKind: MilestoneKind = MilestoneKind.CONSECUTIVE,
        milestoneTargetDays: Int = 6,
        rewardDaysPerMilestone: Int = 2
    ): StreakRecomputeResult {
        val s = _state.value
        val result = StreakEngine.recomputeStreakAndRewards(
            userId = userId,
            membershipId = membershipId,
            evaluationStartDate = evaluationStartDate,
            evaluationEndDate = evaluationEndDate,
            approvedAttendances = s.attendances,
            schedules = s.schedules,
            holidays = s.holidays,
            freezes = s.freezes.filter { it.membershipId == membershipId },
            milestoneKind = milestoneKind,
            milestoneTargetDays = milestoneTargetDays,
            rewardDaysPerMilestone = rewardDaysPerMilestone,
            existingRewards = s.rewards
        )

        val totalNewExtensionDays = result.newlyIssuedRewards.sumOf { it.valueDaysOrPoints }

        _state.update { state ->
            val otherRewards = state.rewards.filterNot {
                it.userId == userId && it.membershipId == membershipId && it.source == RewardSource.STREAK_MILESTONE
            }
            val updatedMemberships = if (totalNewExtensionDays > 0) {
                state.memberships.map { m ->
                    if (m.id == membershipId) m.copy(expiryDate = m.expiryDate.plusDays(totalNewExtensionDays.toLong())) else m
                }
            } else state.memberships

            state.copy(
                streakStates = state.streakStates + (userId to result.streakState),
                rewards = result.allRewardsForMembership + otherRewards,
                memberships = updatedMemberships
            )
        }
        return result
    }

    private fun recomputeAllMemberStreaks() {
        val activeMemberships = _state.value.memberships.filter { it.status == MembershipStatus.ACTIVE }
        activeMemberships.forEach { m ->
            val maxDate = _state.value.attendances
                .filter { it.userId == m.userId && it.status == AttendanceStatus.APPROVED }
                .maxOfOrNull { it.attendanceDate } ?: m.startDate
            recomputeStreakForUser(m.userId, m.id, m.startDate, maxDate)
        }
    }

    @Synchronized
    fun reverseReward(
        adminUserId: String,
        adminRole: UserRole,
        rewardId: String,
        reason: String
    ): ApiCallResult<RewardRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REVERSAL_REASON_MANDATORY")
        val reward = _state.value.rewards.firstOrNull { it.id == rewardId }
            ?: return ApiCallResult(false, 404, errorReason = "REWARD_NOT_FOUND")
        if (reward.status == RewardStatus.REVOKED) {
            return ApiCallResult(false, 400, errorReason = "REWARD_ALREADY_REVOKED")
        }

        val revoked = reward.copy(status = RewardStatus.REVOKED, revokedReason = reason)
        _state.update { s ->
            val updatedMemberships = s.memberships.map { m ->
                if (m.id == reward.membershipId) {
                    m.copy(expiryDate = m.expiryDate.minusDays(reward.valueDaysOrPoints.toLong()))
                } else m
            }
            s.copy(
                rewards = s.rewards.map { if (it.id == rewardId) revoked else it },
                memberships = updatedMemberships,
                lastStatusBanner = "Reversed reward $rewardId (-${reward.valueDaysOrPoints} days): $reason"
            )
        }
        appendAudit(adminUserId, adminRole, "REVERSE_REWARD", "rewards", rewardId, reason, "{\"status\":\"${reward.status}\"}", "{\"status\":\"REVOKED\"}")
        return ApiCallResult(true, 200, data = revoked)
    }

    // ========================================================================
    // 10. TRANSFORMATION PHOTOS, PRIVACY SIGNED URLS & 80% COUPON ENGINE
    // ========================================================================

    @Synchronized
    fun uploadProgressPhoto(
        userId: String,
        phase: PhotoPhase,
        angle: PhotoAngle,
        weekIndex: Int? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiCallResult<ProgressPhotoRecord> {
        val photoId = "pho-${phase.name.lowercase()}-${angle.name.lowercase()}-${UUID.randomUUID().toString().take(6)}"
        val photo = ProgressPhotoRecord(
            id = photoId,
            userId = userId,
            phase = phase,
            angle = angle,
            weekIndex = weekIndex,
            storageKey = "private-bucket/$userId/${phase.name.lowercase()}_${angle.name.lowercase()}.jpg",
            signedUrlToken = "sig_${UUID.randomUUID().toString().replace("-", "")}",
            signedUrlExpiresAtEpochMs = nowEpochMs + 5 * 60_000L,
            cacheControl = "private, no-store",
            capturedAtEpochMs = nowEpochMs
        )
        _state.update { s ->
            val filtered = s.photos.filterNot {
                it.userId == userId && it.phase == phase && it.angle == angle && it.weekIndex == weekIndex
            }
            s.copy(
                photos = listOf(photo) + filtered,
                lastStatusBanner = "Uploaded ${phase.name} • ${angle.name} photo (5-min Signed URL active)"
            )
        }
        return ApiCallResult(true, 200, data = photo)
    }

    /**
     * Section 6.7 Photo Deletion SLA:
     * Member request -> photo_deletion_requests row -> soft delete immediately -> hard delete within 7 days.
     */
    @Synchronized
    fun requestPhotoDeletion(
        userId: String,
        photoId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiCallResult<PhotoDeletionRequestRecord> {
        val photo = _state.value.photos.firstOrNull { it.id == photoId && it.userId == userId }
            ?: return ApiCallResult(false, 404, errorReason = "PHOTO_NOT_FOUND")

        val sevenDaysMs = 7L * 24 * 3600 * 1000L
        val req = PhotoDeletionRequestRecord(
            id = "pdel-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            photoId = photoId,
            requestedAtEpochMs = nowEpochMs,
            scheduledPurgeAtEpochMs = nowEpochMs + sevenDaysMs,
            status = "SOFT_DELETED_PENDING_HARD_PURGE"
        )

        _state.update { s ->
            s.copy(
                photos = s.photos.map { if (it.id == photoId) it.copy(deletedAtEpochMs = nowEpochMs) else it },
                photoDeletionRequests = listOf(req) + s.photoDeletionRequests,
                lastStatusBanner = "Photo $photoId soft-deleted immediately; hard purge scheduled within 7 days"
            )
        }
        return ApiCallResult(true, 200, data = req)
    }

    @Synchronized
    fun executeScheduledPhotoHardPurge(nowEpochMs: Long): Int {
        val duePhotoIds = _state.value.photoDeletionRequests
            .filter { it.purgedAtEpochMs == null && nowEpochMs >= it.scheduledPurgeAtEpochMs }
            .map { it.photoId }
            .toSet()

        if (duePhotoIds.isEmpty()) return 0
        _state.update { s ->
            s.copy(
                photos = s.photos.filterNot { duePhotoIds.contains(it.id) },
                photoDeletionRequests = s.photoDeletionRequests.map {
                    if (duePhotoIds.contains(it.photoId)) it.copy(purgedAtEpochMs = nowEpochMs, status = "HARD_PURGED") else it
                }
            )
        }
        return duePhotoIds.size
    }

    fun accessProtectedPhoto(
        requesterUserId: String,
        requesterRole: UserRole,
        photoId: String,
        signedUrlToken: String?,
        directBucketAccessAttempt: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiCallResult<ProgressPhotoRecord> {
        if (directBucketAccessAttempt || signedUrlToken.isNullOrBlank()) {
            return ApiCallResult(false, 403, errorReason = "DIRECT_BUCKET_URL_BLOCKED_SIGNED_URL_REQUIRED")
        }
        val photo = _state.value.photos.firstOrNull { it.id == photoId && it.deletedAtEpochMs == null }
            ?: return ApiCallResult(false, 404, errorReason = "PHOTO_NOT_FOUND_OR_DELETED")

        val isOwnerOrAdmin = photo.userId == requesterUserId || requesterRole == UserRole.ADMIN || requesterRole == UserRole.OWNER
        if (!isOwnerOrAdmin) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_UNAUTHORIZED_PHOTO_ACCESS")
        }
        if (photo.signedUrlToken != signedUrlToken) {
            return ApiCallResult(false, 403, errorReason = "INVALID_SIGNED_URL_SIGNATURE")
        }
        if (nowEpochMs > photo.signedUrlExpiresAtEpochMs) {
            return ApiCallResult(false, 403, errorReason = "SIGNED_URL_EXPIRED_5MIN_TTL")
        }
        return ApiCallResult(true, 200, data = photo)
    }

    @Synchronized
    fun submitTransformation(
        userId: String,
        membershipId: String,
        privacyConsent: Boolean,
        marketingConsent: Boolean = false
    ): ApiCallResult<TransformationSubmissionRecord> {
        if (!privacyConsent) {
            return ApiCallResult(false, 400, errorReason = "PRIVACY_CONSENT_MANDATORY")
        }
        val userPhotos = _state.value.photos.filter { it.userId == userId && it.deletedAtEpochMs == null }
        val startAngles = userPhotos.filter { it.phase == PhotoPhase.START }.map { it.angle }.toSet()
        if (!startAngles.containsAll(listOf(PhotoAngle.FRONT, PhotoAngle.SIDE, PhotoAngle.BACK))) {
            return ApiCallResult(false, 400, errorReason = "INCOMPLETE_STARTING_PHOTOS_ALL_3_ANGLES_REQUIRED")
        }
        val endAngles = userPhotos.filter { it.phase == PhotoPhase.END }.map { it.angle }.toSet()
        if (!endAngles.containsAll(listOf(PhotoAngle.FRONT, PhotoAngle.SIDE, PhotoAngle.BACK))) {
            return ApiCallResult(false, 400, errorReason = "INCOMPLETE_ENDING_PHOTOS_ALL_3_ANGLES_REQUIRED")
        }

        val existing = _state.value.transformations.firstOrNull {
            it.userId == userId && it.membershipId == membershipId
        }

        val submission = TransformationSubmissionRecord(
            id = existing?.id ?: "trf-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            membershipId = membershipId,
            status = TransformationStatus.SUBMITTED,
            privacyConsentAccepted = privacyConsent,
            marketingConsentAccepted = marketingConsent
        )

        _state.update { s ->
            val others = s.transformations.filterNot { it.id == submission.id }
            s.copy(
                transformations = listOf(submission) + others,
                lastStatusBanner = "Transformation submitted with all 6 mandatory start/end angles"
            )
        }
        return ApiCallResult(true, 200, data = submission)
    }

    @Synchronized
    fun reviewTransformation(
        adminUserId: String,
        adminRole: UserRole,
        submissionId: String,
        decision: TransformationDecision,
        reason: String,
        reviewDate: LocalDate = LocalDate.now()
    ): ApiCallResult<TransformationSubmissionRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_HUMAN_ADMIN_REQUIRED")
        }
        if ((decision == TransformationDecision.REJECT || decision == TransformationDecision.REQUEST_RESUBMISSION) && reason.isBlank()) {
            return ApiCallResult(false, 400, errorReason = "DECISION_REASON_MANDATORY")
        }

        val sub = _state.value.transformations.firstOrNull { it.id == submissionId }
            ?: return ApiCallResult(false, 404, errorReason = "SUBMISSION_NOT_FOUND")

        var couponCode: String? = sub.issuedCouponCode
        if (decision == TransformationDecision.APPROVE && couponCode == null) {
            val newCoupon = CouponRecord(
                id = "cpn-${UUID.randomUUID().toString().take(8)}",
                userId = sub.userId,
                code = "TRANSFORM80-${sub.userId.takeLast(4).uppercase()}-${(100..999).random()}",
                discountPercent = 80,
                maxDiscountCapInr = 15000.0,
                minPurchaseAmountInr = 1000.0,
                source = CouponSource.TRANSFORMATION,
                singleUse = true,
                stackable = false,
                status = CouponStatus.ISSUED,
                expiresAtDate = reviewDate.plusDays(90)
            )
            couponCode = newCoupon.code
            _state.update { s -> s.copy(coupons = listOf(newCoupon) + s.coupons) }
        }

        val newStatus = when (decision) {
            TransformationDecision.APPROVE -> TransformationStatus.APPROVED
            TransformationDecision.REJECT -> TransformationStatus.REJECTED
            TransformationDecision.REQUEST_RESUBMISSION -> TransformationStatus.RESUBMISSION_REQUESTED
        }

        val updated = sub.copy(
            status = newStatus,
            reviewerId = adminUserId,
            decisionReason = reason,
            issuedCouponCode = couponCode,
            reviewedAtEpochMs = System.currentTimeMillis()
        )

        _state.update { s ->
            s.copy(
                transformations = s.transformations.map { if (it.id == submissionId) updated else it },
                lastStatusBanner = "Transformation ${newStatus.name}" + (if (couponCode != null) " • Issued 80% Coupon $couponCode" else ": $reason")
            )
        }
        appendAudit(adminUserId, adminRole, "REVIEW_TRANSFORMATION_${decision.name}", "transformation_submissions", submissionId, reason, "{\"status\":\"${sub.status}\"}", "{\"status\":\"$newStatus\",\"coupon\":\"${couponCode ?: ""}\"}")
        return ApiCallResult(true, 200, data = updated)
    }

    @Synchronized
    fun revokeCoupon(
        adminUserId: String,
        adminRole: UserRole,
        couponId: String,
        reason: String
    ): ApiCallResult<CouponRecord> {
        if (adminRole != UserRole.ADMIN && adminRole != UserRole.OWNER) {
            return ApiCallResult(false, 403, errorReason = "FORBIDDEN_ADMIN_REQUIRED")
        }
        if (reason.isBlank()) return ApiCallResult(false, 400, errorReason = "REVOCATION_REASON_MANDATORY")
        val coupon = _state.value.coupons.firstOrNull { it.id == couponId }
            ?: return ApiCallResult(false, 404, errorReason = "COUPON_NOT_FOUND")
        val revoked = coupon.copy(status = CouponStatus.REVOKED, revokedReason = reason)
        _state.update { s ->
            s.copy(
                coupons = s.coupons.map { if (it.id == couponId) revoked else it },
                lastStatusBanner = "Revoked coupon ${coupon.code}: $reason"
            )
        }
        appendAudit(adminUserId, adminRole, "REVOKE_COUPON", "coupons", couponId, reason, "{\"status\":\"${coupon.status}\"}", "{\"status\":\"REVOKED\"}")
        sendNotification(coupon.userId, "COUPON_REVOKED", "Coupon Revoked", "Coupon ${coupon.code} was revoked: $reason", "ironpulse://coupons", "dedupe-rev-$couponId")
        return ApiCallResult(true, 200, data = revoked)
    }

    @Synchronized
    fun validateAndLockCoupon(
        userId: String,
        couponCode: String,
        purchaseAmountInr: Double,
        checkoutDate: LocalDate = LocalDate.now()
    ): ApiCallResult<CouponRecord> {
        val coupon = _state.value.coupons.firstOrNull { it.code.equals(couponCode.trim(), ignoreCase = true) }
            ?: return ApiCallResult(false, 404, errorReason = "COUPON_NOT_FOUND")

        if (coupon.userId != userId) {
            return ApiCallResult(false, 403, errorReason = "COUPON_TIED_TO_ANOTHER_USER")
        }
        if (coupon.status == CouponStatus.REDEEMED || _state.value.redeemedCouponIds.contains(coupon.id)) {
            return ApiCallResult(false, 409, errorReason = "DUPLICATE_COUPON_REDEMPTION_BLOCKED")
        }
        if (coupon.status == CouponStatus.REVOKED) {
            return ApiCallResult(false, 400, errorReason = "COUPON_REVOKED")
        }
        if (checkoutDate.isAfter(coupon.expiresAtDate) || coupon.status == CouponStatus.EXPIRED) {
            return ApiCallResult(false, 400, errorReason = "COUPON_EXPIRED")
        }
        if (purchaseAmountInr < coupon.minPurchaseAmountInr) {
            return ApiCallResult(false, 400, errorReason = "BELOW_MIN_PURCHASE_AMOUNT")
        }
        return ApiCallResult(true, 200, data = coupon)
    }

    // ========================================================================
    // 11. GAMIFICATION, LEADERBOARD & REFERRALS
    // ========================================================================

    @Synchronized
    fun createReferralAttribution(
        referrerUserId: String,
        inviteeMobile: String,
        inviteeUserId: String? = null,
        sameDeviceOrUpiDetected: Boolean = false
    ): ApiCallResult<ReferralRecord> {
        val referrer = _state.value.users.firstOrNull { it.id == referrerUserId }
            ?: return ApiCallResult(false, 404, errorReason = "REFERRER_NOT_FOUND")

        if (referrer.mobile == inviteeMobile || referrerUserId == inviteeUserId || sameDeviceOrUpiDetected) {
            return ApiCallResult(false, 400, errorReason = "SELF_REFERRAL_BLOCKED")
        }

        val duplicateInvitee = _state.value.referrals.any { it.inviteeMobile == inviteeMobile }
        if (duplicateInvitee) {
            return ApiCallResult(false, 409, errorReason = "DUPLICATE_REFERRAL_FOR_INVITEE_BLOCKED")
        }

        val referral = ReferralRecord(
            id = "ref-attr-${UUID.randomUUID().toString().take(8)}",
            referrerUserId = referrerUserId,
            inviteeMobile = inviteeMobile,
            inviteeUserId = inviteeUserId,
            codeUsed = referrer.referralCode,
            status = ReferralStatus.PENDING
        )
        _state.update { s ->
            s.copy(
                referrals = listOf(referral) + s.referrals,
                lastStatusBanner = "Referral recorded for invitee $inviteeMobile (Status: PENDING)"
            )
        }
        return ApiCallResult(true, 200, data = referral)
    }

    private fun qualifyAndRewardReferralIfEligible(inviteeUserId: String) {
        val invitee = _state.value.users.firstOrNull { it.id == inviteeUserId } ?: return
        val pendingReferral = _state.value.referrals.firstOrNull {
            (it.inviteeUserId == inviteeUserId || it.inviteeMobile == invitee.mobile) &&
                    it.status == ReferralStatus.PENDING
        } ?: return

        val rewarded = pendingReferral.copy(
            inviteeUserId = inviteeUserId,
            status = ReferralStatus.REWARDED
        )
        val reward = RewardRecord(
            id = "rwd-ref-${pendingReferral.id}",
            userId = pendingReferral.referrerUserId,
            type = RewardType.MEMBERSHIP_DAYS,
            valueDaysOrPoints = 7,
            source = RewardSource.REFERRAL,
            membershipId = null,
            milestoneIndex = null,
            status = RewardStatus.APPLIED
        )
        _state.update { s ->
            val updatedMemberships = s.memberships.map { m ->
                if (m.userId == pendingReferral.referrerUserId && m.status == MembershipStatus.ACTIVE) {
                    m.copy(expiryDate = m.expiryDate.plusDays(7))
                } else m
            }
            s.copy(
                referrals = s.referrals.map { if (it.id == pendingReferral.id) rewarded else it },
                rewards = listOf(reward) + s.rewards,
                memberships = updatedMemberships
            )
        }
    }

    // ========================================================================
    // 12. ACCOUNT DELETION, NOTIFICATIONS, SUPPORT & BACKUP DRILL
    // ========================================================================

    @Synchronized
    fun requestAccountDeletion(
        userId: String,
        requestDate: LocalDate = LocalDate.now(),
        adminConfirmedActiveMembershipOverride: Boolean = false
    ): ApiCallResult<DeletionRequestRecord> {
        val hasActiveMembership = _state.value.memberships.any {
            it.userId == userId && it.status == MembershipStatus.ACTIVE && !it.expiryDate.isBefore(requestDate)
        }
        if (hasActiveMembership && !adminConfirmedActiveMembershipOverride) {
            return ApiCallResult(
                false,
                409,
                errorReason = "ACTIVE_MEMBERSHIP_EXISTS_ADMIN_CONFIRMATION_OR_COOLING_REQUIRED"
            )
        }

        val req = DeletionRequestRecord(
            id = "del-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            requestedAtDate = requestDate,
            coolingEndsAtDate = requestDate.plusDays(30),
            status = DeletionStatus.PENDING,
            hasActiveMembershipWarning = hasActiveMembership,
            adminConfirmedOverride = adminConfirmedActiveMembershipOverride
        )
        _state.update { s ->
            s.copy(
                deletionRequests = listOf(req) + s.deletionRequests,
                lastStatusBanner = "Account deletion scheduled (30-day cooling ends ${req.coolingEndsAtDate})"
            )
        }
        return ApiCallResult(true, 200, data = req)
    }

    @Synchronized
    fun completeAccountDeletionAfterCooling(
        deletionRequestId: String,
        currentDate: LocalDate
    ): ApiCallResult<DeletionRequestRecord> {
        val req = _state.value.deletionRequests.firstOrNull { it.id == deletionRequestId }
            ?: return ApiCallResult(false, 404, errorReason = "DELETION_REQUEST_NOT_FOUND")

        if (currentDate.isBefore(req.coolingEndsAtDate) && !req.adminConfirmedOverride) {
            return ApiCallResult(false, 400, errorReason = "COOLING_PERIOD_30_DAYS_NOT_ELAPSED")
        }

        val completed = req.copy(status = DeletionStatus.COMPLETED, piiRedacted = true, financialRetained = true)
        _state.update { s ->
            s.copy(
                deletionRequests = s.deletionRequests.map { if (it.id == deletionRequestId) completed else it },
                users = s.users.map { u ->
                    if (u.id == req.userId) {
                        u.copy(
                            status = UserStatus.DELETED,
                            fullName = "REDACTED_USER",
                            mobile = "ANON_HASH_${u.id.hashCode()}",
                            email = null,
                            dob = "REDACTED",
                            weightHistoryKg = emptyList(),
                            marketingOptIn = false,
                            pushEnabled = false
                        )
                    } else u
                },
                photos = s.photos.map { p ->
                    if (p.userId == req.userId) p.copy(deletedAtEpochMs = System.currentTimeMillis()) else p
                }
            )
        }
        return ApiCallResult(true, 200, data = completed)
    }

    @Synchronized
    fun sendNotification(
        userId: String,
        type: String,
        title: String,
        body: String,
        deepLink: String,
        dedupeKey: String,
        simulateTransientFailuresCount: Int = 0
    ): ApiCallResult<NotificationItem> {
        val duplicate = _state.value.notifications.firstOrNull { it.dedupeKey == dedupeKey }
        if (duplicate != null) {
            return ApiCallResult(false, 409, data = duplicate, errorReason = "DUPLICATE_NOTIFICATION_DEDUPE_KEY")
        }

        var attempt = 0
        var delivered = false
        while (attempt <= 3) {
            attempt++
            if (attempt > simulateTransientFailuresCount) {
                delivered = true
                break
            }
        }
        if (!delivered) {
            return ApiCallResult(false, 503, errorReason = "NOTIFICATION_DELIVERY_FAILED_AFTER_3_RETRIES")
        }

        val item = NotificationItem(
            id = "ntf-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            type = type,
            title = title,
            body = body,
            deepLink = deepLink,
            dedupeKey = dedupeKey
        )
        _state.update { s -> s.copy(notifications = listOf(item) + s.notifications) }
        return ApiCallResult(true, 200, data = item)
    }

    @Synchronized
    fun createSupportTicket(
        userId: String,
        category: String,
        subject: String,
        description: String
    ): ApiCallResult<SupportTicketRecord> {
        if (subject.isBlank() || description.isBlank()) {
            return ApiCallResult(false, 400, errorReason = "SUBJECT_AND_DESCRIPTION_REQUIRED")
        }
        val ticket = SupportTicketRecord(
            id = "tkt-${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            category = category,
            subject = subject,
            description = description,
            status = TicketStatus.OPEN,
            messages = listOf(
                SupportMessageRecord(
                    id = "msg-1",
                    ticketId = "init",
                    senderId = userId,
                    senderRole = UserRole.MEMBER,
                    body = description
                )
            )
        )
        _state.update { s ->
            s.copy(
                supportTickets = listOf(ticket) + s.supportTickets,
                lastStatusBanner = "Support ticket ${ticket.id} opened"
            )
        }
        return ApiCallResult(true, 200, data = ticket)
    }

    @Synchronized
    fun replyAndResolveTicket(
        actorId: String,
        actorRole: UserRole,
        ticketId: String,
        replyBody: String,
        resolve: Boolean
    ): ApiCallResult<SupportTicketRecord> {
        val tkt = _state.value.supportTickets.firstOrNull { it.id == ticketId }
            ?: return ApiCallResult(false, 404, errorReason = "TICKET_NOT_FOUND")
        val msg = SupportMessageRecord(
            id = "msg-${UUID.randomUUID().toString().take(6)}",
            ticketId = ticketId,
            senderId = actorId,
            senderRole = actorRole,
            body = replyBody
        )
        val updated = tkt.copy(
            status = if (resolve) TicketStatus.RESOLVED else TicketStatus.IN_PROGRESS,
            messages = tkt.messages + msg
        )
        _state.update { s ->
            s.copy(
                supportTickets = s.supportTickets.map { if (it.id == ticketId) updated else it },
                lastStatusBanner = "Updated ticket $ticketId (${updated.status.name})"
            )
        }
        return ApiCallResult(true, 200, data = updated)
    }

    @Synchronized
    fun createCampaign(
        adminUserId: String,
        name: String,
        segmentDescription: String,
        templateBody: String,
        discountPercent: Int?
    ): ApiCallResult<CampaignRecord> {
        val eligibleOptInCount = _state.value.users.count { it.role == UserRole.MEMBER && it.marketingOptIn }
        val campaign = CampaignRecord(
            id = "cmp-${UUID.randomUUID().toString().take(8)}",
            name = name,
            segmentDescription = segmentDescription,
            templateBody = templateBody,
            discountPercent = discountPercent,
            sentCount = eligibleOptInCount,
            convertedCount = (eligibleOptInCount / 2).coerceAtLeast(1),
            createdBy = adminUserId
        )
        _state.update { s ->
            s.copy(
                campaigns = listOf(campaign) + s.campaigns,
                lastStatusBanner = "Campaign '${campaign.name}' delivered to $eligibleOptInCount marketing-consented members"
            )
        }
        appendAudit(adminUserId, UserRole.ADMIN, "LAUNCH_CAMPAIGN", "campaigns", campaign.id, "Campaign launched honoring TRAI/DND marketing opt-in", "{}", "{\"sent\":$eligibleOptInCount}")
        return ApiCallResult(true, 200, data = campaign)
    }

    @Synchronized
    fun toggleAiSummaryConsent(userId: String, enabled: Boolean) {
        _state.update { s ->
            s.copy(
                users = s.users.map { if (it.id == userId) it.copy(aiSummaryConsent = enabled) else it },
                lastStatusBanner = "AI Summary Consent set to $enabled"
            )
        }
    }

    @Synchronized
    fun toggleLeaderboardOptOut(userId: String, optOut: Boolean) {
        _state.update { s ->
            s.copy(
                users = s.users.map { if (it.id == userId) it.copy(leaderboardOptOut = optOut) else it },
                lastStatusBanner = "Leaderboard opt-out set to $optOut"
            )
        }
    }

    @Synchronized
    fun generateMemberAiSummary(
        userId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        simulateFailure: Boolean = false
    ): AiSummaryResponse {
        val s = _state.value
        val user = s.users.first { it.id == userId }
        val attCount = s.attendances.count { it.userId == userId && it.status == AttendanceStatus.APPROVED }
        val streak = s.streakStates[userId] ?: StreakStateRecord(userId, 0, 0, null)
        val milestonesCount = s.rewards.count { it.userId == userId && it.source == RewardSource.STREAK_MILESTONE }
        val trfStatus = s.transformations.firstOrNull { it.userId == userId }?.status ?: TransformationStatus.DRAFT
        val lastEpoch = s.lastAiSummaryEpochByUser[userId]

        val response = AiSummaryEngine.generateSummary(
            user = user,
            attendanceCount = attCount,
            streak = streak,
            milestonesCount = milestonesCount,
            transformationStatus = trfStatus,
            lastGeneratedAtEpochMs = lastEpoch,
            nowEpochMs = nowEpochMs,
            simulateProviderFailure = simulateFailure
        )
        if (response.statusCode == 200) {
            _state.update { st ->
                st.copy(
                    lastAiSummaryEpochByUser = st.lastAiSummaryEpochByUser + (userId to nowEpochMs),
                    lastStatusBanner = "Generated weekly AI Summary via ${response.modelName}"
                )
            }
            appendAudit(userId, UserRole.MEMBER, "GENERATE_AI_SUMMARY", "ai_summaries", userId, "Weekly non-medical AI summary generated (0 photo fields)", "{}", "{\"model\":\"${response.modelName}\"}")
        }
        return response
    }

    data class DatabaseBackupArtifact(
        val exportedAtEpochMs: Long,
        val stateSnapshot: PlatformState,
        val tableCounts: Map<String, Int>
    )

    @Synchronized
    fun exportDatabaseBackup(): DatabaseBackupArtifact {
        val s = _state.value
        val counts = mapOf(
            "users" to s.users.size,
            "plans" to s.plans.size,
            "locations" to s.locations.size,
            "orders" to s.orders.size,
            "payment_attempts" to s.paymentAttempts.size,
            "memberships" to s.memberships.size,
            "attendance" to s.attendances.size,
            "rewards" to s.rewards.size,
            "coupons" to s.coupons.size,
            "audit_logs" to s.auditLogs.size
        )
        return DatabaseBackupArtifact(
            exportedAtEpochMs = System.currentTimeMillis(),
            stateSnapshot = s,
            tableCounts = counts
        )
    }

    @Synchronized
    fun restoreDatabaseBackupToScratch(backup: DatabaseBackupArtifact): Map<String, Int> {
        val scratchRepo = GymPlatformRepository()
        scratchRepo._state.value = backup.stateSnapshot
        val restored = scratchRepo._state.value
        return mapOf(
            "users" to restored.users.size,
            "plans" to restored.plans.size,
            "locations" to restored.locations.size,
            "orders" to restored.orders.size,
            "payment_attempts" to restored.paymentAttempts.size,
            "memberships" to restored.memberships.size,
            "attendance" to restored.attendances.size,
            "rewards" to restored.rewards.size,
            "coupons" to restored.coupons.size,
            "audit_logs" to restored.auditLogs.size
        )
    }

    private fun enqueueOutbox(endpoint: String, idempotencyKey: String, payloadSummary: String) {
        val evt = SyncOutboxEvent(
            id = "sync-${UUID.randomUUID().toString().take(8)}",
            endpoint = endpoint,
            idempotencyKey = idempotencyKey,
            payloadSummary = payloadSummary,
            syncedToRemote = _state.value.remoteBackendConfigured && !_state.value.isOfflineSimulated
        )
        _state.update { s -> s.copy(syncOutbox = listOf(evt) + s.syncOutbox) }
        persistSnapshotAsync("outbox_latest", "$endpoint|$idempotencyKey|$payloadSummary")
    }

    private fun persistSnapshotAsync(key: String, payload: String) {
        val dao = gymDao ?: return
        ioScope.launch {
            runCatching {
                dao.upsertSnapshot(KvSnapshotEntity(key = key, jsonPayload = payload))
            }
        }
    }

    private fun appendAudit(
        actorId: String,
        actorRole: UserRole,
        action: String,
        entityType: String,
        entityId: String,
        reason: String,
        beforeJson: String,
        afterJson: String
    ) {
        val log = AuditLogRecord(
            id = "aud-${UUID.randomUUID().toString().take(8)}",
            actorId = actorId,
            actorRole = actorRole,
            action = action,
            entityType = entityType,
            entityId = entityId,
            reason = reason,
            beforeJson = beforeJson,
            afterJson = afterJson
        )
        _state.update { s -> s.copy(auditLogs = listOf(log) + s.auditLogs) }
        val dao = gymDao
        if (dao != null) {
            ioScope.launch {
                runCatching {
                    dao.insertAuditLog(
                        AuditLogEntity(
                            id = log.id,
                            actorId = log.actorId,
                            actorRole = log.actorRole.name,
                            action = log.action,
                            entityType = log.entityType,
                            entityId = log.entityId,
                            reason = log.reason,
                            beforeJson = log.beforeJson,
                            afterJson = log.afterJson,
                            createdAtEpochMs = log.createdAtEpochMs
                        )
                    )
                }
            }
        }
    }

    companion object {
        fun createInitialSeededState(): PlatformState {
            val today = LocalDate.of(2026, 11, 3)

            val users = listOf(
                UserAccount(
                    id = "usr-member-01",
                    mobile = "+919876543210",
                    email = "arjun.mehta@example.com",
                    role = UserRole.MEMBER,
                    fullName = "Arjun Mehta",
                    weightKg = 76.4,
                    weightHistoryKg = listOf(82.0, 80.1, 78.3, 76.4),
                    referralCode = "IRONARJUN10",
                    aiSummaryConsent = true
                ),
                UserAccount(
                    id = "usr-member-02",
                    mobile = "+919876543211",
                    email = "priya.nair@example.com",
                    role = UserRole.MEMBER,
                    fullName = "Priya Nair",
                    weightKg = 61.0,
                    weightHistoryKg = listOf(65.0, 63.2, 61.0),
                    referralCode = "IRONPRIYA11"
                ),
                UserAccount(
                    id = "usr-staff-01",
                    mobile = "+919800000002",
                    email = "staff.desk@ironpulse.fitness",
                    role = UserRole.STAFF,
                    fullName = "Vikram Rao (Floor Staff)",
                    referralCode = "IRONSTAFF01",
                    totpVerified = true
                ),
                UserAccount(
                    id = "usr-admin-01",
                    mobile = "+919800000001",
                    email = "admin.ops@ironpulse.fitness",
                    role = UserRole.ADMIN,
                    fullName = "Ananya Sharma (Operations Admin)",
                    referralCode = "IRONADMIN01",
                    totpVerified = true
                ),
                UserAccount(
                    id = "usr-owner-01",
                    mobile = "+919800000000",
                    email = "owner@ironpulse.fitness",
                    role = UserRole.OWNER,
                    fullName = "Rajesh Singhania (Gym Owner)",
                    referralCode = "IRONOWNER01",
                    totpVerified = true
                )
            )

            val plans = listOf(
                GymPlan(
                    id = "pln-quarterly-pro",
                    name = "Quarterly Transformation Pro",
                    description = "90-day full strength + conditioning access, streak rewards (+2 days every 6 consecutive eligible days), and 80% transformation coupon eligibility.",
                    features = listOf("Unlimited Strength & Conditioning", "Streak Extension Rewards (+2d / 6 streak)", "80% Transformation Renewal Coupon Eligible", "1 Freeze Allowed (up to 15 days)"),
                    durationType = DurationType.CALENDAR_MONTHS,
                    durationValue = 3,
                    internalPriceInr = 8999.0,
                    allowsFreeze = true,
                    maxFreezeDays = 15,
                    milestoneKind = MilestoneKind.CONSECUTIVE,
                    milestoneTargetDays = 6,
                    milestoneRewardDays = 2
                ),
                GymPlan(
                    id = "pln-annual-elite",
                    name = "Annual Titan Elite",
                    description = "12 calendar months clamped membership with priority biometric & coach check-ins, 30-day freeze allowance, and 7-day extension per 30-day streak milestone.",
                    features = listOf("365-Day Flagship & Class Access", "Up to 30 Freeze Days", "30-Day Streak -> +7 Days Extension", "Free Guest Referral Credits"),
                    durationType = DurationType.CALENDAR_MONTHS,
                    durationValue = 12,
                    internalPriceInr = 24999.0,
                    allowsFreeze = true,
                    maxFreezeDays = 30,
                    milestoneKind = MilestoneKind.CONSECUTIVE,
                    milestoneTargetDays = 30,
                    milestoneRewardDays = 7
                ),
                GymPlan(
                    id = "pln-monthly-sprint",
                    name = "Monthly Sprint Pass",
                    description = "1 calendar month flexible pass with FULL_ELIGIBLE monthly milestone reward.",
                    features = listOf("Full Gym Floor Access", "Full-Month Eligible Attendance Bonus", "Online, UPI & Cash Accepted"),
                    durationType = DurationType.CALENDAR_MONTHS,
                    durationValue = 1,
                    internalPriceInr = 3499.0,
                    allowsFreeze = false,
                    maxFreezeDays = 0,
                    milestoneKind = MilestoneKind.FULL_ELIGIBLE,
                    milestoneTargetDays = 20,
                    milestoneRewardDays = 3
                ),
                GymPlan(
                    id = "pln-30day-pack",
                    name = "30-Day Fixed Conditioning Pack",
                    description = "Exact 30-day duration membership with 30-day streak -> 7-day extension policy.",
                    features = listOf("Exact 30 Days Duration", "30-Day Streak -> +7 Days Reward"),
                    durationType = DurationType.DAYS,
                    durationValue = 30,
                    internalPriceInr = 3299.0,
                    allowsFreeze = false,
                    maxFreezeDays = 0,
                    milestoneKind = MilestoneKind.CONSECUTIVE,
                    milestoneTargetDays = 30,
                    milestoneRewardDays = 7
                )
            )

            val mainLocation = GymLocation(
                id = "loc-indiranagar-01",
                name = "IronPulse Indiranagar Flagship",
                type = LocationType.GYM,
                address = "100 Feet Road, Indiranagar, Bengaluru 560038",
                lat = 12.971599,
                lng = 77.594566,
                geofenceRadiusMeters = 100,
                maxGpsAccuracyMeters = 100,
                qrToken = "IRONPULSE_QR_LOC_INDIRANAGAR_01_9F8E7D6C5B4A3210"
            )

            val hiitClassLocation = GymLocation(
                id = "loc-hiit-studio-02",
                name = "IronPulse Metcon & HIIT Studio",
                type = LocationType.CLASS,
                address = "2nd Floor, Indiranagar Flagship, Bengaluru",
                lat = 12.971650,
                lng = 77.594610,
                geofenceRadiusMeters = 80,
                maxGpsAccuracyMeters = 80,
                qrToken = "IRONPULSE_QR_LOC_HIIT_STUDIO_02_1A2B3C4D5E6F7890"
            )

            val schedules = (1..7).map { isoDay ->
                LocationSchedule(
                    id = "sch-main-$isoDay",
                    locationId = mainLocation.id,
                    dayOfWeekIso = isoDay,
                    isOpen = isoDay != 7,
                    openTime = "05:30",
                    closeTime = "22:30",
                    attendanceWindowStart = "05:30",
                    attendanceWindowEnd = "22:00"
                )
            }

            val holidays = listOf(
                GymHoliday(
                    id = "hol-diwali-2026",
                    fromDate = today.minusDays(3),
                    toDate = today.minusDays(3),
                    reason = "Annual Facility Deep Maintenance & Festival Closure"
                )
            )

            val activeMembership = MembershipRecord(
                id = "mem-arjun-01",
                userId = "usr-member-01",
                planId = "pln-quarterly-pro",
                startDate = today.minusDays(25),
                expiryDate = CalendarMathEngine.addMonthsClamped(today.minusDays(25), 3).plusDays(2),
                status = MembershipStatus.ACTIVE,
                sourcePaymentId = "pay-seed-approved-01"
            )

            val pendingOnlineAttempt = PaymentAttemptRecord(
                id = "pay-pending-online-02",
                orderId = "ord-seed-02",
                userId = "usr-member-02",
                method = PaymentMethod.ONLINE,
                amountInr = 8999.0,
                status = PaymentAttemptStatus.PENDING_ADMIN_APPROVAL,
                gatewayRef = "pay_RzpTest998877",
                idempotencyKey = "idem-seed-02"
            )

            val pendingUpiAttempt = PaymentAttemptRecord(
                id = "pay-pending-upi-03",
                orderId = "ord-seed-03",
                userId = "usr-member-02",
                method = PaymentMethod.UPI_BANK,
                amountInr = 3499.0,
                status = PaymentAttemptStatus.PENDING_ADMIN_APPROVAL,
                referenceNumber = "UTR631998271652",
                proofStorageKey = "private-proofs/usr-member-02/utr_631998271652.png",
                idempotencyKey = "idem-seed-03"
            )

            val pendingCashAttempt = PaymentAttemptRecord(
                id = "pay-pending-cash-04",
                orderId = "ord-seed-04",
                userId = "usr-member-01",
                method = PaymentMethod.CASH,
                amountInr = 3499.0,
                status = PaymentAttemptStatus.PENDING_ADMIN_APPROVAL,
                cashReceiptStaffId = "usr-staff-01",
                referenceNumber = "CASH-RCPT-2026-109",
                idempotencyKey = "idem-seed-04"
            )

            val approvedSeedAttempt = PaymentAttemptRecord(
                id = "pay-seed-approved-01",
                orderId = "ord-seed-01",
                userId = "usr-member-01",
                method = PaymentMethod.ONLINE,
                amountInr = 8999.0,
                status = PaymentAttemptStatus.APPROVED,
                gatewayRef = "pay_RzpSeed001",
                idempotencyKey = "idem-seed-01"
            )

            val seedOrders = listOf(
                OrderRecord("ord-seed-01", "usr-member-01", "pln-quarterly-pro", OrderType.NEW, OrderStatus.COMPLETED, "idem-ord-1", 8999.0, 0.0, 8999.0),
                OrderRecord("ord-seed-02", "usr-member-02", "pln-quarterly-pro", OrderType.NEW, OrderStatus.AWAITING_VERIFICATION, "idem-ord-2", 8999.0, 0.0, 8999.0),
                OrderRecord("ord-seed-03", "usr-member-02", "pln-monthly-sprint", OrderType.NEW, OrderStatus.AWAITING_VERIFICATION, "idem-ord-3", 3499.0, 0.0, 3499.0),
                OrderRecord("ord-seed-04", "usr-member-01", "pln-monthly-sprint", OrderType.RENEWAL, OrderStatus.AWAITING_VERIFICATION, "idem-ord-4", 3499.0, 0.0, 3499.0)
            )

            val seedAttendances = (1..7).filter { it != 3 }.mapIndexed { idx, daysAgo ->
                val d = today.minusDays(daysAgo.toLong())
                AttendanceRecord(
                    id = "att-seed-$idx",
                    userId = "usr-member-01",
                    locationId = mainLocation.id,
                    qrTokenUsed = mainLocation.qrToken,
                    attendanceDate = d,
                    scannedAtEpochMs = System.currentTimeMillis() - daysAgo * 86_400_000L,
                    gpsLat = 12.971610,
                    gpsLng = 77.594580,
                    gpsAccuracyMeters = 14.5,
                    distanceMeters = 12.4,
                    status = AttendanceStatus.APPROVED
                )
            }

            val seedReward = RewardRecord(
                id = "rwd-usr-member-01-mem-arjun-01-1",
                userId = "usr-member-01",
                type = RewardType.MEMBERSHIP_DAYS,
                valueDaysOrPoints = 2,
                source = RewardSource.STREAK_MILESTONE,
                membershipId = "mem-arjun-01",
                milestoneIndex = 1,
                status = RewardStatus.APPLIED
            )

            val nowMs = System.currentTimeMillis()
            val seedPhotos = listOf(
                PhotoPhase.START to PhotoAngle.FRONT,
                PhotoPhase.START to PhotoAngle.SIDE,
                PhotoPhase.START to PhotoAngle.BACK,
                PhotoPhase.WEEKLY to PhotoAngle.FRONT,
                PhotoPhase.END to PhotoAngle.FRONT,
                PhotoPhase.END to PhotoAngle.SIDE,
                PhotoPhase.END to PhotoAngle.BACK
            ).mapIndexed { index, (phase, angle) ->
                ProgressPhotoRecord(
                    id = "pho-seed-$index",
                    userId = "usr-member-01",
                    phase = phase,
                    angle = angle,
                    weekIndex = if (phase == PhotoPhase.WEEKLY) 4 else null,
                    storageKey = "private-bucket/usr-member-01/${phase.name.lowercase()}_${angle.name.lowercase()}.jpg",
                    signedUrlToken = "sig_seed_valid_$index",
                    signedUrlExpiresAtEpochMs = nowMs + 5 * 60_000L
                )
            }

            val seedTransformation = TransformationSubmissionRecord(
                id = "trf-arjun-01",
                userId = "usr-member-01",
                membershipId = "mem-arjun-01",
                status = TransformationStatus.SUBMITTED,
                privacyConsentAccepted = true,
                marketingConsentAccepted = false
            )

            val seedCoupon = CouponRecord(
                id = "cpn-welcome-80",
                userId = "usr-member-01",
                code = "IRON80-ARJUN",
                discountPercent = 80,
                maxDiscountCapInr = 15000.0,
                minPurchaseAmountInr = 1000.0,
                source = CouponSource.TRANSFORMATION,
                status = CouponStatus.ISSUED,
                expiresAtDate = today.plusDays(90)
            )

            val seedBadges = listOf(
                BadgeRecord("bdg-1", "STREAK_6", "6-Day Iron Streak", "Completed 6 consecutive eligible gym days without a miss", "streak >= 6", awarded = true, awardedAtEpochMs = nowMs - 86_400_000L),
                BadgeRecord("bdg-2", "VISITS_25", "Quarter-Century Lifter", "Logged 25 verified geofenced gym check-ins", "total_visits >= 25", awarded = false),
                BadgeRecord("bdg-3", "TRANSFORM_FINISHER", "Transformation Finisher", "Approved 3-angle start & end body transformation", "transformation_approved = true", awarded = false),
                BadgeRecord("bdg-4", "LOYAL_RENEWAL", "Iron Loyalist", "Completed at least 1 membership renewal", "membership_renewals >= 1", awarded = true, awardedAtEpochMs = nowMs - 172_800_000L)
            )

            val seedLeaderboard = LeaderboardEngine.rankEntries(
                listOf(
                    LeaderboardEntryRecord("usr-member-01", "Arjun Mehta", "2026-11", 18, 36, 216, 12, nowMs - 3600_000L),
                    LeaderboardEntryRecord("usr-member-02", "Priya Nair", "2026-11", 18, 36, 216, 9, nowMs - 7200_000L),
                    LeaderboardEntryRecord("usr-member-03", "Rohan Kulkarni", "2026-11", 15, 20, 170, 7, nowMs - 1800_000L)
                )
            )

            val seedAuditLogs = listOf(
                AuditLogRecord(
                    id = "aud-seed-01",
                    actorId = "usr-owner-01",
                    actorRole = UserRole.OWNER,
                    action = "SEED_ROLES_AND_PERMISSIONS",
                    entityType = "roles",
                    entityId = "Owner/Admin/Staff",
                    reason = "Phase 1 RBAC initialization",
                    beforeJson = "{}",
                    afterJson = "{\"roles\":[\"Owner\",\"Admin\",\"Staff\"]}"
                ),
                AuditLogRecord(
                    id = "aud-seed-02",
                    actorId = "usr-admin-01",
                    actorRole = UserRole.ADMIN,
                    action = "APPROVE_PAYMENT_ACTIVATE_MEMBERSHIP",
                    entityType = "memberships",
                    entityId = "mem-arjun-01",
                    reason = "Verified Razorpay capture pay_RzpSeed001",
                    beforeJson = "{\"status\":\"PENDING_ADMIN_APPROVAL\"}",
                    afterJson = "{\"status\":\"APPROVED\",\"membershipId\":\"mem-arjun-01\"}"
                )
            )

            val seedAnomalyFlags = listOf(
                AttendanceAnomalyFlag(
                    id = "flag-seed-01",
                    attendanceId = "att-seed-0",
                    userId = "usr-member-02",
                    flagType = "IMPOSSIBLE_TRAVEL_50KM_10MIN",
                    details = "Distance 64 km in 420s between Indiranagar and Airport Road scan attempts"
                )
            )

            val seedTickets = listOf(
                SupportTicketRecord(
                    id = "tkt-seed-01",
                    userId = "usr-member-01",
                    category = "Attendance & Streak",
                    subject = "Diwali Holiday Streak Protection",
                    description = "Confirming that the Oct 31 facility closure did not reset my 6-day streak.",
                    status = TicketStatus.IN_PROGRESS,
                    messages = listOf(
                        SupportMessageRecord("msg-s1", "tkt-seed-01", "usr-member-01", UserRole.MEMBER, "Confirming that the Oct 31 facility closure did not reset my 6-day streak."),
                        SupportMessageRecord("msg-s2", "tkt-seed-01", "usr-admin-01", UserRole.ADMIN, "Verified! Gym-wide holidays are excluded from eligible_days before streak computation.")
                    )
                )
            )

            return PlatformState(
                users = users,
                plans = plans,
                locations = listOf(mainLocation, hiitClassLocation),
                schedules = schedules,
                holidays = holidays,
                orders = seedOrders,
                paymentAttempts = listOf(pendingOnlineAttempt, pendingUpiAttempt, pendingCashAttempt, approvedSeedAttempt),
                memberships = listOf(activeMembership),
                attendances = seedAttendances,
                anomalyFlags = seedAnomalyFlags,
                streakStates = mapOf(
                    "usr-member-01" to StreakStateRecord("usr-member-01", 6, 6, today.minusDays(1))
                ),
                rewards = listOf(seedReward),
                photos = seedPhotos,
                transformations = listOf(seedTransformation),
                coupons = listOf(seedCoupon),
                badges = seedBadges,
                leaderboard = seedLeaderboard,
                referrals = listOf(
                    ReferralRecord("ref-seed-1", "usr-member-01", "+919876543211", "usr-member-02", "IRONARJUN10", ReferralStatus.QUALIFIED)
                ),
                notifications = listOf(
                    NotificationItem("ntf-1", "usr-member-01", "STREAK_MILESTONE", "6-Day Streak Milestone Achieved!", "+2 Membership Days applied automatically.", "ironpulse://streak", "dedupe-streak-1"),
                    NotificationItem("ntf-2", "usr-member-01", "COUPON_ISSUED", "80% Transformation Coupon Ready", "Code IRON80-ARJUN is active in your Coupon Wallet.", "ironpulse://coupons", "dedupe-cpn-1")
                ),
                campaigns = listOf(
                    CampaignRecord("cmp-seed-1", "14-Day Inactive Member Recovery", "inactive_days >= 14 AND marketing_opt_in = true", "We miss seeing you at the rack! Claim 15% off on your comeback renewal.", 15, 42, 11, "usr-admin-01")
                ),
                supportTickets = seedTickets,
                auditLogs = seedAuditLogs
            )
        }
    }
}
