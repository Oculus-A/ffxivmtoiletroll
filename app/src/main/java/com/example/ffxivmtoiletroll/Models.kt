package com.example.ffxivmtoiletroll
import android.graphics.Rect
import java.util.UUID

data class CaptureRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var captureArea: Rect,
    // (已修改) 使用 ResourceIdentifier 代替 Int
    var matchImage: ResourceIdentifier,
    var matchThreshold: Double = 0.9
)


data class Condition(
    val captureRuleId: String,
    var isMet: Boolean = true
)
enum class LogicOperator { AND, OR }

data class ShowImageAction(
    val id: String = UUID.randomUUID().toString(),
    // (已修改) 使用 ResourceIdentifier 代替 Int
    val image: ResourceIdentifier,
    val positionX: Int,
    val positionY: Int
)
data class PlaySoundAction(
    val id: String = UUID.randomUUID().toString(),
    // (已修改) 使用 ResourceIdentifier 代替 Int
    val sound: ResourceIdentifier
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

enum class ResourceType {
    BUILT_IN, // 内置资源
    USER // 用户导入的资源
}

data class ResourceIdentifier(
    val type: ResourceType,
    val path: String // 对于内置资源，这是资源名；对于用户资源，这是文件名
)