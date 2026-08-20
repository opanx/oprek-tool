package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlin.math.ln
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnpackerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var fileName by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var packer by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try {
                val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf()
                val name = uri.lastPathSegment ?: "unknown"
                withContext(Dispatchers.Main) { fileBytes = b; fileName = name; loaded = true }
            } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Unpacker", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Packed Binary") } }
            if (loaded) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("File: $fileName", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Text("Size: ${fileBytes.size} bytes", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        // Detect packer
                        val hasUPX = findSubArray(fileBytes, "UPX!".toByteArray()) >= 0
                        val hasThemida = findSubArray(fileBytes, "Themida".toByteArray()) >= 0 || findSubArray(fileBytes, "WinLicense".toByteArray()) >= 0
                        val entropy = calcEntropy(fileBytes)
                        packer = when {
                            hasUPX -> "UPX Packed"
                            hasThemida -> "Themida/WinLicense"
                            entropy > 7.0 -> "High entropy (${String.format("%.2f", entropy)}) — likely encrypted/packed"
                            else -> "Unknown or not packed"
                        }
                        Text("Detection: $packer", fontWeight = FontWeight.Bold,
                            color = if (packer.contains("not")) AccentGreen else AccentRed, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (packer.contains("UPX")) {
                    Button(onClick = {
                        status = "Attempting UPX unpack... (requires UPX binary on device)"
                        // Search for UPX signature and try basic unpack
                        val upxIdx = findSubArray(fileBytes, "UPX!".toByteArray())
                        if (upxIdx >= 0) {
                            status = "UPX signature found at offset 0x${"%X".format(upxIdx)}\n" +
                                "Manual: Run 'upx -d $fileName' on a Linux system\n" +
                                "Auto-decompilation requires UPX binary"
                        }
                    }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Unpack UPX") }
                }
                if (packer.contains("entropy")) {
                    Button(onClick = {
                        // Analyze sections by entropy
                        val sectionSize = fileBytes.size / 10
                        val results = StringBuilder("Entropy analysis (by ${sectionSize}-byte blocks):\n\n")
                        for (i in 0 until 10) {
                            val start = i * sectionSize
                            val end = minOf(start + sectionSize, fileBytes.size)
                            val ent = calcEntropy(fileBytes.copyOfRange(start, end))
                            val bar = "█".repeat((ent * 3).toInt())
                            results.append("0x${"%06X".format(start)}: ${String.format("%.2f", ent)} $bar\n")
                        }
                        status = results.toString()
                    }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) { Text("Analyze Entropy") }
                }
                Spacer(Modifier.height(8.dp))
                if (status.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                        Text(status, modifier = Modifier.padding(12.dp), color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { status },
                filename = "unpacker.txt",
                subfolder = "analysis"
            )

        }

    }
}

private fun calcEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val freq = IntArray(256); for (b in data) freq[b.toInt() and 0xFF]++
    var e = 0.0; for (f in freq) if (f > 0) { val p = f.toDouble() / data.size; e -= p * ln(p) / ln(2.0) }
    return e
}

private fun findSubArray(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty()) return -1
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
