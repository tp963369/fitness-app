package com.example.domain.model

import java.time.LocalDate

enum class UserRole { MEMBER, ADMIN, STAFF, OWNER }
enum class UserStatus { ACTIVE, BLOCKED, DELETED }
enum class PricingVisibility { PUBLIC, LOGIN_REQUIRED, HIDDEN_CALL_TO_ENQUIRE }
enum class AttendanceScope { PER_DAY, PER_CLASS }
enum class DurationType { DAYS, WEEKS, CALENDAR_MONTHS, CALENDAR_YEARS, CUSTOM }
enum class OrderType { NEW, RENEWAL, UPGRADE, DOWNGRADE }
enum class OrderStatus {
    CREATED, AWAITING_PAYMENT, AWAITING_VERIFICATION, COMPLETED, CANCELLED, EXPIRED, FAILED
}
enum class PaymentMethod { ONLINE, UPI_BANK, CASH }
enum class PaymentAttemptStatus {
    INITIATED, PENDING_GATEWAY, GATEWAY_SUCCESS, GATEWAY_FAILED,
    AWAITING_PROOF, PROOF_SUBMITTED, AWAITING_CASH_CONFIRMATION,
    PENDING_ADMIN_APPROVAL, APPROVED, REJECTED, FAILED,
    INVESTIGATION, REFUND_PENDING, REFUNDED, PARTIALLY_REFUNDED, CANCELLED
}
enum class VerificationAction { APPROVE, REJECT, INVESTIGATE, REQUEST_PROOF }
enum class RefundStatus { REQUESTED, PROCESSING, SUCCESS, FAILED }
enum class RefundMembershipImpact { NONE, CANCEL, EXTEND }
enum class MembershipStatus {
    ACTIVE, EXPIRED, FROZEN, FREEZE_REQUESTED, CANCELLED, UPGRADED, DOWNGRADED
}
enum class FreezeStatus { REQUESTED, APPROVED, REJECTED, RESUMED, CANCELLED }
enum class LocationType { GYM, CLASS }
enum class AttendanceStatus { APPROVED, REJECTED }
enum class AttendanceSource { MEMBER, ADMIN }
enum class MilestoneKind { CONSECUTIVE, FULL_ELIGIBLE }
enum class RewardType { MEMBERSHIP_DAYS, COUPON, POINTS }
enum class RewardSource { STREAK_MILESTONE, REFERRAL, MANUAL }
enum class RewardStatus { ISSUED, APPLIED, EXPIRED, REVOKED }
enum class PhotoPhase { START, WEEKLY, END }
enum class PhotoAngle { FRONT, SIDE, BACK }
enum class TransformationStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, RESUBMISSION_REQUESTED
}
enum class TransformationDecision { APPROVE, REJECT, REQUEST_RESUBMISSION }
enum class CouponType { PERCENT, FLAT }
enum class CouponSource { TRANSFORMATION, REFERRAL, CAMPAIGN, PROMO }
enum class CouponStatus { ISSUED, REDEEMED, EXPIRED, REVOKED }
enum class ReferralStatus { PENDING, QUALIFIED, REWARDED, REVOKED }
enum class TicketStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
enum class DeletionStatus { PENDING, CANCELLED, COMPLETED }

data class GymSettings(
    val gymId: String = "gym-ironpulse-01",
    val gymName: String = "IronPulse Flagship Fitness",
    val tagline: String = "Precision Strength, Verified Streaks & Real Transformations",
    val timezone: String = "Asia/Kolkata",
    val contactPhone: String = "+91 80 4567 8900",
    val contactEmail: String = "ops@ironpulse.fitness",
    val address: String = "100 Feet Road, Indiranagar, Bengaluru 560038",
    val pricingVisibility: PricingVisibility = PricingVisibility.PUBLIC,
    val photoRetentionDays: Int = 7,
    val attendanceScope: AttendanceScope = AttendanceScope.PER_DAY,
    val mfaRequired: Boolean = true,
    val strictGeofenceMode: Boolean = false,
    val upiId: String = "ironpulse.gym@okaxis",
    val bankInstructions: String = "HDFC Bank • A/C 50200098765432 • IFSC HDFC0000123",
    val refundPolicyDefault: RefundMembershipImpact = RefundMembershipImpact.NONE,
    val expiryReminderDays: List<Int> = listOf(7, 3, 1),
    val inactiveRecoveryDays: Int = 14,
    val webCatalogueFeatureFlag: Boolean = true
)

data class UserAccount(
    val id: String,
    val mobile: String,
    val email: String? = null,
    val role: UserRole,
    val status: UserStatus = UserStatus.ACTIVE,
    val fullName: String,
    val dob: String = "1996-05-14",
    val gender: String = "Male",
    val heightCm: Int = 176,
    val weightKg: Double = 78.5,
    val weightHistoryKg: List<Double> = listOf(82.0, 80.5, 79.2, 78.5),
    val fitnessGoal: String = "Lean Muscle & 90-Day Transformation",
    val pushEnabled: Boolean = true,
    val marketingOptIn: Boolean = true,
    val aiSummaryConsent: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "08:00",
    val leaderboardOptOut: Boolean = false,
    val referralCode: String,
    val totpVerified: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class GymPlan(
    val id: String,
    val gymId: String = "gym-ironpulse-01",
    val name: String,
    val description: String,
    val features: List<String>,
    val durationType: DurationType,
    val durationValue: Int,
    val publicVisible: Boolean = true,
    val internalPriceInr: Double,
    val pricingVisibilityOverride: PricingVisibility? = null,
    val allowsFreeze: Boolean = true,
    val maxFreezeDays: Int = 30,
    val paymentMethods: List<PaymentMethod> = listOf(PaymentMethod.ONLINE, PaymentMethod.UPI_BANK, PaymentMethod.CASH),
    val partialPaymentEnabled: Boolean = false,
    val partialPaymentThresholdPct: Int = 50,
    val attendanceScopeOverride: AttendanceScope? = null,
    val milestoneKind: MilestoneKind = MilestoneKind.CONSECUTIVE,
    val milestoneTargetDays: Int = 6,
    val milestoneRewardDays: Int = 2,
    val archivedAtEpochMs: Long? = null
)

data class OrderRecord(
    val id: String,
    val userId: String,
    val planId: String,
    val type: OrderType,
    val status: OrderStatus,
    val idempotencyKey: String,
    val originalAmountInr: Double,
    val discountAmountInr: Double,
    val totalAmountInr: Double,
    val couponId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class PaymentAttemptRecord(
    val id: String,
    val orderId: String,
    val userId: String,
    val method: PaymentMethod,
    val amountInr: Double,
    val status: PaymentAttemptStatus,
    val gatewayRef: String? = null,
    val referenceNumber: String? = null,
    val proofStorageKey: String? = null,
    val cashReceiptStaffId: String? = null,
    val cashConfirmedByAdminId: String? = null,
    val idempotencyKey: String,
    val rejectionReason: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class PaymentVerificationLog(
    val id: String,
    val paymentAttemptId: String,
    val actorId: String,
    val actorRole: UserRole,
    val action: VerificationAction,
    val reason: String,
    val beforeState: String,
    val afterState: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class RefundRecord(
    val id: String,
    val paymentAttemptId: String,
    val amountInr: Double,
    val reason: String,
    val gatewayRefundId: String,
    val status: RefundStatus,
    val initiatedBy: String,
    val membershipImpact: RefundMembershipImpact = RefundMembershipImpact.NONE,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null
)

data class MembershipRecord(
    val id: String,
    val userId: String,
    val planId: String,
    val startDate: LocalDate,
    val expiryDate: LocalDate,
    val status: MembershipStatus,
    val sourcePaymentId: String,
    val parentMembershipId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class MembershipFreezeRecord(
    val id: String,
    val membershipId: String,
    val userId: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val reason: String,
    val status: FreezeStatus,
    val approvedBy: String? = null,
    val rejectionReason: String? = null,
    val requestedAtEpochMs: Long = System.currentTimeMillis()
)

data class GymLocation(
    val id: String,
    val gymId: String = "gym-ironpulse-01",
    val name: String,
    val type: LocationType = LocationType.GYM,
    val address: String,
    val lat: Double,
    val lng: Double,
    val geofenceRadiusMeters: Int = 100,
    val maxGpsAccuracyMeters: Int = 100,
    val qrToken: String,
    val isActive: Boolean = true
)

data class LocationSchedule(
    val id: String,
    val locationId: String,
    val dayOfWeekIso: Int, // 1 = Monday .. 7 = Sunday
    val isOpen: Boolean,
    val openTime: String = "05:30",
    val closeTime: String = "22:30",
    val attendanceWindowStart: String = "05:30",
    val attendanceWindowEnd: String = "22:00"
)

data class GymHoliday(
    val id: String,
    val gymId: String = "gym-ironpulse-01",
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val reason: String
)

data class AttendanceRecord(
    val id: String,
    val userId: String,
    val locationId: String,
    val qrTokenUsed: String,
    val attendanceDate: LocalDate,
    val scannedAtEpochMs: Long,
    val gpsLat: Double,
    val gpsLng: Double,
    val gpsAccuracyMeters: Double,
    val distanceMeters: Double,
    val status: AttendanceStatus,
    val rejectReason: String? = null,
    val isCorrection: Boolean = false,
    val correctedBy: String? = null,
    val correctionReason: String? = null,
    val source: AttendanceSource = AttendanceSource.MEMBER
)

data class AttendanceAnomalyFlag(
    val id: String,
    val attendanceId: String,
    val userId: String,
    val flagType: String,
    val details: String,
    val reviewedBy: String? = null,
    val decision: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class StreakStateRecord(
    val userId: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastQualifyingDate: LocalDate?,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class RewardRecord(
    val id: String,
    val userId: String,
    val type: RewardType,
    val valueDaysOrPoints: Int,
    val source: RewardSource,
    val membershipId: String?,
    val milestoneIndex: Int?,
    val status: RewardStatus = RewardStatus.APPLIED,
    val revokedReason: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class ProgressPhotoRecord(
    val id: String,
    val userId: String,
    val phase: PhotoPhase,
    val angle: PhotoAngle,
    val weekIndex: Int? = null,
    val storageKey: String,
    val signedUrlToken: String,
    val signedUrlExpiresAtEpochMs: Long,
    val cacheControl: String = "private, no-store",
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    val deletedAtEpochMs: Long? = null
)

data class TransformationSubmissionRecord(
    val id: String,
    val userId: String,
    val membershipId: String,
    val status: TransformationStatus,
    val privacyConsentAccepted: Boolean,
    val marketingConsentAccepted: Boolean,
    val reviewerId: String? = null,
    val decisionReason: String? = null,
    val issuedCouponCode: String? = null,
    val submittedAtEpochMs: Long = System.currentTimeMillis(),
    val reviewedAtEpochMs: Long? = null
)

data class CouponRecord(
    val id: String,
    val userId: String,
    val code: String,
    val discountPercent: Int = 80,
    val maxDiscountCapInr: Double = 15000.0,
    val minPurchaseAmountInr: Double = 1000.0,
    val source: CouponSource = CouponSource.TRANSFORMATION,
    val singleUse: Boolean = true,
    val stackable: Boolean = false,
    val status: CouponStatus = CouponStatus.ISSUED,
    val expiresAtDate: LocalDate,
    val redeemedOrderId: String? = null,
    val revokedReason: String? = null,
    val issuedAtEpochMs: Long = System.currentTimeMillis()
)

data class BadgeRecord(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val criteriaSummary: String,
    val awarded: Boolean = false,
    val awardedAtEpochMs: Long? = null
)

data class LeaderboardEntryRecord(
    val userId: String,
    val memberName: String,
    val periodYearMonth: String,
    val attendanceDays: Int,
    val streakBonusPoints: Int,
    val totalScore: Int,
    val maxStreak: Int,
    val reachedScoreAtEpochMs: Long,
    val rank: Int = 0
)

data class ReferralRecord(
    val id: String,
    val referrerUserId: String,
    val inviteeMobile: String,
    val inviteeUserId: String? = null,
    val codeUsed: String,
    val status: ReferralStatus,
    val rewardDescription: String = "7 Membership Days",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class NotificationItem(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val dedupeKey: String,
    val isRead: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class CampaignRecord(
    val id: String,
    val name: String,
    val segmentDescription: String,
    val templateBody: String,
    val discountPercent: Int? = null,
    val sentCount: Int = 0,
    val convertedCount: Int = 0,
    val createdBy: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SupportTicketRecord(
    val id: String,
    val userId: String,
    val category: String,
    val subject: String,
    val description: String,
    val status: TicketStatus = TicketStatus.OPEN,
    val messages: List<SupportMessageRecord> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SupportMessageRecord(
    val id: String,
    val ticketId: String,
    val senderId: String,
    val senderRole: UserRole,
    val body: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class AuditLogRecord(
    val id: String,
    val actorId: String,
    val actorRole: UserRole,
    val action: String,
    val entityType: String,
    val entityId: String,
    val reason: String,
    val beforeJson: String,
    val afterJson: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class DeletionRequestRecord(
    val id: String,
    val userId: String,
    val requestedAtDate: LocalDate,
    val coolingEndsAtDate: LocalDate,
    val status: DeletionStatus,
    val hasActiveMembershipWarning: Boolean,
    val adminConfirmedOverride: Boolean = false,
    val piiRedacted: Boolean = false,
    val financialRetained: Boolean = true
)
