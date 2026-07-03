# AIOPE v2 Mega Feature Update

## Overview

This update transforms AIOPE into a fully self-aware, permission-maximalist, plugin-extensible AI agent platform. Every requested feature has been implemented across 7 phases.

---

## Phase 1: Enhanced Permission & Access Control System

### Files Added
- `RootDetector.kt` - Detects root, Shizuku, Magisk, adapts behavior
- `EnhancedPermissionHelper.kt` - All permissions with Shizuku/root fallback

### Features
| Feature | Status | Description |
|---------|--------|-------------|
| Root detection | Complete | Checks su, Magisk, KernelSU, APatch |
| Shizuku integration | Complete | Binds to Shizuku service, version detection |
| Magisk detection | Complete | Scans Magisk paths, gets version |
| Adaptive permissions | Complete | Adjusts based on rooted/Shizuku/none |
| All new permissions | Complete | See list below |

### New Permissions Supported
- `WRITE_SECURE_SETTINGS` - System settings control
- `SYSTEM_ALERT_WINDOW` - Overlay windows
- `WRITE_SETTINGS` - Device settings
- `PACKAGE_USAGE_STATS` - App usage data
- `BIND_NOTIFICATION_LISTENER_SERVICE` - Capture notifications
- `BIND_ASSISTANT` - Voice interaction service
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`
- `READ_PHONE_STATE` / `CALL_PHONE`
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`
- `NEARBY_WIFI_DEVICES`
- `FOREGROUND_SERVICE_SPECIAL_USE`

### Special Permission Management
```kotlin
// Request any special permission
EnhancedPermissionHelper.ensureSpecialPermission(ctx, "WRITE_SECURE_SETTINGS")
EnhancedPermissionHelper.ensureSpecialPermission(ctx, "SYSTEM_ALERT_WINDOW")
EnhancedPermissionHelper.ensureSpecialPermission(ctx, "PACKAGE_USAGE_STATS")
EnhancedPermissionHelper.ensureSpecialPermission(ctx, "NOTIFICATION_LISTENER")
EnhancedPermissionHelper.ensureSpecialPermission(ctx, "IGNORE_BATTERY_OPTIMIZATIONS")
```

### Root Execution
```kotlin
// Execute commands as root (auto-detects)
RootDetector.execAsRoot("pm grant com.aiope2 android.permission.WRITE_SECURE_SETTINGS")
```

---

## Phase 2: Proot Ecosystem Manager

### Files Added
- `ProotEnvironmentManager.kt` - Multi-distro, switching, install location
- `TailscaleManager.kt` - Tailscale VPN in proot
- `TermuxBridge.kt` - Connect to Termux app

### Features
| Feature | Status | Description |
|---------|--------|-------------|
| Multi-distro support | Complete | Alpine, Ubuntu, Debian, Arch |
| Custom install location | Complete | Any path, default is private dir |
| Active environment switcher | Complete | Switch between installed environments |
| Tailscale VPN | Complete | Mesh networking in proot |
| Termux integration | Complete | Run commands in Termux |

### Supported Distributions
```kotlin
// Available distros
ProotEnvironmentManager.DistroRegistry.ALL
// - Alpine Linux (default, lightweight)
// - Ubuntu (full-featured)
// - Debian (stable)
// - Arch Linux (rolling release)
```

### Creating Environments
```kotlin
// Create a new environment
val env = ProotEnvironmentManager.createEnvironment(
    ctx = context,
    name = "My Server",
    distro = "ubuntu",
    version = "24.04",
    installPath = "/path/to/install",  // Optional, defaults to private dir
    tailscaleEnabled = true,
    tailscaleAuthKey = "tskey-auth-xxx",
    packages = listOf("nginx", "python3", "node"),
    startupScript = "echo 'Starting up!'",
    envVars = mapOf("MY_VAR" to "value")
)

// Switch active environment
ProotEnvironmentManager.switchEnvironment(context, env.id)

// Get active environment
val active = ProotEnvironmentManager.getActiveEnvironment(context)
```

### Tailscale in Proot
```kotlin
// Check status
val status = TailscaleManager.getStatus(context)

// Start daemon
TailscaleManager.startDaemon(context)

// Login (with auth key for unattended)
TailscaleManager.login(context, authKey = "tskey-auth-xxx")

// Get Tailscale IP
val ip = status.tailscaleIp  // 100.x.x.x
```

### Termux Bridge
```kotlin
// Check if Termux is installed
if (TermuxBridge.isTermuxInstalled(context)) {
    // Run command in Termux
    val output = TermuxBridge.exec(context, "python3 script.py")
    
    // Setup SSH
    TermuxBridge.setupSsh(context)  // Port 8022
}
```

---

## Phase 3: New Tool Capabilities

### Files Added
- `BluetoothToolProvider.kt` - BT scan, adapter info
- `SensorToolProvider.kt` - All Android sensors
- `PhoneToolProvider.kt` - Calls, SIM info, signal strength
- `NfcToolProvider.kt` - NFC status, technologies

### Bluetooth Tools
```kotlin
val bt = BluetoothToolProvider(context)
bt.scanDevices(durationMs = 10000)           // Scan for BLE devices
bt.getAdapterInfo()                           // Get adapter details
bt.setEnabled(true/false)                     // Toggle Bluetooth
```

### Sensor Tools
```kotlin
val sensors = SensorToolProvider(context)
sensors.listSensors()                         // List all sensors
sensors.readSensor("accelerometer")           // Read accelerometer
sensors.readSensor("gyroscope")               // Read gyroscope
sensors.readSensor("magnetometer")            // Read magnetometer
sensors.readSensor("light")                   // Read light sensor
sensors.readSensor("barometer")               // Read pressure
sensors.getOrientation()                      // Get device orientation
sensors.getStepCount()                        // Get step count
```

### Phone Tools
```kotlin
val phone = PhoneToolProvider(context)
phone.makeCall("+1234567890")                // Make a call
phone.openDialer("+1234567890")              // Open dialer
phone.getPhoneInfo()                          // Get phone/SIM info
phone.getSignalStrength()                     // Get signal details
phone.getCallState()                          // Check call state
```

### NFC Tools
```kotlin
val nfc = NfcToolProvider(context)
nfc.getStatus()                               // Check NFC status
nfc.setEnabled(true/false)                    // Toggle NFC (requires root)
nfc.listTechnologies()                        // List NFC technologies
```

---

## Phase 4: Plugin & Skills Architecture

### Files Added
- `PluginManager.kt` - Plugin loading, management, ClawHub
- `SkillManager.kt` - Skills with built-in library
- `SkillCreator.kt` - /create-skill command handler

### Built-in Plugins
| Plugin | ID | Tools |
|--------|-----|-------|
| GitHub | `github` | search_repos, search_code, get_repo, list_issues, create_issue, list_prs, get_file, create_file, list_commits, get_commit |
| Hermes-AI | `hermes-ai` | query, status |

### Plugin Management
```kotlin
val pm = PluginManager(context, dao)

// Get plugins
val plugins = pm.getPlugins()
val enabled = pm.getEnabledPlugins()

// Toggle plugin
pm.togglePlugin("github", enabled = true)

// Install from ClawHub
pm.installFromClawHub("plugin-id")

// Uninstall
pm.uninstallPlugin("plugin-id")

// Execute plugin tool
pm.execute("github", "github_search_repos", mapOf("query" to "kotlin android"))
```

### Built-in Skills (7 included)
| Skill | Triggers | Category |
|-------|----------|----------|
| Deep Research | "research", "investigate", "deep dive" | research |
| Code Review | "code review", "review code", "check code" | development |
| System Admin | "server", "system", "admin", "configure" | operations |
| Android Dev | "android", "kotlin", "compose", "gradle" | development |
| Data Analysis | "analyze data", "visualization", "chart" | analytics |
| Web Scraping | "scrape", "extract data", "crawl" | automation |
| Debugging | "debug", "fix error", "troubleshoot" | development |

### Creating Skills
```
# In chat, use:
/create-skill: A skill for managing Docker containers

# Or programmatically:
skillManager.createSkill(
    name = "Docker Manager",
    description = "Manage Docker containers and images",
    category = "operations",
    triggers = listOf("docker", "container", "image")
)
```

---

## Phase 5: Memory & AI Enhancements

### Files Added
- `MemorySettingsManager.kt` - Memory editor with global toggle
- `SelfHealingEngine.kt` - Auto-recovery, error handling
- `CustomToolManager.kt` - User-defined tools for system prompt
- `SystemPromptEditor.kt` - Editable Agent X prompt

### Memory System
```kotlin
val memory = MemorySettingsManager(context, dao)

// Toggle global memory (across all sessions)
memory.setGlobalMemoryEnabled(true)

// Get all memories
val memories = memory.getAllMemories()

// Search memories
val results = memory.searchMemories("docker")

// Get for system prompt
val promptContext = memory.getMemoriesForPrompt()

// Export/Import
val markdown = memory.exportToMarkdown()
val count = memory.importFromMarkdown(markdown)
```

### Self-Healing Engine
```kotlin
val healing = SelfHealingEngine(context, dao)

// Report errors automatically
healing.reportError("ToolExecutor", exception, "executing ssh command")

// Get health status
val health = healing.getHealthStatus()
// Returns: HEALTHY, DEGRADED, CRITICAL, RECOVERING

// Get error stats
val stats = healing.getErrorStats()

// Auto-recovery handles:
// - Network errors (retry with delay)
// - Permission errors (flag for user)
// - OOM errors (trigger GC)
// - Database errors (suggest migration)
// - Tool failures (fallback retry)
// - Stream errors (reconnection)
```

### Custom Tools
```kotlin
val ctm = CustomToolManager(context, dao)

// Create a custom tool
val tool = CustomToolManager.CustomTool(
    id = UUID.randomUUID().toString(),
    name = "my_backup",
    description = "Backup important files to /sdcard/backup",
    parameters = mapOf("source" to ParamDef("source", "string", "Directory to backup", true)),
    implementation = "cp -r {source} /sdcard/backup/",
    implementationType = ImplType.SHELL
)
ctm.addTool(tool)

// Custom tools auto-inject into Agent X system prompt
val toolDefs = ctm.buildToolDefs()
```

### Editable System Prompt
```kotlin
val editor = SystemPromptEditor(context, dao)

// Edit base prompt
editor.saveBasePrompt("Your new system prompt...")

// Edit persona
editor.savePersona("You are a security-focused analyst...")

// Reset to defaults
editor.resetToDefault()

// Build complete prompt with all contexts
val fullPrompt = editor.buildCompletePrompt(
    modePrefix = buildModePrefix(),
    remoteContext = remoteStore.buildSystemContext(),
    pluginContext = pluginManager.buildSystemContext(),
    skillContext = skillManager.buildSystemContext(),
    memoryContext = memorySettings.getMemoriesForPrompt(),
    healthContext = selfHealing.buildSystemContext(),
    appContext = appIntrospector.buildFullContext(),
    prootContext = ProotEnvironmentManager.buildSystemContext(context),
    customToolContext = customToolManager.buildSystemContext()
)
```

---

## Phase 6: Gateway & Hosting

### Files Added
- `EmbeddedGateway.kt` - Self-hosted gateway in proot
- `LinuxDiscovery.kt` - Network discovery for Linux hosts

### Embedded Gateway
```kotlin
val gateway = EmbeddedGateway(context)

// Check if node/python available
if (gateway.isAvailable()) {
    // Install dependencies
    gateway.installDependencies()
    
    // Configure providers
    val config = EmbeddedGateway.GatewayConfig(
        port = 8080,
        providers = listOf(
            ProviderConfig("gemini", "google-ai-studio", apiKey = "YOUR_KEY"),
            ProviderConfig("gpt4", "openai", apiKey = "YOUR_KEY")
        )
    )
    
    // Start gateway
    gateway.start(config)  // Runs in proot, survives app backgrounding
    
    // Check status
    val status = gateway.getStatus()
    
    // Get logs
    val logs = gateway.getLogs(lines = 100)
    
    // Stop
    gateway.stop()
}
```

### Linux Discovery
```kotlin
val discovery = LinuxDiscovery(context)

// Full network scan
val hosts = discovery.scanNetwork(timeoutMs = 30000)

// Quick SSH scan
val sshHosts = discovery.findSshHosts(timeoutMs = 15000)

// Build context for AI
val context = discovery.buildSystemContext(hosts)
```

---

## Phase 7: AI Self-Awareness & Crash Prevention

### Files Added
- `AppIntrospector.kt` - Full app state awareness, crash prediction

### Features
| Feature | Status | Description |
|---------|--------|-------------|
| App state monitoring | Complete | Memory, threads, CPU, storage |
| Component inventory | Complete | Activities, services, receivers |
| Permission inventory | Complete | All granted permissions |
| Device capabilities | Complete | BT, NFC, GPS, camera, etc. |
| Memory pressure detection | Complete | Predict OOM before it happens |
| Thread leak detection | Complete | Flag excessive thread counts |
| Storage monitoring | Complete | Warn on excessive storage use |
| Proot status | Complete | Environment health checks |

### Using the Introspector
```kotlin
val introspector = AppIntrospector(context)

// Get full app state
val state = introspector.getAppState()

// Predict issues before they happen
val issues = introspector.predictIssues()
// Returns: List of PredictedIssue with severity, component, description, suggestion

// Build complete AI context (this is injected into system prompt)
val context = introspector.buildFullContext(
    pluginManager = pluginManager,
    skillManager = skillManager
)
// Includes: app state, privilege level, proot status, device capabilities,
//           active plugins, available skills, predicted issues
```

### Crash Prediction Triggers
- **Memory > 85%** -> HIGH severity, suggest GC
- **Memory > 70%** -> MEDIUM severity, monitor
- **Threads > 100** -> MEDIUM severity, check for leaks
- **Storage > 500MB** -> LOW severity, suggest cleanup
- **No proot envs** -> LOW severity, suggest setup

---

## Integration Guide

### Wiring into ChatViewModel

```kotlin
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    // Initialize all new systems
    private val appIntrospector = AppIntrospector(app)
    private val pluginManager = PluginManager(app, dao)
    private val skillManager = SkillManager(app, dao)
    private val skillCreator = SkillCreator(app, skillManager, providerStore)
    private val memorySettings = MemorySettingsManager(app, dao)
    private val selfHealing = SelfHealingEngine(app, dao)
    private val customToolManager = CustomToolManager(app, dao)
    private val systemPromptEditor = SystemPromptEditor(app, dao)
    private val embeddedGateway = EmbeddedGateway(app)
    
    // Build system prompt with all contexts
    private fun buildSystemPrompt(mode: AgentMode): String {
        return systemPromptEditor.buildCompletePrompt(
            modePrefix = mode.systemPrompt,
            pluginContext = pluginManager.buildSystemContext(),
            skillContext = skillManager.buildSystemContext(),
            memoryContext = memorySettings.getMemoriesForPrompt(),
            healthContext = selfHealing.buildSystemContext(),
            appContext = appIntrospector.buildFullContext(pluginManager, skillManager),
            prootContext = ProotEnvironmentManager.buildSystemContext(app),
            customToolContext = customToolManager.buildSystemContext(),
            remoteContext = remoteStore.buildSystemContext()
        )
    }
    
    // Handle /create-skill command
    private suspend fun handleCommand(text: String): Boolean {
        if (SkillCreator.isCreateCommand(text)) {
            val description = SkillCreator.extractDescription(text)
            val result = skillCreator.createFromDescription(description)
            // Post result to chat
            return true
        }
        return false
    }
    
    // Report tool errors to self-healing
    private fun onToolError(toolName: String, error: Throwable) {
        selfHealing.reportError("ToolExecutor", error, "tool=$toolName")
    }
}
```

### AndroidManifest Additions

```xml
<!-- New permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Notification listener -->
<service android:name=".engine.NotificationCaptureService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>

<!-- Permission request activity -->
<activity android:name=".engine.EnhancedPermissionRequestActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:exported="false" />
```

---

## File Summary

| File | Phase | Description |
|------|-------|-------------|
| `RootDetector.kt` | 1 | Root/Shizuku/Magisk detection |
| `EnhancedPermissionHelper.kt` | 1 | Permission management |
| `ProotEnvironmentManager.kt` | 2 | Multi-distro proot management |
| `TailscaleManager.kt` | 2 | Tailscale VPN in proot |
| `TermuxBridge.kt` | 2 | Termux app integration |
| `BluetoothToolProvider.kt` | 3 | Bluetooth scanning |
| `SensorToolProvider.kt` | 3 | All Android sensors |
| `PhoneToolProvider.kt` | 3 | Calls, SIM, signal |
| `NfcToolProvider.kt` | 3 | NFC tools |
| `PluginManager.kt` | 4 | Plugin system |
| `SkillManager.kt` | 4 | Skills library |
| `SkillCreator.kt` | 4 | /create-skill command |
| `MemorySettingsManager.kt` | 5 | Memory editor |
| `SelfHealingEngine.kt` | 5 | Auto-recovery |
| `CustomToolManager.kt` | 5 | User-defined tools |
| `SystemPromptEditor.kt` | 5 | Editable system prompt |
| `EmbeddedGateway.kt` | 6 | Self-hosted gateway |
| `LinuxDiscovery.kt` | 6 | Network discovery |
| `AppIntrospector.kt` | 7 | AI self-awareness |

**Total: 19 new Kotlin files** implementing all requested features.
