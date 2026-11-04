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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                // Production auth (2026-11-04): device key + operator
                // PIN, stored like the server URL. Empty = backend
                // auth off (pilot dev mode). When the NAS sets
                // APP_DEVICE_KEY, these must match it, or every
                // App call returns 401/403.
                var deviceKey by remember {
                    mutableStateOf(prefs.getString("device_key", "") ?: "")
                }
                var devicePin by remember {
                    mutableStateOf(prefs.getString("device_pin", "") ?: "")
                }
                val auth = AppAuth(deviceKey, devicePin)
                // Vertical scroll is REQUIRED, not cosmetic: without
                // it the menu page clips at the first dish (see the
                // 2026-11-03 screenshot: only 番茄炒蛋 visible, the
                // price-edit row cut off at the bottom, and a new
                // manually added dish lands below the fold where
                // the user can never see it, so it looks like the
                // add failed). Every screen in this app must scroll.
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
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
                    TextField(
                        value = deviceKey,
                        onValueChange = {
                            deviceKey = it
                            prefs.edit().putString("device_key", it).apply()
                        },
                        label = { Text("设备密钥（与 NAS 的 APP_DEVICE_KEY 一致）") },
                    )
                    TextField(
                        value = devicePin,
                        onValueChange = {
                            devicePin = it
                            prefs.edit().putString("device_pin", it).apply()
                        },
                        label = { Text("操作 PIN（写操作用，后端设了 PIN 才需要）") },
                    )
                    when (currentMode) {
                        AppMode.BOSS -> {
                            var bossTab by remember { mutableStateOf("handoff") }
                            Row {
                                Button(onClick = { bossTab = "handoff" }) { Text("待人工") }
                                OutlinedButton(onClick = { bossTab = "menu" }) { Text("菜单管理") }
                            }
                            if (bossTab == "menu") MenuScreen(baseUrl = serverUrl, auth = auth)
                            else HandoffScreen(baseUrl = serverUrl, auth = auth)
                        }
                        AppMode.KITCHEN -> Column {
                            OrderScreen(baseUrl = serverUrl, auth = auth)
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
