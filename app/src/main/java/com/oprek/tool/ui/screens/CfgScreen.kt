package com.oprek.tool.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BasicBlock(val addr: Long, val endAddr: Long, val insns: List<String>, val successors: List<Long>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfgScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var blocks by remember { mutableStateOf(listOf<BasicBlock>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hasNative by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { NativeLib.elfValidate(byteArrayOf(0x7F, 0x45, 0x4C, 0x46)); hasNative = true } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Control Flow Graph", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch(Dispatchers.Default) {
                        val file = context.cacheDir.listFiles()?.firstOrNull() ?: return@launch
                        val data = withContext(Dispatchers.IO) { StreamingIO.readRange(file, 0, minOf(file.length(), 50000L).toInt()) }
                        val disasm = withContext(Dispatchers.IO) { NativeLib.disassemble(data, 0, 1, 2, 300) }
                        blocks = buildCfg(disasm)
                    }}) { Icon(Icons.Default.Refresh, "Build CFG") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${blocks.size} blocks", fontSize = 12.sp, color = AccentCyan)
                    Text("Pinch to zoom, drag to pan", fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.CenterFocusWeak, "Reset", Modifier.size(16.dp), tint = AccentCyan)
                    }
                }
            }

            if (blocks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Tap Refresh to build CFG", color = TextSecondary)
                        if (!hasNative) Text("⚠️ Native library not loaded", color = AccentRed, fontSize = 12.sp)
                    }
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.2f, 3f)
                                offset += pan
                            }
                        }
                ) {
                    drawCfg(blocks, scale, offset)
                }
            }
        }
    }
}

private fun buildCfg(disasm: String): List<BasicBlock> {
    val blocks = mutableListOf<BasicBlock>()
    val lines = disasm.lines().filter { it.contains("0x") }
    if (lines.isEmpty()) return blocks

    var currentInsns = mutableListOf<String>()
    var currentStart = 0L
    var currentEnd = 0L
    var currentSuccs = mutableListOf<Long>()

    for (line in lines) {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 3) continue
        val addrStr = parts[0].removePrefix("0x")
        val addr = try { java.lang.Long.parseLong(addrStr, 16) } catch (_: Exception) { continue }
        val mnemonic = parts[2]

        currentInsns.add(line.trim())
        currentEnd = addr + 4

        // Branch instructions end a basic block
        if (mnemonic.startsWith("b.") || mnemonic == "b" || mnemonic == "br" || mnemonic == "ret" || mnemonic == "blr") {
            val target = parts.getOrElse(3) { "" }.removePrefix("#").removeSuffix(",")
            if (target.isNotEmpty() && target != "x30") {
                try { currentSuccs.add(java.lang.Long.parseLong(target.removePrefix("0x"), 16)) } catch (_: Exception) {}
            }
            if (mnemonic != "ret") currentSuccs.add(currentEnd) // fall-through
            blocks.add(BasicBlock(currentStart, currentEnd, currentInsns.toList(), currentSuccs.toList()))
            currentInsns = mutableListOf()
            currentSuccs = mutableListOf()
            currentStart = currentEnd
        }
    }
    if (currentInsns.isNotEmpty()) {
        blocks.add(BasicBlock(currentStart, currentEnd, currentInsns, listOf()))
    }
    return blocks
}

private fun DrawScope.drawCfg(blocks: List<BasicBlock>, scale: Float, pan: Offset) {
    val blockWidth = 280f * scale
    val blockHeight = 80f * scale
    val gapX = 60f * scale
    val gapY = 40f * scale
    val startX = 50f + pan.x
    val startY = 50f + pan.y

    // Layout: simple top-down
    val positions = mutableMapOf<Long, Pair<Float, Float>>()
    blocks.forEachIndexed { idx, block ->
        val row = idx / 2
        val col = idx % 2
        positions[block.addr] = Pair(startX + col * (blockWidth + gapX), startY + row * (blockHeight + gapY))
    }

    // Draw edges
    for (block in blocks) {
        val (bx, by) = positions[block.addr] ?: continue
        for (succ in block.successors) {
            val (tx, ty) = positions[succ] ?: continue
            val isTrue = block.insns.any { it.contains("b.ne") || it.contains("b.eq") || it.contains("b.gt") || it.contains("b.lt") }
            drawLine(
                color = when {
                    isTrue && block.successors.indexOf(succ) == 0 -> AccentGreen
                    isTrue -> AccentRed
                    else -> AccentCyan
                },
                start = Offset(bx + blockWidth / 2, by + blockHeight),
                end = Offset(tx + blockWidth / 2, ty),
                strokeWidth = 2f * scale
            )
        }
    }

    // Draw blocks
    for (block in blocks) {
        val (bx, by) = positions[block.addr] ?: continue
        val isEntry = block == blocks.first()
        val isExit = block.insns.any { it.contains("ret") }

        // Block background
        drawRect(
            color = when {
                isEntry -> AccentGreen.copy(alpha = 0.2f)
                isExit -> AccentRed.copy(alpha = 0.2f)
                else -> AccentCyan.copy(alpha = 0.15f)
            },
            topLeft = Offset(bx, by),
            size = Size(blockWidth, blockHeight)
        )
        drawRect(
            color = when {
                isEntry -> AccentGreen
                isExit -> AccentRed
                else -> AccentCyan
            },
            topLeft = Offset(bx, by),
            size = Size(blockWidth, blockHeight),
            style = Stroke(width = 2f * scale)
        )

        // Text
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 10f * scale
                typeface = android.graphics.Typeface.MONOSPACE
            }
            drawText("0x${"%X".format(block.addr)}", bx + 4 * scale, by + 12 * scale, paint)
            paint.color = android.graphics.Color.parseColor("#3FB950")
            paint.textSize = 8f * scale
            block.insns.take(3).forEachIndexed { idx, insn ->
                val short = insn.substringAfter(":  ").take(35)
                drawText(short, bx + 4 * scale, by + (24 + idx * 10) * scale, paint)
            }
        }
    }
}
