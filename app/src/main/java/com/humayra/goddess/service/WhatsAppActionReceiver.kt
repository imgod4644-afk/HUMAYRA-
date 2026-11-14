package com.humayra.goddess.service

import android.app.NotificationManager
import android.content.*
import android.widget.Toast

class WhatsAppActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val nid = intent.getIntExtra("nid", -1)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nid > 0) nm.cancel(nid)
        when (intent.action) {
            "SEND" -> {
                val ok = HumayraNotificationService.sendReplyNow(ctx, id, intent.getStringExtra("draft").orEmpty())
                Toast.makeText(ctx, if (ok) "✅ WhatsApp reply sent" else "Reply expired", Toast.LENGTH_SHORT).show()
            }
            "IGNORE" -> HumayraNotificationService.pending.remove(id)
        }
    }
}
