package ngo.xnet.aiope.feature.chat.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Detects root access, Shizuku availability, and Magisk installation.
 * Adapts AIOPE behavior based on privilege level.
 */
object RootDetector {
  private const val TAG = "RootDetector"

  enum class PrivilegeLevel {
    NONE,           // No special access
    SHIZUKU,        // Shizuku service available
    ROOT,           // Full root access
  }

  data class PrivilegeStatus(
    val level: PrivilegeLevel,
    val shizukuVersion: Int = -1,
    val magiskInstalled: Boolean = false,
    val magiskVersion: String = "",
    val canWriteSecureSettings: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val canBindAssistant: Boolean = false,
    val canReadPhoneState: Boolean = false,
    val canBluetooth: Boolean = false,
    val canNfc: Boolean = false,
    val canUsageStats: Boolean = false,
    val canNotificationListener: Boolean = false,
  )

  private var cachedStatus: PrivilegeStatus? = null

  @Synchronized
  fun detect(ctx: Context): PrivilegeStatus {
    cachedStatus?.let { return it }
    
    val hasRoot = checkRootAccess()
    val shizukuInfo = checkShizuku(ctx)
    val magiskInfo = checkMagisk()
    
    val level = when {
      hasRoot -> PrivilegeLevel.ROOT
      shizukuInfo.first -> PrivilegeLevel.SHIZUKU
      else -> PrivilegeLevel.NONE
    }
    
    val status = PrivilegeStatus(
      level = level,
      shizukuVersion = shizukuInfo.second,
      magiskInstalled = magiskInfo.first,
      magiskVersion = magiskInfo.second,
      canWriteSecureSettings = canWriteSecureSettings(ctx),
      canDrawOverlays = canDrawOverlays(ctx),
      canBindAssistant = canBindAssistant(ctx),
      canReadPhoneState = hasPermission(ctx, android.Manifest.permission.READ_PHONE_STATE),
      canBluetooth = if (Build.VERSION.SDK_INT >= 31) {
        hasPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        hasPermission(ctx, android.Manifest.permission.BLUETOOTH)
      },
      canNfc = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
      canUsageStats = canUsageStats(ctx),
      canNotificationListener = canNotificationListener(ctx),
    )
    
    cachedStatus = status
    Log.i(TAG, "Privilege level: ${level.name}, Shizuku: ${shizukuInfo.first}, Root: $hasRoot, Magisk: ${magiskInfo.first}")
    return status
  }

  fun invalidateCache() {
    cachedStatus = null
  }

  fun hasRootAccess(): Boolean = try {
    Runtime.getRuntime().exec("su -c id").let { proc ->
      proc.waitFor(3, TimeUnit.SECONDS) && proc.exitValue() == 0
    }
  } catch (_: Exception) { false }

  fun checkRootAccess(): Boolean = hasRootAccess()

  private fun checkShizuku(ctx: Context): Pair<Boolean, Int> {
    return try {
      // Check if Shizuku is installed
      ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
      // Try to bind to Shizuku service
      val clazz = Class.forName("rikka.shizuku.Shizuku")
      val binder = clazz.getMethod("getBinder").invoke(null)
      val isAlive = clazz.getMethod("isPreV11")
        ?.let { it.invoke(null) as? Boolean } ?: false
      val version = try {
        clazz.getMethod("getVersion").invoke(null) as? Int ?: -1
      } catch (_: Exception) { -1 }
      Pair(binder != null || isAlive, version)
    } catch (_: Exception) {
      Pair(false, -1)
    }
  }

  private fun checkMagisk(): Pair<Boolean, String> {
    val magiskIndicators = listOf(
      "/sbin/.magisk",
      "/dev/.magisk.unblock",
      "/system/bin/magisk",
      "/system/xbin/magisk",
      "/data/adb/magisk",
      "/data/adb/ksu",
      "/data/adb/ap",
    )
    val found = magiskIndicators.any { File(it).exists() }
    val version = if (found) {
      try {
        val proc = Runtime.getRuntime().exec("magisk -V")
        proc.inputStream.bufferedReader().readText().trim()
      } catch (_: Exception) { "unknown" }
    } else ""
    return Pair(found, version)
  }

  private fun canWriteSecureSettings(ctx: Context): Boolean {
    return try {
      ctx.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
  }

  private fun canDrawOverlays(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 23) {
      android.provider.Settings.canDrawOverlays(ctx)
    } else true
  }

  private fun canBindAssistant(ctx: Context): Boolean {
    return ctx.packageManager.resolveService(
      android.content.Intent(android.service.voice.VoiceInteractionService.SERVICE_INTERFACE),
      PackageManager.MATCH_ALL
    ) != null
  }

  private fun canUsageStats(ctx: Context): Boolean {
    return try {
      val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
      val mode = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName)
      } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName)
      }
      mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
  }

  private fun canNotificationListener(ctx: Context): Boolean {
    val cn = android.content.ComponentName(ctx, NotificationCaptureService::class.java)
    val flat = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
    return flat?.contains(cn.flattenToString()) == true
  }

  private fun hasPermission(ctx: Context, perm: String): Boolean {
    return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
  }

  /** Execute command with highest available privilege */
  fun execWithPrivilege(cmd: String): String {
    return when (detectLevel()) {
      PrivilegeLevel.ROOT -> execAsRoot(cmd)
      PrivilegeLevel.SHIZUKU -> execViaShizuku(cmd)
      PrivilegeLevel.NONE -> "Error: No elevated privileges available"
    }
  }

  private fun detectLevel(): PrivilegeLevel {
    return if (hasRootAccess()) PrivilegeLevel.ROOT
    else PrivilegeLevel.NONE // Shizuku check needs context
  }

  fun execAsRoot(cmd: String): String {
    return try {
      val proc = Runtime.getRuntime().exec("su -c $cmd")
      val output = proc.inputStream.bufferedReader().readText()
      val error = proc.errorStream.bufferedReader().readText()
      proc.waitFor(30, TimeUnit.SECONDS)
      if (output.isNotBlank()) output else error
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  private fun execViaShizuku(cmd: String): String {
    return try {
      // Use Shizuku remote process execution
      val clazz = Class.forName("rikka.shizuku.Shizuku")
      val newProcess = clazz.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
      val process = newProcess.invoke(null, arrayOf("/system/bin/sh", "-c", cmd), null, null) as? Process
      process?.inputStream?.bufferedReader()?.readText() ?: "Shizuku process failed"
    } catch (e: Exception) { "Shizuku error: ${e.message}" }
  }
}

/**
 * Notification listener service for capturing notifications.
 * Must be enabled in system settings.
 */
class NotificationCaptureService : android.service.notification.NotificationListenerService() {
  companion object {
    val activeNotifications = mutableListOf<NotificationInfo>()
  }
  
  data class NotificationInfo(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
  )
  
  override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
    val info = NotificationInfo(
      packageName = sbn.packageName,
      title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: "",
      text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "",
    )
    synchronized(activeNotifications) {
      activeNotifications.add(0, info)
      if (activeNotifications.size > 100) activeNotifications.removeAt(activeNotifications.size - 1)
    }
  }
  
  override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {}
}