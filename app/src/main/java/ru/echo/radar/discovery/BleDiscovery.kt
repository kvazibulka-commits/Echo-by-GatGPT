package ru.echo.radar.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object BlePayload {
    const val MANUFACTURER_ID = 0x0ECA
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).copyOf(8)
    fun encode(session: String, track: String): ByteArray = byteArrayOf(1) + hash(session) + hash(track)
    fun valid(bytes: ByteArray): Boolean = bytes.size == 17 && bytes[0] == 1.toByte()
}

/** Foreground discovery only. Caller owns lifecycle and requests runtime permissions first. */
class BleDiscovery(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val adapter get() = app.getSystemService(BluetoothManager::class.java)?.adapter
    private var active = false
    private var advertiserCallback: AdvertiseCallback? = null
    private var scannerCallback: ScanCallback? = null

    fun hasPermissions(): Boolean {
        val names = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return names.all { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    @SuppressLint("MissingPermission")
    fun start(session: String, track: String, onPeer: (ByteArray, Int) -> Unit, onError: (String) -> Unit) {
        close()
        if (!hasPermissions()) { onError("Разрешите поиск устройств поблизости"); return }
        val radio = adapter
        if (radio == null || !radio.isEnabled) { onError("Включите Bluetooth"); return }
        val scanner = radio.bluetoothLeScanner
        val advertiser = radio.bluetoothLeAdvertiser
        if (scanner == null || advertiser == null) { onError("Устройство не поддерживает BLE-маяк"); return }
        val advertise = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) { onError("Не удалось включить BLE-маяк: $errorCode"); close() }
        }
        val scan = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val bytes = result.scanRecord?.getManufacturerSpecificData(BlePayload.MANUFACTURER_ID) ?: return
                if (BlePayload.valid(bytes)) onPeer(bytes.copyOf(), result.rssi)
            }
            override fun onScanFailed(errorCode: Int) { onError("Поиск BLE прерван: $errorCode"); close() }
        }
        advertiserCallback = advertise
        scannerCallback = scan
        active = true
        try {
            advertiser.startAdvertising(AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).setConnectable(false).build(),
                AdvertiseData.Builder().addManufacturerData(BlePayload.MANUFACTURER_ID,
                    BlePayload.encode(session, track)).build(), advertise)
            scanner.startScan(listOf(ScanFilter.Builder().setManufacturerData(BlePayload.MANUFACTURER_ID,
                byteArrayOf(1), byteArrayOf(-1)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scan)
        } catch (_: SecurityException) { close(); onError("Доступ к Bluetooth отозван") }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        if (active && hasPermissions()) {
            try {
                advertiserCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
                scannerCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
            } catch (_: SecurityException) { /* Permission may be revoked during stop. */ }
        }
        advertiserCallback = null
        scannerCallback = null
        active = false
    }
}
