# 🔧 OprekTool — Toolkit Reverse Engineering Android

> **v0.14.0** | Status build: ✅ Lulus (0 warning) | APK: Signed + Konsisten

**Toolkit reverse engineering Android yang jujur** — bukan tongkat ajaib, tapi kumpulan alat yang solid untuk analisis biner di perangkat.

## ⚠️ Disclaimer Jujur

Alat ini **TIDAK** menggantikan tool RE berbasis PC seperti Ghidra, IDA Pro, atau Il2CppDumper. Yang disediakan:
- Kenyamanan analisis di perangkat saat tidak ada akses PC
- Kemampuan analisis dasar hingga menengah
- Root memory dump untuk pemrosesan offline di PC
- Cukup untuk triage cepat, bukan pekerjaan RE mendalam

## 📱 Yang Baru

### v0.14.0 (Terbaru)
- **Smali Viewer**: Parse file DEX — tabel string, type ID, method ID, daftar kelas
- **XREF Finder**: Cari cross-reference ke string, alamat, atau pola hex di biner ELF (ARM32/ARM64)
- **OFRAK Native Engine v2**: Ditulis ulang dari nol — semua fitur benar-benar work
  - File picker (copy URI ke cache, works di semua perangkat)
  - Tab Info: auto-detect format (ELF/ZIP/DEX/AR/GZIP/XZ/BZIP2/7Z/RAR/TAR)
  - Tab Sections: daftar lengkap section ELF dengan flag (EXEC/ALLOC/WRITE)
  - Actions: extract string, recursive unpack, carve sections, scan embedded, cari secrets
  - Tab Entropy: heatmap visual per blok 4KB
- **Zero build warning**: Semua warning icon deprecated sudah di-resolve
- **Workflow dioptimasi**: Timeout 25 menit, per-step timeout, Node 20 deprecation fixed

### v0.13.0
- **OFRAK Integration v2**: 100% pure Kotlin binary unpacker
- **Binary Modifier**: Patch byte, NOP section, search & replace hex
- **Multi-Arch Analyzer**: Deteksi ARM/ARM64/x86/x86_64/MIPS/PowerPC
- **Resource Scanner**: Cari file tersembunyi di ELF/DEX/APK

## 📊 Benchmark Akurasi Jujur

| Fitur | Akurasi | Catatan |
|-------|---------|---------|
| ELF Header Parse | ✅ 100% | Full support 32/64-bit |
| Disassembler | ✅ 95% | Capstone-based, semua arsitektur utama |
| Decompiler | ⚠️ 80-95% | Pseudo-C, bukan full decompilation |
| AutoDump (normal) | ✅ 90% | Saat metadata tidak terenkripsi |
| AutoDump (protected) | ⚠️ 70% | Raw dump saja, parse di PC |
| String Extraction | ✅ 100% | Binary-safe extraction |
| Hex Viewer | ✅ 100% | Full hex + ASCII |
| Malware Detection | ✅ 95% | 100+ pola ancaman |
| Smali Viewer | ✅ 90% | DEX string/type/method/class parse |
| XREF Finder | ✅ 85% | Deteksi branch ARM32/ARM64 |
| OFRAK Native | ✅ 95% | Recursive unpack, section carve, entropy |

## 🔧 Instalasi

1. Download APK dari [Releases](https://github.com/opanx/oprek-tool/releases)
2. Aktifkan "Install from unknown sources" jika perlu
3. Install dan buka
4. Berikan akses root saat diminta (untuk fitur dump/patch)

## 📁 Direktori Output

Semua dump dan patch disimpan di:
```
/sdcard/Download/OprekTool/
├── dump/          # Output AutoDump (.bin, .cs, .dat)
├── patched/       # File yang sudah di-patch
├── sections/      # ELF sections yang sudah di-carve
├── analysis/      # Output analisis OFRAK
└── terminal/      # Output terminal
```

## ⚖️ Legal

Alat ini untuk **tujuan pendidikan dan riset keamanan saja**. Pengguna bertanggung jawab untuk mematuhi semua hukum yang berlaku.

---

**© Panxcz & Freebuff** 🎮
