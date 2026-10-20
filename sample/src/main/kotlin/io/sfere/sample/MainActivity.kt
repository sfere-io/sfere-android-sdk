package io.sfere.sample

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import io.sfere.analytics.Sfere
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * One scrolling screen: settings, consent and opt-out, a button per v1 event, and the requests the
 * mock ingestion server received. Built in code so the sample needs no UI libraries.
 */
class MainActivity : Activity() {
    private lateinit var settings: SampleSettings
    private lateinit var statusText: TextView
    private lateinit var receivedText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SampleSettings(this)
        setContentView(buildContent())
        refreshStatus()
    }

    /** Links that arrive while the app is open. Links that start the Activity are tracked by the SDK. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Sfere.shared.openUrl(intent)
        refreshStatus()
    }

    private fun buildContent(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }

        column.addView(heading("Status"))
        statusText = TextView(this).apply { setTextIsSelectable(true) }
        column.addView(statusText)

        column.addView(heading("Settings (applied on the next launch)"))
        val hostField = textField("Host", settings.host, InputType.TYPE_TEXT_VARIATION_URI)
        val keyField = textField("Write key", settings.writeKey, InputType.TYPE_CLASS_TEXT)
        val timeoutField = textField("Session timeout (minutes)", settings.sessionTimeoutMinutes.toString(), InputType.TYPE_CLASS_NUMBER)
        listOf(hostField, keyField, timeoutField).forEach(column::addView)
        column.addView(
            button("Save settings") {
                settings.host = hostField.text.toString()
                settings.writeKey = keyField.text.toString()
                settings.sessionTimeoutMinutes =
                    timeoutField.text.toString().toIntOrNull() ?: SampleSettings.DEFAULT_SESSION_TIMEOUT_MINUTES
                statusText.text = "Saved. Close the app from Recents and open it again to apply."
            },
        )

        column.addView(heading("Consent and opt-out"))
        val analyticsSwitch = Switch(this).apply { text = "Consent: analytics" }
        val advertisingSwitch = Switch(this).apply { text = "Consent: advertising" }
        val applyConsent = {
            Sfere.shared.setConsent(mapOf("analytics" to analyticsSwitch.isChecked, "advertising" to advertisingSwitch.isChecked))
        }
        analyticsSwitch.setOnCheckedChangeListener { _, _ -> applyConsent() }
        advertisingSwitch.setOnCheckedChangeListener { _, _ -> applyConsent() }
        val enabledSwitch = Switch(this).apply {
            text = "Collection enabled (off = opt-out)"
            isChecked = Sfere.shared.isEnabled
            setOnCheckedChangeListener { _, checked ->
                Sfere.shared.setEnabled(checked)
                refreshStatus()
            }
        }
        listOf(analyticsSwitch, advertisingSwitch, enabledSwitch).forEach(column::addView)

        column.addView(heading("Identity and screens"))
        column.addView(
            button("identify sample_user_1") {
                Sfere.shared.identify(
                    "sample_user_1",
                    mapOf("email" to "fan@example.com", "phone" to "+966500000000", "favorite_team" to "home"),
                )
            },
        )
        column.addView(button("screen: Match Center") { Sfere.shared.screen("Match Center", mapOf("match_id" to "42")) })
        column.addView(button("Open test deep link") { Sfere.shared.openUrl(SampleEvents.TEST_DEEP_LINK) })
        column.addView(button("reset (logout)") { Sfere.shared.reset() })

        column.addView(heading("Commerce and custom events"))
        column.addView(button("purchase") { Sfere.shared.purchase(SampleEvents.purchase()) })
        SampleEvents.trackEvents.forEach { (name, properties) ->
            column.addView(button(name) { Sfere.shared.track(name, properties) })
        }
        column.addView(
            button("send 200 queue_test_event") {
                for (index in 1..SampleEvents.QUEUE_TEST_COUNT) Sfere.shared.track("queue_test_event", mapOf("index" to index))
            },
        )
        column.addView(button("flush now") { Sfere.shared.flush() })

        column.addView(heading("Received by the mock"))
        column.addView(button("Refresh") { loadReceivedRequests() })
        column.addView(button("Clear the mock") { resetMock() })
        receivedText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            text = "Press Refresh to list what ${settings.host} received."
        }
        column.addView(receivedText)

        return ScrollView(this).apply { addView(column) }
    }

    private fun refreshStatus() {
        val sfere = Sfere.shared
        statusText.text = buildString {
            appendLine("Host: ${settings.host}")
            appendLine("Enabled: ${sfere.isEnabled}")
            appendLine("Anonymous id: ${sfere.anonymousId() ?: "–"}")
            append("User id: ${sfere.userId() ?: "–"}")
        }
    }

    private fun loadReceivedRequests() {
        receivedText.text = "Loading…"
        inBackground("GET", "/__events") { body ->
            val requests = JSONArray(body)
            buildString {
                appendLine("${requests.length()} request(s)")
                for (index in requests.length() - 1 downTo 0) {
                    val request = requests.getJSONObject(index)
                    val status = if (request.has("injectedStatus")) " → ${request.getInt("injectedStatus")}" else ""
                    appendLine("${request.optString("method")} ${request.optString("path")}$status")
                    val batch = request.optJSONObject("body")?.optJSONArray("batch") ?: JSONArray()
                    for (eventIndex in 0 until batch.length()) {
                        val event = batch.getJSONObject(eventIndex)
                        val label = event.optString("event").ifEmpty { event.optString("name") }
                        appendLine("  ${event.optString("type")} $label")
                    }
                }
            }
        }
    }

    private fun resetMock() {
        inBackground("POST", "/__reset") { "Cleared." }
    }

    /** Calls the mock's test routes off the main thread and shows the result in [receivedText]. */
    private fun inBackground(method: String, path: String, render: (String) -> String) {
        Thread {
            val text = try {
                val connection = URL(settings.host.trimEnd('/') + path).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                if (method == "POST") {
                    connection.doOutput = true
                    connection.outputStream.close()
                }
                render(connection.inputStream.bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                "Could not reach ${settings.host}$path: $e\nStart the mock with tools/mock_ingest_ctl.sh start."
            }
            mainHandler.post {
                receivedText.text = text
                refreshStatus()
            }
        }.start()
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun textField(hint: String, value: String, inputType: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        this.inputType = inputType
        setSingleLine()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener {
            onClick()
            refreshStatus()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
