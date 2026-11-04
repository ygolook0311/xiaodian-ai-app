package com.xiaodian.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Backend contract (see backend app/main.py):
//   GET  /kf/orders/list            -> {"orders": [...]}
//   POST /kf/orders/{id}/status     -> {"order": {...}} or 400/404
//
// Orders are REAL only: the backend records one only when an AI
// confirmation was actually sent to a customer (status
// pending_payment). No sample orders are seeded. An empty list
// means no order since the backend order store was deployed --
// the UI must say "暂无订单", never invent rows.
// Kitchen may change STATUS only, never price, refund or menu.
data class OrderLine(val name: String, val qty: Int, val notes: String)

data class KitchenOrder(
    val id: String,
    val pickupNo: String,
    val externalUserid: String,
    val items: List<OrderLine>,
    val subtotal: String,
    val totalAmount: String,
    val payableTailAmount: String,
    val fulfillment: String,
    val status: String,
    val createdAt: String,
) {
    fun statusLabel(): String = when (status) {
        "pending_payment" -> "待付款"
        "ordered" -> "已下单"
        "making" -> "制作中"
        "ready" -> "已出餐"
        "delivering" -> "配送中"
        "awaiting_pickup" -> "待自取"
        "completed" -> "已完成"
        "handoff" -> "待人工"
        "cancelled" -> "已取消"
        else -> status
    }

    fun fulfillmentLabel(): String = when (fulfillment) {
        "pickup" -> "自取"
        "delivery" -> "配送"
        "dine_in" -> "堂食"
        else -> fulfillment
    }

    /** Only the legal next statuses from orders.py, for buttons. */
    fun nextStatuses(): List<String> = when (status) {
        "pending_payment" -> listOf("ordered", "cancelled")
        "ordered" -> listOf("making", "cancelled")
        "making" -> listOf("ready")
        "ready" -> listOf("delivering", "awaiting_pickup", "completed")
        "delivering" -> listOf("completed")
        "awaiting_pickup" -> listOf("completed")
        else -> emptyList()
    }
}

class OrderRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val auth: AppAuth = AppAuth(),
) {
    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    fun fetchOrders(): List<KitchenOrder> {
        val req = Request.Builder().url(url("/kf/orders/list")).withAuth(auth).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("订单列表失败: ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val arr: JSONArray = JSONObject(body).optJSONArray("orders") ?: JSONArray()
            val out = mutableListOf<KitchenOrder>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val lines = mutableListOf<OrderLine>()
                for (j in 0 until itemsArr.length()) {
                    val it = itemsArr.getJSONObject(j)
                    lines += OrderLine(
                        name = it.optString("name"),
                        qty = it.optInt("qty", 1),
                        notes = it.optString("notes"),
                    )
                }
                out += KitchenOrder(
                    id = o.optString("id"),
                    pickupNo = o.optString("pickup_no"),
                    externalUserid = o.optString("external_userid"),
                    items = lines,
                    subtotal = o.optString("subtotal"),
                    totalAmount = o.optString("total_amount"),
                    payableTailAmount = o.optString("payable_tail_amount"),
                    fulfillment = o.optString("fulfillment"),
                    status = o.optString("status"),
                    createdAt = o.optString("created_at"),
                )
            }
            return out
        }
    }

    fun updateStatus(orderId: String, newStatus: String) {
        val json = JSONObject().apply {
            put("status", newStatus)
            put("actor", "kitchen")
        }.toString()
        val req = Request.Builder().url(url("/kf/orders/$orderId/status")).withAuth(auth, write = true)
            .post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("状态推进失败: ${resp.code} ${resp.body?.string() ?: ""}")
            }
        }
    }
}
