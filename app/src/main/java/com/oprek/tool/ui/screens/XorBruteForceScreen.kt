package com.oprek.tool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XorBruteForceScreen(navController: NavController) {
    var input by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Pair<Int, String>>()) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("XOR Brute Force", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it },
                label = { Text("Hex string or text to brute-force") }, modifier = Modifier.fillMaxWidth(),
                colors = darkTextFieldColors())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                results = mutableListOf()
                val bytes = try {
                    input.replace(" ", "").chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                } catch (_: Exception) { input.toByteArray() }
                val scored = mutableListOf<Pair<Int, String>>()
                for (k in 0..255) {
                    val decoded = bytes.map { (it.toInt() xor k).toChar() }.joinToString("")
                    val score = decoded.count { it.code in 0x20..0x7E || it == '\n' || it == '\r' }
                    val entropy = calcEntropySimple(decoded.toByteArray())
                    if (score > bytes.size * 0.5 && entropy < 5.0) {
                        scored.add(k to decoded)
                    }
                }
                results = scored.sortedByDescending { it.second.count { c -> c.isLetter() } }.take(20)
            }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Brute Force") }
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(results) { _, (key, decoded) ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Key: 0x${"%02X".format(key)} (${key})", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 11.sp)
                            Text(decoded, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

    }
}

private fun calcEntropySimple(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val freq = IntArray(256); for (b in data) freq[b.toInt() and 0xFF]++
    var e = 0.0
    for (f in freq) if (f > 0) { val p = f.toDouble() / data.size; e -= p * ln(p) / ln(2.0) }
    return e
}
