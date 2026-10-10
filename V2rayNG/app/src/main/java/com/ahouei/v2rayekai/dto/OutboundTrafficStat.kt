package com.ahouei.v2rayekai.dto

data class OutboundTrafficStat(
    val tag: String,
    val direction: String,
    val value: Long,
)