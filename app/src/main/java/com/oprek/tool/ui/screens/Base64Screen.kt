package com.oprek.tool.ui.screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Color
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64Screen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("b64enc") }

    // Auto-detect and convert when file loaded
    LaunchedEffect(Unit) {
        val ctx = context
        val file = java.io.File(ctx.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 1000)) }
        input = bytes.joinToString(" ") { "%02X".format(it) }
        mode = "hexdec"
        output = String(bytes, Charsets.UTF_8).filter { it.code in 0x20..0x7E || it == '\n' }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔄 Encoder/Decoder", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("b64enc" to "B64 Enc", "b64dec" to "B64 Dec", "hexenc" to "Hex Enc", "hexdec" to "Hex Dec", "urle" to "URL Enc", "urld" to "URL Dec").forEach { (k, l) ->
                            FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(l, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("Input...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                output = try {
                    when (mode) {
                        "b64enc" -> android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
                        "b64dec" -> String(android.util.Base64.decode(input.trim(), android.util.Base64.DEFAULT))
                        "hexenc" -> input.toByteArray().joinToString(" ") { "%02X".format(it) }
                        "hexdec" -> input.replace("\\s".toRegex(), "").chunked(2).map { it.toInt(16).toChar() }.joinToString("")
                        "urle" -> java.net.URLEncoder.encode(input, "UTF-8")
                        "urld" -> java.net.URLDecoder.decode(input, "UTF-8")
                        else -> ""
                    }
                } catch (e: Exception) { "Error: ${e.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(12.dp)) {
                Text("Convert", fontWeight = FontWeight.Bold)
            }
            if (output.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan, modifier = Modifier.weight(1f))
                            IconButton(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("out", output)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentCyan) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(output, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { output },
                filename = "base64.txt",
                subfolder = "encode"
            )

        }
    }
}
