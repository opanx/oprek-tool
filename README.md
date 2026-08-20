# Oprek Tool

> 🔬 Professional Binary Analysis & Reverse Engineering Tool for Android

**Copyright © Panxcz & Freebuff**

An all-in-one Android app for binary analysis, disassembly, patching, and reverse engineering. Built with Jetpack Compose + native Capstone disassembler.

## Features (55+ Tools)

### 🔧 Binary Analysis
| Tool | Description | Auto |
|------|-------------|------|
| Hex Viewer | View binary hex dump with ASCII | ✅ |
| Strings | Extract printable strings with search | ✅ |
| ELF Info | Parse ELF headers, entry point | ✅ |
| APK Info | Parse APK structure, DEX detection | ✅ |
| Android Tools | DEX parser, class listing | ✅ |
| File Info | Magic bytes, type detection, hashes | ✅ |
| Hash Calculator | MD5, SHA-1, SHA-256, SHA-512, CRC32 | ✅ |

### 📖 Disassembly & Analysis
| Tool | Description | Auto |
|------|-------------|------|
| **Disassembler (Capstone)** | ARM32/ARM64/x86/x86_64 real disassembly | ✅ |
| Disasm Advanced | Full function disassembly with control flow | ✅ |
| ELF Full Header | All ELF header fields | ✅ |
| Program Headers | Segment viewer (PT_LOAD, etc.) | ✅ |
| Section Headers | .text, .data, .rodata, .symtab | ✅ |
| Symbol Table | .symtab + .dynsym, filter FUNC/OBJECT | ✅ |
| Dynamic Section | DT_NEEDED, DT_INIT, DT_FINI | ✅ |
| Relocations | R_ARM, R_AARCH64, R_X86_64 | ✅ |
| GOT / PLT | Import table viewer | ✅ |
| Function List | All functions with search | ✅ |
| XREF Viewer | Cross-reference finder | ✅ |
| Entropy Analyzer | Per-block entropy visualization | ✅ |
| IDA String Window | Type-tagged strings (URL/CMD/LIB) | ✅ |

### 🛠️ Patching
| Tool | Description | Auto |
|------|-------------|------|
| Patch Editor | Manual hex patch | ✅ |
| Adv. Patch | Auto-detect login/license patterns | ✅ |
| Patch Instruction | NOP/RET/JMP at address | ✅ |
| Patch Branch | Conditional → NOP one-by-one | ✅ |
| Auto Patch Login | Auto-scan "wrong/invalid" + bypass | ✅ |
| Patch String | Search & replace in binary | ✅ |
| Patch Anti-Debug | NOP ptrace/frida/debugger checks | ✅ |

### 🔐 Deobfuscation & Encoding
| Tool | Description | Auto |
|------|-------------|------|
| Deobfuscate | Auto-detect obfuscated strings | ✅ |
| Obfuscate | XOR/AES/ROT13/Base64 encryption | ✅ |
| Shell Deobfuscate | Base64, ROT13, URL decode, hex | ✅ |
| String Encryptor | XOR/AES/ROT13/Base64+XOR | ✅ |
| XOR Brute Force | Key 0x00-0xFF, entropy score | ✅ |
| Base64/Hex | Encode/decode converter | ✅ |

### 🐚 Shell Script Tools
| Tool | Description | Auto |
|------|-------------|------|
| Shell Script Analyzer | Parse commands, URLs, functions | ✅ |
| Shell Script Patcher | Edit URLs, keys, tokens | ✅ |
| Shell Patcher | Binary extract from .sh | ✅ |

### 🎮 Game Analysis
| Tool | Description | Auto |
|------|-------------|------|
| Lua Analyzer | Parse .lua functions, strings | ✅ |
| Pak Archive | .pak/.paks/.unity3d parser | ✅ |

### 🛡️ Hooking & Debugging
| Tool | Description | Auto |
|------|-------------|------|
| Frida Hook | Generate Frida scripts | ✅ |
| Anti-Debug | Detect debug/tracer/frida | ✅ |
| Hook Generator | Auto-detect exported functions | ✅ |

### 📊 Utilities
| Tool | Description | Auto |
|------|-------------|------|
| Diff Tool | Binary comparison | ✅ |
| Manifest Reader | AndroidManifest.xml parser | ✅ |
| Bookmarks | Save addresses with notes | ✅ |
| Export Report | HTML/JSON/TXT export | ✅ |
| Session Manager | Save/load analysis state | ✅ |
| Memory Analyzer | Raw memory dump analysis | ✅ |
| Memory Dump | ELF scan + extract strings | ✅ |
| Logcat | Real-time Android log viewer | ✅ |
| Key Generator | Generate keys/licenses | ✅ |
| Hex Copy | Export as C/Python array | ✅ |
| Terminal | Built-in shell | ✅ |

### 🔍 Packer & Protection
| Tool | Description | Auto |
|------|-------------|------|
| Packer Detection | UPX/Themida/O-LLVM detect | ✅ |
| Unpacker | Auto UPX unpack + manual dump | ✅ |

## Tech Stack

- **Language:** Kotlin + Jetpack Compose
- **Native:** C/C++ with Capstone 5.0.3 disassembler
- **Build:** Gradle KTS + CMake
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **ABI:** arm64-v8a, armeabi-v7a, x86_64

## Build

```bash
git clone https://github.com/opanx/oprek-tool.git
cd oprek-tool
./gradlew assembleDebug
```

Or download the latest APK from [Releases](https://github.com/opanx/oprek-tool/releases).

## Owner

- **@Gk_Gene** — Developer & Owner

## Telegram

- Channel: [t.me/kembungjir](https://t.me/kembungjir)
- Channel: [t.me/lazy_fat_catt](https://t.me/lazy_fat_catt)
- DM: [t.me/Gk_Gene](https://t.me/Gk_Gene)

## License

Copyright © 2024 Panxcz & Freebuff. All rights reserved.
