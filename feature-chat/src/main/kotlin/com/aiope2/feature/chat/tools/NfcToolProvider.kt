package com.aiope2.feature.chat.tools

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.tech.*
import android.util.Log

/**
 * NFC tool provider for reading and writing NFC tags.
 */
class NfcToolProvider(private val ctx: Context) {
  private val TAG = "NfcTool"
  private val nfcManager = ctx.getSystemService(Context.NFC_SERVICE) as? NfcManager
  private val nfcAdapter = nfcManager?.defaultAdapter

  /** Check if NFC is available and enabled */
  fun getStatus(): String {
    if (nfcAdapter == null) return "NFC not supported on this device."
    return buildString {
      appendLine("=== NFC Status ===")
      appendLine("Available: true")
      appendLine("Enabled: ${nfcAdapter.isEnabled}")
      if (Build.VERSION.SDK_INT >= 29) {
        appendLine("Secure NFC: ${nfcAdapter.isSecureNfcEnabled}")
      }
      appendLine("Observe mode: ${if (Build.VERSION.SDK_INT >= 34) nfcAdapter.isObserveModeEnabled else "N/A"}")
    }
  }

  /** Enable/disable NFC */
  fun setEnabled(enable: Boolean): String {
    return if (RootDetector.hasRootAccess()) {
      try {
        if (enable) {
          RootDetector.execAsRoot("svc nfc enable")
          "NFC enabled via root"
        } else {
          RootDetector.execAsRoot("svc nfc disable")
          "NFC disabled via root"
        }
      } catch (e: Exception) {
        "Error: ${e.message}"
      }
    } else {
      "Root access required to toggle NFC programmatically. Please toggle manually in Settings."
    }
  }

  /** List supported NFC technologies */
  fun listTechnologies(): String {
    return buildString {
      appendLine("=== NFC Technologies ===")
      val techs = listOf(
        NfcA::class.java, NfcB::class.java, NfcF::class.java, NfcV::class.java,
        IsoDep::class.java, Ndef::class.java, NdefFormatable::class.java,
        MifareClassic::class.java, MifareUltralight::class.java
      )
      techs.forEach { tech ->
        appendLine("- ${tech.simpleName}")
      }
    }
  }

  /** Information about MIFARE Classic tags */
  fun getMifareInfo(): String {
    return buildString {
      appendLine("=== MIFARE Classic ===")
      appendLine("Sectors: 16 (1K) or 64 (4K)")
      appendLine("Blocks per sector: 4 (or 16 for trailer sectors)")
      appendLine("Block size: 16 bytes")
      appendLine("Total: 1024 bytes (1K) or 4096 bytes (4K)")
      appendLine("Key A/B: 6 bytes each")
      appendLine("Use read_mifare_sector tool with sector number (0-15)")
    }
  }

  /** Build system context for NFC */
  fun buildSystemContext(): String {
    return if (nfcAdapter != null) {
      "NFC: ${if (nfcAdapter.isEnabled) "enabled" else "disabled"}"
    } else {
      "NFC: not available"
    }
  }
}
