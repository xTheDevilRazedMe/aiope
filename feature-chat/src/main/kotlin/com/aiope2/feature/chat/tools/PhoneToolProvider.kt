package com.aiope2.feature.chat.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * Phone call and telephony tools for AIOPE.
 * Supports making calls, checking phone state, and SIM info.
 */
class PhoneToolProvider(private val ctx: Context) {
  private val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

  fun hasPhonePermission(): Boolean {
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
           ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
  }

  /** Initiate a phone call */
  fun makeCall(phoneNumber: String): String {
    return try {
      val intent = Intent(Intent.ACTION_CALL).apply {
        data = Uri.parse("tel:${phoneNumber.replace(" ", "").replace("-", "")}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(intent)
      "Dialing $phoneNumber..."
    } catch (e: SecurityException) {
      "Error: CALL_PHONE permission required."
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Open dialer with number (no direct call) */
  fun openDialer(phoneNumber: String): String {
    return try {
      val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:${phoneNumber.replace(" ", "").replace("-", "")}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(intent)
      "Dialer opened with $phoneNumber"
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Get phone/SIM information */
  fun getPhoneInfo(): String {
    if (telephony == null) return "Telephony not available."
    
    return try {
      buildString {
        appendLine("=== Phone Information ===")
        appendLine("Device ID: ${telephony.deviceId ?: "N/A"}")
        appendLine("IMEI: ${if (Build.VERSION.SDK_INT >= 26) telephony.imei ?: "N/A" else "Requires API 26+"}")
        appendLine("Network operator: ${telephony.networkOperatorName ?: "N/A"}")
        appendLine("SIM operator: ${telephony.simOperatorName ?: "N/A"}")
        appendLine("Network type: ${networkTypeName(telephony.networkType)}")
        appendLine("Phone type: ${phoneTypeName(telephony.phoneType)}")
        appendLine("SIM state: ${simStateName(telephony.simState)}")
        appendLine("Roaming: ${telephony.isNetworkRoaming}")
        appendLine("Data activity: ${dataActivityName(telephony.dataActivity)}")
        appendLine("Data state: ${dataStateName(telephony.dataState)}")
        
        if (Build.VERSION.SDK_INT >= 24) {
          appendLine("Voice network type: ${networkTypeName(telephony.voiceNetworkType)}")
          appendLine("Data network type: ${networkTypeName(telephony.dataNetworkType)}")
        }
        
        // Multi-SIM info
        if (Build.VERSION.SDK_INT >= 24) {
          try {
            val subManager = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subs = subManager?.activeSubscriptionInfoList
            if (!subs.isNullOrEmpty()) {
              appendLine()
              appendLine("=== SIM Cards (${subs.size}) ===")
              subs.forEach { sub ->
                appendLine("- SIM ${sub.simSlotIndex + 1}: ${sub.displayName} (${sub.carrierName})")
                appendLine("  Number: ${sub.number ?: "N/A"}")
                appendLine("  Country: ${sub.countryIso ?: "N/A"}")
              }
            }
          } catch (_: SecurityException) {
            appendLine("(Multi-SIM info requires READ_PHONE_STATE)")
          }
        }
      }
    } catch (e: SecurityException) {
      "Error: READ_PHONE_STATE permission required."
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Get current network signal strength info */
  fun getSignalStrength(): String {
    return try {
      buildString {
        appendLine("=== Signal Strength ===")
        if (Build.VERSION.SDK_INT >= 28) {
          val signalStrength = telephony?.signalStrength
          if (signalStrength != null) {
            if (Build.VERSION.SDK_INT >= 29) {
              val cellSignal = signalStrength.cellSignalStrengths
              cellSignal.forEach { cell ->
                appendLine("- Type: ${cell.javaClass.simpleName}")
                appendLine("  Level: ${cell.level}/4")
                appendLine("  dBm: ${cell.dbm}")
                if (Build.VERSION.SDK_INT >= 30) {
                  appendLine("  ASU: ${cell.asuLevel}")
                }
              }
            } else {
              @Suppress("DEPRECATION")
              appendLine("GSM: ${signalStrength.gsmSignalStrength}")
              @Suppress("DEPRECATION")
              appendLine("CDMA dBm: ${signalStrength.cdmaDbm}")
              @Suppress("DEPRECATION")
              appendLine("EVDO dBm: ${signalStrength.evdoDbm}")
              appendLine("Level: ${signalStrength.level}/4")
            }
          } else {
            appendLine("Signal strength unavailable")
          }
        } else {
          appendLine("Requires Android 9+")
        }
      }
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Check if device is on a call */
  fun getCallState(): String {
    return try {
      val state = telephony?.callState ?: TelephonyManager.CALL_STATE_IDLE
      when (state) {
        TelephonyManager.CALL_STATE_IDLE -> "No active call"
        TelephonyManager.CALL_STATE_RINGING -> "Incoming call"
        TelephonyManager.CALL_STATE_OFFHOOK -> "Active call"
        else -> "Unknown state: $state"
      }
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  private fun networkTypeName(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
    TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
    TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
    TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
    else -> "Other ($type)"
  }

  private fun phoneTypeName(type: Int): String = when (type) {
    TelephonyManager.PHONE_TYPE_GSM -> "GSM"
    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
    TelephonyManager.PHONE_TYPE_NONE -> "None"
    else -> "Other ($type)"
  }

  private fun simStateName(state: Int): String = when (state) {
    TelephonyManager.SIM_STATE_UNKNOWN -> "Unknown"
    TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
    TelephonyManager.SIM_STATE_READY -> "Ready"
    TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
    TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card IO error"
    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Card restricted"
    else -> "Other ($state)"
  }

  private fun dataActivityName(activity: Int): String = when (activity) {
    TelephonyManager.DATA_ACTIVITY_NONE -> "None"
    TelephonyManager.DATA_ACTIVITY_IN -> "Receiving"
    TelephonyManager.DATA_ACTIVITY_OUT -> "Transmitting"
    TelephonyManager.DATA_ACTIVITY_INOUT -> "Both"
    TelephonyManager.DATA_ACTIVITY_DORMANT -> "Dormant"
    else -> "Unknown"
  }

  private fun dataStateName(state: Int): String = when (state) {
    TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
    TelephonyManager.DATA_CONNECTING -> "Connecting"
    TelephonyManager.DATA_CONNECTED -> "Connected"
    TelephonyManager.DATA_SUSPENDED -> "Suspended"
    else -> "Unknown"
  }
}
