package com.xiaodian.ai

// Boss-only menu API (App step 3). Kitchen mode never calls this:
// ModePolicy in MainActivity keeps menu editing boss-only.
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MenuItem(
    val id: String, val name: String, val price: String,
    val available: Boolean, val source: String,
)

data class Usage(
    val ordersTotal: Int, val ordersByStatus: String,
    val completedAmount: Double, val menuTotal: Int, val menuAvailable: Int,
)

class MenuRepository(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    private fun post(path: String, json: JSONObject): JSONObject {
        val req = Request.Builder().url(url(path))
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("$path 失败: ${resp.code} $body")
            return JSONObject(body)
        }
    }

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url(url(path)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("$path 失败: ${resp.code} $body")
            return JSONObject(body)
        }
    }

    fun fetchMenu(): List<MenuItem> {
        val arr = get("/kf/menu/list").optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MenuItem(o.optString("id"), o.optString("name"), o.optString("price"),
                o.optBoolean("available"), o.optString("source"))
        }
    }

    fun add(name: String, price: Double, source: String = "manual") {
        val json = JSONObject().put("name", name).put("price", price).put("source", source)
        post("/kf/menu/add", json)
    }

    fun delete(id: String) {
        post("/kf/menu/delete", JSONObject().put("id", id))
    }

    fun update(id: String, price: Double? = null, available: Boolean? = null) {
        val json = JSONObject().put("id", id)
        if (price != null) json.put("price", price)
        if (available != null) json.put("available", available)
        post("/kf/menu/update", json)
    }

    fun generate(description: String): List<MenuItem> {
        val json = JSONObject().put("description", description)
        val arr = post("/kf/menu/generate", json).optJSONArray("drafts") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MenuItem("", o.optString("name"), o.optDouble("price").toString(), true, "ai")
        }
    }

    fun fetchUsage(): Usage {
        val o = get("/kf/usage")
        return Usage(
            o.optInt("orders_total"), o.optJSONObject("orders_by_status")?.toString() ?: "{}",
            o.optDouble("completed_amount"), o.optInt("menu_total"), o.optInt("menu_available"),
        )
    }
}
