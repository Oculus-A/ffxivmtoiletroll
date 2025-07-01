package com.example.ffxivmtoiletroll
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.ffxivmtoiletroll.databinding.ActivityEditActionRuleBinding

class EditActionRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditActionRuleBinding
    private lateinit var repository: RuleRepository
    private var currentRule: ActionRule? = null

    private lateinit var conditionAdapter: ConditionAdapter
    private lateinit var actionAdapter: ActionAdapter

    private val tempConditions = mutableListOf<Condition>()
    private val tempActions = mutableListOf<Action>()

    // (新增) 资源管理器
    private lateinit var resourceManager: ResourceManager
    private lateinit var soundPlayer: SoundPlayer
    private lateinit var floatingWindowManager: FloatingWindowManager

    // (新增) 用于刷新对话框中 Spinner 的回调
    private var refreshDialogSpinner: (() -> Unit)? = null

    // (新增) 文件选择器启动器
    private val importImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { handleImportResult(it, "image") }
    private val importSoundLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { handleImportResult(it, "sound") }

    private fun handleImportResult(uri: Uri?, type: String) {
        uri?.let {
            val fileName = resourceManager.importResource(it, type)
            if (fileName != null) {
                showToast("资源导入成功: $fileName")
                // 刷新打开的对话框中的 Spinner
                refreshDialogSpinner?.invoke()
            } else {
                showToast("资源导入失败")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditActionRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RuleRepository(this)
        resourceManager = ResourceManager(this)
        soundPlayer = SoundPlayer(this)
        floatingWindowManager = FloatingWindowManager(this)

        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        if (ruleId != null) {
            title = "编辑行动规则"
            currentRule = repository.getActionRuleById(ruleId)
            loadRuleData()
        } else {
            title = "添加行动规则"
            currentRule = ActionRule(name = "")
            binding.radioGroupLogic.check(R.id.radioBtnAnd)
        }

        setupRecyclerViews()
        setupClickListeners()
    }

    private fun loadRuleData() {
        currentRule?.let {
            binding.editTextRuleName.setText(it.name)
            if (it.logicOperator == LogicOperator.AND) {
                binding.radioGroupLogic.check(R.id.radioBtnAnd)
            } else {
                binding.radioGroupLogic.check(R.id.radioBtnOr)
            }
            tempConditions.addAll(it.conditions)
            tempActions.addAll(it.actions)
        }
    }

    private fun setupRecyclerViews() {
        conditionAdapter = ConditionAdapter(tempConditions,
            getCaptureRuleName = { captureId -> repository.getCaptureRuleById(captureId)?.name ?: "未知规则" },
            onItemClick = { condition -> showConditionDialog(condition) },
            onItemLongClick = { condition ->
                showDeleteDialog("删除条件", "确定要删除这个条件吗？") {
                    tempConditions.remove(condition)
                    conditionAdapter.updateData(tempConditions)
                }
            }
        )
        binding.recyclerViewConditions.apply {
            layoutManager = LinearLayoutManager(this@EditActionRuleActivity)
            adapter = conditionAdapter
        }

        actionAdapter = ActionAdapter(tempActions,
            // (已修改) 传递 resourceManager 以便检查资源是否存在
            resourceManager,
            onItemClick = { action ->
                when(action) {
                    is Action.ShowImage -> showImageActionDialog(action)
                    is Action.PlaySound -> showSoundActionDialog(action)
                }
            },
            onItemLongClick = { action ->
                showDeleteDialog("删除动作", "确定要删除这个动作吗？") {
                    tempActions.remove(action)
                    actionAdapter.updateData(tempActions)
                }
            }
        )
        binding.recyclerViewActions.apply {
            layoutManager = LinearLayoutManager(this@EditActionRuleActivity)
            adapter = actionAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnAddCondition.setOnClickListener { showConditionDialog(null) }
        binding.btnAddAction.setOnClickListener { showAddActionTypeDialog() }
        binding.btnSave.setOnClickListener { saveRule() }
    }

    private fun showConditionDialog(conditionToEdit: Condition?) {
        val captureRules = repository.loadCaptureRules()
        if (captureRules.isEmpty()) { showToast("请先创建至少一个捕获规则"); return }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_condition, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCaptureRules)
        val switch = dialogView.findViewById<SwitchMaterial>(R.id.switchInvertCondition)
        val ruleNames = captureRules.map { it.name }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ruleNames)

        val isEditing = conditionToEdit != null
        val dialogTitle = if(isEditing) "编辑条件" else "添加条件"

        if(isEditing) {
            val index = captureRules.indexOfFirst { it.id == conditionToEdit!!.captureRuleId }
            if (index != -1) spinner.setSelection(index)
            switch.isChecked = !conditionToEdit!!.isMet
        }

        AlertDialog.Builder(this).setTitle(dialogTitle).setView(dialogView)
            .setPositiveButton(if(isEditing) "保存" else "添加") { _, _ ->
                if (spinner.selectedItemPosition < 0) return@setPositiveButton
                val selectedRule = captureRules[spinner.selectedItemPosition]
                val newCondition = Condition(captureRuleId = selectedRule.id, isMet = !switch.isChecked)
                if(isEditing) {
                    val index = tempConditions.indexOf(conditionToEdit)
                    if(index != -1) tempConditions[index] = newCondition
                } else {
                    tempConditions.add(newCondition)
                }
                conditionAdapter.updateData(tempConditions)
            }
            .setNegativeButton("取消", null).show()
    }

    private fun showAddActionTypeDialog() {
        val actionTypes = arrayOf("显示图片", "播放音效")
        AlertDialog.Builder(this).setTitle("选择动作类型")
            .setItems(actionTypes) { _, which ->
                if (which == 0) showImageActionDialog(null) else showSoundActionDialog(null)
            }
            .show()
    }

    private fun showImageActionDialog(actionToEdit: Action.ShowImage?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_show_image_action, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerDisplayImages)
        val posX = dialogView.findViewById<EditText>(R.id.editTextPositionX)
        val posY = dialogView.findViewById<EditText>(R.id.editTextPositionY)
        val previewBtn = dialogView.findViewById<Button>(R.id.btnPreviewImage)
        val importBtn = dialogView.findViewById<Button>(R.id.btnImportDisplayImage) // (新增)

        var availableImages: List<ResourceIdentifier> = emptyList()

        val setupSpinner = {
            availableImages = resourceManager.getAvailableImages("display_")
            val displayNames = availableImages.map { formatResourceName(it, "display_") }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)

            if (actionToEdit != null) {
                val index = availableImages.indexOf(actionToEdit.details.image)
                if (index != -1) spinner.setSelection(index)
            }
        }

        setupSpinner()
        refreshDialogSpinner = {
            // 重新设置 Spinner 内容并恢复之前的选择
            val selectedItem = spinner.selectedItemPosition
            setupSpinner()
            spinner.setSelection(selectedItem.coerceAtMost(spinner.adapter.count -1))
        }

        importBtn.setOnClickListener { importImageLauncher.launch("image/*") }

        val isEditing = actionToEdit != null
        val dialogTitle = if (isEditing) "编辑显示图片动作" else "配置显示图片动作"

        if (isEditing) {
            val details = actionToEdit!!.details
            posX.setText(details.positionX.toString())
            posY.setText(details.positionY.toString())
        }

        previewBtn.setOnClickListener {
            val x = posX.text.toString().toIntOrNull()
            val y = posY.text.toString().toIntOrNull()
            if (x == null || y == null) { showToast("请输入有效的预览坐标"); return@setOnClickListener }
            if (spinner.selectedItemPosition < 0) return@setOnClickListener

            val selectedIdentifier = availableImages[spinner.selectedItemPosition]
            val previewId = "action_preview_image"
            floatingWindowManager.showImage(previewId, selectedIdentifier, x, y)
            Handler(Looper.getMainLooper()).postDelayed({ floatingWindowManager.removeView(previewId) }, 8000)
        }

        AlertDialog.Builder(this).setTitle(dialogTitle).setView(dialogView)
            .setPositiveButton(if (isEditing) "保存" else "添加") { _, _ ->
                if (spinner.selectedItemPosition < 0) return@setPositiveButton
                val x = posX.text.toString().toIntOrNull() ?: 0
                val y = posY.text.toString().toIntOrNull() ?: 0
                val selectedIdentifier = availableImages[spinner.selectedItemPosition]

                if (isEditing) {
                    val index = tempActions.indexOf(actionToEdit)
                    val newDetails = actionToEdit!!.details.copy(image = selectedIdentifier, positionX = x, positionY = y)
                    if(index != -1) tempActions[index] = Action.ShowImage(newDetails)
                } else {
                    val newAction = Action.ShowImage(ShowImageAction(image = selectedIdentifier, positionX = x, positionY = y))
                    tempActions.add(newAction)
                }
                actionAdapter.updateData(tempActions)
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener { refreshDialogSpinner = null } // (新增) 对话框关闭时清除回调
            .show()
    }

    private fun showSoundActionDialog(actionToEdit: Action.PlaySound?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_play_sound_action, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerSoundFiles)
        val previewBtn = dialogView.findViewById<Button>(R.id.btnPreviewSound)
        val importBtn = dialogView.findViewById<Button>(R.id.btnImportSound) // (新增)

        var availableSounds: List<ResourceIdentifier> = emptyList()

        val setupSpinner = {
            availableSounds = resourceManager.getAvailableSounds()
            val displayNames = availableSounds.map { formatResourceName(it, "") }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)

            if (actionToEdit != null) {
                val index = availableSounds.indexOf(actionToEdit.details.sound)
                if (index != -1) spinner.setSelection(index)
            }
        }

        setupSpinner()
        refreshDialogSpinner = {
            val selectedItem = spinner.selectedItemPosition
            setupSpinner()
            spinner.setSelection(selectedItem.coerceAtMost(spinner.adapter.count -1))
        }

        importBtn.setOnClickListener { importSoundLauncher.launch("audio/*") }

        val isEditing = actionToEdit != null
        val dialogTitle = if (isEditing) "编辑播放音效动作" else "配置播放音效动作"

        previewBtn.setOnClickListener {
            if (spinner.selectedItemPosition < 0) return@setOnClickListener
            val selectedIdentifier = availableSounds[spinner.selectedItemPosition]
            soundPlayer.playSound(selectedIdentifier)
        }

        AlertDialog.Builder(this).setTitle(dialogTitle).setView(dialogView)
            .setPositiveButton(if(isEditing) "保存" else "添加") { _, _ ->
                if (spinner.selectedItemPosition < 0) return@setPositiveButton
                val selectedIdentifier = availableSounds[spinner.selectedItemPosition]

                if(isEditing) {
                    val index = tempActions.indexOf(actionToEdit)
                    val newDetails = actionToEdit!!.details.copy(sound = selectedIdentifier)
                    if(index != -1) tempActions[index] = Action.PlaySound(newDetails)
                } else {
                    val newAction = Action.PlaySound(PlaySoundAction(sound = selectedIdentifier))
                    tempActions.add(newAction)
                }
                actionAdapter.updateData(tempActions)
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener { refreshDialogSpinner = null } // (新增)
            .show()
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString()
        if (name.isBlank()) { showToast("行动规则名称不能为空"); return }

        val finalRule = currentRule!!.copy(
            name = name,
            logicOperator = if(binding.radioGroupLogic.checkedRadioButtonId == R.id.radioBtnAnd) LogicOperator.AND else LogicOperator.OR,
            conditions = tempConditions,
            actions = tempActions
        )
        repository.saveActionRule(finalRule)
        showToast("行动规则已保存")
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
        floatingWindowManager.removeAllViews()
    }

    // (新增) 统一的资源名称格式化方法
    private fun formatResourceName(identifier: ResourceIdentifier, prefix: String): String {
        val typeStr = when (identifier.type) {
            ResourceType.BUILT_IN -> "[内置]"
            ResourceType.USER -> "[用户]"
        }
        val name = if (prefix.isNotEmpty()) identifier.path.removePrefix(prefix) else identifier.path
        return "$typeStr $name"
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun showDeleteDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("删除") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null).show()
    }

    companion object {
        const val EXTRA_RULE_ID = "extra_action_rule_id"
    }
}
