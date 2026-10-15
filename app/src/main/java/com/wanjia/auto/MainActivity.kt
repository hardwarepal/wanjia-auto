package com.wanjia.auto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wanjia.auto.core.TaskScheduler
import com.wanjia.auto.databinding.ActivityMainBinding
import com.wanjia.auto.service.AutomationAccessibilityService
import com.wanjia.auto.service.KeepAliveService

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding
    private val prefs by lazy { Prefs(this) }

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread {
            vb.tvLog.append(line + "\n")
            // 限制日志行数，防止无限增长
            val text = vb.tvLog.text
            if (text.length > 20_000) {
                vb.tvLog.text = text.takeLast(8_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        vb.switchSchedule.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (checked) scheduleFromUi() else TaskScheduler.cancel(this)
        }

        vb.btnSave.setOnClickListener {
            savePrefsFromUi()
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        }

        vb.btnRunNow.setOnClickListener {
            savePrefsFromUi()
            startActivity(Intent(this, LauncherActivity::class.java))
        }

        vb.btnKeepAlive.setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
            Toast.makeText(this, "保活服务已启动", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        LogBus.add(logListener)
        loadPrefsToUi()
        val ok = AutomationAccessibilityService.instance != null
        vb.tvServiceStatus.text = if (ok) "状态：已开启 ✔" else "状态：未开启 ✘（点击下方按钮开启）"
        vb.tvServiceStatus.setTextColor(
            if (ok) 0xFF389E0D.toInt() else 0xFFD4380D.toInt()
        )
    }

    override fun onPause() {
        super.onPause()
        LogBus.remove(logListener)
    }

    private fun loadPrefsToUi() {
        vb.switchSchedule.isChecked = prefs.enabled
        vb.timePicker.hour = prefs.hour
        vb.timePicker.minute = prefs.minute
        vb.etPackage.setText(prefs.targetPackage)
        vb.etEntry.setText(prefs.entryTexts.joinToString(","))
        vb.etComplete.setText(prefs.completeTexts.joinToString(","))
        vb.etLottery.setText(prefs.lotteryTexts.joinToString(","))
        vb.etExtraWait.setText(prefs.extraWaitSec.toString())
        vb.etMaxWatch.setText(prefs.maxWatchSec.toString())
        vb.etPin.setText(prefs.unlockPin)
    }

    private fun savePrefsFromUi() {
        prefs.targetPackage = vb.etPackage.text.toString()
        prefs.entryTexts = vb.etEntry.text.toString().split(",")
        prefs.completeTexts = vb.etComplete.text.toString().split(",")
        prefs.lotteryTexts = vb.etLottery.text.toString().split(",")
        prefs.extraWaitSec = vb.etExtraWait.text.toString().toIntOrNull() ?: 5
        prefs.maxWatchSec = vb.etMaxWatch.text.toString().toIntOrNull() ?: 240
        prefs.unlockPin = vb.etPin.text.toString()
    }

    private fun scheduleFromUi() {
        savePrefsFromUi()
        TaskScheduler.schedule(this, prefs.hour, prefs.minute)
    }
}
