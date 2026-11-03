package com.xiaodian.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Backend contract (see backend app/main.py):
//   GET  /kf/handoff/list   -> {"handoffs": [...], "pending_customers": [...]}
//   POST /kf/handoff/reply  -> {"status": "replied" | "blocked_by_48h_limit", ...}
//   POST /kf/handoff/resolve -> {"resolved": true, ...}
//   GET  /kf/media/{id}     -> raw media bytes (TEST ONLY: no app auth yet,
//                              production must require device/PIN auth)
//
// A handoff never blocks text orders (2026-11-03 user decision): the
// App shows it as a parallel pending item, it does not pause the AI.
data class HandoffItem(
    val externalUserid: String,
    val msgid: String,
    val msgtype: String,
    val mediaId: String,
    val createdAt: String,
) {
    fun typeLabel(): String = when (msgtype) {
        "voice" -> "语音"
        "image" -> "图片"
        "video" -> "视频"
        "file" -> "文件"
        else -> msgtype
    }
}

class HandoffRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    fun fetchHandoffs(): List<HandoffItem> {
        val req = Request.Builder().url(url("/kf/handoff/list")).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("待处理列表失败: ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val arr: JSONArray = JSONObject(body).optJSONArray("handoffs") ?: JSONArray()
            val out = mutableListOf<HandoffItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += HandoffItem(
                    externalUserid = o.optString("external_userid"),
                    msgid = o.optString("msgid"),
                    msgtype = o.optString("msgtype"),
                    mediaId = o.optString("media_id"),
                    createdAt = o.optString("created_at"),
                )
            }
            return out
        }
    }

    /** Returns "replied", or throws with the blocked detail. */
    fun reply(externalUserid: String, text: String, msgid: String?): String {
        val json = JSONObject().apply {
            put("external_userid", externalUserid)
            put("text", text)
            if (!msgid.isNullOrBlank()) put("msgid", msgid)
        }.toString()
        val req = Request.Builder().url(url("/kf/handoff/reply"))
            .post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("老板回复失败: ${resp.code} $body")
            val status = JSONObject(body).optString("status")
            if (status == "blocked_by_48h_limit") {
                throw IllegalStateException("48小时5条已用完，未发送；等顾客再发消息后可再回")
            }
            return status
        }
    }

    fun resolve(externalUserid: String, msgid: String?) {
        val json = JSONObject().apply {
            put("external_userid", externalUserid)
            if (!msgid.isNullOrBlank()) put("msgid", msgid)
        }.toString()
        val req = Request.Builder().url(url("/kf/handoff/resolve"))
            .post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("标记已处理失败: ${resp.code}")
        }
    }

    fun downloadMedia(mediaId: String): ByteArray {
        val req = Request.Builder().url(url("/kf/media/$mediaId")).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("媒体下载失败: ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    fun mediaUrl(mediaId: String): String = url("/kf/media/$mediaId")
}
