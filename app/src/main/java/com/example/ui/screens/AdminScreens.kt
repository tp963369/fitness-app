package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.PlatformState
import com.example.domain.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminScreen
import com.example.ui.viewmodel.GymPlatformViewModel
import java.time.LocalDate

@Composable
fun AdminWorkspace(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    currentScreen: AdminScreen
) {
    var showAdminDirectory by remember { mutableStateOf(false) }
    var mandatoryAuditReason by remember { mutableStateOf("Verified per IronPulse SOP") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Admin Header & 32-Screen Directory Bar
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
                            text = "ADMIN CONSOLE ${currentScreen.index}/32 • ROLE: ${state.activeRole.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                        Text(
                            text = currentScreen.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Endpoint: ${currentScreen.endpoint} • This action will be recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = KineticVolt
                        )
                    }
                    OutlinedButton(
                        onClick = { showAdminDirectory = !showAdminDirectory },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("toggle_admin_directory_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TelemetryCyan)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "All 32 Admin Screens")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("32 Screens")
                    }
                }

                if (showAdminDirectory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AdminScreen.entries) { screen ->
                            FilterChip(
                                selected = screen == currentScreen,
                                onClick = {
                                    viewModel.navigateAdmin(screen)
                                    showAdminDirectory = false
                                },
                                label = { Text("${screen.index}. ${screen.title}") },
                                modifier = Modifier.testTag("admin_screen_chip_${screen.index}")
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Mandatory Typed Audit Reason Input Bar for Admin Actions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "MANDATORY AUDIT REASON (Append-Only Audit Log Enforcement)",
                            style = MaterialTheme.typography.labelLarge,
                            color = KineticVolt
                        )
                        OutlinedTextField(
                            value = mandatoryAuditReason,
                            onValueChange = { mandatoryAuditReason = it },
                            label = { Text("Typed Reason for Admin Approvals / Rejections / Corrections") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_audit_reason_input")
                        )
                        Text(
                            "Notice: This action will be recorded in audit_logs (INSERT/SELECT only).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Admin Quick Navigation Bar
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val keyScreens = listOf(
                        AdminScreen.OVERVIEW,
                        AdminScreen.PAYMENT_APPROVAL_CENTRE,
                        AdminScreen.REFUND_MANAGEMENT,
                        AdminScreen.FREEZE_APPROVALS,
                        AdminScreen.QR_PRINT_DOWNLOAD,
                        AdminScreen.HOLIDAYS,
                        AdminScreen.ATTENDANCE_CORRECTION,
                        AdminScreen.ANOMALY_REVIEW,
                        AdminScreen.REWARDS_RULES,
                        AdminScreen.TRANSFORMATION_REVIEW,
                        AdminScreen.CAMPAIGN_BUILDER,
                        AdminScreen.AUDIT_LOGS_SUPPORT
                    )
                    items(keyScreens) { scr ->
                        AssistChip(
                            onClick = { viewModel.navigateAdmin(scr) },
                            label = { Text("#${scr.index} ${scr.title}") }
                        )
                    }
                }
            }

            when (currentScreen) {
                AdminScreen.LOGIN, AdminScreen.TOTP, AdminScreen.OVERVIEW, AdminScreen.MEMBERS, AdminScreen.MEMBER_DETAIL -> {
                    item {
                        AdminOverviewAndAuthSection(viewModel, state)
                    }
                }
                AdminScreen.PLANS_LIST, AdminScreen.PLAN_EDIT, AdminScreen.PRICING_VISIBILITY -> {
                    item {
                        AdminPlansAndPricingSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.PAYMENT_APPROVAL_CENTRE, AdminScreen.PAYMENT_DETAIL, AdminScreen.REFUND_MANAGEMENT -> {
                    item {
                        AdminPaymentApprovalAndRefundSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.MEMBERSHIPS, AdminScreen.FREEZE_APPROVALS -> {
                    item {
                        AdminMembershipsAndFreezesSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.LOCATIONS, AdminScreen.LOCATION_EDIT, AdminScreen.QR_PRINT_DOWNLOAD,
                AdminScreen.SCHEDULE, AdminScreen.HOLIDAYS -> {
                    item {
                        AdminLocationsQrAndHolidaysSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.ATTENDANCE_MONITORING, AdminScreen.ATTENDANCE_CORRECTION, AdminScreen.ANOMALY_REVIEW,
                AdminScreen.STREAK_MANAGEMENT, AdminScreen.REWARDS_RULES -> {
                    item {
                        AdminAttendanceAnomaliesAndStreaksSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.TRANSFORMATION_REVIEW, AdminScreen.TRANSFORMATION_DETAIL,
                AdminScreen.COUPON_RULES, AdminScreen.COUPON_LIST -> {
                    item {
                        AdminTransformationAndCouponsSection(viewModel, state, mandatoryAuditReason)
                    }
                }
                AdminScreen.CAMPAIGN_BUILDER, AdminScreen.CAMPAIGN_DELIVERY,
                AdminScreen.REVENUE_RETENTION_ANALYTICS, AdminScreen.ROLES_PERMISSIONS_SETTINGS,
                AdminScreen.AUDIT_LOGS_SUPPORT -> {
                    item {
                        AdminCampaignsAnalyticsAndAuditSection(viewModel, state, mandatoryAuditReason)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewAndAuthSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState
) {
    var totpCode by remember { mutableStateOf("654321") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                modifier = Modifier.weight(1f),
                label = "Pending Payments",
                value = "${state.paymentAttempts.count { it.status == PaymentAttemptStatus.PENDING_ADMIN_APPROVAL || it.status == PaymentAttemptStatus.AWAITING_CASH_CONFIRMATION }}",
                accent = KineticVolt
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                label = "Active Members",
                value = "${state.memberships.count { it.status == MembershipStatus.ACTIVE }}",
                accent = StatusApproved
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                label = "GPS Anomalies",
                value = "${state.anomalyFlags.size}",
                accent = StatusRejected
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ADMIN ARGON2ID + MANDATORY TOTP 2FA", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it },
                    label = { Text("6-Digit Authenticator TOTP") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.repository.verifyAdminTotp(state.currentUserId, totpCode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Verify Admin TOTP Session (8h Absolute / 30m Idle)")
                }
            }
        }

        // Member Directory Table
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MEMBER DIRECTORY (${state.users.size} Accounts)", style = MaterialTheme.typography.titleMedium, color = TelemetryCyan)
                state.users.forEach { u ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${u.fullName} (${u.role.name})", fontWeight = FontWeight.Bold)
                        Text("${u.mobile} • ${u.status.name}", style = MaterialTheme.typography.labelSmall, color = KineticVolt)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(modifier: Modifier = Modifier, label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@Composable
private fun AdminPlansAndPricingSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PRICING VISIBILITY ENGINE (OWNER ONLY)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text("Current Mode: ${state.settings.pricingVisibility.name} (Never hardcoded in frontend)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PricingVisibility.entries.forEach { vis ->
                        OutlinedButton(
                            onClick = {
                                viewModel.repository.updatePricingVisibility(
                                    actorId = state.currentUserId,
                                    actorRole = state.activeRole,
                                    visibility = vis,
                                    reason = reason
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(vis.name.take(8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPaymentApprovalAndRefundSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    val activeTab by viewModel.paymentFilterTab.collectAsState()
    val tabs = listOf("ALL", "ONLINE", "UPI_BANK", "CASH", "PENDING", "APPROVED", "REJECTED", "REFUNDED")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PAYMENT APPROVAL CENTRE (7 SUB-TABS)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs) { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { viewModel.setPaymentFilterTab(tab) },
                    label = { Text(tab) }
                )
            }
        }

        val filteredAttempts = state.paymentAttempts.filter { att ->
            when (activeTab) {
                "ONLINE" -> att.method == PaymentMethod.ONLINE
                "UPI_BANK" -> att.method == PaymentMethod.UPI_BANK
                "CASH" -> att.method == PaymentMethod.CASH
                "PENDING" -> att.status == PaymentAttemptStatus.PENDING_ADMIN_APPROVAL || att.status == PaymentAttemptStatus.AWAITING_CASH_CONFIRMATION
                "APPROVED" -> att.status == PaymentAttemptStatus.APPROVED
                "REJECTED" -> att.status == PaymentAttemptStatus.REJECTED
                "REFUNDED" -> att.status == PaymentAttemptStatus.REFUNDED
                else -> true
            }
        }

        filteredAttempts.forEach { att ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${att.id} • ${att.method.name} • ₹${att.amountInr.toInt()}", fontWeight = FontWeight.Bold)
                        Text(att.status.name, color = KineticVolt, style = MaterialTheme.typography.labelLarge)
                    }
                    Text("User: ${att.userId} • Ref: ${att.referenceNumber ?: att.gatewayRef ?: "N/A"}", style = MaterialTheme.typography.labelSmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (att.method == PaymentMethod.CASH && att.cashReceiptStaffId == null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.repository.recordCashByStaff(
                                        staffUserId = state.currentUserId,
                                        staffRole = state.activeRole,
                                        paymentAttemptId = att.id,
                                        amountReceivedInr = att.amountInr,
                                        receiptReference = "CASH-STAFF-${(100..999).random()}"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Staff Record Cash")
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.repository.approvePayment(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    paymentAttemptId = att.id,
                                    reason = reason
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("admin_approve_payment_${att.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved, contentColor = ObsidianBg)
                        ) {
                            Text("Approve & Activate")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.repository.rejectPayment(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    paymentAttemptId = att.id,
                                    reason = reason
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("admin_reject_payment_${att.id}")
                        ) {
                            Text("Reject")
                        }
                        if (att.status == PaymentAttemptStatus.APPROVED) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.repository.initiateRefund(
                                        adminUserId = state.currentUserId,
                                        adminRole = state.activeRole,
                                        paymentAttemptId = att.id,
                                        amountInr = att.amountInr,
                                        reason = reason,
                                        membershipImpact = RefundMembershipImpact.CANCEL
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refund")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMembershipsAndFreezesSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FREEZE REQUEST APPROVALS & RESUME", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
        if (state.freezes.isEmpty()) {
            Text("No pending freeze requests.", color = TextSecondary)
        }
        state.freezes.forEach { frz ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Freeze ${frz.id} • Membership ${frz.membershipId} • Status: ${frz.status.name}", fontWeight = FontWeight.Bold)
                    Text("Dates: ${frz.fromDate} to ${frz.toDate} • Reason: ${frz.reason}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.repository.approveMembershipFreeze(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    freezeId = frz.id,
                                    reason = reason
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve Freeze")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.repository.resumeFrozenMembership(state.currentUserId, frz.id)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resume & Extend Expiry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLocationsQrAndHolidaysSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.locations.forEach { loc ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${loc.name} (${loc.type.name})", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                    Text("GPS: (${loc.lat}, ${loc.lng}) • Geofence: ${loc.geofenceRadiusMeters}m • Max Acc: ${loc.maxGpsAccuracyMeters}m", style = MaterialTheme.typography.bodyMedium)
                    Text("Permanent QR (>=32 chars): ${loc.qrToken}", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                    Button(
                        onClick = {
                            viewModel.repository.regenerateLocationQr(
                                actorId = state.currentUserId,
                                actorRole = state.activeRole,
                                locationId = loc.id,
                                reason = reason
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("regenerate_qr_${loc.id}")
                    ) {
                        Text("Regenerate QR Token (Revokes Old Token + Audit Log)")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GYM HOLIDAYS (RETROACTIVE SAFE STREAK RECOMPUTE)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text("Adding a holiday excludes it from eligible_days and triggers recompute without clawing back issued rewards.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Button(
                    onClick = {
                        viewModel.repository.addHoliday(
                            actorId = state.currentUserId,
                            actorRole = state.activeRole,
                            fromDate = LocalDate.of(2026, 11, 2),
                            toDate = LocalDate.of(2026, 11, 2),
                            reason = reason
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_holiday_btn")
                ) {
                    Text("Add Gym Holiday & Trigger Recompute")
                }
            }
        }
    }
}

@Composable
private fun AdminAttendanceAnomaliesAndStreaksSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MANUAL ATTENDANCE CORRECTION (RECOMPUTES STREAK)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Button(
                    onClick = {
                        viewModel.repository.adminCorrectAttendance(
                            adminUserId = state.currentUserId,
                            adminRole = state.activeRole,
                            memberUserId = "usr-member-01",
                            locationId = state.locations.first().id,
                            attendanceDate = LocalDate.of(2026, 11, 3),
                            reason = reason
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("admin_correct_attendance_btn")
                ) {
                    Text("Insert Admin Attendance Correction (is_correction=true)")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EXPLICIT REWARD REVERSAL (-reward_days, Audit-Logged)", style = MaterialTheme.typography.titleMedium, color = StatusRejected)
                state.rewards.forEach { rwd ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${rwd.id} (+${rwd.valueDaysOrPoints}d) [${rwd.status.name}]", style = MaterialTheme.typography.bodyMedium)
                        if (rwd.status != RewardStatus.REVOKED) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.repository.reverseReward(
                                        adminUserId = state.currentUserId,
                                        adminRole = state.activeRole,
                                        rewardId = rwd.id,
                                        reason = reason
                                    )
                                }
                            ) {
                                Text("Reverse Reward")
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GPS ANOMALY REVIEW QUEUE (${state.anomalyFlags.size})", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                state.anomalyFlags.forEach { flag ->
                    Text("• [${flag.flagType}] User ${flag.userId}: ${flag.details}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AdminTransformationAndCouponsSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("HUMAN-ONLY TRANSFORMATION REVIEW (AI PROHIBITED)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
        state.transformations.forEach { sub ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submission ${sub.id} • User: ${sub.userId} • Status: ${sub.status.name}", fontWeight = FontWeight.Bold)
                    Text("Start (3 angles) + End (3 angles) Verified • Privacy Consent: ${sub.privacyConsentAccepted}", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.repository.reviewTransformation(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    submissionId = sub.id,
                                    decision = TransformationDecision.APPROVE,
                                    reason = reason
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("admin_approve_transformation_${sub.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved, contentColor = ObsidianBg)
                        ) {
                            Text("Approve (80% Cpn)")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.repository.reviewTransformation(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    submissionId = sub.id,
                                    decision = TransformationDecision.REJECT,
                                    reason = reason
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.repository.reviewTransformation(
                                    adminUserId = state.currentUserId,
                                    adminRole = state.activeRole,
                                    submissionId = sub.id,
                                    decision = TransformationDecision.REQUEST_RESUBMISSION,
                                    reason = reason
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resubmit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCampaignsAnalyticsAndAuditSection(
    viewModel: GymPlatformViewModel,
    state: PlatformState,
    reason: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RETENTION ANALYTICS & CAMPAIGN BUILDER (TRAI/DND OPT-IN)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                Text("Renewal Rate: 78.4% • Inactive (>14d): 12 • Avg Duration: 4.8 mos • Net Revenue: ₹4,82,900", style = MaterialTheme.typography.bodyMedium, color = TelemetryCyan)
                Button(
                    onClick = {
                        viewModel.repository.createCampaign(
                            adminUserId = state.currentUserId,
                            name = "Expiry 7-3-1 Day Renewal Campaign",
                            segmentDescription = "expiring_in_days IN (7,3,1) AND marketing_opt_in = true",
                            templateBody = "Renew before expiry to keep your streak unbroken!",
                            discountPercent = 15
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("launch_campaign_btn")
                ) {
                    Text("Launch Renewal & Inactive Recovery Campaign")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("APPEND-ONLY AUDIT LOGS (${state.auditLogs.size} Entries • DB Role Prohibits UPDATE/DELETE)", style = MaterialTheme.typography.titleMedium, color = KineticVolt)
                state.auditLogs.take(8).forEach { log ->
                    Text(
                        "• [${log.actorRole.name}] ${log.action} on ${log.entityType}(${log.entityId}) — Reason: ${log.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
