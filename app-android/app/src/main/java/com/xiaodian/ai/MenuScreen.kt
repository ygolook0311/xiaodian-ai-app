package com.xiaodian.ai

// Boss daily work (App step 3): manual add, price change (audit-logged
// on the backend), stop/resume selling, AI draft generation, usage.
// AI drafts are NOT live until the boss taps "加入菜单" for each one.
// Kitchen mode has no access to this screen at all.
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

@Composable
fun MenuScreen(baseUrl: String, auth: AppAuth = AppAuth()) {
    var items by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var usage by remember { mutableStateOf<Usage?>(null) }
    var status by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var aiDesc by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                val menu = withContext(Dispatchers.IO) { MenuRepository(baseUrl, auth = auth).fetchMenu() }
                val u = withContext(Dispatchers.IO) { MenuRepository(baseUrl, auth = auth).fetchUsage() }
                items = menu; usage = u
            } catch (e: Exception) {
                status = "加载失败：${e.message}"
            }
        }
    }

    LaunchedEffect(baseUrl) { refresh() }

    Column(Modifier.padding(8.dp)) {
        Text("老板日常：菜单管理（仅老板模式，后厨改不了菜单和价格）")
        // Status FIRST, not at the bottom: the old layout put this
        // message below all menu items, where it was clipped off
        // screen (no scrolling), so add failures looked silent.
        if (status.isNotEmpty()) Text("提示：$status")
        usage?.let {
            Text("用量：订单 ${it.ordersTotal} 单，状态 ${it.ordersByStatus}，" +
                "已完成金额 ${it.completedAmount} 元（仅已完成单，不含待付款），" +
                "菜单 ${it.menuTotal} 道，在售 ${it.menuAvailable} 道")
        }
        Row {
            OutlinedButton(onClick = { refresh() }) { Text("立即刷新") }
        }
        // Manual add
        TextField(value = newName, onValueChange = { newName = it }, label = { Text("新菜名") })
        TextField(value = newPrice, onValueChange = { newPrice = it }, label = { Text("价格（元）") })
        Button(onClick = {
            // Validate in the App first, so a bad price or an empty
            // name shows a reason at the top instead of failing
            // silently (e.g. "25元" is not a number, and a duplicate
            // name is rejected by the backend with 400).
            if (newName.isBlank()) {
                status = "添加失败：菜名不能为空"
                return@Button
            }
            val price = newPrice.toDoubleOrNull()
            if (price == null || price <= 0) {
                status = "添加失败：价格只能填数字，如 25，不要带“元”字"
                return@Button
            }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        MenuRepository(baseUrl, auth = auth).add(newName.trim(), price, "manual")
                    }
                    newName = ""; newPrice = ""; status = "已添加"; refresh()
                } catch (e: Exception) {
                    status = "添加失败：${e.message}（重名菜品会被拒绝）"
                }
            }
        }) { Text("手动添加") }
        // AI generate
        TextField(value = aiDesc, onValueChange = { aiDesc = it },
            label = { Text("AI 生成描述，如：川菜小店，家常菜，20-30元") })
        Button(onClick = {
            scope.launch {
                try {
                    val d = withContext(Dispatchers.IO) {
                        MenuRepository(baseUrl, auth = auth).generate(aiDesc)
                    }
                    drafts = d; status = "AI 草稿 ${d.size} 道，点加入菜单才生效"
                } catch (e: Exception) {
                    status = "AI 生成失败：${e.message}（无模型密钥时请用手动添加）"
                }
            }
        }) { Text("AI 生成菜单草稿") }
        drafts.forEach { draft ->
            Row {
                Text("${draft.name} ${draft.price} 元（草稿）")
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                MenuRepository(baseUrl, auth = auth).add(draft.name, draft.price.toDouble(), "ai")
                            }
                            drafts = drafts - draft; status = "已加入：${draft.name}"; refresh()
                        } catch (e: Exception) {
                            status = "加入失败：${e.message}"
                        }
                    }
                }) { Text("加入菜单") }
            }
        }
        // Live menu
        items.forEach { item ->
            var editPrice by remember(item.id) { mutableStateOf(item.price) }
            // Status as its own explicit value, not glued into a
            // string-concat expression: the old
            // "元 " + if (available) "在售" else "已停售" + "（…）"
            // parsed as "元 " + (if … else ("已停售" + "（…）")),
            // so the source tag only appeared on stopped dishes.
            val statusText = if (item.available) "在售" else "已停售"
            Row {
                Text("${item.name} ${item.price} 元")
                Text(" $statusText（${item.source}）")
            }
            Row {
                TextField(value = editPrice, onValueChange = { editPrice = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("改价") })
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                MenuRepository(baseUrl, auth = auth).update(item.id, price = editPrice.toDouble())
                            }
                            status = "已改价：${item.name}"; refresh()
                        } catch (e: Exception) {
                            status = "改价失败：${e.message}"
                        }
                    }
                }) { Text("改价") }
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                MenuRepository(baseUrl, auth = auth).update(item.id, available = !item.available)
                            }
                            refresh()
                        } catch (e: Exception) {
                            status = "停售/恢复失败：${e.message}"
                        }
                    }
                }) { Text(if (item.available) "停售" else "恢复在售") }
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                MenuRepository(baseUrl, auth = auth).delete(item.id)
                            }
                            status = "已删除：${item.name}"; refresh()
                        } catch (e: Exception) {
                            status = "删除失败：${e.message}（有订单的菜只能停售，不能删除）"
                        }
                    }
                }) { Text("删除") }
            }
        }
    }
}
