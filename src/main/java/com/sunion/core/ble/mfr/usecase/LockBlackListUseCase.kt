package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.BlackListDetail
import com.sunion.core.ble.mfr.entity.Credential
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.toBooleanList
import com.sunion.core.ble.mfr.toLittleEndianByteArrayInt16
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import timber.log.Timber
import javax.inject.Inject

class LockBlackListUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockBlackListUseCase"

    suspend fun getBlackListArray(): List<Boolean> {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getBlackListArray.name
        val function = 0x2B
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function)
            }
            .take(1)
            .map { notification ->
                bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Credential.TwentyB
            }
            .map { decoded ->
                val list = mutableListOf<Boolean>()
                decoded.data.forEach { it.toBooleanList(list) }
                list
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun getBlackList(index: Int, count: Int = 10): Credential.TwentyC {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        if (count !in 0..255) {
            throw IllegalArgumentException("Count must be between 0 and 255")
        }
        val functionName = ::getBlackList.name
        val function = 0x2C
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = index.toLittleEndianByteArrayInt16() + count.toByte()
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
                ) as Credential.TwentyC
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun addBlackList(index: Int, count: Int = 10, blackList: List<BlackListDetail>): Credential.TwentyD {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::addBlackList.name
        val function = 0x2D
        val data = bleCmdRepository.combineCredential2DCmd(
            Credential.TwentyDCmd(
                index = index,
                addCount = count,
                blackList = blackList
            )
        )
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
                ) as Credential.TwentyD
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun deleteBlackList(index: Int, count: Int): Credential.TwentyE {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::deleteBlackList.name
        val function = 0x2E
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = index.toLittleEndianByteArrayInt16() + count.toLittleEndianByteArrayInt16()
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
                ) as Credential.TwentyE
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }
}