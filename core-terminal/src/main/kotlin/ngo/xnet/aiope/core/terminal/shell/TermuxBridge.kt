package ngo.xnet.aiope.core.terminal.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Bridges AIOPE with the Termux app for enhanced terminal capabilities.
 * Allows running commands in Termux's native Linux environment.
 */
object TermuxBridge {
  private const val TAG = "TermuxBridge"
  private const val TERMUX_PACKAGE = "com.termux"
  private const val TERMUX_API_PACKAGE = "com.termux.api"
  
  /** Check if Termux is installed */
  fun isTermuxInstalled(ctx: Context): Boolean {
    return try {
      ctx.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
      true
    } catch (_: Exception) { false }
  }

  /** Check if Termux:API is installed */
  fun isTermuxApiInstalled(ctx: Context): Boolean {
    return try {
      ctx.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
      true
    } catch (_: Exception) { false }
  }

  /** Launch Termux app */
  fun launchTermux(ctx: Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
    if (intent != null) {
      intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.startActivity(intent)
    }
  }

  /** Run a command in Termux via am start */
  fun runInTermux(ctx: Context, command: String, workingDir: String = "~") {
    val intent = Intent("${TERMUX_PACKAGE}.RUN_COMMAND").apply {
      setPackage(TERMUX_PACKAGE)
      putExtra("${TERMUX_PACKAGE}.RUN_COMMAND_PATH", "/data/data/${TERMUX_PACKAGE}/files/usr/bin/bash")
      putExtra("${TERMUX_PACKAGE}.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
      putExtra("${TERMUX_PACKAGE}.RUN_COMMAND_WORKDIR", workingDir)
      putExtra("${TERMUX_PACKAGE}.RUN_COMMAND_BACKGROUND", true)
      putExtra("${TERMUX_PACKAGE}.RUN_COMMAND_SESSION_ACTION", "0") // don't switch to session
    }
    try {
      ctx.sendBroadcast(intent)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to run command in Termux: ${e.message}")
    }
  }

  /** Execute command and get result via Termux */
  fun exec(ctx: Context, command: String): String {
    if (!isTermuxInstalled(ctx)) {
      return "Error: Termux not installed. Install from F-Droid."
    }
    
    return try {
      // Use am to start a command and capture output
      val resultFile = File(ctx.cacheDir, "termux_result_${System.currentTimeMillis()}.txt")
      val wrappedCmd = "$command > ${resultFile.absolutePath} 2>&1; echo EXIT_CODE=$\? >> ${resultFile.absolutePath}"
      
      runInTermux(ctx, wrappedCmd)
      
      // Wait for result
      var attempts = 0
      while (!resultFile.exists() && attempts < 50) {
        Thread.sleep(200)
        attempts++
      }
      
      if (resultFile.exists()) {
        val content = resultFile.readText()
        resultFile.delete()
        content
      } else {
        "Command sent to Termux (async - no output captured)"
      }
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  /** Get Termux home directory path */
  fun getTermuxHome(): String = "/data/data/${TERMUX_PACKAGE}/files/home"

  /** Get Termux usr/bin path */
  fun getTermuxBin(): String = "/data/data/${TERMUX_PACKAGE}/files/usr/bin"

  /** Open Termux with a specific working directory */
  fun openTermuxAt(ctx: Context, path: String) {
    val intent = Intent("${TERMUX_PACKAGE}.OPEN_DIR").apply {
      setPackage(TERMUX_PACKAGE)
      putExtra("dir", path)
    }
    try {
      ctx.startActivity(intent)
    } catch (e: Exception) {
      // Fallback: open Termux normally
      launchTermux(ctx)
    }
  }

  /** Install a package via Termux pkg */
  fun installPackage(ctx: Context, packageName: String) {
    runInTermux(ctx, "pkg install -y $packageName")
  }

  /** Setup SSH access to Termux */
  fun setupSsh(ctx: Context): String {
    return try {
      runInTermux(ctx, "pkg install -y openssh")
      Thread.sleep(5000)
      runInTermux(ctx, "sshd")
      "SSH server started in Termux on port 8022"
    } catch (e: Exception) {
      "Error setting up SSH: ${e.message}"
    }
  }

  /** Build system context for AI awareness */
  fun buildSystemContext(ctx: Context): String {
    return buildString {
      appendLine("## Termux Integration")
      appendLine("Installed: ${isTermuxInstalled(ctx)}")
      appendLine("API installed: ${isTermuxApiInstalled(ctx)}")
      if (isTermuxInstalled(ctx)) {
        appendLine("Home: ${getTermuxHome()}")
        appendLine("Bin: ${getTermuxBin()}")
      }
    }
  }
}
