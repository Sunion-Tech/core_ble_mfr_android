package com.sunion.core.ble.mfr

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.sunion.core.ble.mfr.exception.NotConnectedException
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.NotificationSetupMode
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleConnection.GATT_MTU_MAXIMUM
import com.polidea.rxandroidble2.RxBleCustomOperation
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.RxBleDeviceServices
import com.sunion.core.ble.mfr.BleCmdRepository.Companion.NOTIFICATION_CHARACTERISTIC
import com.sunion.core.ble.mfr.entity.*
import com.sunion.core.ble.mfr.entity.Event
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.rx2.asFlow
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import kotlin.Boolean
import kotlin.Pair

@Singleton
class ReactiveStatefulConnection @Inject constructor(
    private val scheduler: Scheduler,
    private val rxBleClient: RxBleClient,
    private val bleCmdRepository: BleCmdRepository,
    private val bleHandShakeUseCase: BleHandShakeUseCase,
    private val bleSessionRepository: BleSessionRepository
) : StatefulConnection {

    override val trashBin: CompositeDisposable = CompositeDisposable()
    private var _connection: Observable<RxBleConnection>? = null

    override var macAddress: String? = null
    override var connectionDisposable: Disposable? = null
    override val disconnectTriggerSubject = PublishSubject.create<Boolean>()


    private var _lockScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connState = MutableSharedFlow<Event<Pair<Boolean, String>>>(replay = 0, extraBufferCapacity = 1)
    override val connState: SharedFlow<Event<Pair<Boolean, String>>> = _connState.asSharedFlow()

    private val _bluetoothConnectState = MutableStateFlow(BluetoothConnectState.DISCONNECTED)
    override val bluetoothConnectState: StateFlow<BluetoothConnectState> = _bluetoothConnectState.asStateFlow()

    private val connectionTimer = LifecycleCountDownTimer(30000, 1000)

    private var _rxBleConnection: RxBleConnection? = null
    override val rxBleConnection: RxBleConnection?
        get() = _rxBleConnection

    private var _lockConnectionInfo: LockConnectionInfo = LockConnectionInfo()
    override val lockConnectionInfo
        get() = _lockConnectionInfo

    private var rxDeviceToken = DeviceToken.BleUser(
        isValid = false,
        permission = DeviceToken.PERMISSION_NONE,
        token = "",
        startTime = 0,
        endTime = 0
    )
    override var connection: Observable<RxBleConnection>
        get() {
            return _connection ?: connectionFallback()
        }
        set(value) {
            connectionTimer.stopAndClear()
            this._connection = value
        }

    init {
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                throwable.cause?.let {
                    Timber.e(it)
                    return@setErrorHandler
                }
            }
        }
    }

    private fun emitConnectionEvent(event: Event<Pair<Boolean, String>>) {
        _lockScope.launch {
            when (event.status) {
                EventState.LOADING -> {
                    _bluetoothConnectState.emit(BluetoothConnectState.CONNECTING)
                }
                EventState.READY -> {
                    _bluetoothConnectState.emit(BluetoothConnectState.CONNECTED)
                }
                EventState.SUCCESS -> {
                    if (event.data?.first == true) {
                        macAddress = lockConnectionInfo.macAddress
                        rxDeviceToken = rxDeviceToken.copy(
                            permission = lockConnectionInfo.permission ?: bleSessionRepository.requirePermission(),
                            token = lockConnectionInfo.token
                        )
                        _bluetoothConnectState.emit(BluetoothConnectState.CONNECT_SUCCESS)
                    } else {
                        Timber.e("Received SUCCESS event with false data, ignored.")
                    }
                }
                EventState.ERROR -> {
                    _bluetoothConnectState.emit(BluetoothConnectState.DISCONNECTED)
                    // 執行斷線清理
                    disconnectInternalCleanup()
                }
            }

            // 發送事件到 SharedFlow
            _connState.emit(event)
        }
    }

    /**
     * Fallback in case connection isn't valid.
     * @return Observable<RxBleConnection>
     *
     */
    override fun connectionFallback(): Observable<RxBleConnection> {
        return _connection ?: Observable.error(NotConnectedException())
    }

    override fun isConnectedWithDevice(): Boolean {
        return _connection != null && _bluetoothConnectState.value == BluetoothConnectState.CONNECT_SUCCESS
    }

    override fun establishConnection(
        macAddress: String,
        keyOne: String,
        ivOne: String,
        token: String,
        isSilentlyFail: Boolean
    ): Disposable {
        disconnectInternalCleanup()

        connectionTimer.start()

        // six groups of two hex digits without colon
        _lockConnectionInfo = LockConnectionInfo(
            macAddress = macAddress.colonMac(),
            keyOne = keyOne,
            ivOne = ivOne,
            token = token,
            keyTwo = null,
            ivTwo = null,
            permission = null
        )

        // six groups of two hex digits with colon
        this.macAddress = macAddress.colonMac().uppercase()
        val device = rxBleClient.getBleDevice(macAddress.colonMac().uppercase())
        // please see https://github.com/Polidea/RxAndroidBle/wiki/Tutorial:-Connection-Observable-sharing
        val connection = establishBleConnectionAndRequestMtu(device)
            .doOnSubscribe {
                emitConnectionEvent(Event.loading())
            }
            .compose(ReplayingShare.instance())

        val disposable = runConnectionSequence(connection, device, isSilentlyFail)

        connectionDisposable = disposable

        return disposable
    }

    override fun establishBleConnectionAndRequestMtu(device: RxBleDevice): Observable<RxBleConnection> {
        return Observable.timer(500, TimeUnit.MILLISECONDS)
            .flatMap { device.establishConnection(false) }
            .takeUntil(
                disconnectTriggerSubject.doOnNext { isDisconnected ->
                    if (isDisconnected) {
                        // 這裡是 RxJava 的 takeUntil 觸發的斷線
                        Timber.d("device isDisconnected with RxJava connect")
                    }
                }
            )
            .flatMap { connection ->
                Timber.d("connected to device and request mtu :${connection.mtu}")
                connection
                    .requestMtu(GATT_MTU_MAXIMUM)
                    .ignoreElement()
                    .doOnError { error -> Timber.d(error) }
                    .andThen(Observable.just(connection))
            }
    }

    override fun runConnectionSequence(
        rxBleConnection: Observable<RxBleConnection>,
        device: RxBleDevice,
        isSilentlyFail: Boolean
    ): Disposable {
        return connectionSequenceObservable(device, rxBleConnection)
            .doOnSubscribe {
                emitConnectionEvent(Event.loading())
            }
            .doOnNext { this._connection = rxBleConnection }
            .subscribeOn(scheduler.single())
            .subscribe(
                { permission -> actionAfterDeviceTokenExchanged(permission, device) },
                { actionAfterConnectionError(it, device.macAddress, isSilentlyFail) }
            )
            .apply { trashBin.add(this) }
    }

    override fun actionAfterDeviceTokenExchanged(
        permission: String,
        device: RxBleDevice
    ) {
        if (permission.isNotBlank()) {
            connectionTimer.cancel()
            Timber.d("action after device token exchanged: connected with device: ${device.macAddress}, and the stateful connection has been shared: $connection")
            emitConnectionEvent(Event.success(Pair(true, permission)))
        }
    }

    override fun actionAfterConnectionError(
        error: Throwable,
        macAddress: String,
        isSilentlyFail: Boolean
    ) {
        Timber.e("connection $macAddress error called: $error")

        val event = if (isSilentlyFail) {
            Event.error(error::class.java.simpleName)
        } else {
            Event.error(error::class.java.simpleName, false to macAddress)
        }

        emitConnectionEvent(event)
    }

    override fun sendBytes(bytes: ByteArray): Observable<ByteArray> {
        return connection.flatMapSingle { connection ->
            connection.writeCharacteristic(
                NOTIFICATION_CHARACTERISTIC,
                bytes
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun connectionSequenceObservable(
        device: RxBleDevice,
        rxBleConnection: Observable<RxBleConnection>
    ): Observable<String> {
        val bluetoothGattRefreshCustomOp = refreshAndroidStackCacheCustomOperation()
        val discoverServicesCustomOp = customDiscoverServicesOperation()

        return rxBleConnection
            .flatMap { rxConnection ->
                Timber.d("device mtu size: ${rxConnection.mtu}")
                _rxBleConnection = rxConnection
                (_rxBleConnection as RxBleConnection)
                    .queue(bluetoothGattRefreshCustomOp)
                    .ignoreElements()
                    .andThen(rxConnection.queue(discoverServicesCustomOp))
                    .flatMap {
                        emitConnectionEvent(Event.ready(Pair(true, "")))
                        bleHandShakeUseCase.invoke(lockConnectionInfo, device, rxConnection)
                    }
            }
    }

    override fun sendCommandThenWaitSingleNotification(bytes: ByteArray): Observable<ByteArray> {
        return connection.flatMap { rxConnection ->
            Observable.zip(
                rxConnection.setupNotification(
                    NOTIFICATION_CHARACTERISTIC,
                    NotificationSetupMode.DEFAULT
                )
                    .flatMap { notification -> notification },
                rxConnection.writeCharacteristic(NOTIFICATION_CHARACTERISTIC, bytes).toObservable()
            ) { notification: ByteArray, _: ByteArray ->
                notification
            }
        }
    }

    override fun sendCommandThenWaitNotifications(bytes: ByteArray): Observable<ByteArray> {
        return Observable.never()
    }

    override infix fun setupNotificationsFor(function: Int): Observable<ByteArray> {
        return connection.flatMap { rxConnection ->
            rxConnection.setupNotification(NOTIFICATION_CHARACTERISTIC, NotificationSetupMode.DEFAULT)
                .flatMap { it }
        }
    }

    override fun setupSingleNotificationThenSendCommand(
        command: ByteArray,
        functionName: String
    ): Flow<ByteArray> {
        val connection = rxBleConnection ?: return flow {
            throw NotConnectedException()
        }

        return connection
            .setupNotification(NOTIFICATION_CHARACTERISTIC)
            .flatMap { notificationObservable ->
                // writeCharacteristic 回傳 Single<ByteArray>
                val writeOperation = connection.writeCharacteristic(NOTIFICATION_CHARACTERISTIC, command)
                    .toObservable() // 先轉成 Observable<ByteArray>
                    .doOnNext {
                        bleCmdRepository.decrypt(keyTwo(), ivTwo(), command)
                            ?.let { Timber.d("cmd written: ${it.toHexPrint()} in $functionName") }
                    }
                    .ignoreElements() // 忽略資料，轉成 Completable
                    .toObservable<ByteArray>() // 將 Completable 轉回 Observable<ByteArray>
                // notificationObservable: 負責收通知
                // writeOperation: 負責寫入 (不會發出資料，但發生錯誤時會中斷流)
                Observable.merge(notificationObservable, writeOperation)
            }
            .asFlow()
            .onEach { notification ->
                bleCmdRepository.decrypt(keyTwo(), ivTwo(), notification)
                    ?.let { Timber.d("notification received: ${it.toHexPrint()} in $functionName") }
            }
            .catch { e ->
                Timber.e(e, "BLE error in $functionName")
                throw e
            }
    }

    override fun addDisposable(disposable: Disposable): Boolean {
        return if (!disposable.isDisposed) {
            trashBin.add(disposable)
        } else {
            false
        }
    }

    override fun disconnect() {
        val disconnectEvent = Event.error(
            message = "User Disconnected", // 明確標示是使用者主動
            data = Pair(false, "User Disconnected")
        )
        emitConnectionEvent(disconnectEvent)
    }

    private fun disconnectInternalCleanup() {
        this.macAddress = null
        this.rxDeviceToken = DeviceToken.BleUser(
            isValid = false,
            permission = DeviceToken.PERMISSION_NONE,
            token = "",
        )
        close()
    }

    override fun close() {
        disconnectTriggerSubject.onNext(true)
        connectionTimer.stopAndClear()
        connectionDisposable?.dispose()
        connectionDisposable = null
        _connection = null
        _rxBleConnection = null
        trashBin.clear()
    }

    private fun refreshAndroidStackCacheCustomOperation() =
        RxBleCustomOperation { bluetoothGatt, _, _ ->
            try {
                val bluetoothGattRefreshFunction: Method? = try {
                    bluetoothGatt.javaClass.getMethod("refresh")
                } catch (e: NoSuchMethodException) {
                    Timber.e("Could not find BluetoothGatt.refresh method (Hidden API might be removed)")
                    null
                }
                val success = (bluetoothGattRefreshFunction?.invoke(bluetoothGatt) as? Boolean) ?: false

                if (!success) {
                    Timber.e("BluetoothGatt.refresh() returned false or failed")
                    Observable.empty()
                } else {
                    Timber.d("BluetoothGatt cache cleared successfully")
                    Observable.empty<Void>().delay(200, TimeUnit.MILLISECONDS)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to invoke BluetoothGatt.refresh via reflection")
                Observable.empty()
            }
        }


    @SuppressLint("MissingPermission")
    private fun customDiscoverServicesOperation() =
        RxBleCustomOperation { bluetoothGatt, rxBleGattCallback, _ ->
            val success: Boolean = bluetoothGatt.discoverServices()
            if (!success) {
                Observable.error(RuntimeException("BluetoothGatt.discoverServices() returned false"))
            } else {
                rxBleGattCallback.onServicesDiscovered
                    .take(1) // so this RxBleCustomOperation will complete after the first result from BluetoothGattCallback.onServicesDiscovered()
                    .map(RxBleDeviceServices::getBluetoothGattServices)
            }
        }

    fun keyTwo(): ByteArray {
        return bleSessionRepository.requireKeyTwo()
    }

    fun ivTwo(): ByteArray {
        return bleSessionRepository.requireIvTwo()
    }
}