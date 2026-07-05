package ngo.xnet.aiope.feature.chat.introspection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.aiope2.core.terminal.shell.ProotEnvironmentManager
import com.aiope2.feature.chat.engine.RootDetector
import com.aiope2.feature.chat.plugins.PluginManager
import com.aiope2.feature.chat.skills.SkillManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern

/**
 * Provides the AI with full awareness of the AIOPE app, its state,
 * settings, tools, and environment. Enables crash prediction and prevention.
 */
class AppIntrospector(private val ctx: Context) {
  private val TAG = "AppIntrospector"

  data class AppState(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val memoryUsage: MemoryInfo,
    val threadCount: Int,
    val cpuUsage: Double,
    val storageUsage: StorageInfo,
    val permissions: List<String>,
    val components: ComponentInfo,
    val runningServices: List<String>,
    val prootStatus: ProotStatus,
    val deviceCapabilities: DeviceCapabilities,
  )

  data class MemoryInfo(
    val javaHeapUsed: Long,
    val javaHeapMax: Long,
    val nativeHeapUsed: Long,
    val totalPss: Long,
    val totalPrivateDirty: Long,
  )

  data class StorageInfo(
    val appSize: Long,
    val dataSize: Long,
    val cacheSize: Long,
    val externalSize: Long,
  )

  data class ComponentInfo(
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val providerCount: Int,
  )

  data class ProotStatus(
    val isInstalled: Boolean,
    val activeEnvironment: String,
    val environmentCount: Int,
  )

  data class DeviceCapabilities(
    val hasBluetooth: Boolean,
    val hasNfc: Boolean,
    val hasGps: Boolean,
    val hasCamera: Boolean,
    val hasFingerprint: Boolean,
    val hasBiometric: Boolean,
    val hasTelephony: Boolean,
    val hasWifi: Boolean,
    val hasEthernet: Boolean,
    val abiList: List<String>,
  )

  /** Get complete app state */
  fun getAppState(): AppState {
    val pm = ctx.packageManager
    val pkgInfo = pm.getPackageInfo(ctx.packageName, 0)
    
    return AppState(
      packageName = ctx.packageName,
      versionName = pkgInfo.versionName ?: "unknown",
      versionCode = if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong(),
      memoryUsage = getMemoryInfo(),
      threadCount = getThreadCount(),
      cpuUsage = getCpuUsage(),
      storageUsage = getStorageInfo(),
      permissions = getGrantedPermissions(),
      components = getComponentInfo(),
      runningServices = getRunningServices(),
      prootStatus = getProotStatus(),
      deviceCapabilities = getDeviceCapabilities(),
    )
  }

  /** Get memory usage info */
  private fun getMemoryInfo(): MemoryInfo {
    val runtime = Runtime.getRuntime()
    val debugInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(debugInfo)
    
    return MemoryInfo(
      javaHeapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
      javaHeapMax = runtime.maxMemory() / 1024 / 1024,
      nativeHeapUsed = debugInfo.nativePss.toLong(),
      totalPss = debugInfo.totalPss.toLong(),
      totalPrivateDirty = debugInfo.totalPrivateDirty.toLong(),
    )
  }

  /** Get thread count */
  private fun getThreadCount(): Int {
    return Process.myTid().let { tid ->
      File("/proc/self/task").listFiles()?.size ?: 0
    }
  }

  /** Get CPU usage percentage */
  private fun getCpuUsage(): Double {
    return try {
      val pid = Process.myPid()
      val stat = File("/proc/$pid/stat").readText()
      val parts = stat.split(" ")
      if (parts.size > 13) {
        val utime = parts[13].toLongOrNull() ?: 0
        val stime = parts[14].toLongOrNull() ?: 0
        val totalTime = utime + stime
        totalTime.toDouble() // Simplified - would need clock ticks for percentage
      } else 0.0
    } catch (e: Exception) { 0.0 }
  }

  /** Get storage usage */
  private fun getStorageInfo(): StorageInfo {
    val dataDir = ctx.filesDir.parentFile
    val cacheDir = ctx.cacheDir
    val externalDir = ctx.getExternalFilesDir(null)
    
    return StorageInfo(
      appSize = getFolderSize(File(dataDir, "app")),
      dataSize = getFolderSize(dataDir),
      cacheSize = getFolderSize(cacheDir),
      externalSize = externalDir?.let { getFolderSize(it) } ?: 0,
    )
  }

  private fun getFolderSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
  }

  /** Get granted permissions */
  private fun getGrantedPermissions(): List<String> {
    return try {
      val pm = ctx.packageManager
      val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
      }
      
      val permissions = pkgInfo.requestedPermissions ?: emptyArray()
      val states = pkgInfo.requestedPermissionsFlags ?: intArrayOf()
      
      permissions.filterIndexed { idx, _ ->
        idx < states.size && (states[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
      }.toList()
    } catch (e: Exception) { emptyList() }
  }

  /** Get component counts */
  private fun getComponentInfo(): ComponentInfo {
    val pm = ctx.packageManager
    return try {
      val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(
          (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or 
           PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS).toLong()
        ))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(ctx.packageName, 
          PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or 
          PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        )
      }
      
      ComponentInfo(
        activityCount = pkgInfo.activities?.size ?: 0,
        serviceCount = pkgInfo.services?.size ?: 0,
        receiverCount = pkgInfo.receivers?.size ?: 0,
        providerCount = pkgInfo.providers?.size ?: 0,
      )
    } catch (e: Exception) {
      ComponentInfo(0, 0, 0, 0)
    }
  }

  /** Get running services */
  private fun getRunningServices(): List<String> {
    return try {
      val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
      @Suppress("DEPRECATION")
      am.getRunningServices(50).map { it.service.className }
    } catch (e: Exception) { emptyList() }
  }

  /** Get proot status */
  private fun getProotStatus(): ProotStatus {
    val envs = ProotEnvironmentManager.getEnvironments(ctx)
    val active = ProotEnvironmentManager.getActiveEnvironment(ctx)
    return ProotStatus(
      isInstalled = ProotEnvironmentManager.isEnvironmentInstalled(
        active ?: ProotEnvironmentManager.DistroRegistry.getDefault().let { 
        ProotEnvironmentManager.ProotEnvironment(
          id = "default", name = "default", distro = "alpine", version = "3.21",
          installPath = ProotEnvironmentManager.getRootfsDir(ctx).absolutePath
        )
      }),
      activeEnvironment = active?.name ?: "none",
      environmentCount = envs.size,
    )
  }

  /** Get device capabilities */
  private fun getDeviceCapabilities(): DeviceCapabilities {
    val pm = ctx.packageManager
    return DeviceCapabilities(
      hasBluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
      hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC),
      hasGps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
      hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA),
      hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
      hasBiometric = if (Build.VERSION.SDK_INT >= 29) pm.hasSystemFeature(PackageManager.FEATURE_FACE) || pm.hasSystemFeature(PackageManager.FEATURE_IRIS) || pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) else false,
      hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
      hasWifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI),
      hasEthernet = pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET),
      abiList = Build.SUPPORTED_ABIS?.toList() ?: listOf(Build.CPU_ABI),
    )
  }

  /** Predict potential crashes based on current state */
  fun predictIssues(): List<PredictedIssue> {
    val issues = mutableListOf<PredictedIssue>()
    val state = getAppState()
    
    // Memory pressure
    val memoryRatio = state.memoryUsage.javaHeapUsed.toDouble() / state.memoryUsage.javaHeapMax
    if (memoryRatio > 0.85) {
      issues.add(PredictedIssue(
        severity = PredictedIssue.Severity.HIGH,
        component = "Memory",
        description = "Heap usage at ${(memoryRatio * 100).toInt()}%. Risk of OutOfMemoryError.",
        suggestion = "Trigger garbage collection or reduce cache sizes.",
      ))
    } else if (memoryRatio > 0.7) {
      issues.add(PredictedIssue(
        severity = PredictedIssue.Severity.MEDIUM,
        component = "Memory",
        description = "Heap usage at ${(memoryRatio * 100).toInt()}%. Monitor closely.",
        suggestion = "Consider clearing caches if usage continues to rise.",
      ))
    }
    
    // Thread count
    if (state.threadCount > 100) {
      issues.add(PredictedIssue(
        severity = PredictedIssue.Severity.MEDIUM,
        component = "Threads",
        description = "Thread count (${state.threadCount}) is high.",
        suggestion = "Check for thread leaks in network operations.",
      ))
    }
    
    // Storage
    val totalStorage = state.storageUsage.dataSize + state.storageUsage.cacheSize
    if (totalStorage > 500 * 1024 * 1024) { // 500MB
      issues.add(PredictedIssue(
        severity = PredictedIssue.Severity.LOW,
        component = "Storage",
        description = "App using ${totalStorage / 1024 / 1024}MB of storage.",
        suggestion = "Consider clearing cache or old files.",
      ))
    }
    
    // Proot check
    if (state.prootStatus.isInstalled && state.prootStatus.environmentCount == 0) {
      issues.add(PredictedIssue(
        severity = PredictedIssue.Severity.LOW,
        component = "Proot",
        description = "Proot installed but no environments configured.",
        suggestion = "Create a proot environment in Settings.",
      ))
    }
    
    return issues
  }

  data class PredictedIssue(
    val severity: Severity,
    val component: String,
    val description: String,
    val suggestion: String,
  ) {
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
  }

  /** Build comprehensive system context for AI */
  fun buildFullContext(
    pluginManager: PluginManager? = null,
    skillManager: SkillManager? = null,
  ): String {
    val state = getAppState()
    val issues = predictIssues()
    val privilegeStatus = RootDetector.detect(ctx)
    
    return buildString {
      appendLine("## AIOPE Self-Awareness Context")
      appendLine()
      appendLine("### App State")
      appendLine("- Package: ${state.packageName}")
      appendLine("- Version: ${state.versionName} (${state.versionCode})")
      appendLine("- Memory: ${state.memoryUsage.javaHeapUsed}MB / ${state.memoryUsage.javaHeapMax}MB heap, ${state.memoryUsage.totalPss}MB PSS")
      appendLine("- Threads: ${state.threadCount}")
      appendLine("- Components: ${state.components.activityCount} activities, ${state.components.serviceCount} services, ${state.components.receiverCount} receivers, ${state.components.providerCount} providers")
      
      appendLine()
      appendLine("### Privilege Level")
      appendLine("- Level: ${privilegeStatus.level.name}")
      appendLine("- Shizuku: ${if (privilegeStatus.shizukuVersion > 0) "v${privilegeStatus.shizukuVersion}" else "not available"}")
      appendLine("- Magisk: ${if (privilegeStatus.magiskInstalled) "v${privilegeStatus.magiskVersion}" else "not detected"}")
      appendLine("- Secure Settings: ${privilegeStatus.canWriteSecureSettings}")
      
      appendLine()
      appendLine("### Proot Environment")
      appendLine("- Installed: ${state.prootStatus.isInstalled}")
      appendLine("- Active: ${state.prootStatus.activeEnvironment}")
      appendLine("- Environments: ${state.prootStatus.environmentCount}")
      
      appendLine()
      appendLine("### Device Capabilities")
      appendLine("- Model: ${Build.MANUFACTURER} ${Build.MODEL}")
      appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
      appendLine("- ABI: ${state.deviceCapabilities.abiList.joinToString()}")
      appendLine("- Bluetooth: ${state.deviceCapabilities.hasBluetooth}")
      appendLine("- NFC: ${state.deviceCapabilities.hasNfc}")
      appendLine("- GPS: ${state.deviceCapabilities.hasGps}")
      appendLine("- Telephony: ${state.deviceCapabilities.hasTelephony}")
      
      // Plugins
      pluginManager?.let { pm ->
        val plugins = pm.getEnabledPlugins()
        if (plugins.isNotEmpty()) {
          appendLine()
          appendLine("### Active Plugins (${plugins.size})")
          plugins.forEach { p ->
            appendLine("- ${p.name} v${p.version}: ${p.description}")
          }
        }
      }
      
      // Skills
      skillManager?.let { sm ->
        val skills = sm.getEnabledSkills()
        if (skills.isNotEmpty()) {
          appendLine()
          appendLine("### Available Skills (${skills.size})")
          skills.forEach { s ->
            appendLine("- ${s.name} (${s.category}): ${s.description}")
          }
        }
      }
      
      // Predicted issues
      if (issues.isNotEmpty()) {
        appendLine()
        appendLine("### Predicted Issues (${issues.size})")
        issues.forEach { issue ->
          val icon = when (issue.severity) {
            PredictedIssue.Severity.LOW -> "O"
            PredictedIssue.Severity.MEDIUM -> "!"
            PredictedIssue.Severity.HIGH -> "X"
            PredictedIssue.Severity.CRITICAL -> "!!"
          }
          appendLine("[$icon] **[${issue.severity.name}] ${issue.component}**: ${issue.description}")
          appendLine("   Suggestion: ${issue.suggestion}")
        }
      }
    }
  }
}
