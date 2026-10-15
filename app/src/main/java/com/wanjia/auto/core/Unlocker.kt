package com.wanjia.auto.core

import android.app.KeyguardManager
import com.wanjia.auto.LogBus
import com.wanjia.auto.service.AutomationAccessibilityService

/**
 * 锁屏解锁。
 *
 * 实现说明（重要）：
 * Android 不允许第三方应用「直接」清除 PIN 锁屏。本实现走两条路：
 *  1. 无密码 / 滑动锁：上滑手势即可解锁；
 *  2. PIN 锁：上滑唤出数字键盘后，优先通过无障碍节点点击数字键
 *     （HyperOS 锁闸键盘一般可被无障碍读取），失败时降级为坐标点击。
 * 坐标兜底在不同机型/键盘布局上可能有偏移，属正常现象；
 * 追求稳定建议在「设置 → 密码与安全 → 扩展解锁」中配置受信任场景，
 * 让闹钟触发时锁屏已自动解除。
 */
object Unlocker {

    fun unlock(service: AutomationAccessibilityService, pin: String) {
        ScreenWake.acquire(service)

        val km = service.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceLocked) {
            LogBus.i("设备未处于锁屏状态，无需解锁")
            return
        }

        LogBus.i("检测到锁屏，执行上滑解锁")
        service.swipeUp()

        if (pin.isBlank()) {
            LogBus.w("未配置 PIN：仅上滑（对无密码/滑动锁有效）")
            return
        }

        service.handler.postDelayed({ enterPin(service, pin) }, 900)
    }

    private fun enterPin(service: AutomationAccessibilityService, pin: String) {
        LogBus.i("正在输入解锁 PIN（节点方式）")
        pin.forEach { c ->
            if (!service.clickNodeByExactText(c.toString())) {
                LogBus.w("未在锁屏界面找到按键「$c」")
            }
            Thread.sleep(180)
        }

        // 等待键盘响应后校验，仍未解锁则坐标兜底
        service.handler.postDelayed({
            val km = service.getSystemService(KeyguardManager::class.java)
            if (km.isDeviceLocked) {
                LogBus.w("节点输入后仍处于锁屏，尝试坐标兜底")
                KeypadTap.tapPin(service, pin)
            } else {
                LogBus.i("解锁成功")
            }
        }, 1800)
    }
}

/**
 * PIN 键盘坐标兜底：按屏幕比例估算 3×4 数字键盘位置。
 * 各机型键盘区域有差异，命中率取决于机型，仅作兜底。
 */
object KeypadTap {

    fun tapPin(service: AutomationAccessibilityService, pin: String) {
        val m = service.resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()

        // 常见安全键盘布局：屏幕下方约 56%~92% 区域，3 列 × 4 行
        val keyW = w * 0.24f
        val keyH = h * 0.09f
        val left = (w - keyW * 3) / 2f
        val top = h * 0.56f

        pin.forEach { c ->
            val idx = when (c) {
                '0' -> 10
                '1' -> 0; '2' -> 1; '3' -> 2
                '4' -> 3; '5' -> 4; '6' -> 5
                '7' -> 6; '8' -> 7; '9' -> 8
                else -> -1
            }
            if (idx < 0) return@forEach
            val col = idx % 3
            val row = idx / 3
            service.tap(
                left + keyW * (col + 0.5f),
                top + keyH * (row + 0.5f)
            )
            Thread.sleep(220)
        }
        LogBus.i("坐标输入 PIN 完成")
    }
}
