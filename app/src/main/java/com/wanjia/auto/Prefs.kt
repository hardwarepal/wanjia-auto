package com.wanjia.auto

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/** 全局日志总线：任何模块可发日志，UI 可订阅 */
object LogBus {

    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun i(msg: String) = emit("INFO", msg)
    fun w(msg: String) = emit("WARN", msg)
    fun e(msg: String) = emit("ERROR", msg)

    private fun emit(level: String, msg: String) {
        val line = "${fmt.format(Date())} [$level] $msg"
        listeners.forEach { l -> runCatching { l.invoke(line) } }
    }

    fun add(l: (String) -> Unit) { listeners.add(l) }
    fun remove(l: (String) -> Unit) { listeners.remove(l) }
}

/** 配置项持久化 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("wanjia_prefs", Context.MODE_PRIVATE)

    // ---------- 定时 ----------
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var hour: Int
        get() = sp.getInt("hour", 8)
        set(v) = sp.edit().putInt("hour", v).apply()

    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    // ---------- 目标应用 ----------
    /** 玩家视界是小米钱包内的模块，默认 com.mipay.wallet；独立版按实际包名填 */
    var targetPackage: String
        get() = sp.getString("targetPackage", "com.mipay.wallet")!!
        set(v) = sp.edit().putString("targetPackage", v.trim()).apply()

    // ---------- 按钮文案（逗号分隔，任一命中即点击） ----------
    var entryTexts: List<String>
        get() = list("entryTexts", "做任务,领时长")
        set(v) = putList("entryTexts", v, "做任务,领时长")

    var completeTexts: List<String>
        get() = list("completeTexts", "完成任务")
        set(v) = putList("completeTexts", v, "完成任务")

    var lotteryTexts: List<String>
        get() = list("lotteryTexts", "开奖")
        set(v) = putList("lotteryTexts", v, "开奖")

    // ---------- 时长策略 ----------
    /** OCR 识别到剩余时长后，额外等待的秒数（用户要求的 +5S） */
    var extraWaitSec: Int
        get() = sp.getInt("extraWaitSec", 5)
        set(v) = sp.edit().putInt("extraWaitSec", v.coerceIn(0, 60)).apply()

    /** OCR 监控总时长上限，超时直接返回（兜底） */
    var maxWatchSec: Int
        get() = sp.getInt("maxWatchSec", 240)
        set(v) = sp.edit().putInt("maxWatchSec", v.coerceIn(10, 1800)).apply()

    /** 锁屏 PIN，仅存本机 SharedPreferences */
    var unlockPin: String
        get() = sp.getString("unlockPin", "")!!
        set(v) = sp.edit().putString("unlockPin", v.filter { it.isDigit() }).apply()

    private fun list(key: String, def: String): List<String> =
        sp.getString(key, def)!!
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(def) }

    private fun putList(key: String, v: List<String>, def: String) {
        val s = v.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        sp.edit().putString(key, if (s.isEmpty()) def else s).apply()
    }
}
