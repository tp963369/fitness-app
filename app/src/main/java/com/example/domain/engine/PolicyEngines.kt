package com.example.domain.engine

import com.example.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.*

object CalendarMathEngine {

    /**
     * Deterministically adds calendar months with day clamping:
     * - Jan 31 + 1 month -> Feb 28 (or Feb 29 in leap year)
     * - Feb 29 (leap year) + 12 months -> Feb 28 (next non-leap year)
     * - Feb 28 (non-leap) + 12 months -> Feb 28
     */
    fun addMonthsClamped(startDate: LocalDate, monthsToAdd: Int): LocalDate {
        val targetYearMonth = YearMonth.from(startDate).plusMonths(monthsToAdd.toLong())
        val clampedDay = min(startDate.dayOfMonth, targetYearMonth.lengthOfMonth())
        return targetYearMonth.atDay(clampedDay)
    }

    fun calculateExpiryDate(
        startDate: LocalDate,
        durationType: DurationType,
        durationValue: Int
    ): LocalDate {
        return when (durationType) {
            DurationType.DAYS, DurationType.CUSTOM -> startDate.plusDays(durationValue.toLong())
            DurationType.WEEKS -> startDate.plusWeeks(durationValue.toLong())
            DurationType.CALENDAR_MONTHS -> addMonthsClamped(startDate, durationValue)
            DurationType.CALENDAR_YEARS -> addMonthsClamped(startDate, durationValue * 12)
        }
    }

    /**
     * Renewal before expiry starts the day after current expiry;
     * renewal after expiry starts on the approval date.
     */
    fun calculateRenewalStartDate(currentExpiryDate: LocalDate, approvalDate: LocalDate): LocalDate {
        return if (!approvalDate.isAfter(currentExpiryDate)) {
            currentExpiryDate.plusDays(1)
        } else {
            approvalDate
        }
    }
}

data class GeofenceEvaluation(
    val passed: Boolean,
    val distanceMeters: Double,
    val gpsAccuracyMeters: Double,
    val radiusMeters: Int,
    val maxAccuracyMeters: Int,
    val strictMode: Boolean,
    val rejectReason: String? = null,
    val consumesRateLimit: Boolean = true
)

object GeofenceEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) +
                cos(rLat1) * cos(rLat2) * sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * CRITICAL Dual-Gate Geofence Math (Section 6.6):
     * - Gate 1: accuracy <= max_accuracy
     * - Gate 2: distance <= radius
     * - Optional Strict Mode: distance + accuracy <= radius
     * - NEVER computes `distance <= radius + accuracy` (prohibited fence enlargement).
     * - OUTSIDE_GEOFENCE does NOT consume the 5/hour rate limit so members can retry after moving.
     */
    fun evaluateDualGate(
        distanceMeters: Double,
        gpsAccuracyMeters: Double,
        radiusMeters: Int,
        maxAccuracyMeters: Int,
        strictMode: Boolean = false
    ): GeofenceEvaluation {
        // Gate 1: GPS Accuracy
        if (gpsAccuracyMeters > maxAccuracyMeters.toDouble()) {
            return GeofenceEvaluation(
                passed = false,
                distanceMeters = distanceMeters,
                gpsAccuracyMeters = gpsAccuracyMeters,
                radiusMeters = radiusMeters,
                maxAccuracyMeters = maxAccuracyMeters,
                strictMode = strictMode,
                rejectReason = "POOR_GPS_ACCURACY",
                consumesRateLimit = true
            )
        }

        // Gate 2: Haversine Distance (strictly distance <= radius)
        if (distanceMeters > radiusMeters.toDouble()) {
            return GeofenceEvaluation(
                passed = false,
                distanceMeters = distanceMeters,
                gpsAccuracyMeters = gpsAccuracyMeters,
                radiusMeters = radiusMeters,
                maxAccuracyMeters = maxAccuracyMeters,
                strictMode = strictMode,
                rejectReason = "OUTSIDE_GEOFENCE",
                consumesRateLimit = false // Do NOT consume rate limit for OUTSIDE_GEOFENCE
            )
        }

        // Optional Strict Mode: worst-case error still inside fence
        if (strictMode && (distanceMeters + gpsAccuracyMeters) > radiusMeters.toDouble()) {
            return GeofenceEvaluation(
                passed = false,
                distanceMeters = distanceMeters,
                gpsAccuracyMeters = gpsAccuracyMeters,
                radiusMeters = radiusMeters,
                maxAccuracyMeters = maxAccuracyMeters,
                strictMode = strictMode,
                rejectReason = "OUTSIDE_GEOFENCE",
                consumesRateLimit = false
            )
        }

        return GeofenceEvaluation(
            passed = true,
            distanceMeters = distanceMeters,
            gpsAccuracyMeters = gpsAccuracyMeters,
            radiusMeters = radiusMeters,
            maxAccuracyMeters = maxAccuracyMeters,
            strictMode = strictMode,
            rejectReason = null,
            consumesRateLimit = true
        )
    }

    fun detectAnomalies(
        userId: String,
        attendanceId: String,
        scanLat: Double,
        scanLng: Double,
        scanAccuracyMeters: Double,
        scanEpochMs: Long,
        scanDate: LocalDate,
        previousUserScans: List<AttendanceRecord>,
        sameDayOtherMemberScans: List<AttendanceRecord>,
        isMockLocationSignal: Boolean = false
    ): List<AttendanceAnomalyFlag> {
        val flags = mutableListOf<AttendanceAnomalyFlag>()

        if (isMockLocationSignal) {
            flags.add(
                AttendanceAnomalyFlag(
                    id = "flag-mock-$attendanceId",
                    attendanceId = attendanceId,
                    userId = userId,
                    flagType = "MOCK_LOCATION_SIGNAL",
                    details = "Mock location telemetry detected on client scan",
                    createdAtEpochMs = scanEpochMs
                )
            )
        }

        // 1. Distance > 50 km from previous scan within 10 minutes (600,000 ms)
        previousUserScans.forEach { prev ->
            val timeDiffMs = abs(scanEpochMs - prev.scannedAtEpochMs)
            if (timeDiffMs <= 10 * 60 * 1000L) {
                val jumpDistanceMeters = haversineMeters(prev.gpsLat, prev.gpsLng, scanLat, scanLng)
                if (jumpDistanceMeters > 50_000.0) {
                    flags.add(
                        AttendanceAnomalyFlag(
                            id = "flag-vel-$attendanceId",
                            attendanceId = attendanceId,
                            userId = userId,
                            flagType = "IMPOSSIBLE_TRAVEL_50KM_10MIN",
                            details = "Distance ${(jumpDistanceMeters / 1000.0).toInt()} km in ${timeDiffMs / 1000}s",
                            createdAtEpochMs = scanEpochMs
                        )
                    )
                }
            }
        }

        // 2. Unusually high accuracy variance across scans (std dev > 35m)
        if (previousUserScans.size >= 2) {
            val accuracies = previousUserScans.map { it.gpsAccuracyMeters } + scanAccuracyMeters
            val mean = accuracies.average()
            val variance = accuracies.map { (it - mean).pow(2.0) }.average()
            val stdDev = sqrt(variance)
            if (stdDev > 35.0) {
                flags.add(
                    AttendanceAnomalyFlag(
                        id = "flag-var-$attendanceId",
                        attendanceId = attendanceId,
                        userId = userId,
                        flagType = "HIGH_ACCURACY_VARIANCE",
                        details = "GPS accuracy stdDev=${"%.1f".format(stdDev)}m across recent scans",
                        createdAtEpochMs = scanEpochMs
                    )
                )
            }
        }

        // 3. Identical lat/lng across different members on the same day
        val identicalOther = sameDayOtherMemberScans.firstOrNull { other ->
            other.userId != userId &&
                    other.attendanceDate == scanDate &&
                    abs(other.gpsLat - scanLat) < 1e-6 &&
                    abs(other.gpsLng - scanLng) < 1e-6
        }
        if (identicalOther != null) {
            flags.add(
                AttendanceAnomalyFlag(
                    id = "flag-ident-$attendanceId",
                    attendanceId = attendanceId,
                    userId = userId,
                    flagType = "IDENTICAL_COORDINATES_MULTI_MEMBER",
                    details = "Identical lat/lng ($scanLat, $scanLng) with member ${identicalOther.userId} on $scanDate",
                    createdAtEpochMs = scanEpochMs
                )
            )
        }

        return flags
    }
}

data class StreakRecomputeResult(
    val streakState: StreakStateRecord,
    val allRewardsForMembership: List<RewardRecord>,
    val newlyIssuedRewards: List<RewardRecord>,
    val eligibleDays: List<LocalDate>,
    val attendedEligibleDays: List<LocalDate>
)

object StreakEngine {

    /**
     * Excludes:
     * 1. Closed days derived from `location_schedules`
     * 2. Gym-wide `holidays`
     * 3. Approved `membership_freezes` intervals (streak protected during freeze)
     */
    fun computeEligibleDays(
        fromDate: LocalDate,
        toDate: LocalDate,
        schedules: List<LocationSchedule>,
        holidays: List<GymHoliday>,
        freezes: List<MembershipFreezeRecord>
    ): List<LocalDate> {
        if (toDate.isBefore(fromDate)) return emptyList()
        val openIsoDays = if (schedules.isEmpty()) {
            (1..6).toSet() // Default Mon-Sat open, Sunday closed
        } else {
            schedules.filter { it.isOpen }.map { it.dayOfWeekIso }.toSet()
        }

        val approvedFreezes = freezes.filter {
            it.status == FreezeStatus.APPROVED || it.status == FreezeStatus.RESUMED
        }

        val result = mutableListOf<LocalDate>()
        var cursor = fromDate
        while (!cursor.isAfter(toDate)) {
            val isScheduleOpen = openIsoDays.contains(cursor.dayOfWeek.value)
            val isHoliday = holidays.any { !cursor.isBefore(it.fromDate) && !cursor.isAfter(it.toDate) }
            val isFrozen = approvedFreezes.any { !cursor.isBefore(it.fromDate) && !cursor.isAfter(it.toDate) }

            if (isScheduleOpen && !isHoliday && !isFrozen) {
                result.add(cursor)
            }
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Deterministic Streak & Idempotent Reward Recompute:
     * - Unique constraint on `(user_id, membership_id, source, milestone_index)`.
     * - Recomputed milestones that were already issued return the existing row and are NEVER re-issued or clawed back.
     */
    fun recomputeStreakAndRewards(
        userId: String,
        membershipId: String,
        evaluationStartDate: LocalDate,
        evaluationEndDate: LocalDate,
        approvedAttendances: List<AttendanceRecord>,
        schedules: List<LocationSchedule>,
        holidays: List<GymHoliday>,
        freezes: List<MembershipFreezeRecord>,
        milestoneKind: MilestoneKind = MilestoneKind.CONSECUTIVE,
        milestoneTargetDays: Int = 6,
        rewardDaysPerMilestone: Int = 2,
        repeatable: Boolean = true,
        existingRewards: List<RewardRecord>
    ): StreakRecomputeResult {
        val eligibleDays = computeEligibleDays(
            fromDate = evaluationStartDate,
            toDate = evaluationEndDate,
            schedules = schedules,
            holidays = holidays,
            freezes = freezes
        )

        val attendedDatesSet = approvedAttendances
            .filter { it.userId == userId && it.status == AttendanceStatus.APPROVED }
            .map { it.attendanceDate }
            .toSet()

        var currentStreak = 0
        var longestStreak = 0
        var lastQualifyingDate: LocalDate? = null

        val existingByMilestone = existingRewards
            .filter {
                it.userId == userId &&
                        it.membershipId == membershipId &&
                        it.source == RewardSource.STREAK_MILESTONE
            }
            .associateBy { it.milestoneIndex }
            .toMutableMap()

        val newlyIssued = mutableListOf<RewardRecord>()
        var milestonesEarnedCount = 0

        for (day in eligibleDays) {
            if (attendedDatesSet.contains(day)) {
                currentStreak += 1
                lastQualifyingDate = day
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak
                }

                if (milestoneKind == MilestoneKind.CONSECUTIVE && milestoneTargetDays > 0) {
                    if (currentStreak % milestoneTargetDays == 0) {
                        val milestoneIdx = currentStreak / milestoneTargetDays
                        if (repeatable || milestoneIdx == 1) {
                            val existing = existingByMilestone[milestoneIdx]
                            if (existing == null) {
                                val newReward = RewardRecord(
                                    id = "rwd-$userId-$membershipId-$milestoneIdx",
                                    userId = userId,
                                    type = RewardType.MEMBERSHIP_DAYS,
                                    valueDaysOrPoints = rewardDaysPerMilestone,
                                    source = RewardSource.STREAK_MILESTONE,
                                    membershipId = membershipId,
                                    milestoneIndex = milestoneIdx,
                                    status = RewardStatus.APPLIED
                                )
                                existingByMilestone[milestoneIdx] = newReward
                                newlyIssued.add(newReward)
                            }
                        }
                    }
                }
            } else {
                // Missed an eligible open day
                currentStreak = 0
            }
        }

        if (milestoneKind == MilestoneKind.FULL_ELIGIBLE && eligibleDays.isNotEmpty()) {
            val allAttended = eligibleDays.all { attendedDatesSet.contains(it) }
            if (allAttended && eligibleDays.size >= milestoneTargetDays) {
                val milestoneIdx = 1
                if (existingByMilestone[milestoneIdx] == null) {
                    val newReward = RewardRecord(
                        id = "rwd-$userId-$membershipId-full-$milestoneIdx",
                        userId = userId,
                        type = RewardType.MEMBERSHIP_DAYS,
                        valueDaysOrPoints = rewardDaysPerMilestone,
                        source = RewardSource.STREAK_MILESTONE,
                        membershipId = membershipId,
                        milestoneIndex = milestoneIdx,
                        status = RewardStatus.APPLIED
                    )
                    existingByMilestone[milestoneIdx] = newReward
                    newlyIssued.add(newReward)
                }
            }
        }

        val allRewards = existingByMilestone.values.sortedBy { it.milestoneIndex ?: 0 }
        return StreakRecomputeResult(
            streakState = StreakStateRecord(
                userId = userId,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastQualifyingDate = lastQualifyingDate
            ),
            allRewardsForMembership = allRewards,
            newlyIssuedRewards = newlyIssued,
            eligibleDays = eligibleDays,
            attendedEligibleDays = eligibleDays.filter { attendedDatesSet.contains(it) }
        )
    }
}

object LeaderboardEngine {
    /**
     * Points formula: attendance = 10 pts/day, consecutive streak bonus +2/day.
     * Tie-breakers (Section 6.8):
     * (1) higher max streak
     * (2) earliest to reach score (`reachedScoreAtEpochMs` ascending)
     * (3) `userId` ascending (deterministic)
     */
    fun rankEntries(
        rawEntries: List<LeaderboardEntryRecord>,
        optedOutUserIds: Set<String> = emptySet()
    ): List<LeaderboardEntryRecord> {
        return rawEntries
            .filter { !optedOutUserIds.contains(it.userId) }
            .sortedWith(
                compareByDescending<LeaderboardEntryRecord> { it.totalScore }
                    .thenByDescending { it.maxStreak }
                    .thenBy { it.reachedScoreAtEpochMs }
                    .thenBy { it.userId }
            )
            .mapIndexed { index, entry ->
                entry.copy(rank = index + 1)
            }
    }
}

data class AiSummaryResponse(
    val statusCode: Int,
    val summaryText: String,
    val modelName: String = "gemini-3.8-flash",
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val payloadKeysUsed: Set<String> = emptySet()
)

object AiSummaryEngine {
    private val PROHIBITED_PHOTO_KEYS = setOf(
        "photo", "photos", "photo_url", "photoUrl", "storage_key", "storageKey",
        "progress_photos", "progressPhotos", "front", "side", "back", "signedUrl", "image"
    )

    /**
     * Enforces Section 6.7 & 12:
     * NEVER includes progress photos or PII beyond first name.
     */
    fun buildSafeAiPayload(
        user: UserAccount,
        attendanceCount: Int,
        streak: StreakStateRecord,
        milestonesCount: Int,
        transformationStatus: TransformationStatus
    ): Map<String, Any> {
        val firstName = user.fullName.trim().substringBefore(" ")
        val payload = linkedMapOf<String, Any>(
            "firstName" to firstName,
            "weightsKg" to user.weightHistoryKg,
            "attendanceCount" to attendanceCount,
            "currentStreak" to streak.currentStreak,
            "longestStreak" to streak.longestStreak,
            "milestonesReached" to milestonesCount,
            "transformationStatus" to transformationStatus.name
        )
        // Hard assertion that no photo keys exist
        check(payload.keys.intersect(PROHIBITED_PHOTO_KEYS).isEmpty()) {
            "AI payload security violation: photo fields are strictly prohibited."
        }
        return payload
    }

    fun generateSummary(
        user: UserAccount,
        attendanceCount: Int,
        streak: StreakStateRecord,
        milestonesCount: Int,
        transformationStatus: TransformationStatus,
        lastGeneratedAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
        simulateProviderFailure: Boolean = false
    ): AiSummaryResponse {
        if (!user.aiSummaryConsent) {
            return AiSummaryResponse(
                statusCode = 403,
                summaryText = "AI summary consent is turned off in preferences."
            )
        }

        // Rate limit: 1 summary per member per week (7 days)
        if (lastGeneratedAtEpochMs != null && (nowEpochMs - lastGeneratedAtEpochMs) < 7L * 24 * 3600 * 1000L) {
            return AiSummaryResponse(
                statusCode = 429,
                summaryText = "Weekly AI summary limit reached (1 per 7 days)."
            )
        }

        val payload = buildSafeAiPayload(
            user = user,
            attendanceCount = attendanceCount,
            streak = streak,
            milestonesCount = milestonesCount,
            transformationStatus = transformationStatus
        )

        if (attendanceCount < 2 && user.weightHistoryKg.size < 2) {
            return AiSummaryResponse(
                statusCode = 200,
                summaryText = "Not enough data to generate a meaningful summary.",
                payloadKeysUsed = payload.keys
            )
        }

        if (simulateProviderFailure) {
            return AiSummaryResponse(
                statusCode = 503,
                summaryText = "Please try again later.",
                payloadKeysUsed = payload.keys
            )
        }

        val firstName = payload["firstName"] as String
        val startW = user.weightHistoryKg.firstOrNull() ?: user.weightKg
        val latestW = user.weightHistoryKg.lastOrNull() ?: user.weightKg
        val deltaW = latestW - startW
        val deltaFormatted = if (deltaW <= 0) "${"%.1f".format(deltaW)} kg" else "+${"%.1f".format(deltaW)} kg"

        val factualSummary =
            "Activity overview for $firstName: $attendanceCount recorded gym check-ins, " +
                    "current attendance streak of ${streak.currentStreak} eligible days " +
                    "(longest streak: ${streak.longestStreak} days), and $milestonesCount streak milestone(s) recorded. " +
                    "Recorded weight measurements moved from $startW kg to $latestW kg ($deltaFormatted). " +
                    "Transformation submission status: ${transformationStatus.name}. " +
                    "(Factual activity summary only; non-medical and non-diagnostic.)"

        return AiSummaryResponse(
            statusCode = 200,
            summaryText = factualSummary,
            modelName = "gemini-3.8-flash",
            generatedAtEpochMs = nowEpochMs,
            payloadKeysUsed = payload.keys
        )
    }
}
