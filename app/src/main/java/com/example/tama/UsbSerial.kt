package com.example.tama

import android.content.Context
import android.hardware.usb.*
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class UsbSerial(ctx: Context, device: UsbDevice) {

    private val conn: UsbDeviceConnection
    private val epIn: UsbEndpoint
    private val epOut: UsbEndpoint
    private val input: FileInputStream
    private val output: FileOutputStream
    private var listener: ((ByteArray) -> Unit)? = null
    private var running = false

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
                        inEp = ep; itfToClaim = i
                    } else outEp = ep
                }
            }
        }
        if (inEp == null || outEp == null || itfToClaim < 0)
            throw IllegalStateException("no bulk endpoints")
        epIn = inEp; epOut = outEp
        conn.claimInterface(device.getInterface(itfToClaim), true)
        input = FileInputStream(conn.fileDescriptor)
        output = FileOutputStream(conn.fileDescriptor)
    }

    fun start(onData: (ByteArray) -> Unit) {
        listener = onData
        running = true
        thread(isDaemon = true) {
            val buf = ByteArray(256)
            val acc = ArrayList<Byte>()
            while (running) {
                val r = input.read(buf)
                if (r > 0) {
                    for (i in 0 until r) acc.add(buf[i])
                    while (acc.size >= 6) {
                        val plen = acc[2].toInt() and 0xFF
                        val total = 4 + plen + 2
                        if (acc.size < total) break
                        val pkt = ByteArray(total) { acc[it] }
                        val parsed = Proto.parse(pkt, total)
                        if (parsed != null) listener?.invoke(parsed.payload)
                        repeat(total) { acc.removeAt(0) }
                    }
                }
            }
        }
    }

    fun write(data: ByteArray) {
        output.write(data)
        output.flush()
    }

    fun stop() {
        running = false
        conn.close()
    }
}