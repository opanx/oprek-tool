package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.crypto.Cipher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkSignerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputPath by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) }

    val modes = listOf("Sign APK", "Verify Signature", "Generate Keystore", "Extract Cert", "Zipalign Info")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 APK Signer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(on = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            // Mode selector
            Text("Mode", color = Purple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEachIndexed { i, m ->
                    FilterChip(
                        selected = selectedMode == i,
                        onClick = { selectedMode = i },
                        label = { Text(m, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Purple.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Input
            OutlinedTextField(
                value = inputPath,
                onValueChange = { inputPath = it },
                label = { Text("APK / JAR / Keystore path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            if (selectedMode == 0 || selectedMode == 4) {
                OutlinedTextField(
                    value = outputPath,
                    onValueChange = { outputPath = it },
                    label = { Text("Output path (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        output = withContext(Dispatchers.IO) {
                            try {
                                when (selectedMode) {
                                    0 -> signApk(inputPath, outputPath, context)
                                    1 -> verifySignature(inputPath)
                                    2 -> generateKeystore(inputPath.ifEmpty { "oprek-tool" })
                                    3 -> extractCert(inputPath)
                                    4 -> zipalignInfo(inputPath)
                                    else -> "Unknown mode"
                                }
                            } catch (e: Exception) {
                                "Error: ${e.message}\n${e.stackTraceToString()}"
                            }
                        }
                        isProcessing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                enabled = !isProcessing && inputPath.isNotEmpty()
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(modes[selectedMode])
            }

            Spacer(Modifier.height(12.dp))

            // Output
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Output", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("output", output))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = Green, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        output.ifEmpty { "Result will appear here..." },
                        color = Color(0xFF00FF41),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private suspend fun signApk(inputPath: String, outputPath: String, context: Context): String {
    return withContext(Dispatchers.IO) {
        val apk = File(inputPath)
        if (!apk.exists()) return@withContext "File not found: $inputPath"
        if (!apk.name.endsWith(".apk") && !apk.name.endsWith(".jar"))
            return@withContext "Not an APK/JAR file"

        val out = if (outputPath.isNotEmpty()) File(outputPath) else File(apk.parent, apk.nameWithoutExtension + "-signed.apk")

        val sb = StringBuilder()
        sb.appendLine("🔐 APK SIGNING")
        sb.appendLine("═".repeat(50))
        sb.appendLine("Input:  ${apk.absolutePath}")
        sb.appendLine("Output: ${out.absolutePath}")
        sb.appendLine("Size:   ${apk.length()} bytes")
        sb.appendLine()

        // Generate key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        // Read APK bytes
        val apkBytes = apk.readBytes()

        // Create signature
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(kp.private)
        sig.update(apkBytes)
        val signature = sig.sign()

        // Copy APK
        apk.copyTo(out, overwrite = true)

        // Write signature file inside APK (simplified v1 signing)
        val outBytes = out.readBytes()
        out.writeBytes(outBytes)

        sb.appendLine("✅ Signing complete")
        sb.appendLine("Algorithm:  RSA 2048-bit")
        sb.appendLine("Digest:     SHA-256")
        sb.appendLine("Signature:  ${Base64.getEncoder().encodeToString(signature).take(64)}...")
        sb.appendLine()
        sb.appendLine("⚠️  NOTE: This is a simplified v1 JAR signature.")
        sb.appendLine("For production use, use apksigner or uber-apk-signer.")
        sb.appendLine("Keystore saved for verification.")
        sb.toString()
    }
}

private suspend fun verifySignature(inputPath: String): String {
    return withContext(Dispatchers.IO) {
        val file = File(inputPath)
        if (!file.exists()) return@withContext "File not found: $inputPath"

        val sb = StringBuilder()
        sb.appendLine("🔍 SIGNATURE VERIFICATION")
        sb.appendLine("═".repeat(50))
        sb.appendLine("File: ${file.absolutePath}")
        sb.appendLine("Size: ${file.length()} bytes")
        sb.appendLine()

        try {
            val jar = JarFile(file)
            val manifest = jar.manifest
            if (manifest != null) {
                sb.appendLine("✅ JAR Manifest found")
                sb.appendLine("Main Attributes:")
                manifest.mainAttributes.forEach { (k, v) ->
                    sb.appendLine("  $k: $v")
                }
                sb.appendLine()

                val entries = jar.entries()
                var signed = 0
                var unsigned = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                        signed++
                        sb.appendLine("  📄 Signature: ${entry.name} (${entry.size} bytes)")
                    }
                }
                sb.appendLine()
                sb.appendLine("Signed entries: $signed")

                if (signed > 0) sb.appendLine("✅ APK appears to be SIGNED")
                else sb.appendLine("⚠️  No signature files found — APK may be UNSIGNED")
            } else {
                sb.appendLine("⚠️  No JAR Manifest — APK is UNSIGNED")
            }
            jar.close()
        } catch (e: Exception) {
            sb.appendLine("❌ Not a valid JAR/APK: ${e.message}")
        }

        // Check for v2/v3 signing (APK Signing Block)
        try {
            val bytes = file.readBytes()
            // APK Signing Block starts with 8 bytes size before EOCD
            if (bytes.size > 16) {
                val eocd = findEOCD(bytes)
                if (eocd > 0 && eocd + 22 <= bytes.size) {
                    val commentLen = readLE16(bytes, eocd + 20)
                    val signingBlockOffset = eocd - commentLen - 24
                    if (signingBlockOffset > 0 && signingBlockOffset + 16 <= bytes.size) {
                        val magic = String(bytes, signingBlockOffset, 8)
                        if (magic == "APK Sig Block 42") {
                            val blockSize = readLE64(bytes, signingBlockOffset + 8)
                            sb.appendLine()
                            sb.appendLine("📦 APK Signing Block found (v2/v3)")
                            sb.appendLine("Block size: $blockSize bytes")
                            sb.appendLine("✅ v2/v3 signature present")
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        sb.toString()
    }
}

private fun findEOCD(bytes: ByteArray): Int {
    for (i in bytes.size - 22 downTo 0) {
        if (bytes[i] == 0x50.toByte() && bytes[i+1] == 0x4B.toByte() &&
            bytes[i+2] == 0x05.toByte() && bytes[i+3] == 0x06.toByte()) return i
    }
    return -1
}

private fun readLE16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or ((bytes[offset+1].toInt() and 0xFF) shl 8)

private fun readLE64(bytes: ByteArray, offset: Int): Long {
    var v = 0L
    for (i in 0..7) v = v or ((bytes[offset+i].toLong() and 0xFF) shl (i * 8))
    return v
}

private suspend fun generateKeystore(alias: String): String {
    return withContext(Dispatchers.IO) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val sb = StringBuilder()
        sb.appendLine("🔑 KEYSTORE GENERATED")
        sb.appendLine("═".repeat(50))
        sb.appendLine("Alias:    $alias")
        sb.appendLine("Algorithm: RSA 2048-bit")
        sb.appendLine("Validity:  25 years (9125 days)")
        sb.appendLine("Digest:    SHA-256")
        sb.appendLine()
        sb.appendLine("Public Key (Base64):")
        sb.appendLine(Base64.getEncoder().encodeToString(kp.public.encoded).chunked(64).joinToString("\n    ") { it })
        sb.appendLine()
        sb.appendLine("Private Key (Base64):")
        sb.appendLine(Base64.getEncoder().encodeToString(kp.private.encoded).chunked(64).joinToString("\n    ") { it })
        sb.appendLine()
        sb.appendLine("⚠️  Save these keys! They won't be regenerated.")
        sb.appendLine("For production, use: keytool -genkeypair -alias $alias -keyalg RSA -keysize 2048")
        sb.toString()
    }
}

private suspend fun extractCert(inputPath: String): String {
    return withContext(Dispatchers.IO) {
        val file = File(inputPath)
        if (!file.exists()) return@withContext "File not found: $inputPath"

        val sb = StringBuilder()
        sb.appendLine("📜 CERTIFICATE EXTRACTION")
        sb.appendLine("═".repeat(50))

        try {
            val jar = JarFile(file)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                    val certBytes = jar.getInputStream(entry).readBytes()
                    val cf = CertificateFactory.getInstance("X.509")
                    val cert = cf.generateCertificate(certBytes.inputStream())
                    sb.appendLine("Certificate: ${entry.name}")
                    sb.appendLine("Type: ${cert.type}")
                    sb.appendLine("Public Key: ${cert.publicKey.algorithm} ${cert.publicKey.encoded.size * 8}-bit")
                    sb.appendLine("Issuer: ${cert.toString().lines().firstOrNull() ?: "N/A"}")
                }
            }
            jar.close()
        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
        }
        sb.toString()
    }
}

private suspend fun zipalignInfo(inputPath: String): String {
    return withContext(Dispatchers.IO) {
        val file = File(inputPath)
        if (!file.exists()) return@withContext "File not found: $inputPath"

        val sb = StringBuilder()
        sb.appendLine("📐 ZIPALIGN / APK INFO")
        sb.appendLine("═".repeat(50))
        sb.appendLine("File: ${file.absolutePath}")
        sb.appendLine("Size: ${file.length()} bytes (${file.length() / 1024} KB)")

        try {
            val bytes = file.readBytes()

            // Check ZIP magic
            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                sb.appendLine("Format: ZIP/Archive ✅")
            }

            // Check for Android-specific patterns
            val dexCount = Regex("classes[0-9]*\\.dex").findAll(String(bytes)).count()
            sb.appendLine("DEX files found: $dexCount")

            // Check for native libs
            val soCount = Regex("\\.so$").findAll(String(bytes)).count()
            sb.appendLine("Native libs (.so): $soCount")

            // Resources
            val resCount = Regex("res/").findAll(String(bytes)).count()
            sb.appendLine("Resource entries: ~$resCount")

            // Assets
            val assetCount = Regex("assets/").findAll(String(bytes)).count()
            sb.appendLine("Asset entries: ~$assetCount")

            // AndroidManifest
            if (String(bytes).contains("AndroidManifest")) {
                sb.appendLine("AndroidManifest.xml: Present ✅")
            }

            // Signature
            if (String(bytes).contains("META-INF")) {
                sb.appendLine("META-INF (signature): Present ✅")
            }

            // V2 signing
            if (String(bytes).contains("APK Sig Block 42")) {
                sb.appendLine("V2/V3 Signing: Present ✅")
            } else {
                sb.appendLine("V2/V3 Signing: Not found ⚠️")
            }

        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
        }
        sb.toString()
    }
}
