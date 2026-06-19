package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.OSAudioTrack
import com.example.system.OSFile
import com.example.system.OSFileSystem
import com.example.system.OSGeminiClient
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// --- SYSTEM WINDOW STRUCT ---
data class OSWindow(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val initialWidth: Float = 530f,
    val initialHeight: Float = 420f,
    var isMaximized: Boolean = false,
    var isMinimized: Boolean = false,
    var positionX: Float = 60f,
    var positionY: Float = 60f,
    var currentWidth: Float = 530f,
    var currentHeight: Float = 420f,
    var focusOrder: Int = 0
)

// --- SYSTEM WORKSPACE STATE VIEWMODEL ---
class OSViewModel : ViewModel() {
    private val _bootStage = MutableStateFlow(0) // 0 = Powered off / Initial, 1 to 6 = boot stages, 7 = desktop active
    val bootStage: StateFlow<Int> = _bootStage

    private val _bootProgressText = MutableStateFlow("Initializing system kernel...")
    val bootProgressText: StateFlow<String> = _bootProgressText

    val fileSystem = OSFileSystem()

    // Drag-drop floating windows
    val openWindows = mutableStateListOf<OSWindow>()
    var activeWindowId by mutableStateOf<String?>(null)
    private var windowCounter = 0

    // Start Menu visibility
    var isStartMenuOpen by mutableStateOf(false)
    var isWidgetPanelOpen by mutableStateOf(false)
    var searchInput by mutableStateOf("")

    // Active Accent Color Selection
    var accentColorHex by mutableStateOf("#00F3FF") // Default Neon Cyan
    val accentColor: Color get() = Color(android.graphics.Color.parseColor(accentColorHex))

    // Simulated states
    var isWifiEnabled by mutableStateOf(true)
    var isBluetoothEnabled by mutableStateOf(true)
    var isPowerEfficiencyMode by mutableStateOf(false)
    var simulatedSpeakerVolume by mutableStateOf(0.7f)
    var screenBrightness by mutableStateOf(0.85f)
    var activeWallpaperRes by mutableStateOf(R.drawable.img_desktop_wallpaper)

    // Security Scan Simulation
    var biometricLockOn by mutableStateOf(false)
    var isScanningBiometric by mutableStateOf(false)
    var isBiometricAuthorized by mutableStateOf(false)
    var securityThreatCount by mutableStateOf(0)
    var isRunningFullSecurityScan by mutableStateOf(false)
    var securityScanProgress by mutableStateOf(0f)
    var securityDiagnosticLog = mutableStateListOf<String>()

    // Live AI Helper Engine
    var aiFeedHistory = mutableStateListOf<Pair<String, Boolean>>( // true = user, false = AI helper
        Pair("Welcome to Hyper AI Assistant companion. Enter text or launch system commands.", false)
    )
    var isAiGenerating by mutableStateOf(false)

    // File Explorer State
    var explorerTabs = mutableStateListOf<Pair<String, String>>( // UUID to active path
        Pair("Tab_1", "/User/Documents")
    )
    var activeTabId by mutableStateOf("Tab_1")
    var selectedFilePathInsideTab = mutableMapOf<String, String?>() // tab_id to file_path
    var explorerCreateFileText by mutableStateOf("")
    var explorerEditFileContent by mutableStateOf("")

    // System metrics simulation (CPU & RAM rolling buffers)
    val cpuMetrics = mutableStateListOf<Float>()
    val ramMetrics = mutableStateListOf<Float>()

    init {
        // Seed rolling system graph points
        repeat(15) {
            cpuMetrics.add(Random.nextFloat() * 0.2f + 0.15f)
            ramMetrics.add(0.48f + Random.nextFloat() * 0.05f)
        }
    }

    fun addCpuMetricPoint() {
        cpuMetrics.add((Random.nextFloat() * 0.35f + (if (isRunningFullSecurityScan) 0.5f else 0.15f)).coerceIn(0.05f, 0.98f))
        if (cpuMetrics.size > 20) cpuMetrics.removeAt(0)
        
        ramMetrics.add((0.45f + Random.nextFloat() * 0.04f + (if (openWindows.isNotEmpty()) openWindows.size * 0.06f else 0.0f)).coerceIn(0.1f, 0.95f))
        if (ramMetrics.size > 20) ramMetrics.removeAt(0)
    }

    fun rebootOS(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            _bootStage.emit(0)
            isStartMenuOpen = false
            isWidgetPanelOpen = false
            openWindows.clear()
            activeWindowId = null
            startBootSequence(scope)
        }
    }

    fun startBootSequence(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            _bootStage.emit(1)
            _bootProgressText.emit("STAGE 1 — POWER ON CORE MODULES...")
            delay(1200)

            _bootStage.emit(2)
            _bootProgressText.emit("STAGE 2 — WAKING HYPER OS MICROKERNEL...")
            delay(1800)

            _bootStage.emit(3)
            _bootProgressText.emit("STAGE 3 — INTERCONNECTING HYPER AI CHIP...")
            delay(2000)

            _bootStage.emit(4)
            _bootProgressText.emit("STAGE 4 — ALLOCATING HYEPR SYSTEM RESOURCE...")
            delay(1800)

            _bootStage.emit(5)
            _bootProgressText.emit("STAGE 5 — BRAND INTEGRITY SEAL SECURED...")
            
            // Simultaneously synthesize the custom startup 5-tone sequence!
            launch {
                OSAudioTrack.playStartupMelody()
            }
            delay(2200)

            _bootStage.emit(6)
            _bootProgressText.emit("Desktop workspaces loading complete.")
            delay(1200)

            _bootStage.emit(7) // Desktop environment active!
        }
    }

    // Window management mechanics
    fun launchWindow(title: String, icon: ImageVector) {
        val id = "win_${++windowCounter}"
        val leftOffset = 40f + (windowCounter % 5) * 30f
        val topOffset = 40f + (windowCounter % 5) * 30f
        
        val newWin = OSWindow(
            id = id,
            title = title,
            icon = icon,
            positionX = leftOffset,
            positionY = topOffset
        )
        openWindows.add(newWin)
        focusWindow(id)
        isStartMenuOpen = false
    }

    fun focusWindow(id: String) {
        activeWindowId = id
        // Boost index hierarchy
        openWindows.forEach { win ->
            if (win.id == id) {
                win.focusOrder = 100
                win.isMinimized = false
            } else {
                win.focusOrder = (win.focusOrder - 1).coerceAtLeast(0)
            }
        }
    }

    fun closeWindow(id: String) {
        openWindows.removeIf { it.id == id }
        if (activeWindowId == id) {
            activeWindowId = openWindows.lastOrNull()?.id
        }
    }

    fun toggleMaximize(id: String) {
        val win = openWindows.find { it.id == id }
        if (win != null) {
            win.isMaximized = !win.isMaximized
            // Force recreation update
            val idx = openWindows.indexOf(win)
            openWindows[idx] = win.copy(isMaximized = win.isMaximized)
        }
    }

    fun minimizeWindow(id: String) {
        val win = openWindows.find { it.id == id }
        if (win != null) {
            win.isMinimized = true
            // Force recreation
            val idx = openWindows.indexOf(win)
            openWindows[idx] = win.copy(isMinimized = true)
            if (activeWindowId == id) {
                activeWindowId = openWindows.filter { !it.isMinimized }.lastOrNull()?.id
            }
        }
    }

    fun restoreWindow(id: String) {
        val win = openWindows.find { it.id == id }
        if (win != null) {
            win.isMinimized = false
            val idx = openWindows.indexOf(win)
            openWindows[idx] = win.copy(isMinimized = false)
            focusWindow(id)
        }
    }

    fun runSecurityExploitScan(scope: kotlinx.coroutines.CoroutineScope) {
        if (isRunningFullSecurityScan) return
        scope.launch {
            isRunningFullSecurityScan = true
            securityDiagnosticLog.clear()
            securityDiagnosticLog.add("[INIT] Loading AI Security threat analyzer...")
            securityScanProgress = 0.05f
            delay(600)
            
            securityDiagnosticLog.add("[CORE] Verifying NextGen Microkernel v4.1 encryption integrity...")
            securityScanProgress = 0.25f
            delay(800)
            
            securityDiagnosticLog.add("[EXPLORER] Auditing File System directory trees and user files...")
            securityScanProgress = 0.50f
            delay(800)
            
            // Check files
            val anomaliesFound = fileSystem.files.filter { !it.isDirectory && it.content.contains("err", ignoreCase = true) }.size
            securityThreatCount = if (anomaliesFound > 0) anomaliesFound else 0
            
            securityDiagnosticLog.add("[SYSTEM] Checking outbound cloud socket packets for telemetry leakage...")
            securityScanProgress = 0.75f
            delay(700)
            
            securityDiagnosticLog.add("[AI] Launching neural biometric shield defense lines...")
            securityScanProgress = 0.95f
            delay(500)
            
            securityScanProgress = 1.0f
            securityDiagnosticLog.add("[READY] Audit complete. Threat Level: SECURE. $securityThreatCount vulnerabilities flagged.")
            isRunningFullSecurityScan = false
        }
    }

    fun handleAssistantPrompt(scope: kotlinx.coroutines.CoroutineScope, text: String) {
        if (text.isBlank()) return
        aiFeedHistory.add(Pair(text, true))
        val currentPrompt = text
        searchInput = ""
        isAiGenerating = true

        scope.launch {
            val normalizedText = currentPrompt.lowercase().trim()
            
            // 1. Process local terminal instructions first
            if (normalizedText.startsWith("create file ") || normalizedText.startsWith("create ")) {
                val components = currentPrompt.substringAfter("create ").trim().split(" ", limit = 2)
                if (components.isNotEmpty()) {
                    val rawName = components[0]
                    val content = components.getOrNull(1) ?: "Interactive document generated by Hyper AI."
                    val targetPath = if (rawName.startsWith("/")) rawName else "/User/Documents/$rawName"
                    val success = fileSystem.createFile(targetPath, content)
                    if (success) {
                        aiFeedHistory.add(Pair("Hyper AI File Agent: Created document successfully at $targetPath.", false))
                    } else {
                        aiFeedHistory.add(Pair("Hyper AI File Agent: Failed creation. File may already exist or target directory is read-only.", false))
                    }
                } else {
                    aiFeedHistory.add(Pair("Usage syntax: create file <filename.txt> <document content text>", false))
                }
            } else if (normalizedText == "open explorer" || normalizedText == "explorer") {
                launchWindow("Hyper Explorer", Icons.Default.FolderOpen)
                aiFeedHistory.add(Pair("Hyper AI Launcher: Hyper Explorer initiated. Active workspace synchronized.", false))
            } else if (normalizedText == "open settings" || normalizedText == "settings") {
                launchWindow("Control Panel", Icons.Default.Settings)
                aiFeedHistory.add(Pair("Hyper AI Launcher: Control Panel core settings loaded.", false))
            } else if (normalizedText == "open security" || normalizedText == "security") {
                launchWindow("Security Center", Icons.Default.Shield)
                aiFeedHistory.add(Pair("Hyper AI Launcher: Opening Security Layers Dashboard...", false))
            } else if (normalizedText == "system stats" || normalizedText == "stats") {
                aiFeedHistory.add(Pair("SYSTEM CONSOLE TELEMETRY:\n- Host OS: Hyper OS RC 1.0\n- Kernel Core: NextGen Microkernel v4.1\n- Active Accent: $accentColorHex\n- Connected Threads: Ultra high efficiency active sockets\n- Biometric lockdown: ${if(biometricLockOn) "ACTIVE" else "DISABLED"}\n- Pinned Windows: ${openWindows.size} active sessions.", false))
            } else if (normalizedText == "clean" || normalizedText == "optimize") {
                repeat(4) { addCpuMetricPoint() }
                aiFeedHistory.add(Pair("System cache flushed successfully. Neural coprocessors allocated to optimal operational frequency.", false))
            } else if (normalizedText.startsWith("set color ") || normalizedText.startsWith("color ")) {
                val colorArg = normalizedText.substringAfter("color ").trim()
                var hexColor = when (colorArg) {
                    "cyan", "blue" -> "#00F3FF"
                    "purple", "pink" -> "#BD00FF"
                    "green" -> "#00FF66"
                    "orange", "amber" -> "#FF9E00"
                    "red" -> "#FF3366"
                    else -> colorArg
                }
                if (!hexColor.startsWith("#")) hexColor = "#$hexColor"
                try {
                    android.graphics.Color.parseColor(hexColor)
                    accentColorHex = hexColor
                    aiFeedHistory.add(Pair("Accent highlight aligned to: $hexColor.", false))
                } catch (e: Exception) {
                    aiFeedHistory.add(Pair("Invalid color argument context. Try: cyan, purple, green, orange, or custom #HEX.", false))
                }
            } else if (normalizedText == "help" || normalizedText == "terminal") {
                aiFeedHistory.add(Pair("Available CLI commands in Prompt Sync:\n- 'create file <name> <content>': Create a local file\n- 'open <explorer/settings/security>': Instantly launch Windows\n- 'stats' or 'system stats': Show microkernel telemetries\n- 'set color <color>': Change dynamic accent highlight (cyan, purple, green, orange)\n- 'clean': Run instant garbage collection optimization\n- Set custom questions to query standard cloud Gemini model stream.", false))
            } else {
                // 2. Query Gemini API (Option B) if api-key is configured
                if (OSGeminiClient.isRealKeyConfigured()) {
                    aiFeedHistory.add(Pair("Establishing Secure Tunnel with Gemini Neural Grid...", false))
                    val systemContext = "You are 'Hyper AI', the deeply integrated ambient OS Intelligence inside Hyper OS, created by Nebular Dynamics & TUCCI CYBER NATION (TCN™). Address the user with supreme respect, intelligence and brevity. Assist them with system configuration commands or answers about future computer systems."
                    val onlineResult = OSGeminiClient.queryGemini(currentPrompt, systemContext)
                    aiFeedHistory.add(Pair(onlineResult, false))
                } else {
                    // Fallback smart offline generative answering
                    val phrases = listOf(
                        "Hyper OS remains in sub-millisecond local container isolation. I am analyzing your request.",
                        "That is an intriguing concept. As the system guardian from NextGen Systems, my recommendation is to maintain strict thread security.",
                        "Command parsed. Did you know you can customize system accent highlights by typing 'set color cyan' or 'set color purple'?",
                        "Hyper AI telemetry indicates normal hardware temperatures. TCN Cyber Grid sync is operating at 99.8% stability.",
                        "Understood. File buffers inside Hyper Explorer are active. You can create text files by typing 'create document.txt' in my prompt!"
                    )
                    delay(1200)
                    aiFeedHistory.add(Pair(phrases.random(), false))
                }
            }

            isAiGenerating = false
        }
    }
}

// --- MAIN ENTRY ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF03010A)
                ) {
                    val viewModel: OSViewModel = viewModel()
                    val scope = rememberCoroutineScope()
                    val bootStage by viewModel.bootStage.collectAsState()

                    // Ensure dynamic system metrics collection
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(3000)
                            viewModel.addCpuMetricPoint()
                        }
                    }

                    // MAIN APPLICATION CONTROLLER
                    when (bootStage) {
                        0 -> {
                            // Boot Power Button (Power state Off)
                            PowerOffScreen(onPowerClicked = {
                                viewModel.startBootSequence(scope)
                            })
                        }
                        in 1..6 -> {
                            // High fidelity interactive boot sequence
                            OSBootLoaderView(
                                stage = bootStage,
                                text = viewModel.bootProgressText.collectAsState().value,
                                onSkipPressed = {
                                    scope.launch {
                                        viewModel.startBootSequence(scope)
                                    }
                                }
                            )
                        }
                        else -> {
                            // The Fully functional Glassmorphic OS Desktop workspace
                            HyperDesktopWorkspace(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// --- COMPOSE CORE VIEWS ---

@Composable
fun PowerOffScreen(onPowerClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04030A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF131124))
                    .border(2.dp, Color(0xFF00F3FF).copy(alpha = 0.5f), CircleShape)
                    .clickable { onPowerClicked() }
                    .testTag("boot_power_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power On OS",
                    tint = Color(0xFF00F3FF),
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "HYPER OS",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                text = "by NextGen Systems",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Tap to boot simulation microkernel...",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun OSBootLoaderView(stage: Int, text: String, onSkipPressed: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_rings")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF020106), Color(0xFF0C091C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Simulated Rotating transparent UI rings (representing SEC_CORE, UI ENGINE, etc)
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Central Glowing logo sphere forms representation
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF00F3FF).copy(alpha = 0.35f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (stage >= 3) Icons.Default.Adb else Icons.Default.Dns,
                        contentDescription = "Kernel Loaded",
                        tint = Color(0xFF00F3FF),
                        modifier = Modifier
                            .size(50.dp)
                            .scale(if (stage >= 3) 1.2f else 1.0f)
                    )
                }

                // Rotating UI rings drawing using Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius1 = 200f
                    val radius2 = 260f
                    val radius3 = 320f

                    // Ring 1 - Security Core (labeled outline)
                    drawCircle(
                        color = Color(0xFF00F3FF).copy(alpha = 0.15f),
                        radius = radius1,
                        style = Stroke(width = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), ringRotation))
                    )

                    // Ring 2 - UI Engine and File System
                    drawCircle(
                        color = Color(0xFFBD00FF).copy(alpha = 0.25f),
                        radius = radius2,
                        style = Stroke(width = 5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(40f, 25f), -ringRotation * 1.5f))
                    )

                    // Ring 3 - Network Layer
                    drawCircle(
                        color = Color(0xFF00FF66).copy(alpha = 0.1f),
                        radius = radius3,
                        style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(60f, 40f), ringRotation * 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            // Step text diagnostics flow
            Text(
                text = "HYPER OS LOADER v1.0",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    letterSpacing = 3.sp,
                    fontFamily = FontFamily.Monospace
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = text,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF00F3FF)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (stage / 6f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00F3FF),
                trackColor = Color(0xFF131124)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Sub-systems status lists
            Column(
                modifier = Modifier.width(220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BootStageRow(name = "SECURITY CORE", active = stage >= 2, color = Color(0xFF00F3FF))
                BootStageRow(name = "UI COMPILER", active = stage >= 2, color = Color(0xFFBD00FF))
                BootStageRow(name = "AI SYNCHRONIZER", active = stage >= 3, color = Color(0xFFBD00FF))
                BootStageRow(name = "FILE SYSTEM DIRECTORY", active = stage >= 4, color = Color(0xFF00FF66))
                BootStageRow(name = "NETWORK INTEL GATE", active = stage >= 4, color = Color(0xFF00FF66))
            }
        }
    }
}

@Composable
fun BootStageRow(name: String, active: Boolean, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = if (active) 0.9f else 0.4f))
        )
        Text(
            text = if (active) "ONLINE" else "WAITING...",
            style = TextStyle(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (active) color else Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

// --- DESKTOP WORKSPACE LAYOUT (FLUENT DESIGN) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperDesktopWorkspace(viewModel: OSViewModel) {
    val scope = rememberCoroutineScope()
    var activeBackcolor = viewModel.accentColor

    // Full screen outer layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Drawing dark cosmic space background `#05060B`
                drawRect(Color(0xFF05060B))
                
                // Top-left glowing Indigo blur sphere (Indigo-600/20 style)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4F46E5).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.minDimension * 0.95f
                    ),
                    center = Offset(0f, 0f),
                    radius = size.minDimension * 0.95f
                )
                
                // Bottom-right glowing Purple blur sphere (Purple-600/20 style)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF9333EA).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.minDimension * 0.95f
                    ),
                    center = Offset(size.width, size.height),
                    radius = size.minDimension * 0.95f
                )
            }
    ) {
        // Desktop Wallpaper loaded from resources with low alpha to blend with glowing cosmic atmospheres
        Image(
            painter = painterResource(id = R.drawable.img_desktop_wallpaper),
            contentDescription = "OS Wallpaper Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.14f
        )

        // Workspace main canvas (clickable to dismiss panels)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 68.dp) // Margin for floating taskbar dock
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    viewModel.isStartMenuOpen = false
                    viewModel.isWidgetPanelOpen = false
                }
        ) {
            // Centered Branding Reveal Overlay (Behind the window layer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(105.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Pulse Glow behind the logo
                        Box(
                            modifier = Modifier
                                .size(85.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF6366F1).copy(alpha = 0.22f), Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            text = "H",
                            style = TextStyle(
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White, Color(0xFFC7D2FE))
                                ),
                                letterSpacing = (-5).sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "HYPER OS",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White,
                            letterSpacing = 6.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nebula Dynamics Core • TCN™",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8).copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            // TOP HEADER STATUS BAR (Sleek Interface online diagnostics display)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left - Active status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399))
                            .border(1.5.dp, Color(0xFF34D399).copy(alpha = 0.4e-1f), CircleShape)
                    )
                    Text(
                        text = "HYPER AI ONLINE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.55f),
                            letterSpacing = 2.sp
                        )
                    )
                }
                // Right - Time and Signal metrics representation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "12:48 PM",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wifi Link Status",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                        Row(
                            modifier = Modifier.height(9.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            Box(modifier = Modifier.width(1.5.dp).height(3.dp).background(Color.White.copy(alpha = 0.4f)))
                            Box(modifier = Modifier.width(1.5.dp).height(5.dp).background(Color.White.copy(alpha = 0.6f)))
                            Box(modifier = Modifier.width(1.5.dp).height(8.dp).background(Color.White))
                        }
                    }
                }
            }

            // 1. Grid of Desktop Pinned Applications / Shortcuts
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(top = 60.dp, start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DesktopShortcutIcon(
                    title = "Hyper Explorer",
                    icon = Icons.Default.FolderOpen,
                    accentColor = activeBackcolor,
                    tag = "desktop_icon_explorer",
                    onClick = { viewModel.launchWindow("Hyper Explorer", Icons.Default.FolderOpen) }
                )
                DesktopShortcutIcon(
                    title = "Control Panel",
                    icon = Icons.Default.Settings,
                    accentColor = activeBackcolor,
                    tag = "desktop_icon_settings",
                    onClick = { viewModel.launchWindow("Control Panel", Icons.Default.Settings) }
                )
                DesktopShortcutIcon(
                    title = "Hyper AI Helper",
                    icon = Icons.Default.Psychology,
                    accentColor = activeBackcolor,
                    tag = "desktop_icon_ai",
                    onClick = { viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology) }
                )
                DesktopShortcutIcon(
                    title = "Security Center",
                    icon = Icons.Default.Shield,
                    accentColor = activeBackcolor,
                    tag = "desktop_icon_security",
                    onClick = { viewModel.launchWindow("Security Center", Icons.Default.Shield) }
                )
                DesktopShortcutIcon(
                    title = "Widgets HUD",
                    icon = Icons.Default.Dashboard,
                    accentColor = activeBackcolor,
                    tag = "desktop_icon_widgets",
                    onClick = { viewModel.isWidgetPanelOpen = !viewModel.isWidgetPanelOpen }
                )
            }

            // 2. Render all floating Windows
            viewModel.openWindows.forEach { window ->
                if (!window.isMinimized) {
                    key(window.id) {
                        FloatingOSWindow(
                            window = window,
                            viewModel = viewModel,
                            onFocus = { viewModel.focusWindow(window.id) },
                            onClose = { viewModel.closeWindow(window.id) },
                            onMinimize = { viewModel.minimizeWindow(window.id) },
                            onMaximize = { viewModel.toggleMaximize(window.id) }
                        ) {
                            // Inject actual view internally based on titles
                            when (window.title) {
                                "Hyper Explorer" -> AppFileSystemView(viewModel)
                                "Control Panel" -> AppControlCenterView(viewModel)
                                "Hyper AI Assistant" -> AppAssistantView(viewModel)
                                "Security Center" -> AppSecurityView(viewModel)
                            }
                        }
                    }
                }
            }

            // Slidable transparent notification panel
            AnimatedVisibility(
                visible = viewModel.isWidgetPanelOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(300.dp)
            ) {
                OSWidgetPanelHUD(viewModel)
            }

            // Start Menu pop-up dialog panel (Shifted up to accommodate centered floating dock)
            AnimatedVisibility(
                visible = viewModel.isStartMenuOpen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp)
            ) {
                StartMenuPanelHUD(viewModel)
            }
        }

        // 3. Centralised Centered glassmorphic Fluent Taskbar HUD aligned bottom
        TaskbarHUD(viewModel = viewModel)
    }
}

// SHORTCUT ICON COMPONENT
@Composable
fun DesktopShortcutIcon(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(85.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF131124).copy(alpha = 0.70f))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- TASKBARHUD WINDOWS-STYLE ---
@Composable
fun TaskbarHUD(viewModel: OSViewModel) {
    var activeAccent = viewModel.accentColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.35f)) // bg-black/20 styled glassmorphism
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)) // border-white/10
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left - Quick diagnostics indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (viewModel.isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Wifi",
                    tint = if (viewModel.isWifiEnabled) activeAccent else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(15.dp).clickable { viewModel.isWifiEnabled = !viewModel.isWifiEnabled }
                )
                Icon(
                    imageVector = if (viewModel.isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    contentDescription = "Bluetooth",
                    tint = if (viewModel.isBluetoothEnabled) activeAccent else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(15.dp).clickable { viewModel.isBluetoothEnabled = !viewModel.isBluetoothEnabled }
                )
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = "Battery Status",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Separator/Divider
            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.15f)))

            // Center - Centered app lists (like Windows 11 Fluent)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleek Start Button with 'H' Logo in modern layout as requested
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (viewModel.isStartMenuOpen) activeAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, activeAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.isStartMenuOpen = !viewModel.isStartMenuOpen }
                        .testTag("taskbar_start_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "H",
                        style = TextStyle(
                            color = activeAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Opened floating app status indicators inside taskbar
                OSShortcutIndicator(
                    icon = Icons.Default.FolderOpen,
                    isOpen = viewModel.openWindows.any { it.title == "Hyper Explorer" },
                    isActive = viewModel.activeWindowId != null && viewModel.openWindows.find { it.id == viewModel.activeWindowId }?.title == "Hyper Explorer",
                    accentColor = activeAccent,
                    onClick = {
                        val target = viewModel.openWindows.find { it.title == "Hyper Explorer" }
                        if (target == null) {
                            viewModel.launchWindow("Hyper Explorer", Icons.Default.FolderOpen)
                        } else {
                            if (target.isMinimized) viewModel.restoreWindow(target.id) else viewModel.minimizeWindow(target.id)
                        }
                    }
                )

                OSShortcutIndicator(
                    icon = Icons.Default.Settings,
                    isOpen = viewModel.openWindows.any { it.title == "Control Panel" },
                    isActive = viewModel.activeWindowId != null && viewModel.openWindows.find { it.id == viewModel.activeWindowId }?.title == "Control Panel",
                    accentColor = activeAccent,
                    onClick = {
                        val target = viewModel.openWindows.find { it.title == "Control Panel" }
                        if (target == null) {
                            viewModel.launchWindow("Control Panel", Icons.Default.Settings)
                        } else {
                            if (target.isMinimized) viewModel.restoreWindow(target.id) else viewModel.minimizeWindow(target.id)
                        }
                    }
                )

                // Separator/Divider
                Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.15f)))

                // Glowing AI Pulse center widget
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF6366F1).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology) },
                    contentAlignment = Alignment.Center
                ) {
                    // Small AI pulse bar layout
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(2.dp).height(8.dp).background(Color(0xFFC7D2FE)))
                        Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color(0xFF818CF8)))
                        Box(modifier = Modifier.width(2.dp).height(8.dp).background(Color(0xFFC7D2FE)))
                    }
                }

                OSShortcutIndicator(
                    icon = Icons.Default.Psychology,
                    isOpen = viewModel.openWindows.any { it.title == "Hyper AI Assistant" },
                    isActive = viewModel.activeWindowId != null && viewModel.openWindows.find { it.id == viewModel.activeWindowId }?.title == "Hyper AI Assistant",
                    accentColor = activeAccent,
                    onClick = {
                        val target = viewModel.openWindows.find { it.title == "Hyper AI Assistant" }
                        if (target == null) {
                            viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology)
                        } else {
                            if (target.isMinimized) viewModel.restoreWindow(target.id) else viewModel.minimizeWindow(target.id)
                        }
                    }
                )

                OSShortcutIndicator(
                    icon = Icons.Default.Shield,
                    isOpen = viewModel.openWindows.any { it.title == "Security Center" },
                    isActive = viewModel.activeWindowId != null && viewModel.openWindows.find { it.id == viewModel.activeWindowId }?.title == "Security Center",
                    accentColor = activeAccent,
                    onClick = {
                        val target = viewModel.openWindows.find { it.title == "Security Center" }
                        if (target == null) {
                            viewModel.launchWindow("Security Center", Icons.Default.Shield)
                        } else {
                            if (target.isMinimized) viewModel.restoreWindow(target.id) else viewModel.minimizeWindow(target.id)
                        }
                    }
                )
            }

            // Separator/Divider
            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.15f)))

            // Right - Clock, Calendar stats widget trigger button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.isWidgetPanelOpen = !viewModel.isWidgetPanelOpen }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "12:48",
                        style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        text = "Jun 19, 2026",
                        style = TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = "Widget Hud Toggle",
                    tint = activeAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun OSShortcutIndicator(
    icon: ImageVector,
    isOpen: Boolean,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = "App Shortcut",
                tint = if (isOpen) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            if (isOpen) {
                Box(
                    modifier = Modifier
                        .width(if (isActive) 12.dp else 5.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

// --- THE FLOATING MULTI-WINDOW CONTAINER MECHANIC ---
@Composable
fun FloatingOSWindow(
    window: OSWindow,
    viewModel: OSViewModel,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    content: @Composable () -> Unit
) {
    var activeAccent = viewModel.accentColor
    var posX by remember { mutableStateOf(window.positionX) }
    var posY by remember { mutableStateOf(window.positionY) }
    var sizeW by remember { mutableStateOf(window.currentWidth) }
    var sizeH by remember { mutableStateOf(window.currentHeight) }

    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = if (window.isMaximized) {
            Modifier
                .fillMaxSize()
                .padding(bottom = 52.dp) // Taskbar offset
                .background(Color(0xFF0F0E20).copy(alpha = 0.95f))
                .border(2.dp, activeAccent.copy(alpha = 0.5f))
                .pointerInput(Unit) { onFocus() }
        } else {
            Modifier
                .offset { IntOffset(posX.toInt(), posY.toInt()) }
                .width(sizeW.dp)
                .height(sizeH.dp)
                .clip(shape)
                .background(Color(0xFF0B091B).copy(alpha = 0.90f))
                .border(1.5f.dp, if (viewModel.activeWindowId == window.id) activeAccent else Color.White.copy(alpha = 0.15f), shape)
                .pointerInput(Unit) {
                    onFocus()
                }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag-active window title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0xFF13112E))
                    .pointerInput(Unit) {
                        if (!window.isMaximized) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                posX += dragAmount.x
                                posY += dragAmount.y
                                window.positionX = posX
                                window.positionY = posY
                                onFocus()
                            }
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = window.icon,
                        contentDescription = "Window Logo",
                        tint = activeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = window.title,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Window action controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minimize button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onMinimize() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(10.dp))
                    }

                    // Maximize button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onMaximize() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (window.isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Maximize",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3366).copy(alpha = 0.15f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFFF3366), modifier = Modifier.size(10.dp))
                    }
                }
            }

            // Client Component render block
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070514).copy(alpha = 0.70f))
            ) {
                content()

                // Resize corner anchor handle if not maximized
                if (!window.isMaximized) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    sizeW = (sizeW + dragAmount.x).coerceIn(300f, 1000f)
                                    sizeH = (sizeH + dragAmount.y).coerceIn(250f, 800f)
                                    window.currentWidth = sizeW
                                    window.currentHeight = sizeH
                                }
                            }
                    ) {
                        // Drawing diagonal dots
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = activeAccent.copy(alpha = 0.8f), radius = 3f, center = Offset(size.width - 4, size.height - 4))
                            drawCircle(color = activeAccent.copy(alpha = 0.8f), radius = 3f, center = Offset(size.width - 8, size.height - 8))
                            drawCircle(color = activeAccent.copy(alpha = 0.8f), radius = 3f, center = Offset(size.width - 4, size.height - 8))
                            drawCircle(color = activeAccent.copy(alpha = 0.8f), radius = 3f, center = Offset(size.width - 8, size.height - 4))
                        }
                    }
                }
            }
        }
    }
}

// --- EXPANSIVE START MENU PANEL HUD ---
@Composable
fun StartMenuPanelHUD(viewModel: OSViewModel) {
    var searchVal = viewModel.searchInput
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var colorAccent = viewModel.accentColor

    Box(
        modifier = Modifier
            .width(420.dp)
            .height(430.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.50f)) // bg-black/40 modern glassmorphic look
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Input box aligned with AI Assistant Core styled in Slick background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f)) // bg-white/5 style
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✨", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchVal,
                    onValueChange = { viewModel.searchInput = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.SansSerif),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.handleAssistantPrompt(scope, searchVal)
                        focusManager.clearFocus()
                        viewModel.isStartMenuOpen = false
                        viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology)
                    }),
                    decorationBox = { innerTextField ->
                        if (searchVal.isEmpty()) {
                            Text("Ask Hyper AI or search files...", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                        }
                        innerTextField()
                    }
                )
                if (searchVal.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Search Enter",
                        tint = colorAccent,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                viewModel.handleAssistantPrompt(scope, searchVal)
                                viewModel.isStartMenuOpen = false
                                viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology)
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Body dual pane (Windows 7 classic side panel list + Win 11 cards)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Left Pane - Classic List
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("SYSTEM CAPABILITIES", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 2.dp))

                    StartMenuItemRow("Hyper Explorer", Icons.Default.FolderOpen, colorAccent) { viewModel.launchWindow("Hyper Explorer", Icons.Default.FolderOpen) }
                    StartMenuItemRow("Control Panel", Icons.Default.Settings, colorAccent) { viewModel.launchWindow("Control Panel", Icons.Default.Settings) }
                    StartMenuItemRow("Neural AI Bridge", Icons.Default.Psychology, colorAccent) { viewModel.launchWindow("Hyper AI Assistant", Icons.Default.Psychology) }
                    StartMenuItemRow("Biometric Lock Center", Icons.Default.Shield, colorAccent) { viewModel.launchWindow("Security Center", Icons.Default.Shield) }
                    StartMenuItemRow("Metrics HUD Sideout", Icons.Default.Dashboard, colorAccent) { viewModel.isWidgetPanelOpen = !viewModel.isWidgetPanelOpen }
                    StartMenuItemRow("Warm OS Reboot", Icons.Default.PowerSettingsNew, Color(0xFFFF4D79)) { viewModel.rebootOS(scope) }
                }

                // Right Pane - Pinned Piles and Quick files
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f)) // Sleek secondary container
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("RECOMMENDED DATA", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.fileSystem.files.filter { !it.isDirectory }.take(4)) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.launchWindow("Hyper Explorer", Icons.Default.FolderOpen)
                                        viewModel.isStartMenuOpen = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = "TxtFile", tint = colorAccent.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                                Column {
                                    Text(file.name, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(file.path, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

            // Footer - creator identity with high-contrast elements
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF60A5FA)) // bg-gradient-to-tr from-indigo-500 to-blue-400
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("T", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("SYSTEM OPERATOR", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Emma769933", fontSize = 8.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                Text(
                    text = "TUCCI CYBER NATION (TCN™)",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorAccent.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun StartMenuItemRow(name: String, icon: ImageVector, accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = name, tint = accentColor, modifier = Modifier.size(16.dp))
        Text(name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// --- LIVE HUD METRICS SLIDEOUT PANEL ---
@Composable
fun OSWidgetPanelHUD(viewModel: OSViewModel) {
    var activeAccent = viewModel.accentColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B1C).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.1f))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // HUD Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM METRICS HUD",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = activeAccent
                )
                IconButton(onClick = { viewModel.isWidgetPanelOpen = false }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close HUD", tint = Color.White)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.15f))

            // Weather Widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF15122F))
                    .padding(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SEATTLE, WA", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                        Text("68°F / Rainy", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Humidity: 84% • Wind: 12mph", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = "Rainy Cloud", tint = Color(0xFF00F3FF), modifier = Modifier.size(36.dp))
                }
            }

            // Canvas Live CPU usage Monitor
            OSResourceMeterCard(
                title = "CPU CORE FREQUENCY ENGINE",
                currentVal = "${(viewModel.cpuMetrics.lastOrNull()?.times(100)?.toInt()) ?: 15}%",
                metrics = viewModel.cpuMetrics,
                accentColor = activeAccent
            )

            // Canvas Live RAM buffer
            OSResourceMeterCard(
                title = "RAM STACK ALLOCATION",
                currentVal = "${(viewModel.ramMetrics.lastOrNull()?.times(100)?.toInt()) ?: 45}%",
                metrics = viewModel.ramMetrics,
                accentColor = Color(0xFFBD00FF)
            )

            // Global creator branding card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1B0B35), Color(0xFF0A0413))
                        )
                    )
                    .border(0.5f.dp, Color(0xFFBD00FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("CONCEPT INTEGRATION", fontSize = 9.sp, color = Color(0xFFBD00FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("HYPER OS Build v1.0", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("TCN Dynamic micro-kernel architecture utilizing lightweight modular subsystems for enhanced edge multitasking environments.", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Nebula Dynamics © 2026", fontSize = 8.sp, color = Color.White.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun OSResourceMeterCard(title: String, currentVal: String, metrics: List<Float>, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF13112E))
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            Text(currentVal, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Advanced smooth custom canvas graph rendering
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            if (metrics.size < 2) return@Canvas
            val strokePath = Path()
            val fillPath = Path()

            val stepX = size.width / (metrics.size - 1)
            val heightScale = size.height

            // Initial point
            val startY = size.height - (metrics[0] * heightScale)
            strokePath.moveTo(0f, startY)
            fillPath.moveTo(0f, size.height)
            fillPath.lineTo(0f, startY)

            for (i in 1 until metrics.size) {
                val ptX = i * stepX
                val ptY = size.height - (metrics[i] * heightScale)
                strokePath.lineTo(ptX, ptY)
                fillPath.lineTo(ptX, ptY)
            }

            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            // Draw translucent color fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent)
                )
            )

            // Draw line cap outline
            drawPath(
                path = strokePath,
                color = accentColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

// --- WINDOW CLIENT COMPONENTS ---

// 1. HYPER FILE SYSTEM CLIENT VIEW
@Composable
fun AppFileSystemView(viewModel: OSViewModel) {
    var activePath by remember { mutableStateOf("/User/Documents") }
    var accentSelected = viewModel.accentColor
    var scope = rememberCoroutineScope()

    // Retrieve active file system instances
    val fileList = viewModel.fileSystem.getFilesAtDirectory(activePath)
    var selectedFile by remember { mutableStateOf<OSFile?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left - Navigation Folder Tree
        Column(
            modifier = Modifier
                .width(150.dp)
                .fillMaxHeight()
                .background(Color(0xFF0F0D25))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("NAVIGATOR", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            FolderTreeRow("Documents", Icons.Default.Folder, activePath == "/User/Documents") {
                activePath = "/User/Documents"
                selectedFile = null
            }
            FolderTreeRow("Pictures", Icons.Default.Image, activePath == "/User/Pictures") {
                activePath = "/User/Pictures"
                selectedFile = null
            }
            FolderTreeRow("Cloud Storage", Icons.Outlined.Cloud, activePath == "/Cloud/OneDrive") {
                activePath = "/Cloud/OneDrive"
                selectedFile = null
            }
            FolderTreeRow("System Bin", Icons.Default.Terminal, activePath == "/System/bin") {
                activePath = "/System/bin"
                selectedFile = null
            }
            FolderTreeRow("System etc", Icons.Default.Dns, activePath == "/System/etc") {
                activePath = "/System/etc"
                selectedFile = null
            }
        }

        VerticalDivider(color = Color.White.copy(alpha = 0.1f))

        // Center / Right - Files list and contents preview
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(10.dp)
        ) {
            // Path header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Computer, contentDescription = "Home", tint = accentSelected, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activePath,
                    style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedFile == null) {
                // Folder items list
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fileList) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .clickable {
                                    if (file.isDirectory) {
                                        activePath = file.path
                                    } else {
                                        selectedFile = file
                                        viewModel.explorerEditFileContent = file.content
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = "File",
                                    tint = if (file.isDirectory) accentSelected else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = file.name,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Delete action for user created items
                            if (!file.isDirectory && !file.path.startsWith("/System")) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF3366).copy(alpha = 0.70f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            viewModel.fileSystem.deleteFile(file.path)
                                        }
                                )
                            }
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                // Actions box (create new file inside documents / pictures)
                if (!activePath.startsWith("/System")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = viewModel.explorerCreateFileText,
                            onValueChange = { viewModel.explorerCreateFileText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(8.dp),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (viewModel.explorerCreateFileText.isEmpty()) {
                                    Text("New file, e.g. note.txt", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
                                }
                                innerTextField()
                            }
                        )
                        Button(
                            onClick = {
                                val fname = viewModel.explorerCreateFileText.trim()
                                if (fname.isNotEmpty()) {
                                    viewModel.fileSystem.createFile("$activePath/$fname", "# Custom Document generated inside explorer.")
                                    viewModel.explorerCreateFileText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentSelected),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text("Create", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Text editor view
                val currentFile = selectedFile!!
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentFile.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentSelected, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { selectedFile = null }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close text", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = viewModel.explorerEditFileContent,
                        onValueChange = {
                            viewModel.explorerEditFileContent = it
                            viewModel.fileSystem.updateFileContent(currentFile.path, it)
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF040209))
                            .border(0.5f.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderTreeRow(name: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = name, tint = if (active) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        Text(name, fontSize = 11.sp, color = if (active) Color.White else Color.White.copy(alpha = 0.60f), fontFamily = FontFamily.Monospace)
    }
}

// 2. CONTROL CENTER / SYSTEM SETTINGS CLIENT VIEW
@Composable
fun AppControlCenterView(viewModel: OSViewModel) {
    var activeAccent = viewModel.accentColor
    var tabSelected by remember { mutableStateOf(0) } // 0 = Settings, 1 = About TCN
    var scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Tab switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (tabSelected == 0) activeAccent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { tabSelected = 0 },
                contentAlignment = Alignment.Center
            ) {
                Text("System Core Status", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (tabSelected == 1) activeAccent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { tabSelected = 1 },
                contentAlignment = Alignment.Center
            ) {
                Text("About Hyper OS", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (tabSelected == 0) {
            // Quick toggles grid and customize colors
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("DYNAMIC THEME ACCENT COLOR Selector", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentColorBubble(colorHex = "#00F3FF", label = "Cyan", active = viewModel.accentColorHex == "#00F3FF") { viewModel.accentColorHex = "#00F3FF" }
                        AccentColorBubble(colorHex = "#BD00FF", label = "Purple", active = viewModel.accentColorHex == "#BD00FF") { viewModel.accentColorHex = "#BD00FF" }
                        AccentColorBubble(colorHex = "#00FF66", label = "Green", active = viewModel.accentColorHex == "#00FF66") { viewModel.accentColorHex = "#00FF66" }
                        AccentColorBubble(colorHex = "#FF9E00", label = "Amber", active = viewModel.accentColorHex == "#FF9E00") { viewModel.accentColorHex = "#FF9E00" }
                        AccentColorBubble(colorHex = "#FF3366", label = "Red", active = viewModel.accentColorHex == "#FF3366") { viewModel.accentColorHex = "#FF3366" }
                    }
                }

                item {
                    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("SYSTEM INTERFACE DRIVERS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleCard(title = "Wireless Gateway", icon = Icons.Default.Wifi, active = viewModel.isWifiEnabled, modifier = Modifier.weight(1f)) {
                            viewModel.isWifiEnabled = !viewModel.isWifiEnabled
                        }
                        ToggleCard(title = "Bluetooth Core", icon = Icons.Default.Bluetooth, active = viewModel.isBluetoothEnabled, modifier = Modifier.weight(1f)) {
                            viewModel.isBluetoothEnabled = !viewModel.isBluetoothEnabled
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleCard(title = "High Efficiency Mode", icon = Icons.Default.Power, active = viewModel.isPowerEfficiencyMode, modifier = Modifier.weight(1f)) {
                            viewModel.isPowerEfficiencyMode = !viewModel.isPowerEfficiencyMode
                        }
                        ToggleCard(title = "Biometric Shields", icon = Icons.Default.Fingerprint, active = viewModel.biometricLockOn, modifier = Modifier.weight(1f)) {
                            viewModel.biometricLockOn = !viewModel.biometricLockOn
                        }
                    }
                }

                item {
                    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("COPROCESSOR DIAGNOSTICS SOUND GENERATOR", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { scope.launch { OSAudioTrack.playStartupMelody() } },
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Play Startup Theme", color = Color.White, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { scope.launch { OSAudioTrack.playScanChime() } },
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Play Biometric Scan", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
            // TCN Branding information display
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131131))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "HYPER OS Version 1.0 (Concept Buildrc-3)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeAccent,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A revolutionary conceptual multitasking hub merging high efficiency performance cores with classic system tools. Configured for professional enterprise and computing enthusiasts.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.70f),
                            lineHeight = 15.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutDetailCard(label = "DEVELOPER CORE", value = "Nebula Dynamics Systems", icon = Icons.Default.Adb, modifier = Modifier.weight(1f))
                    AboutDetailCard(label = "SYSTEM SOURCE", value = "TUCCI CYBER NATION (TCN™)", icon = Icons.Default.Adb, modifier = Modifier.weight(1f))
                }

                AboutDetailCard(label = "KERNEL INTEGRITY SEALS", value = "NextGen Systems Microkernel RC-v4.1. Secure virtualization isolation compiled for Android Edge multiwindows.", icon = Icons.Default.VerifiedUser, modifier = Modifier.fillMaxWidth())

                Text(
                    text = "Copyright © 2026 Nebula Dynamics Systems All rights reserved. No unlicensed data cloning without TCN authorization protocol encryption codes.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun AccentColorBubble(colorHex: String, label: String, active: Boolean, onClick: () -> Unit) {
    val rgb = Color(android.graphics.Color.parseColor(colorHex))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(rgb)
                .border(2.dp, if (active) Color.White else Color.Transparent, CircleShape)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, fontSize = 9.sp, color = if (active) Color.White else Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ToggleCard(title: String, icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131131))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = title, tint = if (active) Color(0xFF00F3FF) else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            Text(title, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = active,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00F3FF))
        )
    }
}

@Composable
fun AboutDetailCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131131))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
            Text(label, fontSize = 8.sp, color = Color.White.copy(alpha = 0.40f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 10.sp, color = Color.White, lineHeight = 13.sp)
    }
}

// 3. HYPER AI CHAT ASSISTANT COMPANION CLIENT VIEW
@Composable
fun AppAssistantView(viewModel: OSViewModel) {
    var queryText by remember { mutableStateOf("") }
    var activeAccent = viewModel.accentColor
    var scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Ensure list stays near bottom
    LaunchedEffect(viewModel.aiFeedHistory.size) {
        if (viewModel.aiFeedHistory.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.aiFeedHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        // Chat messages section
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF05030E))
                .border(0.5f.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.aiFeedHistory) { feed ->
                val isUser = feed.second
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) activeAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                            .border(0.5f.dp, if (isUser) activeAccent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isUser) "OPERATOR IP" else "HYPER AI CHIP",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) activeAccent else Color(0xFFBD00FF),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = feed.first,
                                fontSize = 10.sp,
                                color = Color.White,
                                lineHeight = 13.sp,
                                fontFamily = if(isUser) FontFamily.Default else FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search engine input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = queryText,
                onValueChange = { queryText = it },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(0.5f.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val prompt = queryText.trim()
                    if (prompt.isNotEmpty()) {
                        viewModel.handleAssistantPrompt(scope, prompt)
                        queryText = ""
                    }
                }),
                decorationBox = { innerTextField ->
                    if (queryText.isEmpty()) {
                        Text("Ask system configurations...", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
                    }
                    innerTextField()
                }
            )

            Button(
                onClick = {
                    val prompt = queryText.trim()
                    if (prompt.isNotEmpty()) {
                        viewModel.handleAssistantPrompt(scope, prompt)
                        queryText = ""
                    }
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.fillMaxHeight(),
                enabled = !viewModel.isAiGenerating
            ) {
                if (viewModel.isAiGenerating) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send prompt", tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// 4. SECURITY SHIELD DEFENSE CLIENT VIEW
@Composable
fun AppSecurityView(viewModel: OSViewModel) {
    var activeAccent = viewModel.accentColor
    var scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status header panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (viewModel.securityThreatCount == 0) Color(0xFF00FF66).copy(alpha = 0.08f)
                    else Color(0xFFFF3366).copy(alpha = 0.08f)
                )
                .border(
                    0.5f.dp,
                    if (viewModel.securityThreatCount == 0) Color(0xFF00FF66).copy(alpha = 0.3f)
                    else Color(0xFFFF3366).copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SYSTEM SECURITY INTEGRITY STATUS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (viewModel.securityThreatCount == 0) "SHIELD INTEGRITY SECURE" else "VULNERABILITIES FLAGGED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.securityThreatCount == 0) Color(0xFF00FF66) else Color(0xFFFF3366),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (viewModel.securityThreatCount == 0) Icons.Default.VerifiedUser else Icons.Default.Warning,
                    contentDescription = "Shield Status",
                    tint = if (viewModel.securityThreatCount == 0) Color(0xFF00FF66) else Color(0xFFFF3366),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Biometric Capture Module widget (Interaction logic!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF131131))
                .padding(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEURAL BIOMETRIC GATE CHANNELS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isScanningBiometric) activeAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .border(1.5f.dp, if (viewModel.isScanningBiometric) activeAccent else Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            scope.launch {
                                viewModel.isScanningBiometric = true
                                delay(900)
                                viewModel.isScanningBiometric = false
                                viewModel.isBiometricAuthorized = true
                                OSAudioTrack.playScanChime()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint Sensor Capture",
                        tint = if (viewModel.isScanningBiometric) activeAccent else if (viewModel.isBiometricAuthorized) Color(0xFF00FF66) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (viewModel.isScanningBiometric) "[PROCESSING CAPTURE SEED...]" 
                           else if (viewModel.isBiometricAuthorized) "BIOMETRICS MATCHED. GATE OPEN." 
                           else "PRESS SENSOR FIELD ABOVE FOR CAPTURE SCAN",
                    fontSize = 8.sp,
                    color = if (viewModel.isScanningBiometric) activeAccent 
                            else if (viewModel.isBiometricAuthorized) Color(0xFF00FF66) 
                            else Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Diagnostic Console scanner
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CORE DIAGNOSTIC CONSOLE PACKETS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Button(
                    onClick = { viewModel.runSecurityExploitScan(scope) },
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp),
                    enabled = !viewModel.isRunningFullSecurityScan
                ) {
                    Text("AUDIT CORES", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF040209))
                    .border(0.5f.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                if (viewModel.securityDiagnosticLog.isEmpty()) {
                    item {
                        Text("[CONSOLE WORKSPACE IDLE] Ready for microkernel packet audit scan.", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    items(viewModel.securityDiagnosticLog) { logText ->
                        Text(logText, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}
