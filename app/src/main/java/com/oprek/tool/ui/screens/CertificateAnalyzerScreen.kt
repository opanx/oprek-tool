package com.oprek.tool.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.util.jar.JarFile
import javax.security.auth.x500.X500Principal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isAnalyzing = true
            output = listOf("[*] Analyzing certificate...")
            scope.launch(Dispatchers.IO) {
                val result = analyzeCertificate(context, it)
                withContext(Dispatchers.Main) { output = result; isAnalyzing = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 Certificate Analyzer", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Select APK to analyze certificate", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select APK", fontSize = 11.sp)
                    }
                }
            }
            if (isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentCyan)
            Card(Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Certificate Info (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(output) { line ->
                            val color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
                                line.startsWith("[*]") -> AccentCyan
                                else -> TextPrimary
                            }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun analyzeCertificate(context: Context, uri: Uri): List<String> {
    val result = mutableListOf<String>()
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open file")
        val bytes = inputStream.readBytes()
        inputStream.close()

        // Find META-INF directory for certificate
        val tempFile = File(context.cacheDir, "cert_check.apk")
        tempFile.writeBytes(bytes)

        val jarFile = JarFile(tempFile)
        val entries = jarFile.entries()
        var certFound = false

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                certFound = true
                result.add("[+] Certificate found: ${entry.name}")

                val certStream = jarFile.getInputStream(entry)
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(certStream)
                certStream.close()

                result.add("[+] Type: ${cert.type}")
                result.add("[+] Algorithm: ${cert.publicKey.algorithm}")
                result.add("[+] Format: ${cert.publicKey.format}")

                val digest = MessageDigest.getInstance("SHA-256")
                val certBytes = cert.encoded
                digest.update(certBytes)
                val sha256 = digest.digest().joinToString(":") { "%02X".format(it) }
                result.add("[+] SHA-256: $sha256")

                val md5 = MessageDigest.getInstance("MD5")
                md5.update(certBytes)
                result.add("[+] MD5: ${md5.digest().joinToString(":") { "%02X".format(it) }}")

                // Principal info
                val x500 = cert as? java.security.cert.X509Certificate
                if (x500 != null) {
                    result.add("")
                    result.add("[+] Subject: ${x500.subjectDN}")
                    result.add("[+] Issuer: ${x500.issuerDN}")
                    result.add("[+] Serial: ${x500.serialNumber}")
                    result.add("[+] Not Before: ${x500.notBefore}")
                    result.add("[+] Not After: ${x500.notAfter}")

                    val now = java.util.Date()
                    if (now.after(x500.notAfter)) {
                        result.add("[!] ⚠️ Certificate EXPIRED!")
                    } else if (now.before(x500.notBefore)) {
                        result.add("[!] ⚠️ Certificate NOT YET VALID!")
                    } else {
                        result.add("[+] ✅ Certificate is VALID")
                        val daysLeft = ((x500.notAfter.time - now.time) / 86400000).toInt()
                        result.add("[+] Days until expiry: $daysLeft")
                    }

                    // Signature algorithm check
                    val sigAlg = x500.sigAlgName
                    result.add("[+] Signature Algorithm: $sigAlg")
                    if (sigAlg.contains("SHA1") || sigAlg.contains("MD5")) {
                        result.add("[!] ⚠️ WEAK signature algorithm detected!")
                    } else {
                        result.add("[+] ✅ Strong signature algorithm")
                    }

                    // V1/V2/V3 check
                    result.add("[+] Version: V${x500.version}")
                    if (x500.version < 3) {
                        result.add("[!] ⚠️ Old certificate version (V${x500.version})")
                    }

                    result.add("")
                    result.add("[+] Extensions:")
                    try {
                        val exts = x500.nonCriticalExtensionOIDs
                        exts?.forEach { oid ->
                            result.add("    - OID: $oid")
                        }
                        if (exts.isNullOrEmpty()) result.add("    (none)")
                    } catch (e: Exception) {
                        result.add("    (unable to list)")
                    }
                }

                // Check for debug signing
                val subject = cert.toString()
                if (subject.contains("Android Debug") || subject.contains("CN=Android")) {
                    result.add("")
                    result.add("[!] ⚠️ DEBUG signing key detected!")
                    result.add("[!] This APK is signed with a debug keystore")
                }

                // Check for test keys
                if (subject.contains("Test") || subject.contains("test")) {
                    result.add("[!] ⚠️ TEST signing key detected!")
                }
            }
        }

        if (!certFound) {
            result.add("[-] No certificate found in META-INF/")
            result.add("[*] APK might be unsigned or use APK Signature Scheme v2/v3")
            result.add("[*] Checking for signature block...")

            // Check for v2/v3 signature
            val sigBlockMagic = byteArrayOf(0x32, 0x4B, 0x50, 0x4B) // APK Sig Block v2
            val found = bytes.indexOf(sigBlockMagic)
            if (found >= 0) {
                result.add("[+] APK Signature Scheme v2/v3 block found at offset $found")
            } else {
                result.add("[-] No signature block found")
            }
        }

        tempFile.delete()
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}

// Extension function to find byte array
private fun ByteArray.indexOf(pattern: ByteArray, startOffset: Int = 0): Int {
    for (i in startOffset until this.size - pattern.size + 1) {
        var match = true
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) { match = false; break }
        }
        if (match) return i
    }
    return -1
}
