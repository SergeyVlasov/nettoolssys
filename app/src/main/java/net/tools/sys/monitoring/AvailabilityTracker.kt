package net.tools.sys.monitoring

import kotlinx.serialization.Serializable

@Serializable
enum class AvailabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
}

enum class StatusNotification {
    BecameUnavailable,
    Recovered,
}

@Serializable
data class AvailabilityRecord(
    val status: AvailabilityStatus? = null,
    val consecutiveFailures: Int = 0,
)

data class AvailabilityDecision(
    val record: AvailabilityRecord,
    val notification: StatusNotification? = null,
)

object AvailabilityTracker {
    /**
     * Unreachable results required before a server is recorded as unavailable.
     * One timeout or connection error leaves an available server unchanged.
     */
    const val FAILURES_BEFORE_UNAVAILABLE = 2

    fun apply(previous: AvailabilityRecord, reachable: Boolean): AvailabilityDecision {
        if (reachable) {
            val notification = when (previous.status) {
                AvailabilityStatus.UNAVAILABLE -> StatusNotification.Recovered
                AvailabilityStatus.AVAILABLE, null -> null
            }
            return AvailabilityDecision(
                record = AvailabilityRecord(
                    status = AvailabilityStatus.AVAILABLE,
                    consecutiveFailures = 0,
                ),
                notification = notification,
            )
        }

        val failures = (previous.consecutiveFailures + 1).coerceAtMost(FAILURES_BEFORE_UNAVAILABLE)
        if (failures < FAILURES_BEFORE_UNAVAILABLE) {
            return AvailabilityDecision(
                record = previous.copy(consecutiveFailures = failures),
                notification = null,
            )
        }

        val notification = when (previous.status) {
            AvailabilityStatus.AVAILABLE -> StatusNotification.BecameUnavailable
            AvailabilityStatus.UNAVAILABLE, null -> null
        }
        return AvailabilityDecision(
            record = AvailabilityRecord(
                status = AvailabilityStatus.UNAVAILABLE,
                consecutiveFailures = failures,
            ),
            notification = notification,
        )
    }
}
