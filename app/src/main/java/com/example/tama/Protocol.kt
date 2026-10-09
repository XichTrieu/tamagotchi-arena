package com.example.tama

object Proto {
    const val VERSION = 6
    const val MSG_HELLO = 0x01
    const val MSG_CAPS = 0x02
    const val MSG_SESSION = 0x03
    const val MSG_INPUT = 0x04
    const val MSG_EVENT = 0x05
    const val MSG_END = 0x06
    const val MSG_ACK = 0x07
    const val MSG_NACK = 0x08

    fun crc16(data: ByteArray, len: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until len) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (b in 0 until 8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    fun build(type: Int, seq: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val out = ByteArray(4 + len + 2)
        out[0] = VERSION.toByte()
        out[1] = type.toByte()
        out[2] = len.toByte()
        out[3] = (seq and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 4, len)
        val c = crc16(out, 4 + len)
        out[4 + len] = (c and 0xFF).toByte()
        out[5 + len] = ((c shr 8) and 0xFF).toByte()
        return out
    }

    data class Parsed(val type: Int, val seq: Int, val payload: ByteArray)

    fun parse(buf: ByteArray, len: Int): Parsed? {
        if (len < 6 || (buf[0].toInt() and 0xFF) != VERSION) return null
        val plen = buf[2].toInt() and 0xFF
        if (len < 4 + plen + 2) return null
        val crcRx = (buf[4 + plen].toInt() and 0xFF) or
                    ((buf[5 + plen].toInt() and 0xFF) shl 8)
        if (crcRx != crc16(buf, 4 + plen)) return null
        val payload = buf.copyOfRange(4, 4 + plen)
        return Parsed(buf[1].toInt() and 0xFF, buf[3].toInt() and 0xFF, payload)
    }

    fun packInput(element: Int, tokens: List<ByteArray>, timing: Int): ByteArray {
        val n = tokens.size
        val out = ByteArray(2 + n * 4 + 2 + 2)
        out[0] = element.toByte()
        out[1] = n.toByte()
        var o = 2
        for (t in tokens) { System.arraycopy(t, 0, out, o, 4); o += 4 }
        out[o++] = (timing and 0xFF).toByte()
        out[o++] = ((timing shr 8) and 0xFF).toByte()
        val c = crc16(out, o)
        out[o++] = (c and 0xFF).toByte()
        out[o] = ((c shr 8) and 0xFF).toByte()
        return out
    }

    fun packHello(petId: Int, species: Int, element: Int,
                  hp: Int, hpMax: Int, mana: Int, manaMax: Int,
                  stage: Int, trust: Int, runeMask: Int,
                  spellHash: Int, role: Int): ByteArray {
        val out = ByteArray(20)
        var o = 0
        fun w32(v: Int) {
            out[o++] = (v and 0xFF).toByte()
            out[o++] = ((v shr 8) and 0xFF).toByte()
            out[o++] = ((v shr 16) and 0xFF).toByte()
            out[o++] = ((v shr 24) and 0xFF).toByte()
        }
        fun w16(v: Int) {
            out[o++] = (v and 0xFF).toByte()
            out[o++] = ((v shr 8) and 0xFF).toByte()
        }
        fun w8(v: Int) { out[o++] = (v and 0xFF).toByte() }
        w32(petId); w8(species); w8(element)
        w16(hp); w16(hpMax); w16(mana); w16(manaMax)
        w8(stage); w8(trust); w8(runeMask)
        w32(spellHash); w8(role)
        return out
    }
}