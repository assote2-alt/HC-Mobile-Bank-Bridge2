package kr.hc.bankbridge.manual

import android.app.Application
import kr.hc.bankbridge.Bridge

class ManualApp : Application() {
    override fun onCreate() { super.onCreate(); Bridge.schedule(this) }
}
