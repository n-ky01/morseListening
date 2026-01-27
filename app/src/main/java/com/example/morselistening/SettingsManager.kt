package com.example.morselistening

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

enum class AppMode {
    KOCH, RANDOM, WORD
}

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val KOCH_LEVEL = intPreferencesKey("koch_level")
        val APP_MODE = intPreferencesKey("app_mode")
        val IS_REPEAT_MODE = booleanPreferencesKey("is_repeat_mode")
        val IS_ALPHA_ON = booleanPreferencesKey("is_alpha_on")
        val NUM_CHAR = intPreferencesKey("num_char")
        val ANSWER_DELAY = longPreferencesKey("answer_delay")
        val WPM = intPreferencesKey("wpm")
        val VOLUME_LEVEL = intPreferencesKey("volume_level")
        val SELECTED_WORDS = stringPreferencesKey("selected_words")
        val SPACING_FACTOR = floatPreferencesKey("spacing_factor")
        // --- kochRate 用のキーを追加 ---
        val KOCH_RATE = intPreferencesKey("koch_rate")
        val EBOOST = intPreferencesKey("eboost")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val selectedWordsString = prefs[SELECTED_WORDS] ?: ""
        val selectedWordsList = if (selectedWordsString.isNotEmpty()) {
            selectedWordsString.split(",").toMutableList()
        } else {
            mutableListOf()
        }

        val modeIndex = prefs[APP_MODE] ?: 0
        val mode = AppMode.values().getOrElse(modeIndex) { AppMode.KOCH }

        AppSettings(
            selectedWords = selectedWordsList,
            appMode = mode,
            kochLevel = prefs[KOCH_LEVEL] ?: 2,
            isRepeatMode = prefs[IS_REPEAT_MODE] ?: false,
            isAlphaON = prefs[IS_ALPHA_ON] ?: true,
            numChar = prefs[NUM_CHAR] ?: 1,
            answerDelay = prefs[ANSWER_DELAY] ?: 1000L,
            wpm = prefs[WPM] ?: 20,
            volumeLevel = prefs[VOLUME_LEVEL] ?: 5,
            spacingFactor = prefs[SPACING_FACTOR] ?: 1.0f,
            kochRate = prefs[KOCH_RATE] ?: 20,
            eboost = prefs[EBOOST] ?: 0
        )
    }

    suspend fun <T> saveSetting(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}

data class AppSettings(
    val selectedWords: MutableList<String>,
    val appMode: AppMode,
    val kochLevel: Int,
    val isRepeatMode: Boolean,
    val isAlphaON: Boolean,
    val numChar: Int,
    val answerDelay: Long,
    val wpm: Int,
    val volumeLevel: Int,
    val spacingFactor: Float,
    // --- データクラスにフィールドを追加 ---
    val kochRate: Int,
    val eboost: Int
)