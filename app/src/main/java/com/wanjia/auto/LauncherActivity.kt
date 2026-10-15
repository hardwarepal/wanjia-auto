package com.wanjia.auto

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wanjia.auto.core.Unlocker
import com.wanjia.auto.service.AutomationAccessibilityService

/**
 * 闹钟触发入口（对用户不可见）。
 *
 * - PendingIntent.getActivity + setAlarmClock：豁免 Android 15/16 后台启动限制，
 *   可在息屏/锁屏状态下直接启动；
 * - showWhenLocked / turnScreenOn：在锁屏之上显示并点亮屏幕；
 * - 先解锁，再把控制权交给无障碍状态机。
 */
class LauncherActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs(this) }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val service = AutomationAccessibilityService.instance
        if (service == null) {
            LogBus.e("无障碍服务未开启，无法执行任务")
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Unlocker.unlock(service, prefs.unlockPin)

        // 给解锁动画留出时间，再启动任务状态机
        val delay = if (prefs.unlockPin.isBlank()) 800L else 2600L
        handler.postDelayed({
            service.startTask()
            finish()
        }, delay)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
