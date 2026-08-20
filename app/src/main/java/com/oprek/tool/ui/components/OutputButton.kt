package com.oprek.tool.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oprek.tool.core.OutputManager
import com.oprek.tool.ui.theme.*

/**
 * Output button that saves content to /sdcard/oprek-tool/output/
 * Usage: OutputButton(content = "my analysis results", filename = "analysis.txt", subfolder = "analysis")
 */
@Composable
fun OutputButton(
    content: () -> String,
    filename: String,
    subfolder: String = "",
    modifier: Modifier = Modifier,
    label: String = "Save Output"
) {
    val context = LocalContext.current
    var savedPath by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Button(
            onClick = {
                try {
                    val text = content()
                    if (text.isBlank()) {
                        Toast.makeText(context, "No output to save", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val path = OutputManager.saveText(context, filename, text, subfolder)
                    if (path != null) {
                        savedPath = path
                        Toast.makeText(context, "✓ Saved to:\n$path", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "✗ Save failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        savedPath?.let { path ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Saved: $path",
                fontSize = 9.sp,
                color = AccentCyan,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Compact output button for inline use
 */
@Composable
fun MiniOutputButton(
    content: () -> String,
    filename: String,
    subfolder: String = ""
) {
    val context = LocalContext.current

    IconButton(
        onClick = {
            try {
                val text = content()
                val path = OutputManager.saveText(context, filename, text, subfolder)
                if (path != null) {
                    Toast.makeText(context, "✓ Saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "✗ Failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Icon(
            Icons.Outlined.SaveAlt,
            contentDescription = "Save output",
            tint = AccentGreen
        )
    }
}

/**
 * Output path display card
 */
@Composable
fun OutputPathCard(path: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📁 ", fontSize = 14.sp)
            Text(path, fontSize = 10.sp, color = AccentCyan, modifier = Modifier.weight(1f))
        }
    }
}
