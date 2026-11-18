package net.tools.sys.monitoring

object MonitoringIntervals {
    val choices: List<Int> = listOf(15, 30, 60)
    const val DEFAULT_MINUTES = 30

    fun normalize(minutes: Int): Int = if (minutes in choices) minutes else DEFAULT_MINUTES
}
