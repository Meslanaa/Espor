package org.mesos.care

/** A moment's view of the device, as far as Android lets an app see it. */
data class CareSnapshot(
    val batteryLevel: Int,
    val charging: Boolean,
    /** null when Android does not report it. */
    val batteryHealthy: Boolean?,
    val batteryTemperatureC: Float?,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val memoryAvailableBytes: Long,
    val memoryTotalBytes: Long,
)

enum class CareStatus { GOOD, FAIR, ATTENTION }

enum class CareTopic { BATTERY_HEALTH, TEMPERATURE, STORAGE, MEMORY, CHARGE }

/** One finding, worst first in [CareScore.checks]. */
data class CareCheck(val topic: CareTopic, val status: CareStatus)

/**
 * The Device Care score: 100 minus clear, explainable deductions. Pure Kotlin,
 * unit tested. It reports; it never pretends to "boost" anything.
 */
object CareScore {

    fun checks(s: CareSnapshot): List<CareCheck> {
        val storage = fraction(s.storageFreeBytes, s.storageTotalBytes)
        val memory = fraction(s.memoryAvailableBytes, s.memoryTotalBytes)
        val temperature = s.batteryTemperatureC
        return listOf(
            CareCheck(
                CareTopic.BATTERY_HEALTH,
                when (s.batteryHealthy) {
                    false -> CareStatus.ATTENTION
                    else -> CareStatus.GOOD
                },
            ),
            CareCheck(
                CareTopic.TEMPERATURE,
                when {
                    temperature == null -> CareStatus.GOOD
                    temperature >= 45f -> CareStatus.ATTENTION
                    temperature >= 40f -> CareStatus.FAIR
                    else -> CareStatus.GOOD
                },
            ),
            CareCheck(
                CareTopic.STORAGE,
                when {
                    storage == null -> CareStatus.GOOD
                    storage < 0.05f -> CareStatus.ATTENTION
                    storage < 0.15f -> CareStatus.FAIR
                    else -> CareStatus.GOOD
                },
            ),
            CareCheck(
                CareTopic.MEMORY,
                when {
                    memory == null -> CareStatus.GOOD
                    memory < 0.08f -> CareStatus.ATTENTION
                    memory < 0.18f -> CareStatus.FAIR
                    else -> CareStatus.GOOD
                },
            ),
            CareCheck(
                CareTopic.CHARGE,
                when {
                    s.charging -> CareStatus.GOOD
                    s.batteryLevel in 0..9 -> CareStatus.ATTENTION
                    s.batteryLevel in 10..19 -> CareStatus.FAIR
                    else -> CareStatus.GOOD
                },
            ),
        ).sortedByDescending { it.status.ordinal }
    }

    fun score(s: CareSnapshot): Int {
        val penalty = checks(s).sumOf { check ->
            val weight = when (check.topic) {
                CareTopic.BATTERY_HEALTH -> 25
                CareTopic.STORAGE -> 30
                CareTopic.TEMPERATURE -> 15
                CareTopic.MEMORY -> 15
                CareTopic.CHARGE -> 10
            }
            when (check.status) {
                CareStatus.GOOD -> 0
                CareStatus.FAIR -> weight / 3
                CareStatus.ATTENTION -> weight
            }
        }
        return (100 - penalty).coerceIn(0, 100)
    }

    fun status(score: Int): CareStatus = when {
        score >= 85 -> CareStatus.GOOD
        score >= 60 -> CareStatus.FAIR
        else -> CareStatus.ATTENTION
    }

    /** [part] / [whole], or null when [whole] is unknown. */
    fun fraction(part: Long, whole: Long): Float? =
        if (whole <= 0L) null else (part.toDouble() / whole).toFloat().coerceIn(0f, 1f)
}
