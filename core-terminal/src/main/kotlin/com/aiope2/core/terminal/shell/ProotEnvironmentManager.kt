package com.aiope2.core.terminal.shell

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages multiple proot Linux environments with distro selection,
 * custom install locations, active environment switching, and Tailscale support.
 */
object ProotEnvironmentManager {
  private const val TAG = "ProotEnvMgr"
  private const val PREFS_NAME = "proot_environments"
  private const val KEY_ENVIRONMENTS = "environments"
  private const val KEY_ACTIVE_ENV = "active_environment"

  data class ProotEnvironment(
    val id: String,
    val name: String,
    val distro: String, // alpine, ubuntu, debian, arch, fedora
    val version: String,
    val installPath: String, // absolute path
    val isDefault: Boolean = false,
    val tailscaleEnabled: Boolean = false,
    val tailscaleAuthKey: String = "",
    val packages: List<String> = emptyList(), // pre-installed packages
    val startupScript: String = "", // runs on env start
    val envVars: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("id", id)
      put("name", name)
      put("distro", distro)
      put("version", version)
      put("installPath", installPath)
      put("isDefault", isDefault)
      put("tailscaleEnabled", tailscaleEnabled)
      put("tailscaleAuthKey", tailscaleAuthKey)
      put("packages", JSONArray(packages))
      put("startupScript", startupScript)
      put("envVars", JSONObject(envVars))
      put("createdAt", createdAt)
      put("lastUsedAt", lastUsedAt)
    }

    companion object {
      fun fromJson(j: JSONObject): ProotEnvironment = ProotEnvironment(
        id = j.getString("id"),
        name = j.getString("name"),
        distro = j.getString("distro"),
        version = j.getString("version"),
        installPath = j.getString("installPath"),
        isDefault = j.optBoolean("isDefault", false),
        tailscaleEnabled = j.optBoolean("tailscaleEnabled", false),
        tailscaleAuthKey = j.optString("tailscaleAuthKey", ""),
        packages = j.optJSONArray("packages")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        startupScript = j.optString("startupScript", ""),
        envVars = j.optJSONObject("envVars")?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } } ?: emptyMap(),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        lastUsedAt = j.optLong("lastUsedAt", System.currentTimeMillis()),
      )
    }
  }

  /** Available distro definitions */
  object DistroRegistry {
    data class DistroDef(
      val id: String,
      val name: String,
      val description: String,
      val defaultVersion: String,
      val rootfsUrlTemplate: String, // {arch} and {version} placeholders
      val packageManager: String, // apk, apt, pacman, dnf
      val setupCommands: List<String> = emptyList(),
      val minimumStorageMb: Int = 200,
    )

    val ALL = listOf(
      DistroDef(
        id = "alpine",
        name = "Alpine Linux",
        description = "Lightweight, security-oriented. Best for containers and minimal setups.",
        defaultVersion = "3.21",
        rootfsUrlTemplate = "https://github.com/xnet-admin-1/box/releases/download/rootfs-alpine-{version}/box-alpine-{version}-{arch}.tar.xz",
        packageManager = "apk",
        setupCommands = listOf("apk update", "apk add bash curl wget git nano python3 py3-pip nodejs npm gcc make"),
        minimumStorageMb = 100,
      ),
      DistroDef(
        id = "ubuntu",
        name = "Ubuntu",
        description = "Full-featured Debian-based distro. Largest compatibility.",
        defaultVersion = "24.04",
        rootfsUrlTemplate = "https://cdimage.ubuntu.com/ubuntu-base/releases/{version}/release/ubuntu-base-{version}-base-{arch}.tar.gz",
        packageManager = "apt",
        setupCommands = listOf(
          "apt update",
          "apt install -y bash curl wget git nano python3 python3-pip nodejs npm build-essential"
        ),
        minimumStorageMb = 500,
      ),
      DistroDef(
        id = "debian",
        name = "Debian",
        description = "Stable, conservative. Rock-solid for servers.",
        defaultVersion = "12",
        rootfsUrlTemplate = "https://github.com/xnet-admin-1/box/releases/download/rootfs-debian-{version}/box-debian-{version}-{arch}.tar.xz",
        packageManager = "apt",
        setupCommands = listOf(
          "apt update", 
          "apt install -y bash curl wget git nano python3 python3-pip nodejs npm build-essential"
        ),
        minimumStorageMb = 400,
      ),
      DistroDef(
        id = "arch",
        name = "Arch Linux",
        description = "Rolling release, bleeding edge. For advanced users.",
        defaultVersion = "latest",
        rootfsUrlTemplate = "https://github.com/xnet-admin-1/box/releases/download/rootfs-arch-{version}/box-arch-{version}-{arch}.tar.xz",
        packageManager = "pacman",
        setupCommands = listOf("pacman-key --init", "pacman -Sy --noconfirm base-devel git nano python python-pip nodejs npm"),
        minimumStorageMb = 400,
      ),
    )

    fun get(id: String): DistroDef? = ALL.find { it.id == id }
    fun getDefault(): DistroDef = ALL.first()
  }

  private fun getPrefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /** Get all configured environments */
  fun getEnvironments(ctx: Context): List<ProotEnvironment> {
    val json = getPrefs(ctx).getString(KEY_ENVIRONMENTS, "[]") ?: "[]"
    return try {
      val arr = JSONArray(json)
      (0 until arr.length()).map { fromJsonOrNull(arr.getJSONObject(it)) }.filterNotNull()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse environments", e)
      emptyList()
    }
  }

  /** Get the currently active environment */
  fun getActiveEnvironment(ctx: Context): ProotEnvironment? {
    val activeId = getPrefs(ctx).getString(KEY_ACTIVE_ENV, "") ?: ""
    if (activeId.isBlank()) {
      // Return default or first environment
      val envs = getEnvironments(ctx)
      return envs.firstOrNull { it.isDefault } ?: envs.firstOrNull()
    }
    return getEnvironments(ctx).find { it.id == activeId }
  }

  /** Set the active environment */
  fun setActiveEnvironment(ctx: Context, envId: String) {
    getPrefs(ctx).edit { putString(KEY_ACTIVE_ENV, envId) }
    Log.i(TAG, "Active environment set to: $envId")
  }

  /** Get the rootfs directory for the active or specified environment */
  fun getRootfsDir(ctx: Context, envId: String? = null): File {
    val env = if (envId != null) {
      getEnvironments(ctx).find { it.id == envId }
    } else {
      getActiveEnvironment(ctx)
    }
    return if (env != null) {
      File(env.installPath)
    } else {
      ProotBootstrap.rootfsDir(ctx)
    }
  }

  /** Get the env directory for the active or specified environment */
  fun getEnvDir(ctx: Context, envId: String? = null): File {
    return getRootfsDir(ctx, envId).parentFile ?: File(ctx.filesDir, "env")
  }

  /** Create a new environment configuration (does not download yet) */
  fun createEnvironment(
    ctx: Context,
    name: String,
    distro: String,
    version: String = "",
    installPath: String = "",
    tailscaleEnabled: Boolean = false,
    packages: List<String> = emptyList(),
    startupScript: String = "",
    envVars: Map<String, String> = emptyMap(),
  ): ProotEnvironment {
    val distroDef = DistroRegistry.get(distro) ?: DistroRegistry.getDefault()
    val resolvedVersion = version.ifBlank { distroDef.defaultVersion }
    val resolvedPath = installPath.ifBlank {
      File(ctx.filesDir, "env/$distro-${System.currentTimeMillis()}").absolutePath
    }
    
    val env = ProotEnvironment(
      id = java.util.UUID.randomUUID().toString().take(8),
      name = name,
      distro = distro,
      version = resolvedVersion,
      installPath = resolvedPath,
      tailscaleEnabled = tailscaleEnabled,
      packages = packages,
      startupScript = startupScript,
      envVars = envVars,
    )
    
    saveEnvironment(ctx, env)
    return env
  }

  /** Save/update an environment */
  fun saveEnvironment(ctx: Context, env: ProotEnvironment) {
    val envs = getEnvironments(ctx).toMutableList()
    val idx = envs.indexOfFirst { it.id == env.id }
    if (idx >= 0) {
      envs[idx] = env
    } else {
      envs.add(env)
    }
    saveEnvironments(ctx, envs)
  }

  /** Remove an environment (optionally delete files) */
  fun removeEnvironment(ctx: Context, envId: String, deleteFiles: Boolean = true) {
    val envs = getEnvironments(ctx).toMutableList()
    val env = envs.find { it.id == envId } ?: return
    if (deleteFiles) {
      File(env.installPath).deleteRecursively()
    }
    envs.removeAll { it.id == envId }
    saveEnvironments(ctx, envs)
    
    // If we removed the active env, clear it
    if (getPrefs(ctx).getString(KEY_ACTIVE_ENV, "") == envId) {
      getPrefs(ctx).edit { remove(KEY_ACTIVE_ENV) }
    }
  }

  /** Install/setup an environment (downloads and configures rootfs) */
  suspend fun installEnvironment(ctx: Context, env: ProotEnvironment, logCb: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
      val distroDef = DistroRegistry.get(env.distro) ?: return@withContext false
      val rootfs = File(env.installPath)
      rootfs.mkdirs()
      
      logCb("Setting up ${distroDef.name} ${env.version}...")
      
      // Check if already installed
      if (isEnvironmentInstalled(env)) {
        logCb("Environment already installed at ${env.installPath}")
        return@withContext true
      }
      
      // For now, use the existing bootstrap for Alpine
      // For other distros, we'd download their rootfs
      if (env.distro == "alpine") {
        val success = ProotBootstrap.setup(ctx) { msg ->
          logCb(msg)
        }
        if (success) {
          // Apply environment-specific configuration
          configureEnvironment(ctx, env, logCb)
        }
        return@withContext success
      } else {
        logCb("Distro ${env.distro} requires manual rootfs download")
        logCb("Place rootfs at: ${env.installPath}")
        return@withContext false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Install failed", e)
      logCb("Error: ${e.message}")
      false
    }
  }

  /** Check if environment files exist */
  fun isEnvironmentInstalled(env: ProotEnvironment): Boolean {
    val rootfs = File(env.installPath)
    return rootfs.isDirectory && (File(rootfs, "bin/sh").exists() || File(rootfs, "bin/busybox").exists())
  }

  /** Apply environment-specific configuration */
  private fun configureEnvironment(ctx: Context, env: ProotEnvironment, logCb: (String) -> Unit) {
    try {
      // Write startup script
      if (env.startupScript.isNotBlank()) {
        val rcFile = File(env.installPath, "root/.proot_startup.sh")
        rcFile.writeText(env.startupScript)
        rcFile.setExecutable(true)
        logCb("Startup script written")
      }
      
      // Write environment variables
      if (env.envVars.isNotEmpty()) {
        val profileFile = File(env.installPath, "root/.profile")
        val envExports = env.envVars.entries.joinToString("\n") { "export ${it.key}=\"${it.value}\"" }
        if (profileFile.exists()) {
          profileFile.appendText("\n# AIOPE environment vars\n$envExports\n")
        }
        logCb("Environment variables configured")
      }
      
      // Install requested packages
      val distroDef = DistroRegistry.get(env.distro)
      if (distroDef != null && env.packages.isNotEmpty()) {
        logCb("Installing packages: ${env.packages.joinToString(", ")}")
        val pkgCmd = when (distroDef.packageManager) {
          "apk" -> "apk add ${env.packages.joinToString(" ")}"
          "apt" -> "apt install -y ${env.packages.joinToString(" ")}"
          "pacman" -> "pacman -Sy --noconfirm ${env.packages.joinToString(" ")}"
          else -> null
        }
        if (pkgCmd != null) {
          ProotExecutor.exec(ctx, pkgCmd, timeoutMs = 300_000)
        }
      }
      
      // Setup Tailscale if enabled
      if (env.tailscaleEnabled) {
        TailscaleManager.setupTailscale(ctx, env, logCb)
      }
      
      logCb("Environment configured: ${env.name}")
    } catch (e: Exception) {
      Log.e(TAG, "Configuration failed", e)
      logCb("Config error: ${e.message}")
    }
  }

  /** Switch to a different environment */
  fun switchEnvironment(ctx: Context, envId: String): Boolean {
    val env = getEnvironments(ctx).find { it.id == envId } ?: return false
    if (!isEnvironmentInstalled(env)) {
      Log.w(TAG, "Environment not installed: ${env.name}")
      return false
    }
    setActiveEnvironment(ctx, envId)
    Log.i(TAG, "Switched to environment: ${env.name} (${env.distro})")
    return true
  }

  /** Get environment info for system prompt */
  fun buildSystemContext(ctx: Context): String {
    val envs = getEnvironments(ctx)
    val active = getActiveEnvironment(ctx)
    return buildString {
      appendLine("## Proot Environments")
      appendLine("Active: ${active?.name ?: "None"} (${active?.distro ?: "N/A"})")
      appendLine("Total environments: ${envs.size}")
      envs.forEach { env ->
        val installed = if (isEnvironmentInstalled(env)) "installed" else "not installed"
        appendLine("- ${env.name}: ${env.distro} ${env.version} [$installed]")
      }
    }
  }

  private fun saveEnvironments(ctx: Context, envs: List<ProotEnvironment>) {
    val arr = JSONArray()
    envs.forEach { arr.put(it.toJson()) }
    getPrefs(ctx).edit { putString(KEY_ENVIRONMENTS, arr.toString()) }
  }

  private fun fromJsonOrNull(j: JSONObject): ProotEnvironment? = try {
    ProotEnvironment.fromJson(j)
  } catch (_: Exception) { null }
}
