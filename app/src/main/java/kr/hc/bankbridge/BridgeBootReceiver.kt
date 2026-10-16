package kr.hc.bankbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BridgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Settings(context).faxEnabled) {
            runCatching { FaxBridgeService.start(context) }
        }
    }
}
