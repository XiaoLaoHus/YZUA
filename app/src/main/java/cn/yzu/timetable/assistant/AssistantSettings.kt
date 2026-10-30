package cn.yzu.timetable.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 智能助手的模型接入设置（参照 HITA 的用户自配方案）：任意 OpenAI 兼容接口。
 * 密钥仅保存在应用私有目录，不上传、不备份。
 */
class AssistantSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("yzua_ai_prefs", Context.MODE_PRIVATE)
    var baseUrl by mutableStateOf(prefs.getString(KEY_URL, null) ?: DEFAULT_URL)
        private set
    var apiKey by mutableStateOf(prefs.getString(KEY_KEY, "").orEmpty())
        private set
    var model by mutableStateOf(prefs.getString(KEY_MODEL, "glm-4-flash").orEmpty())
        private set

    val configured get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun update(baseUrl: String, apiKey: String, model: String) {
        this.baseUrl = baseUrl; this.apiKey = apiKey; this.model = model
        prefs.edit().putString(KEY_URL, baseUrl).putString(KEY_KEY, apiKey).putString(KEY_MODEL, model).apply()
    }

    companion object {
        const val DEFAULT_URL = "https://api.deepseek.com"
        const val DOCS_URL = "https://api-docs.deepseek.com/zh-cn/"
        private const val KEY_URL = "ai_base_url"
        private const val KEY_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private val instances = java.util.concurrent.ConcurrentHashMap<Context, AssistantSettings>()
        fun get(context: Context): AssistantSettings = instances.getOrPut(context.applicationContext) { AssistantSettings(context) }
    }
}
