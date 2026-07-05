package ngo.xnet.aiope.feature.chat.skills

import android.content.Context
import android.util.Log
import com.aiope2.feature.chat.engine.StreamingOrchestrator
import com.aiope2.feature.chat.settings.ProviderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles the /create-skill command by generating a skill using AI.
 * Parses user intent and creates a properly formatted skill.md file.
 */
class SkillCreator(
  private val ctx: Context,
  private val skillManager: SkillManager,
  private val providerStore: ProviderStore,
) {
  private val TAG = "SkillCreator"

  data class CreationResult(
    val success: Boolean,
    val skill: SkillManager.Skill? = null,
    val message: String = "",
  )

  /**
   * Create a skill from natural language description.
   * Uses AI to generate the skill content in proper skill.md format.
   */
  suspend fun createFromDescription(description: String): CreationResult = withContext(Dispatchers.IO) {
    try {
      val profile = providerStore.getActive()
      
      // Build prompt for skill generation
      val prompt = buildSkillGenerationPrompt(description)
      
      val orchestrator = StreamingOrchestrator(
        baseUrl = profile.effectiveApiBase(),
        apiKey = profile.apiKey,
        model = profile.selectedModelId,
      )
      
      val sb = StringBuilder()
      orchestrator.stream(listOf("user" to prompt)).collect { chunk ->
        if (chunk.content.isNotEmpty()) sb.append(chunk.content)
      }
      
      val response = sb.toString()
      
      // Parse the generated skill markdown
      val skill = parseGeneratedSkill(response)
      if (skill != null) {
        skillManager.saveSkill(skill)
        CreationResult(
          success = true,
          skill = skill,
          message = "Created skill: **${skill.name}** (${skill.id})",
        )
      } else {
        CreationResult(
          success = false,
          message = "Failed to parse generated skill. Raw output:\n$response",
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Skill creation failed", e)
      CreationResult(
        success = false,
        message = "Error creating skill: ${e.message}",
      )
    }
  }

  /**
   * Create a skill from structured parameters (for programmatic use).
   */
  fun createFromParams(
    name: String,
    description: String,
    category: String,
    content: String,
    triggers: List<String> = emptyList(),
    tools: List<String> = emptyList(),
  ): CreationResult {
    return try {
      val skill = skillManager.createSkill(
        name = name,
        description = description,
        category = category,
        content = content,
        triggers = triggers,
      )
      CreationResult(
        success = true,
        skill = skill,
        message = "Created skill: **${skill.name}** (${skill.id})",
      )
    } catch (e: Exception) {
      CreationResult(
        success = false,
        message = "Error: ${e.message}",
      )
    }
  }

  private fun buildSkillGenerationPrompt(userDescription: String): String {
    return """Create an AIOPE skill based on this description: "$userDescription"

Generate a skill in the following format:

---
name: <Skill Name>
description: <One-line description>
category: <one of: research, development, operations, analytics, automation, creative, communication, custom>
triggers: <comma-separated keywords that activate this skill>
---

<Detailed skill content with:
1. Purpose and when to use
2. Step-by-step workflow
3. Which tools to use and how
4. Examples
5. Best practices
6. Common pitfalls to avoid>

Rules:
- Use markdown formatting
- Reference specific AIOPE tools (search_web, read_file, run_proot, etc.)
- Make it actionable and specific
- Include concrete examples
- The skill should be self-contained and clear
- Triggers should be natural language phrases users might say

Respond ONLY with the skill markdown, no extra text."""
  }

  private fun parseGeneratedSkill(response: String): SkillManager.Skill? {
    // Extract markdown between --- frontmatter blocks
    val trimmed = response.trim()
    
    // Check if response has frontmatter
    return if (trimmed.startsWith("---")) {
      SkillManager.Skill.fromMarkdown(trimmed)
    } else {
      // Try to wrap in frontmatter if AI didn't format correctly
      val lines = trimmed.lines()
      val firstLine = lines.firstOrNull() ?: ""
      
      // Try to extract a name from the first line
      val name = firstLine.removePrefix("#").trim().takeIf { it.isNotBlank() } ?: "Custom Skill"
      val description = lines.getOrNull(1)?.trim()?.removePrefix("*")?.trim() ?: "User-created skill"
      
      SkillManager.Skill(
        id = java.util.UUID.randomUUID().toString().take(8),
        name = name,
        description = description,
        category = "custom",
        content = trimmed,
        triggers = extractTriggers(name, trimmed),
      )
    }
  }

  private fun extractTriggers(name: String, content: String): List<String> {
    val triggers = mutableListOf<String>()
    triggers.add(name.lowercase())
    
    // Extract common keywords from content
    val keywords = listOf(
      "research", "code", "debug", "analyze", "scrape", "deploy",
      "configure", "setup", "install", "build", "test", "review",
      "search", "fetch", "process", "convert", "generate", "create",
      "manage", "monitor", "backup", "restore", "optimize",
    )
    
    val lower = content.lowercase()
    keywords.forEach { keyword ->
      if (lower.contains(keyword) && !triggers.contains(keyword)) {
        triggers.add(keyword)
      }
    }
    
    return triggers.take(10)
  }

  companion object {
    /** Check if text is a /create-skill command */
    fun isCreateCommand(text: String): Boolean {
      return text.trim().startsWith("/create-skill") || text.trim().startsWith("/new-skill")
    }
    
    /** Extract description from /create-skill command */
    fun extractDescription(text: String): String {
      return text.trim()
        .removePrefix("/create-skill")
        .removePrefix("/new-skill")
        .trim()
        .removePrefix(":")
        .trim()
    }
  }
}
