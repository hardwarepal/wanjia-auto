package com.wanjia.auto.core

import android.content.Context
import android.os.PowerManager
import com.wanjia.auto.LogBus

/** 亮屏控制：ACQUIRE_CAUSES_WAKEUP 保证息屏状态下也能点亮 */
object ScreenWake {

    private var wl: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = 10 * 60 * 1000L) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        release()
        @Suppress("DEPRECATION")
        wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "wanjia:wake"
        ).also { it.acquire(timeoutMs) }
        LogBus.i("屏幕已点亮，保持常亮 ${timeoutMs / 1000}s")
    }

    @Synchronized
    fun release() {
        runCatching { wl?.takeIf { it.isHeld }?.release() }
        wl = null
    }
}
