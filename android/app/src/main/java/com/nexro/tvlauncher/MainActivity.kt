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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        bridge = TvBridge(this) { msg -> runOnUiThread { if (::status.isInitialized) status.text = msg } }
        render()
        bridge.start()
    }

    private fun render() {
        NexroTvOsExperience(this) { showRemoteSettings() }.render()
        bridge.start()
    }

    private fun showRemoteSettings() {
        val details = "Optional phone remote\\n\\nPIN: ${bridge.pin}\\nTV address: ${localIp()}:8765\\n\\nOpen this address on a phone connected to the same Wi-Fi network. Pairing is optional."
        android.app.AlertDialog.Builder(this)
            .setTitle("Phone remote")
            .setMessage(details)
            .setNegativeButton("Close", null)
            .setNeutralButton("New PIN") { _, _ -> bridge.rotatePin(); showRemoteSettings() }
            .setPositiveButton("Open remote on this TV") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://127.0.0.1:8765/"))) } catch (_: Exception) {}
            }.show()
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
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { move("left"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { move("right"); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { move("up"); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { move("down"); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { launchApp(selected); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { bridge.stop(); super.onDestroy() }
}

private class TvBridge(private val activity: MainActivity, private val report: (String) -> Unit) {
    @Volatile var pin: String = newPin()
        private set
    @Volatile private var token: String? = null
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false
    private fun newPin() = (100000 + SecureRandom().nextInt(900000)).toString()

    fun rotatePin() { pin = newPin(); token = null; report("New PIN generated; previous phone sessions revoked.") }
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
                    method == "POST" && path == "/api/pair" -> if (body.optString("pin") == pin) {
                        token = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
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