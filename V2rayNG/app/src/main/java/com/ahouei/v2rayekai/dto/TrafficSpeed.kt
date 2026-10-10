package com.ahouei.v2rayekai.dto

import java.io.Serializable

/** Live tunnel throughput sent from the core daemon to the UI, in bytes per second. */
data class TrafficSpeed(
    val downBytesPerSec: Long,
    val upBytesPerSec: Long,
) : Serializable
