@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*

data class NavToolItem(val name: String, val route: String, val icon: ImageVector, val desc: String = "")
data class NavToolCategory(val name: String, val icon: ImageVector, val tools: List<NavToolItem>, val color: Color)

val toolCategories = listOf(
    NavToolCategory("🔍 Analysis", Icons.Default.Analytics, listOf(
        NavToolItem("ELF Analyzer", "elf", Icons.Default.Memory),
        NavToolItem("APK Analyzer", "apkinfo", Icons.Default.Info),
        NavToolItem("Decompiler", "decompiler", Icons.Default.Code),
        NavToolItem("Disassembler", "disasm", Icons.Default.Terminal),
        NavToolItem("Advanced Disasm", "advdisasm", Icons.Default.Build),
        NavToolItem("GOT/PLT", "gotplt", Icons.Default.Link),
        NavToolItem("Dynamic Section", "dynamic", Icons.Default.DynamicForm),
        NavToolItem("Program Headers", "phdr", Icons.Default.ViewList),
        NavToolItem("Section Headers", "shdr", Icons.Default.List),
        NavToolItem("Relocations", "reloc", Icons.Default.Autorenew),
        NavToolItem("Function List", "functions", Icons.Default.Functions),
        NavToolItem("Memory Scanner", "memscan", Icons.Default.Search),
        NavToolItem("Signature Scanner", "sigscan", Icons.Default.Fingerprint),
        NavToolItem("Vulnerability Scanner", "vulnscan", Icons.Default.BugReport),
        NavToolItem("Hex Viewer", "hex", Icons.Default.GridOn),
        NavToolItem("Binary Modifier", "binmod", Icons.Default.Build),
        NavToolItem("Multi-Arch Analyzer", "multiarch", Icons.Default.Memory),
        NavToolItem("Resource Scanner", "resscan", Icons.Default.Search),
        NavToolItem("Binary Patcher", "binpatch", Icons.Default.Build),
        NavToolItem("Binary Diff", "bindiff", Icons.Default.Compare),
        NavToolItem("Entropy Map", "entmap", Icons.Default.Analytics),
        NavToolItem("Symbol Resolver", "symres", Icons.Default.DataObject),
        NavToolItem("OFRAK Integration", "ofrak", Icons.Default.Terminal),
        NavToolItem(".deb Analyzer", "deb", Icons.Default.Archive),
        NavToolItem("Firmware Analyzer", "firmware", Icons.Default.Memory),
    ), AccentCyan),
    NavToolCategory("🎯 Auto Dump", Icons.Default.Storage, listOf(
        NavToolItem("Auto Dump (IL2CPP)", "autodump", Icons.Default.CloudDownload),
        NavToolItem("Il2CppDumper", "il2cppdump", Icons.Default.Description),
        NavToolItem("Dex Dumper", "dexdump", Icons.Default.Android),
        NavToolItem("Auto Leak Source", "autoleak", Icons.Default.Key),
    ), AccentGreen),
    NavToolCategory("🔧 Patch & Mod", Icons.Default.Construction, listOf(
        NavToolItem("Auto Patch Login", "autopatch", Icons.Default.LockOpen),
        NavToolItem("Shell Patcher", "shellpatch", Icons.Default.Code),
        NavToolItem("Patch Instructions", "patchinst", Icons.Default.EditNote),
        NavToolItem("UPX Unpacker", "upx", Icons.Default.Unarchive),
        NavToolItem("Admin Password Searcher", "adminpass", Icons.Default.AdminPanelSettings),
        NavToolItem("SO Patcher", "sopatch", Icons.Default.Build),
    ), AccentOrange),
    NavToolCategory("🔐 Crypto & Deobfuscate", Icons.Default.EnhancedEncryption, listOf(
        NavToolItem("Deobfuscate", "deobfuscate", Icons.Default.Psychology),
        NavToolItem("Obfuscate", "obfuscate", Icons.Default.VisibilityOff),
        NavToolItem("Decrypt Tool", "decrypt", Icons.Default.Lock),
        NavToolItem("Encrypt Tool", "encrypt", Icons.Default.Lock),
        NavToolItem("Shell Deobfuscate", "shelldeob", Icons.Default.Terminal),
        NavToolItem("String Extractor", "strings", Icons.Default.TextFields),
        NavToolItem("Pattern Detector", "patterndetect", Icons.Default.Science),
    ), AccentPurple),
    NavToolCategory("📱 Build & Create", Icons.Default.Apps, listOf(
        NavToolItem("APK Builder", "apkbuilder", Icons.Default.PhoneAndroid),
        NavToolItem("JNI Builder", "jnibuilder", Icons.Default.Code),
        NavToolItem("APK Signer", "apksigner", Icons.Default.Verified),
        NavToolItem("APK Tools", "apkmisc", Icons.Default.Build),
        NavToolItem("Batch Renamer", "batchrenamer", Icons.Default.Edit),
        NavToolItem("Permission Remover", "permremover", Icons.Default.RemoveCircle),
        NavToolItem("APKTool Suite", "apktasksuite", Icons.Default.FolderZip),
    ), AccentRed),
    NavToolCategory("🛡️ Security", Icons.Default.Shield, listOf(
        NavToolItem("Certificate Analyzer", "certanalysis", Icons.Default.Verified),
        NavToolItem("Permission Analyzer", "permanalysis", Icons.Default.Security),
        NavToolItem("SSL Pinning Bypass", "sslpinning", Icons.Default.VpnKey),
        NavToolItem("Malware Detector", "malwaredetect", Icons.Default.HealthAndSafety),
    ), Color(0xFFE91E63)),
    NavToolCategory("📜 Scripts", Icons.Default.Code, listOf(
        NavToolItem("Ghidra/Frida Scripts", "scripts", Icons.Default.BugReport),
        NavToolItem("Native Hook Generator", "nativehook", Icons.Default.Memory),
        NavToolItem("Resource Decoder", "resdecode", Icons.Default.FolderOpen),
    ), Color(0xFF7C4DFF)),
    NavToolCategory("💻 System", Icons.Default.PhoneAndroid, listOf(
        NavToolItem("Terminal", "terminal", Icons.Default.Terminal),
        NavToolItem("Tools Download", "toolsdownload", Icons.Default.Download),
    ), Color(0xFF455A64)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationDrawer(
    drawerState: DrawerState,
    navController: NavController,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = DarkBg
            ) {
                // Header
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(16.dp)
                ) {
                    Text("⚡ OprekTool", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AccentGreen)
                    Text("Reverse Engineering Toolkit", fontSize = 11.sp, color = TextSecondary)
                    Text("v0.9.1 • © Panxcz & Freebuff", fontSize = 9.sp, color = TextMuted)
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        NavigationDrawerItem(
                            label = { Text("🏠 Home", fontSize = 13.sp) },
                            selected = false,
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }

                    toolCategories.forEach { category ->
                        item {
                            CategoryHeader(category)
                        }
                        items(category.tools) { tool ->
                            NavigationDrawerItem(
                                label = { Text(tool.name, fontSize = 12.sp) },
                                selected = false,
                                onClick = {
                                    navController.navigate(tool.route)
                                },
                                icon = { Icon(tool.icon, null, Modifier.size(18.dp), tint = category.color) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                            )
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = DarkSurface
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun CategoryHeader(category: NavToolCategory) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(category.icon, null, Modifier.size(16.dp), tint = category.color)
        Spacer(Modifier.width(8.dp))
        Text(
            category.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = category.color
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${category.tools.size}",
            fontSize = 9.sp,
            color = TextMuted,
            modifier = Modifier
                .background(category.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
