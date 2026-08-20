package com.oprek.tool.engine

/**
 * DecompilerEngine - Pseudo-C decompiler for ARM64/ARM32
 *
 * Pipeline:
 * 1. Parse Capstone disassembly into IR statements
 * 2. Build basic blocks and CFG
 * 3. Recover variables (stack frame + registers)
 * 4. Lift instructions to expressions
 * 5. Generate readable pseudo-C
 */
object DecompilerEngine {

    // ═══════════════════════════════════════════
    // IR (Intermediate Representation)
    // ═══════════════════════════════════════════

    data class IRVar(val name: String, val size: Int = 8) {
        override fun toString() = name
    }

    sealed class IRExpr {
        data class Var(val v: IRVar) : IRExpr()
        data class Const(val value: Long) : IRExpr()
        data class BinOp(val op: String, val left: IRExpr, val right: IRExpr) : IRExpr()
        data class UnaryOp(val op: String, val expr: IRExpr) : IRExpr()
        data class Deref(val addr: IRExpr, val size: Int = 8) : IRExpr()
        data class Call(val func: String, val args: List<IRExpr>) : IRExpr()
        data class Phi(val vars: List<IRVar>) : IRExpr()

        override fun toString(): String = when (this) {
            is Var -> v.name
            is Const -> if (value in -0x1000..0x1000) "$value" else "0x${java.lang.Long.toHexString(value)}"
            is BinOp -> "($left $op $right)"
            is UnaryOp -> "$op($expr)"
            is Deref -> "*($addr)"
            is Call -> "$func(${args.joinToString(", ")})"
            is Phi -> "PHI(${vars.joinToString(", ")})"
        }
    }

    sealed class IRStmt {
        data class Assign(val dst: IRVar, val src: IRExpr) : IRStmt()
        data class Store(val addr: IRExpr, val value: IRExpr) : IRStmt()
        data class Branch(val cond: IRExpr?, val target: Long) : IRStmt()
        data class Jump(val target: Long) : IRStmt()
        data class Return(val value: IRExpr?) : IRStmt()
        data class CallStmt(val func: String, val args: List<IRExpr>, val result: IRVar?) : IRStmt()
        data class Comment(val text: String) : IRStmt()
        data class Label(val addr: Long) : IRStmt()
        data class Nop(val addr: Long) : IRStmt()
        data class Push(val reg: IRVar) : IRStmt()
        data class Pop(val reg: IRVar) : IRStmt()
        data class IfElse(val cond: IRExpr, val thenLabel: Long, val elseLabel: Long) : IRStmt()

        override fun toString(): String = when (this) {
            is Assign -> "$dst = $src"
            is Store -> "*$addr = $value"
            is Branch -> if (cond != null) "if ($cond) goto 0x${java.lang.Long.toHexString(target)}" else "goto 0x${java.lang.Long.toHexString(target)}"
            is Jump -> "goto 0x${java.lang.Long.toHexString(target)}"
            is Return -> "return${if (value != null) " $value" else ""}"
            is CallStmt -> "${result?.let { "$it = " } ?: ""}$func(${args.joinToString(", ")})"
            is Comment -> "/* $text */"
            is Label -> "label_0x${java.lang.Long.toHexString(addr)}:"
            is Nop -> ""
            is Push -> "PUSH($reg)"
            is Pop -> "POP($reg)"
            is IfElse -> "if ($cond) goto 0x${java.lang.Long.toHexString(thenLabel)} else goto 0x${java.lang.Long.toHexString(elseLabel)}"
        }
    }

    // ═══════════════════════════════════════════
    // Basic Block & CFG
    // ═══════════════════════════════════════════

    data class BasicBlock(
        val startAddr: Long,
        val endAddr: Long,
        val stmts: MutableList<IRStmt> = mutableListOf(),
        val successors: MutableList<Long> = mutableListOf(),
        var predAddrs: MutableList<Long> = mutableListOf()
    )

    data class CFG(val blocks: Map<Long, BasicBlock>, val entry: Long)

    // ═══════════════════════════════════════════
    // Instruction Parser (from Capstone output)
    // ═══════════════════════════════════════════

    data class ParsedInsn(
        val addr: Long,
        val mnemonic: String,
        val operands: List<String>,
        val raw: String
    )

    private fun parseDisasmLine(line: String): ParsedInsn? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("0x")) return null
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size < 4) return null
        val addr = try { java.lang.Long.parseLong(parts[0].removePrefix("0x"), 16) } catch (_: Exception) { return null }
        // parts[1] is hex bytes, parts[2] is mnemonic, rest is operands
        val mnemonic = parts[2]
        val operands = if (parts.size > 3) parts.drop(3).map { it.trimEnd(',') } else emptyList()
        return ParsedInsn(addr, mnemonic, operands, trimmed)
    }

    // ═══════════════════════════════════════════
    // Variable Recovery
    // ═══════════════════════════════════════════

    private val ARM64_REGS = mapOf(
        "x0" to "arg0", "x1" to "arg1", "x2" to "arg2", "x3" to "arg3",
        "x4" to "arg4", "x5" to "arg5", "x6" to "arg6", "x7" to "arg7",
        "x8" to "result", "x9" to "v0", "x10" to "v1", "x11" to "v2",
        "x12" to "v3", "x13" to "v4", "x14" to "v5", "x15" to "v6",
        "x16" to "ip0", "x17" to "ip1",
        "x19" to "this", "x20" to "v7", "x21" to "v8", "x22" to "v9",
        "x23" to "v10", "x24" to "v11", "x25" to "v12", "x26" to "v13",
        "x27" to "v14", "x28" to "v15",
        "x29" to "fp", "x30" to "lr",
        "w0" to "arg0_w", "w1" to "arg1_w", "w2" to "arg2_w", "w3" to "arg3_w",
        "w8" to "result_w"
    )

    private val varPool = mutableMapOf<String, IRVar>()
    private var tempCounter = 0

    private fun getVar(name: String): IRVar {
        return varPool.getOrPut(name) { IRVar(name) }
    }

    private fun getTemp(): IRVar {
        return IRVar("_t${tempCounter++}")
    }

    private fun regToVar(reg: String): IRVar {
        val clean = reg.lowercase().trim()
        val mapped = ARM64_REGS[clean] ?: clean
        return getVar(mapped)
    }

    // ═══════════════════════════════════════════
    // Operand Parser
    // ═══════════════════════════════════════════

    private fun parseOperand(op: String): IRExpr {
        val clean = op.trim().lowercase()
        // Immediate: #0x1234 or #1234
        if (clean.startsWith("#")) {
            val value = try {
                val hex = clean.removePrefix("#")
                if (hex.startsWith("0x")) java.lang.Long.parseLong(hex.removePrefix("0x"), 16)
                else hex.toLong()
            } catch (_: Exception) { 0L }
            return IRExpr.Const(value)
        }
        // Memory: [x29, #offset]
        if (clean.startsWith("[")) {
            val inner = clean.removePrefix("[").removeSuffix("]")
            val parts = inner.split(",")
            val base = regToVar(parts[0].trim())
            return if (parts.size > 1) {
                val offset = parseOperand(parts[1].trim())
                IRExpr.Deref(IRBinOp("+", IRExpr.Var(base), offset))
            } else {
                IRExpr.Deref(IRExpr.Var(base))
            }
        }
        // Register
        if (clean.matches(Regex("[xwh]\\d+"))) {
            return IRExpr.Var(regToVar(clean))
        }
        // Symbol/function name
        if (clean.startsWith("0x") || clean.all { it.isLetterOrDigit() || it == '_' }) {
            return IRExpr.Var(getVar(clean))
        }
        return IRExpr.Var(getVar(clean))
    }

    private fun IRBinOp(op: String, left: IRExpr, right: IRExpr) = IRExpr.BinOp(op, left, right)

    // ═══════════════════════════════════════════
    // Instruction → IR Lifting
    // ═══════════════════════════════════════════

    private fun liftInstruction(insn: ParsedInsn): List<IRStmt> {
        val stmts = mutableListOf<IRStmt>()
        val m = insn.mnemonic.lowercase()

        when {
            // ── Move ──
            m == "mov" || m == "movz" || m == "movk" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val src = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, src))
                }
            }
            m == "movn" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val src = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, IRExpr.UnaryOp("~", src)))
                }
            }

            // ── Arithmetic ──
            m == "add" || m == "adds" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("+", a, b)))
                }
            }
            m == "sub" || m == "subs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("-", a, b)))
                }
            }
            m == "mul" || m == "madd" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("*", a, b)))
                }
            }
            m == "sdiv" || m == "udiv" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("/", a, b)))
                }
            }
            m == "lsl" || m == "lsr" || m == "asr" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    val op = when(m) { "lsl" -> "<<"; "lsr" -> ">>"; else -> ">>>" }
                    stmts.add(IRStmt.Assign(dst, IRBinOp(op, a, b)))
                }
            }
            m == "and" || m == "ands" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("&", a, b)))
                }
            }
            m == "orr" || m == "orrs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("|", a, b)))
                }
            }
            m == "eor" || m == "eors" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("^", a, b)))
                }
            }
            m == "neg" || m == "negs" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, IRBinOp("-", IRExpr.Const(0), a)))
                }
            }

            // ── Load/Store ──
            m == "ldr" || m == "ldrb" || m == "ldrh" || m == "ldp" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val addr = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, IRExpr.Deref(addr)))
                }
            }
            m == "str" || m == "strb" || m == "strh" || m == "stp" -> {
                if (insn.operands.size >= 2) {
                    val src = parseOperand(insn.operands[0])
                    val addr = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Store(addr, src))
                }
            }

            // ── Compare ──
            m == "cmp" || m == "cmn" -> {
                if (insn.operands.size >= 2) {
                    val a = parseOperand(insn.operands[0])
                    val b = parseOperand(insn.operands[1])
                    val op = if (m == "cmn") "+" else "-"
                    val tmp = getTemp()
                    stmts.add(IRStmt.Assign(tmp, IRBinOp(op, a, b)))
                    stmts.add(IRStmt.Comment("CMP: $a $op $b (sets flags)"))
                }
            }
            m == "tst" -> {
                if (insn.operands.size >= 2) {
                    val a = parseOperand(insn.operands[0])
                    val b = parseOperand(insn.operands[1])
                    val tmp = getTemp()
                    stmts.add(IRStmt.Assign(tmp, IRBinOp("&", a, b)))
                    stmts.add(IRStmt.Comment("TST: $a & $b (sets flags)"))
                }
            }

            // ── Branch ──
            m == "b" -> {
                if (insn.operands.isNotEmpty()) {
                    val target = parseBranchTarget(insn.operands[0])
                    stmts.add(IRStmt.Jump(target))
                }
            }
            m.startsWith("b.") -> {
                if (insn.operands.isNotEmpty()) {
                    val target = parseBranchTarget(insn.operands[0])
                    val cond = m.removePrefix("b.")
                    val condExpr = IRExpr.Var(getVar("flags.$cond"))
                    stmts.add(IRStmt.Branch(condExpr, target))
                }
            }
            m == "br" -> {
                if (insn.operands.isNotEmpty()) {
                    val reg = regToVar(insn.operands[0])
                    stmts.add(IRStmt.Jump(-1)) // Indirect jump
                    stmts.add(IRStmt.Comment("INDIRECT JUMP: ${reg.name}"))
                }
            }
            m == "cbz" || m == "cbnz" -> {
                if (insn.operands.size >= 2) {
                    val reg = parseOperand(insn.operands[0])
                    val target = parseBranchTarget(insn.operands[1])
                    val cond = if (m == "cbz") {
                        IRBinOp("==", reg, IRExpr.Const(0))
                    } else {
                        IRBinOp("!=", reg, IRExpr.Const(0))
                    }
                    stmts.add(IRStmt.Branch(cond, target))
                }
            }
            m == "tbz" || m == "tbnz" -> {
                if (insn.operands.size >= 3) {
                    val reg = parseOperand(insn.operands[0])
                    val bit = parseOperand(insn.operands[1])
                    val target = parseBranchTarget(insn.operands[2])
                    val cond = if (m == "tbz") {
                        IRBinOp("==", IRBinOp("&", reg, IRExpr.BinOp("<<", IRExpr.Const(1), bit)), IRExpr.Const(0))
                    } else {
                        IRBinOp("!=", IRBinOp("&", reg, IRExpr.BinOp("<<", IRExpr.Const(1), bit)), IRExpr.Const(0))
                    }
                    stmts.add(IRStmt.Branch(cond, target))
                }
            }

            // ── Return ──
            m == "ret" -> {
                stmts.add(IRStmt.Return(IRExpr.Var(getVar("arg0"))))
            }

            // ── Call ──
            m == "bl" -> {
                if (insn.operands.isNotEmpty()) {
                    val target = insn.operands[0].trim().lowercase()
                    val args = (0..7).map { IRExpr.Var(getVar("arg$it")) }
                    stmts.add(IRStmt.CallStmt(target, args, getVar("result")))
                }
            }
            m == "blr" -> {
                if (insn.operands.isNotEmpty()) {
                    val reg = regToVar(insn.operands[0])
                    val args = (0..7).map { IRExpr.Var(getVar("arg$it")) }
                    stmts.add(IRStmt.CallStmt("(${reg.name})", args, getVar("result")))
                }
            }

            // ── Push/Pop (prologue/epilogue) ──
            m == "stp" && insn.operands.any { it.contains("x29") } -> {
                stmts.add(IRStmt.Comment("PROLOGUE: save frame pointer + link register"))
            }
            m == "ldp" && insn.operands.any { it.contains("x29") } -> {
                stmts.add(IRStmt.Comment("EPILOGUE: restore frame pointer + link register"))
            }

            // ── NOP ──
            m == "nop" -> {
                stmts.add(IRStmt.Nop(insn.addr))
            }

            // ── Unknown ──
            else -> {
                stmts.add(IRStmt.Comment(insn.raw))
            }
        }
        return stmts
    }

    private fun parseBranchTarget(operand: String): Long {
        val clean = operand.trim().lowercase().removePrefix("#")
        return try {
            if (clean.startsWith("0x")) java.lang.Long.parseLong(clean.removePrefix("0x"), 16)
            else clean.toLong()
        } catch (_: Exception) { 0L }
    }

    // ═══════════════════════════════════════════
    // Build CFG from IR statements
    // ═══════════════════════════════════════════

    fun buildCFG(disasmOutput: String): CFG {
        // Parse all instructions
        val insns = disasmOutput.lines()
            .mapNotNull { parseDisasmLine(it) }

        if (insns.isEmpty()) return CFG(emptyMap(), 0)

        // Lift to IR
        val allStmts = mutableListOf<IRStmt>()
        for (insn in insns) {
            allStmts.addAll(liftInstruction(insn))
        }

        // Find block boundaries (branches/jumps are block ends)
        val blockStarts = mutableSetOf<Long>()
        blockStarts.add(insns.first().addr)

        val branchTargets = mutableSetOf<Long>()
        for (insn in insns) {
            val m = insn.mnemonic.lowercase()
            if (m.startsWith("b.") || m == "b" || m == "cbz" || m == "cbnz" || m == "tbz" || m == "tbnz") {
                val target = if (insn.operands.isNotEmpty()) parseBranchTarget(insn.operands.last()) else 0L
                if (target > 0) {
                    branchTargets.add(target)
                    blockStarts.add(target)
                }
                // Fall-through target
                val nextAddr = insn.addr + 4
                blockStarts.add(nextAddr)
            }
            if (m == "ret") {
                blockStarts.add(insn.addr + 4)
            }
        }

        // Create blocks
        val blocks = mutableMapOf<Long, BasicBlock>()
        val sortedStarts = blockStarts.sorted()

        for (start in sortedStarts) {
            val block = BasicBlock(start, start)
            blocks[start] = block
        }

        // Fill blocks with statements
        for (insn in insns) {
            val blockAddr = findBlockForAddr(blocks, insn.addr) ?: continue
            blocks[blockAddr]!!.stmts.addAll(liftInstruction(insn))
            blocks[blockAddr]!!.endAddr = insn.addr + 4
        }

        // Add successors
        for ((addr, block) in blocks) {
            val lastStmt = block.stmts.lastOrNull()
            when (lastStmt) {
                is IRStmt.Jump -> {
                    if (lastStmt.target > 0) {
                        block.successors.add(lastStmt.target)
                        blocks[lastStmt.target]?.predAddrs?.add(addr)
                    }
                }
                is IRStmt.Branch -> {
                    block.successors.add(lastStmt.target)
                    blocks[lastStmt.target]?.predAddrs?.add(addr)
                    // Fall-through
                    val fallThrough = findNextBlockStart(blocks, addr)
                    if (fallThrough != null) {
                        block.successors.add(fallThrough)
                        blocks[fallThrough]?.predAddrs?.add(addr)
                    }
                }
                is IRStmt.Return -> { /* no successor */ }
                else -> {
                    // Fall-through
                    val next = findNextBlockStart(blocks, addr)
                    if (next != null) {
                        block.successors.add(next)
                        blocks[next]?.predAddrs?.add(addr)
                    }
                }
            }
        }

        return CFG(blocks, insns.first().addr)
    }

    private fun findBlockForAddr(blocks: Map<Long, BasicBlock>, addr: Long): Long? {
        // Find the block whose range contains this address
        for ((start, block) in blocks) {
            if (addr >= start && addr <= block.endAddr) return start
        }
        // Find the nearest block before this address
        return blocks.keys.filter { it <= addr }.maxOrNull()
    }

    private fun findNextBlockStart(blocks: Map<Long, BasicBlock>, current: Long): Long? {
        return blocks.keys.filter { it > current }.minOrNull()
    }

    // ═══════════════════════════════════════════
    // Pseudo-C Generator
    // ═══════════════════════════════════════════

    fun generatePseudoC(disasmOutput: String, funcName: String = "unknown", showAddresses: Boolean = false): String {
        val cfg = buildCFG(disasmOutput)
        if (cfg.blocks.isEmpty()) return "// No disassembly to decompile"

        varPool.clear()
        tempCounter = 0

        val sb = StringBuilder()
        sb.appendLine("// ═══════════════════════════════════════════════")
        sb.appendLine("// Pseudo-C decompilation of $funcName")
        sb.appendLine("// Generated by OprekTool Decompiler v2.0")
        sb.appendLine("// Target: ARM64 (AArch64)")
        sb.appendLine("// ═══════════════════════════════════════════════")
        sb.appendLine()

        // Detect return type from last block
        val lastBlock = cfg.blocks.values.maxByOrNull { it.endAddr }
        val hasReturn = lastBlock?.stmts?.any { it is IRStmt.Return } ?: false

        // Detect parameters (used arg0-arg7)
        val params = mutableListOf<String>()
        for (i in 0..7) {
            val argVar = getVar("arg$i")
            if (varPool.values.any { it.name == argVar.name }) {
                params.add("long arg$i")
            }
        }
        val retType = if (hasReturn) "long" else "void"
        val paramStr = if (params.isEmpty()) "void" else params.joinToString(", ")

        sb.appendLine("$retType $funcName($paramStr) {")
        sb.appendLine()

        // Collect local variables
        val locals = varPool.values.filter {
            !it.name.startsWith("arg") && !it.name.startsWith("flags") &&
            !it.name.startsWith("result") && !it.name.startsWith("_t") &&
            it.name != "fp" && it.name != "lr" && it.name != "sp"
        }.distinct()

        if (locals.isNotEmpty()) {
            sb.appendLine("    // Local variables")
            for (v in locals) {
                sb.appendLine("    long ${v.name};")
            }
            sb.appendLine()
        }

        // Generate code for each block
        val visited = mutableSetOf<Long>()
        val indent = StringBuilder("    ")

        fun genBlock(addr: Long, depth: Int) {
            if (addr in visited || !cfg.blocks.containsKey(addr)) return
            visited.add(addr)

            val block = cfg.blocks[addr] ?: return
            val ind = "    " + "    ".repeat(depth)

            // Label
            if (depth > 0 || block.startAddr != cfg.entry) {
                sb.appendLine("${ind}// Block 0x${java.lang.Long.toHexString(block.startAddr)}:")
            }

            for (stmt in block.stmts) {
                when (stmt) {
                    is IRStmt.Label -> { /* skip */ }
                    is IRStmt.Nop -> { /* skip */ }
                    is IRStmt.Comment -> sb.appendLine("$ind${stmt.text}")
                    is IRStmt.Assign -> {
                        val srcStr = formatExpr(stmt.src)
                        sb.appendLine("$ind${stmt.dst.name} = $srcStr;")
                    }
                    is IRStmt.Store -> {
                        sb.appendLine("$ind*${stmt.addr} = ${stmt.value};")
                    }
                    is IRStmt.Branch -> {
                        // Generate if/else structure
                        val condStr = formatExpr(stmt.cond)
                        sb.appendLine("$indif ($condStr) {")
                        genBlock(stmt.target, depth + 1)
                        val fallThrough = findNextBlockStart(cfg.blocks, block.endAddr - 4)
                        if (fallThrough != null && fallThrough in cfg.blocks && fallThrough !in visited) {
                            sb.appendLine("$ind} else {")
                            genBlock(fallThrough, depth + 1)
                        }
                        sb.appendLine("$ind}")
                    }
                    is IRStmt.Jump -> {
                        if (stmt.target > 0) genBlock(stmt.target, depth)
                    }
                    is IRStmt.Return -> {
                        sb.appendLine("$indreturn${if (stmt.value != null) " ${formatExpr(stmt.value)}" else ""};")
                    }
                    is IRStmt.CallStmt -> {
                        val argsStr = stmt.args.joinToString(", ") { formatExpr(it) }
                        val prefix = if (stmt.result != null) "${stmt.result.name} = " else ""
                        sb.appendLine("$ind$prefix${stmt.func}($argsStr);")
                    }
                    else -> {}
                }
            }

            // If no terminator, follow successors
            val lastStmt = block.stmts.lastOrNull()
            if (lastStmt == null || (lastStmt !is IRStmt.Branch && lastStmt !is IRStmt.Jump && lastStmt !is IRStmt.Return)) {
                for (succ in block.successors) {
                    if (succ !in visited) genBlock(succ, depth)
                }
            }
        }

        genBlock(cfg.entry, 0)
        sb.appendLine("}")
        sb.appendLine()

        // Summary
        sb.appendLine("// ═══════════════════════════════════════════════")
        sb.appendLine("// Decompilation complete")
        sb.appendLine("// Blocks: ${cfg.blocks.size}")
        sb.appendLine("// Variables: ${varPool.size}")
        sb.appendLine("// ═══════════════════════════════════════════════")

        return sb.toString()
    }

    private fun formatExpr(expr: IRExpr): String {
        return when (expr) {
            is IRExpr.Var -> expr.v.name
            is IRExpr.Const -> {
                if (expr.value == 0L) "0"
                else if (expr.value in -0x1000..0x1000) "${expr.value}"
                else "0x${java.lang.Long.toHexString(expr.value)}"
            }
            is IRExpr.BinOp -> {
                val l = formatExpr(expr.left)
                val r = formatExpr(expr.right)
                "$l ${expr.op} $r"
            }
            is IRExpr.UnaryOp -> "${expr.op}(${formatExpr(expr.expr)})"
            is IRExpr.Deref -> "*(${formatExpr(expr.addr)})"
            is IRExpr.Call -> "${expr.func}(${expr.args.joinToString(", ") { formatExpr(it) }})"
            is IRExpr.Phi -> "PHI(${expr.vars.joinToString(", ") { it.name }})"
        }
    }
}
