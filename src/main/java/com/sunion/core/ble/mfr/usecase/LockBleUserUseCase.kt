package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.byteLength
import com.sunion.core.ble.mfr.entity.DeviceToken
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.hexStringToByteArray
import com.sunion.core.ble.mfr.toAsciiByteArray
import com.sunion.core.ble.mfr.toLittleEndianByteArray
import com.sunion.core.ble.mfr.toLittleEndianByteArrayInt16
import com.sunion.core.ble.mfr.toLittleEndianUInt32ByteArray
import com.sunion.core.ble.mfr.unSignedInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockBleUserUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockBleUserUseCase"

    suspend fun getBleUserArray(): List<Int> {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getBleUserArray.name
        val function = 0x20
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification,
                    function
                )
            }
            .take(1)
            .map { notification ->
                bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as ByteArray
            }
            .map { bytes ->
                val indexIterable = bytes
                    .mapIndexed { index, byte -> if (byte.unSignedInt() != 0x00) index else null }
                    .filterNotNull()
                indexIterable
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun getBleUser(index: Int): DeviceToken.BleUser {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getBleUser.name
        val function = 0x21
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            byteArrayOf(index.toByte())
        )

        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification,
                    function
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as DeviceToken.BleUser
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun addBleUser(index: Int, permission: String, token: String, startTime: Long, endTime: Long, identity:String): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::addBleUser.name
        val function = 0x22
        val data = byteArrayOf(index.toByte()) + permission.toByteArray() + token.hexStringToByteArray() + startTime.toLittleEndianUInt32ByteArray() + endTime.toLittleEndianUInt32ByteArray() + identity.toByteArray()
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
                    statefulConnection.keyTwo(),
                    statefulConnection.ivTwo(),
                    notification,
                    function
                )
            }
            .take(1)
            .map { notification ->
                val isSuccess = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Boolean
                isSuccess
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun deleteBleUser(index: Int): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::deleteBleUser.name
        val function = 0x23
        val data = byteArrayOf(index.toByte())

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
                    notification,
                    function
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Boolean
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }
}