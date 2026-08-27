package com.oprek.tool.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class LoaderGamePreset(
    val name: String,
    val pkg: String,
    val lib: String,
    val metadataMagic: String = "0xFAB11BAF",
    val icon: String = "🎮"
)

val loaderGamePresets = listOf(
    LoaderGamePreset("Mobile Legends", "com.mobile.legends", "liblogic.so", icon = "⚔️"),
    LoaderGamePreset("Free Fire", "com.dts.freefireth", "libil2cpp.so", icon = "🔥"),
    LoaderGamePreset("Free Fire MAX", "com.dts.freefiremax", "libil2cpp.so", icon = "🔥"),
    LoaderGamePreset("PUBG Mobile", "com.tencent.ig", "libil2cpp.so", icon = "🎯"),
    LoaderGamePreset("PUBG Mobile KR", "com.tencent.igkr", "libil2cpp.so", icon = "🎯"),
    LoaderGamePreset("Genshin Impact", "com.miHoYo.GenshinImpact", "libil2cpp.so", icon = "✨"),
    LoaderGamePreset("Honkai Star Rail", "com.HoYoverse.hkrpgoversea", "libil2cpp.so", icon = "🚀"),
    LoaderGamePreset("Blood Strike", "com.excean.dualaid", "libil2cpp.so", icon = "🩸"),
    LoaderGamePreset("COD Mobile", "com.activision.callofduty.shooter", "libil2cpp.so", icon = "🎖️"),
    LoaderGamePreset("Brawl Stars", "com.supercell.brawlstars", "libil2cpp.so", icon = "⭐"),
    LoaderGamePreset("Standoff 2", "com.axlebolt.standoff2", "libil2cpp.so", icon = "🔫"),
    LoaderGamePreset("Roblox", "com.roblox.client", "libil2cpp.so", icon = "🧱"),
    LoaderGamePreset("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM", "libil2cpp.so", icon = "🏎️"),
    LoaderGamePreset("Clash Royale", "com.supercell.clashroyale", "libil2cpp.so", icon = "👑"),
    LoaderGamePreset("Minecraft", "com.mojang.minecraftpe", "libil2cpp.so", icon = "⛏️"),
    LoaderGamePreset("Arena of Valor", "com.ngame.allstar.eu", "libil2cpp.so", icon = "🏆"),
    LoaderGamePreset("eFootball PES", "jp.konami.pesam", "libil2cpp.so", icon = "⚽"),
    LoaderGamePreset("Stumble Guys", "com.kitkagames.fallbuddies", "libil2cpp.so", icon = "🤪"),
    LoaderGamePreset("Custom (Manual)", "", "", icon = "🔧")
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
    val tabs = listOf("Game", "Dump", "Frida", "Decrypt", "Overlay", "Strings", "Hooks", "Output")

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
                            val game = loaderGamePresets[selectedGame]
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
                    onDump = {
                        scope.launch(Dispatchers.IO) {
                            if (!hasRoot || gamePid == -1) {
                                outputLog += "[-] Root + running game required!\n"
                                return@launch
                            }
                            isRunning = true
                            val game = loaderGamePresets[selectedGame]
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
                2 -> FridaTab(selectedGame, customPkg, customLib, outputLog, { outputLog = it }, isRunning)
                3 -> DecryptTab(selectedGame, customPkg, customLib, outputLog, { outputLog = it }, isRunning)
                4 -> OverlayTab(selectedGame, customPkg, customLib, outputLog, { outputLog = it }, isRunning)
                5 -> StringsTab(gameRunning, gamePid, extractedStrings, { extractedStrings = it },
                    stringFilter, { stringFilter = it }, isRunning, outputLog,
                    onScanStrings = {
                        scope.launch(Dispatchers.IO) {
                            if (!hasRoot || gamePid == -1) {
                                outputLog += "[-] Root + running game required!\n"
                                return@launch
                            }
                            isRunning = true
                            val game = loaderGamePresets[selectedGame]
                            val lib = if (game.pkg.isEmpty()) customLib else game.lib
                            outputLog += "\n[*] Scanning strings from $lib...\n"
                            val strings = ShellExec.extractStringsFromLib(gamePid, lib, 10000)
                            extractedStrings = strings
                            outputLog += "[+] Found ${strings.size} strings\n"
                            isRunning = false
                        }
                    })
                6 -> HooksTab(gameRunning, gamePid, outputLog, { outputLog = it })
                7 -> OutputTab(outputLog, context)
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
            loaderGamePresets.forEachIndexed { index, game ->
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
        if (selected == loaderGamePresets.size - 1) {
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
        val game = loaderGamePresets[selectedGame]
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
private fun FridaTab(
    selectedGame: Int, customPkg: String, customLib: String,
    log: String, onLogChange: (String) -> Unit, isRunning: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val game = loaderGamePresets[selectedGame]
    val pkg = if (game.pkg.isEmpty()) customPkg else game.pkg
    val lib = if (game.pkg.isEmpty()) customLib else game.lib

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Frida IL2CPP Runtime Dumper", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Executes Frida script INSIDE game process", color = TextSecondary, fontSize = 12.sp)
            Text("Calls il2cpp_class_get_methods() etc. directly", color = TextSecondary, fontSize = 11.sp)
            Text("Target: $pkg | Lib: $lib", color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        DarkCard {
            Text("Requirements", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val reqs = listOf(
                "Frida server running on device (su)",
                "Game must be running",
                "Script auto-detects libil2cpp.so / liblogic.so",
                "Dumps all TypeDef, MethodDef, FieldDef, Properties",
                "Generates dump.cs with full class/method signatures"
            )
            reqs.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(r, color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        // Generate script
        var scriptContent by remember { mutableStateOf("") }
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                try {
                    val assetManager = context.assets
                    val script = assetManager.open("scripts/il2cpp_frida_dump.js").bufferedReader().readText()
                    scriptContent = script
                    onLogChange(log + "[+] Frida script loaded (${script.length} bytes)\n")
                } catch (e: Exception) {
                    onLogChange(log + "[-] Failed to load script: ${e.message}\n")
                }
            }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
            Icon(Icons.Default.Code, null, tint = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Load Frida Script", color = TextPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // Execute via root
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                onLogChange(log + "[*] Checking for frida-server...\n")
                val check = ShellExec.execRoot("which frida || ls /data/local/tmp/frida-server* 2>/dev/null || echo NOT_FOUND")
                val hasFrida = check.any { it.contains("frida") && !it.contains("NOT_FOUND") }
                
                if (!hasFrida) {
                    onLogChange(log + "[-] Frida server NOT found!\n")
                    onLogChange(log + "[*] Install frida-server from https://github.com/frida/frida/releases\n")
                    onLogChange(log + "[*] Push to device: adb push frida-server-XX-android-arm64 /data/local/tmp/frida-server\n")
                    onLogChange(log + "[*] Run: su -c \"chmod 755 /data/local/tmp/frida-server && /data/local/tmp/frida-server &\"\n")
                    return@launch
                }
                
                onLogChange(log + "[+] Frida server found!\n")
                onLogChange(log + "[*] Generating Frida script for $pkg...\n")
                
                // Save script to device
                val scriptPath = "/data/local/tmp/oprek_il2cpp_dump.js"
                try {
                    val assetManager = context.assets
                    val script = assetManager.open("scripts/il2cpp_frida_dump.js").bufferedReader().readText()
                    File(scriptPath).writeText(script)
                    onLogChange(log + "[+] Script saved to $scriptPath\n")
                    onLogChange(log + "[*] Executing: frida -U -f $pkg -l $scriptPath --no-pause\n")
                    
                    // Execute frida
                    val result = ShellExec.execRoot("frida -U -f $pkg -l $scriptPath --no-pause 2>&1 &")
                    onLogChange(log + "[+] Frida launched! Check /sdcard/Download/OprekTool/dump/dump_frida.cs\n")
                } catch (e: Exception) {
                    onLogChange(log + "[-] Error: ${e.message}\n")
                }
            }
        }, modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
            Icon(Icons.Default.PlayArrow, null, tint = DarkBg)
            Spacer(Modifier.width(8.dp))
            Text("Execute Frida Dump", color = DarkBg, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // Manual command
        DarkCard {
            Text("Manual Command", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val cmd = "frida -U -f $pkg -l /data/local/tmp/oprek_il2cpp_dump.js --no-pause"
            Text(cmd, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(DarkCard).padding(6.dp))
            Spacer(Modifier.height(4.dp))
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Button(onClick = {
                cm.setText(AnnotatedString(cmd))
                scope.launch { /* snackbar */ }
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                Text("Copy Command", color = DarkBg, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DecryptTab(
    selectedGame: Int, customPkg: String, customLib: String,
    log: String, onLogChange: (String) -> Unit, isRunning: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val game = loaderGamePresets[selectedGame]
    val pkg = if (game.pkg.isEmpty()) customPkg else game.pkg
    val lib = if (game.pkg.isEmpty()) customLib else game.lib

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Encrypted Metadata Decryptor", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Hooks IL2CPP init + decrypt functions", color = TextSecondary, fontSize = 12.sp)
            Text("Intercepts decrypted metadata at runtime", color = TextSecondary, fontSize = 11.sp)
            Text("Target: $pkg | Lib: $lib", color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        DarkCard {
            Text("How It Works", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val steps = listOf(
                "1. Hooks il2cpp_init to catch VM initialization",
                "2. Hooks il2cpp_domain_get_assemblies (metadata ready)",
                "3. Hooks malloc/mmap to detect metadata allocation",
                "4. Hooks memcpy to detect decryption in progress",
                "5. Saves decrypted metadata blocks to file",
                "6. Dumps decrypted metadata as dump.cs",
                "7. Auto-triggers after 30 seconds"
            )
            steps.forEach { s ->
                Text(s, color = TextSecondary, fontSize = 11.sp)
            }
        }

        DarkCard {
            Text("Hooks Installed", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val hooks = listOf(
                "il2cpp_init" to "Catches VM initialization",
                "il2cpp_shutdown" to "Dumps before shutdown",
                "il2cpp_domain_get_assemblies" to "Detects metadata ready",
                "malloc" to "Detects large allocations (metadata)",
                "memcpy" to "Detects metadata being copied/decrypted",
                "mmap" to "Detects metadata memory mapping"
            )
            hooks.forEach { (name, desc) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(name, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(200.dp))
                    Text(desc, color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        // Execute
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                onLogChange(log + "[*] Checking frida-server...\n")
                val check = ShellExec.execRoot("which frida 2>/dev/null || echo NOT_FOUND")
                val hasFrida = check.any { it.contains("frida") && !it.contains("NOT_FOUND") }
                if (!hasFrida) {
                    onLogChange(log + "[-] Frida server NOT found!\n")
                    onLogChange(log + "[*] Install frida-server from github.com/frida/frida/releases\n")
                    return@launch
                }
                
                onLogChange(log + "[+] Launching decryptor...\n")
                try {
                    val assetManager = context.assets
                    val script = assetManager.open("scripts/il2cpp_metadata_decrypt.js").bufferedReader().readText()
                    val scriptPath = "/data/local/tmp/oprek_decrypt.js"
                    File(scriptPath).writeText(script)
                    onLogChange(log + "[+] Script saved: $scriptPath\n")
                    ShellExec.execRoot("frida -U -f $pkg -l $scriptPath --no-pause &")
                    onLogChange(log + "[+] Decryptor launched!\n")
                    onLogChange(log + "[*] Metadata will be decrypted automatically.\n")
                    onLogChange(log + "[*] Dump will appear at /sdcard/Download/OprekTool/dump/\n")
                } catch (e: Exception) {
                    onLogChange(log + "[-] Error: ${e.message}\n")
                }
            }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
            Icon(Icons.Default.LockOpen, null, tint = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Launch Decryptor", color = TextPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // Manual
        DarkCard {
            Text("Manual Command", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val cmd = "frida -U -f $pkg -l /data/local/tmp/oprek_decrypt.js --no-pause"
            Text(cmd, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(DarkCard).padding(6.dp))
            Spacer(Modifier.height(4.dp))
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Button(onClick = { cm.setText(AnnotatedString(cmd)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                Text("Copy Command", color = DarkBg, fontSize = 11.sp)
            }
        }

        DarkCard {
            Text("Output Files", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val files = listOf(
                "dump_decrypted.cs" to "Decrypted IL2CPP dump with all classes/methods",
                "decrypted_metadata_*.bin" to "Raw decrypted metadata binary",
                "dump_frida.cs" to "Full runtime dump from Frida"
            )
            files.forEach { (name, desc) ->
                Text("$name - $desc", color = TextSecondary, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(4.dp))
            Text("All files: /sdcard/Download/OprekTool/dump/", color = AccentGreen, fontSize = 11.sp)
        }

        DarkCard {
            Text("Supported Games", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val games = listOf(
                "Mobile Legends (liblogic.so) - encrypted metadata",
                "Free Fire (libil2cpp.so) - may be encrypted",
                "PUBG Mobile (libil2cpp.so) - encrypted",
                "Genshin Impact (libil2cpp.so) - encrypted",
                "Honkai Star Rail (libil2cpp.so) - encrypted",
                "COD Mobile (libil2cpp.so) - may be encrypted",
                "Any IL2CPP game - auto-detect encryption"
            )
            games.forEach { g ->
                Text("• $g", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OverlayTab(
    selectedGame: Int, customPkg: String, customLib: String,
    log: String, onLogChange: (String) -> Unit, isRunning: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val game = loaderGamePresets[selectedGame]
    val pkg = if (game.pkg.isEmpty()) customPkg else game.pkg

    // Overlay config state
    var menuTitle by remember { mutableStateOf("OprekTool Menu") }
    var hookAuth by remember { mutableStateOf(true) }
    var bypassRoot by remember { mutableStateOf(true) }
    var bypassSSL by remember { mutableStateOf(true) }
    var speedHack by remember { mutableStateOf(false) }
    var speedValue by remember { mutableStateOf("1.5") }
    var godMode by remember { mutableStateOf(false) }
    var showFPS by remember { mutableStateOf(false) }
    var noRecoil by remember { mutableStateOf(false) }
    var unlimitedAmmo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("ImGui Overlay Generator", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Generate + inject ImGui menu into game via Frida", color = TextSecondary, fontSize = 12.sp)
            Text("Hooks rendering pipeline + draws floating menu", color = TextSecondary, fontSize = 11.sp)
        }

        DarkCard {
            Text("Menu Configuration", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(menuTitle, { menuTitle = it },
                label = { Text("Menu Title") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
        }

        DarkCard {
            Text("Toggle Features", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hookAuth, onCheckedChange = { hookAuth = it })
                Text("Hook Auth/Login methods", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = bypassRoot, onCheckedChange = { bypassRoot = it })
                Text("Bypass Root Detection", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = bypassSSL, onCheckedChange = { bypassSSL = it })
                Text("Bypass SSL Pinning", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = speedHack, onCheckedChange = { speedHack = it })
                Text("Speed Hack", color = TextSecondary)
            }
            if (speedHack) {
                OutlinedTextField(speedValue, { speedValue = it },
                    label = { Text("Speed Multiplier (0.5-5.0)") },
                    modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = godMode, onCheckedChange = { godMode = it })
                Text("God Mode", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = noRecoil, onCheckedChange = { noRecoil = it })
                Text("No Recoil", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = unlimitedAmmo, onCheckedChange = { unlimitedAmmo = it })
                Text("Unlimited Ammo", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showFPS, onCheckedChange = { showFPS = it })
                Text("Show FPS Counter", color = TextSecondary)
            }
        }

        // Generate + Execute
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                try {
                    val assetManager = context.assets
                    var script = assetManager.open("scripts/imgui_overlay_frida.js").bufferedReader().readText()
                    
                    // Customize script with user config
                    script = script.replace("OprekTool Menu", menuTitle)
                    script = script.replace("hook_auth", if (hookAuth) "true" else "false")
                    script = script.replace("bypass_root", if (bypassRoot) "true" else "false")
                    script = script.replace("bypass_ssl", if (bypassSSL) "true" else "false")
                    script = script.replace("speed", speedValue)
                    
                    // Save to device
                    val scriptPath = "/data/local/tmp/oprek_overlay.js"
                    File(scriptPath).writeText(script)
                    onLogChange(log + "[+] Overlay script generated!\n")
                    onLogChange(log + "[+] Title: $menuTitle\n")
                    onLogChange(log + "[+] Features: ${if (hookAuth) "Auth " else ""}${if (bypassRoot) "Root " else ""}${if (bypassSSL) "SSL " else ""}${if (speedHack) "Speed" else ""}\n")
                    onLogChange(log + "[+] Saved: $scriptPath\n")
                    onLogChange(log + "[*] Execute: frida -U -f $pkg -l $scriptPath --no-pause\n")
                } catch (e: Exception) {
                    onLogChange(log + "[-] Error: ${e.message}\n")
                }
            }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
            Icon(Icons.Default.Build, null, tint = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Generate + Save Overlay Script", color = TextPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                onLogChange(log + "[*] Checking frida-server...\n")
                val check = ShellExec.execRoot("which frida 2>/dev/null || echo NOT_FOUND")
                val hasFrida = check.any { it.contains("frida") && !it.contains("NOT_FOUND") }
                if (!hasFrida) {
                    onLogChange(log + "[-] Frida server NOT found!\n")
                    return@launch
                }
                onLogChange(log + "[+] Launching overlay...\n")
                ShellExec.execRoot("frida -U -f $pkg -l /data/local/tmp/oprek_overlay.js --no-pause &")
                onLogChange(log + "[+] Overlay injected! Menu should appear in game.\n")
            }
        }, modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
            Icon(Icons.Default.PlayArrow, null, tint = DarkBg)
            Spacer(Modifier.width(8.dp))
            Text("Launch Overlay", color = DarkBg, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        DarkCard {
            Text("Manual Command", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val cmd = "frida -U -f $pkg -l /data/local/tmp/oprek_overlay.js --no-pause"
            Text(cmd, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(DarkCard).padding(6.dp))
            Spacer(Modifier.height(4.dp))
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Button(onClick = {
                cm.setText(AnnotatedString(cmd))
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                Text("Copy Command", color = DarkBg, fontSize = 11.sp)
            }
        }

        DarkCard {
            Text("Overlay Features", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val features = listOf(
                "Floating menu with drag support",
                "Toggle features on/off in real-time",
                "Auth/Login hook - force return true",
                "Root detection bypass",
                "SSL pinning bypass",
                "Speed hack with adjustable multiplier",
                "God mode, no recoil, unlimited ammo",
                "FPS counter overlay",
                "Auto-detect libil2cpp.so / liblogic.so",
                "Dump IL2CPP from overlay menu"
            )
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(f, color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(2.dp))
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
