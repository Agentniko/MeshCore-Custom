package io.github.thatsfguy.meshcore.custom

/**
 * A deliberately tiny, strict parser for a FLAT JSON object whose values are strings,
 * numbers, booleans or null. Imported theme files are untrusted input, so this accepts
 * nothing else: no nesting, no arrays, no duplicate keys, bounded size. It only builds
 * data; nothing in an imported file is ever executed.
 */
object MiniJson {
    const val MAX_CHARS = 16_384
    const val MAX_KEYS = 64

    class ParseError(message: String) : Exception(message)

    /** Returns key → value (String, Double, Boolean or null) or throws [ParseError]. */
    fun parseFlatObject(input: String): Map<String, Any?> {
        if (input.length > MAX_CHARS) throw ParseError("File too large")
        val p = Parser(input)
        p.skipWs()
        p.expect('{')
        val out = LinkedHashMap<String, Any?>()
        p.skipWs()
        if (p.peek() == '}') { p.next(); p.finish(); return out }
        while (true) {
            p.skipWs()
            val key = p.string()
            if (key in out) throw ParseError("Duplicate key \"$key\"")
            if (out.size >= MAX_KEYS) throw ParseError("Too many keys")
            p.skipWs(); p.expect(':'); p.skipWs()
            out[key] = p.value()
            p.skipWs()
            when (p.next()) {
                ',' -> continue
                '}' -> break
                else -> throw ParseError("Expected , or }")
            }
        }
        p.finish()
        return out
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch.code < 0x20 -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else -> append(ch)
        }
        append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun peek(): Char? = if (i < s.length) s[i] else null
        fun next(): Char = if (i < s.length) s[i++] else throw ParseError("Unexpected end")
        fun skipWs() { while (i < s.length && s[i] in " \t\r\n") i++ }
        fun expect(c: Char) { if (next() != c) throw ParseError("Expected '$c'") }
        fun finish() { skipWs(); if (i != s.length) throw ParseError("Trailing data") }

        fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> sb.append(e)
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'u' -> {
                            if (i + 4 > s.length) throw ParseError("Bad escape")
                            val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw ParseError("Bad escape")
                            sb.append(code.toChar()); i += 4
                        }
                        else -> throw ParseError("Bad escape")
                    }
                    c.code < 0x20 -> throw ParseError("Control character in string")
                    else -> sb.append(c)
                }
            }
        }

        fun value(): Any? {
            return when (val c = peek() ?: throw ParseError("Unexpected end")) {
                '"' -> string()
                '{', '[' -> throw ParseError("Nested values are not allowed")
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw ParseError("Unexpected '$c'")
            }
        }

        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseError("Unexpected token")
            i += word.length
            return v
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw ParseError("Bad number")
        }
    }
}
