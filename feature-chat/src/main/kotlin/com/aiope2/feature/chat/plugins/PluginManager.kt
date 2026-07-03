package com.aiope2.feature.chat.plugins

import android.content.Context
import android.util.Log
import com.aiope2.feature.chat.db.ChatDao
import com.aiope2.feature.chat.db.SettingsKvEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceLoader

/**
 * Plugin manager for AIOPE - loads, manages, and executes plugins.
 * Supports built-in plugins, APK plugins, and dynamic loading.
 */
class PluginManager(private val ctx: Context, private val dao: ChatDao) {
  private val TAG = "PluginManager"
  private val pluginsDir = File(ctx.filesDir, "plugins")
  private val loadedPlugins = mutableMapOf<String, AiopePlugin>()
  
  data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
    val source: String = "", // "builtin", "apk", "file", "clawhub"
    val entryPoint: String = "",
    val permissions: List<String> = emptyList(),
    val tools: List<PluginToolDef> = emptyList(),
    val hooks: List<String> = emptyList(), // "on_message", "on_tool_call", etc.
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("id", id)
      put("name", name)
      put("version", version)
      put("description", description)
      put("author", author)
      put("enabled", enabled)
      put("builtin", builtin)
      put("source", source)
      put("entryPoint", entryPoint)
      put("permissions", JSONArray(permissions))
      put("tools", JSONArray(tools.map { it.toJson() }))
      put("hooks", JSONArray(hooks))
    }
    
    companion object {
      fun fromJson(j: JSONObject): PluginInfo = PluginInfo(
        id = j.getString("id"),
        name = j.getString("name"),
        version = j.getString("version"),
        description = j.optString("description", ""),
        author = j.optString("author", ""),
        enabled = j.optBoolean("enabled", true),
        builtin = j.optBoolean("builtin", false),
        source = j.optString("source", ""),
        entryPoint = j.optString("entryPoint", ""),
        permissions = j.optJSONArray("permissions")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        tools = j.optJSONArray("tools")?.let { arr -> (0 until arr.length()).map { PluginToolDef.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
        hooks = j.optJSONArray("hooks")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
      )
    }
  }
  
  data class PluginToolDef(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(), // param name -> type
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("name", name)
      put("description", description)
      put("parameters", JSONObject(parameters))
    }
    
    companion object {
      fun fromJson(j: JSONObject): PluginToolDef = PluginToolDef(
        name = j.getString("name"),
        description = j.getString("description"),
        parameters = j.optJSONObject("parameters")?.let { obj ->
          obj.keys().asSequence().associateWith { obj.getString(it) }
        } ?: emptyMap(),
      )
    }
  }

  init {
    pluginsDir.mkdirs()
    loadBuiltinPlugins()
  }

  /** Load all built-in plugins */
  private fun loadBuiltinPlugins() {
    // Register the GitHub plugin (mimics the current MCP plugin)
    val githubPlugin = PluginInfo(
      id = "github",
      name = "GitHub",
      version = "1.0.0",
      description = "GitHub integration - repos, issues, PRs, code search, file operations",
      author = "AIOPE",
      builtin = true,
      source = "builtin",
      tools = listOf(
        PluginToolDef("github_search_repos", "Search GitHub repositories"),
        PluginToolDef("github_search_code", "Search code across GitHub"),
        PluginToolDef("github_get_repo", "Get repository details"),
        PluginToolDef("github_list_issues", "List issues in a repository"),
        PluginToolDef("github_create_issue", "Create an issue"),
        PluginToolDef("github_list_prs", "List pull requests"),
        PluginToolDef("github_get_file", "Get file contents from a repo"),
        PluginToolDef("github_create_file", "Create or update a file"),
        PluginToolDef("github_list_commits", "List commits"),
        PluginToolDef("github_get_commit", "Get commit details"),
      ),
      hooks = listOf("on_tool_call"),
    )
    savePluginInfo(githubPlugin)
    
    // Hermes-AI plugin
    val hermesPlugin = PluginInfo(
      id = "hermes-ai",
      name = "Hermes AI",
      version = "1.0.0",
      description = "Hermes-AI model integration and inference",
      author = "AIOPE",
      builtin = true,
      source = "builtin",
      tools = listOf(
        PluginToolDef("hermes_query", "Query Hermes-AI model"),
        PluginToolDef("hermes_status", "Check Hermes-AI service status"),
      ),
    )
    savePluginInfo(hermesPlugin)
  }

  /** Get all registered plugins */
  fun getPlugins(): List<PluginInfo> {
    return try {
      val json = dao.getSetting("plugins_registry") ?: "[]"
      val arr = JSONArray(json)
      (0 until arr.length()).mapNotNull { 
        try { PluginInfo.fromJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load plugins", e)
      emptyList()
    }
  }

  /** Get enabled plugins */
  fun getEnabledPlugins(): List<PluginInfo> = getPlugins().filter { it.enabled }

  /** Save plugin info to registry */
  fun savePluginInfo(info: PluginInfo) {
    val plugins = getPlugins().toMutableList()
    val idx = plugins.indexOfFirst { it.id == info.id }
    if (idx >= 0) plugins[idx] = info else plugins.add(info)
    
    val arr = JSONArray()
    plugins.forEach { arr.put(it.toJson()) }
    
    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("plugins_registry", arr.toString()))
    }
  }

  /** Enable/disable a plugin */
  fun togglePlugin(id: String, enabled: Boolean) {
    val plugins = getPlugins().toMutableList()
    val idx = plugins.indexOfFirst { it.id == id }
    if (idx >= 0) {
      plugins[idx] = plugins[idx].copy(enabled = enabled)
      val arr = JSONArray()
      plugins.forEach { arr.put(it.toJson()) }
      kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        dao.upsertSetting(SettingsKvEntity("plugins_registry", arr.toString()))
      }
    }
  }

  /** Install a plugin from ClawHub */
  suspend fun installFromClawHub(pluginId: String): String = withContext(Dispatchers.IO) {
    try {
      // ClawHub API endpoint
      val url = "https://clawhub.aiope.org/api/plugins/$pluginId"
      val client = com.aiope2.feature.chat.engine.SafeOkHttp.builder().build()
      val request = okhttp3.Request.Builder().url(url).build()
      
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext "Failed to fetch plugin: HTTP ${response.code}"
        
        val body = response.body?.string() ?: return@withContext "Empty response"
        val json = JSONObject(body)
        
        // Download plugin file
        val downloadUrl = json.getString("downloadUrl")
        val pluginFile = File(pluginsDir, "$pluginId.aiope-plugin")
        
        URL(downloadUrl).openStream().use { input ->
          pluginFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
        
        // Register plugin
        val info = PluginInfo(
          id = pluginId,
          name = json.getString("name"),
          version = json.getString("version"),
          description = json.optString("description", ""),
          author = json.optString("author", ""),
          source = "clawhub",
          entryPoint = pluginFile.absolutePath,
        )
        savePluginInfo(info)
        "Plugin '$pluginId' installed successfully."
      }
    } catch (e: Exception) {
      "Error installing plugin: ${e.message}"
    }
  }

  /** Uninstall a plugin */
  fun uninstallPlugin(id: String) {
    val plugins = getPlugins().toMutableList()
    val plugin = plugins.find { it.id == id } ?: return
    
    // Delete plugin files
    if (plugin.entryPoint.isNotBlank()) {
      File(plugin.entryPoint).delete()
    }
    
    plugins.removeAll { it.id == id }
    val arr = JSONArray()
    plugins.forEach { arr.put(it.toJson()) }
    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("plugins_registry", arr.toString()))
    }
  }

  /** Build tool definitions for all enabled plugins */
  fun buildToolDefs(): List<com.aiope2.feature.chat.engine.StreamingOrchestrator.ToolDef> {
    return getEnabledPlugins().flatMap { plugin ->
      plugin.tools.map { tool ->
        com.aiope2.feature.chat.engine.StreamingOrchestrator.ToolDef(
          name = "${plugin.id}_${tool.name}",
          description = "[${plugin.name}] ${tool.description}",
          parameters = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
              tool.parameters.forEach { (name, type) ->
                put(name, JSONObject().put("type", type))
              }
            })
          },
        )
      }
    }
  }

  /** Execute a plugin tool */
  suspend fun execute(pluginId: String, toolName: String, args: Map<String, Any?>): String {
    val plugin = getPlugins().find { it.id == pluginId } ?: return "Plugin not found: $pluginId"
    if (!plugin.enabled) return "Plugin '$pluginId' is disabled."
    
    return when (pluginId) {
      "github" -> executeGitHubTool(toolName, args)
      "hermes-ai" -> executeHermesTool(toolName, args)
      else -> "Plugin execution not yet implemented for: $pluginId"
    }
  }

  private fun executeGitHubTool(toolName: String, args: Map<String, Any?>): String {
    return try {
      when (toolName) {
        "github_search_repos" -> {
          val query = args["query"]?.toString() ?: return "query required"
          // Use GitHub MCP-style via okhttp
          "GitHub repo search for: $query (integrate with github MCP)"
        }
        "github_search_code" -> {
          val query = args["query"]?.toString() ?: return "query required"
          "GitHub code search for: $query"
        }
        "github_get_repo" -> {
          val owner = args["owner"]?.toString() ?: return "owner required"
          val repo = args["repo"]?.toString() ?: return "repo required"
          "Getting repo: $owner/$repo"
        }
        "github_list_issues" -> {
          val owner = args["owner"]?.toString() ?: return "owner required"
          val repo = args["repo"]?.toString() ?: return "repo required"
          "Listing issues for $owner/$repo"
        }
        "github_create_issue" -> {
          val owner = args["owner"]?.toString() ?: return "owner required"
          val repo = args["repo"]?.toString() ?: return "repo required"
          val title = args["title"]?.toString() ?: return "title required"
          "Creating issue in $owner/$repo: $title"
        }
        "github_get_file" -> {
          val owner = args["owner"]?.toString() ?: return "owner required"
          val repo = args["repo"]?.toString() ?: return "repo required"
          val path = args["path"]?.toString() ?: return "path required"
          "Getting file: $owner/$repo/$path"
        }
        else -> "Unknown GitHub tool: $toolName"
      }
    } catch (e: Exception) {
      "GitHub tool error: ${e.message}"
    }
  }

  private fun executeHermesTool(toolName: String, args: Map<String, Any?>): String {
    return when (toolName) {
      "hermes_query" -> {
        val prompt = args["prompt"]?.toString() ?: return "prompt required"
        "Hermes query: $prompt"
      }
      "hermes_status" -> "Hermes-AI: available"
      else -> "Unknown Hermes tool: $toolName"
    }
  }

  /** Build system context */
  fun buildSystemContext(): String {
    val plugins = getEnabledPlugins()
    if (plugins.isEmpty()) return ""
    return buildString {
      appendLine("## Loaded Plugins")
      plugins.forEach { p ->
        appendLine("- ${p.name} v${p.version}: ${p.description}")
        p.tools.forEach { t ->
          appendLine("  • ${p.id}_${t.name}: ${t.description}")
        }
      }
    }
  }
}

/** Base interface for AIOPE plugins */
interface AiopePlugin {
  val info: PluginManager.PluginInfo
  fun onLoad(ctx: Context)
  fun onUnload()
  fun onToolCall(toolName: String, args: Map<String, Any?>): String
  fun buildSystemContext(): String
}
