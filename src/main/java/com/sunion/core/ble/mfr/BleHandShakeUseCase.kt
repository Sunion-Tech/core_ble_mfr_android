package com.sunion.core.ble.mfr

import androidx.annotation.VisibleForTesting
import com.sunion.core.ble.mfr.exception.ConnectionTokenException
import com.polidea.rxandroidble2.NotificationSetupMode
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.sunion.core.ble.mfr.BleCmdRepository.Companion.NOTIFICATION_CHARACTERISTIC
import com.sunion.core.ble.mfr.entity.*
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BleHandShakeUseCase @Inject constructor(
    private val bleCmdRepository: BleCmdRepository,
    private val bleSessionRepository: BleSessionRepository
) : UseCase.ExecuteArgument3<LockConnectionInfo, RxBleDevice, RxBleConnection, Observable<String>> {


    override fun invoke(
        input1: LockConnectionInfo,
        input2: RxBleDevice,
        input3: RxBleConnection
    ): Observable<String> {
        val keyOne = input1.keyOne.hexToByteArray()
        val ivOne = input1.ivOne.hexToByteArray()
        val connectionToken = input1.token.hexToByteArray()
        Timber.d("keyOne: ${keyOne.toHexPrint()}")
        Timber.d("ivOne: ${ivOne.toHexPrint()}")
        Timber.d("connectionToken: ${connectionToken.toHexPrint()}")
        return bleHandshake(
            connection = input3,
            keyOne = keyOne,
            ivOne = ivOne,
            token = connectionToken
        )
    }

    private fun bleHandshake(
        connection: RxBleConnection,
        keyOne: ByteArray,
        ivOne: ByteArray,
        token: ByteArray
    ): Observable<String> {
        return send00(connection, keyOne, ivOne, token)
            .flatMap { sessionKey ->
                send01(connection, sessionKey.keyTwo, sessionKey.ivTwo, token)
                    .take(1)
                    .filter { it.first == DeviceToken.VALID_TOKEN }
                    .flatMap { stateAndPermission ->
                        bleSessionRepository.saveHandshakeSession(
                            sessionKey.keyTwo,
                            sessionKey.ivTwo,
                            token,
                            stateAndPermission.second
                        )
                        Observable.just(stateAndPermission.second)
                    }
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun send00(
        rxConnection: RxBleConnection,
        keyOne: ByteArray,
        ivOne: ByteArray,
        token: ByteArray,
        function: Int = 0x00
    ): Observable<SessionKey> {
        return Observable.zip(
            rxConnection.setupNotification(
                NOTIFICATION_CHARACTERISTIC,
                NotificationSetupMode.DEFAULT
            )
                .flatMap { notification -> notification }
                .filter { notification ->
                    val decrypted = bleCmdRepository.decrypt(
                        keyOne,
                        ivOne,
                        notification
                    )
                    decrypted?.component3()?.unSignedInt() == function
                },
            rxConnection.writeCharacteristic(
                NOTIFICATION_CHARACTERISTIC,
                bleCmdRepository.createCommand(function, keyOne, ivOne,  token)
            ).toObservable(),
            BiFunction { notification: ByteArray, written: ByteArray ->
                val randomNumberOne = bleCmdRepository.resolve(function, keyOne, ivOne, written) as ByteArray
                val randomNumberTwo = bleCmdRepository.resolve(function, keyOne, ivOne, notification) as ByteArray
                Timber.d("[${function.toHexString()}] has written: ${written.toHexPrint()} \nnotified: ${notification.toHexPrint()}")
                Timber.d("randomNumberOne: ${randomNumberOne.toHexPrint()}")
                Timber.d("randomNumberTwo: ${randomNumberTwo.toHexPrint()}")
                val keyTwo = bleCmdRepository.generateKeyTwo(
                    randomNumberOneSecondHalf = randomNumberOne.copyOfRange(16, 32),
                    randomNumberTwoFirstHalf = randomNumberTwo.copyOfRange(0, 16)
                )
                val ivTwo = bleCmdRepository.generateIvTwo(
                    randomNumberOneFirstHalf = randomNumberOne.copyOfRange(0, 16),
                    randomNumberTwoSecondHalf = randomNumberTwo.copyOfRange(16, 32)
                )
                Timber.d("keyTwo: ${keyTwo.toHexPrint()}")
                Timber.d("ivTwo: ${ivTwo.toHexPrint()}")

                SessionKey(keyTwo, ivTwo)
            }
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun send01(
        rxConnection: RxBleConnection,
        keyTwo: ByteArray,
        ivTwo: ByteArray,
        token: ByteArray,
        function: Int = 0x01
    ): Observable<Pair<Int, String>> {
        return Observable.zip(
            rxConnection.setupNotification(
                NOTIFICATION_CHARACTERISTIC,
                NotificationSetupMode.DEFAULT
            )
                .flatMap { notification -> notification }
                .filter { notification ->
                    bleCmdRepository.decrypt(keyTwo, ivTwo, notification)?.component3()
                        ?.unSignedInt() == function
                },
            rxConnection.writeCharacteristic(
                NOTIFICATION_CHARACTERISTIC,
                bleCmdRepository.createCommand(function, keyTwo, ivTwo, token)
            ).toObservable(),
            BiFunction { notification: ByteArray, written: ByteArray ->
                Timber.d("[${function.toHexString()}] has written: ${written.toHexPrint()}")
                Timber.d("[${function.toHexString()}] has notified: ${notification.toHexPrint()}")
                val tokenStateFromDevice = bleCmdRepository.resolve(function, keyTwo, ivTwo, notification) as ByteArray
                Timber.d("token state from device : ${tokenStateFromDevice.toHexPrint()}")
                val deviceToken = determineTokenState(tokenStateFromDevice)
                Timber.d("token state: ${token.toHexPrint()}")
                val permission = determineTokenPermission(tokenStateFromDevice)
                Timber.d("token permission: $permission")
                deviceToken to permission
            }
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun determineTokenState(data: ByteArray): Int {
        return when (data.component1().unSignedInt()) {
            0 -> throw ConnectionTokenException.IllegalTokenException()
            1 -> DeviceToken.VALID_TOKEN
            2 -> throw ConnectionTokenException.DeviceRefusedException()
            else -> throw ConnectionTokenException.IllegalTokenStateException()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun determineTokenPermission(data: ByteArray): String {
        return String(data.copyOfRange(1, 2))
    }

    data class SessionKey(
        val keyTwo: ByteArray,
        val ivTwo: ByteArray
    )

}
