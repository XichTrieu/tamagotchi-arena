package com.example.tama

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var serial: UsbSerial? = null
    private lateinit var statusText: TextView
    private lateinit var logView: TextView
    private var seq = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        statusText = TextView(this).apply { text = "Tamagotchi Arena\n(disconnected)" }
        val connect = Button(this).apply { text = "Connect USB" }
        val sendHello = Button(this).apply { text = "Send HELLO" }
        val sendFire = Button(this).apply { text = "Send FIRE input" }
        val sendWater = Button(this).apply { text = "Send WATER input" }
        logView = TextView(this).apply { text = "" }

        root.addView(statusText)
        root.addView(connect)
        root.addView(sendHello)
        root.addView(sendFire)
        root.addView(sendWater)
        root.addView(logView)
        setContentView(root)

        connect.setOnClickListener { connectUsb() }
        sendHello.setOnClickListener { sendHelloPacket() }
        sendFire.setOnClickListener { sendInput(1) }
        sendWater.setOnClickListener { sendInput(2) }
    }

    private fun connectUsb() {
        val mgr = getSystemService(Context.USB_SERVICE) as UsbManager
        val dev = mgr.deviceList.values.firstOrNull { it.vendorId == 0x303A }
            ?: mgr.deviceList.values.firstOrNull()
        if (dev == null) { toast("No USB device"); return }
        if (!mgr.hasPermission(dev)) {
            val pi = PendingIntent.getBroadcast(this, 0,
                Intent("com.example.tama.USB_PERM"),
                PendingIntent.FLAG_IMMUTABLE)
            mgr.requestPermission(dev, pi)
            toast("Permission requested — replug if needed")
            return
        }
        try {
            serial = UsbSerial(this, dev)
            serial?.start { payload -> runOnUiThread { onPacket(payload) } }
            statusText.text = "Connected: ${dev.deviceName}"
            toast("Connected")
        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun sendHelloPacket() {
        val s = serial ?: return toast("Not connected")
        val payload = Proto.packHello(
            petId = 0x12345678, species = 0, element = 1,
            hp = 100, hpMax = 100, mana = 50, manaMax = 50,
            stage = 3, trust = 50, runeMask = 0,
            spellHash = 0xCAFEBABE.toInt(), role = 0
        )
        val pkt = Proto.build(Proto.MSG_HELLO, seq++, payload)
        s.write(pkt)
        append("→ HELLO")
    }

    private fun sendInput(element: Int) {
        val s = serial ?: return toast("Not connected")
        val tokens = listOf(
            byteArrayOf(0, 0, 0, 180.toByte()),
            byteArrayOf(0, 2, 0, 200.toByte()),
            byteArrayOf(2, 0, 0, 220.toByte())
        )
        val payload = Proto.packInput(element, tokens, 32767)
        val pkt = Proto.build(Proto.MSG_INPUT, seq++, payload)
        s.write(pkt)
        append("→ INPUT element=$element")
    }

    private fun onPacket(payload: ByteArray) {
        append("← packet ${payload.size} bytes")
    }

    private fun append(s: String) {
        logView.text = "${logView.text}\n$s"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        serial?.stop()
    }
}