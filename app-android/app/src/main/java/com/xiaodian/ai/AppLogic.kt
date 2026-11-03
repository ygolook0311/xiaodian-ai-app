package com.xiaodian.ai

// Pure logic (no Android framework types): PIN gate, mode policy, offline queue.
// Kept framework-free so the rules can be reviewed without an Android toolchain.
// Real Android UI (Compose) calls these classes; it must not re-implement the rules.

enum class AppMode { KITCHEN, BOSS }

/** Boss-mode money actions require PIN + spoken/text repeat confirmation. */
class PinGate(private val expectedPinHash: String, private val maxFailures: Int = 5) {
    private var failures = 0
    private var lockedUntilMs: Long = 0
    fun isLocked(nowMs: Long): Boolean = nowMs < lockedUntilMs
    /** Returns true only when the PIN matches and the gate is not locked. */
    fun check(pinHash: String, nowMs: Long, lockMs: Long = 600_000L): Boolean {
        if (isLocked(nowMs)) return false
        if (pinHash == expectedPinHash) { failures = 0; return true }
        failures++
        if (failures >= maxFailures) lockedUntilMs = nowMs + lockMs
        return false
    }
    fun failures(): Int = failures
}

/** Kitchen mode may view and mark orders; it may never change price, refund, or menu. */
object ModePolicy {
    fun canChangePrice(mode: AppMode): Boolean = mode == AppMode.BOSS
    fun canRefund(mode: AppMode): Boolean = mode == AppMode.BOSS
    fun canEditMenu(mode: AppMode): Boolean = mode == AppMode.BOSS
    fun canMarkOrderStatus(mode: AppMode): Boolean = true // both modes, with pickup_no
}

/** Offline queue: orders received while disconnected are queued, then
 *  replayed in order on reconnect, deduplicated by order id. The server
 *  remains the source of truth; this queue never decides an order state. */
class OfflineQueue {
    private val queued = LinkedHashMap<String, String>() // orderId -> payload JSON
    fun enqueue(orderId: String, payloadJson: String) { queued[orderId] = payloadJson }
    fun drain(): List<Pair<String, String>> {
        val out = queued.entries.map { it.key to it.value }
        queued.clear()
        return out
    }
    fun size(): Int = queued.size
}

/** Push message contract shared with the backend (see backend app/push.py).
 *  type: "order_created" | "order_status" | "handoff"; every message carries pickup_no. */
data class PushMessage(val type: String, val pickupNo: String, val payloadJson: String) {
    init { require(pickupNo.isNotBlank()) { "push message must carry pickup_no" } }
}
