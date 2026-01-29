package com.sunion.core.ble.mfr.entity

import com.sunion.core.ble.mfr.accessByteArrayToString

data class OTAStatus(
    val target: Int,
    val state: Int,
    val isSuccess: Int,
)

data class Ability(
    val weekDayScheduleCount: Int,
    val yearDayScheduleCount: Int,
    val codeCredentialCount: Int,
    val fpCredentialCount: Int,
    val faceCredentialCount: Int,
    val logCount: Int,
    val blackListCount: Int
)

data class LockVersion(
    val target: Int,
    val mainVersion: Int,
    val subVersion: Int,
)

data class WeekDaySchedule(
    val status: Int = ScheduleStatus.AVAILABLE.value,
    val dayMask: Int = 0,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 0,
    val endMinute: Int = 0
)

data class YearDaySchedule(
    val status: Int = ScheduleStatus.AVAILABLE.value,
    val start: Long = 0L,
    val end: Long = 0L
)

data class TimeSetting(
    val time: Int,
    val timeZone: Int
)

data class LockSetup(
    val keyOne: Int,
    val token: Int,
    val uid: Int,
    val remotePinCode: Int
)

data class EventLog(
    val timestamp: Long,
    val millisecond: Int,
    val event: Int,
    val name: String
)

data class BlackListDetail(
    val isUsing: Int,
    val type: Int,
    val code: ByteArray,
    val codeString: String? = code.accessByteArrayToString()
)