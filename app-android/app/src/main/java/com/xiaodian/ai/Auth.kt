package com.xiaodian.ai

// Production auth headers (2026-11-04). Device key identifies the
// App install; PIN is required for writes. When the backend has no
// APP_DEVICE_KEY set, headers are simply ignored (pilot dev mode).
import okhttp3.Request

data class AppAuth(val deviceKey: String = "", val pin: String = "")

fun Request.Builder.withAuth(auth: AppAuth, write: Boolean = false): Request.Builder {
    if (auth.deviceKey.isNotBlank()) header("X-Device-Key", auth.deviceKey)
    if (write && auth.pin.isNotBlank()) header("X-Device-Pin", auth.pin)
    return this
}
