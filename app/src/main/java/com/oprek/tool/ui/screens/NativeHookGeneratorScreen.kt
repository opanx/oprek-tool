package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeHookGeneratorScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedScript by remember { mutableStateOf("") }
    var scriptOutput by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔗 Native Hook Generator", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (scriptOutput.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("script", scriptOutput.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = DarkSurface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Frida Native", fontSize = 10.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dobby Hook", fontSize = 10.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("PLT Hook", fontSize = 10.sp) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Inline Hook", fontSize = 10.sp) })
            }
            Spacer(Modifier.height(8.dp))

            val scripts = when (selectedTab) { 0 -> fridaNativeScripts; 1 -> dobbyScripts; 2 -> pltScripts; 3 -> inlineScripts; else -> fridaNativeScripts }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                    scripts.forEach { (name, pair) ->
                        Card(
                            onClick = { selectedScript = name; scriptOutput = pair.second },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedScript == name) AccentCyan.copy(alpha = 0.15f) else DarkCard),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                                    Text(pair.first, fontSize = 9.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            if (scriptOutput.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("📋 $selectedScript", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        LazyColumn {
                            items(scriptOutput) { line ->
                                val color = when { line.startsWith("//") || line.startsWith("#") -> Color.Gray; line.contains("Interceptor") || line.contains("Dobby") -> AccentGreen; line.contains("function") || line.contains("void") -> AccentPurple; line.contains("send(") || line.contains("console.log") -> AccentCyan; else -> TextPrimary }
                                Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val fridaNativeScripts = linkedMapOf(
    "Hook libc read()" to ("Intercept read() calls" to listOf("// Hook libc read()", "Interceptor.attach(Module.findExportByName('libc.so', 'read'), {", "    onEnter: function(args) {", "        this.fd = args[0].toInt32();", "        this.buf = args[1];", "        this.count = args[2].toInt32();", "    },", "    onLeave: function(retval) {", "        if (this.fd > 2) {", "            console.log('[read] fd=' + this.fd + ' ret=' + retval.toInt32());", "            if (retval.toInt32() > 0) {", "                var buf = Memory.readByteArray(this.buf, Math.min(retval.toInt32(), 64));", "                console.log(hexdump(this.buf, { length: Math.min(retval.toInt32(), 64) }));", "            }", "        }", "    }", "});")),
    "Hook libc write()" to ("Intercept write() calls" to listOf("// Hook libc write()", "Interceptor.attach(Module.findExportByName('libc.so', 'write'), {", "    onEnter: function(args) {", "        this.fd = args[0].toInt32();", "        this.buf = args[1];", "        this.count = args[2].toInt32();", "        console.log('[write] fd=' + this.fd + ' size=' + this.count);", "        if (this.count > 0 && this.count < 512) {", "            console.log(hexdump(this.buf, { length: this.count }));", "        }", "    }", "});")),
    "Hook libc malloc()" to ("Track memory allocation" to listOf("// Track malloc calls", "Interceptor.attach(Module.findExportByName('libc.so', 'malloc'), {", "    onEnter: function(args) {", "        this.size = args[0].toInt32();", "    },", "    onLeave: function(retval) {", "        if (this.size > 4096) {", "            console.log('[malloc] size=' + this.size + ' ptr=' + retval);", "        }", "    }", "});")),
    "Hook libc memcpy()" to ("Monitor memory copies" to listOf("// Monitor memcpy", "Interceptor.attach(Module.findExportByName('libc.so', 'memcpy'), {", "    onEnter: function(args) {", "        console.log('[memcpy] dst=' + args[0] + ' src=' + args[1] + ' len=' + args[2].toInt32());", "    }", "});")),
    "Hook dlopen()" to ("Track library loading" to listOf("// Track dlopen", "Interceptor.attach(Module.findExportByName('libc.so', 'dlopen'), {", "    onEnter: function(args) {", "        console.log('[dlopen] ' + args[0].readUtf8String());", "    }", "});", "Interceptor.attach(Module.findExportByName('libc.so', 'android_dlopen_ext'), {", "    onEnter: function(args) {", "        console.log('[android_dlopen_ext] ' + args[0].readUtf8String());", "    }", "});")),
    "Hook pthread_create()" to ("Track thread creation" to listOf("// Track pthread_create", "Interceptor.attach(Module.findExportByName('libc.so', 'pthread_create'), {", "    onEnter: function(args) {", "        console.log('[pthread_create] thread=' + args[0] + ' start=' + args[2]);", "    }", "});"))
)

private val dobbyScripts = linkedMapOf(
    "Dobby Basic Hook" to ("Basic Dobby inline hook" to listOf("// Dobby Inline Hook", "// Requires Dobby library loaded in target process", "void* target_addr = DobbySymbolResolver(\"libtarget.so\", \"target_function\");", "if (target_addr) {", "    DobbyHook(target_addr, (void*)hook_function, (void**)&orig_function);", "    LOGI(\"[+] Hooked target_function at %p\", target_addr);", "} else {", "    LOGE(\"[-] Cannot find target_function\");", "}")),
    "Dobby Hide Function" to ("Hide function from process" to listOf("// Hide function using Dobby", "void* func = DobbySymbolResolver(\"libtarget.so\", \"detect_function\");", "if (func) {", "    DobbyHook(func, (void*)always_return_true, NULL);", "}")),
    "Dobby Game Hook" to ("Hook game IL2CPP functions" to listOf("// Game IL2CPP Hook using Dobby", "void* il2cpp_addr = DobbySymbolResolver(\"libil2cpp.so\", \"il2cpp_class_get_method_from_name\");", "if (il2cpp_addr) {", "    DobbyHook(il2cpp_addr, (void*)hook_get_method, (void**)&orig_get_method);", "}", "", "// Hook specific game function by offset", "void* base = GetBaseAddr(\"libil2cpp.so\");", "void* game_func = (void*)((uintptr_t)base + 0x123456);", "DobbyHook(game_func, (void*)my_hook, (void**)&original);"))
)

private val pltScripts = linkedMapOf(
    "PLT Hook Method" to ("Hook via PLT/GOT table" to listOf("// PLT Hook using Frida", "var module = Module.findBaseAddress('libtarget.so');", "var plt = Module.findExportByName('libtarget.so', 'target_func');", "", "Interceptor.attach(plt, {", "    onEnter: function(args) {", "        console.log('[PLT] target_func called');", "    }", "});", "", "// Direct GOT patching", "var got_addr = module.add(0x1234); // GOT offset", "Memory.protect(got_addr, 0x10, 'rwx');", "Memory.writePointer(got_addr, new NativeCallback(function(arg0) {", "    console.log('[GOT] intercepted');", "    return 0;", "}, 'int', ['pointer']));"))
)

private val inlineScripts = linkedMapOf(
    "ARM64 Inline Hook" to ("ARM64 trampoline hook" to listOf("// ARM64 Inline Hook", "// Save original bytes", "var addr = Module.findExportByName('libtarget.so', 'func');", "var origBytes = Memory.readByteArray(addr, 8);", "", "// Create trampoline", "Memory.protect(addr, 0x10, 'rwx');", "", "// Patch: LDR X16, [PC, #8] + BR X16", "var patch = [0x50, 0x00, 0x00, 0x58, 0x00, 0x02, 0x1F, 0xD6];", "Memory.writeByteArray(addr, patch);", "", "// Write hook address", "var hookAddr = Memory.alloc(8);", "Memory.writePointer(hookAddr, new NativeCallback(function() {", "    console.log('[Inline] Hook called!');", "    // Call original", "}, 'void', []));", "Memory.writePointer(addr.add(8), hookAddr);")),
    "ARM32 Inline Hook" to ("ARM32 trampoline hook" to listOf("// ARM32 Inline Hook", "var addr = Module.findExportByName('libtarget.so', 'func');", "Memory.protect(addr, 0x10, 'rwx');", "", "// Patch: LDR PC, [PC, #-4]", "var patch = [0x04, 0xF0, 0x1F, 0xE5];", "Memory.writeByteArray(addr, patch);", "", "// Write hook address after", "var hookAddr = new NativeCallback(function() {", "    console.log('[ARM32 Inline] Hook called!');", "}, 'void', []);", "Memory.writePointer(addr.add(4), hookAddr);"))
)
