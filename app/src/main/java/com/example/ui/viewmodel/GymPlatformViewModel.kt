package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.repository.GymPlatformRepository
import com.example.data.repository.PlatformState
import com.example.domain.engine.AiSummaryResponse
import com.example.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class MemberScreen(val index: Int, val title: String, val category: String, val endpoint: String) {
    SPLASH(1, "Splash & Telemetry Init", "Onboarding", "GET /public/plans"),
    WELCOME(2, "Welcome & Brand Showcase", "Onboarding", "GET /public/plans"),
    REGISTRATION_OTP(3, "Mobile Registration + 6-Digit OTP", "Onboarding", "POST /auth/otp/send & /verify"),
    ONBOARDING(4, "Athlete Profile Onboarding", "Onboarding", "PUT /me/profile"),
    DASHBOARD(5, "Member Command Dashboard", "Core", "GET /me/dashboard"),
    CATALOGUE(6, "Plan Catalogue (Visibility-Gated)", "Plans & Checkout", "GET /public/plans"),
    PLAN_DETAILS(7, "Plan Details & Policy Breakdown", "Plans & Checkout", "GET /public/plans/:id"),
    CHECKOUT(8, "Checkout & 80% Coupon Validator", "Plans & Checkout", "POST /orders & /coupons/validate"),
    ONLINE_PAYMENT(9, "Online Gateway Payment (Razorpay)", "Payments", "POST /payments/online/initiate"),
    UPI_BANK_SUBMISSION(10, "UPI / Bank Proof Submission", "Payments", "POST /payments/upi-bank/submit"),
    CASH_CONFIRMATION(11, "Cash Payment Selection & Staff Code", "Payments", "POST /payments/cash/select"),
    PAYMENT_PENDING(12, "Payment Pending Admin Verification", "Payments", "GET /payments/:id/status"),
    PAYMENT_SUCCESS(13, "Payment Approved & Receipt", "Payments", "GET /payments/:id/status"),
    PAYMENT_REJECTION(14, "Payment Rejected & Retry", "Payments", "GET /payments/:id/status"),
    ACTIVE_MEMBERSHIP(15, "Active Membership Pass", "Membership", "GET /me/membership"),
    MEMBERSHIP_HISTORY(16, "Membership & Event History", "Membership", "GET /me/memberships/history"),
    RENEWAL(17, "Membership Renewal (Clamped Dates)", "Membership", "POST /orders (type=renewal)"),
    UPGRADE(18, "Plan Upgrade & Proration", "Membership", "POST /orders (type=upgrade)"),
    FREEZE_REQUEST(19, "Request Membership Freeze", "Membership", "POST /me/membership/freeze-request"),
    FREEZE_STATUS(20, "Freeze Status & Resume Tracker", "Membership", "POST /me/membership/freeze-cancel"),
    QR_SCANNER(21, "Permanent QR Attendance Scanner", "Attendance & GPS", "GET /attendance/precheck"),
    LOCATION_PERMISSION(22, "Dual-Gate GPS Permission & Precheck", "Attendance & GPS", "POST /attendance/scan"),
    ATTENDANCE_RESULT(23, "Attendance Verification Telemetry", "Attendance & GPS", "POST /attendance/scan"),
    ATTENDANCE_HISTORY(24, "Attendance Log & Accuracy History", "Attendance & GPS", "GET /me/attendance"),
    MONTHLY_CALENDAR(25, "IST Monthly Attendance Calendar", "Attendance & GPS", "GET /me/attendance/calendar"),
    STREAK_DASHBOARD(26, "Deterministic Streak Dashboard", "Streaks & Gamification", "GET /me/streak"),
    REWARD_HISTORY(27, "Idempotent Milestone Rewards", "Streaks & Gamification", "GET /me/rewards"),
    BADGES(28, "Earned Athlete Badges", "Streaks & Gamification", "GET /me/badges"),
    LEADERBOARD(29, "Monthly IST Leaderboard", "Streaks & Gamification", "GET /leaderboard"),
    REFERRAL(30, "Invite Friends & Referral Tracker", "Streaks & Gamification", "GET /me/referrals"),
    START_PHOTOS(31, "Start Photos (Front / Side / Back)", "Transformation", "POST /me/photos/signature"),
    WEEKLY_PHOTOS(32, "Weekly Progress Check-In Photos", "Transformation", "POST /me/photos/confirm"),
    END_PHOTOS(33, "End Photos (Front / Side / Back)", "Transformation", "POST /me/photos/confirm"),
    BEFORE_AFTER_COMPARISON(34, "Interactive Before/After Slider", "Transformation", "GET /me/photos/compare"),
    TRANSFORMATION_STATUS(35, "Transformation Submission & Review", "Transformation", "POST /me/transformation/submit"),
    COUPON_WALLET(36, "80% Transformation Coupon Wallet", "Transformation", "GET /me/coupons"),
    PROFILE_PRIVACY_SUPPORT(37, "Profile, AI Summary, Privacy & Support", "Account & Support", "GET /me/profile & /support/tickets")
}

enum class AdminScreen(val index: Int, val title: String, val category: String, val endpoint: String) {
    LOGIN(1, "Admin / Staff Argon2id Login", "Auth & Security", "POST /admin/auth/login"),
    TOTP(2, "Mandatory TOTP 2FA Verification", "Auth & Security", "POST /admin/auth/totp/verify"),
    OVERVIEW(3, "Executive KPI & Queue Overview", "Operations", "GET /admin/overview"),
    MEMBERS(4, "Member Directory & Search", "Members", "GET /admin/members"),
    MEMBER_DETAIL(5, "Member 360° Dossier", "Members", "GET /admin/members/:id"),
    PLANS_LIST(6, "Plans & Duration Catalogue", "Plans & Pricing", "GET /admin/plans"),
    PLAN_EDIT(7, "Plan Builder & Policy Overrides", "Plans & Pricing", "POST/PUT /admin/plans"),
    PRICING_VISIBILITY(8, "Dynamic Pricing Visibility Engine", "Plans & Pricing", "PUT /admin/settings"),
    PAYMENT_APPROVAL_CENTRE(9, "Payment Approval Centre (7 Sub-Tabs)", "Payments & Refunds", "GET /admin/payments"),
    PAYMENT_DETAIL(10, "Payment Verification & Proof Inspector", "Payments & Refunds", "POST /admin/payments/:id/approve"),
    REFUND_MANAGEMENT(11, "Refund Processor & Membership Impact", "Payments & Refunds", "POST /admin/payments/:id/refund"),
    MEMBERSHIPS(12, "Active / Frozen / Expired Memberships", "Memberships", "GET /admin/memberships"),
    FREEZE_APPROVALS(13, "Freeze Request Approval Queue", "Memberships", "POST /admin/memberships/:id/freeze/:reqId/approve"),
    LOCATIONS(14, "Gym & Class Locations Registry", "Locations & QR", "GET /admin/locations"),
    LOCATION_EDIT(15, "Geofence Radius & Max GPS Accuracy", "Locations & QR", "PUT /admin/locations"),
    QR_PRINT_DOWNLOAD(16, "Permanent QR (>=32 chars) & Regenerate", "Locations & QR", "POST /admin/locations/:id/qr/regenerate"),
    SCHEDULE(17, "Weekly Operating & Attendance Windows", "Locations & QR", "PUT /admin/schedule"),
    HOLIDAYS(18, "Gym Holidays & Retroactive Recompute", "Locations & QR", "POST /admin/holidays"),
    ATTENDANCE_MONITORING(19, "Live Attendance & Dual-Gate Telemetry", "Attendance & Streaks", "GET /admin/attendance"),
    ATTENDANCE_CORRECTION(20, "Manual Attendance Correction", "Attendance & Streaks", "POST /admin/attendance/correct"),
    ANOMALY_REVIEW(21, "GPS Anomaly & Anti-Fraud Review Queue", "Attendance & Streaks", "POST /admin/anomalies/:id/review"),
    STREAK_MANAGEMENT(22, "Member Streak Inspector & Recompute", "Attendance & Streaks", "GET /admin/streaks"),
    REWARDS_RULES(23, "Streak Milestone Rules & Reward Reversal", "Attendance & Streaks", "POST /admin/rewards/:id/reverse"),
    TRANSFORMATION_REVIEW(24, "Transformation Review Queue", "Transformations & Coupons", "GET /admin/transformations"),
    TRANSFORMATION_DETAIL(25, "Side-by-Side Slider & 80% Coupon Decision", "Transformations & Coupons", "POST /admin/transformations/:id/approve"),
    COUPON_RULES(26, "80% Coupon Rules & Cap Configuration", "Transformations & Coupons", "PUT /admin/coupons/rules"),
    COUPON_LIST(27, "Issued Coupons & Mandatory-Reason Revoke", "Transformations & Coupons", "POST /admin/coupons/:id/revoke"),
    CAMPAIGN_BUILDER(28, "Inactive Recovery & Renewal Campaigns", "Marketing & Analytics", "POST /admin/campaigns"),
    CAMPAIGN_DELIVERY(29, "Campaign Delivery & TRAI/DND Report", "Marketing & Analytics", "GET /admin/campaigns/:id/delivery"),
    REVENUE_RETENTION_ANALYTICS(30, "Revenue, Cohort Retention & CSV Export", "Marketing & Analytics", "GET /admin/analytics/retention"),
    ROLES_PERMISSIONS_SETTINGS(31, "RBAC Matrix (Owner / Admin / Staff)", "Governance", "PUT /admin/roles"),
    AUDIT_LOGS_SUPPORT(32, "Append-Only Audit Logs & Support Desk", "Governance", "GET /admin/audit-logs")
}

class GymPlatformViewModel(
    val repository: GymPlatformRepository = GymPlatformRepository()
) : ViewModel() {

    val state: StateFlow<PlatformState> = repository.state

    private val _currentMemberScreen = MutableStateFlow(MemberScreen.DASHBOARD)
    val currentMemberScreen: StateFlow<MemberScreen> = _currentMemberScreen.asStateFlow()

    private val _currentAdminScreen = MutableStateFlow(AdminScreen.OVERVIEW)
    val currentAdminScreen: StateFlow<AdminScreen> = _currentAdminScreen.asStateFlow()

    private val _selectedPlanId = MutableStateFlow("pln-quarterly-pro")
    val selectedPlanId: StateFlow<String> = _selectedPlanId.asStateFlow()

    private val _couponInput = MutableStateFlow("IRON80-ARJUN")
    val couponInput: StateFlow<String> = _couponInput.asStateFlow()

    private val _lastAiSummary = MutableStateFlow<AiSummaryResponse?>(null)
    val lastAiSummary: StateFlow<AiSummaryResponse?> = _lastAiSummary.asStateFlow()

    private val _paymentFilterTab = MutableStateFlow("ALL")
    val paymentFilterTab: StateFlow<String> = _paymentFilterTab.asStateFlow()

    fun navigateMember(screen: MemberScreen) {
        _currentMemberScreen.value = screen
    }

    fun navigateAdmin(screen: AdminScreen) {
        _currentAdminScreen.value = screen
    }

    fun selectPlan(planId: String) {
        _selectedPlanId.value = planId
    }

    fun setCouponInput(code: String) {
        _couponInput.value = code
    }

    fun setPaymentFilterTab(tab: String) {
        _paymentFilterTab.value = tab
    }

    fun requestAiSummary() {
        val userId = state.value.currentUserId
        _lastAiSummary.value = repository.generateMemberAiSummary(userId)
    }
}
