# ⚡ OprekTool v0.0.4

**Professional-Grade Android Reverse Engineering Toolkit**

> A mobile-first reverse engineering tool inspired by IDA Pro, Ghidra, and Binary Ninja. Analyze, disassemble, patch, and reverse engineer ELF, APK, DEX, PE, and shell scripts — all offline on your Android device.

![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Version](https://img.shields.io/badge/version-0.0.4-blue)
![License](https://img.shields.io/badge/license-MIT-orange)
![SDK](https://img.shields.io/badge/SDK-26--35-brightgreen)

---

## 📱 Download

| Build | Status |
|-------|--------|
| **Debug APK** | [Download from Actions](https://github.com/opanx/oprek-tool/actions) → latest run → **OprekTool-debug** |
| **Release APK** | [Download from Actions](https://github.com/opanx/oprek-tool/actions) → latest run → **OprekTool-release** |

---

## 🔥 Features (55+ Tools)

### 📦 Binary Analysis
| Tool | Description |
|------|-------------|
| **ELF Info** | Parse ELF headers, sections, symbols |
| **ELF Full Header** | All ELF fields + architecture detection |
| **Program Headers** | PT_LOAD/PT_DYNAMIC viewer with permission colors |
| **Section Headers** | .text/.data/.symtab/.dynsym/.got/.plt |
| **Symbol Table** | Full .symtab + .dynsym with search/filter |
| **Dynamic Section** | DT_NEEDED, DT_SONAME, DT_INIT/DT_FINI |
| **Relocations** | R_ARM, R_AARCH64, R_X86_64 |
| **GOT / PLT** | Import table viewer |
| **Function List** | All functions with size/type/binding |
| **APK Info** | List entries, detect DEX/native libs |
| **Android Tools** | DEX header, class dump |
| **Manifest Reader** | APK permissions/entries |

### 🔍 Disassembly & Analysis
| Tool | Description |
|------|-------------|
| **Disassembler** | ARM32/ARM64/x86 instruction decode |
| **Disasm Advanced** | Full disassembly with hex bytes |
| **XREF Viewer** | Cross-reference finder |
| **IDA Strings** | Type-tagged string window (URL/CMD/LIB) |
| **Entropy Analyzer** | Per-block entropy visualization |
| **Packer Detection** | UPX/Themida/OLLMV detection |
| **Unpacker** | UPX detection + entropy analysis |

### 🔧 Patching
| Tool | Description |
|------|-------------|
| **Patch Editor** | Single + bulk binary patch |
| **Adv. Patch** | Auto-detect login/license/anti-debug |
| **Patch Instruction** | NOP/RET/RET X0=0/JMP |
| **Patch Branch** | Conditional → NOP/JMP |
| **Auto Patch Login** | Auto-scan + bypass login checks |
| **Patch String** | Search & replace in binary |
| **Patch Anti-Debug** | NOP ptrace/frida/debugger checks |

### 🔐 Deobfuscation & Encoding
| Tool | Description |
|------|-------------|
| **Deobfuscate** | Auto-scan Base64/Hex/XOR/Unicode |
| **Obfuscate** | XOR/AES/ROT13/Base64+XOR |
| **Shell Deobfuscate** | Decode shell obfuscation |
| **XOR Brute Force** | Key 0x00-0xFF with entropy scoring |
| **String Encryptor** | XOR/AES/ROT13 encode |
| **Base64/Hex** | Encode/decode strings |

### 🛠️ Utilities
| Tool | Description |
|------|-------------|
| **Hash Calculator** | MD5/SHA1/SHA256/SHA512/CRC32 |
| **Key Generator** | Random keys with charset config |
| **Diff Tool** | Compare two binary files |
| **File Info** | MD5/SHA256, 20+ magic bytes |
| **Bookmarks** | Save important offsets |
| **Session Manager** | Save/load analysis state |
| **Export Report** | Save analysis as TXT |
| **Recent Files** | History with SharedPrefs |

### 🐚 Shell Script Tools
| Tool | Description |
|------|-------------|
| **Shell Script** | Parse + analyze scripts |
| **Shell Patcher** | Edit URLs/keys/commands |
| **Shell Deobfuscate** | Decode shell obfuscation |

### 🎮 Hooking & Debugging
| Tool | Description |
|------|-------------|
| **Frida Hook** | Generate hook scripts |
| **Anti-Debug** | Detect debuggers + root |
| **Inline Hook** | LD_PRELOAD + trampoline |

### 📱 Android Tools
| Tool | Description |
|------|-------------|
| **Logcat** | Capture Android logs |
| **Terminal** | Shell executor |
| **Hex Copy** | Export as C/Python/hex |
| **Hex Viewer** | View + edit raw bytes |
| **Strings** | Extract readable text |

### 🎮 Game Analysis
| Tool | Description |
|------|-------------|
| **Lua Analyzer** | Parse .lua scripts |
| **Pak Archive** | Analyze .pak/.paks files |

---

## 🏗️ Tech Stack

- **Language:** Kotlin + C++ (JNI)
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + StateFlow + Coroutines
- **Native:** ELF/PE/DEX parsers, obfuscation engine
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **120fps** support on capable devices

---

## 📂 Output Directory

All tool outputs are saved to:
```
/sdcard/OprekTool/output/
├── elf/        (headers, sections, symbols)
├── strings/    (extracted strings)
├── disasm/     (disassembly output)
├── patches/    (all patch results)
├── analysis/   (entropy, packer, anti-debug)
├── hooks/      (frida scripts, inline hooks)
├── shell/      (shell analysis, deobfuscation)
├── encode/     (base64, obfuscate, encrypt)
├── bookmarks/  (saved bookmarks)
└── info/       (file info, manifest)
```

---

## 🚀 Build Instructions

```bash
# Clone
git clone https://github.com/opanx/oprek-tool.git
cd oprek-tool

# Build Debug
./gradlew assembleDebug

# Build Release
./gradlew assembleRelease

# APK Output
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## 👤 Owner & Community

| Link | Description |
|------|-------------|
| [@Gk_Gene](https://t.me/Gk_Gene) | Owner / Developer |
| [t.me/kembungjir](https://t.me/kembungjir) | Channel |
| [t.me/lazy_fat_catt](https://t.me/lazy_fat_catt) | Channel |

---

## 📋 Known Limitations

- Disassembler uses hex display (Capstone native not bundled yet)
- Decompiler (pseudo-C) not available yet
- CFG visualization not available yet
- Room DB for bookmarks not implemented yet
- Some advanced features require root access

---

## 📄 License

MIT License - Free to use and modify

---

**© Panxcz & Freebuff** — Built with ❤️ for the reverse engineering community
