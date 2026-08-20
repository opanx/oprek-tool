# Oprek Tool

> 🔬 Professional Binary Analysis & Reverse Engineering Tool for Android

**Copyright © Panxcz & Freebuff**

An all-in-one Android app for binary analysis, disassembly, patching, encryption/decryption, and reverse engineering. Built with Jetpack Compose + native Capstone 5.0.3 disassembler.

---

## 📱 Screenshots

<table>
<tr>
<td align="center"><b>🏠 Home Screen</b><br><img src="img/Screenshot_20260820-020322_OprekTool.png" width="250"></td>
<td align="center"><b>🔬 Hex Viewer</b><br><img src="img/Screenshot_20260820-020353_OprekTool.png" width="250"></td>
</tr>
<tr>
<td align="center"><b>📝 Strings Analysis</b><br><img src="img/Screenshot_20260820-024100_OprekTool.png" width="250"></td>
<td align="center"><b>⚙️ Advanced Tools</b><br><img src="img/Screenshot_20260820-184635_OprekTool.png" width="250"></td>
</tr>
</table>

> 🔥 **65+ tools** in one app — Real Capstone disassembler, auto-detect encryption, ELF full parser, and more!

---

## 📊 Comparison with Other Tools

| Feature | **OprekTool** | Gidra Mobile | radare2 | IDA Pro | JEB | Hex Editor |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| **Offline Mode** | ✅ 100% | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Free / Open Source** | ✅ | ❌ $$$$ | ✅ | ❌ $$$$$ | ❌ $$$$ | Partial |
| **Real Disassembler** | ✅ Capstone | ❌ | ✅ | ✅ | ✅ | ❌ |
| **ARM32/ARM64/x86** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **ELF Full Parser** | ✅ 12 tools | Partial | ✅ | ✅ | ✅ | ❌ |
| **Auto Patch Login** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Encrypt/Decrypt 10+** | ✅ | ❌ | Partial | ❌ | ❌ | ❌ |
| **Auto-Detect Encryption** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Frida Hook Generator** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Anti-Debug Patcher** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Shell Script Crack** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **APK/DEX Analysis** | ✅ | Partial | ❌ | ❌ | ✅ | ❌ |
| **Lua/Pak Analyzer** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **XREF Viewer** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **CFG Visualization** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **XOR Brute Force** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Streaming I/O (200MB+)** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Session Save/Load** | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| **Export HTML/JSON/TXT** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Android Native App** | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **120fps UI** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### 🏆 Kelebihan OprekTool
1. **100% Offline** — Tidak perlu internet, semua fitur jalan lokal
2. **Free & Open Source** — Gratis selamanya, tidak ada license fee
3. **Real Capstone Disassembler** — ARM32/ARM64/x86/x86_64 asli (bukan hex dump)
4. **Auto-Detect Encryption** — Masukkan ciphertext → otomatis deteksi & decrypt (Base64/ROT/XOR/Vigenère/RC4)
5. **10 Encrypt + 10 Decrypt** — XOR, AES, DES, Base64, ROT13, ROT47, Vigenère, RC4, Caesar, Multi-Key
6. **Shell Script Cracking** — Parse, deobfuscate, patch URL/key/token langsung dari .sh
7. **Auto Patch Login** — Scan "wrong/invalid/failed" → auto bypass
8. **Anti-Debug Patcher** — NOP ptrace/frida/debugger checks
9. **Frida Hook Generator** — Auto-detect exported functions dari .so
10. **55+ Tools** — Lebih banyak dari tools lain dalam satu aplikasi
11. **Streaming I/O** — Support file 200MB+ tanpa OOM
12. **120fps UI** — Smooth scrolling, responsive

### ⚠️ Kekurangan OprekTool
1. **Tidak ada Decompiler (Pseudo-C)** — Belum support r2ghidra/Ghidra native
2. **Tidak ada Debugger** — Belum bisa attach ke process running
3. **Tidak ada Emulator** — Tidak bisa emulate ARM code
4. **GUI sederhana** — Belum sepolos IDA/JEB untuk complex analysis
5. **Tidak ada Scripting** — Belum support Python/Lua scripting seperti r2
6. **File Size Limit** — Max 200MB (streaming I/O limit)
7. **No Windows/Linux** — Hanya Android (bisa cross-compile tapi belum)
8. **Belum ada Plugin System** — Tidak bisa extend dengan plugin

---

## Features (65+ Tools)

### 🔧 Binary Analysis
| Tool | Description | Auto |
|------|-------------|------|
| Hex Viewer | View binary hex dump with ASCII | ✅ |
| Strings | Extract printable strings + **auto-detect encrypted** | ✅ |
| ELF Info | Parse ELF headers, entry point | ✅ |
| APK Info | Parse APK structure, DEX detection | ✅ |
| Android Tools | DEX parser, class listing | ✅ |
| File Info | Magic bytes, type detection, hashes | ✅ |
| Hash Calculator | MD5, SHA-1, SHA-256, SHA-512, CRC32 | ✅ |

### 📖 Disassembly & Analysis
| Tool | Description | Auto |
|------|-------------|------|
| **Disassembler (Capstone 5.0.3)** | ARM32/ARM64/x86/x86_64 real disassembly | ✅ |
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

### 🔒 Encryption Tools (NEW)
| Tool | Description | Auto |
|------|-------------|------|
| **Encrypt Tool** | 10 methods: XOR/AES/DES/Base64/ROT13/ROT47/Vigenère/RC4/Caesar | ✅ |
| **Decrypt Tool** | 10 methods + **Auto-Detect** (try all methods) | ✅ |
| XOR Single-Key | XOR with single byte key | ✅ |
| XOR Multi-Key | XOR with multi-byte hex key | ✅ |
| AES-128/256 | AES encrypt/decrypt with PKCS5 | ✅ |
| DES | DES encrypt/decrypt | ✅ |
| Base64 | Standard Base64 encode/decode | ✅ |
| ROT13 | ROT13 letter substitution | ✅ |
| ROT47 | ROT47 ASCII substitution | ✅ |
| Vigenère | Vigenère cipher with keyword | ✅ |
| RC4 | RC4 stream cipher | ✅ |
| Caesar | Caesar cipher shift 1-25 | ✅ |

### 🔓 Auto-Detect Decryption (NEW)
The Decrypt Tool includes **auto-detect** mode that tries all methods:
1. Base64 decode
2. ROT13 / ROT47
3. Caesar shift 1-25 (best score)
4. XOR brute force (0x00-0xFF)
5. Vigenère with common keys (KEY, SECRET, PASSWORD, ADMIN, TEST, HELLO, WORLD, PASS)
6. RC4 with common keys (key, admin, secret, password)
7. Hex decode

The **Strings screen** also has auto-detect button that finds encrypted strings and shows decrypted results inline.

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
| Key Generator | Generate random keys/licenses | ✅ |
| Hex Copy | Export as C/Python array | ✅ |
| Terminal | Built-in shell | ✅ |

### 🔍 Packer & Protection
| Tool | Description | Auto |
|------|-------------|------|
| Packer Detection | UPX/Themida/O-LLVM detect | ✅ |
| Unpacker | Auto UPX unpack + manual dump | ✅ |

---

## Tech Stack

- **Language:** Kotlin + Jetpack Compose
- **Native:** C/C++ with Capstone 5.0.3 disassembler
- **Crypto:** Java Cryptography Extension (JCE) + custom XOR/ROT/RC4
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
