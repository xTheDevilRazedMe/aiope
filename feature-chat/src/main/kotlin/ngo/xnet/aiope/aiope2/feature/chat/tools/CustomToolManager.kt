package ngo.xnet.aiope.feature.chat.tools

import android.content.Context
import android.util.Log
import com.aiope2.feature.chat.db.ChatDao
import com.aiope2.feature.chat.db.SettingsKvEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages user-created custom tools that extend AIOPE's capabilities.
 * Custom tools are injected into the Agent X system prompt.
 */
class CustomToolManager(private val ctx: Context, private val dao: ChatDao) {
  private val TAG = "CustomToolManager"
  private val toolsDir = File(ctx.filesDir, "custom_tools")

  data class CustomTool(
    val id: String,
    val name: String,        // Tool name (snake_case)
    val description: String, // Description for AI
    val parameters: Map<String, ParamDef>, // Parameter definitions
    val implementation: String, // Kotlin code or shell command
    val implementationType: ImplType = ImplType.SHELL,
    val enabled: Boolean = true,
    val dangerous: Boolean = false, // Requires confirmation
    val createdAt: Long = System.currentTimeMillis(),
  ) {
    data class ParamDef(
      val name: String,
      val type: String, // string, number, boolean, array
      val description: String = "",
      val required: Boolean = true,
    ) {
      fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("description", description)
      }
    }

    enum class ImplType { SHELL, KOTLIN, PROOT, INTENT }

    fun toJson(): JSONObject = JSONObject().apply {
      put("id", id)
      put("name", name)
      put("description", description)
      put("parameters", JSONObject().apply {
        parameters.forEach { (k, v) -> put(k, v.toJson()) }
      })
      put("implementation", implementation)
      put("implementationType", implementationType.name)
      put("enabled", enabled)
      put("dangerous", dangerous)
      put("createdAt", createdAt)
    }

    /** Generate tool definition for system prompt */
    fun toToolDef(): String {
      val params = parameters.entries.joinToString(", ") { (name, def) ->
        "\"$name\": {\"type\": \"${def.type}\", \"description\": \"${def.description}\"}"
      }
      val required = parameters.filter { it.value.required }.keys.joinToString(", ") { "\"$it\"" }
      return """{"type":"object","properties":{$params},"required":[$required]}"""
    }

    companion object {
      fun fromJson(j: JSONObject): CustomTool = CustomTool(
        id = j.getString("id"),
        name = j.getString("name"),
        description = j.getString("description"),
        parameters = j.optJSONObject("parameters")?.let { obj ->
          obj.keys().asSequence().associateWith { key ->
            val pObj = obj.getJSONObject(key)
            ParamDef(
              name = key,
              type = pObj.optString("type", "string"),
              description = pObj.optString("description", ""),
            )
          }
        } ?: emptyMap(),
        implementation = j.optString("implementation", ""),
        implementationType = try {
          ImplType.valueOf(j.optString("implementationType", "SHELL"))
        } catch (_: Exception) { ImplType.SHELL },
        enabled = j.optBoolean("enabled", true),
        dangerous = j.optBoolean("dangerous", false),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
      )
    }
  }

  init {
    toolsDir.mkdirs()
  }

  /** Get all custom tools */
  fun getTools(): List<CustomTool> {
    return try {
      val json = runBlocking(Dispatchers.IO) { dao.getSetting("custom_tools") } ?: "[]"
      val arr = JSONArray(json)
      (0 until arr.length()).mapNotNull {
        try { CustomTool.fromJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load custom tools", e)
      emptyList()
    }
  }

  /** Get enabled tools */
  fun getEnabledTools(): List<CustomTool> = getTools().filter { it.enabled }

  /** Add a new custom tool */
  fun addTool(tool: CustomTool) {
    val tools = getTools().toMutableList()
    tools.add(tool)
    saveTools(tools)
    
    // Save implementation to file if it's Kotlin code
    if (tool.implementationType == CustomTool.ImplType.KOTLIN) {
      File(toolsDir, "${tool.name}.kt").writeText(tool.implementation)
    }
  }

  /** Update a tool */
  fun updateTool(tool: CustomTool) {
    val tools = getTools().toMutableList()
    val idx = tools.indexOfFirst { it.id == tool.id }
    if (idx >= 0) {
      tools[idx] = tool
      saveTools(tools)
    }
  }

  /** Remove a tool */
  fun removeTool(id: String) {
    val tools = getTools().toMutableList()
    val tool = tools.find { it.id == id } ?: return
    tools.removeAll { it.id == id }
    saveTools(tools)
    File(toolsDir, "${tool.name}.kt").delete()
  }

  /** Toggle tool enabled state */
  fun toggleTool(id: String, enabled: Boolean) {
    val tools = getTools().toMutableList()
    val idx = tools.indexOfFirst { it.id == id }
    if (idx >= 0) {
      tools[idx] = tools[idx].copy(enabled = enabled)
      saveTools(tools)
    }
  }

  /** Execute a custom tool */
  fun execute(tool: CustomTool, args: Map<String, Any?>): String {
    return when (tool.implementationType) {
      CustomTool.ImplType.SHELL -> executeShell(tool, args)
      CustomTool.ImplType.PROOT -> executeProot(tool, args)
      CustomTool.ImplType.INTENT -> executeIntent(tool, args)
      CustomTool.ImplType.KOTLIN -> "Kotlin execution requires compilation"
    }
  }

  private fun executeShell(tool: CustomTool, args: Map<String, Any?>): String {
    var cmd = tool.implementation
    args.forEach { (k, v) ->
      cmd = cmd.replace("{$k}", v?.toString() ?: "")
    }
    return try {
      val proc = Runtime.getRuntime().exec(cmd)
      val output = proc.inputStream.bufferedReader().readText()
      val err = proc.errorStream.bufferedReader().readText()
      proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
      output.ifBlank { err }
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  private fun executeProot(tool: CustomTool, args: Map<String, Any?>): String {
    var cmd = tool.implementation
    args.forEach { (k, v) ->
      cmd = cmd.replace("{$k}", v?.toString() ?: "")
    }
    return com.aiope2.core.terminal.shell.ProotExecutor.exec(ctx, cmd)
  }

  private fun executeIntent(tool: CustomTool, args: Map<String, Any?>): String {
    return try {
      val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        val uri = tool.implementation.replace("{query}", args["query"]?.toString() ?: "")
        data = android.net.Uri.parse(uri)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(intent)
      "Intent launched"
    } catch (e: Exception) { "Error: ${e.message}" }
  }

  /** Build tool definitions for system prompt */
  fun buildToolDefs(): List<com.aiope2.feature.chat.engine.StreamingOrchestrator.ToolDef> {
    return getEnabledTools().map { tool ->
      com.aiope2.feature.chat.engine.StreamingOrchestrator.ToolDef(
        name = tool.name,
        description = tool.description,
        parameters = JSONObject(tool.toToolDef()),
      )
    }
  }

  /** Build system context */
  fun buildSystemContext(): String {
    val tools = getEnabledTools()
    if (tools.isEmpty()) return ""
    return buildString {
      appendLine("## Custom Tools")
      tools.forEach { t ->
        appendLine("- ${t.name}: ${t.description}")
      }
    }
  }

  private fun saveTools(tools: List<CustomTool>) {
    val arr = JSONArray()
    tools.forEach { arr.put(it.toJson()) }
    runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("custom_tools", arr.toString()))
    }
  }
}
