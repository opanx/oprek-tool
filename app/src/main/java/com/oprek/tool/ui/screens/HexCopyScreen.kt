package com.oprek.tool.ui.screens

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
import java.io.File
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Color
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexCopyScreen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var cArray by remember { mutableStateOf("") }
    var pyBytes by remember { mutableStateOf("") }
    var hexStr by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📋 Copy Bytes As...", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Input hex bytes (e.g. 7F 45 4C 46)", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("7F 45 4C 46 02 01 01 00", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val bytes = input.replace("\\s".toRegex(), "").chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                cArray = "unsigned char data[] = {\n  " + bytes.joinToString(", ") { "0x%02X".format(it) } + "\n};"
                pyBytes = "data = b'" + bytes.joinToString("") { "\\x%02X".format(it) } + "'"
                hexStr = bytes.joinToString(" ") { "%02X".format(it) }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                Text("Convert", fontWeight = FontWeight.Bold)
            }
            if (cArray.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                CopyBlock("C Array", cArray, context)
                Spacer(Modifier.height(8.dp))
                CopyBlock("Python bytes", pyBytes, context)
                Spacer(Modifier.height(8.dp))

                CopyBlock("Hex String", hexStr, context)
            }
        }
    }
}

@Composable
fun CopyBlock(title: String, content: String, context: Context) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText(title, content))
                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentPurple) }
            }
            Spacer(Modifier.height(4.dp))
            Text(content, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Hex copy complete" },
                filename = "hex_copy.txt",
                subfolder = "hex"
            )

        }
    }
}
