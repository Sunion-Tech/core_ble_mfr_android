package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.DeviceStatus
import com.sunion.core.ble.mfr.exception.LockStatusException
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.unSignedInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockDirectionUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockDirectionUseCase"

    suspend operator fun invoke(): DeviceStatus {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val function = 0x0C
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo()
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, className)
            .filter { notification ->
                bleCmdRepository.decrypt(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                )?.let { decrypted ->
                    when(decrypted.component3().unSignedInt()){
                        0x14 -> true
                        else -> false
                    }
                } ?: false
            }
            .take(1)
            .map { notification ->
                var result: DeviceStatus = DeviceStatus.UNKNOWN
                bleCmdRepository.decrypt(
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                )?.let { decrypted ->
                    when (val resolveFunction = decrypted.component3().unSignedInt()) {
                        0x14 -> {
                            result = bleCmdRepository.resolve(
                                resolveFunction,
                                statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                                notification
                            ) as DeviceStatus.Fourteen
                        }
                        else -> {}
                    }
                }
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$className exception $e")
                throw e
            }
            .single()
    }
}