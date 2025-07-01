package com.example.ffxivmtoiletroll
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.ffxivmtoiletroll.databinding.ActivityEditCaptureRuleBinding
import java.util.UUID

class EditCaptureRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditCaptureRuleBinding
    private lateinit var repository: RuleRepository
    private var currentRuleId: String? = null

    // (新增) 资源管理器
    private lateinit var resourceManager: ResourceManager
    // (已修改) 存储资源标识符列表
    private lateinit var matchImageResources: List<ResourceIdentifier>
    private lateinit var imageSpinnerAdapter: ArrayAdapter<String>

    private lateinit var floatingWindowManager: FloatingWindowManager

    // (新增) 文件选择器启动器
    private val importImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = resourceManager.importResource(it, "image")
            if (fileName != null) {
                showToast("图片导入成功: $fileName")
                // 导入成功后刷新 Spinner
                setupImageSpinner()
                // 自动选中刚刚导入的图片
                val newIdentifier = ResourceIdentifier(ResourceType.USER, fileName)
                val position = matchImageResources.indexOf(newIdentifier)
                if (position != -1) {
                    binding.spinnerMatchImage.setSelection(position)
                }
            } else {
                showToast("图片导入失败")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCaptureRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RuleRepository(this)
        resourceManager = ResourceManager(this) // 初始化
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
            // (新增) 如果没有图片资源，禁用保存按钮
            if (matchImageResources.isEmpty()) {
                showToast("警告：没有任何可用的匹配图片！请先导入。")
                binding.btnSave.isEnabled = false
            }
        }
    }

    private fun setupImageSpinner() {
        // (已修改) 从 ResourceManager 获取所有可用图片
        matchImageResources = resourceManager.getAvailableImages("match_")
        // (已修改) 格式化资源名称用于显示
        val displayNames = matchImageResources.map { formatResourceName(it) }

        imageSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        imageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMatchImage.adapter = imageSpinnerAdapter
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener { saveRule() }
        binding.btnPreviewCaptureArea.setOnClickListener { previewCaptureArea() }
        // (新增) 导入按钮的点击事件
        binding.btnImportMatchImage.setOnClickListener {
            importImageLauncher.launch("image/*")
        }
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
        }, 8000)
    }

    private fun loadRuleData() {
        val rule = repository.getCaptureRuleById(currentRuleId!!) ?: return

        binding.editTextRuleName.setText(rule.name)
        binding.editTextX.setText(rule.captureArea.left.toString())
        binding.editTextY.setText(rule.captureArea.top.toString())
        binding.editTextWidth.setText(rule.captureArea.width().toString())
        binding.editTextHeight.setText(rule.captureArea.height().toString())

        // (已修改) 根据 ResourceIdentifier 找到对应的位置
        val position = matchImageResources.indexOf(rule.matchImage)
        if (position != -1) {
            binding.spinnerMatchImage.setSelection(position)
        } else {
            showToast("警告：规则引用的图片资源丢失！")
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

        // (已修改) 获取选中的 ResourceIdentifier
        val selectedImageIdentifier = matchImageResources[binding.spinnerMatchImage.selectedItemPosition]

        val ruleToSave = CaptureRule(
            id = currentRuleId ?: UUID.randomUUID().toString(),
            name = name,
            captureArea = Rect(x, y, x + width, y + height),
            matchImage = selectedImageIdentifier, // 保存 ResourceIdentifier
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

    // (新增) 格式化资源名称，方便在 Spinner 中显示
    private fun formatResourceName(identifier: ResourceIdentifier): String {
        return when (identifier.type) {
            ResourceType.BUILT_IN -> "[内置] ${identifier.path.removePrefix("match_")}"
            ResourceType.USER -> "[用户] ${identifier.path}"
        }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
    }
}
