package ngo.xnet.aiope.feature.chat.gateway

import android.content.Context
import android.util.Log
import com.aiope2.core.terminal.shell.ProotExecutor
import kotlinx.coroutines.*
import java.io.File
import java.net.ServerSocket

/**
 * Embedded AIOPE Gateway that runs inside the Android app.
 * Hosts a local LLM proxy server within the proot environment.
 * Prevents Android from killing it via foreground service + wake lock.
 */
class EmbeddedGateway(private val ctx: Context) {
  private val TAG = "EmbeddedGateway"
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var gatewayJob: Job? = null
  private var isRunning = false
  
  data class GatewayConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val providers: List<ProviderConfig> = emptyList(),
    val enableAdmin: Boolean = true,
    val adminPort: Int = 8081,
    val logLevel: String = "info",
    val rateLimit: Int = 100, // requests per minute
  ) {
    data class ProviderConfig(
      val name: String,
      val type: String, // "google-ai-studio", "openai", "anthropic", "ollama"
      val apiKey: String,
      val baseUrl: String = "",
      val enabled: Boolean = true,
    )
  }
  
  data class GatewayStatus(
    val isRunning: Boolean,
    val pid: String = "",
    val uptime: Long = 0,
    val port: Int = 0,
    val requestsHandled: Int = 0,
    val errors: Int = 0,
  )

  /** Check if the embedded gateway is available */
  fun isAvailable(): Boolean {
    // Check if node/python is available in proot
    val nodeCheck = ProotExecutor.exec(ctx, "which node 2>/dev/null || which python3 2>/dev/null || echo 'NONE'", timeoutMs = 5000)
    return !nodeCheck.contains("NONE")
  }

  /** Start the embedded gateway */
  fun start(config: GatewayConfig = GatewayConfig()): String {
    if (isRunning) return "Gateway already running"
    
    return try {
      // Create gateway directory
      val gwDir = File(ctx.filesDir, "gateway")
      gwDir.mkdirs()
      
      // Write gateway configuration
      writeGatewayConfig(gwDir, config)
      
      // Write the gateway server script
      writeGatewayScript(gwDir)
      
      // Start in proot with nohup
      scope.launch {
        try {
          isRunning = true
          ProotExecutor.exec(
            ctx, 
            "cd ${gwDir.absolutePath} && nohup node gateway.js > gateway.log 2>&1 &\necho $! > gateway.pid",
            timeoutMs = 10000
          )
          
          // Wait for it to start
          delay(3000)
          
          // Verify it's running
          val pid = ProotExecutor.exec(ctx, "cat ${gwDir.absolutePath}/gateway.pid 2>/dev/null || echo 'NONE'")
          if (pid != "NONE" && pid.isNotBlank()) {
            Log.i(TAG, "Gateway started with PID: $pid")
          }
        } catch (e: Exception) {
          Log.e(TAG, "Gateway failed to start", e)
          isRunning = false
        }
      }
      
      "Starting embedded gateway on port ${config.port}..."
    } catch (e: Exception) {
      "Error starting gateway: ${e.message}"
    }
  }

  /** Stop the embedded gateway */
  fun stop(): String {
    return try {
      val gwDir = File(ctx.filesDir, "gateway")
      ProotExecutor.exec(ctx, "cd ${gwDir.absolutePath} && kill $(cat gateway.pid 2>/dev/null) 2>/dev/null || true", timeoutMs = 5000)
      isRunning = false
      gatewayJob?.cancel()
      "Gateway stopped"
    } catch (e: Exception) {
      "Error stopping gateway: ${e.message}"
    }
  }

  /** Get gateway status */
  fun getStatus(): GatewayStatus {
    return try {
      val gwDir = File(ctx.filesDir, "gateway")
      val pid = ProotExecutor.exec(ctx, "cat ${gwDir.absolutePath}/gateway.pid 2>/dev/null || echo ''", timeoutMs = 3000).trim()
      val running = pid.isNotBlank() && ProotExecutor.exec(ctx, "kill -0 $pid 2>/dev/null && echo YES || echo NO", timeoutMs = 3000).trim() == "YES"
      
      GatewayStatus(
        isRunning = running,
        pid = pid,
        port = if (running) 8080 else 0,
      )
    } catch (e: Exception) {
      GatewayStatus(isRunning = false)
    }
  }

  /** Get gateway logs */
  fun getLogs(lines: Int = 50): String {
    return try {
      val gwDir = File(ctx.filesDir, "gateway")
      ProotExecutor.exec(ctx, "tail -n $lines ${gwDir.absolutePath}/gateway.log 2>/dev/null || echo 'No logs'")
    } catch (e: Exception) {
      "Error reading logs: ${e.message}"
    }
  }

  /** Install gateway dependencies */
  fun installDependencies(): String {
    return try {
      val gwDir = File(ctx.filesDir, "gateway")
      gwDir.mkdirs()
      
      // Check if node is available, install if not
      val nodeCheck = ProotExecutor.exec(ctx, "which node 2>/dev/null || echo 'NO_NODE'", timeoutMs = 5000)
      if (nodeCheck.contains("NO_NODE")) {
        ProotExecutor.exec(ctx, "apk add nodejs npm 2>/dev/null || apt install -y nodejs npm 2>/dev/null || echo 'Install node manually'", timeoutMs = 120000)
      }
      
      // Write package.json and install
      val packageJson = """
        {"name":"aiope-gateway-embedded","version":"1.0.0","dependencies":{"express":"^4.18.0","http-proxy-middleware":"^2.0.6","cors":"^2.8.5"}}
      """.trimIndent()
      File(gwDir, "package.json").writeText(packageJson)
      
      ProotExecutor.exec(ctx, "cd ${gwDir.absolutePath} && npm install 2>&1", timeoutMs = 120000)
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }

  private fun writeGatewayConfig(dir: File, config: GatewayConfig) {
    val providers = config.providers.joinToString(",\n") { p ->
      "    { name: '${p.name}', type: '${p.type}', apiKey: '${p.apiKey}', baseUrl: '${p.baseUrl}', enabled: ${p.enabled} }"
    }
    
    val configJs = """
      module.exports = {
        port: ${config.port},
        host: '${config.host}',
        enableAdmin: ${config.enableAdmin},
        adminPort: ${config.adminPort},
        logLevel: '${config.logLevel}',
        rateLimit: ${config.rateLimit},
        providers: [
$providers
        ]
      };
    """.trimIndent()
    
    File(dir, "config.js").writeText(configJs)
  }

  private fun writeGatewayScript(dir: File) {
    val script = """
      const express = require('express');
      const { createProxyMiddleware } = require('http-proxy-middleware');
      const cors = require('cors');
      const config = require('./config');
      
      const app = express();
      app.use(cors());
      app.use(express.json());
      
      // Health check
      app.get('/health', (req, res) => {
        res.json({ status: 'ok', uptime: process.uptime() });
      });
      
      // Proxy to configured providers
      config.providers.forEach(provider => {
        if (!provider.enabled) return;
        const target = provider.baseUrl || getDefaultUrl(provider.type);
        app.use(`/v1/${provider.name}/*`, createProxyMiddleware({
          target,
          changeOrigin: true,
          pathRewrite: { [`^/v1/${provider.name}`]: '/v1' },
          onProxyReq: (proxyReq, req) => {
            proxyReq.setHeader('Authorization', `Bearer ${provider.apiKey}`);
          }
        }));
      });
      
      // Chat completions endpoint
      app.post('/v1/chat/completions', async (req, res) => {
        const activeProvider = config.providers.find(p => p.enabled);
        if (!activeProvider) {
          return res.status(500).json({ error: 'No provider configured' });
        }
        // Route to active provider
        const target = activeProvider.baseUrl || getDefaultUrl(activeProvider.type);
        const proxy = createProxyMiddleware({
          target,
          changeOrigin: true,
          onProxyReq: (proxyReq) => {
            proxyReq.setHeader('Authorization', `Bearer ${activeProvider.apiKey}`);
          }
        });
        proxy(req, res);
      });
      
      function getDefaultUrl(type) {
        switch(type) {
          case 'openai': return 'https://api.openai.com';
          case 'anthropic': return 'https://api.anthropic.com';
          default: return '';
        }
      }
      
      app.listen(config.port, config.host, () => {
        console.log(`AIOPE Gateway running on ${config.host}:${config.port}`);
      });
    """.trimIndent()
    
    File(dir, "gateway.js").writeText(script)
  }

  /** Build system context */
  fun buildSystemContext(): String {
    val status = getStatus()
    return buildString {
      appendLine("## Embedded Gateway")
      appendLine("Running: ${status.isRunning}")
      if (status.isRunning) {
        appendLine("Port: ${status.port}")
        appendLine("PID: ${status.pid}")
      }
    }
  }
}
