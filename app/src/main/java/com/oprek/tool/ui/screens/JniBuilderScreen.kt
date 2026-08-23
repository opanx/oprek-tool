package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JniBuilderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var javaCode by remember { mutableStateOf("") }
    var generatedCpp by remember { mutableStateOf("") }
    var selectedMode by remember { mutableIntStateOf(0) } // 0=Generate, 1=Compile, 2=FromFile

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 JNI Builder", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (generatedCpp.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("cpp", generatedCpp))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                        IconButton(onClick = {
                            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OprekTool/jni")
                            dir.mkdirs()
                            File(dir, "jni_bridge.cpp").writeText(generatedCpp)
                            Toast.makeText(context, "Saved to ${dir.absolutePath}", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Save, "Save", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Mode tabs
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = selectedMode == 0, onClick = { selectedMode = 0 },
                        label = { Text("⚙️ Generate", fontSize = 10.sp) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f)))
                    FilterChip(selected = selectedMode == 1, onClick = { selectedMode = 1 },
                        label = { Text("🔨 Compile", fontSize = 10.sp) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                    FilterChip(selected = selectedMode == 2, onClick = { selectedMode = 2 },
                        label = { Text("📋 Templates", fontSize = 10.sp) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                }
            }

            when (selectedMode) {
                0 -> GenerateMode(context, scope, { output = it }, { generatedCpp = it }, { isRunning = it }, isRunning, output, generatedCpp)
                1 -> CompileMode(context, scope, { output = it }, { isRunning = it }, isRunning, output)
                2 -> TemplateMode(context, { generatedCpp = it }, { selectedMode = 0 })
            }
        }
    }
}

@Composable
private fun GenerateMode(
    context: Context, scope: kotlinx.coroutines.CoroutineScope,
    setOutput: (List<String>) -> Unit, setCpp: (String) -> Unit,
    setRunning: (Boolean) -> Unit, isRunning: Boolean,
    output: List<String>, cpp: String
) {
    var javaClass by remember { mutableStateOf("") }
    var methodName by remember { mutableStateOf("") }
    var returnType by remember { mutableStateOf("void") }

    Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = javaClass, onValueChange = { javaClass = it },
            label = { Text("Java class (e.g. com.game.Main)", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = methodName, onValueChange = { methodName = it },
            label = { Text("Method name (e.g. nativeInit)", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = returnType, onValueChange = { returnType = it },
            label = { Text("Return type (void, jstring, jint, etc)", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))

        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            if (javaClass.isBlank() || methodName.isBlank()) {
                setOutput(listOf("[-] Enter class + method name"))
                return@Button
            }
            setRunning(true)
            scope.launch(Dispatchers.IO) {
                val result = generateJniCode(javaClass, methodName, returnType)
                setCpp(result)
                setOutput(result.lines())
                setRunning(false)
            }
        }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
            shape = RoundedCornerShape(8.dp)) {
            if (isRunning) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("⚙️ Generate JNI", fontSize = 12.sp)
        }

        if (cpp.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)) {
                LazyColumn(Modifier.padding(8.dp)) {
                    items(cpp.lines()) { line ->
                        Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("#")) AccentGreen else if (line.contains("JNIEXPORT")) AccentCyan else TextPrimary,
                            lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompileMode(
    context: Context, scope: kotlinx.coroutines.CoroutineScope,
    setOutput: (List<String>) -> Unit, setRunning: (Boolean) -> Unit,
    isRunning: Boolean, output: List<String>
) {
    var cppPath by remember { mutableStateOf("") }
    var targetArch by remember { mutableIntStateOf(0) }

    Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = cppPath, onValueChange = { cppPath = it },
            label = { Text("JNI .cpp/.c path", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp))

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEachIndexed { i, arch ->
                FilterChip(selected = targetArch == i, onClick = { targetArch = i },
                    label = { Text(arch, fontSize = 8.sp) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
            }
        }

        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            if (cppPath.isBlank()) {
                setOutput(listOf("[-] Enter source path"))
                return@Button
            }
            setRunning(true)
            scope.launch(Dispatchers.IO) {
                val result = compileJni(context, cppPath, targetArch)
                setOutput(result)
                setRunning(false)
            }
        }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(8.dp)) {
            Text("🔨 Compile to .so", fontSize = 12.sp)
        }

        if (output.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 350.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)) {
                LazyColumn(Modifier.padding(8.dp)) {
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

        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(8.dp)) {
                Text("ℹ️ Compile Notes", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 11.sp)
                Text("• Requires NDK (download from Tools menu)", fontSize = 9.sp, color = Color.Gray)
                Text("• Output: lib<name>.so in /sdcard/Download/OprekTool/jni/", fontSize = 9.sp, color = Color.Gray)
                Text("• Supports arm64-v8a, armeabi-v7a, x86_64", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun TemplateMode(context: Context, setCpp: (String) -> Unit, setMode: (Int) -> Unit) {
    val templates = listOf(
        "Simple JNI" to "Basic JNI bridge with native method registration",
        "Memory Hook" to "DobbyHook-style inline hooking",
        "IL2CPP Hook" to "Hook IL2CPP runtime functions",
        "Anti-Debug" to "ptrace-based anti-debug bypass",
        "Frida Script" to "Generate Frida agent script"
    )

    Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        templates.forEachIndexed { i, (name, desc) ->
            Card(onClick = {
                    val code = getJniTemplate(i)
                    setCpp(code)
                    setMode(0)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(6.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                        Text(desc, fontSize = 9.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color.Gray)
                }
            }
        }
    }
}

// ========== JNI CODE GENERATOR ==========
private fun generateJniCode(javaClass: String, methodName: String, returnType: String): String {
    val jniClass = javaClass.replace(".", "/")
    val className = javaClass.substringAfterLast(".")
    val jniRetType = when (returnType.lowercase()) {
        "void" -> "void"
        "jstring", "string", "string" -> "jstring"
        "jint", "int" -> "jint"
        "jlong", "long" -> "jlong"
        "jboolean", "boolean", "bool" -> "jboolean"
        "jfloat", "float" -> "jfloat"
        "jdouble", "double" -> "jdouble"
        else -> "jobject"
    }

    return """// Auto-generated JNI bridge by OprekTool
// Package: $javaClass
// Method: $methodName
// Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}

#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "JNI_Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ========== Native Implementation ==========

extern "C" JNIEXPORT $jniRetType JNICALL
Java_${jniClass.replace("/", "_")}_$methodName(
    JNIEnv *env,
    jobject thiz${if (returnType != "void") "" else ""}
) {
    LOGI("JNI: $methodName called from $className");

    // TODO: Add your native code here
${when (returnType.lowercase()) {
    "void", "" -> ""
    "jstring", "string", "string" -> "    return env->NewStringUTF(\"Hello from JNI!\");"
    "jint", "int" -> "    return 42;"
    "jlong", "long" -> "    return 0x12345678LL;"
    "jboolean", "boolean", "bool" -> "    return JNI_TRUE;"
    "jfloat", "float" -> "    return 3.14f;"
    "jdouble", "double" -> "    return 3.14159265358979;"
    else -> "    return nullptr;"
}}

}

// ========== Additional Helper Functions ==========

// Register native methods (optional - for System.loadLibrary)
static const JNINativeMethod methods[] = {
    {"$methodName", "()${when (returnType.lowercase()) {
        "void" -> "V"
        "jstring", "string" -> "Ljava/lang/String;"
        "jint", "int" -> "I"
        "jlong", "long" -> "J"
        "jboolean", "boolean" -> "Z"
        "jfloat", "float" -> "F"
        "jdouble", "double" -> "D"
        else -> "Ljava/lang/Object;"
    }}", (void*)Java_${jniClass.replace("/", "_")}_$methodName}
};

// JNI_OnLoad - auto-register methods
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("$jniClass");
    if (clazz == nullptr) {
        LOGE("JNI_OnLoad: FindClass($jniClass) failed");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clazz, methods, sizeof(methods)/sizeof(methods[0])) < 0) {
        LOGE("JNI_OnLoad: RegisterNatives failed");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: Registered methods successfully");
    return JNI_VERSION_1_6;
}
"""
}

private fun compileJni(context: Context, cppPath: String, targetArch: Int): List<String> {
    val result = mutableListOf<String>()
    result.add("🔨 JNI Compiler v1.0")
    result.add("")

    val archName = when (targetArch) { 0 -> "arm64-v8a"; 1 -> "armeabi-v7a"; 2 -> "x86_64"; else -> "arm64-v8a" }
    result.add("Target: $archName")

    // Check NDK
    val ndkPath = listOf(
        "/usr/local/lib/android/sdk/ndk",
        System.getenv("ANDROID_NDK_HOME") ?: "",
        "/opt/android-ndk"
    ).firstOrNull { it.isNotEmpty() && File(it).exists() }

    if (ndkPath == null) {
        result.add("")
        result.add("⚠️ NDK not found!")
        result.add("")
        result.add("To compile JNI code:")
        result.add("1. Download NDK from Android Studio")
        result.add("2. Set ANDROID_NDK_HOME environment variable")
        result.add("3. Or use the compile from source code mode")
        result.add("")
        result.add("💡 Alternative: Copy the generated .cpp to your NDK project")
        result.add("   and compile with ndk-build or cmake")
        return result
    }

    result.add("NDK: $ndkPath")
    result.add("")

    val srcFile = File(cppPath)
    if (!srcFile.exists()) {
        result.add("❌ Source file not found: $cppPath")
        return result
    }

    result.add("Source: ${srcFile.name} (${srcFile.length()} bytes)")
    result.add("")
    result.add("💡 To compile manually:")
    result.add("   cd <ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin/")
    result.add("   ./$archName-linux-android31-clang++ \\")
    result.add("     -shared -o lib${srcFile.nameWithoutExtension}.so \\")
    result.add("     -I<jni-include> $cppPath")

    return result
}

private fun getJniTemplate(index: Int): String {
    return when (index) {
        0 -> """// Simple JNI Bridge Template
#include <jni.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "JNI", __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_NativeLib_getString(JNIEnv *env, jobject) {
    return env->NewStringUTF("Hello from Native!");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeLib_getInt(JNIEnv *env, jobject) {
    return 42;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeLib_process(JNIEnv *env, jobject, jstring input) {
    const char *str = env->GetStringUTFChars(input, nullptr);
    LOGI("Input: %s", str);
    env->ReleaseStringUTFChars(input, str);
}"""

        1 -> """// Memory Hook Template (DobbyHook style)
#include <jni.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "Hook", __VA_ARGS__)

// Get module base address
void* getModuleBase(const char* moduleName) {
    FILE *fp = fopen("/proc/self/maps", "r");
    char line[256];
    void* base = nullptr;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, moduleName)) {
            unsigned long start;
            sscanf(line, "%lx-", &start);
            base = (void*)start;
            break;
        }
    }
    fclose(fp);
    return base;
}

// Inline hook (simple version)
int hookFunction(void* addr, void* newFunc, void** origFunc) {
    // Save original bytes
    uint8_t* trampoline = (uint8_t*)mmap(nullptr, 64, PROT_READ|PROT_WRITE|PROT_EXEC,
        MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    memcpy(trampoline, addr, 16);

    // Simple trampoline: jump to new function
    // ARM64: LDR X16, [PC, #8]; BR X16; <addr>
    trampoline[0] = 0x58; trampoline[1] = 0x00; trampoline[2] = 0x40; trampoline[3] = 0xF9;
    trampoline[4] = 0x00; trampoline[5] = 0x02; trampoline[6] = 0x1F; trampoline[7] = 0xD6;
    memcpy(trampoline + 8, &newFunc, 8);

    *origFunc = trampoline;
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_HookLib_installHooks(JNIEnv *env, jobject) {
    void* base = getModuleBase("libil2cpp.so");
    LOGI("libil2cpp base: %p", base);
    // TODO: Add your hooks here
}"""

        2 -> """// IL2CPP Hook Template
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "IL2CPP", __VA_ARGS__)

// IL2CPP function signatures
typedef void* (*il2cpp_class_get_method_from_name_t)(void*, const char*, int);
typedef void* (*il2cpp_class_get_field_from_name_t)(void*, const char*);
typedef const char* (*il2cpp_method_get_name_t)(void*);
typedef void* (*il2cpp_class_get_name_t)(void*);

// Hooked functions
il2cpp_class_get_method_from_name_t orig_il2cpp_class_get_method_from_name = nullptr;

void* hooked_il2cpp_class_get_method_from_name(void* klass, const char* name, int paramCount) {
    void* result = orig_il2cpp_class_get_method_from_name(klass, name, paramCount);
    if (result) {
        LOGI("Method found: %s (params: %d)", name, paramCount);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_Il2cppHook_install(JNIEnv *env, jobject) {
    // Get IL2CPP API functions
    void* il2cpp = dlopen("libil2cpp.so", RTLD_NOW);
    if (il2cpp) {
        orig_il2cpp_class_get_method_from_name = (il2cpp_class_get_method_from_name_t)
            dlsym(il2cpp, "il2cpp_class_get_method_from_name");
        LOGI("il2cpp_class_get_method_from_name: %p", orig_il2cpp_class_get_method_from_name);
    }
}"""

        3 -> """// Anti-Debug Bypass Template
#include <jni.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AntiDebug", __VA_ARGS__)

// ptrace self-attach to prevent debugger
void antiPtrace() {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        LOGI("Debugger detected via ptrace!");
        // _exit(1); // Optional: kill process
    }
}

// Check /proc/self/status for TracerPid
void checkTracerPid() {
    FILE *fp = fopen("/proc/self/status", "r");
    char line[128];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "TracerPid:")) {
            int pid = atoi(line + 10);
            if (pid != 0) {
                LOGI("TracerPid: %d (debugger attached!)", pid);
            }
        }
    }
    fclose(fp);
}

// Check for common debuggers
void checkDebugger() {
    // Check /proc/self/maps for frida/xposed
    FILE *fp = fopen("/proc/self/maps", "r");
    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "frida") || strstr(line, "xposed") || strstr(line, "substrate")) {
            LOGI("Debug framework detected: %s", line);
        }
    }
    fclose(fp);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_AntiDebug_install(JNIEnv *env, jobject) {
    antiPtrace();
    checkTracerPid();
    checkDebugger();
    LOGI("Anti-debug installed");
}"""

        4 -> """// Frida Agent Script Template
// Save as agent.js and run with: frida -U -f com.target.app -l agent.js

'use strict';

Java.perform(function() {
    console.log('[*] Frida Agent loaded');

    // Hook a native method
    var targetClass = Java.use('com.target.app.NativeClass');
    targetClass.nativeMethod.implementation = function() {
        console.log('[*] nativeMethod called');
        return this.nativeMethod();
    };

    // Hook Java method
    var mainActivity = Java.use('com.target.app.MainActivity');
    mainActivity.onCreate.overload('android.os.Bundle').implementation = function(bundle) {
        console.log('[*] onCreate called');
        this.onCreate(bundle);
    };

    // Read memory
    var il2cpp = Module.findBaseAddress('libil2cpp.so');
    if (il2cpp) {
        console.log('[*] libil2cpp.so base: ' + il2cpp);
        // Read memory at offset
        var value = Memory.readU32(il2cpp.add(0x123456));
        console.log('[*] Value at offset: ' + value.toString(16));
    }

    // Hook IL2CPP
    var il2cppExports = Module.findExportByName('libil2cpp.so', 'il2cpp_class_get_method_from_name');
    if (il2cppExports) {
        Interceptor.attach(il2cppExports, {
            onEnter: function(args) {
                this.className = args[0];
                this.methodName = Memory.readUtf8String(args[1]);
            },
            onLeave: function(retval) {
                if (this.methodName && this.methodName.indexOf('Update') !== -1) {
                    console.log('[*] il2cpp_class_get_method_from_name: ' + this.methodName);
                }
            }
        });
    }
});"""

        else -> "// Template not found"
    }
}
