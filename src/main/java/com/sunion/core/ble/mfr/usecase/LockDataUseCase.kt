package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.Ability
import com.sunion.core.ble.mfr.entity.Data
import com.sunion.core.ble.mfr.entity.HashFormat
import com.sunion.core.ble.mfr.exception.NotConnectedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import timber.log.Timber
import javax.inject.Inject

class LockDataUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockDataUseCase"

    suspend fun getCredentialHash(): Data.Thirty = getHash(HashFormat.CREDENTIAL.value)
    suspend fun getBleUserHash(): Data.Thirty = getHash(HashFormat.BLE_USER.value)
    suspend fun getBlacklistHash(): Data.Thirty = getHash(HashFormat.BLACKLIST.value)

    suspend fun getAbility(): Ability {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getAbility.name
        val function = 0x18
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
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Ability
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    private suspend fun getHash(index: Int): Data.Thirty {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getHash.name
        val function = 0x30
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = byteArrayOf(index.toByte())
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
                ) as Data.Thirty
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun setAllDataSynced(): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::setAllDataSynced.name
        val function = 0x34
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