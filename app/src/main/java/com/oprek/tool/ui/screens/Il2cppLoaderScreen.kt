package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Il2cppLoaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntOf(0) }
    val tabs = listOf("Generator", "Template", "Config", "Guide")

    // Config state
    var toolTitle by remember { mutableStateOf("IL2CPP Tool by Oprek") }
    var targetPackage by remember { mutableStateOf("com.mobile.legends") }
    var targetLib by remember { mutableStateOf("libil2cpp.so") }
    var telegramLink by remember { mutableStateOf("https://t.me/kembungjir") }
    var channelLink by remember { mutableStateOf("https://t.me/lazy_fat_catt") }
    var dumpPath by remember { mutableStateOf("/sdcard/Download/OprekTool/dump") }
    var selectedArch by remember { mutableIntOf(1) } // 0=arm32, 1=arm64
    var obfuscate by remember { mutableStateOf(true) }
    var useFrida by remember { mutableStateOf(false) }

    // Generated files
    var generatedCode by remember { mutableStateOf("") }
    var generatedManifest by remember { mutableStateOf("") }
    var generatedMk by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IL2CPP Loader", color = AccentCyan) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = AccentCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = AccentCyan
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> GeneratorTab(
                    toolTitle, { toolTitle = it },
                    targetPackage, { targetPackage = it },
                    targetLib, { targetLib = it },
                    telegramLink, { telegramLink = it },
                    channelLink, { channelLink = it },
                    dumpPath, { dumpPath = it },
                    selectedArch, { selectedArch = it },
                    obfuscate, { obfuscate = it },
                    useFrida, { useFrida = it },
                    onGenerate = {
                        scope.launch {
                            val result = generateIl2cppTool(
                                toolTitle, targetPackage, targetLib,
                                telegramLink, channelLink, dumpPath,
                                selectedArch, obfuscate, useFrida
                            )
                            generatedCode = result.first
                            generatedManifest = result.second
                            generatedMk = result.third
                            snackbarHostState.showSnackbar("Generated!")
                        }
                    },
                    onCopy = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("Copied!") }
                    }
                )
                1 -> TemplateTab(
                    generatedCode, generatedManifest, generatedMk,
                    onCopy = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("Copied!") }
                    },
                    onSave = { name, content ->
                        scope.launch {
                            saveToFile(context, name, content)
                            snackbarHostState.showSnackbar("Saved: $name")
                        }
                    }
                )
                2 -> ConfigTab(
                    targetPackage, targetLib,
                    onPickApk = { /* TODO */ }
                )
                3 -> GuideTab()
            }
        }
    }
}

@Composable
private fun GeneratorTab(
    toolTitle: String, onTitleChange: (String) -> Unit,
    targetPackage: String, onPackageChange: (String) -> Unit,
    targetLib: String, onLibChange: (String) -> Unit,
    telegramLink: String, onTelegramChange: (String) -> Unit,
    channelLink: String, onChannelChange: (String) -> Unit,
    dumpPath: String, onDumpPathChange: (String) -> Unit,
    selectedArch: Int, onArchChange: (Int) -> Unit,
    obfuscate: Boolean, onObfuscateChange: (Boolean) -> Unit,
    useFrida: Boolean, onFridaChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Tool Title
        DarkCard {
            Text("Tool Configuration", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = toolTitle, onValueChange = onTitleChange,
                label = { Text("Tool Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = telegramLink, onValueChange = onTelegramChange,
                label = { Text("Telegram Link") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = channelLink, onValueChange = onChannelChange,
                label = { Text("Channel Link") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
        }

        // Target Config
        DarkCard {
            Text("Target Configuration", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = targetPackage, onValueChange = onPackageChange,
                label = { Text("Package Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = targetLib, onValueChange = onLibChange,
                label = { Text("Target Library") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = dumpPath, onValueChange = onDumpPathChange,
                label = { Text("Dump Output Path") },
                modifier = Modifier.fillMaxWidth(),
                colors = DarkTextFieldColors()
            )
        }

        // Architecture
        DarkCard {
            Text("Architecture", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = selectedArch == 0,
                    onClick = { onArchChange(0) },
                    label = { Text("ARM32") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = selectedArch == 1,
                    onClick = { onArchChange(1) },
                    label = { Text("ARM64") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f)
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = obfuscate, onCheckedChange = onObfuscateChange)
                Text("Obfuscate Strings", color = TextGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useFrida, onCheckedChange = onFridaChange)
                Text("Use Frida (instead of Dobby)", color = TextGray)
            }
        }

        // Generate Button
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
        ) {
            Icon(Icons.Default.Build, null, tint = DarkBg)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Code", color = DarkBg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TemplateTab(
    code: String, manifest: String, mk: String,
    onCopy: (String) -> Unit,
    onSave: (String, String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Main.cpp
        TemplateFile(
            name = "Main.cpp",
            content = code,
            onCopy = { onCopy(code) },
            onSave = { onSave("Main.cpp", code) }
        )

        // AndroidManifest.xml
        TemplateFile(
            name = "AndroidManifest.xml",
            content = manifest,
            onCopy = { onCopy(manifest) },
            onSave = { onSave("AndroidManifest.xml", manifest) }
        )

        // Android.mk
        TemplateFile(
            name = "Android.mk",
            content = mk,
            onCopy = { onCopy(mk) },
            onSave = { onSave("Android.mk", mk) }
        )
    }
}

@Composable
private fun TemplateFile(
    name: String,
    content: String,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    DarkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, color = AccentCyan, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = TextGray, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, "Save", tint = TextGray, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (content.isNotEmpty()) {
            Text(
                text = content,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceDark)
                    .padding(8.dp)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            )
        } else {
            Text(
                "Click 'Generate Code' to generate templates",
                color = TextGray.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ConfigTab(
    targetPackage: String,
    targetLib: String,
    onPickApk: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("Quick Target Presets", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val presets = listOf(
                Triple("Mobile Legends", "com.mobile.legends", "libil2cpp.so"),
                Triple("Free Fire", "com.dts.freefireth", "libil2cpp.so"),
                Triple("Free Fire MAX", "com.dts.freefiremax", "libil2cpp.so"),
                Triple("PUBG Mobile", "com.tencent.ig", "libil2cpp.so"),
                Triple("PUBG Mobile KR", "com.tencent.igkr", "libil2cpp.so"),
                Triple("Genshin Impact", "com.miHoYo.GenshinImpact", "libil2cpp.so"),
                Triple("Blood Strike", "com.excean.dualaid", "libil2cpp.so"),
                Triple("COD Mobile", "com.activision.callofduty.shooter", "libil2cpp.so"),
                Triple("Brawl Stars", "com.supercell.brawlstars", "libil2cpp.so"),
                Triple("Standoff 2", "com.axlebolt.standoff2", "libil2cpp.so"),
                Triple("Roblox", "com.roblox.client", "libil2cpp.so"),
                Triple("Subway Surfers", "com.kiloo.subwaysurf", "libil2cpp.so")
            )

            presets.forEach { (name, pkg, lib) ->
                Surface(
                    onClick = { /* Will trigger recomposition */ },
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Gamepad, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = TextWhite, fontSize = 12.sp)
                            Text("$pkg | $lib", color = TextGray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DarkCard {
            Text("File Picker", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Select target APK or .so file for analysis", color = TextGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onPickApk,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = DarkBg)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select File", color = DarkBg)
            }
        }
    }
}

@Composable
private fun GuideTab() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DarkCard {
            Text("IL2CPP Tool Loader Guide", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val steps = listOf(
                "1. Generate Code" to "Configure your tool settings and click 'Generate Code'",
                "2. Clone Template" to "Clone Android-LibTool-New from GitHub",
                "3. Replace Files" to "Copy generated Main.cpp, AndroidManifest.xml to the project",
                "4. Build" to "Build with Android Studio or NDK: ndk-build",
                "5. Sign & Install" to "Sign the APK and install on device",
                "6. Load" to "Open target game - overlay menu will appear"
            )

            steps.forEach { (title, desc) ->
                Text(title, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(desc, color = TextGray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        DarkCard {
            Text("Features", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val features = listOf(
                "IL2CPP Dumper - Full dump.cs generation",
                "Runtime API Dumper - Capture method calls",
                "Method Tracer - Trace function execution",
                "String Viewer - Search runtime strings",
                "Memory Patcher - Patch bytes at offsets",
                "Class Browser - Browse all IL2CPP classes",
                "ImGui Overlay - Floating menu",
                "Config Save/Restore - Persistent settings"
            )

            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(feature, color = TextGray, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        DarkCard {
            Text("Quick Links", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val links = listOf(
                "Android-LibTool-New" to "https://github.com/Android-LibTool-New",
                "Dobby Hooking" to "https://github.com/jmpews/Dobby",
                "KittyMemory" to "https://github.com/MJx0/KittyMemory",
                "ImGui" to "https://github.com/ocornut/imgui"
            )

            links.forEach { (name, url) ->
                Text("$name: $url", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        DarkCard {
            Text("Dump Output Format", color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "// dump.cs format:\n" +
                "// Address: Method\n" +
                "public class ClassName {\n" +
                "    // 0x12345678\n" +
                "    public ReturnType MethodName(ParamType param) { }\n" +
                "    // 0x12345690\n" +
                "    public static int StaticMethod() { }\n" +
                "}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceDark)
                    .padding(8.dp)
            )
        }
    }
}

private fun generateIl2cppTool(
    title: String,
    pkg: String,
    lib: String,
    telegram: String,
    channel: String,
    dumpPath: String,
    arch: Int,
    obfuscate: Boolean,
    useFrida: Boolean
): Triple<String, String, String> {
    val archStr = if (arch == 1) "arm64-v8a" else "armeabi-v7a"
    val obfMacro = if (obfuscate) "#define USE_OBFUSCATE" else "// #define USE_OBFUSCATE"
    val hookEngine = if (useFrida) "USE_FRIDA" else "// USE_FRIDA"

    val mainCpp = """// IL2CPP Tool - Generated by OprekTool
// Target: $pkg
// Library: $lib
// Arch: $archStr

#include <jni.h>
#include <pthread.h>
#include <thread>
#include <unistd.h>
#include "Il2cpp/Il2cpp.h"
#include "Il2cpp/il2cpp-class.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Menu/ImGui.h"
#include "Tool/Keyboard.h"
#include "Tool/Tool.h"
#include "Tool/Util.h"
#include "imgui/imgui.h"
#include "imgui/imgui_internal.h"
#include <sstream>

$obfMacro
$hookEngine

Il2CppImage *g_Image = nullptr;
std::vector<MethodInfo *> g_Methods;
extern std::unordered_map<void *, HookerData> hookerMap;
extern int maxLine;

bool collapsed = false;
bool fullScreen = false;
bool resetWindow = false;
int selectedScale = 3;
int selectedTheme = 0;
bool doChangeScale = false;
bool doChangeTheme = false;

constexpr std::array<const char *, 7> possibleScale = {
    "Smallest", "Smaller", "Small", "Default", "Large", "Larger", "Largest",
};
constexpr std::array<float, 7> scaleFactors = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

const char *title = "$title";

void draw_thread()
{
    static ImVec2 lastSize = ImVec2(0, 0);
    static ImVec2 lastPos = ImVec2(0, 0);

    if (resetWindow) {
        resetWindow = false;
        if (fullScreen) {
            ImGui::SetNextWindowPos(ImVec2(0, 0));
            ImGui::SetNextWindowSize(ImGui::GetIO().DisplaySize);
        } else {
            ImGui::SetNextWindowPos(lastPos);
            ImGui::SetNextWindowSize(lastSize);
        }
    }
    if (fullScreen) {
        ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(0, ImGui::GetFrameHeight()));
    }

    collapsed = !ImGui::Begin(title, nullptr,
        fullScreen ? ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove : 0);
    if (fullScreen) ImGui::PopStyleVar();

    Keyboard::Update();

    if (ImGui::BeginTabBar("mainTabber")) {
        if (ImGui::BeginTabItem("Tools")) {
            if (ImGui::Checkbox("Fullscreen", &fullScreen)) {
                if (fullScreen) {
                    lastSize = ImGui::GetWindowSize();
                    lastPos = ImGui::GetWindowPos();
                }
                resetWindow = true;
            }
            Tool::Draw();
            ImGui::EndTabItem();
        }
        if (!hookerMap.empty() && ImGui::BeginTabItem("Tracer")) {
            ImGui::Text("Traced: %zu", hookerMap.size());
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Strings")) {
            Tool::Strings();
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Dumper")) {
            Tool::Dumper();
            ImGui::EndTabItem();
        }
        if (ImGui::BeginTabItem("Settings")) {
            ImGui::Separator();
            ImGui::Text("Info");
            ImGui::Text("Package: %s", Il2cpp::getPackageName().c_str());
            ImGui::Text("Version: %s", Il2cpp::getGameVersion().c_str());
            ImGui::Text("Unity: %s", Il2cpp::getUnityVersion().c_str());
#ifdef __aarch64__
            ImGui::Text("Arch: arm64-v8a");
#else
            ImGui::Text("Arch: armeabi-v7a");
#endif
            ImGui::Separator();
            if (ImGui::Button("Telegram: $telegram")) {
                auto App = Il2cpp::FindClass("UnityEngine.Application");
                auto OpenURL = App->getMethod("OpenURL", 1);
                if (OpenURL)
                    OpenURL->invoke_static<void>(Il2cpp::NewString("$telegram"));
            }
            if (ImGui::Button("Channel: $channel")) {
                auto App = Il2cpp::FindClass("UnityEngine.Application");
                auto OpenURL = App->getMethod("OpenURL", 1);
                if (OpenURL)
                    OpenURL->invoke_static<void>(Il2cpp::NewString("$channel"));
            }
            ImGui::EndTabItem();
        }
        ImGui::EndTabBar();
    }
    ImGui::End();
    Tool::DrawNotifications();
}

void on_init()
{
    while (!isLibraryLoaded(targetLibName)) sleep(1);
    Il2cpp::Init();
    Il2cpp::EnsureAttached();
    Keyboard::Init();

    g_Image = Il2cpp::GetAssembly("Assembly-CSharp")->getImage();
    auto images = Il2cpp::GetImages();
    Tool::Init(g_Image, images);

    for (auto image : images) {
        for (auto klass : image->getClasses()) {
            for (auto m : klass->getMethods()) {
                if (m->methodPointer) g_Methods.emplace_back(m);
            }
        }
    }
    std::sort(g_Methods.begin(), g_Methods.end(),
        [](const auto &a, const auto &b) { return a->methodPointer < b->methodPointer; });
}

bool useJava = false;
void *hack_thread(void *)
{
    logger::Clear();
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    if (!useJava) initModMenu((void *)draw_thread, (void *)on_init);
    return nullptr;
}

extern int glWidth;
extern int glHeight;
extern "C" {
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onDrawFrame(JNIEnv *, jclass) {
        internalDrawMenu(glWidth, glHeight);
    }
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onSurfaceChanged(JNIEnv *, jclass, jint w, jint h) {
        glWidth = w; glHeight = h;
        setupMenu();
    }
    JNIEXPORT void JNICALL Java_imgui_il2cpp_tool_NativeMethods_onSurfaceCreate(JNIEnv *, jclass) {
        initModMenu((void *)draw_thread, (void *)on_init, useJava);
    }
}

__attribute__((constructor)) void lib_main() {
    pthread_t ptid;
    pthread_create(&ptid, nullptr, hack_thread, nullptr);
}

JavaVM *g_vm = nullptr;
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    JNIEnv *env;
    vm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (env->FindClass("imgui/il2cpp/tool/NativeMethods") != nullptr) useJava = true;
    return JNI_VERSION_1_6;
}
"""

    val manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.oprek.il2cpploader">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:label="$title"
        android:theme="@style/Theme.AppCompat.NoActionBar">
        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

    val androidMk = """LOCAL_PATH := $(call my-dir)
MY_ROOT_PATH := $(LOCAL_PATH)

include $(MY_ROOT_PATH)/asmjit/Android.mk
include $(MY_ROOT_PATH)/Dobby/Android.mk
include $(MY_ROOT_PATH)/Frida/Android.mk

LOCAL_PATH := $(MY_ROOT_PATH)

include $(CLEAR_VARS)

LOCAL_MODULE := Tool

LOCAL_CFLAGS := -w -s -Wno-error=format-security -fvisibility=hidden -fpermissive -fexceptions
LOCAL_CPPFLAGS := -w -s -Wno-error=format-security \\
    -fvisibility=hidden \\
    -std=c++17 \\
    -Wno-error=c++11-narrowing \\
    -fpermissive \\
    -fexceptions \\
    $hookEngine

LOCAL_LDFLAGS += -Wl,--gc-sections,--strip-all
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv3 -ldl -latomic -lz -lm -lc

LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES += $(MY_ROOT_PATH)
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/imgui
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/asmjit
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/Dobby
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/Dobby/include

LOCAL_STATIC_LIBRARIES := asmjit dobby
ifeq ($(USE_FRIDA),1)
LOCAL_STATIC_LIBRARIES += frida_gum
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/Frida/gumpp
LOCAL_C_INCLUDES += $(MY_ROOT_PATH)/Frida/$(TARGET_ARCH_ABI)
LOCAL_SRC_FILES += Frida/gumpp/runtime.cpp Frida/gumpp/backtracer.cpp Frida/gumpp/interceptor.cpp
endif

LOCAL_SRC_FILES := \\
    Main.cpp \\
    Menu/ImGui.cpp \\
    Tool/Keyboard.cpp \\
    Tool/Tool.cpp \\
    Tool/Util.cpp \\
    Tool/Patcher.cpp \\
    Tool/PopUpSelector.cpp \\
    Tool/ClassesTab.cpp \\
    Tool/Unity.cpp \\
    Includes/Utils.cpp \\
    Includes/Logger.cpp \\
    KittyMemory/KittyMemory.cpp \\
    KittyMemory/MemoryPatch.cpp \\
    KittyMemory/MemoryBackup.cpp \\
    KittyMemory/KittyUtils.cpp \\
    Il2cpp/Il2cpp.cpp \\
    Il2cpp/il2cpp-class.cpp \\
    Il2cpp/xdl/xdl.c \\
    Il2cpp/xdl/xdl_iterate.c \\
    Il2cpp/xdl/xdl_linker.c \\
    Il2cpp/xdl/xdl_lzma.c \\
    Il2cpp/xdl/xdl_util.c \\
    imgui/imgui_widgets.cpp \\
    imgui/imgui_draw.cpp \\
    imgui/imgui_demo.cpp \\
    imgui/imgui.cpp \\
    imgui/imgui_tables.cpp \\
    imgui/backends/imgui_impl_opengl3.cpp \\
    imgui/backends/imgui_impl_android.cpp

include $(BUILD_SHARED_LIBRARY)
"""

    return Triple(mainCpp, manifest, androidMk)
}

private suspend fun saveToFile(context: Context, name: String, content: String) = withContext(Dispatchers.IO) {
    val dir = File(context.getExternalFilesDir(null), "il2cpp-tool")
    dir.mkdirs()
    val file = File(dir, name)
    FileOutputStream(file).use { it.write(content.toByteArray()) }
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}
