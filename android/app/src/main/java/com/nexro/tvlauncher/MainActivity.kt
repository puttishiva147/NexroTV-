package com.nexro.tvlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 15)
    private val panel = Color.rgb(18, 22, 31)
    private val tile = Color.rgb(26, 32, 44)
    private val mint = Color.rgb(117, 229, 209)
    private val white = Color.rgb(245, 247, 251)
    private val muted = Color.rgb(145, 155, 173)
    private lateinit var status: TextView
    private lateinit var pinView: TextView
    private lateinit var ipView: TextView
    private lateinit var bridge: TvBridge
    private val apps = mutableListOf<Pair<String, Intent>>()
    private val tiles = mutableListOf<View>()
    private var selected = 0
    private var tvOsExperience: NexroTvOsExperience? = null
    private var remoteDialog: android.app.AlertDialog? = null
    private var firstLaunchRemotePromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        bridge = TvBridge(this) { msg ->
            runOnUiThread {
                if (::status.isInitialized) status.text = msg
                if (msg == "Phone remote connected.") {
                    remoteDialog?.dismiss()
                    remoteDialog = null
                    android.widget.Toast.makeText(this, "Phone remote connected", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        render()
        bridge.start()
        if (!getPreferences(MODE_PRIVATE).getBoolean("remote_setup_prompt_seen", false)) {
            firstLaunchRemotePromptShown = true
            getPreferences(MODE_PRIVATE).edit().putBoolean("remote_setup_prompt_seen", true).apply()
            window.decorView.postDelayed({ if (!isFinishing && firstLaunchRemotePromptShown) showRemoteSettings() }, 700)
        }
    }

    private fun render() {
        tvOsExperience = NexroTvOsExperience(this) { showRemoteSettings() }
        tvOsExperience!!.render()
        bridge.start()
    }

    private fun showRemoteSettings() {
        if (isFinishing) return
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt().coerceAtLeast(1)
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        }
        val backgroundColor = Color.rgb(17, 21, 31)
        val primary = Color.rgb(245, 248, 255)
        val secondary = Color.rgb(164, 176, 198)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(14), dp(26), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(53, 66, 88))
            }
        }
        val connected = bridge.isPaired
        content.addView(label(if (connected) "Phone Remote Connected" else "Connect your phone to Nexro TV", 22f, primary, true),
            LinearLayout.LayoutParams(-1, -2))
        content.addView(label(
            if (connected) "Your phone is paired. You can remove this connection below or pair again later."
            else "Use the same Wi-Fi network on your TV and phone.",
            14f, secondary
        ).apply { setPadding(0, dp(8), 0, dp(18)) }, LinearLayout.LayoutParams(-1, -2))

        fun infoCard(title: String, value: String, hint: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(25, 31, 44))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(55, 69, 93))
                }
            }
            card.addView(label(title.uppercase(), 11f, secondary, true))
            card.addView(label(value, 22f, primary, true).apply {
                setPadding(0, dp(7), 0, dp(5))
                setTextIsSelectable(true)
            })
            card.addView(label(hint, 12f, secondary))
            return card
        }

        val address = "${localIp()}:8765"
        if (!connected) {
            content.addView(infoCard("TV ADDRESS", address, "Enter this address in the phone browser"),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            content.addView(infoCard("PAIRING PIN", bridge.pin, "Six digits; only share with your own phone"),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            content.addView(label("1. Open http://$address on your phone.\n2. Enter the 6-digit PIN.\n3. Stay on the same Wi-Fi network.", 14f, primary).apply {
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(0, 0, 0, dp(12))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            content.addView(infoCard("STATUS", "Connected", "This phone has an active pairing session"),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }

        val builder = android.app.AlertDialog.Builder(this).setView(content)
        if (connected) {
            builder.setNegativeButton("Close", null)
                .setNeutralButton("Remove Connection", null)
                .setPositiveButton("Connect another", null)
        } else {
            builder.setNegativeButton("Not now", null)
                .setNeutralButton("New PIN", null)
                .setPositiveButton("Copy address", null)
        }
        val dialog = builder.create()
        remoteDialog = dialog
        dialog.setOnDismissListener { if (remoteDialog === dialog) remoteDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                if (connected) {
                    bridge.removePairing()
                    dialog.dismiss()
                    showRemoteSettings()
                } else {
                    bridge.rotatePin()
                    dialog.dismiss()
                    showRemoteSettings()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (connected) {
                    dialog.dismiss()
                    bridge.removePairing()
                    showRemoteSettings()
                } else {
                    try {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nexro TV address", "http://$address/"))
                        android.widget.Toast.makeText(this, "TV address copied", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this, "Could not copy address", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.72f).toInt(), -2)
    }

    private lateinit var remotePanel: LinearLayout

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(1, stroke)
        }

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress ?: "TV-IP"
    } catch (_: Exception) { "TV-IP" }

    private fun launchApp(index: Int) {
        if (index !in apps.indices) return
        selected = index
        try { startActivity(apps[index].second) } catch (_: Exception) { status.text = "This app could not be opened." }
    }

    private fun move(action: String) {
        if (apps.isEmpty()) return
        selected = when (action) {
            "left" -> (selected - 1 + apps.size) % apps.size
            "right" -> (selected + 1) % apps.size
            "up" -> (selected - 4 + apps.size) % apps.size
            "down" -> (selected + 4) % apps.size
            else -> selected
        }
        tiles.getOrNull(selected)?.requestFocus()
    }

    fun execute(action: String, value: String?): JSONObject {
        tvOsExperience?.let { return it.executeRemote(action, value) }
        var ok = true
        val message: String
        when (action) {
            "volume_up" -> { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); message = "Volume up requested." }
            "volume_down" -> { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); message = "Volume down requested." }
            "mute", "unmute" -> { val a = getSystemService(AUDIO_SERVICE) as AudioManager; a.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (action == "mute") AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); message = if (action == "mute") "Mute requested." else "Sound enabled." }
            "up", "down", "left", "right" -> { runOnUiThread { move(action) }; message = "Launcher focus moved $action." }
            "select" -> { runOnUiThread { launchApp(selected) }; message = "Opening selected app." }
            "home" -> { runOnUiThread { move("home"); if (apps.isNotEmpty()) { selected = 0; tiles.firstOrNull()?.requestFocus() } }; message = "Returned to Nexro home." }
            "open_app" -> {
                val name = value?.lowercase()?.trim().orEmpty()
                val i = apps.indexOfFirst { it.first.lowercase().contains(name) || name.contains(it.first.lowercase()) }
                if (i >= 0) { runOnUiThread { launchApp(i) }; message = "Opening ${apps[i].first}." } else { ok = false; message = "App not found: $name" }
            }
            "play", "pause" -> { val a = getSystemService(AUDIO_SERVICE) as AudioManager; a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); message = "Media key sent; the active app decides whether to respond." }
            "back" -> { runOnUiThread { onBackPressed() }; message = "Back requested." }
            "settings" -> { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }; message = "Opening Android settings." }
            "power_off" -> { ok = false; message = "Android does not allow ordinary launcher apps to power off every TV." }
            "channel_up", "channel_down", "number", "input" -> { ok = false; message = "This Android TV does not expose this control to the launcher." }
            else -> { ok = false; message = "Unsupported command: $action" }
        }
        return JSONObject().put("ok", ok).put("executed", ok).put("simulated", false).put("action", action).put("message", message)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (tvOsExperience?.handleKeyCode(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { tvOsExperience?.dispose(); bridge.stop(); super.onDestroy() }
}

private class TvBridge(private val activity: MainActivity, private val report: (String) -> Unit) {
    @Volatile var pin: String = newPin()
        private set
    @Volatile private var token: String? = null
    @Volatile var isPaired: Boolean = false
        private set
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false
    private fun newPin() = (100000 + SecureRandom().nextInt(900000)).toString()

    fun rotatePin() { pin = newPin(); token = null; isPaired = false; report("New PIN generated; previous phone sessions revoked.") }
    fun removePairing() { token = null; isPaired = false; report("Phone remote connection removed.") }
    fun start() {
        if (running) return
        running = true
        thread(name = "NexroTVBridge") {
            try {
                socket = ServerSocket(8765)
                report("Local bridge ready on port 8765.")
                while (running) {
                    val client = socket!!.accept()
                    thread(name = "NexroTVClient") { handle(client) }
                }
            } catch (e: Exception) { if (running) report("Bridge error: ${e.message}") }
        }
    }
    fun stop() { running = false; try { socket?.close() } catch (_: Exception) {} }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            try {
                s.soTimeout = 4000
                val input = BufferedInputStream(s.getInputStream())
                val headerBytes = ByteArrayOutputStream()
                var state = 0
                while (headerBytes.size() < 8192) {
                    val b = input.read()
                    if (b < 0) return
                    headerBytes.write(b)
                    state = when {
                        state == 0 && b == 13 -> 1
                        state == 1 && b == 10 -> 2
                        state == 2 && b == 13 -> 3
                        state == 3 && b == 10 -> 4
                        b == 13 -> 1
                        else -> 0
                    }
                    if (state == 4) break
                }
                val lines = headerBytes.toString("UTF-8").split("\r\n")
                val parts = lines.firstOrNull()?.split(" ") ?: return
                val method = parts.getOrNull(0) ?: return
                val path = parts.getOrNull(1) ?: return
                val headers = lines.drop(1).mapNotNull { line ->
                    val i = line.indexOf(':')
                    if (i > 0) line.substring(0, i).lowercase() to line.substring(i + 1).trim() else null
                }.toMap()
                val length = (headers["content-length"] ?: "0").toIntOrNull()?.coerceIn(0, 16384) ?: 0
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val n = input.read(bytes, offset, length - offset)
                    if (n < 0) break
                    offset += n
                }
                val body = JSONObject(String(bytes, 0, offset, Charsets.UTF_8).ifBlank { "{}" })
                if (method == "OPTIONS") { respond(s, 200, JSONObject().put("ok", true)); return }
                if (method == "GET" && (path == "/" || path == "/index.html")) { respondHtml(s); return }
                val auth = headers["authorization"]?.removePrefix("Bearer ")
                val result = when {
                    method == "GET" && path == "/api/health" -> JSONObject().put("ok", true).put("service", "Nexro TV Launcher Bridge")
                    method == "POST" && path == "/api/pair" -> if (normalizePin(body.opt("pin")) == pin) {
                        token = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
                        isPaired = true
                        report("Phone remote connected.")
                        JSONObject().put("ok", true).put("token", token).put("capabilities", JSONObject().put("volume", true).put("navigation", true).put("apps", true).put("media", true).put("power", false))
                    } else {
                        respond(s, 401, JSONObject().put("error", "Incorrect TV pairing PIN.")); return
                    }
                    token != null && auth == token && method == "POST" && path == "/api/command" ->
                        activity.execute(body.optString("action"), body.opt("value")?.takeUnless { it == JSONObject.NULL }?.toString())
                    else -> { respond(s, 401, JSONObject().put("error", "Pairing token missing or endpoint not found.")); return }
                }
                respond(s, if (result.optBoolean("ok", true)) 200 else 422, result)
            } catch (_: Exception) {
                try { respond(s, 400, JSONObject().put("error", "Malformed request.")) } catch (_: Exception) {}
            }
        }
    }

    private fun normalizePin(value: Any?): String = value?.toString()?.trim()?.filter { it.isDigit() }.orEmpty()

    private fun respondHtml(s: java.net.Socket) {
        val data = activity.assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n"
        s.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(data); flush() }
    }

    private fun respond(s: java.net.Socket, code: Int, json: JSONObject) {
        val data = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 422 -> "Unprocessable Entity"; else -> "Error" }
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Content-Type, Authorization\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n"
        s.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(data); flush() }
    }
}