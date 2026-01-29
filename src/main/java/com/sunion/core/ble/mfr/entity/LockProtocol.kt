package com.sunion.core.ble.mfr.entity

import com.sunion.core.ble.mfr.accessByteArrayToString

sealed class SunionBleNotification {
    object UNKNOWN : SunionBleNotification()
}

sealed class DeviceStatus: SunionBleNotification() {
    object UNKNOWN : DeviceStatus()
    data class Fourteen(
        val mainVersion: Int,
        val subVersion: Int,
        val direction: Int,
        val vacationMode: Int,
        val deadBolt: Int,
        val doorState: Int,
        val lockState: Int,
        val securityBolt: Int,
        val battery: Int,
        val batteryState: Int,
    ) : DeviceStatus()
}

sealed class Credential: SunionBleNotification() {
    object UNKNOWN : Credential()

    data class TwentyFive(
        val transferComplete: Int,
        val data: ByteArray
    ) : Credential()

    data class TwentySix(
        val index: Int,
        val status: Int,
        val type: Int,
        val userType: Int,
        val code: ByteArray,
        val weekDayScheduleListCount: Int,
        val yearDayScheduleListCount: Int,
        val weekDayScheduleList: MutableList<WeekDaySchedule>,
        val yearDayScheduleList: MutableList<YearDaySchedule>,
        val codeString: String? = code.accessByteArrayToString()
    ) : Credential()

    data class TwentySevenCmd(
        val index: Int = 0xFFFF,
        val status: Int,
        val type: Int,
        val userType: Int,
        val code: ByteArray,
        val weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() },
        val yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule()),
    ) : Credential()

    data class TwentySeven(
        val index: Int,
        val isSuccess: Boolean,
    ) : Credential()

    data class TwentyEight(
        val type: Int,
        val state: Int,
        val index: Int,
        val status: Int,
        val data: ByteArray,
    ) : Credential()

    data class TwentyNine(
        val index: Int,
        val isSuccess: Boolean,
    ) : Credential()

    data class TwentyB(
        val transferComplete: Int,
        val data: ByteArray
    ) : Credential()

    data class TwentyC(
        val index: Int,
        val blackList: List<BlackListDetail>,
    ) : Credential()

    data class TwentyDCmd(
        val index: Int,
        val addCount: Int,
        val blackList: List<BlackListDetail>
    ) : Credential()

    data class TwentyD(
        val index: Int,
        val addCount: Int,
        val isSuccess: Boolean
    ) : Credential()

    data class TwentyE(
        val index: Int,
        val deleteCount: Int,
        val isSuccess: Boolean
    ) : Credential()

}

sealed class Alert : SunionBleNotification() {
    object UNKNOWN : Alert()

    data class Seventeen(
        val alertType: Int,
    ) : Alert()
}

sealed class LockConfig {
    object UNKNOWN : LockConfig()

    data class Twelve(
        val size: Int,
        val mainVersion: Int,
        val subVersion: Int,
        val formatVersion: Int,
        val direction: Int,
        val currentVersion: Int,
        val password: Int,
        val remotePinCode: Int,
        val guidingCode: Int,
        val twoFA: Int,
        val vacationMode: Int,
        val autoLock: Int,
        val autoLockTime: Int,
        val autoLockTimeLowerLimit: Int,
        val autoLockTimeUpperLimit: Int,
        val operatingSound: Int,
        val soundType: Int,
        val soundValue: Int,
        val showFastTrackMode: Int,
        val sabbathMode: Int,
        val phoneticLanguage: Int,
        val supportPhoneticLanguage: Int,
        val bleTx: Int,
        val bleAdv: Int
    ): LockConfig()

    data class Thirteen(
        val isSuccess: Boolean,
        val version: Int? = null,
    ): LockConfig()
}

sealed class Data {
    data class Thirty(
        val target: Int,
        val sha256: String,
    ) : Data()
}

