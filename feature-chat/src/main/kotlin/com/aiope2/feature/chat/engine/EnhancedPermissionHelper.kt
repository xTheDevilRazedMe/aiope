package com.aiope2.feature.chat.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Enhanced permission helper with Shizuku support, root fallback,
 * and adaptive permission requesting based on device capabilities.
 */
object EnhancedPermissionHelper {
  private const val TAG = "EnhancedPerm"
  private var latch: CountDownLatch? = null
  private var granted = false

  // All permissions AIOPE can request
  val ALL_PERMISSIONS = listOf(
    // Core
    android.Manifest.permission.INTERNET,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
    // Communication
    android.Manifest.permission.READ_SMS,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.READ_CONTACTS,
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR,
    // Phone
    android.Manifest.permission.READ_PHONE_STATE,
    android.Manifest.permission.CALL_PHONE,
    // Bluetooth
    android.Manifest.permission.BLUETOOTH,
    android.Manifest.permission.BLUETOOTH_ADMIN,
    // Media
    android.Manifest.permission.RECORD_AUDIO,
    // Notifications
    android.Manifest.permission.POST_NOTIFICATIONS,
    // Camera (for QR/vision)
    android.Manifest.permission.CAMERA,
  ) + if (Build.VERSION.SDK_INT >= 31) listOf(
    android.Manifest.permission.BLUETOOTH_SCAN,
    android.Manifest.permission.BLUETOOTH_CONNECT,
    android.Manifest.permission.BLUETOOTH_ADVERTISE,
  ) else emptyList() + if (Build.VERSION.SDK_INT >= 33) listOf(
    android.Manifest.permission.READ_MEDIA_IMAGES,
    android.Manifest.permission.READ_MEDIA_VIDEO,
    android.Manifest.permission.READ_MEDIA_AUDIO,
    android.Manifest.permission.NEARBY_WIFI_DEVICES,
  ) else emptyList() + if (Build.VERSION.SDK_INT >= 34) listOf(
    android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
  ) else emptyList()

  // Special permissions that need system-level granting
  val SPECIAL_PERMISSIONS = mapOf(
    "WRITE_SECURE_SETTINGS" to android.Manifest.permission.WRITE_SECURE_SETTINGS,
    "SYSTEM_ALERT_WINDOW" to android.Manifest.permission.SYSTEM_ALERT_WINDOW,
    "WRITE_SETTINGS" to android.Manifest.permission.WRITE_SETTINGS,
    "PACKAGE_USAGE_STATS" to "android.permission.PACKAGE_USAGE_STATS",
    "BIND_NOTIFICATION_LISTENER" to "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "BIND_ASSISTANT" to "android.permission.BIND_VOICE_INTERACTION",
  )

  fun hasPermission(ctx: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
  }

  fun hasShizukuPermission(): Boolean {
    return try {
      Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
    }

  /** Check + request permission with Shizuku/root fallback */
  fun ensurePermission(ctx: Context, vararg permissions: String): Boolean {
    if (permissions.all { hasPermission(ctx, it) }) return true
    
    // Try Shizuku for elevated permissions
    if (hasShizukuPermission()) {
      return grantViaShizuku(ctx, *permissions)
    }
    
    // Try root for WRITE_SECURE_SETTINGS etc.
    val needsElevated = permissions.any { it in SPECIAL_PERMISSIONS.values }
    if (needsElevated && RootDetector.hasRootAccess()) {
      return grantViaRoot(ctx, *permissions)
    }
    
    // Standard permission request
    latch = CountDownLatch(1)
    granted = false
    val intent = Intent(ctx, PermissionRequestActivity::class.java).apply {
      putExtra("permissions", permissions)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
    latch?.await(30, TimeUnit.SECONDS)
    return granted
  }

  /** Request a special permission using system settings or elevated access */
  fun ensureSpecialPermission(ctx: Context, permName: String): Boolean {
    return when (permName) {
      "WRITE_SECURE_SETTINGS" -> {
        if (hasPermission(ctx, android.Manifest.permission.WRITE_SECURE_SETTINGS)) return true
        if (RootDetector.hasRootAccess()) {
          val pkg = ctx.packageName
          RootDetector.execAsRoot("pm grant $pkg ${android.Manifest.permission.WRITE_SECURE_SETTINGS}")
          return hasPermission(ctx, android.Manifest.permission.WRITE_SECURE_SETTINGS)
        }
        false
      }
      "SYSTEM_ALERT_WINDOW" -> {
        if (Build.VERSION.SDK_INT >= 23) {
          if (Settings.canDrawOverlays(ctx)) return true
          val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          ctx.startActivity(intent)
        }
        true
      }
      "PACKAGE_USAGE_STATS" -> {
        try {
          val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
          val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName)
          } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName)
          }
          if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
          val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          ctx.startActivity(intent)
          false
        } catch (_: Exception) { false }
      }
      "NOTIFICATION_LISTENER" -> {
        val cn = android.content.ComponentName(ctx, NotificationCaptureService::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        if (flat?.contains(cn.flattenToString()) == true) return true
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        false
      }
      "IGNORE_BATTERY_OPTIMIZATIONS" -> {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return true
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        false
      }
      else -> false
    }
  }

  private fun grantViaShizuku(ctx: Context, vararg permissions: String): Boolean {
    return try {
      for (perm in permissions) {
        try {
          Shizuku.requestPermission(0)
        } catch (_: Exception) {}
      }
      permissions.all { hasPermission(ctx, it) }
    } catch (e: Exception) {
      Log.w(TAG, "Shizuku grant failed: ${e.message}")
      false
    }
  }

  private fun grantViaRoot(ctx: Context, vararg permissions: String): Boolean {
    val pkg = ctx.packageName
    for (perm in permissions) {
      RootDetector.execAsRoot("pm grant $pkg $perm 2>/dev/null || true")
    }
    return permissions.all { hasPermission(ctx, it) }
  }

  internal fun onResult(allGranted: Boolean) {
    granted = allGranted
    latch?.countDown()
  }

  /** Initialize Shizuku if available */
  fun initShizuku(ctx: Context) {
    try {
      ShizukuProvider.enableManuallyLoadedManagerProvider()
      Shizuku.addRequestPermissionResultListener { _, grantResult ->
        Log.d(TAG, "Shizuku permission result: $grantResult")
      }
    } catch (_: Exception) {
      Log.d(TAG, "Shizuku not available")
    }
  }
}

class EnhancedPermissionRequestActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val perms = intent.getStringArrayExtra("permissions") ?: run { finish(); return }
    requestPermissions(perms, 1)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    EnhancedPermissionHelper.onResult(grantResults.all { it == PackageManager.PERMISSION_GRANTED })
    finish()
  }
}