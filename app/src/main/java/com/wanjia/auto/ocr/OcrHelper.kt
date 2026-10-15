package com.wanjia.auto.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/** 一个 OCR 文本块（含屏幕坐标，可用于点击） */
class OcrBlock(val text: String, val rect: Rect?)

/**
 * ML Kit 中文 OCR 封装。
 * 使用 text-recognition-chinese，模型打包在 APK 内，无需 Google Play 服务。
 */
class OcrHelper {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /** 识别整屏文本 */
    fun text(bitmap: Bitmap, cb: (String) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cb(it.text) }
            .addOnFailureListener { cb("") }
    }

    /** 识别整屏文本块（带位置） */
    fun blocks(bitmap: Bitmap, cb: (List<OcrBlock>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { t ->
                cb(t.textBlocks.map { OcrBlock(it.text, it.boundingBox) })
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}

/**
 * 从 OCR 文本中解析倒计时秒数。
 * 覆盖常见格式：「剩余 25 秒」「1分25秒」「04:23」「剩余 120」。
 * 解析失败返回 null。
 */
object TimeParser {

    private val PATTERNS: List<Pair<Regex, (MatchResult) -> Int?>> = listOf(
        // 1分25秒 / 1分
        Regex("(\\d{1,2})\\s*分\\s*(\\d{1,2})\\s*秒") to { m ->
            m.gv(1) * 60 + m.gv(2)
        },
        Regex("(\\d{1,3})\\s*分") to { m -> m.gv(1) * 60 },
        // 04:23 / 4:23
        Regex("(\\d{1,2}):(\\d{2})") to { m -> m.gv(1) * 60 + m.gv(2) },
        // 25秒 / 25 秒
        Regex("(\\d{1,3})\\s*秒") to { m -> m.gv(1) },
        // 剩余120 / 剩余 120
        Regex("剩余\\s*(\\d{1,3})") to { m -> m.gv(1) },
    )

    fun parseSeconds(raw: String): Int? {
        val t = raw.replace(" ", "").replace("：", ":")
        for ((re, fn) in PATTERNS) {
            re.find(t)?.let { m ->
                val v = fn(m)
                if (v != null && v in 1..3600) return v
            }
        }
        return null
    }

    private fun MatchResult.gv(i: Int): Int = groupValues[i].toIntOrNull() ?: 0
}
