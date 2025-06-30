package com.example.ffxivmtoiletroll
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ffxivmtoiletroll.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RuleRepository
    private lateinit var captureRuleAdapter: RuleAdapter<CaptureRule>
    private lateinit var actionRuleAdapter: RuleAdapter<ActionRule>

    // --- ActivityResultLaunchers ---

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri?.let { exportConfigToFile(it) } ?: showToast("未选择文件，导出已取消")
        }

    // (新增) 文件选择(导入)的启动器
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { importConfigFromFile(it) } ?: showToast("未选择文件，导入已取消")
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showToast("存储权限已授予，请再次点击导入按钮")
            } else {
                showToast("未授予存储权限，导入功能无法使用")
            }
        }

    private val editActionRuleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) loadAndDisplayActionRules() }

    private val editCaptureRuleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) loadAndDisplayCaptureRules() }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val interval = repository.loadDetectionInterval()
            val serviceIntent = Intent(this, DetectionService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                putExtra("detectionInterval", interval)
            }
            startForegroundService(serviceIntent)
            showToast("服务已启动")
        } else {
            showToast("未授予屏幕捕获权限，服务无法启动")
        }
    }
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasOverlayPermission()) requestMediaProjection() else showToast("未授予悬浮窗权限，部分功能可能无法使用")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RuleRepository(this)
        setupRecyclerViews()
        setupButtonClickListeners()
        refreshAllData()
    }

    private fun setupButtonClickListeners() {
        binding.btnAddCaptureRule.setOnClickListener {
            editCaptureRuleLauncher.launch(Intent(this, EditCaptureRuleActivity::class.java))
        }
        binding.btnAddActionRule.setOnClickListener {
            editActionRuleLauncher.launch(Intent(this, EditActionRuleActivity::class.java))
        }
        binding.btnStartService.setOnClickListener {
            val interval = binding.editTextInterval.text.toString().toLongOrNull()
            if (interval == null || interval <= 0) {
                showToast("检测间隔必须是大于0的数字"); return@setOnClickListener
            }
            repository.saveDetectionInterval(interval)
            startDetectionService()
        }
        binding.btnStopService.setOnClickListener {
            stopService(Intent(this, DetectionService::class.java))
            showToast("服务已停止")
        }

        binding.btnExportConfig.setOnClickListener {
            val defaultConfigName = "config_${System.currentTimeMillis()}.json"
            createDocumentLauncher.launch(defaultConfigName)
        }

        binding.btnImportConfig.setOnClickListener {
            checkStoragePermissionAndExecute {
                openDocumentLauncher.launch("application/json")
            }
        }
    }

    private fun exportConfigToFile(uri: Uri) {
        try {
            val config = repository.getFullConfig()
            val jsonString = repository.configToJson(config)
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
                showToast("配置已成功导出！")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("导出失败: ${e.message}")
        }
    }

    // (新增)
    private fun importConfigFromFile(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: run {
                showToast("无法读取文件内容"); return
            }

            val config = repository.jsonToConfig(jsonString)
            if (config == null) {
                showToast("配置文件格式错误或已损坏"); return
            }

            showImportConfirmationDialog(config)

        } catch (e: Exception) {
            e.printStackTrace()
            showToast("导入失败: ${e.message}")
        }
    }

    // (新增)
    private fun showImportConfirmationDialog(config: AppConfig) {
        AlertDialog.Builder(this)
            .setTitle("确认导入")
            .setMessage("这将覆盖您当前所有的规则和设置，此操作无法撤销。您确定要继续吗？")
            .setPositiveButton("确定导入") { _, _ ->
                repository.applyFullConfig(config)
                refreshAllData()
                showToast("配置已成功导入！")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshAllData() {
        loadAndDisplayCaptureRules()
        loadAndDisplayActionRules()
        binding.editTextInterval.setText(repository.loadDetectionInterval().toString())
    }

    private fun loadAndDisplayCaptureRules() {
        captureRuleAdapter.updateData(repository.loadCaptureRules())
    }

    private fun loadAndDisplayActionRules() {
        actionRuleAdapter.updateData(repository.loadActionRules())
    }

    private fun setupRecyclerViews() {
        captureRuleAdapter = RuleAdapter(emptyList(), { it.name },
            onItemClick = { rule ->
                val intent = Intent(this, EditCaptureRuleActivity::class.java).apply {
                    putExtra(EditCaptureRuleActivity.EXTRA_RULE_ID, rule.id)
                }
                editCaptureRuleLauncher.launch(intent)
            },
            onItemLongClick = { rule -> showDeleteCaptureRuleDialog(rule) }
        )
        binding.recyclerViewCaptureRules.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = captureRuleAdapter
        }
        actionRuleAdapter = RuleAdapter(emptyList(), { it.name },
            onItemClick = { rule ->
                val intent = Intent(this, EditActionRuleActivity::class.java).apply {
                    putExtra(EditActionRuleActivity.EXTRA_RULE_ID, rule.id)
                }
                editActionRuleLauncher.launch(intent)
            },
            onItemLongClick = { rule -> showDeleteActionRuleDialog(rule) }
        )
        binding.recyclerViewActionRules.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = actionRuleAdapter
        }
    }

    private fun checkStoragePermissionAndExecute(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                    action()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showToast("需要存储权限来导入/导出配置文件")
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        } else {
            action()
        }
    }

    private fun startDetectionService() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission(); return
        }
        requestMediaProjection()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun showDeleteCaptureRuleDialog(rule: CaptureRule) {
        AlertDialog.Builder(this).setTitle("删除捕获规则").setMessage("您确定要删除规则 “${rule.name}” 吗？")
            .setPositiveButton("删除") { _, _ ->
                repository.deleteCaptureRule(rule.id)
                loadAndDisplayCaptureRules()
                showToast("规则 “${rule.name}” 已删除")
            }.setNegativeButton("取消", null).show()
    }

    private fun showDeleteActionRuleDialog(rule: ActionRule) {
        AlertDialog.Builder(this).setTitle("删除行动规则").setMessage("您确定要删除规则 “${rule.name}” 吗？")
            .setPositiveButton("删除") { _, _ ->
                repository.deleteActionRule(rule.id)
                loadAndDisplayActionRules()
                showToast("规则 “${rule.name}” 已删除")
            }.setNegativeButton("取消", null).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
