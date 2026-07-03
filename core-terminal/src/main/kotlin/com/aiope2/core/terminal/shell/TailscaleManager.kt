package com.aiope2.core.terminal.shell

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages Tailscale VPN within proot environments.
 * Provides mesh networking and secure remote access.
 */
object TailscaleManager {
  private const val TAG = "TailscaleManager"

  data class TailscaleStatus(
    val isRunning: Boolean = false,
    val isLoggedIn: Boolean = false,
    val tailscaleIp: String = "",
    val meshIps: List<String> = emptyList(),
    val exitNode: String = "",
    val version: String = "",
  )

  /** Check if tailscale is installed in the active proot */
  fun isInstalled(ctx: Context): Boolean {
    return try {
      val result = ProotExecutor.exec(ctx, "which tailscale 2>/dev/null", timeoutMs = 5000)
      result.contains("/tailscale")
    } catch (_: Exception) { false }
  }

  /** Install tailscale in the proot environment */
  fun install(ctx: Context): String {
    return try {
      Log.i(TAG, "Installing Tailscale...")
      
      // Download and install tailscale
      val installScript = """
        cd /tmp
        curl -fsSL https://tailscale.com/install.sh -o install-tailscale.sh
        chmod +x install-tailscale.sh
        sh install-tailscale.sh
        rm install-tailscale.sh
      """.trimIndent()
      
      ProotExecutor.exec(ctx, installScript, timeoutMs = 120_000)
    } catch (e: Exception) {
      "Error installing Tailscale: ${e.message}"
    }
  }

  /** Start tailscaled daemon */
  fun startDaemon(ctx: Context): String {
    return try {
      // Start tailscaled with userspace networking (no TUN required in proot)
      ProotExecutor.exec(ctx, 
        "tailscaled --tun=userspace-networking --socks5-server=localhost:1055 --outbound-http-proxy-listen=localhost:1055 --state=/root/tailscale.state > /dev/null 2>&1 &", 
        timeoutMs = 5000
      )
      Thread.sleep(2000)
      "Tailscale daemon started"
    } catch (e: Exception) {
      "Error starting tailscaled: ${e.message}"
    }
  }

  /** Login to Tailscale */
  fun login(ctx: Context, authKey: String = ""): String {
    return try {
      val cmd = if (authKey.isNotBlank()) {
        "tailscale up --auth-key=$authKey --accept-routes --accept-dns"
      } else {
        "tailscale up --accept-routes --accept-dns"
      }
      ProotExecutor.exec(ctx, cmd, timeoutMs = 30_000)
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Get Tailscale status */
  fun getStatus(ctx: Context): TailscaleStatus {
    return try {
      val result = ProotExecutor.exec(ctx, "tailscale status 2>/dev/null || echo 'NOT_RUNNING'", timeoutMs = 10_000)
      
      if (result.contains("NOT_RUNNING") || result.contains("Logged out")) {
        return TailscaleStatus()
      }
      
      val lines = result.lines()
      val ip = try {
        ProotExecutor.exec(ctx, "tailscale ip -4 2>/dev/null || echo ''", timeoutMs = 5000).trim()
      } catch (_: Exception) { "" }
      
      TailscaleStatus(
        isRunning = true,
        isLoggedIn = !result.contains("Logged out"),
        tailscaleIp = ip,
        meshIps = lines.filter { it.contains("100.") }.map { it.substringBefore(" ").trim() },
      )
    } catch (e: Exception) {
      Log.e(TAG, "Status error: ${e.message}")
      TailscaleStatus()
    }
  }

  /** Logout from Tailscale */
  fun logout(ctx: Context): String {
    return try {
      ProotExecutor.exec(ctx, "tailscale logout", timeoutMs = 10_000)
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Stop tailscaled */
  fun stop(ctx: Context): String {
    return try {
      ProotExecutor.exec(ctx, "killall tailscaled 2>/dev/null || true", timeoutMs = 5000)
      "Tailscale stopped"
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Advertise as exit node */
  fun advertiseExitNode(ctx: Context): String {
    return try {
      ProotExecutor.exec(ctx, "tailscale up --advertise-exit-node", timeoutMs = 15_000)
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Use an exit node */
  fun useExitNode(ctx: Context, nodeIp: String): String {
    return try {
      ProotExecutor.exec(ctx, "tailscale up --exit-node=$nodeIp", timeoutMs = 15_000)
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Enable Tailscale SSH */
  fun enableSSH(ctx: Context): String {
    return try {
      ProotExecutor.exec(ctx, "tailscale up --ssh", timeoutMs = 15_000)
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Setup Tailscale in environment (used by ProotEnvironmentManager) */
  internal fun setupTailscale(ctx: Context, env: ProotEnvironmentManager.ProotEnvironment, logCb: (String) -> Unit) {
    try {
      logCb("Setting up Tailscale...")
      
      // Install tailscale
      ProotExecutor.exec(ctx, "apk add tailscale || true", timeoutMs = 60_000)
      
      // Start tailscaled
      ProotExecutor.exec(ctx, "tailscaled --tun=userspace-networking --socks5-server=localhost:1055 --outbound-http-proxy-listen=localhost:1055 &", timeoutMs = 10_000)
      
      // Login with auth key if provided
      if (env.tailscaleAuthKey.isNotBlank()) {
        ProotExecutor.exec(ctx, "tailscale up --auth-key=${env.tailscaleAuthKey} --accept-routes", timeoutMs = 30_000)
        logCb("Tailscale connected")
      } else {
        logCb("Tailscale installed. Use 'tailscale up' to connect.")
      }
      
      // Write tailscale helper script
      val tsScript = File(env.installPath, "usr/local/bin/tailscale-status")
      tsScript.writeText("#!/bin/sh\necho \"=== Tailscale Status ===\"\ntailscale status\necho \"\n=== Tailscale IP ===\"\ntailscale ip -4\n")
      tsScript.setExecutable(true)
      
    } catch (e: Exception) {
      logCb("Tailscale setup error: ${e.message}")
    }
  }

  /** Build system context */
  fun buildSystemContext(ctx: Context): String {
    val status = getStatus(ctx)
    return buildString {
      appendLine("## Tailscale VPN")
      appendLine("Running: ${status.isRunning}")
      appendLine("Logged in: ${status.isLoggedIn}")
      if (status.tailscaleIp.isNotBlank()) {
        appendLine("Tailscale IP: ${status.tailscaleIp}")
      }
      if (status.meshIps.isNotEmpty()) {
        appendLine("Mesh peers: ${status.meshIps.size}")
      }
    }
  }
}
