package com.sunion.core.ble.mfr.entity

data class LockConnectionInfo(
    var macAddress: String = "",
    var keyOne: String = "",
    var ivOne: String = "",
    var token: String = "",
    var keyTwo: String? = null,
    var ivTwo: String? = null,
    var permission: String? = null,
    var model: String? = "",
)

sealed class DeviceToken {
    companion object Permissions {
        const val ILLEGAL_TOKEN = 0
        const val VALID_TOKEN = 1
        const val REFUSED_TOKEN = 2
        const val PERMISSION_OWNER = "O"
        const val PERMISSION_MANAGER = "M"
        const val PERMISSION_USER = "U"
        const val PERMISSION_PLUG = "P"
        const val PERMISSION_NONE = "N"
    }

    data class BleUser(
        val isValid: Boolean = false,
        val permission: String = PERMISSION_NONE,
        val token: String,
        val startTime: Long? = null,
        val endTime: Long? = null,
        val identity: String? = null,
    ) : DeviceToken()
}