package com.oprek.tool.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.oprek.tool.core.FileUtils
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffToolScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file1Name by remember { mutableStateOf("No file") }
    var file2Name by remember { mutableStateOf("No file") }
    var file1Data by remember { mutableStateOf<ByteArray?>(null) }
    var file2Data by remember { mutableStateOf<ByteArray?>(null) }
    var diffResult by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }

    val pick1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = FileUtils.getFileName(context, it)
            val tmp = FileUtils.getTempFile(context, "diff1_$name")
            FileUtils.copyUriToFile(context, it, tmp)
            file1Name = name; file1Data = tmp.readBytes()
        }
    }
    val pick2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = FileUtils.getFileName(context, it)
            val tmp = FileUtils.getTempFile(context, "diff2_$name")
            FileUtils.copyUriToFile(context, it, tmp)
            file2Name = name; file2Data = tmp.readBytes()
        }
    }

    // Auto-compare when both files loaded
    LaunchedEffect(file1Data, file2Data) {
        val d1 = file1Data; val d2 = file2Data
        if (d1 != null && d2 != null) {
            val sb = StringBuilder(); var diffs = 0
            val maxLen = maxOf(d1.size, d2.size)
            for (i in 0 until minOf(maxLen, 4096)) {
                val b1 = if (i < d1.size) d1[i].toInt() and 0xFF else -1
                val b2 = if (i < d2.size) d2[i].toInt() and 0xFF else -1
                if (b1 != b2) { sb.appendLine("0x${"%08X".format(i)}: ${if (b1 >= 0) "%02X".format(b1) else "??"} != ${if (b2 >= 0) "%02X".format(b2) else "??"}"); diffs++ }
            }
            diffResult = sb.toString().ifBlank { "Files are identical (first 4096 bytes)" }
            stats = "Diffs: $diffs | File A: ${d1.size}B | File B: ${d2.size}B"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("⚖️ Diff Tool", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // File selectors
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f).clickable { pick1.launch(arrayOf("*/*")) }, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📄", fontSize = 24.sp)
                        Text("File A", fontSize = 12.sp, color = AccentPurple, fontWeight = FontWeight.Bold)
                        Text(file1Name, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Card(Modifier.weight(1f).clickable { pick2.launch(arrayOf("*/*")) }, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📄", fontSize = 24.sp)
                        Text("File B", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                        Text(file2Name, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                scope.launch(Dispatchers.Default) {
                    val d1 = file1Data ?: return@launch
                    val d2 = file2Data ?: return@launch
                    val sb = StringBuilder()
                    var diffs = 0
                    val maxLen = maxOf(d1.size, d2.size)
                    for (i in 0 until minOf(maxLen, 4096)) {
                        val b1 = if (i < d1.size) d1[i].toInt() and 0xFF else -1
                        val b2 = if (i < d2.size) d2[i].toInt() and 0xFF else -1
                        if (b1 != b2) {
                            sb.appendLine("0x${"%08X".format(i)}: ${if (b1 >= 0) "%02X".format(b1) else "??"} != ${if (b2 >= 0) "%02X".format(b2) else "??"}")
                            diffs++
                        }
                    }
                    diffResult = sb.toString().ifBlank { "Files are identical (first 4096 bytes)" }
                    stats = "Diffs: $diffs | File A: ${d1.size}B | File B: ${d2.size}B"
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple), shape = RoundedCornerShape(12.dp)) {
                Text("Compare", fontWeight = FontWeight.Bold)
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stats, fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
            }
            if (diffResult.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Text(diffResult, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                        modifier = Modifier.padding(12.dp).fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { diffResult },
                filename = "diff.txt",
                subfolder = "diff"
            )

        }
    }
}
