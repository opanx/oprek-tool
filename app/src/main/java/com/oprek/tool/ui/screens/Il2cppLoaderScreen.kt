package com.oprek.tool.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Il2cppLoaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Generator", "Templates", "Frida Scripts", "Config", "Guide")

    // Config state
    var toolTitle by remember { mutableStateOf("IL2CPP Tool by Oprek") }
    var targetPackage by remember { mutableStateOf("com.mobile.legends") }
    var targetLib by remember { mutableStateOf("libil2cpp.so") }
    var telegramLink by remember { mutableStateOf("https://t.me/kembungjir") }
    var channelLink by remember { mutableStateOf("https://t.me/lazy_fat_catt") }
    var dumpPath by remember { mutableStateOf("/sdcard/Download/OprekTool/dump") }
    var selectedArch by remember { mutableIntStateOf(1) }
    var obfuscate by remember { mutableStateOf(true) }
    var useFrida by remember { mutableStateOf(false) }
    var hideFromRecents by remember { mutableStateOf(true) }
    var antiDebug by remember { mutableStateOf(true) }
    var rootCheck by remember { mutableStateOf(false) }
    var maxHookCount by remember { mutableStateOf("5000") }

    // Generated files
    var generatedMainCpp by remember { mutableStateOf("") }
    var generatedManifest by remember { mutableStateOf("") }
    var generatedMk by remember { mutableStateOf("") }
    var generatedFrida by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IL2CPP Loader", color = AccentCyan) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentCyan)
                    }
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = AccentCyan
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> GeneratorTab(
                    toolTitle, { toolTitle = it },
                    targetPackage, { targetPackage = it },
                    targetLib, { targetLib = it },
                    telegramLink, { telegramLink = it },
                    channelLink, { channelLink = it },
                    dumpPath, { dumpPath = it },
                    selectedArch, { selectedArch = it },
                    obfuscate, { obfuscate = it },
                    useFrida, { useFrida = it },
                    hideFromRecents, { hideFromRecents = it },
                    antiDebug, { antiDebug = it },
                    rootCheck, { rootCheck = it },
                    maxHookCount, { maxHookCount = it },
                    onGenerate = {
                        scope.launch {
                            val r = generateIl2cppTool(
                                toolTitle, targetPackage, targetLib,
                                telegramLink, channelLink, dumpPath,
                                selectedArch, obfuscate, useFrida,
                                hideFromRecents, antiDebug, rootCheck, maxHookCount
                            )
                            generatedMainCpp = r.first
                            generatedManifest = r.second
                            generatedMk = r.third
                            snackbarHostState.showSnackbar("Generated!")
                        }
                    }
                )
                1 -> TemplateTab(generatedMainCpp, generatedManifest, generatedMk, snackbarHostState)
                2 -> FridaTab(targetPackage, targetLib, snackbarHostState)
                3 -> ConfigTab(targetPackage, targetLib, snackbarHostState)
                4 -> GuideTab()
            }
        }
    }
}

@Composable
private fun GeneratorTab(
    toolTitle: String, onTitleChange: (String) -> Unit,
    targetPackage: String, onPackageChange: (String) -> Unit,
    targetLib: String, onLibChange: (String) -> Unit,
    telegramLink: String, onTelegramChange: (String) -> Unit,
    channelLink: String, onChannelChange: (String) -> Unit,
    dumpPath: String, onDumpPathChange: (String) -> Unit,
    selectedArch: Int, onArchChange: (Int) -> Unit,
    obfuscate: Boolean, onObfuscateChange: (Boolean) -> Unit,
    useFrida: Boolean, onFridaChange: (Boolean) -> Unit,
    hideFromRecents: Boolean, onHideChange: (Boolean) -> Unit,
    antiDebug: Boolean, onAntiDebugChange: (Boolean) -> Unit,
    rootCheck: Boolean, onRootChange: (Boolean) -> Unit,
    maxHookCount: String, onMaxHookChange: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Tool Configuration", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(toolTitle, onTitleChange, label = { Text("Tool Title") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(telegramLink, onTelegramChange, label = { Text("Telegram Link") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(channelLink, onChannelChange, label = { Text("Channel Link") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
        }
        DarkCard {
            Text("Target", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(targetPackage, onPackageChange, label = { Text("Package Name") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(targetLib, onLibChange, label = { Text("Target Library") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(dumpPath, onDumpPathChange, label = { Text("Dump Path") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(maxHookCount, onMaxHookChange, label = { Text("Max Hook Count") },
                modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors())
        }
        DarkCard {
            Text("Architecture", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(selected = selectedArch == 0, onClick = { onArchChange(0) },
                    label = { Text("ARM32") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = selectedArch == 1, onClick = { onArchChange(1) },
                    label = { Text("ARM64") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
            }
        }
        DarkCard {
            Text("Options", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = obfuscate, onCheckedChange = onObfuscateChange)
                Text("Obfuscate Strings", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useFrida, onCheckedChange = onFridaChange)
                Text("Use Frida Engine (vs Dobby)", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hideFromRecents, onCheckedChange = onHideChange)
                Text("Hide from Recents", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = antiDebug, onCheckedChange = onAntiDebugChange)
                Text("Anti-Debug (ptrace/frida detect)", color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rootCheck, onCheckedChange = onRootChange)
                Text("Root Check Required", color = TextSecondary)
            }
        }
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
            Icon(Icons.Default.Build, null, tint = DarkBg)
            Spacer(Modifier.width(8.dp))
            Text("Generate All Code", color = DarkBg, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TemplateTab(code: String, manifest: String, mk: String, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TemplateFile("Main.cpp", code, { cm.setText(AnnotatedString(code)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "Main.cpp", code); snackbar.showSnackbar("Saved!") } })
        TemplateFile("AndroidManifest.xml", manifest,
            { cm.setText(AnnotatedString(manifest)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "AndroidManifest.xml", manifest); snackbar.showSnackbar("Saved!") } })
        TemplateFile("Android.mk", mk,
            { cm.setText(AnnotatedString(mk)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "Android.mk", mk); snackbar.showSnackbar("Saved!") } })

        // Save all
        Button(onClick = {
            scope.launch {
                saveToFile(ctx, "Main.cpp", code)
                saveToFile(ctx, "AndroidManifest.xml", manifest)
                saveToFile(ctx, "Android.mk", mk)
                snackbar.showSnackbar("All files saved to /OprekTool/il2cpp-tool/")
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
            Icon(Icons.Default.SaveAll, null, tint = DarkBg)
            Spacer(Modifier.width(8.dp))
            Text("Save All Files", color = DarkBg, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FridaTab(pkg: String, lib: String, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Frida IL2CPP Hook Scripts", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Auto-generated Frida scripts for IL2CPP hooking", color = TextSecondary, fontSize = 12.sp)
        }

        // Script 1: IL2CPP Dump via Frida
        val fridaDump = """
// IL2CPP Runtime Dumper - Frida Script
// Package: $pkg
// Generated by OprekTool
Java.perform(function() {
    var Il2Cpp = Java.use('com.facebook.soloader.SoLoader');
    var il2cpp = Module.findBaseAddress('$lib');
    if (!il2cpp) {
        console.log('[-] $lib not found! Waiting...');
        setTimeout(function() {
            il2cpp = Module.findBaseAddress('$lib');
            if (!il2cpp) { console.log('[-] Still not found'); return; }
            dumpMethods(il2cpp);
        }, 5000);
    } else {
        dumpMethods(il2cpp);
    }

    function dumpMethods(base) {
        console.log('[+] $lib loaded at: ' + base);
        // Find il2cpp_class_get_methods
        var exports = Process.findModuleByName('$lib').enumerateExports();
        var getClassMethods = null;
        var getClassName = null;
        for (var i = 0; i < exports.length; i++) {
            if (exports[i].name === 'il2cpp_class_get_methods') getClassMethods = exports[i].address;
            if (exports[i].name === 'il2cpp_class_get_name') getClassName = exports[i].address;
        }
        if (!getClassMethods) {
            console.log('[-] il2cpp_class_get_methods not found');
            return;
        }
        console.log('[+] il2cpp_class_get_methods @ ' + getClassMethods);
        // Hook and dump
        Interceptor.attach(getClassMethods, {
            onEnter: function(args) {
                this.className = new NativeFunction(getClassName, 'pointer', ['pointer'])(args[0]);
            },
            onLeave: function(retval) {
                if (this.className) {
                    var name = this.className.readCString();
                    if (name && name.indexOf('Player') !== -1) {
                        console.log('[CLASS] ' + name);
                    }
                }
            }
        });
        console.log('[+] Hooks installed. Play the game to capture methods.');
    }
});
        """.trimIndent()

        TemplateFile("frida_il2cpp_dump.js", fridaDump,
            { cm.setText(AnnotatedString(fridaDump)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "frida_il2cpp_dump.js", fridaDump); snackbar.showSnackbar("Saved!") } })

        // Script 2: Method Hooker
        val fridaHooker = """
// IL2CPP Method Hooker - Frida Script
// Package: $pkg | Library: $lib
// Generated by OprekTool
Java.perform(function() {
    var il2cpp = Process.findModuleByName('$lib');
    if (!il2cpp) { console.log('[-] Module not found'); return; }

    var exports = il2cpp.enumerateExports();
    var methods = {};
    for (var i = 0; i < exports.length; i++) {
        if (exports[i].name.indexOf('il2cpp_') === 0) {
            methods[exports[i].name] = exports[i].address;
        }
    }

    console.log('[+] Found ' + Object.keys(methods).length + ' IL2CPP exports');

    // Hook il2cpp_runtime_invoke for runtime method calls
    if (methods.il2cpp_runtime_invoke) {
        Interceptor.attach(methods.il2cpp_runtime_invoke, {
            onEnter: function(args) {
                var method = args[0];
                if (method.isNull()) return;
                try {
                    var methodName = new NativeFunction(methods.il2cpp_method_get_name || ptr(0), 'pointer', ['pointer']);
                    var namePtr = methodName(method);
                    if (!namePtr.isNull()) {
                        var name = namePtr.readCString();
                        if (name && (name.indexOf('Login') !== -1 || name.indexOf('Auth') !== -1 || name.indexOf('License') !== -1)) {
                            console.log('[HOOK] ' + name + ' called!');
                            // Log arguments
                            console.log('  arg0: ' + args[1]);
                            console.log('  arg1: ' + args[2]);
                            console.log('  arg2: ' + args[3]);
                        }
                    }
                } catch(e) {}
            }
        });
        console.log('[+] Runtime invoke hooked');
    }

    // Hook il2cpp_object_new for object creation tracking
    if (methods.il2cpp_object_new) {
        var callCount = 0;
        Interceptor.attach(methods.il2cpp_object_new, {
            onEnter: function(args) {
                callCount++;
                if (callCount % 100 === 0) {
                    console.log('[STATS] Objects created: ' + callCount);
                }
            }
        });
        console.log('[+] Object creation tracking active');
    }

    console.log('[+] All hooks installed. Interact with the game.');
});
        """.trimIndent()

        TemplateFile("frida_method_hooker.js", fridaHooker,
            { cm.setText(AnnotatedString(fridaHooker)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "frida_method_hooker.js", fridaHooker); snackbar.showSnackbar("Saved!") } })

        // Script 3: String Interceptor
        val fridaStrings = """
// IL2CPP String Interceptor - Frida Script
// Package: $pkg | Library: $lib
// Captures all string allocations from IL2CPP
Java.perform(function() {
    var il2cpp = Process.findModuleByName('$lib');
    if (!il2cpp) { console.log('[-] Module not found'); return; }

    var exports = il2cpp.enumerateExports();
    var methods = {};
    for (var i = 0; i < exports.length; i++) {
        if (exports[i].name.indexOf('il2cpp_') === 0) {
            methods[exports[i].name] = exports[i].address;
        }
    }

    // Hook il2cpp_string_new to capture all string creation
    if (methods.il2cpp_string_new) {
        var strings = new Set();
        Interceptor.attach(methods.il2cpp_string_new, {
            onEnter: function(args) {
                try {
                    var str = args[0].readCString();
                    if (str && str.length > 3 && !strings.has(str)) {
                        strings.add(str);
                        // Filter interesting strings
                        var lower = str.toLowerCase();
                        if (lower.indexOf('http') !== -1 ||
                            lower.indexOf('key') !== -1 ||
                            lower.indexOf('token') !== -1 ||
                            lower.indexOf('secret') !== -1 ||
                            lower.indexOf('password') !== -1 ||
                            lower.indexOf('license') !== -1 ||
                            lower.indexOf('api') !== -1 ||
                            lower.indexOf('auth') !== -1 ||
                            lower.indexOf('login') !== -1) {
                            console.log('[STRING] ' + str);
                        }
                    }
                } catch(e) {}
            }
        });
        console.log('[+] String interceptor active. Unique strings tracked.');
    }

    // Hook il2cpp_utf8_to_utf16 for encoded strings
    if (methods.il2cpp_utf8_to_utf16) {
        Interceptor.attach(methods.il2cpp_utf8_to_utf16, {
            onEnter: function(args) {
                try {
                    var str = args[0].readCString();
                    if (str && str.length > 20) {
                        console.log('[UTF16] ' + str);
                    }
                } catch(e) {}
            }
        });
    }

    console.log('[+] All string hooks installed');
});
        """.trimIndent()

        TemplateFile("frida_string_interceptor.js", fridaStrings,
            { cm.setText(AnnotatedString(fridaStrings)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "frida_string_interceptor.js", fridaStrings); snackbar.showSnackbar("Saved!") } })

        // Script 4: SSL Pinning Bypass
        val fridaSSL = """
// SSL Pinning Bypass for IL2CPP Games
// Package: $pkg
// Generated by OprekTool
Java.perform(function() {
    console.log('[*] SSL Pinning Bypass Active');

    // OkHttp3 CertificatePinner
    try {
        var CertPinner = Java.use('okhttp3.CertificatePinner');
        CertPinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {
            console.log('[+] SSL Bypass: ' + hostname);
        };
        console.log('[+] OkHttp3 CertificatePinner bypassed');
    } catch(e) { console.log('[-] OkHttp3 not found'); }

    // TrustManagerImpl
    try {
        var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');
        TrustManagerImpl.verifyChain.implementation = function(untrustedChain, trustAnchorChain, host, clientAuth, ocspData, tlsSctData) {
            console.log('[+] TrustManagerImpl bypass: ' + host);
            return untrustedChain;
        };
        console.log('[+] TrustManagerImpl bypassed');
    } catch(e) { console.log('[-] TrustManagerImpl not found'); }

    // WebViewClient
    try {
        var WebViewClient = Java.use('android.webkit.WebViewClient');
        WebViewClient.onReceivedSslError.implementation = function(view, handler, error) {
            console.log('[+] WebView SSL bypass');
            handler.proceed();
        };
        console.log('[+] WebViewClient SSL bypassed');
    } catch(e) { console.log('[-] WebViewClient not found'); }

    // HostnameVerifier
    try {
        var HostnameVerifier = Java.use('javax.net.ssl.HostnameVerifier');
        HostnameVerifier.verify.implementation = function(hostname, session) {
            console.log('[+] HostnameVerifier bypass: ' + hostname);
            return true;
        };
        console.log('[+] HostnameVerifier bypassed');
    } catch(e) { console.log('[-] HostnameVerifier not found'); }

    console.log('[+] SSL Pinning Bypass complete');
});
        """.trimIndent()

        TemplateFile("frida_ssl_bypass.js", fridaSSL,
            { cm.setText(AnnotatedString(fridaSSL)); scope.launch { snackbar.showSnackbar("Copied!") } },
            { scope.launch { saveToFile(ctx, "frida_ssl_bypass.js", fridaSSL); snackbar.showSnackbar("Saved!") } })

        Button(onClick = {
            scope.launch {
                saveToFile(ctx, "frida_il2cpp_dump.js", fridaDump)
                saveToFile(ctx, "frida_method_hooker.js", fridaHooker)
                saveToFile(ctx, "frida_string_interceptor.js", fridaStrings)
                saveToFile(ctx, "frida_ssl_bypass.js", fridaSSL)
                snackbar.showSnackbar("All 4 Frida scripts saved!")
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
            Icon(Icons.Default.SaveAll, null, tint = DarkBg)
            Spacer(Modifier.width(8.dp))
            Text("Save All Frida Scripts", color = DarkBg, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConfigTab(pkg: String, lib: String, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Game Presets", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val presets = listOf(
                "Mobile Legends" to "com.mobile.legends",
                "Free Fire" to "com.dts.freefireth",
                "Free Fire MAX" to "com.dts.freefiremax",
                "PUBG Mobile" to "com.tencent.ig",
                "PUBG Mobile KR" to "com.tencent.igkr",
                "PUBG Mobile VN" to "com.tencent.igvnm",
                "Genshin Impact" to "com.miHoYo.GenshinImpact",
                "Honkai Star Rail" to "com.HoYoverse.hkrpgoversea",
                "Blood Strike" to "com.excean.dualaid",
                "COD Mobile" to "com.activision.callofduty.shooter",
                "Brawl Stars" to "com.supercell.brawlstars",
                "Standoff 2" to "com.axlebolt.standoff2",
                "Roblox" to "com.roblox.client",
                "Subway Surfers" to "com.kiloo.subwaysurf",
                "Asphalt 9" to "com.gameloft.android.ANMP.GloftA9HM",
                "Clash Royale" to "com.supercell.clashroyale",
                "Minecraft" to "com.mojang.minecraftpe",
                "Arena of Valor" to "com.ngame.allstar.eu",
                "Stumble Guys" to "com.kitkagames.fallbuddies",
                "eFootball PES" to "jp.konami.pesam"
            )
            presets.forEach { (name, pkgName) ->
                Surface(
                    onClick = {
                        scope.launch {
                            snackbar.showSnackbar("Selected: $name ($pkgName)")
                        }
                    },
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gamepad, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = TextPrimary, fontSize = 12.sp)
                            Text(pkgName, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        DarkCard {
            Text("Library Mappings", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val libs = listOf(
                "libil2cpp.so" to "Unity IL2CPP (most games)",
                "liblogic.so" to "Mobile Legends (MLBB)",
                "libUE4.so" to "Unreal Engine games",
                "libmain.so" to "Unity Native Main",
                "libunity.so" to "Unity Engine core",
                "libg.so" to "Custom game engines",
                "libgamename.so" to "Custom game libraries"
            )
            libs.forEach { (libName, desc) ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(libName, color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(140.dp))
                    Text(desc, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GuideTab() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Quick Start Guide", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val steps = listOf(
                "1. Configure" to "Set tool title, target game, links, options",
                "2. Generate" to "Click 'Generate All Code' to create C++ files",
                "3. Clone Template" to "git clone https://github.com/Android-LibTool-New",
                "4. Replace Files" to "Copy Main.cpp, AndroidManifest.xml, Android.mk",
                "5. Build NDK" to "ndk-build or Android Studio (CMake/NDK)",
                "6. Sign APK" to "jarsigner or apksigner to sign the APK",
                "7. Install" to "adb install -r app.apk on rooted device",
                "8. Launch Game" to "Open target game — overlay menu appears!"
            )
            steps.forEach { (t, d) ->
                Text(t, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(d, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
            }
        }

        DarkCard {
            Text("Features Included", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val features = listOf(
                "IL2CPP Dumper — Full dump.cs generation",
                "Runtime API Dumper — Capture method calls at runtime",
                "Method Tracer — Trace function execution flow",
                "String Viewer — Search runtime string objects",
                "Memory Patcher — Patch bytes at runtime offsets",
                "Class Browser — Browse all IL2CPP classes/methods",
                "ImGui Overlay — Floating menu with tabs",
                "Anti-Debug — Detect & bypass debuggers",
                "Frida Engine — Alternative hooking backend",
                "Config Save — Persistent settings between sessions"
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

        DarkCard {
            Text("Dump Output Format", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "// dump.cs — IL2CPP Runtime Dump\n" +
                "// Address | Class::Method\n" +
                "namespace Game {\n" +
                "    public class PlayerController {\n" +
                "        // 0x1A2B3C4D\n" +
                "        public void Move(float speed) { }\n" +
                "        // 0x1A2B3C60\n" +
                "        public static bool IsAlive() { }\n" +
                "    }\n" +
                "}",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface).padding(8.dp)
            )
        }

        DarkCard {
            Text("Quick Links", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val links = listOf(
                "Android-LibTool-New" to "github.com/Android-LibTool-New",
                "Dobby Hooking" to "github.com/jmpews/Dobby",
                "KittyMemory" to "github.com/MJx0/KittyMemory",
                "ImGui" to "github.com/ocornut/imgui",
                "Frida" to "frida.re",
                "OprekTool" to "github.com/opanx/oprek-tool"
            )
            links.forEach { (n, u) ->
                Text("$n: $u", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private fun generateIl2cppTool(
    title: String, pkg: String, lib: String,
    telegram: String, channel: String, dumpPath: String,
    arch: Int, obfuscate: Boolean, useFrida: Boolean,
    hideRecents: Boolean, antiDebug: Boolean, rootCheck: Boolean,
    maxHook: String
): Triple<String, String, String> {
    val archStr = if (arch == 1) "arm64-v8a" else "armeabi-v7a"
    val obfMacro = if (obfuscate) "#define USE_OBFUSCATE" else "// #define USE_OBFUSCATE"
    val hookEngine = if (useFrida) "USE_FRIDA" else "// USE_FRIDA"
    val antiDbg = if (antiDebug) "AntiDebug::Install();" else ""
    val rootChk = if (rootCheck) "RootCheck::Verify();" else ""

    val mainCpp = """// IL2CPP Tool - Generated by OprekTool v0.16.0
// Target: $pkg | Library: $lib | Arch: $archStr
// Features: Dumper, Tracer, String Viewer, Memory Patcher

#include <jni.h>
#include <pthread.h>
#include <thread>
#include <unistd.h>
#include "Il2cpp/Il2cpp.h"
#include "Il2cpp/il2cpp-class.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/obfuscate.h"
#include "Menu/ImGui.h"
#include "Tool/Keyboard.h"
#include "Tool/Tool.h"
#include "Tool/Util.h"
#include "imgui/imgui.h"
#include "imgui/imgui_internal.h"

$obfMacro
$hookEngine

Il2CppImage *g_Image = nullptr;
std::vector<MethodInfo *> g_Methods;
extern std::unordered_map<void *, HookerData> hookerMap;

bool collapsed = false;
bool fullScreen = false;
bool resetWindow = false;
int selectedScale = 3;
int selectedTheme = 0;

constexpr std::array<const char *, 7> possibleScale = {
    "Smallest", "Smaller", "Small", "Default", "Large", "Larger", "Largest",
};
constexpr std::array<float, 7> scaleFactors = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

const char *g_title = "$title";

void draw_thread() {
    static ImVec2 lastSize = ImVec2(0, 0);
    static ImVec2 lastPos = ImVec2(0, 0);

    if (resetWindow) {
        resetWindow = false;
        if (fullScreen) {
            ImGui::SetNextWindowPos(ImVec2(0, 0));
            ImGui::SetNextWindowSize(ImGui::GetIO().DisplaySize);
        } else {
            ImGui::SetNextWindowPos(lastPos);
            ImGui::SetNextWindowSize(lastSize);
        }
    }
    if (fullScreen) ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(0, ImGui::GetFrameHeight()));

    collapsed = !ImGui::Begin(g_title, nullptr,
        fullScreen ? ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove : 0);
    if (fullScreen) ImGui::PopStyleVar();

    Keyboard::Update();

    if (ImGui::BeginTabBar("mainTabber")) {
        if (ImGui::BeginTabItem("Tools")) {
            if (ImGui::Checkbox("Fullscreen", &fullScreen)) {
                if (fullScreen) { lastSize = ImGui::GetWindowSize(); lastPos = ImGui::GetWindowPos(); }
                resetWindow = true;
            }
            Tool::Draw();
            ImGui::EndTabItem();
        }
        if (!hookerMap.empty() && ImGui::BeginTabItem("Tracer")) {
            ImGui::Text("Traced methods: %zu", hookerMap.size());
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Strings")) {
            Tool::Strings();
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Dumper")) {
            Tool::Dumper();
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Settings")) {
            ImGui::Separator();
            ImGui::Text("Package: %s", Il2cpp::getPackageName().c_str());
            ImGui::Text("Game: %s", Il2cpp::getGameVersion().c_str());
            ImGui::Text("Unity: %s", Il2cpp::getUnityVersion().c_str());
#ifdef __aarch64__
            ImGui::Text("Arch: arm64-v8a");
#else
            ImGui::Text("Arch: armeabi-v7a");
#endif
            ImGui::Separator();
            if (ImGui::Button("Telegram")) {
                auto App = Il2cpp::FindClass("UnityEngine.Application");
                auto OpenURL = App->getMethod("OpenURL", 1);
                if (OpenURL) OpenURL->invoke_static<void>(Il2cpp::NewString(OBFUSCATE("$telegram")));
            }
            ImGui::EndTabItem();
        }
        ImGui::EndTabBar();
    }
    ImGui::End();
    Tool::DrawNotifications();
}

void on_init() {
    while (!isLibraryLoaded(targetLibName)) sleep(1);
    $antiDbg
    $rootChk
    Il2cpp::Init();
    Il2cpp::EnsureAttached();
    Keyboard::Init();

    g_Image = Il2cpp::GetAssembly("Assembly-CSharp")->getImage();
    auto images = Il2cpp::GetImages();
    Tool::Init(g_Image, images);

    for (auto image : images) {
        for (auto klass : image->getClasses()) {
            for (auto m : klass->getMethods()) {
                if (m->methodPointer) g_Methods.emplace_back(m);
            }
        }
    }
    std::sort(g_Methods.begin(), g_Methods.end(),
        [](const auto &a, const auto &b) { return a->methodPointer < b->methodPointer; });
    LOGI("Init complete: %zu methods", g_Methods.size());
}

bool useJava = false;
void *hack_thread(void *) {
    logger::Clear();
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    if (!useJava) initModMenu((void *)draw_thread, (void *)on_init);
    return nullptr;
}

extern int glWidth;
extern int glHeight;
extern "C" {
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onDrawFrame(JNIEnv *, jclass) {
        internalDrawMenu(glWidth, glHeight);
    }
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onSurfaceChanged(JNIEnv *, jclass, jint w, jint h) {
        glWidth = w; glHeight = h; setupMenu();
    }
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onSurfaceCreate(JNIEnv *, jclass) {
        initModMenu((void *)draw_thread, (void *)on_init, useJava);
    }
}

__attribute__((constructor)) void lib_main() {
    pthread_t ptid;
    pthread_create(&ptid, nullptr, hack_thread, nullptr);
}

JavaVM *g_vm = nullptr;
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    JNIEnv *env;
    vm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (env->FindClass("imgui/il2cpp/tool/NativeMethods") != nullptr) useJava = true;
    return JNI_VERSION_1_6;
}
"""

    val manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.oprek.il2cpploader">
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <application android:allowBackup="true" android:label="$title"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        ${if (hideRecents) "android:excludeFromRecents=\"true\"" else ""}>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

    val androidMk = """LOCAL_PATH := $(call my-dir)
MY_ROOT_PATH := $(LOCAL_PATH)

include $(MY_ROOT_PATH)/asmjit/Android.mk
include $(MY_ROOT_PATH)/Dobby/Android.mk
include $(MY_ROOT_PATH)/Frida/Android.mk

LOCAL_PATH := $(MY_ROOT_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := Tool
LOCAL_CFLAGS := -w -s -Wno-error=format-security -fvisibility=hidden -fpermissive -fexceptions
LOCAL_CPPFLAGS := -w -s -Wno-error=format-security -fvisibility=hidden -std=c++17 -Wno-error=c++11-narrowing -fpermissive -fexceptions $hookEngine
LOCAL_LDFLAGS += -Wl,--gc-sections,--strip-all
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv3 -ldl -latomic -lz -lm -lc
LOCAL_ARM_MODE := arm
LOCAL_C_INCLUDES += $(MY_ROOT_PATH) $(MY_ROOT_PATH)/imgui $(MY_ROOT_PATH)/asmjit $(MY_ROOT_PATH)/Dobby $(MY_ROOT_PATH)/Dobby/include

LOCAL_STATIC_LIBRARIES := asmjit dobby
ifeq ($(USE_FRIDA),1)
LOCAL_STATIC_LIBRARIES += frida_gum
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/Frida/gumpp $(MY_ROOT_PATH)/Frida/$(TARGET_ARCH_ABI)
LOCAL_SRC_FILES += Frida/gumpp/runtime.cpp Frida/gumpp/backtracer.cpp Frida/gumpp/interceptor.cpp
endif

LOCAL_SRC_FILES := \
    Main.cpp Menu/ImGui.cpp Tool/Keyboard.cpp Tool/Tool.cpp Tool/Util.cpp \
    Tool/Patcher.cpp Tool/PopUpSelector.cpp Tool/ClassesTab.cpp Tool/Unity.cpp \
    Includes/Utils.cpp Includes/Logger.cpp \
    KittyMemory/KittyMemory.cpp KittyMemory/MemoryPatch.cpp KittyMemory/MemoryBackup.cpp KittyMemory/KittyUtils.cpp \
    Il2cpp/Il2cpp.cpp Il2cpp/il2cpp-class.cpp \
    Il2cpp/xdl/xdl.c Il2cpp/xdl/xdl_iterate.c Il2cpp/xdl/xdl_linker.c Il2cpp/xdl/xdl_lzma.c Il2cpp/xdl/xdl_util.c \
    imgui/imgui_widgets.cpp imgui/imgui_draw.cpp imgui/imgui_demo.cpp imgui/imgui.cpp imgui/imgui_tables.cpp \
    imgui/backends/imgui_impl_opengl3.cpp imgui/backends/imgui_impl_android.cpp

include $(BUILD_SHARED_LIBRARY)
"""

    return Triple(mainCpp, manifest, androidMk)
}

@Composable
private fun TemplateFile(name: String, content: String, onCopy: () -> Unit, onSave: () -> Unit) {
    DarkCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = AccentCyan, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (content.isNotEmpty()) {
            Text(text = content, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface).padding(8.dp).heightIn(max = 250.dp).verticalScroll(rememberScrollState()))
        } else {
            Text("Click 'Generate All Code' first", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

private suspend fun saveToFile(context: Context, name: String, content: String) = withContext(Dispatchers.IO) {
    val dir = File(context.getExternalFilesDir(null), "il2cpp-tool")
    dir.mkdirs()
    File(dir, name).outputStream().use { it.write(content.toByteArray()) }
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}
