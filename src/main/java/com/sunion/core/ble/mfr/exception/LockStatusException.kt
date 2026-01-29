package com.sunion.core.ble.mfr.exception

sealed class LockStatusException : Throwable() {
    class LockFunctionNotSupportException : LockStatusException()
}
