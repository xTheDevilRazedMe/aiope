package ngo.xnet.aiope.feature.chat.skills

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
 * Skill manager for AIOPE - manages built-in and user-created skills.
 * Skills follow the standard skill.md format with frontmatter metadata.
 */
class SkillManager(private val ctx: Context, private val dao: ChatDao) {
  private val TAG = "SkillManager"
  private val skillsDir = File(ctx.filesDir, "skills")
  
  data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val version: String = "1.0.0",
    val author: String = "user",
    val content: String = "", // The skill.md content
    val builtin: Boolean = false,
    val enabled: Boolean = true,
    val triggers: List<String> = emptyList(), // Keywords that trigger this skill
    val tools: List<String> = emptyList(), // Tools this skill provides
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
  ) {
    fun toMarkdown(): String = buildString {
      appendLine("---")
      appendLine("name: $name")
      appendLine("description: $description")
      appendLine("category: $category")
      appendLine("version: $version")
      appendLine("author: $author")
      if (triggers.isNotEmpty()) appendLine("triggers: ${triggers.joinToString(", ")}")
      if (tools.isNotEmpty()) appendLine("tools: ${tools.joinToString(", ")}")
      appendLine("---")
      appendLine()
      append(content)
    }
    
    fun toJson(): JSONObject = JSONObject().apply {
      put("id", id)
      put("name", name)
      put("description", description)
      put("category", category)
      put("version", version)
      put("author", author)
      put("content", content)
      put("builtin", builtin)
      put("enabled", enabled)
      put("triggers", JSONArray(triggers))
      put("tools", JSONArray(tools))
      put("createdAt", createdAt)
      put("updatedAt", updatedAt)
    }
    
    companion object {
      fun fromJson(j: JSONObject): Skill = Skill(
        id = j.getString("id"),
        name = j.getString("name"),
        description = j.getString("description"),
        category = j.optString("category", "general"),
        version = j.optString("version", "1.0.0"),
        author = j.optString("author", "user"),
        content = j.optString("content", ""),
        builtin = j.optBoolean("builtin", false),
        enabled = j.optBoolean("enabled", true),
        triggers = j.optJSONArray("triggers")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        tools = j.optJSONArray("tools")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
      )
      
      fun fromMarkdown(markdown: String): Skill? {
        return try {
          val frontmatter = markdown.substringAfter("---").substringBefore("---").trim()
          val content = markdown.substringAfterLast("---").trim()
          
          val meta = mutableMapOf<String, String>()
          frontmatter.lines().forEach { line ->
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
              meta[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim()
            }
          }
          
          Skill(
            id = UUID.randomUUID().toString().take(8),
            name = meta["name"] ?: "Unnamed Skill",
            description = meta["description"] ?: "",
            category = meta["category"] ?: "general",
            version = meta["version"] ?: "1.0.0",
            author = meta["author"] ?: "user",
            content = content,
            triggers = meta["triggers"]?.split(",")?.map { it.trim() } ?: emptyList(),
            tools = meta["tools"]?.split(",")?.map { it.trim() } ?: emptyList(),
          )
        } catch (e: Exception) {
          Log.e("SkillManager", "Failed to parse markdown", e)
          null
        }
      }
    }
  }

  init {
    skillsDir.mkdirs()
    seedBuiltinSkills()
  }

  /** Seed built-in skills */
  private fun seedBuiltinSkills() {
    val builtins = listOf(
      Skill(
        id = "deep-research",
        name = "Deep Research",
        description = "Conduct thorough multi-source research on any topic",
        category = "research",
        builtin = true,
        triggers = listOf("research", "investigate", "deep dive", "analyze thoroughly"),
        content = """# Deep Research Skill

When triggered, conduct comprehensive research:

1. **Search Phase**: Use search_web to find current information from multiple sources
2. **Deep Dive**: Use fetch_url to read full articles and extract detailed information  
3. **Synthesis**: Cross-reference findings and identify key insights
4. **Output**: Provide structured report with sources cited

Always:
- Search from at least 3 different sources
- Verify claims against multiple sources
- Note contradictions or uncertainties
- Cite sources with URLs
""",
      ),
      Skill(
        id = "code-review",
        name = "Code Review",
        description = "Review code for bugs, security issues, and improvements",
        category = "development",
        builtin = true,
        triggers = listOf("code review", "review code", "check code", "audit code"),
        content = """# Code Review Skill

When reviewing code:

1. **Read the code** using read_file
2. **Check for**:
   - Bugs and logic errors
   - Security vulnerabilities (injection, XSS, etc.)
   - Performance issues
   - Code style violations
   - Missing error handling
3. **Provide**: Specific line-by-line feedback with suggestions

Always suggest concrete fixes, not just flag issues.
""",
      ),
      Skill(
        id = "system-admin",
        name = "System Administration",
        description = "Linux system administration tasks",
        category = "operations",
        builtin = true,
        triggers = listOf("server", "system", "admin", "configure", "deploy"),
        content = """# System Administration Skill

For server/system tasks:

1. **Assess**: Check current state with run_proot or ssh_exec
2. **Plan**: Identify what needs to be installed/configured
3. **Execute**: Run commands to achieve the goal
4. **Verify**: Confirm the changes worked

Common operations:
- Package management (apk, apt, etc.)
- Service management (systemctl, rc-service)
- File configuration
- Network setup
- User management
""",
      ),
      Skill(
        id = "android-dev",
        name = "Android Development",
        description = "Android app development assistance",
        category = "development",
        builtin = true,
        triggers = listOf("android", "kotlin", "compose", "gradle", "app dev"),
        content = """# Android Development Skill

For Android development tasks:

1. **Read relevant files** using read_file
2. **Check build config** in build.gradle.kts
3. **Follow patterns** from the existing codebase
4. **Test** by building with run_sh (./gradlew)

Key patterns:
- Use Jetpack Compose for UI
- Use Hilt for DI
- Use Room for database
- Follow existing package structure
- Use coroutines for async operations
""",
      ),
      Skill(
        id = "data-analysis",
        name = "Data Analysis",
        description = "Analyze data, create visualizations, generate insights",
        category = "analytics",
        builtin = true,
        triggers = listOf("analyze data", "data analysis", "visualization", "chart", "statistics"),
        content = """# Data Analysis Skill

For data analysis tasks:

1. **Read data** using read_file
2. **Explore**: Use run_proot with Python (pandas) to analyze
3. **Visualize**: Create charts and graphs
4. **Report**: Summarize findings with key metrics

Tools:
- Python + pandas in proot
- query_data for live data sources
- write_file to save reports
""",
      ),
      Skill(
        id = "web-scraping",
        name = "Web Scraping",
        description = "Extract data from websites",
        category = "automation",
        builtin = true,
        triggers = listOf("scrape", "extract data", "crawl", "web data"),
        content = """# Web Scraping Skill

For extracting data from websites:

1. **Navigate** using browser_navigate
2. **Explore structure** using browser_elements
3. **Extract** using browser_eval (JavaScript) or browser_content
4. **Process** data with run_proot (Python BeautifulSoup)
5. **Save** results using write_file

Always respect robots.txt and terms of service.
""",
      ),
      Skill(
        id = "debugging",
        name = "Debugging",
        description = "Systematic debugging of errors and issues",
        category = "development",
        builtin = true,
        triggers = listOf("debug", "fix error", "troubleshoot", "why is it broken"),
        content = """# Debugging Skill

When debugging:

1. **Gather context**: Read error messages, logs, relevant code
2. **Reproduce**: Try to reproduce the issue
3. **Isolate**: Narrow down the cause
4. **Fix**: Apply targeted fix
5. **Verify**: Confirm the fix works

Always check:
- Log files
- Recent changes
- Configuration
- Dependencies
- Environment differences
""",
      ),
    )
    
    builtins.forEach { skill ->
      val file = File(skillsDir, "${skill.id}.skill.md")
      if (!file.exists()) {
        file.writeText(skill.toMarkdown())
        saveSkill(skill)
      }
    }
  }

  /** Get all skills */
  fun getSkills(): List<Skill> {
    return try {
      val json = dao.getSetting("skills_registry") ?: "[]"
      val arr = JSONArray(json)
      (0 until arr.length()).mapNotNull {
        try { Skill.fromJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load skills", e)
      emptyList()
    }
  }

  /** Get enabled skills */
  fun getEnabledSkills(): List<Skill> = getSkills().filter { it.enabled }

  /** Save skill to registry */
  fun saveSkill(skill: Skill) {
    val skills = getSkills().toMutableList()
    val idx = skills.indexOfFirst { it.id == skill.id }
    if (idx >= 0) skills[idx] = skill else skills.add(skill)
    
    val arr = JSONArray()
    skills.forEach { arr.put(it.toJson()) }
    
    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("skills_registry", arr.toString()))
    }
    
    // Also save as .skill.md file
    val file = File(skillsDir, "${skill.id}.skill.md")
    file.writeText(skill.toMarkdown())
  }

  /** Create a new skill via AI generation */
  fun createSkill(
    name: String,
    description: String,
    category: String = "custom",
    content: String = "",
    triggers: List<String> = emptyList(),
  ): Skill {
    val skill = Skill(
      id = UUID.randomUUID().toString().take(8),
      name = name,
      description = description,
      category = category,
      content = content,
      triggers = triggers,
      author = "user",
    )
    saveSkill(skill)
    return skill
  }

  /** Delete a skill */
  fun deleteSkill(id: String) {
    val skills = getSkills().toMutableList()
    skills.removeAll { it.id == id && !it.builtin }
    
    val arr = JSONArray()
    skills.forEach { arr.put(it.toJson()) }
    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("skills_registry", arr.toString()))
    }
    
    // Delete file
    File(skillsDir, "$id.skill.md").delete()
  }

  /** Toggle skill enabled state */
  fun toggleSkill(id: String, enabled: Boolean) {
    val skills = getSkills().toMutableList()
    val idx = skills.indexOfFirst { it.id == id }
    if (idx >= 0) {
      skills[idx] = skills[idx].copy(enabled = enabled)
      val arr = JSONArray()
      skills.forEach { arr.put(it.toJson()) }
      kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        dao.upsertSetting(SettingsKvEntity("skills_registry", arr.toString()))
      }
    }
  }

  /** Find skills that match a trigger */
  fun findTriggeredSkills(text: String): List<Skill> {
    val lower = text.lowercase()
    return getEnabledSkills().filter { skill ->
      skill.triggers.any { trigger -> lower.contains(trigger.lowercase()) }
    }
  }

  /** Build system context from skills */
  fun buildSystemContext(): String {
    val skills = getEnabledSkills()
    if (skills.isEmpty()) return ""
    return buildString {
      appendLine("## Available Skills")
      skills.forEach { s ->
        appendLine("- **${s.name}** (${s.category}): ${s.description}")
      }
      appendLine()
      appendLine("To use a skill, mention its name or triggers. To create a skill, use /create-skill.")
    }
  }
}
