package com.sunion.core.ble.mfr.usecase

import com.sunion.core.ble.mfr.BleCmdRepository
import com.sunion.core.ble.mfr.ReactiveStatefulConnection
import com.sunion.core.ble.mfr.entity.Credential
import com.sunion.core.ble.mfr.entity.CredentialState
import com.sunion.core.ble.mfr.entity.CredentialType
import com.sunion.core.ble.mfr.entity.WeekDaySchedule
import com.sunion.core.ble.mfr.entity.YearDaySchedule
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.sunion.core.ble.mfr.toAsciiByteArray
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

class LockCredentialUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val statefulConnection: ReactiveStatefulConnection
) {
    private val className = this::class.simpleName ?: "LockCredentialUseCase"

    suspend fun getCredentialArray(): List<Boolean> {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getCredentialArray.name
        val function = 0x25
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
                bleCmdRepository.resolve(
                    function,
                    statefulConnection.keyTwo(), statefulConnection.ivTwo(),
                    notification
                ) as Credential.TwentyFive
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

    suspend fun getCredential(index: Int): Credential.TwentySix {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::getCredential.name
        val function = 0x26
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
                ) as Credential.TwentySix
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun addCode(index: Int, status: Int, userType:Int, code: String, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = addCredential(index, status, type = CredentialType.PIN.value, userType, code = code.toAsciiByteArray(), weekDayScheduleList, yearDayScheduleList)
    suspend fun addCard(index: Int, status: Int, userType:Int, code: ByteArray, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = addCredential(index, status, type = CredentialType.RFID.value, userType, code = code, weekDayScheduleList, yearDayScheduleList)
    suspend fun addFingerPrint(index: Int, status: Int, userType:Int, credential28Index: Int, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = addCredential(index, status, type = CredentialType.FINGERPRINT.value, userType, code = credential28Index.toLittleEndianByteArrayInt16(), weekDayScheduleList, yearDayScheduleList)
    suspend fun addFace(index: Int, status: Int, userType:Int, credential28Index: Int, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = addCredential(index, status, type = CredentialType.FACE.value, userType, code = credential28Index.toLittleEndianByteArrayInt16(), weekDayScheduleList, yearDayScheduleList)

    private suspend fun addCredential(
        index: Int,
        status: Int,
        type: Int,
        userType: Int,
        code: ByteArray,
        weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() },
        yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule()),
    ): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::addCredential.name
        val function = 0x27
        val data = bleCmdRepository.combineCredential27Cmd(
            Credential.TwentySevenCmd(
                index = index,
                status = status,
                type = type,
                userType = userType,
                code = code,
                weekDayScheduleList = weekDayScheduleList,
                yearDayScheduleList = yearDayScheduleList,
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
                ) as Credential.TwentySeven
                result.isSuccess
            }
            .flowOn(Dispatchers.IO)
            .catch { e -> Timber.e("$functionName exception $e") }
            .single()
    }

    suspend fun editCode(index: Int, status: Int, userType:Int, code: String, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = editCredential(index, status, type = CredentialType.PIN.value, userType, code = code.toAsciiByteArray(), weekDayScheduleList, yearDayScheduleList)
    suspend fun editCard(index: Int, status: Int, userType:Int, code: ByteArray, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = editCredential(index, status, type = CredentialType.RFID.value, userType, code = code, weekDayScheduleList, yearDayScheduleList)
    suspend fun editFingerprint(index: Int, status: Int, userType:Int, credential28Index: Int, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = editCredential(index, status, type = CredentialType.FINGERPRINT.value, userType, code = credential28Index.toLittleEndianByteArrayInt16(), weekDayScheduleList, yearDayScheduleList)
    suspend fun editFace(index: Int, status: Int, userType:Int, credential28Index: Int, weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() }, yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule())): Boolean = editCredential(index, status, type = CredentialType.FACE.value, userType, code = credential28Index.toLittleEndianByteArrayInt16(), weekDayScheduleList, yearDayScheduleList)
    private suspend fun editCredential(
        index: Int,
        status: Int,
        type: Int,
        userType: Int,
        code: ByteArray,
        weekDayScheduleList: MutableList<WeekDaySchedule> = MutableList(7) { WeekDaySchedule() },
        yearDayScheduleList: MutableList<YearDaySchedule> = mutableListOf(YearDaySchedule()),
    ): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::editCredential.name
        val function = 0x27
        val data = bleCmdRepository.combineCredential27Cmd(
            Credential.TwentySevenCmd(
                index = index,
                status = status,
                type = type,
                userType = userType,
                code = code,
                weekDayScheduleList = weekDayScheduleList,
                yearDayScheduleList = yearDayScheduleList,
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
                ) as Credential.TwentySeven
                result.isSuccess
            }
            .flowOn(Dispatchers.IO)
            .catch { e -> Timber.e("$functionName exception $e") }
            .single()
    }

    private suspend fun deviceGetCredential(type:Int, state:Int, index: Int): Credential.TwentyEight {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::deviceGetCredential.name
        val function = 0x28
        val sendCmd = bleCmdRepository.createCommand(
            function = function,
            key = statefulConnection.keyTwo(),
            iv = statefulConnection.ivTwo(),
            data = byteArrayOf(type.toByte(), state.toByte()) + index.toLittleEndianByteArrayInt16()
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
                ) as Credential.TwentyEight
                result
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

    suspend fun deviceGetCard(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.RFID.value , CredentialState.START.value, index)
    suspend fun deviceGetFingerprint(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.FINGERPRINT.value,  CredentialState.START.value, index)
    suspend fun deviceGetFace(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.FACE.value,  CredentialState.START.value ,index)

    suspend fun deviceExitCard(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.RFID.value,  CredentialState.EXIT.value, index)
    suspend fun deviceExitFingerprint(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.FINGERPRINT.value,  CredentialState.EXIT.value, index)
    suspend fun deviceExitFace(index: Int): Credential.TwentyEight = deviceGetCredential(CredentialType.FACE.value,  CredentialState.EXIT.value ,index)

    suspend fun deleteCredential(index: Int): Boolean {
        if (!statefulConnection.isConnectedWithDevice()) throw NotConnectedException()
        val functionName = ::deleteCredential.name
        val function = 0x29
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
                ) as Credential.TwentyNine
                result.isSuccess
            }
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Timber.e("$functionName exception $e")
            }
            .single()
    }

}