package com.sunion.core.ble.mfr.exception

class NotConnectedException : Exception()

sealed class ConnectionTokenException : Exception() {
    class DeviceRefusedException : ConnectionTokenException()
    class IllegalTokenException : ConnectionTokenException()
    class IllegalTokenStateException : ConnectionTokenException()
    class LockFromSharingHasBeenUsedException : ConnectionTokenException()
}