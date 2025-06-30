package com.example.ffxivmtoiletroll
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class ActionTypeAdapter : com.google.gson.JsonSerializer<Action>, com.google.gson.JsonDeserializer<Action> {
    companion object {
        const val TYPE = "type"
        const val DATA = "data"
    }
    override fun serialize(src: Action, typeOfSrc: Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val jsonObject = com.google.gson.JsonObject()
        jsonObject.addProperty(TYPE, src.javaClass.name)
        jsonObject.add(DATA, context.serialize(src))
        return jsonObject
    }
    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: Type, context: com.google.gson.JsonDeserializationContext): Action {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get(TYPE).asString
        val data = jsonObject.get(DATA)
        val clazz = Class.forName(type)
        return context.deserialize(data, clazz)
    }
}

class RuleRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("RulePrefs", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Action::class.java, ActionTypeAdapter())
        .create()

    private val CAPTURE_RULES_KEY = "capture_rules"
    private val ACTION_RULES_KEY = "action_rules"
    private val DETECTION_INTERVAL_KEY = "detection_interval"

    // --- 配置打包与解析 ---

    fun getFullConfig(): AppConfig {
        return AppConfig(
            captureRules = loadCaptureRules(),
            actionRules = loadActionRules(),
            detectionInterval = loadDetectionInterval()
        )
    }

    fun configToJson(config: AppConfig): String {
        return gson.toJson(config)
    }

    // (新增)
    fun jsonToConfig(jsonString: String): AppConfig? {
        return try {
            gson.fromJson(jsonString, AppConfig::class.java)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            null
        }
    }

    // (新增)
    fun applyFullConfig(config: AppConfig) {
        saveCaptureRules(config.captureRules)
        saveActionRules(config.actionRules)
        saveDetectionInterval(config.detectionInterval)
    }

    // --- 捕获规则 ---
    fun loadCaptureRules(): MutableList<CaptureRule> {
        val json = sharedPreferences.getString(CAPTURE_RULES_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<CaptureRule>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }
    fun saveCaptureRules(rules: List<CaptureRule>) {
        val json = gson.toJson(rules)
        sharedPreferences.edit().putString(CAPTURE_RULES_KEY, json).apply()
    }
    fun getCaptureRuleById(id: String): CaptureRule? = loadCaptureRules().find { it.id == id }
    fun saveCaptureRule(rule: CaptureRule) {
        val rules = loadCaptureRules()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) rules[index] = rule else rules.add(rule)
        saveCaptureRules(rules)
    }
    fun deleteCaptureRule(id: String) {
        val rules = loadCaptureRules()
        rules.removeAll { it.id == id }
        saveCaptureRules(rules)
    }

    // --- 行动规则 ---
    fun loadActionRules(): MutableList<ActionRule> {
        val json = sharedPreferences.getString(ACTION_RULES_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<ActionRule>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }
    fun saveActionRules(rules: List<ActionRule>) {
        val json = gson.toJson(rules)
        sharedPreferences.edit().putString(ACTION_RULES_KEY, json).apply()
    }
    fun getActionRuleById(id: String): ActionRule? = loadActionRules().find { it.id == id }
    fun saveActionRule(rule: ActionRule) {
        val rules = loadActionRules()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) rules[index] = rule else rules.add(rule)
        saveActionRules(rules)
    }
    fun deleteActionRule(id: String) {
        val rules = loadActionRules()
        rules.removeAll { it.id == id }
        saveActionRules(rules)
    }

    // --- 全局设置 ---
    fun saveDetectionInterval(interval: Long) {
        sharedPreferences.edit().putLong(DETECTION_INTERVAL_KEY, interval).apply()
    }
    fun loadDetectionInterval(): Long {
        return sharedPreferences.getLong(DETECTION_INTERVAL_KEY, 1000L)
    }
}