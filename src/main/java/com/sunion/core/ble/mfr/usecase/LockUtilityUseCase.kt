package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.BuildConfig
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.DoorType
import com.sunion.core.ble.mfr.entity.LockSetup
import com.sunion.core.ble.mfr.entity.LockVersion
import com.sunion.core.ble.mfr.entity.ResetMaskMap
import com.sunion.core.ble.mfr.entity.VersionType
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.hexToByteArray
import com.sunion.core.ble.mfr.toAsciiByteArray
import com.sunion.core.ble.mfr.toCString
import com.sunion.core.ble.mfr.toHexString
import com.sunion.core.ble.mfr.toLittleEndianByteArrayInt16
import com.sunion.core.ble.mfr.uIntStringToLittleEndianByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.rx2.asFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockUtilityUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockUtilityUseCase"

    suspend fun getMcuVersion(): String = getFirmwareVersion(VersionType.MCU.value)
    suspend fun getRfVersion(): String = getFirmwareVersion(VersionType.RF.value)

    private suspend fun getFirmwareVersion(type: Int): String {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "getFirmwareVersion"
        val function = 0x02
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = byteArrayOf(type.toByte())
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
                ) as LockVersion
                "mainVersion:${result.mainVersion} subVersion:${result.subVersion}"
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun getLockSetup(): LockSetup {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "getLockSetup"
        val function = 0x05
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
                val result = bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as LockSetup
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
                throw e
            }
            .single()
    }

    suspend fun setSessionKey(keyOne: ByteArray, ivOne: ByteArray, cardKeyOne: ByteArray, cardSalt: ByteArray): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "setSessionKey"
        val function = 0x06
        val data = keyOne + ivOne + cardKeyOne + cardSalt
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function)
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

    suspend fun setPublicUid(
        siteCode: String,
        uid: Int,
        startMinute: Int = 0,
        endMinute: Int = 1439,
        zone: Int = 0xFF
    ) = setUid(
        siteCode = siteCode,
        uid = uid,
        doorType = DoorType.Public.value,
        startMinute = startMinute,
        endMinute = endMinute,
        zone = zone
    )

    suspend fun setPrivateUid(
        siteCode: String,
        uid: Int,
        startMinute: Int = 0,
        endMinute: Int = 1439,
        zone: Int = 0xFF
    ) = setUid(
        siteCode = siteCode,
        uid = uid,
        doorType = DoorType.Private.value,
        startMinute = startMinute,
        endMinute = endMinute,
        zone = zone
    )

    private suspend fun setUid(siteCode: String, uid: Int, doorType: Int, startMinute: Int, endMinute: Int, zone: Int = 0xFF): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "setUid"
        val function = 0x07

        val safeStartMinute = startMinute.coerceIn(0, 1439)
        val safeEndMinute = endMinute.coerceIn(0, 1439)

        val data = siteCode.uIntStringToLittleEndianByteArray() +
                uid.toLittleEndianByteArrayInt16() +
                doorType.toByte() +
                zone.toByte() +
                safeStartMinute.toLittleEndianByteArrayInt16() +
                safeEndMinute.toLittleEndianByteArrayInt16()

        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function)
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

    suspend fun setRemotePinCodeRandomNumbers(remotePinCode: String): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "setRemotePinCodeRandomNumbers"
        val function = 0x08
        val data = remotePinCode.hexToByteArray()
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = data
        )
        return statefulConnection
            .setupSingleNotificationThenSendCommand(sendCmd, "$className.$functionName")
            .filter { notification ->
                bleCmdRepository.isValidNotification(statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification, function)
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

    suspend fun factoryReset(adminCode: String): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "factoryReset"
        val function = 0x0E
        val adminCodeByteArray = adminCode.toAsciiByteArray()
        val data = byteArrayOf(ResetMaskMap.ALL.value.toByte()) + adminCodeByteArray
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

    suspend fun factoryResetSetting(): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "factoryResetSetting"
        val function = 0x0E
        val data = byteArrayOf(ResetMaskMap.SETTING.value.toByte())
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

    suspend fun factoryResetBlackList(): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "factoryResetBlackList"
        val function = 0x0E
        val data = byteArrayOf(ResetMaskMap.BLACKLIST.value.toByte())
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

    suspend fun factoryResetPinCode(): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = "factoryResetPinCode"
        val function = 0x0E
        val data = byteArrayOf(ResetMaskMap.PINCODE.value.toByte())
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

    suspend fun getFirmwareVersion(): String {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val versionByteArray = runCatching {
            statefulConnection.rxBleConnection?.readCharacteristic(UUID.fromString(BuildConfig.FIRMWARE_REVISION))?.toObservable()?.asFlow()?.single()
        }.getOrNull() ?: throw IllegalStateException("null version")
        Timber.d("versionByteArray: ${versionByteArray.toHexString()}")
        return String(versionByteArray)
    }


    suspend fun getFirmwareModel(): String {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val modelByteArray = runCatching {
            statefulConnection.rxBleConnection?.readCharacteristic(UUID.fromString(BuildConfig.MODEL_NUMBER))?.toObservable()?.asFlow()?.single()
        }.getOrNull() ?: throw IllegalStateException("null model")
        Timber.d("modelByteArray: ${modelByteArray.toHexString()}, model: ${modelByteArray.toCString()}")
        return modelByteArray.toCString()
    }

    suspend fun restart(): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::restart.name
        val function = 0x0B
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
                throw e
            }
            .single()
    }

}