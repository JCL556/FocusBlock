package io.github.xiaoxia.focusblock.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.appDataStore by preferencesDataStore(name = "focusblock")

/**
 * 单文件 JSON 存储。不引 Room，全部数据一次读一次写。
 */
class Store(context: Context) {

    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("app_data")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** 读出来就按时间排好，旧版本存下的乱序数据也能自动归位 */
    private fun decode(raw: String?): AppData =
        raw?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() }?.ordered()
            ?: AppData()

    val data: Flow<AppData> = appContext.appDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[key]) }

    suspend fun current(): AppData = data.first()

    suspend fun update(transform: (AppData) -> AppData) {
        appContext.appDataStore.edit { prefs ->
            val cur = decode(prefs[key])
            // 落盘前再排一次：新增/改动时间后立刻回到正确位置
            prefs[key] = json.encodeToString(AppData.serializer(), transform(cur).ordered())
        }
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun of(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
