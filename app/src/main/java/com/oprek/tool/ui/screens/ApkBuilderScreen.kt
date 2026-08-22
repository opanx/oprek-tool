package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkBuilderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var shellScript by remember { mutableStateOf("#!/system/bin/sh\necho \"Hello from OprekTool!\"\n") }
    var apkName by remember { mutableStateOf("my_script") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 APK Builder", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (output.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    OutlinedTextField(value = apkName, onValueChange = { apkName = it },
                        label = { Text("APK/Script name", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))

                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = shellScript, onValueChange = { shellScript = it },
                        label = { Text("Shell script / source code", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace))

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Build as .sh (zip)
                        Button(onClick = {
                            if (apkName.isBlank()) {
                                output = listOf("[-] Enter name")
                                return@Button
                            }
                            isRunning = true
                            scope.launch(Dispatchers.IO) {
                                val result = buildShellScript(context, apkName, shellScript)
                                output = result
                                isRunning = false
                            }
                        }, modifier = Modifier.weight(1f).height(40.dp), enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp)) {
                            Text("📜 Build .sh", fontSize = 11.sp)
                        }

                        // Build as .zip with DEX
                        Button(onClick = {
                            if (apkName.isBlank()) {
                                output = listOf("[-] Enter name")
                                return@Button
                            }
                            isRunning = true
                            scope.launch(Dispatchers.IO) {
                                val result = buildApkShell(context, apkName, shellScript)
                                output = result
                                isRunning = false
                            }
                        }, modifier = Modifier.weight(1f).height(40.dp), enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(8.dp)) {
                            Text("📦 Build .zip", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Output
            Card(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 ${output.size} lines", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    if (output.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📦", fontSize = 36.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Write script → Build", color = TextSecondary, fontSize = 11.sp)
                                Text("Creates shell script package", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    } else {
                        LazyColumn {
                            items(output) { line ->
                                val color = when {
                                    line.startsWith("✅") -> AccentGreen
                                    line.startsWith("❌") -> AccentRed
                                    else -> TextPrimary
                                }
                                Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }

            // Templates
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(6.dp)) {
                    Text("📋 Quick Templates", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Auto Install", "Root Check", "Backup", "Debloat").forEachIndexed { i, name ->
                            AssistChip(onClick = {
                                shellScript = getApkTemplate(i)
                                apkName = name.lowercase().replace(" ", "_")
                            }, label = { Text(name, fontSize = 8.sp) },
                                modifier = Modifier.weight(1f),
                                colors = AssistChipDefaults.assistChipColors(containerColor = DarkCard))
                        }
                    }
                }
            }
        }
    }
}

private fun buildShellScript(context: Context, name: String, script: String): List<String> {
    val result = mutableListOf<String>()
    result.add("📜 Building shell script package...")

    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/scripts")
    dir.mkdirs()

    // Create executable shell script
    val shFile = File(dir, "$name.sh")
    shFile.writeText(script)
    shFile.setExecutable(true)
    result.add("✅ Created: ${shFile.absolutePath}")
    result.add("   Size: ${shFile.length()} bytes")

    // Create a simple Android package (ZIP with shell script)
    val zipFile = File(dir, "$name.zip")
    try {
        val zos = java.util.zip.ZipOutputStream(FileOutputStream(zipFile))
        zos.putNextEntry(java.util.zip.ZipEntry("assets/$name.sh"))
        zos.write(script.toByteArray())
        zos.closeEntry()
        zos.close()
        result.add("✅ Package: ${zipFile.absolutePath}")
        result.add("   Size: ${zipFile.length()} bytes")
    } catch (e: Exception) {
        result.add("❌ ZIP error: ${e.message}")
    }

    result.add("")
    result.add("💡 Usage:")
    result.add("   sh ${shFile.absolutePath}")
    result.add("   # Or push to device:")
    result.add("   adb push ${shFile.absolutePath} /data/local/tmp/")
    result.add("   adb shell sh /data/local/tmp/$name.sh")

    return result
}

private fun buildApkShell(context: Context, name: String, script: String): List<String> {
    val result = mutableListOf<String>()
    result.add("📦 Building script package...")

    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/scripts")
    dir.mkdirs()

    // Create a simple Android package structure
    val pkgDir = File(dir, name)
    pkgDir.mkdirs()
    File(pkgDir, "assets").mkdirs()
    File(pkgDir, "lib").mkdirs()

    // Write shell script to assets
    val scriptFile = File(pkgDir, "assets/$name.sh")
    scriptFile.writeText(script)
    result.add("✅ Script: ${scriptFile.absolutePath}")

    // Create a minimal AndroidManifest.xml
    val manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.oprek.tool.$name">
    <application android:label="$name" android:hasCode="false">
        <meta-data android:name="script" android:value="$name.sh"/>
    </application>
</manifest>"""
    File(pkgDir, "AndroidManifest.xml").writeText(manifest)

    // Create a simple DEX (empty)
    val dexBytes = byteArrayOf(
        0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00, // "dex\n035\0"
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )
    File(pkgDir, "classes.dex").writeBytes(dexBytes)

    // Create ZIP (APK)
    val zipFile = File(dir, "$name.apk")
    try {
        val zos = java.util.zip.ZipOutputStream(FileOutputStream(zipFile))

        // Add manifest
        zos.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
        zos.write(manifest.toByteArray())
        zos.closeEntry()

        // Add script
        zos.putNextEntry(java.util.zip.ZipEntry("assets/$name.sh"))
        zos.write(script.toByteArray())
        zos.closeEntry()

        // Add empty DEX
        zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
        zos.write(dexBytes)
        zos.closeEntry()

        zos.close()
        result.add("✅ APK: ${zipFile.absolutePath}")
        result.add("   Size: ${zipFile.length()} bytes")
    } catch (e: Exception) {
        result.add("❌ Build error: ${e.message}")
    }

    result.add("")
    result.add("💡 Install:")
    result.add("   adb install ${zipFile.absolutePath}")
    result.add("   # Or use termux:")
    result.add("   pm install ${zipFile.absolutePath}")

    // Cleanup
    pkgDir.deleteRecursively()

    return result
}

private fun getApkTemplate(index: Int): String {
    return when (index) {
        0 -> """#!/system/bin/sh
# Auto Install Script
# Usage: sh auto_install.sh <apk_path>

APK="$1"
if [ -z "$APK" ]; then
    echo "Usage: $0 <apk_path>"
    exit 1
fi

echo "Installing $APK..."
pm install -r "$APK"
if [ $? -eq 0 ]; then
    echo "✅ Installed successfully!"
else
    echo "❌ Installation failed"
fi"""

        1 -> """#!/system/bin/sh
# Root Check Script
echo "=== Root Check ==="

if [ "$(id -u)" = "0" ]; then
    echo "✅ Running as ROOT"
else
    echo "❌ Not root (uid=$(id -u))"
fi

# Check su binary
if which su >/dev/null 2>&1; then
    echo "✅ su binary found: $(which su)"
else
    echo "❌ su binary not found"
fi

# Check Magisk
if [ -f "/data/adb/magisk/magisk64" ]; then
    echo "✅ Magisk installed"
else
    echo "⚠️ Magisk not detected"
fi

# Check KSU
if [ -f "/data/adb/ksud" ]; then
    echo "✅ KernelSU installed"
else
    echo "⚠️ KernelSU not detected"
fi"""

        2 -> """#!/system/bin/sh
# Backup Script
# Usage: sh backup.sh <package_name>

PKG="$1"
if [ -z "$PKG" ]; then
    echo "Usage: $0 <package_name>"
    exit 1
fi

BACKUP_DIR="/sdcard/Download/OprekTool/backup/$PKG"
mkdir -p "$BACKUP_DIR"

echo "Backing up $PKG..."

# APK
APK_PATH=$(pm path "$PKG" | head -1 | sed 's/package://')
if [ -n "$APK_PATH" ]; then
    cp "$APK_PATH" "$BACKUP_DIR/base.apk"
    echo "✅ APK: $BACKUP_DIR/base.apk"
fi

# Data
if [ -d "/data/data/$PKG" ]; then
    tar -czf "$BACKUP_DIR/data.tar.gz" -C /data/data "$PKG" 2>/dev/null
    echo "✅ Data: $BACKUP_DIR/data.tar.gz"
fi

echo "Backup complete: $BACKUP_DIR"
ls -la "$BACKUP_DIR" """

        3 -> """#!/system/bin/sh
# Debloat Script
# Remove unnecessary system apps

BLOAT_LIST="
com.google.android.youtube
com.google.android.gm
com.google.android.music
com.google.android.apps.maps
com.google.android.plus
com.google.android.talk
"

echo "=== Debloat Script ==="
echo "WARNING: This will disable system apps!"
echo ""

for pkg in $BLOAT_LIST; do
    echo "Disabling: $pkg"
    pm disable-user --user 0 "$pkg" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "  ✅ Disabled"
    else
        echo "  ❌ Not found or already disabled"
    fi
done

echo ""
echo "Done! Restart device to apply changes."
echo "To re-enable: pm enable <package>" """

        else -> "# Template $index"
    }
}
