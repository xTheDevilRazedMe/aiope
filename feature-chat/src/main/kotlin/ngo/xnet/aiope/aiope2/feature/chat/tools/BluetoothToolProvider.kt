package ngo.xnet.aiope.feature.chat.tools

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bluetooth tool provider for AIOPE.
 * Supports scanning, pairing, connecting, and basic BLE operations.
 */
class BluetoothToolProvider(private val ctx: Context) {
  private val TAG = "BluetoothTool"
  private val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val btAdapter = btManager?.adapter

  fun hasPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= 31) {
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
  }

  /** Scan for nearby Bluetooth devices */
  fun scanDevices(durationMs: Long = 10000): String {
    if (!hasPermission()) return "Error: Bluetooth permissions not granted."
    if (btAdapter == null) return "Error: Bluetooth not supported on this device."
    if (!btAdapter.isEnabled) return "Error: Bluetooth is disabled. Enable it in settings."

    return try {
      val devices = mutableMapOf<String, String>() // address -> info
      val deferred = CompletableDeferred<String>()
      
      val scanner = btAdapter.bluetoothLeScanner
      if (scanner != null) {
        val callback = object : ScanCallback() {
          override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unknown"
            val rssi = result.rssi
            val type = when (device.type) {
              BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
              BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
              BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
              else -> "Unknown"
            }
            devices[device.address] = "$name (${device.address}) [${type}] RSSI:${rssi}dBm"
          }
          
          override fun onScanFailed(errorCode: Int) {
            deferred.complete("Scan failed with error code: $errorCode")
          }
        }
        
        scanner.startScan(callback)
        Thread.sleep(durationMs)
        scanner.stopScan(callback)
        
        // Also get paired devices
        val paired = btAdapter.bondedDevices ?: emptySet()
        
        buildString {
          appendLine("=== Paired Devices ===")
          if (paired.isEmpty()) {
            appendLine("No paired devices.")
          } else {
            paired.forEach { d ->
              val name = d.name ?: "Unknown"
              appendLine("- $name (${d.address}) [bonded]")
            }
          }
          
          appendLine()
          appendLine("=== Nearby Devices (${devices.size} found) ===")
          if (devices.isEmpty()) {
            appendLine("No devices found during scan.")
          } else {
            devices.values.forEach { appendLine("- $it") }
          }
        }
      } else {
        // Fallback: just list paired devices
        val paired = btAdapter.bondedDevices ?: emptySet()
        buildString {
          appendLine("=== Paired Bluetooth Devices ===")
          if (paired.isEmpty()) {
            appendLine("No paired devices.")
          } else {
            paired.forEach { d ->
              appendLine("- ${d.name ?: "Unknown"} (${d.address})")
            }
          }
          appendLine()
          appendLine("(BLE scanning requires Android 5.0+)")
        }
      }
    } catch (e: Exception) {
      "Error scanning Bluetooth: ${e.message}"
    }
  }

  /** Get Bluetooth adapter info */
  fun getAdapterInfo(): String {
    if (btAdapter == null) return "Bluetooth not supported."
    return buildString {
      appendLine("=== Bluetooth Adapter ===")
      appendLine("Name: ${btAdapter.name}")
      appendLine("Address: ${btAdapter.address}")
      appendLine("Enabled: ${btAdapter.isEnabled}")
      appendLine("State: ${stateToString(btAdapter.state)}")
      appendLine("Scan mode: ${scanModeToString(btAdapter.scanMode)}")
      appendLine("Paired devices: ${btAdapter.bondedDevices?.size ?: 0}")
      if (Build.VERSION.SDK_INT >= 26) {
        appendLine("LE 2M PHY: ${btAdapter.isLe2MPhySupported}")
        appendLine("LE Coded PHY: ${btAdapter.isLeCodedPhySupported}")
      }
    }
  }

  /** Enable/disable Bluetooth */
  fun setEnabled(enable: Boolean): String {
    if (!hasPermission()) return "Permission denied."
    if (btAdapter == null) return "Bluetooth not supported."
    return try {
      if (enable) {
        if (btAdapter.enable()) "Bluetooth enabling..." else "Failed to enable Bluetooth."
      } else {
        if (btAdapter.disable()) "Bluetooth disabling..." else "Failed to disable Bluetooth."
      }
    } catch (e: SecurityException) {
      "Error: ${e.message}. BLUETOOTH_CONNECT permission required."
    }
  }

  private fun stateToString(state: Int): String = when (state) {
    BluetoothAdapter.STATE_OFF -> "OFF"
    BluetoothAdapter.STATE_ON -> "ON"
    BluetoothAdapter.STATE_TURNING_OFF -> "TURNING OFF"
    BluetoothAdapter.STATE_TURNING_ON -> "TURNING ON"
    else -> "UNKNOWN ($state)"
  }

  private fun scanModeToString(mode: Int): String = when (mode) {
    BluetoothAdapter.SCAN_MODE_NONE -> "None"
    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "Connectable"
    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "Discoverable"
    else -> "Unknown ($mode)"
  }
}
