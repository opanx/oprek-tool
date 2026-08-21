package com.oprek.tool.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.core.FileType
import com.oprek.tool.ui.theme.*
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MainViewModel) {
    val context = LocalContext.current
    val currentFile by vm.currentFile.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            vm.loadFile(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("OprekTool", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Reverse Engineering Toolkit", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // File info card
            currentFile?.let { info ->
                FileInfoCard(info)
            } ?: run {
                // Welcome + file picker
                HeroSection { filePicker.launch(arrayOf("*/*")) }
            }

            // Status
            if (statusMessage.isNotEmpty()) {
                StatusBanner(statusMessage) { vm.clearStatus() }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = AccentGreen
                )
            }

            Spacer(Modifier.height(16.dp))

            // Tool grid
            Text("  Tools", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            val tools = listOf(
                ToolItem("Hex Viewer", "View & edit raw bytes", Icons.Outlined.Code, AccentGreen, "hex"),
                ToolItem("Strings", "Extract readable text", Icons.Outlined.TextSnippet, AccentBlue, "strings"),
                ToolItem("Disassembler", "ARM64/x86 disasm", Icons.Outlined.BugReport, AccentPurple, "disasm"),
                ToolItem("ELF Info", "Parse ELF headers", Icons.Outlined.Memory, AccentCyan, "elf"),
                ToolItem("APK Info", "Analyze APK structure", Icons.Outlined.Apps, AccentOrange, "apk"),
                ToolItem("Android Tools", "DEX, classes, Smali", Icons.Outlined.PhoneAndroid, AccentGreen, "android"),
                ToolItem("Patch Editor", "Binary patching tool", Icons.Outlined.Build, AccentRed, "patch"),
                ToolItem("Adv. Patch", "NOP/RET/String patch", Icons.Outlined.Bolt, AccentPurple, "advpatch"),
                ToolItem("Deobfuscate", "Decode/decrypt strings", Icons.Outlined.Key, AccentCyan, "deobfuscate"),
                ToolItem("Obfuscate", "Encode/encrypt strings", Icons.Outlined.Lock, AccentOrange, "obfuscate"),
                ToolItem("Frida Hook", "Generate hook scripts", Icons.Outlined.Code, AccentGreen, "frida"),
                ToolItem("Anti-Debug", "Detect debuggers", Icons.Outlined.Shield, AccentRed, "antidebug"),
                ToolItem("Hash Calculator", "MD5/SHA/CRC32", Icons.Outlined.Security, AccentOrange, "hash"),
                ToolItem("Key Generator", "Generate random keys", Icons.Outlined.VpnKey, AccentPurple, "keygen"),
                ToolItem("Base64/Hex", "Encode/decode strings", Icons.Outlined.Transform, AccentCyan, "base64"),
                ToolItem("Diff Tool", "Compare two files", Icons.Outlined.Compare, AccentGreen, "diff"),
                ToolItem("Manifest Reader", "APK permissions", Icons.Outlined.Description, AccentBlue, "manifest"),
                ToolItem("Bookmarks", "Save important offsets", Icons.Outlined.Bookmark, AccentOrange, "bookmark"),
                ToolItem("Export Report", "Save analysis report", Icons.Outlined.Share, AccentPurple, "export"),
                ToolItem("Recent Files", "History of opened files", Icons.Outlined.History, AccentCyan, "recent"),
                ToolItem("Memory Analyzer", "Entropy, packer detect", Icons.Outlined.Analytics, AccentRed, "memory"),
                ToolItem("Logcat", "Capture Android logs", Icons.Outlined.List, AccentGreen, "logcat"),
                ToolItem("Hex Copy", "Export bytes as C/Python", Icons.Outlined.ContentCopy, AccentPurple, "hexcopy"),
                ToolItem("ELF Symbols", "Symbol table + dynamic", Icons.Outlined.DataObject, AccentCyan, "elfsymbol"),
                ToolItem("Hook Generator", "LD_PRELOAD + trampoline", Icons.Outlined.Link, AccentOrange, "inlinehook"),
                ToolItem("Shell Script", "Parse + extract binary", Icons.Outlined.Description, AccentGreen, "shellscript"),
                ToolItem("ELF Full Header", "All ELF fields", Icons.Outlined.DataObject, AccentCyan, "elfheader"),
                ToolItem("Packer Detect", "UPX/Themida/OLLMV", Icons.Outlined.Security, AccentRed, "packer"),
                ToolItem("Shell Patcher", "Edit URLs/keys/commands", Icons.Outlined.Edit, AccentOrange, "shellpatch"),
                ToolItem("Memory Dump", "Analyze raw memory dump", Icons.Outlined.Storage, AccentCyan, "memdump"),
                ToolItem("Lua Analyzer", "Parse .lua scripts", Icons.Outlined.Code, AccentGreen, "lua"),
                ToolItem("Pak Archive", "Analyze .pak/.paks", Icons.Outlined.Archive, AccentOrange, "pak"),
                ToolItem("File Info", "Hash, magic, metadata", Icons.Outlined.Info, AccentBlue, "info"),
                ToolItem("Terminal", "Run shell commands", Icons.Outlined.Terminal, AccentRed, "terminal"),
                // === NEW v4 TOOLS ===
                ToolItem("Program Headers", "PT_LOAD/PT_DYNAMIC viewer", Icons.Outlined.ViewList, AccentBlue, "proghdr"),
                ToolItem("Section Headers", ".text/.data/.symtab viewer", Icons.Outlined.ViewModule, AccentCyan, "sechdr"),
                ToolItem("Symbol Table", "Full .symtab/.dynsym", Icons.Outlined.DataObject, AccentGreen, "symtable"),
                ToolItem("Dynamic Section", "DT_NEEDED, DT_SONAME", Icons.Outlined.Settings, AccentOrange, "dynamic"),
                ToolItem("Relocations", "R_ARM/R_AARCH64 relocs", Icons.Outlined.SwapHoriz, AccentPurple, "reloc"),
                ToolItem("GOT / PLT", "GOT entries + PLT stubs", Icons.Outlined.AccountTree, AccentRed, "gotplt"),
                ToolItem("Shell Deobfuscate", "Decode shell obfuscation", Icons.Outlined.FmdBad, AccentOrange, "shelldeob"),
                ToolItem("Disasm Advanced", "ARM64/x86 with decode", Icons.Outlined.BugReport, AccentPurple, "advdisasm"),
                ToolItem("XREF Viewer", "Cross-reference finder", Icons.Outlined.CallSplit, AccentCyan, "xref"),
                ToolItem("Patch Instruction", "NOP/RET/JMP patcher", Icons.Outlined.Build, AccentRed, "patchinsn"),
                ToolItem("Patch Branch", "Conditional → NOP/JMP", Icons.Outlined.TrendingDown, AccentOrange, "patchbranch"),
                ToolItem("Auto Patch Login", "Login bypass auto-scan", Icons.Outlined.LockOpen, AccentRed, "autologin"),
                ToolItem("Patch String", "Search & replace string", Icons.Outlined.FindReplace, AccentCyan, "patchstring"),
                ToolItem("XOR Brute Force", "Brute-force XOR key", Icons.Outlined.Key, AccentPurple, "xorbrute"),
                ToolItem("Patch Anti-Debug", "NOP ptrace/frida checks", Icons.Outlined.Shield, AccentRed, "patchantidebug"),
                ToolItem("Unpacker", "UPX/Themida/entropy", Icons.Outlined.Unarchive, AccentOrange, "unpacker"),
                ToolItem("Function List", "All functions + filter", Icons.Outlined.Functions, AccentGreen, "funclist"),
                ToolItem("IDA Strings", "String window + type", Icons.Outlined.TextFields, AccentBlue, "idaststrings"),
                ToolItem("Entropy Analyzer", "Detect encryption/packing", Icons.Outlined.Analytics, AccentRed, "entropy"),
                ToolItem("String Encryptor", "XOR/AES/ROT13 encode", Icons.Outlined.EnhancedEncryption, AccentPurple, "strencrypt"),
                ToolItem("Session Manager", "Save/load analysis state", Icons.Outlined.Save, AccentCyan, "session"),
                ToolItem("Encrypt Tool", "10 encrypt methods", Icons.Outlined.Lock, AccentGreen, "encrypt"),
                ToolItem("Decrypt Tool", "10 decrypt + auto-detect", Icons.Outlined.LockOpen, AccentRed, "decrypt"),
                ToolItem("Pseudo-C Decompiler", "Basic decompilation", Icons.Outlined.Code, AccentPurple, "decompiler"),
                ToolItem("Control Flow Graph", "CFG visualization", Icons.Outlined.AccountTree, AccentCyan, "cfg"),
                ToolItem("Frida Script Lib", "15+ pre-built scripts", Icons.Outlined.Description, AccentGreen, "fridalib"),
                ToolItem("Manifest Patcher", "Edit AndroidManifest", Icons.Outlined.Edit, AccentOrange, "manifestpatch"),
                ToolItem("DEX → Smali", "Convert DEX to Smali", Icons.Outlined.Transform, AccentPurple, "smali"),
                ToolItem("Multi-File Compare", "Compare 3+ files", Icons.Outlined.Compare, AccentCyan, "multidiff"),
                ToolItem("Script Engine", "IDC-like scripting", Icons.Outlined.Code, AccentPurple, "scripting"),
                ToolItem("Python Script", "Deobfuscate/Encrypt/Decrypt", Icons.Outlined.Code, AccentGreen, "python"),
                ToolItem("Tools & Downloads", "SDK/NDK/RE tools links", Icons.Outlined.Link, AccentCyan, "toolsdl"),
                ToolItem("Script Engine", "JavaScript runtime", Icons.Outlined.Code, AccentCyan, "scriptengine"),
                ToolItem("APK Signer", "Sign/Verify APK", Icons.Outlined.Security, AccentOrange, "apksigner"),
                ToolItem("Native Lib Analyzer", "Deep .so/ELF analysis", Icons.Outlined.Memory, AccentPurple, "nativelib"),
                ToolItem("IL2CPP Dumper", "Dump libil2cpp.so metadata", Icons.Outlined.BugReport, AccentCyan, "il2cpp"),
                ToolItem("DEX Dumper", "Extract DEX from APK/process", Icons.Default.Inventory2, AccentOrange, "dexdump"),
                ToolItem("🚀 Auto Dump", "One-click root game dump", Icons.Default.Rocket, AccentRed, "autodump"),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((tools.size / 2 + tools.size % 2) * 100).dp + ((tools.size / 2) * 8).dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tools) { tool ->
                    ToolCard(tool) {
                        when (tool.route) {
                            "search" -> navController.navigate("search?query=")
                            else -> navController.navigate(tool.route)
                        }
                    }
                }
            }

            // Copyright + Owner Info
            Spacer(Modifier.height(16.dp))
            val ctx = LocalContext.current
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚡ OprekTool v2.0", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(4.dp))
                    Text("© Panxcz & Freebuff", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Owner:", fontSize = 10.sp, color = TextMuted)
                    Text("@Gk_Gene", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/Gk_Gene"))
                            ctx.startActivity(i)
                        })
                    Spacer(Modifier.height(4.dp))
                    Text("Channels:", fontSize = 10.sp, color = TextMuted)
                    Text("t.me/kembungjir", fontSize = 11.sp, color = AccentBlue,
                        modifier = Modifier.clickable {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/kembungjir"))
                            ctx.startActivity(i)
                        })
                    Text("t.me/lazy_fat_catt", fontSize = 11.sp, color = AccentBlue,
                        modifier = Modifier.clickable {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/lazy_fat_catt"))
                            ctx.startActivity(i)
                        })
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeroSection(onPickFile: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔬", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Reverse Engineering Toolkit", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Text("Analyze .sh, .apk, .so, .elf, .bin files", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPickFile,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open File", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FileInfoCard(info: com.oprek.tool.core.FileInfo) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeIcon = when (info.type) {
                    FileType.ELF, FileType.SO -> "📦"
                    FileType.APK -> "📱"
                    FileType.SH -> "📜"
                    FileType.BIN -> "💾"
                    else -> "📄"
                }
                Text(typeIcon, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text(info.type.name + " • " + formatSize(info.size), fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            InfoRow("Magic", info.magic)
            InfoRow("MD5", info.md5)
            InfoRow("SHA256", info.sha256.take(32) + "...")
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 11.sp, color = AccentCyan, fontFamily = FontFamily.Monospace,
            maxLines = 1, modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
}

@Composable
fun StatusBanner(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ", color = AccentGreen, fontWeight = FontWeight.Bold)
            Text(message, fontSize = 12.sp, color = AccentGreen, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = AccentGreen)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(90.dp)
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(tool.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Text(tool.desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

data class ToolItem(val name: String, val desc: String, val icon: ImageVector, val color: Color, val route: String)

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1048576 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / 1048576.0)}MB"
}
