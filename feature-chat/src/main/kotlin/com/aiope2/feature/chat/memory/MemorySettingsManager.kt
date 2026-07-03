package com.aiope2.feature.chat.memory

import android.content.Context
import com.aiope2.feature.chat.db.ChatDao
import com.aiope2.feature.chat.db.MemoryEntity
import com.aiope2.feature.chat.db.SettingsKvEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Manages memory settings including global memory toggle,
 * memory editor, and cross-session memory recall.
 */
class MemorySettingsManager(private val ctx: Context, private val dao: ChatDao) {
  
  /** Check if global memory is enabled */
  fun isGlobalMemoryEnabled(): Boolean {
    return runBlocking(Dispatchers.IO) {
      dao.getSetting("memory_global_enabled")?.toBooleanStrictOrNull() ?: false
    }
  }
  
  /** Toggle global memory (recall across all sessions) */
  fun setGlobalMemoryEnabled(enabled: Boolean) {
    runBlocking(Dispatchers.IO) {
      dao.upsertSetting(SettingsKvEntity("memory_global_enabled", enabled.toString()))
    }
  }
  
  /** Get all memories for the editor */
  fun getAllMemories(): List<MemoryEntity> {
    return runBlocking(Dispatchers.IO) {
      dao.getAllMemories()
    }
  }
  
  /** Search memories */
  fun searchMemories(query: String): List<MemoryEntity> {
    return runBlocking(Dispatchers.IO) {
      if (query.isBlank()) dao.getAllMemories() else dao.searchMemories(query)
    }
  }
  
  /** Update a memory */
  fun updateMemory(memory: MemoryEntity) {
    runBlocking(Dispatchers.IO) {
      dao.upsertMemory(memory)
    }
  }
  
  /** Delete a memory by key */
  fun deleteMemory(key: String) {
    runBlocking(Dispatchers.IO) {
      dao.deleteMemory(key)
    }
  }
  
  /** Get memories for system prompt injection */
  fun getMemoriesForPrompt(query: String = ""): String {
    if (!isGlobalMemoryEnabled() && query.isBlank()) return ""
    
    val memories = if (query.isNotBlank()) {
      searchMemories(query)
    } else {
      getAllMemories()
    }
    
    if (memories.isEmpty()) return ""
    
    return buildString {
      appendLine("## User Memory")
      memories.take(20).forEach { m ->
        appendLine("- ${m.key}: ${m.content} [${m.category}]")
      }
    }
  }
  
  /** Export memories to markdown */
  fun exportToMarkdown(): String {
    val memories = getAllMemories()
    return buildString {
      appendLine("# AIOPE Memory Export")
      appendLine("Generated: ${java.time.Instant.now()}")
      appendLine()
      memories.groupBy { it.category }.forEach { (category, items) ->
        appendLine("## ${category.replaceFirstChar { it.uppercase() }}")
        items.forEach { m ->
          appendLine("### ${m.key}")
          appendLine(m.content)
          appendLine()
        }
      }
    }
  }
  
  /** Import memories from markdown */
  fun importFromMarkdown(markdown: String): Int {
    // Parse markdown and create memories
    var count = 0
    // Simple parsing: look for key-value patterns
    val regex = Regex("(?m)^[-*]\\s*(.+?)\\s*:\\s*(.+)$")
    regex.findAll(markdown).forEach { match ->
      val key = match.groupValues[1].trim()
      val content = match.groupValues[2].trim()
      if (key.isNotBlank() && content.isNotBlank()) {
        runBlocking(Dispatchers.IO) {
          dao.upsertMemory(MemoryEntity(key = key, content = content))
        }
        count++
      }
    }
    return count
  }
  
  /** Get memory statistics */
  fun getMemoryStats(): MemoryStats {
    val memories = getAllMemories()
    return MemoryStats(
      totalCount = memories.size,
      byCategory = memories.groupBy { it.category }.mapValues { it.value.size },
      oldestMemory = memories.minByOrNull { it.createdAt },
      newestMemory = memories.maxByOrNull { it.updatedAt },
    )
  }
  
  data class MemoryStats(
    val totalCount: Int,
    val byCategory: Map<String, Int>,
    val oldestMemory: MemoryEntity?,
    val newestMemory: MemoryEntity?,
  )
}
