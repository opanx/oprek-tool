package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

data class LuaFunction(val name: String, val line: Int, val params: String)
data class LuaObfuscated(val offset: Int, val raw: String, val type: String, val decoded: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaAnalyzerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var rawContent by remember { mutableStateOf("") }
    var functions by remember { mutableStateOf<List<LuaFunction>>(emptyList()) }
    var strings by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var obfuscated by remember { mutableStateOf<List<LuaObfuscated>>(emptyList()) }
    var globals by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val rev = SharedFileState.revision

    LaunchedEffect(rev) {
        val file = SharedFileState.findFile(context) ?: return@LaunchedEffect
        if (!file.name.endsWith(".lua") && !file.name.endsWith(".luac") && !file.name.endsWith(".luajit")) return@LaunchedEffect
        rawContent = withContext(Dispatchers.IO) { file.readText() }
        val lines = rawContent.lines()

        // Extract functions
        val funcs = mutableListOf<LuaFunction>()
        val funcRegex = Regex("""function\s+([a-zA-Z_][\w.]*)\s*\(([^)]*)\)""")
        for ((idx, line) in lines.withIndex()) {
            funcRegex.find(line)?.let { m ->
                funcs.add(LuaFunction(m.groupValues[1], idx + 1, m.groupValues[2]))
            }
        }
        // Local functions
        val localFuncRegex = Regex("""local\s+function\s+([a-zA-Z_]\w*)\s*\(([^)]*)\)""")
        for ((idx, line) in lines.withIndex()) {
            localFuncRegex.find(line)?.let { m ->
                funcs.add(LuaFunction("local:" + m.groupValues[1], idx + 1, m.groupValues[2]))
            }
        }
        functions = funcs

        // Extract strings
        val strs = mutableListOf<Pair<Int, String>>()
        val strRegex = Regex("""["']([^"']{4,})["']""")
        for ((idx, line) in lines.withIndex()) {
            strRegex.findAll(line).forEach { m -> strs.add(idx + 1 to m.groupValues[1]) }
            if (strs.size >= 500) break
        }
        strings = strs

        // Detect obfuscation
        val obs = mutableListOf<LuaObfuscated>()
        val b64Regex = Regex("""["']([A-Za-z0-9+/]{20,}={0,2})["']""")
        for ((idx, line) in lines.withIndex()) {
            b64Regex.findAll(line).forEach { m ->
                try {
                    val dec = android.util.Base64.decode(m.groupValues[1], android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
                    if (dec.any { it.code in 0x20..0x7E }) obs.add(LuaObfuscated(idx, m.groupValues[1], "Base64", dec))
                } catch (_: Exception) {}
            }
        }
        val hexRegex = Regex("""["']([0-9A-Fa-f]{16,})["']""")
        for ((idx, line) in lines.withIndex()) {
            hexRegex.findAll(line).forEach { m ->
                val dec = m.groupValues[1].chunked(2).mapNotNull { it.toIntOrNull(16)?.toChar() }.joinToString("")
                if (dec.any { it.code in 0x20..0x7E }) obs.add(LuaObfuscated(idx, m.groupValues[1], "Hex", dec))
            }
        }
        val xorRegex = Regex("""load\s*\(\s*["'].*\\x[0-9a-fA-F]{2}""")
        for ((idx, line) in lines.withIndex()) {
            if (xorRegex.containsMatchIn(line)) obs.add(LuaObfuscated(idx, line.trim().take(60), "load()+escape", "Encoded bytecode"))
        }
        obfuscated = obs

        // Extract globals
        val globalRegex = Regex("""([A-Z][A-Z_0-9]{2,})\s*=""")
        globals = lines.mapNotNull { globalRegex.find(it)?.groupValues?.get(1) }.distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🌙 Lua Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("lua", rawContent)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        if (rawContent.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Open a .lua file to analyze", color = TextSecondary) }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Info
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lua Script", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentCyan)
                            Text("${rawContent.lines().size} lines • ${rawContent.length} bytes", fontSize = 12.sp, color = TextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill("Funcs", "${functions.size}", AccentGreen)
                            InfoPill("Strings", "${strings.size}", AccentOrange)
                            InfoPill("Obf", "${obfuscated.size}", AccentRed)
                        }
                    }
                }
                // Tabs
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Functions" to functions.size, "Strings" to strings.size, "Obfuscated" to obfuscated.size, "Globals" to globals.size).forEachIndexed { idx, (name, count) ->
                        FilterChip(selected = selectedTab == idx, onClick = { selectedTab = idx }, label = { Text("$name ($count)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f)))
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> LazyColumn(Modifier.padding(12.dp)) {
                        itemsIndexed(functions) { _, f ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(DarkSurface).padding(8.dp)) {
                                Text("${f.line}", fontSize = 10.sp, color = TextMuted, modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, fontWeight = FontWeight.Bold)
                                    if (f.params.isNotEmpty()) Text("(${f.params})", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
                                }
                            }
                        }
                    }
                    1 -> LazyColumn(Modifier.padding(12.dp)) {
                        itemsIndexed(strings.take(200)) { _, (line, str) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).background(DarkSurface).padding(8.dp)) {
                                Text("L$line", fontSize = 9.sp, color = TextMuted, modifier = Modifier.width(40.dp))
                                Text(str, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentOrange, maxLines = 1)
                            }
                        }
                    }
                    2 -> LazyColumn(Modifier.padding(12.dp)) {
                        itemsIndexed(obfuscated) { _, obs ->
                            val col = when(obs.type) { "Base64" -> AccentCyan; "Hex" -> AccentGreen; else -> AccentRed }
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(DarkSurface).padding(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${obs.type} (L${obs.offset})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col)
                                    Text("Raw: ${obs.raw.take(50)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, maxLines = 1)
                                    Text("Dec: ${obs.decoded.take(50)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, maxLines = 1)
                                }
                                IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("dec", obs.decoded)) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = AccentGreen)
                                }
                            }
                        }
                    }
                    3 -> LazyColumn(Modifier.padding(12.dp)) {
                        itemsIndexed(globals) { _, g -> Text(g, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentPurple, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 8.sp, color = TextMuted)

    }
}
