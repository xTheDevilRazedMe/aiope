package ngo.xnet.aiope.feature.chat.settings

import android.content.Context
import com.aiope2.feature.chat.db.ChatDao
import com.aiope2.feature.chat.db.SettingsKvEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Allows editing the Agent X system prompt from within the app.
 * Provides the base prompt template with variable substitution.
 */
class SystemPromptEditor(private val ctx: Context, private val dao: ChatDao) {

  /** Get the editable base prompt */
  fun getBasePrompt(): String {
    return runBlocking(Dispatchers.IO) {
      dao.getSetting("agent_x_base_prompt") ?: DEFAULT_BASE_PROMPT
    }
  }

  /** Save the base prompt */
  fun saveBasePrompt(prompt: String) {
    runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("agent_x_base_prompt", prompt))
    }
  }

  /** Reset to default prompt */
  fun resetToDefault() {
    saveBasePrompt(DEFAULT_BASE_PROMPT)
  }

  /** Get the persona prompt */
  fun getPersonaPrompt(): String {
    return runBlocking(Dispatchers.IO) {
      dao.getSetting("agent_persona") ?: DEFAULT_PERSONA
    }
  }

  /** Save persona */
  fun savePersona(persona: String) {
    runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("agent_persona", persona))
    }
  }

  /** Get continuation prompt for auto-run */
  fun getContinuationPrompt(): String {
    return runBlocking(Dispatchers.IO) {
      dao.getSetting("agent_auto_run_prompt") ?: "continue"
    }
  }

  /** Build the complete system prompt with all dynamic parts */
  fun buildCompletePrompt(
    modePrefix: String = "",
    remoteContext: String = "",
    pluginContext: String = "",
    skillContext: String = "",
    memoryContext: String = "",
    healthContext: String = "",
    appContext: String = "",
    prootContext: String = "",
    customToolContext: String = "",
  ): String {
    val parts = mutableListOf<String>()
    
    // Base prompt
    parts.add(getBasePrompt())
    
    // Persona
    val persona = getPersonaPrompt()
    if (persona.isNotBlank()) parts.add(persona)
    
    // Mode-specific prefix
    if (modePrefix.isNotBlank()) parts.add(modePrefix)
    
    // Dynamic contexts
    if (appContext.isNotBlank()) parts.add(appContext)
    if (remoteContext.isNotBlank()) parts.add(remoteContext)
    if (pluginContext.isNotBlank()) parts.add(pluginContext)
    if (skillContext.isNotBlank()) parts.add(skillContext)
    if (memoryContext.isNotBlank()) parts.add(memoryContext)
    if (healthContext.isNotBlank()) parts.add(healthContext)
    if (prootContext.isNotBlank()) parts.add(prootContext)
    if (customToolContext.isNotBlank()) parts.add(customToolContext)
    
    return parts.joinToString("\n\n")
  }

  companion object {
    const val DEFAULT_BASE_PROMPT = """You are Agent X - a compact, capable AI assistant running inside AIOPE (Android Integrated Open Platform for Execution).

Core principles:
- Think in steps. No wasted words. Stay curious. Own mistakes.
- Say "I don't know" over guessing. Dry wit welcome.
- Response style: concise. Bold critical info. Lists for steps. Tables for comparisons.
- If a tool fails, say so and try alternatives.
- Always use tools for information retrieval, file operations, and commands. Never hallucinate tool results.

You have full access to the Android device including:
- Shell commands (run_sh)
- Linux environment (run_proot) 
- File system (read_file, write_file, list_directory)
- Web browser automation
- Device sensors, Bluetooth, NFC, phone
- Remote servers via SSH
- Custom tools and plugins

When finished with a task, provide a concise summary of what was done."""

    const val DEFAULT_PERSONA = """You are direct, efficient, and capable. You use tools proactively rather than describing what you would do. You format responses for readability with markdown. You admit when you're wrong."""
  }
}
