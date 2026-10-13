package kr.hc.bankbridge

import android.app.Application

class BridgeApp : Application() {
    override fun onCreate() { super.onCreate(); Bridge.schedule(this) }
}
