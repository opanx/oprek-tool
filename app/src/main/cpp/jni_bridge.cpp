#include <jni.h>
#include <string>
#include <cstring>
#include <cstdio>
#include <cstdint>
// inttypes.h not needed - using direct format specifiers
#include <capstone/capstone.h>

// C functions
extern "C" {
#include "elf_parser.c"
#include "pe_parser.c"
#include "dex_parser.c"
#include "obfuscate.c"
#include "patch_utils.c"
}

// Capstone instances (one per arch)
static csh cs_arm64_handle = 0;
static csh cs_arm_handle = 0;
static csh cs_x86_handle = 0;

static bool initCapstone(int arch) {
    switch (arch) {
        case CS_ARCH_ARM64: {
            if (cs_arm64_handle) return true;
            cs_err err = cs_open(CS_ARCH_ARM64, CS_MODE_ARM, &cs_arm64_handle);
            if (err) return false;
            cs_option(cs_arm64_handle, CS_OPT_DETAIL, CS_OPT_ON);
            return true;
        }
        case CS_ARCH_ARM: {
            if (cs_arm_handle) return true;
            cs_err err = cs_open(CS_ARCH_ARM, CS_MODE_ARM, &cs_arm_handle);
            if (err) return false;
            cs_option(cs_arm_handle, CS_OPT_DETAIL, CS_OPT_ON);
            return true;
        }
        case CS_ARCH_X86: {
            if (cs_x86_handle) return true;
            cs_err err = cs_open(CS_ARCH_X86, CS_MODE_32, &cs_x86_handle);
            if (err) return false;
            cs_option(cs_x86_handle, CS_OPT_DETAIL, CS_OPT_ON);
            return true;
        }
        default: return false;
    }
}

static csh getHandle(int arch) {
    switch (arch) {
        case CS_ARCH_ARM64: return cs_arm64_handle;
        case CS_ARCH_ARM: return cs_arm_handle;
        case CS_ARCH_X86: return cs_x86_handle;
        default: return 0;
    }
}

// ======== ELF ========
extern "C" JNIEXPORT jboolean JNICALL
Java_com_oprek_tool_core_NativeLib_elfValidate(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = elf_validate((const unsigned char *)bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oprek_tool_core_NativeLib_elfGetInfo(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    ElfInfo info;
    int ret = elf_parse_info((const unsigned char *)bytes, len, &info);
    if (ret < 0) {
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return env->NewStringUTF("Invalid ELF");
    }

    // Read machine type before releasing
    uint16_t machine = (info.is_le) ?
        ((unsigned char)*(bytes + 18) | ((unsigned char)*(bytes + 19) << 8)) :
        (((unsigned char)*(bytes + 18) << 8) | (unsigned char)*(bytes + 19));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    const char* arch_str = "unknown";
    if (info.is_64) {
        switch (machine) {
            case 0xB7: arch_str = "AArch64"; break;
            case 0x3E: arch_str = "x86_64"; break;
            default: arch_str = "ELF64"; break;
        }
    } else {
        switch (machine) {
            case 0x28: arch_str = "ARM"; break;
            case 0x03: arch_str = "x86"; break;
            case 0x08: arch_str = "MIPS"; break;
            default: arch_str = "ELF32"; break;
        }
    }

    char buf[1024];
    snprintf(buf, sizeof(buf),
        "Arch: %s %s\n"
        "Machine: %s (0x%04X)\n"
        "Entry: 0x%016" PRIX64 "\n"
        "Program Headers: %u @ 0x%llX\n"
        "Section Headers: %u @ 0x%llX\n"
        "Section StrTab idx: %u\n"
        "File size: %llu bytes",
        info.is_64 ? "ELF64" : "ELF32",
        info.is_le ? "Little Endian" : "Big Endian",
        arch_str, (unsigned)machine,
        (unsigned long long)info.entry, (unsigned)info.phnum, (unsigned long long)info.phoff,
        (unsigned)info.shnum, (unsigned long long)info.shoff, (unsigned)info.shstrndx, (unsigned long long)info.file_size);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oprek_tool_core_NativeLib_elfGetSections(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);

    ElfSectionInfo sections[256];
    int count = elf_parse_sections((const unsigned char *)bytes, len, sections, 256);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(count > 0 ? count : 0, strClass, nullptr);

    for (int i = 0; i < count; i++) {
        char buf[512];
        snprintf(buf, sizeof(buf), "%s|%s|0x%lX|%lu|0x%lX|%u",
            sections[i].name,
            elf_section_type_str(sections[i].type),
            (unsigned long)sections[i].offset,
            (unsigned long)sections[i].size,
            (unsigned long)sections[i].addr,
            (unsigned)sections[i].flags);
        env->SetObjectArrayElement(result, i, env->NewStringUTF(buf));
    }

    return result;
}

// ======== DISASSEMBLER (CAPSTONE) ========
// arch: 0=ARM, 1=ARM64, 2=X86
// mode: 0=ARM, 1=THUMB, 2=ARM64, 3=X86_64, 4=X86_32

extern "C" JNIEXPORT jstring JNICALL
Java_com_oprek_tool_core_NativeLib_disassemble(JNIEnv *env, jclass,
        jbyteArray code, jlong offset, jint arch, jint mode, jint count) {

    jsize len = env->GetArrayLength(code);
    jbyte *bytes = env->GetByteArrayElements(code, nullptr);

    int cs_arch, cs_mode;
    switch (arch) {
        case 0: cs_arch = CS_ARCH_ARM; cs_mode = (mode == 1) ? CS_MODE_THUMB : CS_MODE_ARM; break;
        case 1: cs_arch = CS_ARCH_ARM64; cs_mode = CS_MODE_ARM; break;
        case 2: cs_arch = CS_ARCH_X86; cs_mode = (mode == 3) ? CS_MODE_64 : CS_MODE_32; break;
        default: cs_arch = CS_ARCH_ARM64; cs_mode = CS_MODE_ARM; break;
    }

    if (!initCapstone(cs_arch)) {
        env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
        return env->NewStringUTF("Error: Failed to initialize Capstone disassembler");
    }

    csh handle = getHandle(cs_arch);
    if (!handle) {
        env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
        return env->NewStringUTF("Error: No Capstone handle");
    }

    cs_insn *insn = cs_malloc(handle);
    std::string result;
    char line[512];
    const uint8_t *code_ptr = (const uint8_t *)bytes;
    size_t code_len = (size_t)len;
    uint64_t addr = (uint64_t)offset;

    // Auto-detect ARM/THUMB for ARM mode
    if (cs_arch == CS_ARCH_ARM && mode == 0) {
        // Check if code starts with Thumb-2 instructions
        if (code_len >= 4) {
            uint32_t first_insn = *(uint32_t*)code_ptr;
            if ((first_insn & 0xFFFF) < 0xE800) {
                cs_option(handle, CS_OPT_MODE, CS_MODE_THUMB);
            }
        }
    }

    int printed = 0;
    while (printed < count && addr < (uint64_t)(offset + code_len)) {
        size_t remaining = code_len - (size_t)(addr - (uint64_t)offset);
        if (remaining < 4) break;

        if (cs_disasm_iter(handle, &code_ptr, &remaining, &addr, insn)) {
            // Format: address  hex_bytes  mnemonic  operands
            std::string hex_bytes;
            for (int j = 0; j < insn->size; j++) {
                char hb[4];
                snprintf(hb, sizeof(hb), "%02X ", insn->bytes[j]);
                hex_bytes += hb;
            }
            // Pad hex bytes to consistent width
            while (hex_bytes.length() < 24) hex_bytes += " ";

            snprintf(line, sizeof(line), "0x%016" PRIX64 ":  %s  %s %s\n",
                insn->address, hex_bytes.c_str(), insn->mnemonic, insn->op_str);
            result += line;
            printed++;
        } else {
            // Unknown instruction - show raw bytes
            snprintf(line, sizeof(line), "0x%016" PRIX64 ":  %02X %02X %02X %02X    .byte 0x%02X,0x%02X,0x%02X,0x%02X\n",
                addr,
                code_ptr[0], code_ptr[1], code_ptr[2], code_ptr[3],
                code_ptr[0], code_ptr[1], code_ptr[2], code_ptr[3]);
            result += line;
            code_ptr += 4;
            remaining -= 4;
            addr += 4;
            printed++;
        }
    }

    cs_free(insn, 1);
    env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

// ======== DISASSEMBLE FUNCTION (from symbol) ========
extern "C" JNIEXPORT jstring JNICALL
Java_com_oprek_tool_core_NativeLib_disassembleFunction(JNIEnv *env, jclass,
        jbyteArray code, jlong funcAddr, jlong funcSize, jint arch, jint mode) {

    jsize len = env->GetArrayLength(code);
    jbyte *bytes = env->GetByteArrayElements(code, nullptr);

    int cs_arch, cs_mode;
    switch (arch) {
        case 0: cs_arch = CS_ARCH_ARM; cs_mode = (mode == 1) ? CS_MODE_THUMB : CS_MODE_ARM; break;
        case 1: cs_arch = CS_ARCH_ARM64; cs_mode = CS_MODE_ARM; break;
        case 2: cs_arch = CS_ARCH_X86; cs_mode = (mode == 3) ? CS_MODE_64 : CS_MODE_32; break;
        default: cs_arch = CS_ARCH_ARM64; cs_mode = CS_MODE_ARM; break;
    }

    if (!initCapstone(cs_arch)) {
        env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
        return env->NewStringUTF("Error: Failed to initialize Capstone");
    }

    csh handle = getHandle(cs_arch);
    if (!handle) {
        env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
        return env->NewStringUTF("Error: No Capstone handle");
    }

    cs_insn *insn = cs_malloc(handle);
    std::string result;
    char line[512];

    const uint8_t *code_ptr = (const uint8_t *)bytes;
    size_t code_len = (size_t)len;
    uint64_t addr = (uint64_t)funcAddr;
    uint64_t end_addr = addr + (uint64_t)funcSize;

    while (addr < end_addr && addr < (uint64_t)code_len) {
        size_t remaining = code_len - (size_t)(addr - (uint64_t)0);
        if (remaining < 4) break;

        if (cs_disasm_iter(handle, &code_ptr, &remaining, &addr, insn)) {
            std::string hex_bytes;
            for (int j = 0; j < insn->size; j++) {
                char hb[4];
                snprintf(hb, sizeof(hb), "%02X ", insn->bytes[j]);
                hex_bytes += hb;
            }
            while (hex_bytes.length() < 24) hex_bytes += " ";

            // Highlight return instructions
            bool is_ret = (strcmp(insn->mnemonic, "ret") == 0) ||
                          (strcmp(insn->mnemonic, "bx") == 0) ||
                          (strcmp(insn->mnemonic, "pop") == 0);

            snprintf(line, sizeof(line), "%s0x%016" PRIX64 ":  %s  %s %s%s\n",
                is_ret ? "*" : " ",
                insn->address, hex_bytes.c_str(), insn->mnemonic, insn->op_str,
                is_ret ? "  ; <- return" : "");
            result += line;
        } else {
            snprintf(line, sizeof(line), "  0x%016" PRIX64 ":  %02X %02X %02X %02X    .byte\n",
                addr, code_ptr[0], code_ptr[1], code_ptr[2], code_ptr[3]);
            result += line;
            code_ptr += 4;
            remaining -= 4;
            addr += 4;
        }
    }

    cs_free(insn, 1);
    env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

// ======== OBFUSCATE ========
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_oprek_tool_core_NativeLib_xorEncrypt(JNIEnv *env, jclass,
        jbyteArray data, jbyte key) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);

    jbyteArray result = env->NewByteArray(len);
    jbyte *out = env->GetByteArrayElements(result, nullptr);

    obf_xor((const unsigned char *)bytes, len,
            (unsigned char *)out, (uint8_t)key);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    env->ReleaseByteArrayElements(result, out, 0);
    return result;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_oprek_tool_core_NativeLib_entropy(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    double ent = obf_entropy((const unsigned char *)bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return ent;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_detectPacker(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = obf_detect_packer((const unsigned char *)bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result;
}

// ======== PATCH ========
extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_patchNop(JNIEnv *env, jclass,
        jbyteArray data, jlong offset) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = patch_nop((unsigned char *)bytes, len, offset);
    env->ReleaseByteArrayElements(data, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_patchRet(JNIEnv *env, jclass,
        jbyteArray data, jlong offset) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = patch_ret((unsigned char *)bytes, len, offset);
    env->ReleaseByteArrayElements(data, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_patchRetZero(JNIEnv *env, jclass,
        jbyteArray data, jlong offset) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = patch_ret_zero((unsigned char *)bytes, len, offset);
    env->ReleaseByteArrayElements(data, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_patchBranchUncond(JNIEnv *env, jclass,
        jbyteArray data, jlong offset, jlong target) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = patch_branch((unsigned char *)bytes, len, offset, target - offset);
    env->ReleaseByteArrayElements(data, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oprek_tool_core_NativeLib_patchCondToUncond(JNIEnv *env, jclass,
        jbyteArray data, jlong offset) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = patch_cond_to_uncond((unsigned char *)bytes, len, offset);
    env->ReleaseByteArrayElements(data, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oprek_tool_core_NativeLib_searchPattern(JNIEnv *env, jclass,
        jbyteArray data, jbyteArray pattern, jlong start) {
    jsize dlen = env->GetArrayLength(data);
    jsize plen = env->GetArrayLength(pattern);
    jbyte *dbytes = env->GetByteArrayElements(data, nullptr);
    jbyte *pbytes = env->GetByteArrayElements(pattern, nullptr);

    int64_t result = search_pattern((const unsigned char *)dbytes, dlen,
        (const unsigned char *)pbytes, plen, (size_t)start);

    env->ReleaseByteArrayElements(data, dbytes, JNI_ABORT);
    env->ReleaseByteArrayElements(pattern, pbytes, JNI_ABORT);
    return (jlong)result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oprek_tool_core_NativeLib_packerName(JNIEnv *env, jclass, jint id) {
    return env->NewStringUTF(obf_packer_name(id));
}

// ======== PE ========
extern "C" JNIEXPORT jboolean JNICALL
Java_com_oprek_tool_core_NativeLib_peValidate(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = pe_validate((const unsigned char *)bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

// ======== DEX ========
extern "C" JNIEXPORT jboolean JNICALL
Java_com_oprek_tool_core_NativeLib_dexValidate(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int result = dex_validate((const unsigned char *)bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oprek_tool_core_NativeLib_dexGetInfo(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    DexHeader header;
    int ret = dex_parse_header((const unsigned char *)bytes, len, &header);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (ret < 0) return env->NewStringUTF("Invalid DEX");

    char buf[1024];
    snprintf(buf, sizeof(buf),
        "DEX Version: %s\n"
        "File Size: %u bytes\n"
        "Header Size: %u bytes\n"
        "Endian Tag: 0x%08X\n"
        "String IDs: %u @ 0x%X\n"
        "Type IDs: %u @ 0x%X\n"
        "Proto IDs: %u @ 0x%X\n"
        "Field IDs: %u @ 0x%X\n"
        "Method IDs: %u @ 0x%X\n"
        "Class Defs: %u @ 0x%X\n"
        "Data: %u @ 0x%X",
        header.version, header.file_size, header.header_size,
        header.endian_tag, header.string_ids_size, header.string_ids_off,
        header.type_ids_size, header.type_ids_off,
        header.proto_ids_size, header.proto_ids_off,
        header.field_ids_size, header.field_ids_off,
        header.method_ids_size, header.method_ids_off,
        header.class_defs_size, header.class_defs_off,
        header.data_size, header.data_off);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oprek_tool_core_NativeLib_dexGetClasses(JNIEnv *env, jclass, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);

    DexClass classes[1024];
    int count = dex_parse_classes((const unsigned char *)bytes, len, classes, 1024);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(count, strClass, nullptr);

    for (int i = 0; i < count; i++) {
        char buf[512];
        snprintf(buf, sizeof(buf), "%s|0x%X", classes[i].name, classes[i].access_flags);
        env->SetObjectArrayElement(result, i, env->NewStringUTF(buf));
    }
    return result;
}

// ======== CLEANUP ========
__attribute__((destructor))
static void cleanup_capstone() {
    if (cs_arm64_handle) { cs_close(&cs_arm64_handle); cs_arm64_handle = 0; }
    if (cs_arm_handle) { cs_close(&cs_arm_handle); cs_arm_handle = 0; }
    if (cs_x86_handle) { cs_close(&cs_x86_handle); cs_x86_handle = 0; }
}
