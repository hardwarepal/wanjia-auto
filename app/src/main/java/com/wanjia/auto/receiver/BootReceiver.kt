package com.wanjia.auto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wanjia.auto.Prefs
import com.wanjia.auto.core.TaskScheduler

/** 开机 / 应用升级后恢复每日定时（重启后 AlarmManager 定时会被清空） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val prefs = Prefs(context)
                if (prefs.enabled) {
                    TaskScheduler.schedule(context, prefs.hour, prefs.minute)
                }
            }
        }
    }
}
