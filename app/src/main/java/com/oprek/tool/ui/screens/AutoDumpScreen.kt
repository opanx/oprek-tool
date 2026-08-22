package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var selectedPkg by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf(0) } // 0=fast, 1=normal, 2=deep
    var rootOk by remember { mutableStateOf<Boolean?>(null) }

    val games = listOf(
        "com.mobile.legends" to "MLBB",
        "com.dts.freefiremax" to "FF MAX",
        "com.dts.freefireth" to "FF",
        "com.tencent.ig" to "PUBG",
        "com.miHoYo.GenshinImpact" to "Genshin",
        "com.supercell.clashofclans" to "COC",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.activision.callofduty.shooter" to "COD Mobile",
        "com.garena.game.codm" to "CODM",
        "com.moonton.magicrush" to "Magic Rush",
        "com.riotgames.league.teamfighttactics" to "TFT"
    )

    fun addLine(msg: String) { output = output + msg }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚀 Auto Dump", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("dump", output.joinToString("\n")))
                            Toast.makeText(context, "Copied ${output.size} lines!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/dump")
                            dir.mkdirs()
                            val name = if (selectedPkg.isNotEmpty()) selectedPkg.replace(".", "_") else "auto_dump"
                            val file = File(dir, "${name}_${System.currentTimeMillis()}.txt")
                            file.writeText(output.joinToString("\n"))
                            Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Game selector + scan mode
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🎮 Quick Select", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    // Game chips
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        games.take(6).forEach { (pkg, name) ->
                            FilterChip(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        games.drop(6).forEach { (pkg, name) ->
                            FilterChip(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = selectedPkg, onValueChange = { selectedPkg = it },
                        label = { Text("Package name (or custom)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp))

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("⚡ Fast", "📋 Normal", "🔬 Deep").forEachIndexed { idx, label ->
                            FilterChip(selected = scanMode == idx, onClick = { scanMode = idx },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.2f)))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (selectedPkg.isBlank()) {
                                addLine("[-] Select or enter a package name!")
                                return@Button
                            }
                            isRunning = true
                            output = emptyList()
                            scope.launch(Dispatchers.IO) {
                                addLine("🚀 Auto Dump v3 - No Limits Edition")
                                addLine("Package: $selectedPkg")
                                addLine("Scan mode: ${when(scanMode) { 0->"Fast"; 1->"Normal"; else->"Deep" }}")
                                addLine("")

                                // Check root
                                addLine("🔍 Checking root access...")
                                val rootCheck = checkRoot()
                                rootOk = rootCheck
                                if (!rootCheck) {
                                    addLine("❌ No root access! Install Magisk/KSU.")
                                    isRunning = false
                                    return@launch
                                }
                                addLine("✅ Root access confirmed")

                                // Find PID
                                addLine("\n🔍 Finding process...")
                                val pid = findPid(selectedPkg)
                                if (pid == null) {
                                    addLine("❌ Process not found. Is $selectedPkg running?")
                                    addLine("💡 Launch the game first, then come back here.")
                                    isRunning = false
                                    return@launch
                                }
                                addLine("✅ PID: $pid")

                                // Parse maps
                                addLine("\n📋 Parsing memory maps...")
                                val maps = parseMaps(pid)
                                addLine("   Found ${maps.size} memory regions")
                                val codeRegions = maps.filter { it.contains("r-xp") }
                                val dataRegions = maps.filter { it.contains("rw-p") }
                                val libRegions = maps.filter { it.contains(".so") }
                                addLine("   Code regions: ${codeRegions.size}")
                                addLine("   Data regions: ${dataRegions.size}")
                                addLine("   Libraries: ${libRegions.size}")

                                // Find libil2cpp
                                addLine("\n🎯 Searching for libil2cpp.so...")
                                val il2cppRegion = libRegions.find { it.contains("libil2cpp.so") }
                                if (il2cppRegion != null) {
                                    addLine("✅ Found: ${il2cppRegion.substringAfterLast(" ")}")
                                    val addrRange = il2cppRegion.substringBefore(" ")
                                    val startAddr = "0x${addrRange.substringBefore("-")}"
                                    val endAddr = "0x${addrRange.substringAfter("-")}"
                                    addLine("   Base: $startAddr | Size: ${calculateSize(addrRange)}")
                                } else {
                                    addLine("⚠️ libil2cpp.so not found in maps")
                                    addLine("   Available libs:")
                                    libRegions.take(10).forEach { addLine("     ${it.substringAfterLast(" ").trim()}") }
                                }

                                // Find global-metadata.dat
                                addLine("\n📦 Searching for global-metadata.dat...")
                                val metaRegion = dataRegions.find { readFromProcess(pid, it, 4).contentEquals(byteArrayOf(0xAF.toByte(), 0x1B, 0xF1, 0xFA)) }
                                if (metaRegion != null) {
                                    addLine("✅ Found global-metadata.dat (magic: 0xFAB11BAF)")
                                } else {
                                    addLine("⚠️ global-metadata.dat not found (may be encrypted)")
                                }

                                // Dump all regions
                                addLine("\n💾 Dumping memory regions...")
                                val saveDir = File(context.getExternalFilesDir(null), "dump/$selectedPkg")
                                saveDir.mkdirs()
                                var dumpCount = 0

                                for ((i, region) in maps.withIndex()) {
                                    progress = (i.toFloat() / maps.size)
                                    val addrRange = region.substringBefore(" ")
                                    val perms = region.substringAfter(" ").substringBefore(" ")
                                    val path = region.substringAfterLast(" ").trim()

                                    if (perms.contains("r") && !path.startsWith("[") && path.isNotBlank()) {
                                        try {
                                            val data = readFromProcess(pid, region, if (scanMode == 2) 4096 else if (scanMode == 1) 1024 else 256)
                                            if (data != null && data.isNotEmpty()) {
                                                val safeName = path.replace("/", "_").replace(" ", "_")
                                                val outFile = File(saveDir, "${safeName}_0x${addrRange.substringBefore("-")}.bin")
                                                outFile.writeBytes(data)
                                                dumpCount++
                                                if (dumpCount % 10 == 0) {
                                                    addLine("   Dumped $dumpCount regions...")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Skip unreadable regions
                                        }
                                    }
                                    // Yield to prevent ANR
                                    if (i % 50 == 0) kotlinx.coroutines.delay(10)
                                }

                                addLine("\n✅ Dump complete!")
                                addLine("   Total regions dumped: $dumpCount")
                                addLine("   Save directory: ${saveDir.absolutePath}")

                                // Try to generate dump.cs (IL2CPP)
                                addLine("\n📝 Generating IL2CPP dump...")
                                if (il2cppRegion != null) {
                                    addLine("   ℹ️ Full IL2CPP dump requires Il2CppDumper tool")
                                    addLine("   Use IL2CPP Dumper screen for metadata parsing")
                                }

                                addLine("\n🎉 Done! All output saved to:")
                                addLine("   ${saveDir.absolutePath}")
                                progress = 1f
                                isRunning = false
                            }
                        }, modifier = Modifier.weight(1f), enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AccentRed else AccentGreen)) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Dumping...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start Dump", fontSize = 12.sp)
                            }
                        }
                        if (isRunning) {
                            Button(onClick = { isRunning = false }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                                Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                            }
                        }
                    }
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = AccentGreen)
                    }
                }
            }

            // Output
            Card(Modifier.fillMaxWidth().weight(1f).padding(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 13.sp)
                        if (rootOk != null) {
                            Text(if (rootOk!!) "🟢 Root OK" else "🔴 No Root", fontSize = 10.sp, color = if (rootOk!!) AccentGreen else AccentRed)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🚀", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Select a game and tap Start Dump", color = TextSecondary, fontSize = 13.sp)
                                Text("Root required for memory dump", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn {
                            items(output) { line ->
                                val color = when {
                                    line.startsWith("✅") || line.startsWith("🎉") -> AccentGreen
                                    line.startsWith("❌") -> AccentRed
                                    line.startsWith("⚠️") -> AccentOrange
                                    line.startsWith("🎯") || line.startsWith("📦") -> AccentCyan
                                    else -> TextPrimary
                                }
                                Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }

            // Footer
            Text("© Panxcz & Freebuff | v3.0 No Limits", color = TextSecondary, fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// Helper functions - use Runtime.exec for root commands
private fun checkRoot(): Boolean {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = p.inputStream.bufferedReader().readText()
        p.waitFor()
        output.contains("uid=0")
    } catch (e: Exception) {
        false
    }
}

private fun findPid(pkg: String): String? {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof $pkg"))
        val output = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (output.isNotBlank()) output.split("\\s+".toRegex()).firstOrNull() else null
    } catch (e: Exception) { null }
}

private fun parseMaps(pid: String): List<String> {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/maps"))
        val lines = p.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
        p.waitFor()
        lines
    } catch (e: Exception) { emptyList() }
}

private fun readFromProcess(pid: String, region: String, size: Int): ByteArray? {
    return try {
        val addrRange = region.substringBefore(" ")
        val startAddr = "0x${addrRange.substringBefore("-")}"
        val cmd = "dd if=/proc/$pid/mem bs=1 count=$size skip=$startAddr 2>/dev/null"
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val data = p.inputStream.readBytes()
        p.waitFor()
        if (data.size > 0) data else null
    } catch (e: Exception) { null }
}

private fun calculateSize(addrRange: String): String {
    return try {
        val parts = addrRange.split("-")
        val start = parts[0].toLong(16)
        val end = parts[1].toLong(16)
        val size = end - start
        when {
            size < 1024 -> "${size}B"
            size < 1048576 -> "${size / 1024}KB"
            else -> "${"%.1f".format(size / 1048576.0)}MB"
        }
    } catch (e: Exception) { "?" }
}
