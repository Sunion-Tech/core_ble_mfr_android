package com.sunion.core.ble.mfr

import android.annotation.SuppressLint
import com.sunion.core.ble.mfr.entity.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.copyOfRange
import kotlin.random.Random

@Singleton
class BleCmdRepository @Inject constructor(){

    private val commandSerial = AtomicInteger()

    companion object {
        const val CIPHER_MODE = "AES/CBC/NoPadding"
        val NOTIFICATION_CHARACTERISTIC: UUID = UUID.fromString(BuildConfig.NOTIFICATION_CHARACTERISTIC)
    }

    enum class Config12(val byte: Int){
        MAIN_VERSION (0),
        SUB_VERSION (1),
        FORMAT_VERSION (2),
        DIRECTION (3),
        CURRENT_VERSION (4),
        PASSWORD (8),
        REMOTE_PINCODE (9),
        GUIDING_CODE (10),
        TWO_FA (11),
        VACATION_MODE (12),
        AUTOLOCK (13),
        AUTOLOCK_DELAY ( 14),
        AUTOLOCK_DELAY_LOWER_LIMIT (16),
        AUTOLOCK_DELAY_UPPER_LIMIT (18),
        OPERATING_SOUND (20),
        SOUND_TYPE (21),
        SOUND_VALUE (22),
        SHOW_FAST_TRACK_MODE (23),
        SABBATH_MODE (24),
        PHONETIC_LANGUAGE (25),
        SUPPORT_PHONETIC_LANGUAGE (26),
        BLE_TX (27),
        BLE_ADV (28),
    }

    enum class Config13(val byte: Int){
        SIZE(17),
        DIRECTION (0),
        PASSWORD (1),
        REMOTE_PINCODE (2),
        GUIDING_CODE (3),
        TWO_FA (4),
        VACATION_MODE (5),
        AUTOLOCK (6),
        AUTOLOCK_DELAY (7),
        OPERATING_SOUND (9),
        SOUND_TYPE (10),
        SOUND_VALUE (11),
        SHOW_FAST_TRACK_MODE (12),
        SABBATH_MODE (13),
        PHONETIC_LANGUAGE (14),
        BLE_TX (15),
        BLE_ADV (16),
    }

    enum class Config14(val byte: Int){
        SIZE(10),
        MAIN_VERSION (0),
        SUB_VERSION (1),
        LOCK_DIRECTION (2),
        VACATION_MODE (3),
        DEAD_BOLT (4),
        DOOR_STATE ( 5),
        LOCK_STATE (6),
        SECURITY_BOLT (7),
        BATTERY (8),
        LOW_BATTERY (9)
    }

    @SuppressLint("GetInstance")
    fun encrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray? {
        Timber.d("encrypt key: ${key.toHexPrint()} \niv: ${iv.toHexPrint()} \ndata: ${data.toHexPrint()}")
        return try {
            val cipher: Cipher = Cipher.getInstance(CIPHER_MODE)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted: ByteArray = cipher.doFinal(data)
            encrypted
        } catch (exception: Exception) {
            Timber.e(exception)
            null
        }
    }

    @SuppressLint("GetInstance")
    fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray? {
        return try {
            val cipher: Cipher = Cipher.getInstance(CIPHER_MODE)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val original: ByteArray = cipher.doFinal(data)
            original
        } catch (exception: Exception) {
            Timber.e(exception)
            null
        }
    }

    fun pad(data: ByteArray, padZero: Boolean = false): ByteArray {
        if (data.isEmpty()) throw IllegalArgumentException("Invalid command.")

        val remainder = data.size % 16
        if (remainder == 0) return data

        val padNumber = 16 - remainder
        val padBytes = if (padZero) ByteArray(padNumber) else Random.nextBytes(padNumber)

        return data + padBytes
    }

    private fun serialIncrementAndGet(): ByteArray {
        return commandSerial.incrementAndGet().toLittleEndianByteArrayInt16()
    }

    fun generateRandomBytes(size: Int): ByteArray = Random.nextBytes(size)

    fun createCommand(
        function: Int,
        key: ByteArray,
        iv: ByteArray,
        data: ByteArray = byteArrayOf()
    ): ByteArray {
        return when (function) {
            0x00 -> {
                commandSerial.set(0)
                cmd(function, key, iv, 32)
            }
            0x01 -> {
                cmd(function, key, iv, 16, data)
            }
            0x02, 0x21, 0x23, 0x30 -> {
                cmd(function, key, iv, 1, data)
            }
            0x03, 0x04, 0x0E, 0x11, 0x13, 0x15, 0x22, 0x27, 0x2D-> {
                cmd(function, key, iv, data.size, data)
            }
            0x05, 0x0B, 0x0C, 0x10, 0x12, 0x14, 0x18, 0x1C, 0x20, 0x25, 0x2B, 0x34 -> {
                cmd(function, key, iv)
            }
            0x06 -> {
                cmd(function, key, iv, 56, data)
            }
            0x07 -> {
                cmd(function, key, iv, 11, data)
            }
            0x08 -> {
                cmd(function, key, iv, 128, data)
            }
            0x16, 0x1D, 0x26, 0x29 -> {
                cmd(function, key, iv, 2, data)
            }
            0x28, 0x2E -> {
                cmd(function, key, iv, 4, data)
            }
            0x2C -> {
                cmd(function, key, iv, 3, data)
            }
            else -> throw IllegalArgumentException("Unknown function")
        }
    }

    private fun cmd(
        function: Int,
        key: ByteArray,
        iv: ByteArray,
        dataSize :Int? = null,
        data: ByteArray? = null,
        serial: ByteArray = serialIncrementAndGet(),
    ): ByteArray {
        val functionName = ::cmd.name
        if (function == 0x00 && serial.size != 2) {
            throw IllegalArgumentException("Invalid serial size for function 0x00")
        }
        if (data != null && dataSize == null) {
            throw IllegalArgumentException("dataSize must be provided when data is not null")
        }
        val payload = when {
            function == 0x00 && dataSize != null -> {
                generateRandomBytes(dataSize).also {
                    Timber.d("$functionName[${function.toHexString()}]: ${serial.toHexPrint()}, ${function.toHexString()}, $dataSize, ${it.toHexPrint()}")
                }
            }
            data != null -> {
                data.also {
                    Timber.d("$functionName[${function.toHexString()}]: ${serial.toHexPrint()}, ${function.toHexString()}, $dataSize, ${it.toHexPrint()}")
                }
            }
            else -> {
                Timber.d("$functionName[${function.toHexString()}]: ${serial.toHexPrint()}, ${function.toHexString()}")
                byteArrayOf()
            }
        }

        val header = byteArrayOf(function.toByte(), (dataSize ?: 0).toByte())

        val rawData = serial + header + payload

        return encrypt(key, iv, pad(rawData))
            ?: throw IllegalArgumentException("Encryption failed: bytes cannot be null")
    }

    fun combineLockConfig13Cmd(lockConfig12: LockConfig.Twelve): ByteArray {
        val settingBytes = ByteArray(Config13.SIZE.byte)

        Timber.d("LockConfig.12: $lockConfig12")
        settingBytes[Config13.DIRECTION.byte] = lockConfig12.direction.toByte()
        settingBytes[Config13.PASSWORD.byte] = lockConfig12.password.toByte()
        settingBytes[Config13.REMOTE_PINCODE.byte] = lockConfig12.remotePinCode.toByte()
        settingBytes[Config13.GUIDING_CODE.byte] = lockConfig12.guidingCode.toByte()
        settingBytes[Config13.TWO_FA.byte] = lockConfig12.twoFA.toByte()
        settingBytes[Config13.VACATION_MODE.byte] = lockConfig12.vacationMode.toByte()
        settingBytes[Config13.AUTOLOCK.byte] = lockConfig12.autoLock.toByte()
        val autoLockDelayBytes = lockConfig12.autoLockTime.toLittleEndianByteArrayInt16()
        for (i in 0..autoLockDelayBytes.lastIndex) settingBytes[Config13.AUTOLOCK_DELAY.byte + i] = autoLockDelayBytes[i]
        settingBytes[Config13.OPERATING_SOUND.byte] = lockConfig12.operatingSound.toByte()
        settingBytes[Config13.SOUND_TYPE.byte] = lockConfig12.soundType.toByte()
        settingBytes[Config13.SOUND_VALUE.byte] = lockConfig12.soundValue.toByte()
        settingBytes[Config13.SHOW_FAST_TRACK_MODE.byte] = lockConfig12.showFastTrackMode.toByte()
        settingBytes[Config13.SABBATH_MODE.byte] = lockConfig12.sabbathMode.toByte()
        settingBytes[Config13.PHONETIC_LANGUAGE.byte] = lockConfig12.phoneticLanguage.toByte()
        settingBytes[Config13.BLE_TX.byte] = lockConfig12.bleTx.toByte()
        settingBytes[Config13.BLE_ADV.byte] = lockConfig12.bleAdv.toByte()
        return settingBytes
    }

    fun combineCredential27Cmd(credential27Cmd: Credential.TwentySevenCmd): ByteArray {
        val outputStream = ByteArrayOutputStream()
        outputStream.write(credential27Cmd.index.toLittleEndianByteArrayInt16())
        outputStream.write(credential27Cmd.status)
        outputStream.write(credential27Cmd.type)
        outputStream.write(credential27Cmd.userType)
        outputStream.write(credential27Cmd.code.extendedByteArray(8))
        val emptySchedule = WeekDaySchedule()
        repeat(7) { index ->
            val schedule = credential27Cmd.weekDayScheduleList.getOrNull(index) ?: emptySchedule
            outputStream.write(schedule.status)
            outputStream.write(schedule.dayMask)
            outputStream.write(schedule.startHour)
            outputStream.write(schedule.startMinute)
            outputStream.write(schedule.endHour)
            outputStream.write(schedule.endMinute)
        }
        credential27Cmd.yearDayScheduleList.forEach { schedule ->
            Timber.d("ValidTimeRange status: ${schedule.status}, start: ${schedule.start}, end: ${schedule.end}")
            outputStream.write(schedule.status)
            outputStream.write(schedule.start.limitValidTimeRange().toLittleEndianUInt32ByteArray())
            outputStream.write(schedule.end.limitValidTimeRange().toLittleEndianUInt32ByteArray())
        }
        return outputStream.toByteArray()
    }

    fun combineCredential2DCmd(credential2DCmd: Credential.TwentyDCmd): ByteArray {
        val outputStream = ByteArrayOutputStream()
        outputStream.write(credential2DCmd.index.toLittleEndianByteArrayInt16())
        outputStream.write(credential2DCmd.addCount)
        credential2DCmd.blackList.forEach { blackListDetail ->
            outputStream.write(blackListDetail.isUsing)
            outputStream.write(blackListDetail.type)
            outputStream.write(blackListDetail.code.extendedByteArray(12))
        }
        return outputStream.toByteArray()
    }

    fun resolve(function: Int, key: ByteArray, iv: ByteArray, notification: ByteArray): Any {
        return decrypt(key, iv, notification)?.let { decrypted ->
            val functionName = ::resolve.name
            val serialNumber = decrypted.copyOfRange(0, 2)
            val checkFunction = decrypted.component3().unSignedInt()
            val byteArrayData = decrypted.copyOfRange(4, 4 + decrypted.component4().unSignedInt())
            val booleanData = decrypted.component5().unSignedInt()
            Timber.d("$functionName[${function.toHexString()}]: ${serialNumber.toHexPrint()}, ${checkFunction.toHexString()}, ${byteArrayData.size}, ${byteArrayData.toHexPrint()}")
            if (checkFunction == function) {
                when (function) {
                    0x00, 0x01, 0x20 -> {
                        // ByteArray
                        byteArrayData
                    }
                    0x02 -> {
                        // LockVersion
                        resolve02(byteArrayData)
                    }
                    0x03 -> {
                        // OTAStatus
                        resolve03(byteArrayData)
                    }
                    0x04 -> {
                        // HexString
                        byteArrayData.toHexPrint()
                    }
                    0x05 -> {
                        // LockSetup
                        resolve05(byteArrayData)
                    }
                    0x06, 0x07, 0x08, 0x0B, 0x0E, 0x11, 0x16, 0x22, 0x23, 0x34 -> {
                        // Boolean
                        when (booleanData) {
                            0x01 -> true
                            0x00 -> false
                            else -> throw IllegalArgumentException("Unknown data")
                        }
                    }
                    0X10 -> {
                        // TimeSetting
                        resolve10(byteArrayData)
                    }
                    0x12 -> {
                        // LockConfig.Twelve
                        resolve12(byteArrayData)
                    }
                    0X13 -> {
                        // LockConfig.Thirteen
                        resolve13(byteArrayData)
                    }
                    0x14 -> {
                        // DeviceStatus.Fourteen & 0x15
                        resolve14(byteArrayData)
                    }
                    0x17 -> {
                        // Alert.Seventeen
                        resolve17(byteArrayData)
                    }
                    0x18 -> {
                        // Ability
                        resolve18(byteArrayData)
                    }
                    0x1C -> {
                        // Int
                        byteArrayData.toInt()
                    }
                    0x1D -> {
                        // EventLog
                        resolve1D(byteArrayData)
                    }
                    0x21 -> {
                        // DeviceToken.BleUser
                        resolve21(byteArrayData)
                    }
                    0x25 -> {
                        // Credential.TwentyFive
                        resolve25(byteArrayData)
                    }
                    0x26 -> {
                        // Credential.TwentySix
                        resolve26(byteArrayData)
                    }
                    0x27 -> {
                        // Credential.TwentySeven
                        resolve27(byteArrayData)
                    }
                    0x28 -> {
                        // Credential.TwentyEight
                        resolve28(byteArrayData)
                    }
                    0x29 -> {
                        // Credential.TwentyNine
                        resolve29(byteArrayData)
                    }
                    0x2B -> {
                        // Credential.TwentyB
                        resolve2B(byteArrayData)
                    }
                    0x2C -> {
                        // Credential.TwentyC
                        resolve2C(byteArrayData)
                    }
                    0x2D -> {
                        // Credential.TwentyD
                        resolve2D(byteArrayData)
                    }
                    0x2E -> {
                        // Credential.TwentyE
                        resolve2E(byteArrayData)
                    }
                    0x30 -> {
                        // Data.Thirty
                        resolve30(byteArrayData)
                    }
                    else -> throw IllegalArgumentException("Unknown function byte")
                }
            } else {
                throw IllegalArgumentException("Return function byte is not [$function]")
            }
        } ?: throw IllegalArgumentException("Error when decryption")
    }

    private fun resolve02(data: ByteArray): LockVersion {
        val functionName = ::resolve02.name
        val lockVersion = LockVersion(
            target = data.component1().unSignedInt(),
            mainVersion = data.component2().unSignedInt(),
            subVersion = data.component3().unSignedInt(),
        )
        Timber.d("$functionName: $lockVersion")
        return lockVersion
    }

    private fun resolve03(data: ByteArray): OTAStatus {
        val functionName = ::resolve03.name
        val target  = data.component1()
        val state  = data.component2()
        val isSuccess  = data.component3()
        val response = OTAStatus(
            target.toInt(),
            state.toInt(),
            isSuccess.toInt(),
        )
        Timber.d("$functionName: $response")
        return response
    }

    private fun resolve05(data: ByteArray): LockSetup {
        val functionName = ::resolve05.name
        val lockSetup = LockSetup(
            keyOne = data.component1().toInt(),
            token = data.component2().toInt(),
            uid = data.component3().toInt(),
            remotePinCode = data.component4().toInt(),
        )
        Timber.d("$functionName: $lockSetup")
        return lockSetup
    }

    private fun resolve10(data: ByteArray): TimeSetting {
        val functionName = ::resolve10.name
        val timeSetting = TimeSetting(
            time = data.copyOfRange(0, 4).toInt(),
            timeZone = data.copyOfRange(4, 8).toInt(),
        )
        Timber.d("$functionName: $timeSetting")
        return timeSetting
    }

    private fun resolve12(data: ByteArray): LockConfig.Twelve {
        val functionName = ::resolve12.name
        val autoLockTimeInt = data.copyOfRange(Config12.AUTOLOCK_DELAY.byte, Config12.AUTOLOCK_DELAY_LOWER_LIMIT.byte).toInt()
        Timber.d("autoLockTimeInt: $autoLockTimeInt")
        val autoLockTimeLowerLimitInt = data.copyOfRange(Config12.AUTOLOCK_DELAY_LOWER_LIMIT.byte, Config12.AUTOLOCK_DELAY_UPPER_LIMIT.byte).toInt()
        Timber.d("autoLockTimeLowerLimitInt: $autoLockTimeLowerLimitInt")
        val autoLockTimeUpperLimitInt = data.copyOfRange(Config12.AUTOLOCK_DELAY_UPPER_LIMIT.byte, Config12.OPERATING_SOUND.byte).toInt()
        Timber.d("autoLockTimeUpperLimitInt: $autoLockTimeUpperLimitInt")

        val lockConfig12 = LockConfig.Twelve(
            size = data.size,
            mainVersion = data[Config12.MAIN_VERSION.byte].unSignedInt(),
            subVersion = data[Config12.SUB_VERSION.byte].unSignedInt(),
            formatVersion = data[Config12.FORMAT_VERSION.byte].unSignedInt(),
            direction = when (data[Config12.DIRECTION.byte].unSignedInt()) {
                0xA0 -> Direction.RIGHT.value
                0xA1 -> Direction.LEFT.value
                0xA2 -> Direction.UNKNOWN.value
                else -> Direction.NOT_SUPPORT.value
            },
            currentVersion = data[Config12.CURRENT_VERSION.byte].unSignedInt(),
            password = when (data[Config12.PASSWORD.byte].unSignedInt()) {
                0 -> Password.CLOSE.value
                1 -> Password.OPEN.value
                else -> Password.NOT_SUPPORT.value
            },
            remotePinCode = when (data[Config12.REMOTE_PINCODE.byte].unSignedInt()) {
                0 -> RemotePinCode.CLOSE.value
                1 -> RemotePinCode.OPEN.value
                else -> RemotePinCode.NOT_SUPPORT.value
            },
            guidingCode = when (data[Config12.GUIDING_CODE.byte].unSignedInt()) {
                0 -> GuidingCode.CLOSE.value
                1 -> GuidingCode.OPEN.value
                else -> GuidingCode.NOT_SUPPORT.value
            },
            twoFA = when (data[Config12.TWO_FA.byte].unSignedInt()) {
                0 -> TwoFA.CLOSE.value
                1 -> TwoFA.OPEN.value
                else -> TwoFA.NOT_SUPPORT.value
            },
            vacationMode = when (data[Config12.VACATION_MODE.byte].unSignedInt()) {
                0 -> VacationMode.CLOSE.value
                1 -> VacationMode.OPEN.value
                else -> VacationMode.NOT_SUPPORT.value
            },
            autoLock = when (data[Config12.AUTOLOCK.byte].unSignedInt()) {
                0 -> AutoLock.CLOSE.value
                1 -> AutoLock.OPEN.value
                else -> AutoLock.NOT_SUPPORT.value
            },
            autoLockTime = when (autoLockTimeInt) {
                0xFFFF -> AutoLockTime.NOT_SUPPORT.value
                else -> {
                    autoLockTimeInt
                }
            },
            autoLockTimeLowerLimit = when (autoLockTimeLowerLimitInt) {
                0xFFFF -> AutoLockTimeLowerLimit.NOT_SUPPORT.value
                else -> {
                    autoLockTimeLowerLimitInt
                }
            },
            autoLockTimeUpperLimit = when (autoLockTimeUpperLimitInt) {
                0xFFFF -> AutoLockTimeUpperLimit.NOT_SUPPORT.value
                else -> {
                    autoLockTimeUpperLimitInt
                }
            },
            operatingSound = when (data[Config12.OPERATING_SOUND.byte].unSignedInt()) {
                0 -> OperatingSound.CLOSE.value
                1 -> OperatingSound.OPEN.value
                else -> OperatingSound.NOT_SUPPORT.value
            },
            soundType = when (data[Config12.SOUND_TYPE.byte].unSignedInt()) {
                0x01 -> SoundType.ON_OFF.value
                0x02 -> SoundType.LEVEL.value
                0x03 -> SoundType.PERCENTAGE.value
                else -> SoundType.NOT_SUPPORT.value
            },
            soundValue = when (data[Config12.SOUND_TYPE.byte].unSignedInt()) {
                0x01 -> if (data[Config12.SOUND_VALUE.byte].unSignedInt() == 100) SoundValue.OPEN.value else SoundValue.CLOSE.value
                0x02 -> when (data[Config12.SOUND_VALUE.byte].unSignedInt()) {
                    100 -> SoundValue.HIGH_VOICE.value
                    50 -> SoundValue.LOW_VOICE.value
                    else -> SoundValue.CLOSE.value
                }
                0x03 -> data[Config12.SOUND_VALUE.byte].unSignedInt()
                else -> SoundValue.NOT_SUPPORT.value
            },
            showFastTrackMode = when (data[Config12.SHOW_FAST_TRACK_MODE.byte].unSignedInt()) {
                0 -> ShowFastTrackMode.CLOSE.value
                1 -> ShowFastTrackMode.OPEN.value
                else -> ShowFastTrackMode.NOT_SUPPORT.value
            },
            sabbathMode = when (data[Config12.SABBATH_MODE.byte].unSignedInt()) {
                0 -> SabbathMode.CLOSE.value
                1 -> SabbathMode.OPEN.value
                else -> SabbathMode.NOT_SUPPORT.value
            },
            phoneticLanguage = when (data[Config12.PHONETIC_LANGUAGE.byte].unSignedInt()) {
                0 -> PhoneticLanguage.ENGLISH.value
                1 -> PhoneticLanguage.SPANISH.value
                2 -> PhoneticLanguage.FRENCH.value
                3 -> PhoneticLanguage.CHINESE.value
                else -> PhoneticLanguage.NOT_SUPPORT.value
            },
            supportPhoneticLanguage = when (data[Config12.SUPPORT_PHONETIC_LANGUAGE.byte].unSignedInt()) {
                0 -> SupportPhoneticLanguage.NOT_SUPPORT.value
                else -> data[Config12.SUPPORT_PHONETIC_LANGUAGE.byte].unSignedInt()
            },
            bleTx = data[Config12.BLE_TX.byte].unSignedInt(),
            bleAdv = data[Config12.BLE_ADV.byte].unSignedInt(),
        )
        Timber.d("$functionName: $lockConfig12")
        return lockConfig12
    }

    private fun resolve13(data: ByteArray): LockConfig.Thirteen {
        val functionName = ::resolve13.name
        val lockConfig13 = LockConfig.Thirteen(
            isSuccess = data.component1().unSignedInt() == 0x01,
            version = data.copyOfRange(1, 5).toInt()
        )
        Timber.d("$functionName: $lockConfig13")
        return lockConfig13
    }

    private fun resolve14(data: ByteArray): DeviceStatus.Fourteen {
        val functionName = ::resolve14.name
        val lockSetting = DeviceStatus.Fourteen(
            mainVersion = data[Config14.MAIN_VERSION.byte].unSignedInt(),
            subVersion = data[Config14.SUB_VERSION.byte].unSignedInt(),
            direction = when (data[Config14.LOCK_DIRECTION.byte].unSignedInt()) {
                0xA0 -> Direction.RIGHT.value
                0xA1 -> Direction.LEFT.value
                0xA2 -> Direction.UNKNOWN.value
                else -> Direction.NOT_SUPPORT.value
            },
            vacationMode = when (data[Config14.VACATION_MODE.byte].unSignedInt()) {
                0 -> VacationMode.CLOSE.value
                1 -> VacationMode.OPEN.value
                else -> VacationMode.NOT_SUPPORT.value
            },
            deadBolt = when (data[Config14.DEAD_BOLT.byte].unSignedInt()) {
                0 -> DeadBolt.NOT_PROTRUDE.value
                1 -> DeadBolt.PROTRUDE.value
                else -> DeadBolt.NOT_SUPPORT.value
            },
            doorState = when (data[Config14.DOOR_STATE.byte].unSignedInt()) {
                0 -> DoorState.OPEN.value
                1 -> DoorState.CLOSE.value
                else -> DoorState.NOT_SUPPORT.value
            },
            lockState = when (data[Config14.LOCK_STATE.byte].unSignedInt()) {
                0 -> LockState.UNLOCKED.value
                1 -> LockState.LOCKED.value
                else -> LockState.UNKNOWN.value
            },
            securityBolt = when (data[Config14.SECURITY_BOLT.byte].unSignedInt()) {
                0 -> SecurityBolt.NOT_PROTRUDE.value
                1 -> SecurityBolt.PROTRUDE.value
                else -> SecurityBolt.NOT_SUPPORT.value
            },
            battery = data[Config14.BATTERY.byte].unSignedInt(),
            batteryState = when (data[Config14.LOW_BATTERY.byte].unSignedInt()) {
                0 -> BatteryState.NORMAL.value
                1 -> BatteryState.WEAK_CURRENT.value
                else -> BatteryState.DANGEROUS.value
            }
        )
        Timber.d("$functionName: $lockSetting")
        return lockSetting
    }

    private fun resolve17(data: ByteArray): Alert.Seventeen {
        val functionName = ::resolve17.name
        val alertType = Alert.Seventeen(
            alertType = when (data.toInt()) {
                0 -> AlertType.ERROR_ACCESS_CODE.value
                1 -> AlertType.CURRENT_ACCESS_CODE_AT_WRONG_TIME.value
                2 -> AlertType.CURRENT_ACCESS_CODE_BUT_AT_VACATION_MODE.value
                3 -> AlertType.ACTIVELY_PRESS_THE_CLEAR_KEY.value
                20 -> AlertType.MANY_ERROR_KEY_LOCKED.value
                40 -> AlertType.LOCK_BREAK_ALERT.value
                0xFF -> AlertType.NONE.value
                else -> AlertType.UNKNOWN_ALERT_TYPE.value
            }
        )
        Timber.d("$functionName: $alertType")
        return alertType
    }

    private fun resolve18(data: ByteArray): Ability {
        val functionName = ::resolve18.name
        val response = Ability(
            weekDayScheduleCount = data.component1().unSignedInt(),
            yearDayScheduleCount = data.component2().unSignedInt(),
            codeCredentialCount = data.component3().unSignedInt(),
            fpCredentialCount = data.component4().unSignedInt(),
            faceCredentialCount = data.component5().unSignedInt(),
            logCount = data.copyOfRange(5, 7).toInt(),
            blackListCount = data.copyOfRange(7, 9).toInt()
        )
        Timber.d("$functionName: $response")
        return response
    }

    private fun resolve1D(data: ByteArray): EventLog {
        val functionName = ::resolve1D.name
        val timestamp = data.copyOfRange(0, 4).toInt().toLong()
        val millisecond = data.copyOfRange(4, 6).toInt()
        val event = data.copyOfRange(6, 7).toInt()
        val name = data.copyOfRange(7, data.size)
        val log = EventLog(
            timestamp = timestamp,
            millisecond = millisecond,
            event = event,
            name = String(name)
        )
        Timber.d("$functionName: $log")
        return log
    }

    private fun resolve21(data: ByteArray): DeviceToken.BleUser {
        val functionName = ::resolve21.name
        val bleUser = DeviceToken.BleUser(
            isValid = data.component1().unSignedInt() == 1,
            permission = String(data.copyOfRange(1, 2)),
            token = data.copyOfRange(2, 18).toHexString(),
            startTime = data.copyOfRange(18, 22).toInt().toLong(),
            endTime = data.copyOfRange(22, 26).toInt().toLong(),
            identity = String(data.copyOfRange(26, data.size))
        )
        Timber.d("$functionName: $bleUser")
        return bleUser
    }

    private fun resolve25(data: ByteArray): Credential.TwentyFive {
        val functionName = ::resolve25.name
        val transferComplete = data.component1().unSignedInt()
        val dataByteArray = data.copyOfRange(1, data.size)
        val credential25 = Credential.TwentyFive(transferComplete, dataByteArray)
        Timber.d("$functionName: $credential25")
        return credential25
    }

    private fun resolve26(data: ByteArray): Credential.TwentySix {
        val functionName = ::resolve26.name
        val index = data.copyOfRange(0, 2).toInt()
        val status = data.component3().toInt()
        val type = data.component4().toInt()
        val userType = data.component5().toInt()
        val code = if(type == CredentialType.PIN.value) data.copyOfRange(5, 13).toAsciiString().accessCodeToHex() else data.copyOfRange(5, 13)
        val weekDayScheduleListCount = data[13].unSignedInt()
        val yearDayScheduleListCount = data[14].unSignedInt()
        val weekDayScheduleListData = data.copyOfRange(15, 15 + weekDayScheduleListCount * 6)
        val yearDayScheduleListData = data.copyOfRange(
            15 + weekDayScheduleListCount * 6,
            15 + weekDayScheduleListCount * 6 + yearDayScheduleListCount * 9
        )
        val weekDayScheduleList:MutableList<WeekDaySchedule> = mutableListOf()
        if(weekDayScheduleListCount > 0) {
            for (i in 0 until weekDayScheduleListCount) {
                weekDayScheduleList.add(
                    WeekDaySchedule(
                        status = weekDayScheduleListData.copyOfRange(i * 6 + 0, i * 6 + 1).toInt(),
                        dayMask = weekDayScheduleListData.copyOfRange(i * 6 + 1, i * 6 + 2).toInt(),
                        startHour = weekDayScheduleListData.copyOfRange(i * 6 + 2, i * 6 + 3).toInt(),
                        startMinute = weekDayScheduleListData.copyOfRange(i * 6 + 3, i * 6 + 4).toInt(),
                        endHour = weekDayScheduleListData.copyOfRange(i * 6 + 4, i * 6 + 5).toInt(),
                        endMinute = weekDayScheduleListData.copyOfRange(i * 6 + 5, i * 6 + 6).toInt()
                    )
                )
            }
        }
        val yearDayScheduleList:MutableList<YearDaySchedule> = mutableListOf()
        if(yearDayScheduleListCount > 0) {
            for (i in 0 until yearDayScheduleListCount) {
                yearDayScheduleList.add(
                    YearDaySchedule(
                        status = yearDayScheduleListData.copyOfRange(i * 9 + 0, i * 9 + 1).toInt(),
                        start = yearDayScheduleListData.copyOfRange(i * 9 + 1, i * 9 + 5).toLong(),
                        end = yearDayScheduleListData.copyOfRange(i * 9 + 5, i * 9 + 9).toLong()
                    )
                )
            }
        }
        val credential26 = Credential.TwentySix(
            index = index,
            status = status,
            type = type,
            userType = userType,
            code = code,
            weekDayScheduleListCount = weekDayScheduleListCount,
            yearDayScheduleListCount = yearDayScheduleListCount,
            weekDayScheduleList = weekDayScheduleList,
            yearDayScheduleList = yearDayScheduleList
        )
        Timber.d("$functionName: $credential26")
        return credential26
    }

    private fun resolve27(data: ByteArray): Credential.TwentySeven {
        val functionName = ::resolve27.name
        val credential27 = Credential.TwentySeven(
            index = data.copyOfRange(0, 2).toInt(),
            isSuccess = data.component3().unSignedInt() == 1,
        )
        Timber.d("$functionName: $credential27")
        return credential27
    }

    private fun resolve28(data: ByteArray): Credential.TwentyEight {
        val functionName = ::resolve28.name
        val type = data.component1().unSignedInt()
        val state = data.component2().unSignedInt()
        val index = data.copyOfRange(2, 4).toInt()
        val status = data.component5().unSignedInt()
        val dataInfo = data.copyOfRange(5, data.size)
        val code = if(type == CredentialType.RFID.value) dataInfo.toHexString() else dataInfo.accessByteArrayToString()
        val credential28 = Credential.TwentyEight(type, state, index, status, dataInfo)
        Timber.d("$functionName: $credential28 codeString: $code")
        return credential28
    }

    private fun resolve29(data: ByteArray): Credential.TwentyNine {
        val functionName = ::resolve29.name
        val credential29 = Credential.TwentyNine(
            index = data.copyOfRange(0, 2).toInt(),
            isSuccess = data.component3().unSignedInt() == 1,
        )
        Timber.d("$functionName: $credential29")
        return credential29
    }

    private fun resolve2B(data: ByteArray): Credential.TwentyB {
        val functionName = ::resolve2B.name
        val transferComplete = data.component1().unSignedInt()
        val dataByteArray = data.copyOfRange(1, data.size)
        val credential2B = Credential.TwentyB(transferComplete, dataByteArray)
        Timber.d("$functionName: $credential2B")
        return credential2B
    }

    private fun resolve2C(data: ByteArray): Credential.TwentyC {
        val functionName = ::resolve2C.name
        val headerSize = 2
        val recordSize = 14
        val index = data.copyOfRange(0, headerSize).toInt()
        val remainingBytes = data.size - headerSize
        val blackListDetailSize = remainingBytes / recordSize
        val blackList = mutableListOf<BlackListDetail>()

        if(blackListDetailSize > 0){
            for (i in 0 until blackListDetailSize) {
                val currentOffset = headerSize + (i * recordSize)
                // isUsing: Offset + 0 (1 byte)
                val isUsing = data.copyOfRange(currentOffset, currentOffset + 1).toInt()
                // type: Offset + 1 (1 byte)
                val type = data.copyOfRange(currentOffset + 1, currentOffset + 2).toInt()
                // code: Offset + 2 (12 bytes) -> (2 + 12 = 14)
                val rawCodeBytes = data.copyOfRange(currentOffset + 2, currentOffset + 14)
                val code = if (type == CredentialType.PIN.value) {
                    rawCodeBytes.toAsciiString().accessCodeToHex()
                } else {
                    rawCodeBytes
                }
                blackList.add(
                    BlackListDetail(
                        isUsing = isUsing,
                        type = type,
                        code = code
                    )
                )
            }
        }
        val credential2C = Credential.TwentyC(index, blackList)
        Timber.d("$functionName: $credential2C")
        return credential2C
    }

    private fun resolve2D(data: ByteArray): Credential.TwentyD {
        val functionName = ::resolve2D.name
        val index = data.copyOfRange(0, 2).toInt()
        val addCount = data.component3().unSignedInt()
        val isSuccess = data.component4().unSignedInt() == 1
        val credential2D = Credential.TwentyD(index, addCount, isSuccess)
        Timber.d("$functionName: $credential2D")
        return credential2D
    }

    private fun resolve2E(data: ByteArray): Credential.TwentyE {
        val functionName = ::resolve2E.name
        val index = data.copyOfRange(0, 2).toInt()
        val deleteCount = data.copyOfRange(2, 4).toInt()
        val isSuccess = data.component5().unSignedInt() == 1
        val credential2E = Credential.TwentyE(index, deleteCount, isSuccess)
        Timber.d("$functionName: $credential2E")
        return credential2E
    }

    private fun resolve30(data: ByteArray): Data.Thirty {
        val functionName = ::resolve30.name
        val target = data.component1().unSignedInt()
        val sha256 = String(data.copyOfRange(1, 33))
        val data30 = Data.Thirty(target, sha256)
        Timber.d("$functionName: $data30")
        return data30
    }

    fun isValidNotification(key:ByteArray, iv:ByteArray, notification: ByteArray, function:Int): Boolean {
        return decrypt(key, iv, notification)?.let { decrypted ->
            decrypted.component3().unSignedInt() == function
        } ?: false
    }

    fun generateKeyTwo(randomNumberOneSecondHalf: ByteArray, randomNumberTwoFirstHalf: ByteArray): ByteArray {
        val keyTwo = ByteArray(16)
        for (i in 0..15) keyTwo[i] =
            ((randomNumberOneSecondHalf[i].unSignedInt()) xor (randomNumberTwoFirstHalf[i].unSignedInt())).toByte()
        return keyTwo
    }

    fun generateIvTwo(randomNumberOneFirstHalf: ByteArray, randomNumberTwoSecondHalf: ByteArray): ByteArray {
        val ivTwo = ByteArray(16)
        for (i in 0..15) ivTwo[i] =
            ((randomNumberOneFirstHalf[i].unSignedInt()) xor (randomNumberTwoSecondHalf[i].unSignedInt())).toByte()
        return ivTwo
    }

}