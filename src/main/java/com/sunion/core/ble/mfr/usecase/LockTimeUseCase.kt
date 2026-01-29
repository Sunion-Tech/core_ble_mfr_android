package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.*
import com.sunion.core.ble.mfr.entity.TimeSetting
import com.sunion.core.ble.mfr.exception.NotConnectedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockTimeUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockTimeUseCase"

    suspend fun setTime(timeStamp: Long): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setTime.name
        val function = 0x11
        val bytes = timeStamp.toLittleEndianUInt32ByteArray()
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            bytes
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
                ) as Boolean
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setTimeAndTimeZone(timeStamp: Long, timezone: String): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setTime.name
        val function = 0x11
        val zonedDateTime = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of(timezone))
        val offsetSeconds = zonedDateTime.offset.totalSeconds
        val offsetByte = offsetSeconds.toLittleEndianByteArray()
        val bytes = timeStamp.toLittleEndianUInt32ByteArray() + offsetByte
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            bytes
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
                ) as Boolean
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun getTime(): Int {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getTime.name
        val function = 0x10
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
                val ret = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as TimeSetting
                ret.time
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun getTimeZone(): String {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getTimeZone.name
        val function = 0x10
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
                val ret = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as TimeSetting
                val offsetSeconds = ret.timeZone
                val zoneOffset = ZoneOffset.ofTotalSeconds(offsetSeconds)
                val zoneId = ZoneId.ofOffset("UTC", zoneOffset)
                zoneId.toString()
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }
}