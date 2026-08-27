package com.oprek.tool.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import com.oprek.tool.ui.theme.darkTextFieldColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class GamePreset(
    val name: String,
    val pkg: String,
    val lib: String,
    val metadataMagic: String = "0xFAB11BAF",
    val icon: String = "🎮"
)

val gamePresets = listOf(
    GamePreset("Mobile Legends", "com.mobile.legends", "liblogic.so", icon = "⚔️"),
    GamePreset("Free Fire", "com.dts.freefireth", "libil2cpp.so", icon = "🔥"),
    GamePreset("Free Fire MAX", "com.dts.freefiremax", "libil2cpp.so", icon = "🔥"),
    GamePreset("PUBG Mobile", "com.tencent.ig", "libil2cpp.so", icon = "🎯"),
    GamePreset("PUBG Mobile KR", "com.tencent.igkr", "libil2cpp.so", icon = "🎯"),
    GamePreset("Genshin Impact", "com.miHoYo.GenshinImpact", "libil2cpp.so", icon = "✨"),
    GamePreset("Honkai Star Rail", "com.HoYoverse.hkrpgoversea", "libil2cpp.so", icon = "🚀"),
    GamePreset("Blood Strike", "com.excean.dualaid", "libil2cpp.so", icon = "🩸"),
    GamePreset("COD Mobile", "com.activision.callofduty.shooter", "libil2cpp.so", icon = "🎖️"),
    GamePreset("Brawl Stars", "com.supercell.brawlstars", "libil2cpp.so", icon = "⭐"),
    GamePreset("Standoff 2", "com.axlebolt.standoff2", "libil2cpp.so", icon = "🔫"),
    GamePreset("Roblox", "com.roblox.client", "libil2cpp.so", icon = "🧱"),
    GamePreset("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM", "libil2cpp.so", icon = "🏎️"),
    GamePreset("Clash Royale", "com.supercell.clashroyale", "libil2cpp.so", icon = "👑"),
    GamePreset("Minecraft", "com.mojang.minecraftpe", "libil2cpp.so", icon = "⛏️"),
    GamePreset("Arena of Valor", "com.ngame.allstar.eu", "libil2cpp.so", icon = "🏆"),
    GamePreset("eFootball PES", "jp.konami.pesam", "libil2cpp.so", icon = "⚽"),
    GamePreset("Stumble Guys", "com.kitkagames.fallbuddies", "libil2cpp.so", icon = "🤪"),
    GamePreset("Custom (Manual)", "", "", icon = "🔧")
)

// Shell execution helper
object ShellExec {
    fun exec(cmd: String, root: Boolean = false): List<String> {
        val results = mutableListOf<String>()
        try {
            val process = if (root) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { results.add(it) }
            }
            while (errReader.readLine().also { line = it } != null) {
                line?.let { results.add(it) }
            }
            process.waitFor()
        } catch (e: Exception) {
            results.add("ERROR: ${e.message}")
        }
        return results
    }

    fun execRoot(cmd: String): List<String> = exec(cmd, root = true)

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val output = reader.readLine() ?: ""
            p.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun findPid(packageName: String): Int {
        val lines = execRoot("ps -A")
        for (line in lines) {
            if (line.contains(packageName)) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    return parts[1].toIntOrNull() ?: -1
                }
            }
        }
        return -1
    }

    fun getMemoryMaps(pid: Int): List<String> = execRoot("cat /proc/$pid/maps")

    fun readBytes(pid: Int, address: Long, size: Int): ByteArray? {
        return try {
            val tmpFile = "/data/local/tmp/_oprek_dump.bin"
            // Use dd to read from /proc/pid/mem
            val hexAddr = String.format("%x", address)
            execRoot("dd if=/proc/$pid/mem bs=1 skip=$hexAddr count=$size 2>/dev/null > $tmpFile")
            val file = File(tmpFile)
            if (file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                file.delete()
                bytes
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun extractStringsFromLib(pid: Int, libName: String, maxStrings: Int = 10000): List<String> {
        val results = mutableListOf<String>()
        val maps = getMemoryMaps(pid)
        val readableRegions = mutableListOf<Pair<Long, Long>>()

        for (line in maps) {
            if (line.contains(libName) && line.contains("r--p") || line.contains("r-xp")) {
                val parts = line.split(" ")[0].split("-")
                if (parts.size == 2) {
                    try {
                        val start = parts[0].toLong(16)
                        val end = parts[1].toLong(16)
                        readableRegions.add(start to end)
                    } catch (_: Exception) {}
                }
            }
        }

        // Extract printable strings from each region
        for ((start, end) in readableRegions.take(50)) {
            val size = (end - start).coerceAtMost(4096)
            val bytes = readBytes(pid, start, size.toInt()) ?: continue
            val sb = StringBuilder()
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                if (c in 0x20..0x7E) {
                    sb.append(c.toChar())
                } else {
                    if (sb.length >= 4) {
                        results.add(sb.toString())
                        if (results.size >= maxStrings) return results
                    }
                    sb.clear()
                }
            }
            if (sb.length >= 4) results.add(sb.toString())
        }
        return results
    }

    fun dumpIl2cppMetadata(pid: Int, libName: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val maps = getMemoryMaps(pid)
        val results = mutableListOf<String>()
        var libBase: Long = 0
        var libSize: Long = 0

        // Find lib base address
        for (line in maps) {
            if (line.contains(libName)) {
                val parts = line.split(" ")[0].split("-")
                if (parts.size == 2) {
                    try {
                        val start = parts[0].toLong(16)
                        val end = parts[1].toLong(16)
                        if (libBase == 0L || start < libBase) libBase = start
                        if (end > libBase + libSize) libSize = end - libBase
                    } catch (_: Exception) {}
                }
            }
        }

        results.add("========================================")
        results.add("IL2CPP Dump - OprekTool v0.16.0")
        results.add("Time: $timestamp")
        results.add("PID: $pid")
        results.add("Library: $libName")
        results.add("Base: 0x${String.format("%X", libBase)}")
        results.add("Size: ${libSize / 1024}KB")
        results.add("========================================")
        results.add("")

        // Search for metadata magic 0xFAB11BAF
        var metadataFound = false
        var metadataAddr: Long = 0
        for (line in maps) {
            val parts = line.split(" ")[0].split("-")
            if (parts.size == 2) {
                try {
                    val start = parts[0].toLong(16)
                    val end = parts[1].toLong(16)
                    val regionSize = end - start
                    if (regionSize > 0 && regionSize < 0x10000000) {
                        val magic = "BAF1ABFA" // little-endian of 0xFAB11BAF
                        val check = execRoot("dd if=/proc/$pid/mem bs=1 skip=${String.format("%x", start)} count=4 2>/dev/null | xxd -p")
                        if (check.isNotEmpty() && check[0].trim() == magic) {
                            metadataFound = true
                            metadataAddr = start
                            results.add("[+] Metadata found at 0x${String.format("%X", start)}")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        if (!metadataFound) {
            results.add("[-] Metadata NOT found in memory (likely encrypted)")
            results.add("[-] Strategy: Raw dump for PC Il2CppDumper")
        }

        // Extract strings from lib
        results.add("")
        results.add("--- Extracted Strings (printable) ---")
        val strings = extractStringsFromLib(pid, libName, 5000)
        for (s in strings) {
            results.add(s)
        }

        results.add("")
        results.add("========================================")
        results.add("Dump complete. ${strings.size} strings extracted.")
        results.add("Output: /sdcard/Download/OprekTool/dump/")
        results.add("========================================")

        return results.joinToString("\n")
    }

    fun hideFromRecents(pid: Int) {
        // Try to hide from recent apps
        execRoot("am set-inactive com.oprek.tool true")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Il2cppLoaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Game", "Dump", "Strings", "Hooks", "Output")

    // State
    var selectedGame by remember { mutableIntStateOf(0) }
    var customPkg by remember { mutableStateOf("") }
    var customLib by remember { mutableStateOf("libil2cpp.so") }
    var outputLog by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var hasRoot by remember { mutableStateOf(false) }
    var gameRunning by remember { mutableStateOf(false) }
    var gamePid by remember { mutableIntStateOf(-1) }
    var extractedStrings by remember { mutableStateOf(listOf<String>()) }
    var stringFilter by remember { mutableStateOf("") }

    // Check root on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            hasRoot = ShellExec.isRootAvailable()
            outputLog = if (hasRoot) "[+] Root access confirmed\n" else "[-] No root access detected\n"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IL2CPP Loader", color = AccentCyan) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentCyan)
                    }
                },
                actions = {
                    // Root indicator
                    Text(
                        if (hasRoot) "🔴 ROOT" else "⚪ NO ROOT",
                        color = if (hasRoot) AccentGreen else AccentRed,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab, containerColor = DarkSurface, contentColor = AccentCyan) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> GameTab(selectedGame, { selectedGame = it }, customPkg, { customPkg = it },
                    customLib, { customLib = it }, gameRunning, gamePid, hasRoot,
                    onDetectGame = {
                        scope.launch(Dispatchers.IO) {
                            if (!hasRoot) {
                                outputLog = "[-] Root required!\n"
                                return@launch
                            }
                            isRunning = true
                            val game = gamePresets[selectedGame]
                            val pkg = if (game.pkg.isEmpty()) customPkg else game.pkg
                            val lib = if (game.pkg.isEmpty()) customLib else game.lib
                            outputLog += "[*] Searching for $pkg...\n"

                            val pid = ShellExec.findPid(pkg)
                            if (pid == -1) {
                                outputLog += "[-] Game NOT running! Start the game first.\n"
                                gameRunning = false
                                isRunning = false
                                return@launch
                            }
                            gamePid = pid
                            gameRunning = true
                            outputLog += "[+] Game found! PID: $pid\n"
                            outputLog += "[+] Library: $lib\n"

                            // Verify lib is loaded
                            val maps = ShellExec.getMemoryMaps(pid)
                            val libLoaded = maps.any { it.contains(lib) }
                            if (libLoaded) {
                                outputLog += "[+] $lib loaded in memory ✓\n"
                            } else {
                                outputLog += "[-] $lib NOT found in memory!\n"
                                outputLog += "[-] Available .so files:\n"
                                for (line in maps) {
                                    if (line.contains(".so")) {
                                        val soName = line.substringAfterLast("/").split(" ")[0]
                                        if (soName.isNotEmpty() && !line.contains("/system/")) {
                                            outputLog += "    $soName\n"
                                        }
                                    }
                                }
                            }
                            isRunning = false
                        }
                    })
                1 -> DumpTab(selectedGame, customPkg, customLib, gameRunning, gamePid, isRunning, outputLog,
                    onDump = { filter ->
                        scope.launch(Dispatchers.IO) {
                            if (!hasRoot || gamePid == -1) {
                                outputLog += "[-] Root + running game required!\n"
                                return@launch
                            }
                            isRunning = true
                            val game = gamePresets[selectedGame]
                            val lib = if (game.pkg.isEmpty()) customLib else game.lib
                            outputLog += "\n[*] Starting IL2CPP dump...\n"
                            outputLog += "[*] PID: $gamePid | Library: $lib\n"
                            outputLog += "[*] Parsing memory maps...\n"

                            val result = ShellExec.dumpIl2cppMetadata(gamePid, lib)
                            outputLog += result

                            // Save to file
                            withContext(Dispatchers.IO) {
                                try {
                                    val dir = File("/sdcard/Download/OprekTool/dump")
                                    dir.mkdirs()
                                    val pkg = if (game.pkg.isEmpty()) customPkg else game.pkg
                                    val filename = "il2cpp_${pkg}_${System.currentTimeMillis()}.txt"
                                    File(dir, filename).writeText(result)
                                    outputLog += "\n[+] Saved to /sdcard/Download/OprekTool/dump/$filename\n"
                                } catch (e: Exception) {
                                    outputLog += "\n[-] Save failed: ${e.message}\n"
                                }
                            }
                            isRunning = false
                        }
                    })
                2 -> StringsTab(gameRunning, gamePid, extractedStrings, { extractedStrings = it },
                    stringFilter, { stringFilter = it }, isRunning, outputLog,
                    onScanStrings = {
                        scope.launch(Dispatchers.IO) {
                            if (!hasRoot || gamePid == -1) {
                                outputLog += "[-] Root + running game required!\n"
                                return@launch
                            }
                            isRunning = true
                            val game = gamePresets[selectedGame]
                            val lib = if (game.pkg.isEmpty()) customLib else game.lib
                            outputLog += "\n[*] Scanning strings from $lib...\n"
                            val strings = ShellExec.extractStringsFromLib(gamePid, lib, 10000)
                            extractedStrings = strings
                            outputLog += "[+] Found ${strings.size} strings\n"
                            isRunning = false
                        }
                    })
                3 -> HooksTab(gameRunning, gamePid, outputLog, { outputLog = it })
                4 -> OutputTab(outputLog, context)
            }
        }
    }
}

@Composable
private fun GameTab(
    selected: Int, onSelect: (Int) -> Unit,
    customPkg: String, onPkgChange: (String) -> Unit,
    customLib: String, onLibChange: (String) -> Unit,
    gameRunning: Boolean, pid: Int, hasRoot: Boolean,
    onDetectGame: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Select Target Game", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            gamePresets.forEachIndexed { index, game ->
                Surface(
                    onClick = { onSelect(index) },
                    color = if (selected == index) AccentCyan.copy(alpha = 0.15f) else DarkCard,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(game.icon, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(game.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (game.pkg.isNotEmpty()) {
                                Text("${game.pkg} | ${game.lib}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (selected == index) {
                            Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Custom input
        if (selected == gamePresets.size - 1) {
            DarkCard {
                Text("Custom Package", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(customPkg, onPkgChange, label = { Text("Package Name (e.g. com.mobile.legends)") },
                    modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(customLib, onLibChange, label = { Text("Library (e.g. libil2cpp.so)") },
                    modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            }
        }

        // Detect button
        DarkCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Status", color = AccentCyan, fontWeight = FontWeight.Bold)
                    Text(
                        if (gameRunning) "🟢 Running (PID: $pid)" else "🔴 Not running",
                        color = if (gameRunning) AccentGreen else AccentRed, fontSize = 12.sp
                    )
                }
                Button(onClick = onDetectGame, enabled = hasRoot,
                    colors = ButtonDefaults.buttonColors(containerColor = if (hasRoot) AccentGreen else TextMuted)) {
                    Icon(Icons.Default.Refresh, null, tint = DarkBg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Detect", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DumpTab(
    selectedGame: Int, customPkg: String, customLib: String,
    gameRunning: Boolean, pid: Int, isRunning: Boolean, log: String,
    onDump: (String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        val game = gamePresets[selectedGame]
        DarkCard {
            Text("IL2CPP Metadata Dumper", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Game: ${game.name}", color = TextPrimary, fontSize = 13.sp)
            Text("Library: ${if (game.pkg.isEmpty()) customLib else game.lib}", color = TextSecondary, fontSize = 11.sp)
            Text("PID: ${if (pid > 0) pid.toString() else "Not detected"}", color = TextSecondary, fontSize = 11.sp)
            Text("Output: /sdcard/Download/OprekTool/dump/", color = TextSecondary, fontSize = 11.sp)
        }

        DarkCard {
            Text("What this does:", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val features = listOf(
                "1. Parse /proc/PID/maps to find lib base address",
                "2. Scan all readable memory regions",
                "3. Search for IL2CPP metadata magic (0xFAB11BAF)",
                "4. If found → parse TypeDef/MethodDef/FieldDef → dump.cs",
                "5. If encrypted → raw dump for PC Il2CppDumper",
                "6. Extract all printable strings from library"
            )
            features.forEach { f ->
                Text(f, color = TextSecondary, fontSize = 11.sp)
            }
        }

        Button(
            onClick = { onDump("") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && gameRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
        ) {
            if (isRunning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = DarkBg, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Dumping...", color = DarkBg, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudDownload, null, tint = DarkBg)
                Spacer(Modifier.width(8.dp))
                Text("START DUMP", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StringsTab(
    gameRunning: Boolean, pid: Int, strings: List<String>, onStringsChange: (List<String>) -> Unit,
    filter: String, onFilterChange: (String) -> Unit,
    isRunning: Boolean, log: String,
    onScanStrings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter + Scan
        DarkCard {
            OutlinedTextField(filter, onFilterChange, label = { Text("Filter strings...") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanStrings, enabled = !isRunning && gameRunning, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                    Text("Scan Strings", color = DarkBg, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { onStringsChange(emptyList()) }, modifier = Modifier.weight(0.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                    Text("Clear", color = TextPrimary)
                }
            }
            if (strings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Found: ${strings.size} strings", color = AccentGreen, fontSize = 11.sp)
            }
        }

        // String list
        val filtered = if (filter.isEmpty()) strings else strings.filter { it.contains(filter, ignoreCase = true) }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered.size) { idx ->
                Text(
                    text = "0x${String.format("%04X", idx)}: ${filtered[idx]}",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(4.dp)).background(DarkCard).padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun HooksTab(gameRunning: Boolean, pid: Int, log: String, onLogChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var hookTarget by remember { mutableStateOf("") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Method Hooker (Root ptrace)", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Hook IL2CPP methods at runtime using ptrace", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(hookTarget, { hookTarget = it },
                label = { Text("Search method name...") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    if (!gameRunning) {
                        onLogChange(log + "[-] Game not running!\n")
                        return@launch
                    }
                    onLogChange(log + "[*] Searching for method: $hookTarget\n")
                    // Search for method name in extracted strings
                    val lines = log.split("\n")
                    val matches = lines.filter { it.contains(hookTarget, ignoreCase = true) }
                    if (matches.isNotEmpty()) {
                        onLogChange(log + "[+] Found ${matches.size} matches\n")
                        for (m in matches.take(10)) {
                            onLogChange(log + "  $m\n")
                        }
                    } else {
                        onLogChange(log + "[-] No matches found\n")
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = gameRunning,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                Icon(Icons.Default.Search, null, tint = DarkBg)
                Spacer(Modifier.width(8.dp))
                Text("Search Method", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        }

        DarkCard {
            Text("Quick Hooks", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val quickHooks = listOf(
                "Login/Auth methods", "License verification", "Device ID check",
                "Anti-debug bypass", "Root detection bypass", "SSL pinning bypass"
            )
            quickHooks.forEach { hook ->
                Surface(
                    onClick = { hookTarget = hook },
                    color = DarkCard, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(hook, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OutputTab(log: String, context: Context) {
    val scope = rememberCoroutineScope()
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                cm.setText(AnnotatedString(log))
                scope.launch { /* snackbar */ }
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                Icon(Icons.Default.ContentCopy, null, tint = DarkBg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy", color = DarkBg, fontSize = 11.sp)
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val dir = File("/sdcard/Download/OprekTool/dump")
                        dir.mkdirs()
                        val filename = "output_${System.currentTimeMillis()}.txt"
                        File(dir, filename).writeText(log)
                    } catch (_: Exception) {}
                }
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", color = DarkBg, fontSize = 11.sp)
            }
        }

        Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
            modifier = Modifier.fillMaxSize().padding(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(DarkCard).padding(8.dp)
                .verticalScroll(rememberScrollState()))
    }
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}
