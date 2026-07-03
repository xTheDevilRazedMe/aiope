package com.aiope2.feature.chat.discovery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.net.*
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

/**
 * Discovers nearby Linux environments and services on the local network.
 * Used to find potential gateway hosts or SSH targets.
 */
class LinuxDiscovery(private val ctx: Context) {
  private val TAG = "LinuxDiscovery"
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  data class DiscoveredHost(
    val ip: String,
    val hostname: String,
    val services: List<DiscoveredService>,
    val osGuess: String = "unknown",
    val lastSeen: Long = System.currentTimeMillis(),
  )

  data class DiscoveredService(
    val type: String,    // "ssh", "http", "https", "smb", "nfs", etc.
    val port: Int,
    val name: String,
    val details: String = "",
  )

  /** Scan local network for Linux hosts */
  suspend fun scanNetwork(timeoutMs: Long = 30000): List<DiscoveredHost> = withContext(Dispatchers.IO) {
    val hosts = mutableListOf<DiscoveredHost>()
    
    try {
      // Get local network range
      val localIp = getLocalIpAddress() ?: return@withContext emptyList()
      val subnet = localIp.substringBeforeLast(".")
      
      // Scan common ports on each host in subnet
      val jobs = (1..254).map { i ->
        async {
          val ip = "$subnet.$i"
          val services = scanHost(ip, timeoutMs / 10)
          if (services.isNotEmpty()) {
            val hostname = resolveHostname(ip)
            val osGuess = guessOS(services)
            DiscoveredHost(ip = ip, hostname = hostname, services = services, osGuess = osGuess)
          } else null
        }
      }
      
      jobs.awaitAll().filterNotNull().let { hosts.addAll(it) }
      
      // Also try mDNS/Bonjour discovery
      try {
        hosts.addAll(discoverViaMdns())
      } catch (e: Exception) {
        Log.w(TAG, "mDNS discovery failed: ${e.message}")
      }
      
    } catch (e: Exception) {
      Log.e(TAG, "Network scan failed", e)
    }
    
    hosts
  }

  /** Quick scan for SSH hosts (common for Linux servers) */
  suspend fun findSshHosts(timeoutMs: Long = 15000): List<DiscoveredHost> = withContext(Dispatchers.IO) {
    val hosts = mutableListOf<DiscoveredHost>()
    
    try {
      val localIp = getLocalIpAddress() ?: return@withContext emptyList()
      val subnet = localIp.substringBeforeLast(".")
      
      val jobs = (1..254).map { i ->
        async {
          val ip = "$subnet.$i"
          if (isPortOpen(ip, 22, timeoutMs / 20)) {
            val hostname = resolveHostname(ip)
            DiscoveredHost(
              ip = ip,
              hostname = hostname,
              services = listOf(DiscoveredService("ssh", 22, "SSH")),
              osGuess = "linux",
            )
          } else null
        }
      }
      
      jobs.awaitAll().filterNotNull().let { hosts.addAll(it) }
    } catch (e: Exception) {
      Log.e(TAG, "SSH scan failed", e)
    }
    
    hosts
  }

  /** Scan specific host for services */
  private fun scanHost(ip: String, timeoutMs: Long): List<DiscoveredService> {
    val commonPorts = mapOf(
      22 to "ssh",
      80 to "http",
      443 to "https",
      445 to "smb",
      2049 to "nfs",
      8080 to "http-alt",
      2222 to "ssh-alt", // aiope-remote
    )
    
    return commonPorts.mapNotNull { (port, service) ->
      if (isPortOpen(ip, port, timeoutMs)) {
        DiscoveredService(type = service, port = port, name = service.uppercase())
      } else null
    }
  }

  /** Check if a port is open */
  private fun isPortOpen(ip: String, port: Int, timeoutMs: Long): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
        true
      }
    } catch (_: Exception) { false }
  }

  /** Resolve hostname from IP */
  private fun resolveHostname(ip: String): String {
    return try {
      InetAddress.getByName(ip).hostName?.takeIf { it != ip } ?: ip
    } catch (_: Exception) { ip }
  }

  /** Guess OS from services */
  private fun guessOS(services: List<DiscoveredService>): String {
    return when {
      services.any { it.type == "ssh" } -> "linux/bsd"
      services.any { it.type == "smb" } -> "windows/linux"
      services.any { it.type == "nfs" } -> "linux/unix"
      else -> "unknown"
    }
  }

  /** Discover via mDNS/Bonjour */
  private fun discoverViaMdns(): List<DiscoveredHost> {
    val hosts = mutableListOf<DiscoveredHost>()
    try {
      val jmdns = JmDNS.create(InetAddress.getByName(getLocalIpAddress()))
      
      // Listen for SSH services
      jmdns.addServiceListener("_ssh._tcp.local.", object : ServiceListener {
        override fun onServiceAdded(event: ServiceEvent) {}
        override fun onServiceRemoved(event: ServiceEvent) {}
        override fun onServiceResolved(event: ServiceEvent) {
          val info = event.info
          hosts.add(DiscoveredHost(
            ip = info.inetAddresses.firstOrNull()?.hostAddress ?: "",
            hostname = info.name,
            services = listOf(DiscoveredService("ssh", info.port, info.name)),
            osGuess = "linux",
          ))
        }
      })
      
      Thread.sleep(5000)
      jmdns.close()
    } catch (e: Exception) {
      Log.w(TAG, "mDNS error: ${e.message}")
    }
    return hosts
  }

  /** Get local IP address */
  private fun getLocalIpAddress(): String? {
    return try {
      NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get local IP", e)
      null
    }
  }

  /** Build system context */
  fun buildSystemContext(discoveredHosts: List<DiscoveredHost>): String {
    return buildString {
      appendLine("## Network Discovery")
      if (discoveredHosts.isEmpty()) {
        appendLine("No hosts discovered yet. Use discover_networks to scan.")
      } else {
        appendLine("Discovered ${discoveredHosts.size} host(s):")
        discoveredHosts.forEach { host ->
          appendLine("- ${host.hostname} (${host.ip}) [${host.osGuess}]")
          host.services.forEach { svc ->
            appendLine("  * ${svc.type}:${svc.port}")
          )
        }
      }
    }
  }
}
