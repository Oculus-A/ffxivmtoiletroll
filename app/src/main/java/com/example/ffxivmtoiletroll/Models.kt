package com.example.ffxivmtoiletroll
import android.graphics.Rect
import java.util.UUID

// (已修改) 移除了 previewCaptureArea 字段
data class CaptureRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var captureArea: Rect,
    var matchImageResId: Int,
    var matchThreshold: Double = 0.9
)

// --- 以下数据类保持不变 ---
data class Condition(
    val captureRuleId: String,
    var isMet: Boolean = true
)
enum class LogicOperator { AND, OR }
data class ShowImageAction(
    val id: String = UUID.randomUUID().toString(),
    val imageResId: Int,
    val positionX: Int,
    val positionY: Int
)
data class PlaySoundAction(
    val id: String = UUID.randomUUID().toString(),
    val soundResId: Int
)
sealed class Action {
    data class ShowImage(val details: ShowImageAction) : Action()
    data class PlaySound(val details: PlaySoundAction) : Action()
}
data class ActionRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var conditions: MutableList<Condition> = mutableListOf(),
    var logicOperator: LogicOperator = LogicOperator.AND,
    var actions: MutableList<Action> = mutableListOf()
)
data class AppConfig(
    val captureRules: List<CaptureRule>,
    val actionRules: List<ActionRule>,
    val detectionInterval: Long
)