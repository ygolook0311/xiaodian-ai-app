package com.xiaodian.ai

// App entry point. Boss mode + kitchen mode in one Android app
// (Android-only decision, 2026-11-03). Mode switch is one tap;
// kitchen mode may never change price, refund, or menu (ModePolicy).
// Boss money actions still go through PinGate + repeat confirmation
// (not yet wired to a server write -- see AppLogic.kt).
//
// Server address is editable in the boss screen and stored in
// SharedPreferences; the default is the pilot NAS backend. It is
// not a secret, and it must never be hard-coded as the only value.
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var mode: AppMode = AppMode.KITCHEN
    private val pinGate = PinGate(expectedPinHash = "PLACEHOLDER_DO_NOT_SHIP")
    private val offlineQueue = OfflineQueue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("xiaodian", Context.MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var currentMode by remember { mutableStateOf(mode) }
                var serverUrl by remember {
                    mutableStateOf(
                        prefs.getString("server_base_url", "https://kf.golook.eu.org") ?: ""
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Button(onClick = {
                            currentMode = AppMode.KITCHEN
                            mode = AppMode.KITCHEN
                        }) { Text("后厨模式") }
                        OutlinedButton(onClick = {
                            currentMode = AppMode.BOSS
                            mode = AppMode.BOSS
                        }) { Text("老板模式") }
                        // App close (2026-11-03 user report: "App连关闭都没有").
                        // Android apps normally close via system Back/Home,
                        // but this app had no in-app exit at all, so add one:
                        // finish() closes this activity, i.e. exits the app.
                        OutlinedButton(onClick = { finish() }) { Text("退出 App") }
                    }
                    TextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            prefs.edit().putString("server_base_url", it).apply()
                        },
                        label = { Text("后端地址（试点 NAS）") },
                    )
                    when (currentMode) {
                        AppMode.BOSS -> HandoffScreen(baseUrl = serverUrl)
                        AppMode.KITCHEN -> Column {
                            OrderScreen(baseUrl = serverUrl)
                            Text("离线队列待处理数：${offlineQueue.size()}")
                        }
                    }
                }
            }
        }
    }

    fun switchMode(next: AppMode) { mode = next }
    fun currentMode(): AppMode = mode
}
