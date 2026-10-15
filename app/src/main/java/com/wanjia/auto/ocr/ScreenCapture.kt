package com.wanjia.auto.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import com.wanjia.auto.LogBus

/**
 * 无障碍服务截图（API 30+ / Android 11+ 可用）。
 * 在 accessibility_service_config.xml 中声明 canTakeScreenshot="true"，
 * 用户在系统无障碍设置里开启「屏幕截图」开关后即可使用。
 */
object ScreenCapture {

    fun capture(service: AccessibilityService, cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            LogBus.w("系统版本过低，不支持无障碍截图")
            cb(null)
            return
        }
        runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var soft: Bitmap? = null
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace
                            )
                            soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                        } catch (e: Exception) {
                            LogBus.e("截图转换失败: ${e.message}")
                        }
                        cb(soft)
                    }

                    override fun onFailure(errorCode: Int) {
                        LogBus.w("截图失败 code=$errorCode（请检查无障碍「屏幕截图」权限）")
                        cb(null)
                    }
                }
            )
        }.onFailure {
            LogBus.e("截图调用异常: ${it.message}")
            cb(null)
        }
    }
}
