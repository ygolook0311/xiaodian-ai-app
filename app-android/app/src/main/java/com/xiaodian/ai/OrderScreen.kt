package com.xiaodian.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Kitchen queue (App step 2, 2026-11-03). Manual refresh only --
// same rule as the handoff screen: no timed polling, so a button
// press or a scroll is never interrupted by a background refresh.
// Kitchen mode: status buttons only. No price change, no refund,
// no menu edit here (ModePolicy: those are boss-only, step 3).
@Composable
fun OrderScreen(baseUrl: String, auth: AppAuth = AppAuth()) {
    var orders by remember { mutableStateOf<List<KitchenOrder>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            refreshing = true
            try {
                val fresh = withContext(Dispatchers.IO) {
                    OrderRepository(baseUrl, auth = auth).fetchOrders()
                }
                if (fresh != orders) orders = fresh
                error = ""
            } catch (e: Exception) {
                error = "拉取订单失败：${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(baseUrl) { refresh() }

    Column(Modifier.padding(16.dp)) {
        Text("后厨订单队列")
        Text("只显示真实订单：AI 确认单发出后才进队列，没有订单时不造假单。")
        Text("后厨只能推进状态，不能改价、退款、改菜单。")
        if (error.isNotBlank()) Text(error)
        Button(onClick = { refresh() }, enabled = !refreshing) {
            Text(if (refreshing) "刷新中…" else "立即刷新")
        }
        Spacer(Modifier.height(8.dp))
        if (orders.isEmpty()) {
            Text("暂无订单")
            Text("说明：自后端订单存储部署后，还没有 AI 确认单发出；新单发出后点“立即刷新”就能看到。")
        } else {
            // Plain Column, NOT LazyColumn: MainActivity already
            // vertical-scrolls the whole page (added for the menu
            // page, 2026-11-03). A LazyColumn inside a vertical
            // scroll parent is measured with infinite height and
            // crashes the app on refresh -- that is exactly the
            // "kitchen mode exits on refresh" bug reported after
            // build-18. Pilot queues are small, so a plain Column
            // is fine and the outer scroll handles the scrolling.
            Column {
                for (order in orders) {
                    OrderCard(order, baseUrl, auth, onDone = { refresh() })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: KitchenOrder, baseUrl: String, auth: AppAuth = AppAuth(), onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("取餐号 ${order.pickupNo} · ${order.statusLabel()} · ${order.fulfillmentLabel()}")
            Text("顾客 ${order.externalUserid.take(12)}… · ${order.createdAt}")
            for (line in order.items) {
                val notes = if (line.notes.isNotBlank()) "（备注：${line.notes}）" else ""
                Text("- ${line.name} ×${line.qty}$notes")
            }
            Text("合计 ${order.totalAmount} 元，应付尾数 ${order.payableTailAmount} 元")
            Row {
                for (next in order.nextStatuses()) {
                    val label = when (next) {
                        "ordered" -> "确认下单"
                        "making" -> "开始制作"
                        "ready" -> "已出餐"
                        "delivering" -> "开始配送"
                        "awaiting_pickup" -> "待自取"
                        "completed" -> "已完成"
                        "cancelled" -> "取消"
                        else -> next
                    }
                    Button(onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    OrderRepository(baseUrl, auth = auth).updateStatus(order.id, next)
                                }
                                status = "已推进"
                                onDone()
                            } catch (e: Exception) {
                                status = "推进失败：${e.message}"
                            }
                        }
                    }) { Text(label) }
                }
            }
            if (status.isNotBlank()) Text(status)
        }
    }
}
