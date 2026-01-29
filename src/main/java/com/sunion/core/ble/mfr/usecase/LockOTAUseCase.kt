package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.OTAState
import com.sunion.core.ble.mfr.entity.OTAStatus
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.hexToByteArray
import com.sunion.core.ble.mfr.toLittleEndianByteArray
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
class LockOTAUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockOTAUseCase"

    suspend fun setOTAStart(target:Int, fileSize:Int): OTAStatus {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setOTAStart.name
        val function = 0x03
        val startState = OTAState.START.value
        val bytes = byteArrayOf(target.toByte()) + byteArrayOf(startState.toByte()) + fileSize.toLittleEndianByteArray()
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = bytes
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
                ) as OTAStatus
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setOTAFinish(target:Int, fileSize:Int, iv:String, signature:String): OTAStatus {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setOTAFinish.name
        val function = 0x03
        val finishState = OTAState.FINISH.value
        val bytes = byteArrayOf(target.toByte()) + byteArrayOf(finishState.toByte()) + fileSize.toLittleEndianByteArray() +iv.hexToByteArray() + signature.hexToByteArray()
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = bytes
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
                ) as OTAStatus
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setOTACancel(target:Int): OTAStatus {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setOTACancel.name
        val function = 0x03
        val cancelState = OTAState.CANCEL.value
        val bytes = byteArrayOf(target.toByte()) + byteArrayOf(cancelState.toByte())
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = bytes
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
                ) as OTAStatus
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun transferOTAData(offset:Int, data: ByteArray): String {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::transferOTAData.name
        val function = 0x04
        val offsetByte  = offset.toLittleEndianByteArray()
        val bytes = offsetByte + data
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = bytes
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
                ) as String
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }
}