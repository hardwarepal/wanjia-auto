package com.wanjia.auto.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wanjia.auto.LogBus
import com.wanjia.auto.Prefs
import com.wanjia.auto.core.ScreenWake
import com.wanjia.auto.core.TaskScheduler
import com.wanjia.auto.ocr.OcrHelper
import com.wanjia.auto.ocr.ScreenCapture
import com.wanjia.auto.ocr.TimeParser

/**
 * 玩家视界自动任务核心服务。
 *
 * 流程状态机：
 *   LAUNCH → WAIT_APP → CLICK_ENTRY(做任务 领时长) → CLICK_COMPLETE(完成任务)
 *   → WATCH(OCR 每秒识别屏幕，解析剩余时长) → RETURN(回到小米钱包)
 *   → WAIT_APP2 → CLICK_LOTTERY(开奖) → 完成并排程次日
 *
 * 点击策略：先走无障碍节点（快、准）；找不到时降级为
 * 截图 + ML Kit 中文 OCR 定位文字坐标，再用手势点击。
 */
class AutomationAccessibilityService : AccessibilityService() {

    enum class Step { IDLE, LAUNCH, WAIT_APP, CLICK_ENTRY, CLICK_COMPLETE, WATCH, RETURN, WAIT_APP2, CLICK_LOTTERY }

    @Volatile
    var step: Step = Step.IDLE
        private set

    internal val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(applicationContext) }
    private var ocr: OcrHelper? = null

    @Volatile
    private var ocrBusy = false
    private var stepAt = 0L
    private var watchAt = 0L
    private var leaveAt = 0L      // 预计可以返回玩家视界的时间戳
    private var lastOcrAt = 0L

    companion object {
        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set
        private const val POLL_MS = 400L
        private const val OCR_INTERVAL_MS = 1000L
    }

    // ---------------- 生命周期 ----------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ocr = OcrHelper()
        LogBus.i("无障碍服务已连接，任务状态机就绪")
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 主驱动为轮询；事件仅用于日志观察，避免重复触发
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(poll)
        ocr?.close()
        ocr = null
        step = Step.IDLE
        return super.onUnbind(intent)
    }

    private val poll = object : Runnable {
        override fun run() {
            runCatching { runStep() }
                .onFailure { LogBus.e("状态机异常: ${it.message}") }
            handler.postDelayed(this, POLL_MS)
        }
    }

    // ---------------- 对外入口 ----------------

    fun startTask() {
        if (step != Step.IDLE) {
            LogBus.w("任务已在进行中（${step.name}），忽略重复启动")
            return
        }
        LogBus.i("===== 开始执行自动任务 =====")
        step = Step.LAUNCH
        stepAt = now()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    // ---------------- 状态机 ----------------

    private fun runStep() {
        when (step) {
            Step.LAUNCH -> {
                LogBus.i("步骤 1/6：启动应用 ${prefs.targetPackage}")
                launchApp(prefs.targetPackage)
                step = Step.WAIT_APP
                stepAt = now()
            }

            Step.WAIT_APP -> {
                if (currentPackage() == prefs.targetPackage) {
                    LogBus.i("应用已打开，查找「${prefs.entryTexts}」入口")
                    step = Step.CLICK_ENTRY
                    stepAt = now()
                } else if (now() - stepAt > 20_000) {
                    fail("等待目标应用打开超时")
                }
            }

            Step.CLICK_ENTRY -> {
                if (ocrBusy) return
                attemptClick(prefs.entryTexts) { ok ->
                    when {
                        ok -> {
                            LogBus.i("已点击入口按钮「${prefs.entryTexts}」")
                            step = Step.CLICK_COMPLETE
                            stepAt = now()
                        }
                        now() - stepAt > 30_000 -> fail("未找到入口按钮 ${prefs.entryTexts}")
                    }
                }
            }

            Step.CLICK_COMPLETE -> {
                if (ocrBusy) return
                attemptClick(prefs.completeTexts) { ok ->
                    when {
                        ok -> {
                            LogBus.i("已点击「${prefs.completeTexts}」，进入 OCR 监控")
                            watchAt = now()
                            leaveAt = 0
                            lastOcrAt = 0
                            step = Step.WATCH
                        }
                        now() - stepAt > 30_000 -> fail("未找到「${prefs.completeTexts}」按钮")
                    }
                }
            }

            Step.WATCH -> {
                // 每秒截图 + OCR 一次，解析剩余时长
                if (!ocrBusy && now() - lastOcrAt >= OCR_INTERVAL_MS) {
                    lastOcrAt = now()
                    ocrBusy = true
                    ScreenCapture.capture(this) { bmp ->
                        if (bmp == null) {
                            ocrBusy = false
                            return@capture
                        }
                        val o = ocr ?: run {
                            bmp.recycle(); ocrBusy = false; return@capture
                        }
                        o.text(bmp) { txt ->
                            bmp.recycle()
                            ocrBusy = false
                            val secs = TimeParser.parseSeconds(txt)
                            if (secs != null) {
                                val leave = now() + secs * 1000L + prefs.extraWaitSec * 1000L
                                if (leave > leaveAt) {
                                    leaveAt = leave
                                    LogBus.i(
                                        "OCR 识别剩余 ${secs}s → 等待 ${secs + prefs.extraWaitSec}s " +
                                            "后返回（识别文本: ${txt.take(40).replace("\n", " ")}）"
                                    )
                                }
                            }
                        }
                    }
                }
                when {
                    leaveAt > 0 && now() >= leaveAt -> {
                        LogBus.i("倒计时 + ${prefs.extraWaitSec}s 已到，返回玩家视界")
                        step = Step.RETURN
                        stepAt = now()
                    }
                    now() - watchAt > prefs.maxWatchSec * 1000L -> {
                        LogBus.w("OCR 监控超时（${prefs.maxWatchSec}s），直接返回")
                        step = Step.RETURN
                        stepAt = now()
                    }
                }
            }

            Step.RETURN -> {
                LogBus.i("步骤 5/6：返回 ${prefs.targetPackage}")
                launchApp(prefs.targetPackage)
                step = Step.WAIT_APP2
                stepAt = now()
            }

            Step.WAIT_APP2 -> {
                if (currentPackage() == prefs.targetPackage) {
                    LogBus.i("已回到玩家视界，查找「${prefs.lotteryTexts}」按钮")
                    step = Step.CLICK_LOTTERY
                    stepAt = now()
                } else if (now() - stepAt > 20_000) {
                    fail("返回目标应用超时")
                }
            }

            Step.CLICK_LOTTERY -> {
                if (ocrBusy) return
                attemptClick(prefs.lotteryTexts) { ok ->
                    when {
                        ok -> {
                            LogBus.i("已点击「${prefs.lotteryTexts.first()}」，任务完成")
                            finishTask()
                        }
                        now() - stepAt > 30_000 -> fail("未找到「${prefs.lotteryTexts}」按钮")
                    }
                }
            }

            Step.IDLE -> {}
        }
    }

    private fun finishTask() {
        step = Step.IDLE
        ScreenWake.release()
        if (prefs.enabled) {
            TaskScheduler.schedule(this, prefs.hour, prefs.minute)
        } else {
            LogBus.i("定时未开启，不排程次日任务")
        }
        LogBus.i("===== 任务完成 =====")
    }

    private fun fail(msg: String) {
        LogBus.e("任务失败：$msg")
        step = Step.IDLE
        ScreenWake.release()
    }

    // ---------------- 点击：节点优先，OCR 兜底 ----------------

    private fun attemptClick(texts: List<String>, onResult: (Boolean) -> Unit) {
        if (clickByNodes(texts)) {
            onResult(true)
            return
        }
        if (ocrBusy) {
            onResult(false)
            return
        }
        ocrBusy = true
        ScreenCapture.capture(this) { bmp ->
            if (bmp == null) {
                ocrBusy = false
                onResult(false)
                return@capture
            }
            val o = ocr ?: run {
                bmp.recycle(); ocrBusy = false; onResult(false); return@capture
            }
            o.blocks(bmp) { list ->
                bmp.recycle()
                ocrBusy = false
                for (block in list) {
                    if (texts.any { block.text.contains(it, ignoreCase = true) }) {
                        val r: Rect? = block.rect
                        if (r != null) {
                            LogBus.i("OCR 定位按钮「${block.text.take(20)}」并点击")
                            tap(r.exactCenterX(), r.exactCenterY())
                            onResult(true)
                            return@blocks
                        }
                    }
                }
                onResult(false)
            }
        }
    }

    private fun clickByNodes(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        // 1) 系统 API 按文本查找（最快）
        for (t in texts) {
            val list = root.findAccessibilityNodeInfosByText(t) ?: continue
            for (n in list) {
                if (n.clickWithParents()) return true
            }
        }
        // 2) 全节点遍历，匹配 text / contentDescription（覆盖只设 desc 的按钮）
        val all = collectNodes(root)
        for (t in texts) {
            for (n in all) {
                val txt = n.text?.toString() ?: ""
                val desc = n.contentDescription?.toString() ?: ""
                if (txt.contains(t) || desc.contains(t)) {
                    if (n.clickWithParents()) return true
                }
            }
        }
        return false
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) {
                runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
            }
        }
        return out
    }

    private fun AccessibilityNodeInfo.clickWithParents(): Boolean {
        if (isClickable && performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var p = parent
        while (p != null) {
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            p = p.parent
        }
        return false
    }

    /** 解锁 PIN 键盘用：精确匹配单个字符的按键节点 */
    fun clickNodeByExactText(s: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in collectNodes(root)) {
            val txt = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            if (txt == s || desc == s) {
                if (n.clickWithParents()) return true
            }
        }
        return false
    }

    // ---------------- 手势 ----------------

    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipeUp() {
        val m = resources.displayMetrics
        val path = Path().apply {
            moveTo(m.widthPixels / 2f, m.heightPixels * 0.82f)
            lineTo(m.widthPixels / 2f, m.heightPixels * 0.30f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---------------- 工具 ----------------

    private fun now() = System.currentTimeMillis()

    private fun currentPackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            fail("未安装应用: $pkg（请在配置中修改包名）")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
            .onFailure { LogBus.e("启动 $pkg 失败: ${it.message}") }
    }
}
