package net.tools.sys.monitoring

import net.tools.sys.model.PortProtocol
import net.tools.sys.monitoring.monitoringErrorMessage
import net.tools.sys.monitoring.monitoringResponseTimeMs
import net.tools.sys.network.CheckResult
import net.tools.sys.network.PortCheckResult
import net.tools.sys.network.ServerCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityTrackerTest {

    @Test
    fun initialSuccess_recordsAvailableWithoutNotification() {
        val decision = AvailabilityTracker.apply(AvailabilityRecord(), reachable = true)

        assertEquals(AvailabilityStatus.AVAILABLE, decision.record.status)
        assertEquals(0, decision.record.consecutiveFailures)
        assertNull(decision.notification)
    }

    @Test
    fun initialFailure_doesNotMarkUnavailableOrNotify() {
        val decision = AvailabilityTracker.apply(AvailabilityRecord(), reachable = false)

        assertNull(decision.record.status)
        assertEquals(1, decision.record.consecutiveFailures)
        assertNull(decision.notification)
    }

    @Test
    fun repeatedInitialFailures_recordUnavailableWithoutNotification() {
        val first = AvailabilityTracker.apply(AvailabilityRecord(), reachable = false)
        val second = AvailabilityTracker.apply(first.record, reachable = false)

        assertEquals(AvailabilityStatus.UNAVAILABLE, second.record.status)
        assertNull(second.notification)
    }

    @Test
    fun singleFailureWhileAvailable_doesNotNotify() {
        val available = AvailabilityRecord(status = AvailabilityStatus.AVAILABLE)
        val decision = AvailabilityTracker.apply(available, reachable = false)

        assertEquals(AvailabilityStatus.AVAILABLE, decision.record.status)
        assertEquals(1, decision.record.consecutiveFailures)
        assertNull(decision.notification)
    }

    @Test
    fun consecutiveFailures_markAvailableServerUnavailableAndNotifyOnce() {
        val available = AvailabilityRecord(status = AvailabilityStatus.AVAILABLE)
        val first = AvailabilityTracker.apply(available, reachable = false)
        val second = AvailabilityTracker.apply(first.record, reachable = false)
        val third = AvailabilityTracker.apply(second.record, reachable = false)

        assertEquals(StatusNotification.BecameUnavailable, second.notification)
        assertEquals(AvailabilityStatus.UNAVAILABLE, second.record.status)
        assertNull(third.notification)
        assertEquals(AvailabilityStatus.UNAVAILABLE, third.record.status)
    }

    @Test
    fun successAfterOneFailure_clearsFailureCountWithoutNotification() {
        val afterBlip = AvailabilityTracker.apply(
            AvailabilityRecord(status = AvailabilityStatus.AVAILABLE),
            reachable = false,
        )
        val recovered = AvailabilityTracker.apply(afterBlip.record, reachable = true)

        assertEquals(AvailabilityStatus.AVAILABLE, recovered.record.status)
        assertEquals(0, recovered.record.consecutiveFailures)
        assertNull(recovered.notification)
    }

    @Test
    fun recovery_notifiesOnce() {
        val down = AvailabilityRecord(status = AvailabilityStatus.UNAVAILABLE, consecutiveFailures = 2)
        val recovered = AvailabilityTracker.apply(down, reachable = true)
        val stillUp = AvailabilityTracker.apply(recovered.record, reachable = true)

        assertEquals(StatusNotification.Recovered, recovered.notification)
        assertEquals(AvailabilityStatus.AVAILABLE, recovered.record.status)
        assertNull(stillUp.notification)
    }

    @Test
    fun unchangedAvailable_doesNotNotify() {
        val decision = AvailabilityTracker.apply(
            AvailabilityRecord(status = AvailabilityStatus.AVAILABLE),
            reachable = true,
        )

        assertNull(decision.notification)
        assertEquals(AvailabilityStatus.AVAILABLE, decision.record.status)
    }
}

class ServerAvailabilityTest {

    @Test
    fun pingSuccess_isAvailable() {
        assertTrue(result(pingReachable = true, portReachable = false).isAvailable())
    }

    @Test
    fun openPort_isAvailableWhenPingFails() {
        assertTrue(result(pingReachable = false, portReachable = true).isAvailable())
    }

    @Test
    fun timeoutsAndConnectionErrors_areUnavailable() {
        val failed = result(pingReachable = false, portReachable = false)
        assertFalse(failed.isAvailable())
        assertNull(failed.monitoringResponseTimeMs())
        assertEquals("Ping failed; Port 80 TCP failed", failed.monitoringErrorMessage())
    }

    @Test
    fun successfulCheck_recordsResponseTimeAndNoError() {
        val checked = result(pingReachable = true, portReachable = false, pingDetail = null)
        assertEquals(10L, checked.monitoringResponseTimeMs())
        assertNull(checked.monitoringErrorMessage())
    }

    @Test
    fun dnsFailure_isIncludedInTheError() {
        val checked = result(
            pingReachable = false,
            portReachable = false,
            pingDetail = "Unable to resolve host",
        )
        assertEquals(
            "Ping failed: Unable to resolve host; Port 80 TCP failed",
            checked.monitoringErrorMessage(),
        )
    }

    private fun result(
        pingReachable: Boolean,
        portReachable: Boolean,
        pingDetail: String? = null,
    ) = ServerCheckResult(
        ping = CheckResult(
            reachable = pingReachable,
            responseTimeMs = if (pingReachable) 10 else null,
            detail = pingDetail,
        ),
        ports = listOf(
            PortCheckResult(
                port = 80,
                protocol = PortProtocol.TCP,
                result = CheckResult(
                    reachable = portReachable,
                    responseTimeMs = if (portReachable) 5 else null,
                    detail = null,
                ),
            ),
        ),
    )
}
