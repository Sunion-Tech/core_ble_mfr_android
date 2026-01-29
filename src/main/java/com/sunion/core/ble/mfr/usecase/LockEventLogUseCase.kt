package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.EventLog
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.toLittleEndianByteArrayInt16
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

class LockEventLogUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockEventLogUseCase"

    suspend fun getEventQuantity(): Int {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getEventQuantity.name
        val function = 0x1C
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
                    notification, function
                )
            }
            .take(1)
            .map { notification ->
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Int
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun getEvent(index: Int): EventLog {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getEvent.name
        val function = 0x1D
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = index.toLittleEndianByteArrayInt16()
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
                ) as EventLog
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

}
