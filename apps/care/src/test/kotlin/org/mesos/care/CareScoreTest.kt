package org.mesos.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareScoreTest {

    private val gb = 1024L * 1024 * 1024
    private val healthy = CareSnapshot(
        batteryLevel = 80,
        charging = false,
        batteryHealthy = true,
        batteryTemperatureC = 30f,
        storageFreeBytes = 40 * gb,
        storageTotalBytes = 128 * gb,
        memoryAvailableBytes = 3 * gb,
        memoryTotalBytes = 8 * gb,
    )

    @Test
    fun healthyDeviceScoresFull() {
        assertEquals(100, CareScore.score(healthy))
        assertEquals(CareStatus.GOOD, CareScore.status(100))
        CareScore.checks(healthy).forEach { assertEquals(CareStatus.GOOD, it.status) }
    }

    @Test
    fun fullStorageIsTheWorstFinding() {
        val full = healthy.copy(storageFreeBytes = 2 * gb)
        assertEquals(70, CareScore.score(full))
        assertEquals(CareTopic.STORAGE, CareScore.checks(full).first().topic)
        assertEquals(CareStatus.ATTENTION, CareScore.checks(full).first().status)
    }

    @Test
    fun deductionsAddUpAndClamp() {
        val bad = healthy.copy(
            batteryLevel = 5,
            batteryHealthy = false,
            batteryTemperatureC = 47f,
            storageFreeBytes = 1 * gb,
            memoryAvailableBytes = 0,
        )
        assertEquals(5, CareScore.score(bad))
        assertEquals(CareStatus.ATTENTION, CareScore.status(CareScore.score(bad)))
        val fair = healthy.copy(batteryTemperatureC = 41f, storageFreeBytes = 12 * gb)
        assertEquals(100 - 5 - 10, CareScore.score(fair))
        assertEquals(CareStatus.GOOD, CareScore.status(85))
        assertEquals(CareStatus.FAIR, CareScore.status(84))
    }

    @Test
    fun chargingIsNeverAProblem() {
        assertEquals(100, CareScore.score(healthy.copy(batteryLevel = 3, charging = true)))
    }

    @Test
    fun unknownValuesDoNotCount() {
        val unknown = healthy.copy(batteryHealthy = null, batteryTemperatureC = null, storageTotalBytes = 0, memoryTotalBytes = 0)
        assertEquals(100, CareScore.score(unknown))
        assertNull(CareScore.fraction(1, 0))
    }
}
