package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.BleSessionRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.LockConfig
import com.sunion.core.ble.mfr.entity.SoundType
import com.sunion.core.ble.mfr.entity.SoundValue
import com.sunion.core.ble.mfr.exception.LockStatusException
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.isSupport
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
class LockConfig12UseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection,
    private val bleSessionRepository: BleSessionRepository
) {
    private val className = this::class.simpleName ?: "LockConfig12UseCase"

    suspend fun get(): LockConfig.Twelve {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::get.name
        val function = 0x12
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo()
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
                ) as LockConfig.Twelve
                bleSessionRepository.updateConfig(result)
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun updateConfig(lockConfig12: LockConfig.Twelve): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::updateConfig.name
        val function = 0x13
        val bytes = bleCmdRepository.combineLockConfig13Cmd(lockConfig12)
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
                ) as LockConfig.Thirteen
                get()
                result.isSuccess
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setGuidingCode(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().guidingCode.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(guidingCode = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setTwoFA(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().twoFA.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(twoFA = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setVacationMode(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().vacationMode.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(vacationMode = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setAutoLock(isOn: Boolean, autoLockTime: Int): Boolean {
        if (autoLockTime < 1) throw IllegalArgumentException("Auto lock time should greater than 1.")
        if (autoLockTime < getCurrentLockConfig12().autoLockTimeLowerLimit || autoLockTime > getCurrentLockConfig12().autoLockTimeUpperLimit) {
            throw IllegalArgumentException("Set auto lock will fail because autoLockTime is not support value")
        }
        if (getCurrentLockConfig12().autoLock.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(autoLock = if (isOn) 1 else 0, autoLockTime = autoLockTime))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setOperatingSound(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().operatingSound.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(operatingSound = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setSoundValue(isOn: Boolean, soundValue :Int): Boolean {
        if (getCurrentLockConfig12().soundType.isSupport()) {
            val value = when (getCurrentLockConfig12().soundType) {
                SoundType.ON_OFF.value -> { if (isOn) SoundValue.OPEN.value else SoundValue.CLOSE.value }
                SoundType.LEVEL.value -> {
                    if(isOn){
                        when (soundValue) {
                            SoundValue.HIGH_VOICE.value -> {
                                SoundValue.HIGH_VOICE.value
                            }
                            SoundValue.LOW_VOICE.value -> {
                                SoundValue.LOW_VOICE.value
                            }
                            else -> {
                                throw IllegalArgumentException("Not support sound value.")
                            }
                        }
                    } else {
                        SoundValue.CLOSE.value
                    }
                }
                SoundType.PERCENTAGE.value -> { if (isOn) soundValue else SoundValue.CLOSE.value }
                else -> { if (isOn) SoundValue.OPEN.value else SoundValue.CLOSE.value }
            }
            return updateConfig(getCurrentLockConfig12().copy(soundValue = value))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setShowFastTrackMode(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().showFastTrackMode.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(showFastTrackMode = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setSabbathMode(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().sabbathMode.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(sabbathMode = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setPhoneticLanguage(language: Int): Boolean {
        if (getCurrentLockConfig12().phoneticLanguage.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(phoneticLanguage = language))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setPassword(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().password.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(password = if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setRemotePinCode(isOn: Boolean): Boolean {
        if (getCurrentLockConfig12().remotePinCode.isSupport()) {
            return updateConfig(getCurrentLockConfig12().copy(remotePinCode =  if (isOn) 1 else 0))
        } else {
            throw LockStatusException.LockFunctionNotSupportException()
        }
    }

    suspend fun setBleTx(bleTxValue: Int): Boolean {
        return updateConfig(getCurrentLockConfig12().copy(bleTx = bleTxValue))
    }

    suspend fun setBleAdv(bleAdvValue: Int): Boolean {
        return updateConfig(getCurrentLockConfig12().copy(bleAdv = bleAdvValue))
    }

    //update 12LockConfig after get()
    suspend fun getCurrentLockConfig12(): LockConfig.Twelve {
        return bleSessionRepository.currentConfig ?: get()
    }

}