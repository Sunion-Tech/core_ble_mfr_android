package com.sunion.core.ble.mfr.usecase

import android.os.PowerManager
import androidx.core.util.forEach
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import com.sunion.core.ble.mfr.UseCase
import com.sunion.core.ble.mfr.hexToByteArray
import com.sunion.core.ble.mfr.toHexString
import com.sunion.core.ble.mfr.toInt
import com.sunion.core.ble.mfr.toLittleEndianByteArrayInt16
import io.reactivex.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanUseCase @Inject constructor(
    private val rxBleClient: RxBleClient,
    private val powerManager: PowerManager,
) : UseCase.Execute<Unit, Observable<ScanResult>> {

    override fun invoke(input: Unit): Observable<ScanResult> {
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return rxBleClient.scanBleDevices(scanSettings, ScanFilter.empty())
    }

    fun scanAllManufacturerData(): Observable<ScanResult> {
        val functionName = ::scanAllManufacturerData.name
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return rxBleClient.scanBleDevices(scanSettings, ScanFilter.empty()).map { scanResult ->
            scanResult.scanRecord.manufacturerSpecificData.forEach { key, value ->
                Timber.d("$functionName manufacturerId: ${key.toHexString()}, manufacturerData: ${value.toHexString()} mac: ${scanResult.bleDevice.macAddress}")
            }
            scanResult
        }

    }

    fun scanAllManufacturerIdDevices(manufacturerId: Int = 0x0CE3): Observable<ScanResult> {
        val functionName = ::scanAllManufacturerIdDevices.name
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanFilter = ScanFilter.Builder().setManufacturerData(manufacturerId, byteArrayOf()).build()

        return rxBleClient
            .scanBleDevices(scanSettings, scanFilter)
            .filter { scanResult ->
                val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(manufacturerId)
                Timber.d("$functionName manufacturerId: ${manufacturerId.toHexString()} manufacturerData: ${manufacturerData?.toHexString()} mac: ${scanResult.bleDevice.macAddress}")
                manufacturerData != null && manufacturerData.isNotEmpty() && manufacturerData.component3() == 1.toByte()
            }

    }

    fun scanUid(manufacturerId: Int = 0x0CE3, uid: Int): Observable<ScanResult> {
        val functionName = ::scanUid.name
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanFilter = ScanFilter.Builder().setManufacturerData(manufacturerId, uid.toLittleEndianByteArrayInt16()).build()

        return rxBleClient
            .scanBleDevices(scanSettings, scanFilter)
            .filter { scanResult ->
                val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(manufacturerId)
                Timber.d("$functionName manufacturerId: ${manufacturerId.toHexString()} uuid:$uid manufacturerData: ${manufacturerData?.toHexString()} mac: ${scanResult.bleDevice.macAddress}")
                manufacturerData != null && manufacturerData.isNotEmpty() && manufacturerData.component3() == 0.toByte() && manufacturerData.copyOfRange(0, 2).toInt() == uid
            }

    }

    fun scanUuid(manufacturerId: Int = 0x0CE3, uuid: String): Observable<ScanResult> {
        val functionName = ::scanUuid.name
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanFilter = ScanFilter.Builder().setManufacturerData(manufacturerId, uuid.hexToByteArray()).build()

        return rxBleClient
            .scanBleDevices(scanSettings, scanFilter)
            .filter { scanResult ->
                val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(manufacturerId)
                Timber.d("$functionName manufacturerId: ${manufacturerId.toHexString()} uuid:$uuid manufacturerData: ${manufacturerData?.toHexString()} mac: ${scanResult.bleDevice.macAddress}")
                manufacturerData != null && manufacturerData.isNotEmpty() && manufacturerData.copyOfRange(0, 8).toHexString() == uuid
            }

    }

    fun longScan(macAddress: String? = null, uuid: String? = null): Observable<ScanResult> {
        if(macAddress.isNullOrBlank() && uuid.isNullOrBlank()){
            throw IllegalArgumentException("Either macAddress or uuid must be provided")
        }
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilter = if(uuid?.isNotBlank() == true) {
            Timber.d("Scanning of Lock uuid: $uuid started, the device screen isInteractive: ${powerManager.isInteractive}")
            ScanFilter.Builder().setManufacturerData(0x0CE3, uuid.hexToByteArray()).build()
        } else {
            Timber.d("Scanning of Lock macAddress: $macAddress started, the device screen isInteractive: ${powerManager.isInteractive}")
            ScanFilter.Builder().setDeviceAddress(macAddress).build()
        }
        return rxBleClient.scanBleDevices(scanSettings, scanFilter)
    }
}
