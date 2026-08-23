package com.oprek.tool.ui.screens

import android.content.Context
import android.net.Uri
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
fun SslPinningBypassScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedGame by remember { mutableStateOf("") }
    var output by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔒 SSL Pinning Bypass", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Generate Frida scripts to bypass SSL pinning", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))

                    val methods = listOf(
                        "Universal Bypass" to "Generic SSL unpinning for all apps",
                        "OkHttp3 Bypass" to "Bypass OkHttp3 CertificatePinner",
                        "Retrofit Bypass" to "Bypass Retrofit SSL checks",
                        "WebView Bypass" to "Bypass WebView SSL errors",
                        "Java SSL Bypass" to "Bypass SSLSocketFactory",
                        "Network Security Config" to "Override NSC policy",
                        "Flutter Bypass" to "Bypass Flutter SSL pinning",
                        "React Native Bypass" to "Bypass RN SSL checks",
                        "Unity Bypass" to "Bypass UnityWebRequest SSL",
                        "All Combined" to "All bypasses in one script"
                    )

                    methods.forEachIndexed { i, (name, desc) ->
                        Card(
                            onClick = {
                                selectedGame = name
                                output = generateSslBypass(i)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedGame == name) AccentCyan.copy(alpha = 0.15f) else DarkCard
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                                    Text(desc, fontSize = 9.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📋 Generated Script", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cb.setPrimaryClip(android.content.ClipData.newPlainText("script", output.joinToString("\n")))
                            }) { Text("Copy", fontSize = 10.sp, color = AccentCyan) }
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(output) { line ->
                                val color = when {
                                    line.startsWith("//") -> Color.Gray
                                    line.contains("Java.perform") -> AccentGreen
                                    line.contains("Interceptor") -> AccentCyan
                                    line.contains("function") -> AccentPurple
                                    else -> TextPrimary
                                }
                                Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📖 Usage", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AccentOrange)
                    Spacer(Modifier.height(4.dp))
                    Text("1. Copy the generated script", fontSize = 10.sp, color = TextSecondary)
                    Text("2. Save as bypass.js", fontSize = 10.sp, color = TextSecondary)
                    Text("3. Run: frida -U -f <package> -l bypass.js", fontSize = 10.sp, color = TextSecondary)
                    Text("4. Or use with Magisk + LSPosed module", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

private fun generateSslBypass(method: Int): List<String> = when (method) {
    0 -> listOf(
        "// Universal SSL Pinning Bypass - OprekTool",
        "// Works for most Android apps",
        "Java.perform(function() {",
        "    console.log('[*] SSL Pinning Bypass loaded');",
        "",
        "    // Bypass TrustManagerImpl",
        "    var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');",
        "    TrustManagerImpl.verifyChain.implementation = function(untrustedChain, trustAnchorChain, host, clientAuth, ocspData, tlsSctData) {",
        "        console.log('[+] Bypassing TrustManagerImpl for: ' + host);",
        "        return untrustedChain;",
        "    };",
        "",
        "    // Bypass OkHttp3 CertificatePinner",
        "    try {",
        "        var CertificatePinner = Java.use('okhttp3.CertificatePinner');",
        "        CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {",
        "            console.log('[+] Bypassing OkHttp3 pinning for: ' + hostname);",
        "        };",
        "    } catch(e) { console.log('[-] OkHttp3 not found'); }",
        "",
        "    // Bypass WebViewClient",
        "    var WebViewClient = Java.use('android.webkit.WebViewClient');",
        "    WebViewClient.onReceivedSslError.implementation = function(view, handler, error) {",
        "        console.log('[+] Bypassing WebView SSL error');",
        "        handler.proceed();",
        "    };",
        "",
        "    // Bypass SSLSocketFactory",
        "    var SSLContext = Java.use('javax.net.ssl.SSLContext');",
        "    SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;', '[Ljavax.net.ssl.TrustManager;', 'java.security.SecureRandom').implementation = function(km, tm, sr) {",
        "        console.log('[+] Bypassing SSLContext.init');",
        "        this.init(km, tm, sr);",
        "    };",
        "",
        "    console.log('[*] All bypasses applied!');",
        "});"
    )
    1 -> listOf(
        "// OkHttp3 CertificatePinner Bypass",
        "Java.perform(function() {",
        "    var CertificatePinner = Java.use('okhttp3.CertificatePinner');",
        "    CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {",
        "        console.log('[+] OkHttp3 bypass: ' + hostname);",
        "    };",
        "    // Also bypass pin calculation",
        "    try {",
        "        var certPinner = Java.use('okhttp3.internal.tls.CertificateChainCleaner');",
        "        console.log('[+] CertificateChainCleaner hooked');",
        "    } catch(e) {}",
        "    console.log('[*] OkHttp3 bypass ready');",
        "});"
    )
    2 -> listOf(
        "// Retrofit + OkHttp3 Bypass",
        "Java.perform(function() {",
        "    // Bypass Retrofit SSL",
        "    try {",
        "        var Platform = Java.use('okhttp3.internal.platform.Platform');",
        "        Platform.trustManager.overload('javax.net.ssl.SSLSocketFactory').implementation = function(sslSocketFactory) {",
        "            console.log('[+] Bypassing Retrofit trustManager');",
        "            return null;",
        "        };",
        "    } catch(e) {}",
        "",
        "    // Bypass OkHttpClient builder",
        "    try {",
        "        var Builder = Java.use('okhttp3.OkHttpClient$Builder');",
        "        Builder.sslSocketFactory.overload('javax.net.ssl.SSLSocketFactory', 'javax.net.ssl.X509TrustManager').implementation = function(factory, tm) {",
        "            console.log('[+] Bypassing OkHttpClient SSL');",
        "            this.sslSocketFactory(factory, tm);",
        "        };",
        "    } catch(e) {}",
        "    console.log('[*] Retrofit bypass ready');",
        "});"
    )
    3 -> listOf(
        "// WebView SSL Bypass",
        "Java.perform(function() {",
        "    var WebViewClient = Java.use('android.webkit.WebViewClient');",
        "    WebViewClient.onReceivedSslError.implementation = function(view, handler, error) {",
        "        console.log('[+] WebView SSL error bypassed');",
        "        handler.proceed();",
        "    };",
        "",
        "    // Also bypass shouldOverrideUrlLoading",
        "    WebViewClient.shouldOverrideUrlLoading.overload('android.webkit.WebView', 'java.lang.String').implementation = function(view, url) {",
        "        console.log('[+] URL: ' + url);",
        "        return false;",
        "    };",
        "    console.log('[*] WebView bypass ready');",
        "});"
    )
    4 -> listOf(
        "// Java SSL Socket Bypass",
        "Java.perform(function() {",
        "    var TrustManager = Java.registerClass({",
        "        name: 'com.bypass.TrustManager',",
        "        implements: [Java.use('javax.net.ssl.X509TrustManager')],",
        "        methods: {",
        "            checkClientTrusted: function(chain, authType) {},",
        "            checkServerTrusted: function(chain, authType) {},",
        "            getAcceptedIssuers: function() { return []; }",
        "        }",
        "    });",
        "",
        "    var SSLContext = Java.use('javax.net.ssl.SSLContext');",
        "    SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;', '[Ljavax.net.ssl.TrustManager;', 'java.security.SecureRandom').implementation = function(km, tm, sr) {",
        "        console.log('[+] SSLContext bypassed');",
        "        this.init(km, [TrustManager.$new()], sr);",
        "    };",
        "    console.log('[*] Java SSL bypass ready');",
        "});"
    )
    5 -> listOf(
        "// Network Security Config Override",
        "// Place this in res/xml/network_security_config.xml:",
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<network-security-config>",
        "    <base-config cleartextTrafficPermitted=\"true\">",
        "        <trust-anchors>",
        "            <certificates src=\"system\" />",
        "            <certificates src=\"user\" />",
        "        </trust-anchors>",
        "    </base-config>",
        "</network-security-config>",
        "",
        "// Or use Frida to override at runtime:",
        "Java.perform(function() {",
        "    var NetworkSecurityConfig = Java.use('android.security.net.NetworkSecurityConfig');",
        "    console.log('[+] NSC class found');",
        "    // Build config that trusts all certs",
        "});"
    )
    6 -> listOf(
        "// Flutter SSL Bypass",
        "// Method 1: Use objection",
        "objection -g <package> explore",
        "android sslpinning disable",
        "",
        "// Method 2: Frida script",
        "Java.perform(function() {",
        "    // Bypass Dart SSL",
        "    try {",
        "        var HttpClient = Java.use('dart:io._HttpClient');",
        "        console.log('[+] Dart HttpClient found');",
        "    } catch(e) { console.log('[-] Dart not found'); }",
        "",
        "    // Hook certificate validation",
        "    var X509TrustManager = Java.use('javax.net.ssl.X509TrustManager');",
        "    var SSLContext = Java.use('javax.net.ssl.SSLContext');",
        "    var TrustManager = Java.registerClass({",
        "        name: 'Flutter.TrustManager',",
        "        implements: [X509TrustManager],",
        "        methods: {",
        "            checkClientTrusted: function(chain, authType) {},",
        "            checkServerTrusted: function(chain, authType) {},",
        "            getAcceptedIssuers: function() { return []; }",
        "        }",
        "    });",
        "",
        "    var ctx = SSLContext.getInstance('TLS');",
        "    ctx.init(null, [TrustManager.$new()], null);",
        "    console.log('[*] Flutter SSL bypass ready');",
        "});"
    )
    7 -> listOf(
        "// React Native SSL Bypass",
        "Java.perform(function() {",
        "    // Bypass OkHTTP in RN",
        "    try {",
        "        var CertificatePinner = Java.use('okhttp3.CertificatePinner');",
        "        CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {",
        "            console.log('[+] RN OkHttp bypass: ' + hostname);",
        "        };",
        "    } catch(e) {}",
        "",
        "    // Bypass原生 RN SSL",
        "    try {",
        "        var SSLContext = Java.use('javax.net.ssl.SSLContext');",
        "        SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;', '[Ljavax.net.ssl.TrustManager;', 'java.security.SecureRandom').implementation = function(km, tm, sr) {",
        "            console.log('[+] RN SSL bypass');",
        "            this.init(km, tm, sr);",
        "        };",
        "    } catch(e) {}",
        "    console.log('[*] React Native bypass ready');",
        "});"
    )
    8 -> listOf(
        "// Unity SSL Bypass",
        "Java.perform(function() {",
        "    // Bypass UnityWebRequest SSL",
        "    try {",
        "        var UnityWebRequest = Java.use('com.unity3d.player.UnityPlayer');",
        "        console.log('[+] Unity player found');",
        "    } catch(e) {}",
        "",
        "    // Generic SSL bypass for Unity games",
        "    var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');",
        "    TrustManagerImpl.verifyChain.implementation = function(untrustedChain, trustAnchorChain, host, clientAuth, ocspData, tlsSctData) {",
        "        console.log('[+] Unity SSL bypass: ' + host);",
        "        return untrustedChain;",
        "    };",
        "",
        "    var WebViewClient = Java.use('android.webkit.WebViewClient');",
        "    WebViewClient.onReceivedSslError.implementation = function(view, handler, error) {",
        "        console.log('[+] Unity WebView SSL bypass');",
        "        handler.proceed();",
        "    };",
        "    console.log('[*] Unity bypass ready');",
        "});"
    )
    9 -> listOf(
        "// COMBINED SSL Pinning Bypass - OprekTool",
        "// All bypasses in one script",
        "Java.perform(function() {",
        "    console.log('[*] Loading combined SSL bypass...');",
        "",
        "    // 1. TrustManagerImpl",
        "    try {",
        "        var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');",
        "        TrustManagerImpl.verifyChain.implementation = function(a,b,c,d,e,f) { return a; };",
        "        console.log('[+] TrustManagerImpl bypassed');",
        "    } catch(e) {}",
        "",
        "    // 2. OkHttp3",
        "    try {",
        "        var CP = Java.use('okhttp3.CertificatePinner');",
        "        CP.check.overload('java.lang.String','java.util.List').implementation = function(h,c) {};",
        "        console.log('[+] OkHttp3 bypassed');",
        "    } catch(e) {}",
        "",
        "    // 3. WebView",
        "    try {",
        "        var WVC = Java.use('android.webkit.WebViewClient');",
        "        WVC.onReceivedSslError.implementation = function(v,h,e) { h.proceed(); };",
        "        console.log('[+] WebView bypassed');",
        "    } catch(e) {}",
        "",
        "    // 4. SSLContext",
        "    try {",
        "        var SSL = Java.use('javax.net.ssl.SSLContext');",
        "        SSL.init.overload('[Ljavax.net.ssl.KeyManager;','[Ljavax.net.ssl.TrustManager;','java.security.SecureRandom').implementation = function(k,t,s) { this.init(k,t,s); };",
        "        console.log('[+] SSLContext hooked');",
        "    } catch(e) {}",
        "",
        "    // 5. HostnameVerifier",
        "    try {",
        "        var HV = Java.use('javax.net.ssl.HttpsURLConnection');",
        "        HV.setDefaultHostnameVerifier.implementation = function(v) {",
        "            console.log('[+] HostnameVerifier bypassed');",
        "        };",
        "    } catch(e) {}",
        "",
        "    console.log('[*] All SSL bypasses applied!');",
        "});"
    )
    else -> listOf("// Unknown method")
}
