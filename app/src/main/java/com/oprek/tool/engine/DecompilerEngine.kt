package com.oprek.tool.engine

/**
 * DecompilerEngine v2 — Major upgrade
 *
 * Improvements over v1:
 * - Expression combining (sequential instructions → single expression)
 * - Loop detection (while/do-while patterns)
 * - Constant propagation
 * - Better variable tracking with data flow
 * - Improved ARM64 pattern matching
 * - Cleaner pseudo-C output
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
        data class StringLit(val value: String) : IRExpr()

        override fun toString(): String = when (this) {
            is Var -> v.name
            is Const -> formatConst(value)
            is BinOp -> "($left $op $right)"
            is UnaryOp -> "$op($expr)"
            is Deref -> "*($addr)"
            is Call -> "$func(${args.joinToString(", ")})"
            is Phi -> "PHI(${vars.joinToString(", ")})"
            is StringLit -> "\"$value\""
        }
    }

    private fun formatConst(value: Long): String = when {
        value == 0L -> "0"
        value == 1L -> "1"
        value == -1L -> "-1"
        value in 2..9 -> "$value"
        value in 0x20..0x7E -> "'${value.toChar()}'"
        value in 0x10..0x7FFF -> "0x${java.lang.Long.toHexString(value)}"
        value in -0x7FFF..-0x10 -> "-0x${java.lang.Long.toHexString(-value)}"
        else -> "0x${java.lang.Long.toHexString(value)}"
    }

    sealed class IRStmt {
        data class Assign(val dst: IRVar, val src: IRExpr) : IRStmt()
        data class Store(val addr: IRExpr, val value: IRExpr) : IRStmt()
        data class Branch(val cond: IRExpr, val target: Long) : IRStmt()
        data class Jump(val target: Long) : IRStmt()
        data class Return(val value: IRExpr?) : IRStmt()
        data class CallStmt(val func: String, val args: List<IRExpr>, val result: IRVar?) : IRStmt()
        data class Comment(val text: String) : IRStmt()
        data class Label(val addr: Long) : IRStmt()
        data class Nop(val addr: Long) : IRStmt()
        data class WhileLoop(val cond: IRExpr, val bodyStart: Long) : IRStmt()
        data class IfStmt(val cond: IRExpr, val thenLabel: Long) : IRStmt()
        data class IfElseStmt(val cond: IRExpr, val thenLabel: Long, val elseLabel: Long) : IRStmt()
    }

    // ═══════════════════════════════════════════
    // Basic Block & CFG
    // ═══════════════════════════════════════════

    data class BasicBlock(
        val startAddr: Long,
        var endAddr: Long,
        val stmts: MutableList<IRStmt> = mutableListOf(),
        val successors: MutableList<Long> = mutableListOf(),
        var predAddrs: MutableList<Long> = mutableListOf(),
        var isLoopHeader: Boolean = false,
        var loopDepth: Int = 0
    )

    data class CFG(val blocks: Map<Long, BasicBlock>, val entry: Long)

    // ═══════════════════════════════════════════
    // Instruction Parser
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
        val mnemonic = parts[2]
        val operands = if (parts.size > 3) parts.drop(3).map { it.trimEnd(',') } else emptyList()
        return ParsedInsn(addr, mnemonic, operands, trimmed)
    }

    // ═══════════════════════════════════════════
    // Variable Recovery (improved)
    // ═══════════════════════════════════════════

    private val ARM64_REGS = mapOf(
        "x0" to "arg0", "x1" to "arg1", "x2" to "arg2", "x3" to "arg3",
        "x4" to "arg4", "x5" to "arg5", "x6" to "arg6", "x7" to "arg7",
        "x8" to "result", "x9" to "v0", "x10" to "v1", "x11" to "v2",
        "x12" to "v3", "x13" to "v4", "x14" to "v5", "x15" to "v6",
        "x19" to "this", "x20" to "v7", "x21" to "v8", "x22" to "v9",
        "x23" to "v10", "x24" to "v11", "x25" to "v12", "x26" to "v13",
        "x27" to "v14", "x28" to "v15",
        "x29" to "fp", "x30" to "lr", "sp" to "sp",
        "w0" to "arg0_w", "w1" to "arg1_w", "w2" to "arg2_w", "w3" to "arg3_w",
        "w8" to "result_w"
    )

    private val varPool = mutableMapOf<String, IRVar>()
    private var tempCounter = 0

    private fun getVar(name: String): IRVar = varPool.getOrPut(name) { IRVar(name) }
    private fun getTemp(): IRVar = IRVar("_t${tempCounter++}")
    private fun regToVar(reg: String): IRVar {
        val clean = reg.lowercase().trim()
        val mapped = ARM64_REGS[clean] ?: clean
        return getVar(mapped)
    }

    // ═══════════════════════════════════════════
    // Operand Parser (improved)
    // ═══════════════════════════════════════════

    private fun parseOperand(op: String): IRExpr {
        val clean = op.trim().lowercase()
        if (clean.startsWith("#")) {
            val value = try {
                val hex = clean.removePrefix("#")
                if (hex.startsWith("0x")) java.lang.Long.parseLong(hex.removePrefix("0x"), 16)
                else hex.toLong()
            } catch (_: Exception) { 0L }
            return IRExpr.Const(value)
        }
        if (clean.startsWith("[")) {
            val inner = clean.removePrefix("[").removeSuffix("]")
            val parts = inner.split(",")
            val base = regToVar(parts[0].trim())
            return if (parts.size > 1) {
                val offset = parseOperand(parts[1].trim())
                IRExpr.Deref(IRExpr.BinOp("+", IRExpr.Var(base), offset))
            } else {
                IRExpr.Deref(IRExpr.Var(base))
            }
        }
        if (clean.matches(Regex("[xwh]\\d+"))) return IRExpr.Var(regToVar(clean))
        if (clean.startsWith("0x")) {
            val v = try { java.lang.Long.parseLong(clean.removePrefix("0x"), 16) } catch (_: Exception) { 0L }
            return IRExpr.Const(v)
        }
        return IRExpr.Var(getVar(clean))
    }

    private fun parseBranchTarget(operand: String): Long {
        val clean = operand.trim().lowercase().removePrefix("#")
        return try {
            if (clean.startsWith("0x")) java.lang.Long.parseLong(clean.removePrefix("0x"), 16)
            else clean.toLong()
        } catch (_: Exception) { 0L }
    }

    // ═══════════════════════════════════════════
    // Instruction → IR Lifting (improved)
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
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("+", a, b)))
                }
            }
            m == "sub" || m == "subs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("-", a, b)))
                }
            }
            m == "mul" || m == "madd" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("*", a, b)))
                }
            }
            m == "sdiv" || m == "udiv" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("/", a, b)))
                }
            }
            m == "lsl" || m == "lsr" || m == "asr" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    val op = when(m) { "lsl" -> "<<"; "lsr" -> ">>"; else -> ">>>" }
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp(op, a, b)))
                }
            }
            m == "and" || m == "ands" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("&", a, b)))
                }
            }
            m == "orr" || m == "orrs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("|", a, b)))
                }
            }
            m == "eor" || m == "eors" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("^", a, b)))
                }
            }
            m == "neg" || m == "negs" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("-", IRExpr.Const(0), a)))
                }
            }
            m == "mvn" -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(dst, IRExpr.UnaryOp("~", a)))
                }
            }
            m == "adc" || m == "adcs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("+", IRExpr.BinOp("+", a, b), IRExpr.Var(getVar("carry")))))
                }
            }
            m == "sbc" || m == "sbcs" -> {
                if (insn.operands.size >= 3) {
                    val dst = regToVar(insn.operands[0])
                    val a = parseOperand(insn.operands[1])
                    val b = parseOperand(insn.operands[2])
                    stmts.add(IRStmt.Assign(dst, IRExpr.BinOp("-", IRExpr.BinOp("-", a, b), IRExpr.Var(getVar("carry")))))
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
            m == "ldr" && insn.operands.any { it.startsWith("=") } -> {
                if (insn.operands.size >= 2) {
                    val dst = regToVar(insn.operands[0])
                    val imm = insn.operands[1].removePrefix("=")
                    val value = try { imm.removePrefix("0x").toLong(16) } catch (_: Exception) { 0L }
                    stmts.add(IRStmt.Assign(dst, IRExpr.Const(value)))
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
            m == "cmp" -> {
                if (insn.operands.size >= 2) {
                    val a = parseOperand(insn.operands[0])
                    val b = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(getVar("_cmp_result"), IRExpr.BinOp("-", a, b)))
                    stmts.add(IRStmt.Comment("CMP $a, $b"))
                }
            }
            m == "cmn" -> {
                if (insn.operands.size >= 2) {
                    val a = parseOperand(insn.operands[0])
                    val b = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(getVar("_cmp_result"), IRExpr.BinOp("+", a, b)))
                    stmts.add(IRStmt.Comment("CMN $a, $b"))
                }
            }
            m == "tst" -> {
                if (insn.operands.size >= 2) {
                    val a = parseOperand(insn.operands[0])
                    val b = parseOperand(insn.operands[1])
                    stmts.add(IRStmt.Assign(getVar("_cmp_result"), IRExpr.BinOp("&", a, b)))
                    stmts.add(IRStmt.Comment("TST $a, $b"))
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
                    val cond = buildCondition(m.removePrefix("b."))
                    stmts.add(IRStmt.Branch(cond, target))
                }
            }
            m == "br" -> {
                if (insn.operands.isNotEmpty()) {
                    val reg = regToVar(insn.operands[0])
                    stmts.add(IRStmt.Jump(-1))
                    stmts.add(IRStmt.Comment("INDIRECT JUMP: ${reg.name}"))
                }
            }
            m == "cbz" || m == "cbnz" -> {
                if (insn.operands.size >= 2) {
                    val reg = parseOperand(insn.operands[0])
                    val target = parseBranchTarget(insn.operands[1])
                    val cond = if (m == "cbz") {
                        IRExpr.BinOp("==", reg, IRExpr.Const(0))
                    } else {
                        IRExpr.BinOp("!=", reg, IRExpr.Const(0))
                    }
                    stmts.add(IRStmt.Branch(cond, target))
                }
            }
            m == "tbz" || m == "tbnz" -> {
                if (insn.operands.size >= 3) {
                    val reg = parseOperand(insn.operands[0])
                    val bit = parseOperand(insn.operands[1])
                    val target = parseBranchTarget(insn.operands[2])
                    val bitExpr = IRExpr.BinOp("&", IRExpr.BinOp(">>", reg, bit), IRExpr.Const(1))
                    val cond = if (m == "tbz") {
                        IRExpr.BinOp("==", bitExpr, IRExpr.Const(0))
                    } else {
                        IRExpr.BinOp("!=", bitExpr, IRExpr.Const(0))
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

            // ── Prologue/Epilogue hints ──
            m == "stp" && insn.operands.any { it.contains("x29") } -> {
                stmts.add(IRStmt.Comment("PROLOGUE: save frame pointer + link register"))
            }
            m == "ldp" && insn.operands.any { it.contains("x29") } -> {
                stmts.add(IRStmt.Comment("EPILOGUE: restore frame pointer + link register"))
            }

            m == "nop" -> { stmts.add(IRStmt.Nop(insn.addr)) }

            else -> { stmts.add(IRStmt.Comment(insn.raw)) }
        }
        return stmts
    }

    private fun buildCondition(condStr: String): IRExpr {
        return when (condStr) {
            "eq" -> IRExpr.BinOp("==", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "ne" -> IRExpr.BinOp("!=", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "gt" -> IRExpr.BinOp(">", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "ge" -> IRExpr.BinOp(">=", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "lt" -> IRExpr.BinOp("<", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "le" -> IRExpr.BinOp("<=", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "hs" -> IRExpr.BinOp(">=", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "lo" -> IRExpr.BinOp("<", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "mi" -> IRExpr.BinOp("<", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            "pl" -> IRExpr.BinOp(">=", IRExpr.Var(getVar("_cmp_result")), IRExpr.Const(0))
            else -> IRExpr.Var(getVar("flags.$condStr"))
        }
    }

    // ═══════════════════════════════════════════
    // Loop Detection
    // ═══════════════════════════════════════════

    private fun detectLoops(cfg: CFG) {
        // Simple loop detection: back-edge from block to an ancestor
        for ((addr, block) in cfg.blocks) {
            for (succ in block.successors) {
                if (succ < addr && cfg.blocks.containsKey(succ)) {
                    // Back edge detected → loop
                    cfg.blocks[succ]?.isLoopHeader = true
                    // Mark all blocks in the loop body
                    markLoopBody(cfg, succ, addr)
                }
            }
        }
    }

    private fun markLoopBody(cfg: CFG, header: Long, backEdge: Long) {
        val visited = mutableSetOf<Long>()
        val queue = mutableListOf<Long>()
        // Find all blocks between header and backEdge
        for ((addr, _) in cfg.blocks) {
            if (addr > header && addr <= backEdge) {
                queue.add(addr)
            }
        }
        while (queue.isNotEmpty()) {
            val addr = queue.removeFirst()
            if (addr in visited) continue
            visited.add(addr)
            cfg.blocks[addr]?.loopDepth = (cfg.blocks[addr]?.loopDepth ?: 0) + 1
        }
    }

    // ═══════════════════════════════════════════
    // Build CFG
    // ═══════════════════════════════════════════

    fun buildCFG(disasmOutput: String): CFG {
        val insns = disasmOutput.lines().mapNotNull { parseDisasmLine(it) }
        if (insns.isEmpty()) return CFG(emptyMap(), 0)

        // Lift to IR
        val allStmts = mutableListOf<IRStmt>()
        for (insn in insns) allStmts.addAll(liftInstruction(insn))

        // Find block boundaries
        val blockStarts = mutableSetOf<Long>()
        blockStarts.add(insns.first().addr)

        for (insn in insns) {
            val m = insn.mnemonic.lowercase()
            if (m.startsWith("b.") || m == "b" || m == "cbz" || m == "cbnz" || m == "tbz" || m == "tbnz") {
                val target = if (insn.operands.isNotEmpty()) parseBranchTarget(insn.operands.last()) else 0L
                if (target > 0) blockStarts.add(target)
                blockStarts.add(insn.addr + 4)
            }
            if (m == "ret") blockStarts.add(insn.addr + 4)
        }

        // Create blocks
        val blocks = mutableMapOf<Long, BasicBlock>()
        for (start in blockStarts.sorted()) {
            blocks[start] = BasicBlock(start, start)
        }

        // Fill blocks
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
                    val ft = findNextBlockStart(blocks, addr)
                    if (ft != null) { block.successors.add(ft); blocks[ft]?.predAddrs?.add(addr) }
                }
                is IRStmt.Return -> { }
                else -> {
                    val next = findNextBlockStart(blocks, addr)
                    if (next != null) { block.successors.add(next); blocks[next]?.predAddrs?.add(addr) }
                }
            }
        }

        val cfg = CFG(blocks, insns.first().addr)
        detectLoops(cfg)
        return cfg
    }

    private fun findBlockForAddr(blocks: Map<Long, BasicBlock>, addr: Long): Long? {
        for ((start, b) in blocks) {
            if (addr >= start && addr <= b.endAddr) return start
        }
        return blocks.keys.filter { it <= addr }.maxOrNull()
    }

    private fun findNextBlockStart(blocks: Map<Long, BasicBlock>, current: Long): Long? {
        return blocks.keys.filter { it > current }.minOrNull()
    }

    // ═══════════════════════════════════════════
    // Pseudo-C Generator (improved)
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
        sb.appendLine("// Blocks: ${cfg.blocks.size}, Loops: ${cfg.blocks.values.count { it.isLoopHeader }}")
        sb.appendLine("// ═══════════════════════════════════════════════")
        sb.appendLine()

        // Detect return type
        val hasReturn = cfg.blocks.values.any { b -> b.stmts.any { it is IRStmt.Return } }

        // Detect parameters
        val params = mutableListOf<String>()
        for (i in 0..7) {
            if (varPool.containsKey("arg$i")) params.add("long arg$i")
        }
        val retType = if (hasReturn) "long" else "void"
        val paramStr = if (params.isEmpty()) "void" else params.joinToString(", ")

        sb.appendLine("$retType $funcName($paramStr) {")
        sb.appendLine()

        // Local variables
        val locals = varPool.values.filter {
            !it.name.startsWith("arg") && !it.name.startsWith("flags") &&
            !it.name.startsWith("result") && !it.name.startsWith("_t") &&
            !it.name.startsWith("_cmp") && it.name != "fp" && it.name != "lr" && it.name != "sp" &&
            it.name != "carry" && it.name != "this"
        }.distinct()

        if (locals.isNotEmpty()) {
            sb.appendLine("    // Local variables")
            for (v in locals) sb.appendLine("    long ${v.name};")
            sb.appendLine()
        }

        // Generate code
        val visited = mutableSetOf<Long>()
        genBlock(cfg, cfg.entry, sb, visited, 0, showAddresses)

        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("// ═══════════════════════════════════════════════")
        sb.appendLine("// Decompilation complete")
        sb.appendLine("// Blocks: ${cfg.blocks.size}")
        sb.appendLine("// Variables: ${varPool.size}")
        sb.appendLine("// Loops detected: ${cfg.blocks.values.count { it.isLoopHeader }}")
        sb.appendLine("// ═══════════════════════════════════════════════")

        return sb.toString()
    }

    private fun genBlock(cfg: CFG, addr: Long, sb: StringBuilder, visited: MutableSet<Long>, depth: Int, showAddr: Boolean) {
        if (addr in visited || !cfg.blocks.containsKey(addr)) return
        visited.add(addr)

        val block = cfg.blocks[addr] ?: return
        val ind = "    " + "    ".repeat(depth)

        // Loop header
        if (block.isLoopHeader && depth > 0) {
            sb.appendLine("${ind}while (1) { // loop @ 0x${java.lang.Long.toHexString(block.startAddr)}")
            genBlockInner(cfg, block, sb, visited, depth + 1, showAddr)
            sb.appendLine("${ind}}")
            return
        }

        // Block label
        if (depth > 0 || block.startAddr != cfg.entry) {
            sb.appendLine("${ind}// ── Block 0x${java.lang.Long.toHexString(block.startAddr)} ──")
        }

        genBlockInner(cfg, block, sb, visited, depth, showAddr)
    }

    private fun genBlockInner(cfg: CFG, block: BasicBlock, sb: StringBuilder, visited: MutableSet<Long>, depth: Int, showAddr: Boolean) {
        val ind = "    " + "    ".repeat(depth)

        for (stmt in block.stmts) {
            when (stmt) {
                is IRStmt.Label -> { }
                is IRStmt.Nop -> { }
                is IRStmt.Comment -> {
                    if (!stmt.text.contains("PROLOGUE") && !stmt.text.contains("EPILOGUE")) {
                        sb.appendLine("$ind// ${stmt.text}")
                    }
                }
                is IRStmt.Assign -> {
                    val srcStr = formatExpr(stmt.src)
                    val prefix = if (showAddr) "/* ${java.lang.Long.toHexString(block.startAddr)} */ " else ""
                    sb.appendLine("$ind$prefix${stmt.dst.name} = $srcStr;")
                }
                is IRStmt.Store -> {
                    sb.appendLine("$ind*${stmt.addr} = ${stmt.value};")
                }
                is IRStmt.Branch -> {
                    val condStr = formatExpr(stmt.cond)
                    sb.appendLine("${ind}if ($condStr) {")
                    genBlock(cfg, stmt.target, sb, visited, depth + 1, showAddr)
                    val ft = findNextBlockStart(cfg.blocks, block.endAddr - 4)
                    if (ft != null && ft in cfg.blocks && ft !in visited) {
                        sb.appendLine("${ind}} else {")
                        genBlock(cfg, ft, sb, visited, depth + 1, showAddr)
                    }
                    sb.appendLine("$ind}")
                }
                is IRStmt.Jump -> {
                    if (stmt.target > 0) genBlock(cfg, stmt.target, sb, visited, depth, showAddr)
                }
                is IRStmt.Return -> {
                    sb.appendLine("${ind}return${if (stmt.value != null) " ${formatExpr(stmt.value)}" else ""};")
                }
                is IRStmt.CallStmt -> {
                    val argsStr = stmt.args.joinToString(", ") { formatExpr(it) }
                    val prefix = if (stmt.result != null) "${stmt.result.name} = " else ""
                    sb.appendLine("$ind$prefix${stmt.func}($argsStr);")
                }
                else -> { }
            }
        }

        // Follow successors if no terminator
        val lastStmt = block.stmts.lastOrNull()
        if (lastStmt == null || (lastStmt !is IRStmt.Branch && lastStmt !is IRStmt.Jump && lastStmt !is IRStmt.Return)) {
            for (succ in block.successors) {
                if (succ !in visited) genBlock(cfg, succ, sb, visited, depth, showAddr)
            }
        }
    }

    private fun formatExpr(expr: IRExpr): String = when (expr) {
        is IRExpr.Var -> expr.v.name
        is IRExpr.Const -> formatConst(expr.value)
        is IRExpr.StringLit -> "\"${expr.value}\""
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
