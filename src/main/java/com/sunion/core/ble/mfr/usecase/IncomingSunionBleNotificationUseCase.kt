package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.BleCmdRepository.Companion.NOTIFICATION_CHARACTERISTIC
import com.sunion.core.ble.mfr.BleSessionRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.Alert
import com.sunion.core.ble.mfr.entity.Credential
import com.sunion.core.ble.mfr.entity.DeviceStatus
import com.sunion.core.ble.mfr.entity.SunionBleNotification
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.toHexPrint
import com.sunion.core.ble.mfr.toHexString
import com.sunion.core.ble.mfr.unSignedInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.rx2.asFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomingSunionBleNotificationUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val bleSessionRepository: BleSessionRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {

    private val targetFunctions = setOf(0x14, 0x17, 0x28)

    operator fun invoke(): Flow<SunionBleNotification> = flow {
        val connection = statefulConnection.rxBleConnection
            ?: run {
                Timber.e("BLE not connected: cannot execute SunionBleNotification")
                return@flow
            }

        connection
            .setupNotification(NOTIFICATION_CHARACTERISTIC)
            .flatMap { it }
            .asFlow()
            .collect { notification ->
                val keyTwo = try {
                    bleSessionRepository.requireKeyTwo()
                } catch (e: NotConnectedException) {
                    return@collect
                }
                val ivTwo = try {
                    bleSessionRepository.requireIvTwo()
                } catch (e: NotConnectedException) {
                    return@collect
                }
                if (keyTwo.isEmpty() || ivTwo.isEmpty()) return@collect

                val decrypted = bleCmdRepository.decrypt(keyTwo, ivTwo, notification) ?: return@collect

                val functionCode = decrypted.component3().unSignedInt()

                if (functionCode in targetFunctions) {
                    val result = when (functionCode) {
                        0x14 -> bleCmdRepository.resolve(
                            functionCode, keyTwo, ivTwo, notification
                        ) as? DeviceStatus.Fourteen

                        0x17 -> bleCmdRepository.resolve(
                            functionCode, keyTwo, ivTwo, notification
                        ) as? Alert.Seventeen

                        0x28 -> bleCmdRepository.resolve(
                            functionCode, keyTwo, ivTwo, notification
                        ) as? Credential.TwentyEight

                        else -> null
                    }

                    if (result != null) {
                        emit(result)
                    } else {
                        Timber.e("Failed to resolve target command: ${functionCode.toHexString()} data:${decrypted.toHexPrint()}")
                    }
                }
            }
    }.catch { e ->
        Timber.e("Error receiving SunionBleNotification: $e")
    }
}