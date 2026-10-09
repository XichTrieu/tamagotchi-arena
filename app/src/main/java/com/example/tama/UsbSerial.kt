package com.example.tama

import android.content.Context
import android.hardware.usb.*
import kotlin.concurrent.thread

class UsbSerial(ctx: Context, device: UsbDevice) {

    private val conn: UsbDeviceConnection
    private val epIn: UsbEndpoint
    private val epOut: UsbEndpoint
    private var listener: ((ByteArray) -> Unit)? = null
    @Volatile private var running = false

    init {
        val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        conn = mgr.openDevice(device) ?: throw IllegalStateException("open failed")

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        var itfToClaim = -1

        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            for (j in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        inEp = ep
                        itfToClaim = i
                    } else {
                        outEp = ep
                    }
                }
            }
        }

        if (inEp == null || outEp == null || itfToClaim < 0)
            throw IllegalStateException("no bulk endpoints found")

        epIn = inEp
        epOut = outEp
        if (!conn.claimInterface(device.getInterface(itfToClaim), true))
            throw IllegalStateException("claimInterface failed")
    }

    fun start(onData: (ByteArray) -> Unit) {
        listener = onData
        running = true
        thread(isDaemon = true) {
            val readBuf = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
            val acc = ArrayList<Byte>(256)
            while (running) {
                val r = conn.bulkTransfer(epIn, readBuf, readBuf.size, 100)
                if (r > 0) {
                    for (i in 0 until r) acc.add(readBuf[i])
                    // Try to parse every complete packet in the accumulator
                    while (acc.size >= 6) {
                        val plen = acc[2].toInt() and 0xFF
                        val total = 4 + plen + 2
                        if (acc.size < total) break
                        val pkt = ByteArray(total) { acc[it] }
                        val parsed = Proto.parse(pkt, total)
                        if (parsed != null) listener?.invoke(parsed.payload)
                        repeat(total) { acc.removeAt(0) }
                    }
                    // Safety: don't let the accumulator grow forever
                    if (acc.size > 2048) {
                        while (acc.size > 1024) acc.removeAt(0)
                    }
                }
            }
        }
    }

    fun write(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val chunk = if (data.size - offset > epOut.maxPacketSize)
                            epOut.maxPacketSize
                        else
                            data.size - offset
            val sent = conn.bulkTransfer(epOut, data, offset, chunk, 500)
            if (sent <= 0) throw RuntimeException("bulkTransfer write failed: $sent")
            offset += sent
        }
    }

    fun stop() {
        running = false
        conn.close()
    }
}
