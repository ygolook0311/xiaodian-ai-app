package com.xiaodian.ai

// Stub only. Real parsing of a merchant's own WeChat pay-reminder wording
// (amount, remark, nickname) can only be measured in a pilot with that merchant's
// phone; it must not be guessed from another merchant's screenshot.
// Contract when implemented: forward (amount, time, rawText) to the server;
// the server — never this client — decides payment matching.
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // STUB: do not parse or forward anything until pilot wording is measured.
        // Planned: if sbn.packageName is the merchant's WeChat and the notification
        // text matches the pilot-measured pay-reminder pattern, forward it verbatim
        // to the server's payment-signal endpoint for matching.
    }
}
