@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*

data class ToolLink(val name: String, val desc: String, val url: String, val icon: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsDownloadScreen(navController: NavController) {
    val context = LocalContext.current

    val androidTools = listOf(
        ToolLink("Android SDK", "Android development SDK (cmdline-tools)", "https://developer.android.com/studio#command-line-tools-only", "📱", AccentGreen),
        ToolLink("Android NDK", "Native Development Kit for C/C++", "https://developer.android.com/ndk/downloads", "🔧", AccentCyan),
        ToolLink("Android Studio", "Full IDE for Android development", "https://developer.android.com/studio", "💡", AccentPurple),
        ToolLink("Build Tools", "aapt2, d8, apksigner, zipalign", "https://developer.android.com/studio/releases/build-tools", "🔨", AccentOrange),
    )

    val reverseEngTools = listOf(
        ToolLink("Ghidra", "Free reverse engineering tool (NSA)", "https://ghidra-sre.org/", "🔬", AccentGreen),
        ToolLink("radare2", "Reverse engineering framework", "https://rada.re/n/", "⚙️", AccentCyan),
        ToolLink("Capstone", "Disassembly engine (used by OprekTool)", "https://www.capstone-engine.org/", "📖", AccentPurple),
        ToolLink("Frida", "Dynamic instrumentation toolkit", "https://frida.re/", "🎯", AccentRed),
        ToolLink("Ghidrathon", "Ghidra + Python integration", "https://github.com/mandiant/Ghidrathon", "🐍", AccentGreen),
        ToolLink("Il2CppDumper", "Unity IL2CPP dumper", "https://github.com/Perfare/Il2CppDumper", "📦", AccentOrange),
    )

    val scriptingTools = listOf(
        ToolLink("Python", "Python programming language", "https://www.python.org/downloads/", "🐍", AccentGreen),
        ToolLink("Node.js", "JavaScript runtime (for scripting)", "https://nodejs.org/", "📦", AccentCyan),
        ToolLink("Lua", "Lua scripting language", "https://www.lua.org/download.html", "📜", AccentPurple),
        ToolLink("Termux", "Terminal emulator for Android", "https://f-droid.org/en/packages/com.termux/", "💻", AccentBlue),
    )

    val reverseResources = listOf(
        ToolLink("ELF Format", "Executable and Linkable Format spec", "https://en.wikipedia.org/wiki/Executable_and_Linkable_Format", "📖", AccentGreen),
        ToolLink("ARM64 Reference", "ARM Architecture Reference Manual", "https://developer.arm.com/documentation/ddi0602/latest", "📖", AccentCyan),
        ToolLink("Frida Docs", "Frida official documentation", "https://frida.re/docs/javascript-api/", "📖", AccentPurple),
        ToolLink("CTF Wiki", "Capture The Flag resources", "https://ctf-wiki.org/", "🏆", AccentOrange),
        ToolLink("awesome-embedded-security", "Curated list of embedded security tools", "https://github.com/hexsecs/awesome-embedded-security", "📚", AccentRed),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔗 Tools & Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // How to install section
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📥 How to Install Tools", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("Windows:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                    Text("• Use IDM (Internet Download Manager) for fast downloads", fontSize = 11.sp, color = TextPrimary)
                    Text("• Or use browser built-in download", fontSize = 11.sp, color = TextPrimary)
                    Text("• Install Android Studio → SDK Manager → install SDK/NDK", fontSize = 11.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Linux:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                    Text("• wget <url> or curl -O <url>", fontSize = 11.sp, color = TextPrimary)
                    Text("• sudo apt install build-essential", fontSize = 11.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("macOS:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                    Text("• brew install <tool>", fontSize = 11.sp, color = TextPrimary)
                }
            }

            // Android SDK/NDK
            SectionHeader("📱 Android SDK & NDK")
            androidTools.forEach { tool ->
                ToolCard(tool) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                }
            }

            // Reverse Engineering Tools
            SectionHeader("🔬 Reverse Engineering Tools")
            reverseEngTools.forEach { tool ->
                ToolCard(tool) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                }
            }

            // Scripting Languages
            SectionHeader("📜 Scripting Languages")
            scriptingTools.forEach { tool ->
                ToolCard(tool) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                }
            }

            // Learning Resources
            SectionHeader("📚 Learning Resources")
            reverseResources.forEach { tool ->
                ToolCard(tool) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
fun ToolCard(tool: ToolLink, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tool.icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tool.color)
                Text(tool.desc, fontSize = 11.sp, color = TextSecondary)
            }
            Icon(Icons.Filled.OpenInNew, "Open", tint = tool.color, modifier = Modifier.size(20.dp))
        }
    }
}
