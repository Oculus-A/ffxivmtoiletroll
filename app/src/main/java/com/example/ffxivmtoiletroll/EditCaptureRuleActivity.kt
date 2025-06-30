package com.example.ffxivmtoiletroll
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ffxivmtoiletroll.R
import com.example.ffxivmtoiletroll.databinding.ActivityEditCaptureRuleBinding
import java.util.UUID

class EditCaptureRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditCaptureRuleBinding
    private lateinit var repository: RuleRepository
    private var currentRuleId: String? = null
    private lateinit var matchImageResources: List<Pair<String, String>>
    private lateinit var imageSpinnerAdapter: ArrayAdapter<String>

    private lateinit var floatingWindowManager: FloatingWindowManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCaptureRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RuleRepository(this)
        floatingWindowManager = FloatingWindowManager(this)

        currentRuleId = intent.getStringExtra(EXTRA_RULE_ID)

        setupImageSpinner()
        setupClickListeners()

        if (currentRuleId != null) {
            title = "编辑捕获规则"
            loadRuleData()
        } else {
            title = "添加捕获规则"
            binding.editTextThreshold.setText("0.9")
        }
    }

    private fun setupImageSpinner() {
        matchImageResources = getDrawableResourcesByPrefix("match_")
        val displayNames = matchImageResources.map { it.first }
        imageSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        imageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMatchImage.adapter = imageSpinnerAdapter
        if (matchImageResources.isEmpty()) {
            showToast("警告：未找到任何以 'match_' 开头的图片资源！")
            binding.btnSave.isEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener { saveRule() }
        binding.btnPreviewCaptureArea.setOnClickListener { previewCaptureArea() }
    }

    private fun previewCaptureArea() {
        val x = binding.editTextX.text.toString().toIntOrNull()
        val y = binding.editTextY.text.toString().toIntOrNull()
        val width = binding.editTextWidth.text.toString().toIntOrNull()
        val height = binding.editTextHeight.text.toString().toIntOrNull()

        if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0) {
            showToast("请输入有效的预览坐标和尺寸")
            return
        }

        val previewId = "capture_area_preview"
        val area = Rect(x, y, x + width, y + height)
        floatingWindowManager.showPreviewArea(previewId, area)
        Handler(Looper.getMainLooper()).postDelayed({
            floatingWindowManager.removeView(previewId)
        }, 2000)
    }

    private fun loadRuleData() {
        val rule = repository.getCaptureRuleById(currentRuleId!!) ?: return

        binding.editTextRuleName.setText(rule.name)
        binding.editTextX.setText(rule.captureArea.left.toString())
        binding.editTextY.setText(rule.captureArea.top.toString())
        binding.editTextWidth.setText(rule.captureArea.width().toString())
        binding.editTextHeight.setText(rule.captureArea.height().toString())

        val resourceName = getResourceNameById(rule.matchImageResId)
        val position = matchImageResources.indexOfFirst { it.second == resourceName }
        if (position != -1) {
            binding.spinnerMatchImage.setSelection(position)
        }

        binding.editTextThreshold.setText(rule.matchThreshold.toString())
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString().takeIf { it.isNotBlank() } ?: run {
            showToast("规则名称不能为空"); return
        }
        val x = binding.editTextX.text.toString().toIntOrNull()
        val y = binding.editTextY.text.toString().toIntOrNull()
        val width = binding.editTextWidth.text.toString().toIntOrNull()
        val height = binding.editTextHeight.text.toString().toIntOrNull()
        if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0) {
            showToast("捕获区域的数值无效"); return
        }

        val threshold = binding.editTextThreshold.text.toString().toDoubleOrNull()
        if (threshold == null || threshold < 0.0 || threshold > 1.0) {
            showToast("匹配度阈值必须是 0.0 到 1.0 之间的数字"); return
        }
        if (binding.spinnerMatchImage.selectedItemPosition < 0) {
            showToast("请选择一张匹配图片"); return
        }

        val selectedImageResource = matchImageResources[binding.spinnerMatchImage.selectedItemPosition]
        val imageResId = getResourceIdByName(selectedImageResource.second, "drawable")

        val ruleToSave = CaptureRule(
            id = currentRuleId ?: UUID.randomUUID().toString(),
            name = name,
            captureArea = Rect(x, y, x + width, y + height),
            matchImageResId = imageResId,
            matchThreshold = threshold
        )

        repository.saveCaptureRule(ruleToSave)
        showToast("规则已保存")
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindowManager.removeAllViews()
    }

    private fun getDrawableResourcesByPrefix(prefix: String): List<Pair<String, String>> = R.drawable::class.java.fields.map { it.name }.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) to it }
    private fun getResourceIdByName(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)
    private fun getResourceNameById(resId: Int): String = if (resId != 0) resources.getResourceEntryName(resId) else ""
    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
    }
}
