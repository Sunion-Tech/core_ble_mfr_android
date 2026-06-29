package com.sunion.core.ble.mfr.exception

sealed class LockStatusException : Exception() {
    class LockFunctionNotSupportException : LockStatusException()
}
