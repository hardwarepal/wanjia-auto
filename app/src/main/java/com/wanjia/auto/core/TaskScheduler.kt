package com.wanjia.auto.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wanjia.auto.LauncherActivity
import com.wanjia.auto.LogBus
import java.util.Calendar

/**
 * 每日定时调度。
 *
 * 关键点（HyperOS 2/3 适配）：
 * 1. 使用 setAlarmClock：免 SCHEDULE_EXACT_ALARM 授权，且系统会展示闹钟图标，
 *    被 Doze / 厂商策略杀死的概率最低。
 * 2. 使用 PendingIntent.getActivity 直接拉起 LauncherActivity：
 *    Android 10+ 后台启动 Activity（BAL）限制下，闹钟触发的 PendingIntent
 *    属于豁免场景，可在息屏/锁屏状态下直接启动 Activity。
 */
object TaskScheduler {

    private const val REQ_CODE = 1001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return PendingIntent.getActivity(
            context, REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 排程下一个 hour:minute；已过时间则排明天 */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, pi), pi)
        LogBus.i("已排程每日定时 %02d:%02d（下次：%tF %tT）".format(hour, minute, cal, cal))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context))
        LogBus.i("已取消每日定时")
    }
}
