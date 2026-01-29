package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.DeviceStatus
import com.sunion.core.ble.mfr.entity.LockConfig
import com.sunion.core.ble.mfr.entity.LockState
import com.sunion.core.ble.mfr.entity.LockStateAction
import com.sunion.core.ble.mfr.entity.SecurityBolt
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.isNotSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatus14UseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = DeviceStatus14UseCase::class.java.simpleName ?: "DeviceStatus14UseCase"

    suspend operator fun invoke(): DeviceStatus.Fourteen {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val function = 0x14
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo()
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, className)
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as DeviceStatus.Fourteen
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$className exception $e")
                throw e
            }
            .single()
    }

    suspend fun setLockState(state: Int): DeviceStatus.Fourteen {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setLockState.name
        val function = 0x15
        val resolveFunction = 0x14
        if (state == LockState.UNKNOWN.value) throw IllegalArgumentException("Unknown desired lock state.")
        val lockState = if (state == LockState.UNLOCKED.value) byteArrayOf(0x00) else byteArrayOf(0x01)
        val data = byteArrayOf(LockStateAction.LOCK_STATE.value.toByte()) + lockState
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, resolveFunction
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    resolveFunction,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),

                    notification
                )
                result as DeviceStatus.Fourteen
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setSecurityBolt(state: Int): DeviceStatus.Fourteen {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setSecurityBolt.name
        val function = 0x15
        val resolveFunction = 0x14
        if (state.isNotSupport()) throw IllegalArgumentException("$functionName not support.")
        val securityBoltState = if (state == SecurityBolt.NOT_PROTRUDE.value) byteArrayOf(0x00) else byteArrayOf(0x01)
        val data = byteArrayOf(LockStateAction.SECURITY_BOLT.value.toByte()) + securityBoltState

        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, resolveFunction
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    resolveFunction,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),

                    notification
                )
                result as DeviceStatus.Fourteen
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setAutoUnlockLockState(state: Int): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setAutoUnlockLockState.name
        val function = 0x16
        if (state == LockState.UNKNOWN.value) throw IllegalArgumentException("Unknown desired lock state.")
        val lockState = if (state == LockState.LOCKED.value) byteArrayOf(0x01) else return false
        val data = byteArrayOf(LockStateAction.LOCK_STATE.value.toByte()) + lockState
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                )
                result as Boolean
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

}