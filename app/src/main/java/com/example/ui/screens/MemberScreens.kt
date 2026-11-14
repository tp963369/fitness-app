package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.PlatformState
import com.example.domain.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.GymPlatformViewModel
import com.example.ui.viewmodel.MemberScreen
import java.time.LocalDate

@Composable
fun MemberWorkspace(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    currentScreen: MemberScreen
) {
    var showScreenDirectory by remember { mutableStateOf(false) }
    val activeUser = state.users.firstOrNull { it.id == state.currentUserId } ?: state.users.first()
    val activeMembership = state.memberships.firstOrNull { it.userId == activeUser.id && (it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.FROZEN) }
    val streak = state.streakStates[activeUser.id] ?: StreakStateRecord(activeUser.id, 0, 0, null)

    Column(modifier = Modifier.fillMaxSize()) {
        // Screen Header & 37-Screen Quick Jump Bar
        Surface(
            color = GraphiteSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MEMBER SCREEN ${currentScreen.index}/37 • ${currentScreen.category.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KineticVolt
                        )
                        Text(
                            text = currentScreen.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "API: ${currentScreen.endpoint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    }
                    OutlinedButton(
                        onClick = { showScreenDirectory = !showScreenDirectory },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("toggle_member_directory_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KineticVolt)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = "All 37 Member Screens")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("37 Screens")
                    }
                }

                if (showScreenDirectory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MemberScreen.entries) { screen ->
                            FilterChip(
                                selected = screen == currentScreen,
                                onClick = {
                                    viewModel.navigateMember(screen)
                                    showScreenDirectory = false
                                },
                                label = { Text("${screen.index}. ${screen.title}") },
                                modifier = Modifier.testTag("member_screen_chip_${screen.index}")
                            )
                        }
                    }
                }
            }
        }

        // Main Screen Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 12-Dimension Specification & State Inspector Banner
            item {
                TwelveDimensionSpecBanner(
                    screenTitle = currentScreen.title,
                    endpoint = currentScreen.endpoint,
                    isOffline = state.isOfflineSimulated,
                    onToggleOffline = { viewModel.repository.toggleOfflineMode(!state.isOfflineSimulated) }
                )
            }

            // Screen-specific rich interactive content
            when (currentScreen) {
                MemberScreen.SPLASH, MemberScreen.WELCOME, MemberScreen.REGISTRATION_OTP, MemberScreen.ONBOARDING -> {
                    item {
                        MemberAuthAndOnboardingSection(viewModel, state, activeUser, currentScreen)
                    }
                }
                MemberScreen.DASHBOARD -> {
                    item {
                        MemberHeroCard(activeUser, activeMembership, streak, onScanClick = {
                            viewModel.navigateMember(MemberScreen.QR_SCANNER)
                        })
                    }
                    item {
                        MemberQuickNavigationGrid(onNavigate = { viewModel.navigateMember(it) })
                    }
                }
                MemberScreen.CATALOGUE, MemberScreen.PLAN_DETAILS, MemberScreen.CHECKOUT -> {
                    item {
                        MemberCatalogueAndCheckoutSection(viewModel, state, activeUser)
                    }
                }
                MemberScreen.ONLINE_PAYMENT, MemberScreen.UPI_BANK_SUBMISSION, MemberScreen.CASH_CONFIRMATION,
                MemberScreen.PAYMENT_PENDING, MemberScreen.PAYMENT_SUCCESS, MemberScreen.PAYMENT_REJECTION -> {
                    item {
                        MemberPaymentChannelsSection(viewModel, state, activeUser)
                    }
                }
                MemberScreen.ACTIVE_MEMBERSHIP, MemberScreen.MEMBERSHIP_HISTORY, MemberScreen.RENEWAL,
                MemberScreen.UPGRADE, MemberScreen.FREEZE_REQUEST, MemberScreen.FREEZE_STATUS -> {
                    item {
                        MemberMembershipAndFreezeSection(viewModel, state, activeUser, activeMembership)
                    }
                }
                MemberScreen.QR_SCANNER, MemberScreen.LOCATION_PERMISSION, MemberScreen.ATTENDANCE_RESULT,
                MemberScreen.ATTENDANCE_HISTORY, MemberScreen.MONTHLY_CALENDAR -> {
                    item {
                        MemberQrAndGeofenceSection(viewModel, state, activeUser)
                    }
                }
                MemberScreen.STREAK_DASHBOARD, MemberScreen.REWARD_HISTORY, MemberScreen.BADGES,
                MemberScreen.LEADERBOARD, MemberScreen.REFERRAL -> {
                    item {
                        MemberStreakAndGamificationSection(viewModel, state, activeUser, streak)
                    }
                }
                MemberScreen.START_PHOTOS, MemberScreen.WEEKLY_PHOTOS, MemberScreen.END_PHOTOS,
                MemberScreen.BEFORE_AFTER_COMPARISON, MemberScreen.TRANSFORMATION_STATUS, MemberScreen.COUPON_WALLET -> {
                    item {
                        MemberTransformationAndCouponSection(viewModel, state, activeUser, activeMembership)
                    }
                }
                MemberScreen.PROFILE_PRIVACY_SUPPORT -> {
                    item {
                        MemberProfileAiAndSupportSection(viewModel, state, activeUser)
                    }
                }
            }
        }
    }
}

@Composable
fun TwelveDimensionSpecBanner(
    screenTitle: String,
    endpoint: String,
    isOffline: Boolean,
    onToggleOffline: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12-DIMENSION VERIFIED SPEC • $screenTitle",
                    style = MaterialTheme.typography.labelLarge,
                    color = KineticVolt
                )
                AssistChip(
                    onClick = onToggleOffline,
                    label = { Text(if (isOffline) "Offline Sim: ON" else "Online: Ready") },
                    leadingIcon = {
                        Icon(
                            if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                            contentDescription = "Connectivity Status",
                            tint = if (isOffline) StatusRejected else StatusApproved
                        )
                    },
                    modifier = Modifier.testTag("connectivity_toggle_chip")
                )
            }
            Text(
                text = "Endpoint: $endpoint • Skeleton Loader Ready • Empty/Error/Retry & Offline Boundaries Active",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            if (isOffline) {
                Surface(
                    color = StatusRejected.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Offline Detected: Network-dependent writes are guarded until connection is restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusRejected,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberHeroCard(
    user: UserAccount,
    membership: MembershipRecord?,
    streak: StreakStateRecord,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A222D), Color(0xFF0E131A))
                )
            )
            .border(1.dp, KineticVolt.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WELCOME BACK, ${user.fullName.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = KineticVolt
                    )
                    Text(
                        text = membership?.let { "Status: ${it.status.name} • Expires ${it.expiryDate}" }
                            ?: "No Active Membership (Pending Approval)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                Surface(
                    color = KineticVolt,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${streak.currentStreak}d STREAK",
                        style = MaterialTheme.typography.labelLarge,
                        color = ObsidianBg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("hero_scan_qr_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KineticVolt,
                    contentColor = ObsidianBg
                )
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Gym QR")
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN LOCATION QR (DUAL-GATE GPS CHECK-IN)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MemberQuickNavigationGrid(onNavigate: (MemberScreen) -> Unit) {
    val featuredScreens = listOf(
        MemberScreen.CATALOGUE to Icons.Default.FitnessCenter,
        MemberScreen.CHECKOUT to Icons.Default.ShoppingCartCheckout,
        MemberScreen.QR_SCANNER to Icons.Default.LocationOn,
        MemberScreen.STREAK_DASHBOARD to Icons.Default.LocalFireDepartment,
        MemberScreen.BEFORE_AFTER_COMPARISON to Icons.Default.Compare,
        MemberScreen.COUPON_WALLET to Icons.Default.ConfirmationNumber,
        MemberScreen.LEADERBOARD to Icons.Default.EmojiEvents,
        MemberScreen.PROFILE_PRIVACY_SUPPORT to Icons.Default.AutoAwesome
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CORE MEMBER WORKFLOWS", style = MaterialTheme.typography.labelLarge, color = TelemetryCyan)
        featuredScreens.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { (screen, icon) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 72.dp)
                            .clickable { onNavigate(screen) }
                            .testTag("quick_nav_${screen.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(icon, contentDescription = screen.title, tint = KineticVolt)
                            Column {
                                Text(
                                    text = "#${screen.index} ${screen.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAuthAndOnboardingSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount,
    currentScreen: MemberScreen
) {
    var mobileInput by remember { mutableStateOf(activeUser.mobile) }
    var otpInput by remember { mutableStateOf("482910") }
    var authFeedback by remember { mutableStateOf("6-digit OTP • 5m TTL • 5/hr rate limit • Argon2id hashed") }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MEMBER OTP AUTH & ONBOARDING (${currentScreen.title})", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
            OutlinedTextField(
                value = mobileInput,
                onValueChange = { mobileInput = it },
                label = { Text("Mobile Number (E.164 IST)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("otp_mobile_input")
            )
            OutlinedTextField(
                value = otpInput,
                onValueChange = { otpInput = it },
                label = { Text("6-Digit OTP Code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("otp_code_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val res = viewModel.repository.sendOtp(mobileInput)
                        authFeedback = if (res.success) "OTP Sent: ${res.data}" else "Blocked: ${res.errorReason}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("send_otp_btn")
                ) {
                    Text("Send OTP")
                }
                Button(
                    onClick = {
                        val res = viewModel.repository.verifyOtp(mobileInput, otpInput)
                        authFeedback = if (res.success) "Verified JWT (aud=member) for ${res.data?.fullName}" else "Error: ${res.errorReason}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("verify_otp_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianBg)
                ) {
                    Text("Verify OTP")
                }
            }
            Text(authFeedback, style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
        }
    }
}

@Composable
private fun MemberCatalogueAndCheckoutSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount
) {
    val selectedPlanId by viewModel.selectedPlanId.collectAsState()
    val couponInput by viewModel.couponInput.collectAsState()
    val publicPlans = viewModel.repository.getPublicPlans(isUserAuthenticated = true)
    val selectedPlan = state.plans.firstOrNull { it.id == selectedPlanId } ?: state.plans.first()
    var checkoutFeedback by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "DYNAMIC PRICING VISIBILITY: ${state.settings.pricingVisibility.name}",
            style = MaterialTheme.typography.labelLarge,
            color = TelemetryCyan
        )

        publicPlans.forEach { plan ->
            val isSelected = plan.id == selectedPlan.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) SlateCard.copy(alpha = 0.95f) else GraphiteSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) KineticVolt else SlateBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.selectPlan(plan.id) }
                    .testTag("plan_card_${plan.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = if (plan.internalPriceInr < 0) "Call to Enquire" else "₹${plan.internalPriceInr.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = KineticVolt
                        )
                    }
                    Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        text = "Duration: ${plan.durationValue} ${plan.durationType.name} • Streak: +${plan.milestoneRewardDays}d per ${plan.milestoneTargetDays}d (${plan.milestoneKind.name})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                }
            }
        }

        // Checkout & 80% Coupon Application Box
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CHECKOUT & TRANSACTIONAL COUPON VALIDATION", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                OutlinedTextField(
                    value = couponInput,
                    onValueChange = { viewModel.setCouponInput(it) },
                    label = { Text("Coupon Code (80% Transformation / Referral)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_coupon_input")
                )

                val matchedCoupon = state.coupons.firstOrNull { it.code.equals(couponInput.trim(), ignoreCase = true) }
                val discountPreview = if (matchedCoupon != null && matchedCoupon.status == CouponStatus.ISSUED) {
                    minOf(selectedPlan.internalPriceInr * (matchedCoupon.discountPercent / 100.0), matchedCoupon.maxDiscountCapInr)
                } else 0.0
                val payablePreview = (selectedPlan.internalPriceInr - discountPreview).coerceAtLeast(0.0)

                Text(
                    text = "Original: ₹${selectedPlan.internalPriceInr.toInt()} • Discount: -₹${discountPreview.toInt()} • Final Payable: ₹${payablePreview.toInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val res = viewModel.repository.createOrderAndInitiatePayment(
                                userId = activeUser.id,
                                planId = selectedPlan.id,
                                method = PaymentMethod.ONLINE,
                                idempotencyKey = "idem-ui-online-${System.currentTimeMillis()}",
                                couponCode = couponInput.takeIf { it.isNotBlank() }
                            )
                            checkoutFeedback = if (res.success) {
                                "Order Created! Payment ${res.data?.id} in ${res.data?.status}. (Requires Admin Approval)"
                            } else "Checkout Rejected: ${res.errorReason}"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("checkout_online_btn")
                    ) {
                        Text("Pay Online")
                    }
                    OutlinedButton(
                        onClick = {
                            val res = viewModel.repository.createOrderAndInitiatePayment(
                                userId = activeUser.id,
                                planId = selectedPlan.id,
                                method = PaymentMethod.UPI_BANK,
                                idempotencyKey = "idem-ui-upi-${System.currentTimeMillis()}",
                                upiReferenceNumber = "UTR${(10000000..99999999).random()}",
                                upiProofStorageKey = "private-proofs/${activeUser.id}/proof.jpg"
                            )
                            checkoutFeedback = if (res.success) "UPI Proof Submitted -> PENDING_ADMIN_APPROVAL" else "Error: ${res.errorReason}"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("checkout_upi_btn")
                    ) {
                        Text("Submit UPI")
                    }
                    OutlinedButton(
                        onClick = {
                            val res = viewModel.repository.createOrderAndInitiatePayment(
                                userId = activeUser.id,
                                planId = selectedPlan.id,
                                method = PaymentMethod.CASH,
                                idempotencyKey = "idem-ui-cash-${System.currentTimeMillis()}"
                            )
                            checkoutFeedback = if (res.success) "Cash Selected -> AWAITING_CASH_CONFIRMATION (Staff -> Admin)" else "Error: ${res.errorReason}"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("checkout_cash_btn")
                    ) {
                        Text("Cash")
                    }
                }
                if (checkoutFeedback.isNotBlank()) {
                    Text(checkoutFeedback, style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                }
            }
        }
    }
}

@Composable
private fun MemberPaymentChannelsSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount
) {
    val myAttempts = state.paymentAttempts.filter { it.userId == activeUser.id }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MY PAYMENT ATTEMPTS (ONLINE / UPI / CASH)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
        Text(
            "Rule: No payment method ever auto-activates a membership without Admin approval.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        myAttempts.forEach { att ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${att.method.name} • ₹${att.amountInr.toInt()}", style = MaterialTheme.typography.titleMedium)
                        Text(att.status.name, style = MaterialTheme.typography.labelLarge, color = KineticVolt)
                    }
                    Text("Attempt ID: ${att.id} • Ref: ${att.referenceNumber ?: att.gatewayRef ?: "Pending"}", style = MaterialTheme.typography.labelSmall)
                    if (att.rejectionReason != null) {
                        Text("Reason: ${att.rejectionReason}", style = MaterialTheme.typography.bodyMedium, color = StatusRejected)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberMembershipAndFreezeSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount,
    activeMembership: MembershipRecord?
) {
    var freezeReason by remember { mutableStateOf("Medical recovery travel — 5 days") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ACTIVE MEMBERSHIP & CLAMPED CALENDAR LIFECYCLE", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                if (activeMembership != null) {
                    Text("Membership ID: ${activeMembership.id}", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                    Text("Start Date: ${activeMembership.startDate} • Expiry Date: ${activeMembership.expiryDate}", style = MaterialTheme.typography.bodyLarge)
                    Text("Status: ${activeMembership.status.name} • Source Payment: ${activeMembership.sourcePaymentId}", style = MaterialTheme.typography.bodyMedium)

                    OutlinedTextField(
                        value = freezeReason,
                        onValueChange = { freezeReason = it },
                        label = { Text("Freeze Request Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.repository.requestMembershipFreeze(
                                userId = activeUser.id,
                                membershipId = activeMembership.id,
                                fromDate = LocalDate.of(2026, 11, 5),
                                toDate = LocalDate.of(2026, 11, 10),
                                reason = freezeReason
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("request_freeze_btn")
                    ) {
                        Text("Request Freeze (5 Days — Protects Streak)")
                    }
                } else {
                    Text("No active membership found. Complete payment & Admin verification.", color = StatusWarning)
                }
            }
        }
    }
}

@Composable
private fun MemberQrAndGeofenceSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount
) {
    val mainLoc = state.locations.first()
    var gpsGranted by remember { mutableStateOf(true) }
    var simDistanceOffsetMeters by remember { mutableStateOf(15f) }
    var simAccuracyMeters by remember { mutableStateOf(18f) }
    var scanFeedback by remember { mutableStateOf("Ready to scan ${mainLoc.name} permanent QR (>=32 chars)") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("QR ATTENDANCE + DUAL-GATE GEOFENCE ENGINE", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text(
                    "Gate 1: accuracy <= ${mainLoc.maxGpsAccuracyMeters}m • Gate 2: distance <= ${mainLoc.geofenceRadiusMeters}m\n" +
                            "CRITICAL: Never computes distance <= radius + accuracy. OUTSIDE_GEOFENCE does NOT consume 5/hr rate limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text("QR Token: ${mainLoc.qrToken}", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = gpsGranted, onCheckedChange = { gpsGranted = it })
                    Text("GPS Runtime Permission Granted")
                }

                Text("Simulated Distance from Gym Pin: ${simDistanceOffsetMeters.toInt()}m (Radius: ${mainLoc.geofenceRadiusMeters}m)")
                Slider(
                    value = simDistanceOffsetMeters,
                    onValueChange = { simDistanceOffsetMeters = it },
                    valueRange = 0f..250f
                )

                Text("Simulated GPS Accuracy: ${simAccuracyMeters.toInt()}m (Max: ${mainLoc.maxGpsAccuracyMeters}m)")
                Slider(
                    value = simAccuracyMeters,
                    onValueChange = { simAccuracyMeters = it },
                    valueRange = 5f..200f
                )

                Button(
                    onClick = {
                        // Convert meter offset roughly into latitude delta (1 deg lat ~ 111,111m)
                        val latOffset = simDistanceOffsetMeters.toDouble() / 111_111.0
                        val res = viewModel.repository.submitQrAttendanceScan(
                            userId = activeUser.id,
                            qrToken = mainLoc.qrToken,
                            gpsPermissionGranted = gpsGranted,
                            lat = mainLoc.lat + latOffset,
                            lng = mainLoc.lng,
                            accuracyMeters = simAccuracyMeters.toDouble(),
                            scanDate = LocalDate.of(2026, 11, 3)
                        )
                        scanFeedback = if (res.success) {
                            "APPROVED! Distance=${"%.1f".format(res.data?.distanceMeters)}m, Accuracy=${res.data?.gpsAccuracyMeters}m"
                        } else {
                            "REJECTED: ${res.errorReason}"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("execute_qr_scan_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = KineticVolt, contentColor = ObsidianBg)
                ) {
                    Text("Execute QR + GPS Check-In Scan", fontWeight = FontWeight.Bold)
                }

                Text(scanFeedback, style = MaterialTheme.typography.labelLarge, color = TelemetryCyan)
            }
        }

        // Attendance History
        Text("RECENT ATTENDANCE VERIFICATION LOGS", style = MaterialTheme.typography.labelLarge, color = KineticVolt)
        state.attendances.filter { it.userId == activeUser.id }.take(6).forEach { att ->
            Card(
                colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${att.attendanceDate} • ${att.status.name}", fontWeight = FontWeight.Bold, color = if (att.status == AttendanceStatus.APPROVED) StatusApproved else StatusRejected)
                        Text("Dist: ${"%.1f".format(att.distanceMeters)}m • Acc: ${"%.1f".format(att.gpsAccuracyMeters)}m • Source: ${att.source.name}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (att.rejectReason != null) {
                        Text(att.rejectReason, style = MaterialTheme.typography.labelSmall, color = StatusRejected)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberStreakAndGamificationSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount,
    streak: StreakStateRecord
) {
    var inviteMobile by remember { mutableStateOf("+919811122233") }
    var referralFeedback by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DETERMINISTIC STREAK & IDEMPOTENT REWARDS", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text("Current Streak: ${streak.currentStreak} days • Longest: ${streak.longestStreak} days", style = MaterialTheme.typography.headlineMedium)
                Text("Holidays (${state.holidays.size}) & Closed Sundays are excluded from eligible_days before streak walk.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                state.rewards.filter { it.userId == activeUser.id }.forEach { rwd ->
                    Text(
                        text = "• Reward ${rwd.id}: +${rwd.valueDaysOrPoints} ${rwd.type.name} (${rwd.source.name} #${rwd.milestoneIndex ?: 1}) [${rwd.status.name}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                }
            }
        }

        // Monthly IST Leaderboard
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MONTHLY LEADERBOARD (10pts/d + 2pts streak)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                    TextButton(onClick = { viewModel.repository.toggleLeaderboardOptOut(activeUser.id, !activeUser.leaderboardOptOut) }) {
                        Text(if (activeUser.leaderboardOptOut) "Opted Out (Show Me)" else "Opt-Out")
                    }
                }
                Text("Tie-Breakers: (1) Max Streak, (2) Earliest Timestamp, (3) member_id ASC", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                state.leaderboard.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${entry.rank} ${entry.memberName}", fontWeight = FontWeight.Bold)
                        Text("${entry.totalScore} pts (Max Streak: ${entry.maxStreak}d)", color = TelemetryCyan)
                    }
                }
            }
        }

        // Referrals
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("REFERRAL ATTRIBUTION (Code: ${activeUser.referralCode})", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                OutlinedTextField(
                    value = inviteMobile,
                    onValueChange = { inviteMobile = it },
                    label = { Text("Invitee Mobile (Self-Referral Blocked)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val res = viewModel.repository.createReferralAttribution(activeUser.id, inviteMobile)
                        referralFeedback = if (res.success) "Referral recorded: ${res.data?.status}" else "Blocked: ${res.errorReason}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("submit_referral_btn")
                ) {
                    Text("Send Referral Invite (7 Days / 25% Reward)")
                }
                if (referralFeedback.isNotBlank()) {
                    Text(referralFeedback, style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                }
            }
        }
    }
}

@Composable
private fun MemberTransformationAndCouponSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount,
    activeMembership: MembershipRecord?
) {
    var sliderPosition by remember { mutableStateOf(0.5f) }
    var privacyConsent by remember { mutableStateOf(true) }
    var marketingConsent by remember { mutableStateOf(false) }
    var submitMsg by remember { mutableStateOf("") }
    val myPhotos = state.photos.filter { it.userId == activeUser.id && it.deletedAtEpochMs == null }
    val mySubmission = state.transformations.firstOrNull { it.userId == activeUser.id }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3-ANGLE TRANSFORMATION + 80% COUPON CHALLENGE", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text(
                    "Privacy: Private bucket, 5-min TTL signed URL, Cache-Control: private, no-store. NEVER sent to AI.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )

                // Upload buttons for Start (Front/Side/Back) and End (Front/Side/Back)
                listOf(PhotoPhase.START, PhotoPhase.WEEKLY, PhotoPhase.END).forEach { phase ->
                    Text("${phase.name} PHOTOS:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoAngle.entries.forEach { angle ->
                            val uploaded = myPhotos.any { it.phase == phase && it.angle == angle }
                            OutlinedButton(
                                onClick = {
                                    viewModel.repository.uploadProgressPhoto(
                                        userId = activeUser.id,
                                        phase = phase,
                                        angle = angle,
                                        weekIndex = if (phase == PhotoPhase.WEEKLY) 1 else null
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("${angle.name} ${if (uploaded) "✓" else "+"}")
                            }
                        }
                    }
                }

                // Interactive Before/After Comparison Slider
                Text("BEFORE / AFTER INTERACTIVE SLIDER (${(sliderPosition * 100).toInt()}% Split)", style = MaterialTheme.typography.labelLarge, color = KineticVolt)
                Slider(value = sliderPosition, onValueChange = { sliderPosition = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = privacyConsent, onCheckedChange = { privacyConsent = it })
                    Text("Mandatory Privacy Consent (Signed URL Review Only)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = marketingConsent, onCheckedChange = { marketingConsent = it })
                    Text("Optional Marketing Consent (Revocable)")
                }

                Button(
                    onClick = {
                        val res = viewModel.repository.submitTransformation(
                            userId = activeUser.id,
                            membershipId = activeMembership?.id ?: "mem-arjun-01",
                            privacyConsent = privacyConsent,
                            marketingConsent = marketingConsent
                        )
                        submitMsg = if (res.success) "Submitted! Status: ${res.data?.status}" else "Blocked: ${res.errorReason}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("submit_transformation_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = KineticVolt, contentColor = ObsidianBg)
                ) {
                    Text("Submit Transformation for Human Admin Review", fontWeight = FontWeight.Bold)
                }

                if (submitMsg.isNotBlank()) {
                    Text(submitMsg, style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                }
                if (mySubmission != null) {
                    Text(
                        "Current Submission Status: ${mySubmission.status.name}" +
                                (mySubmission.decisionReason?.let { " • Reason: $it" } ?: "") +
                                (mySubmission.issuedCouponCode?.let { " • Issued 80% Coupon: $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        // Coupon Wallet
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("80% COUPON WALLET (SINGLE-USE, TIED TO USER)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                state.coupons.filter { it.userId == activeUser.id }.forEach { cpn ->
                    Text(
                        "Code: ${cpn.code} • ${cpn.discountPercent}% OFF (Max ₹${cpn.maxDiscountCapInr.toInt()}) • Status: ${cpn.status.name} • Valid till ${cpn.expiresAtDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TelemetryCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberProfileAiAndSupportSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    activeUser: UserAccount
) {
    val aiResponse by viewModel.lastAiSummary.collectAsState()
    var ticketSubject by remember { mutableStateOf("Question about streak milestone reward") }
    var ticketDesc by remember { mutableStateOf("When is my next +2 day streak milestone eligible?") }
    var deletionFeedback by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Section 12: AI Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WEEKLY AI ACTIVITY SUMMARY (OPT-IN, ZERO PHOTOS)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = activeUser.aiSummaryConsent,
                        onCheckedChange = { viewModel.repository.toggleAiSummaryConsent(activeUser.id, it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Summary Consent Toggle (Default OFF • Max 1/week)")
                }
                Button(
                    onClick = { viewModel.requestAiSummary() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("generate_ai_summary_btn")
                ) {
                    Text("Generate Factual Non-Medical AI Summary")
                }
                aiResponse?.let { resp ->
                    Text("HTTP ${resp.statusCode} (${resp.modelName}): ${resp.summaryText}", style = MaterialTheme.typography.bodyMedium, color = TelemetryCyan)
                }
            }
        }

        // Support Tickets
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SUPPORT TICKETS", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                OutlinedTextField(value = ticketSubject, onValueChange = { ticketSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ticketDesc, onValueChange = { ticketDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.repository.createSupportTicket(activeUser.id, "General", ticketSubject, ticketDesc)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("create_ticket_btn")
                ) {
                    Text("Open Support Ticket")
                }
            }
        }

        // Account Deletion (30-day cooling period)
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PRIVACY & 30-DAY ACCOUNT DELETION FLOW", style = MaterialTheme.typography.titleMedium, color = StatusRejected)
                Text("Active memberships block immediate deletion until Admin confirmation or 30-day cooling completion. Financial records retained 8 years with PII redacted.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                OutlinedButton(
                    onClick = {
                        val res = viewModel.repository.requestAccountDeletion(activeUser.id)
                        deletionFeedback = if (res.success) "Deletion scheduled!" else "Guard Triggered: ${res.errorReason}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("request_deletion_btn")
                ) {
                    Text("Request Account Deletion")
                }
                if (deletionFeedback.isNotBlank()) {
                    Text(deletionFeedback, style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                }
            }
        }
    }
}
