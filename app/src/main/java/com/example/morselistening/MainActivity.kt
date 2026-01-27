package com.example.morselistening

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.morselistening.R
import java.util.*
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var soundPool: SoundPool
    private var beepSoundId: Int = 0
    private var silenceSoundId: Int = 0
    private val voiceSoundMap = mutableMapOf<String, Int>()
    private val handler = Handler(Looper.getMainLooper())

    // --- 設定値 ---
    private var wpm: Int = 20 // 10〜25
    private var volumeLevel: Int = 5 // 0〜10
    private var volume: Float = 1.0f
    private var period: Long = 1200L / wpm
    private var isPlayingLoop = false
    private var answerDelay: Long = 1000L
    private var numChar: Int = 1 // 一度に再生する文字数

    // 例：文字間を少し広めに設定（1.0が標準、1.5〜2.0が初心者向け）
    private var spacingFactor: Float = 1.0f // 1.0 〜 2.0
    private lateinit var spacingValueText: TextView

    private val lcwoList = listOf(
        "K", "M", "U", "R", "E", "S", "N", "A", "P", "T",
        "L", "W", "I", ".", "J", "Z", "=", "F", "O", "Y",
        ",", "V", "G", "5", "/", "Q", "9", "2", "H", "3",
        "8", "B", "?", "4", "7", "C", "1", "D", "6", "0", "X"
    )
    private val morseMap = mapOf(
        "A" to ".-", "B" to "-...", "C" to "-.-.", "D" to "-..", "E" to ".",
        "F" to "..-.", "G" to "--.", "H" to "....", "I" to "..", "J" to ".---",
        "K" to "-.-", "L" to ".-..", "M" to "--", "N" to "-.", "O" to "---",
        "P" to ".--.", "Q" to "--.-", "R" to ".-.", "S" to "...", "T" to "-",
        "U" to "..-", "V" to "...-", "W" to ".--", "X" to "-..-", "Y" to "-.--", "Z" to "--..",
        "0" to "-----", "1" to ".----", "2" to "..---", "3" to "...--", "4" to "....-",
        "5" to ".....", "6" to "-....", "7" to "--...", "8" to "---..", "9" to "----.",
        "." to ".-.-.-", "," to "--..--", "?" to "..--..", "/" to "-..-.", "=" to "-...-",
        " " to " "
    )

    private var wordList: List<String> = listOf("CQ", "QRA", "QRZ") // デフォルト値
    private var selectedWords: MutableList<String> = mutableListOf() // ユーザーがチェックした単語
    private var kochlevel: Int = 2   // 1〜41。初期値は最初の2文字 (K, M)
    private var kochRate: Int = 20 // 追加: koch rate (5〜50%)
    private var eboost: Int = 0
    private var currentMode: AppMode = AppMode.KOCH
    private var isRepeatMode: Boolean = false // true: Repeat, false: random
    private var lastSelectedChars: List<String> = emptyList() // 前回の文字を保存する用
    private var isAlphaON: Boolean = true // Alphabet発音

    private val alphabetList = morseMap.keys.toList()

    private lateinit var wpmValueText: TextView
    private lateinit var volumeValueText: TextView
    private lateinit var morseResultText: TextView
    private lateinit var delayValueText: TextView
    private lateinit var numCharValueText: TextView
    private lateinit var kochLevelText: TextView
    private lateinit var kochRateText: TextView
    private lateinit var eboostText: TextView

    private lateinit var morseAudioPlayer: MorseAudioPlayer
    private lateinit var settingsManager: SettingsManager



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)
        morseAudioPlayer = MorseAudioPlayer()
        morseAudioPlayer.init()


        // SoundPoolの初期化
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
//            .setAudioAttributes(audioAttributes)
            .build()


        loadWordsFromFile() // 起動時に一度読み込む

        // 1. モールス音をロード
        beepSoundId = soundPool.load(this, R.raw.beep, 1)

        // 2. アルファベット音声を一括ロード (a.ogg ~ z.ogg)
        for (char in alphabetList) {
//            val resName = char.lowercase(Locale.ROOT)
            val resName = when(char) {
                "." -> "period"
                "," -> "comma"
                "?" -> "question"
                "/" -> "slash"
                "=" -> "equal"
                "0" -> "zero"
                "1" -> "one"
                "2" -> "two"
                "3" -> "three"
                "4" -> "four"
                "5" -> "five"
                "6" -> "six"
                "7" -> "seven"
                "8" -> "eight"
                "9" -> "nine"
                else -> char.lowercase(Locale.ROOT)
            }
            val resId = resources.getIdentifier(resName, "raw", packageName)
            if (resId != 0) {
                voiceSoundMap[char] = soundPool.load(this, resId, 1)
            }
        }

        silenceSoundId = soundPool.load(this, R.raw.silence, 1)



        // UI初期化
        wpmValueText = findViewById(R.id.wpmValueText)
        volumeValueText = findViewById(R.id.volumeValueText)
        morseResultText = findViewById(R.id.morseResultText)
        delayValueText = findViewById(R.id.delayValueText)
        numCharValueText = findViewById(R.id.numCharValueText)
        kochLevelText = findViewById(R.id.kochLevelText)
        spacingValueText = findViewById(R.id.spacingValueText)
        kochRateText = findViewById(R.id.newRateValueText)
        eboostText = findViewById(R.id.eBoostValueText)
        updateSettings()


        // ★ DataStoreから値を読み込んで変数にセット
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first() // 初回のみ取得
            selectedWords = settings.selectedWords // 保存されていたリストを復元
            kochlevel = settings.kochLevel
            currentMode = settings.appMode // モードを読み込み
            isRepeatMode = settings.isRepeatMode
            isAlphaON = settings.isAlphaON
            numChar = settings.numChar
            answerDelay = settings.answerDelay
            wpm = settings.wpm
            volumeLevel = settings.volumeLevel
            spacingFactor = settings.spacingFactor
            kochRate = settings.kochRate
            eboost = settings.eboost

            // UIに反映
            updateSettings()
            findViewById<Button>(R.id.modeBtn).text = currentMode.name
            findViewById<Button>(R.id.rptrndmBtn).text = if (isRepeatMode) "REPEAT" else "RANDOM"
            findViewById<Button>(R.id.alphaToggleBtn).text = if (isAlphaON) "ON" else "OFF"
        }

        // --- Kochレベル調整ボタンの例 ---
        findViewById<Button>(R.id.kochPlusBtn).setOnClickListener {
            if (kochlevel < lcwoList.size) {
                kochlevel++
                saveInt(SettingsManager.KOCH_LEVEL, kochlevel) // 保存
                lastSelectedChars = emptyList()
                updateSettings()
            }
        }

        findViewById<Button>(R.id.kochMinusBtn).setOnClickListener {
            if (kochlevel > 1) {
                kochlevel--
                saveInt(SettingsManager.KOCH_LEVEL, kochlevel) // 保存
                lastSelectedChars = emptyList()
                updateSettings()
            }
        }

        // --- モード切替ボタン (3モードトグル) ---
        findViewById<Button>(R.id.modeBtn).setOnClickListener {
            // モードを順番に切り替える (KOCH -> RANDOM -> WORD -> KOCH...)
            currentMode = when (currentMode) {
                AppMode.KOCH -> AppMode.RANDOM
                AppMode.RANDOM -> AppMode.WORD
                AppMode.WORD -> AppMode.KOCH
            }

            saveInt(SettingsManager.APP_MODE, currentMode.ordinal)
            (it as Button).text = currentMode.name

            // WORDモードになったらダイアログを表示
            if (currentMode == AppMode.WORD) {
                showWordSelectionDialog()
            }

            lastSelectedChars = emptyList()
            updateSettings()
        }


        // --- repeat/random切替ボタン ---
        findViewById<Button>(R.id.rptrndmBtn).setOnClickListener {
            isRepeatMode = !isRepeatMode
            saveBool(SettingsManager.IS_REPEAT_MODE, isRepeatMode) // 保存
            (it as Button).text = if (isRepeatMode) "REPEAT" else "RANDOM"
            updateSettings()
        }


        // --- alphabet ON/OFF切替ボタン ---
        findViewById<Button>(R.id.alphaToggleBtn).setOnClickListener {
            isAlphaON = !isAlphaON
            saveBool(SettingsManager.IS_ALPHA_ON, isAlphaON) // 保存
            (it as Button).text = if (isAlphaON) "ON" else "OFF"
            updateSettings()
        }


        // 文字数変更ボタン
        findViewById<Button>(R.id.numCharPlusBtn).setOnClickListener {
            if (numChar < 7) {
                numChar++
                saveInt(SettingsManager.NUM_CHAR, numChar) // 保存
                lastSelectedChars = emptyList()
                updateSettings()
            }
        }
        findViewById<Button>(R.id.numCharMinusBtn).setOnClickListener {
            if (numChar > 1) {
                numChar--
                saveInt(SettingsManager.NUM_CHAR, numChar) // 保存
                lastSelectedChars = emptyList()
                updateSettings()
            }
        }


        // Delayプラスボタン (上限3000)
        findViewById<Button>(R.id.delayplusBtn).setOnClickListener {
            if (answerDelay < 3000) {
                answerDelay += 100
                saveLong(SettingsManager.ANSWER_DELAY, answerDelay) // 保存
                updateSettings()
            }
        }

        // Delayマイナスボタン (下限500)
        findViewById<Button>(R.id.delayminusBtn).setOnClickListener {
            if (answerDelay > 500) {
                answerDelay -= 100
                saveLong(SettingsManager.ANSWER_DELAY, answerDelay) // 保存
                updateSettings()
            }
        }

        // WPM変更ボタン
        findViewById<Button>(R.id.plusBtn).setOnClickListener {
            if (wpm < 30) {
                wpm++
                saveInt(SettingsManager.WPM, wpm) // 保存
                updateSettings()
            }
        }
        findViewById<Button>(R.id.minusBtn).setOnClickListener {
            if (wpm > 15) {
                wpm--
                saveInt(SettingsManager.WPM, wpm) // 保存
                updateSettings() }
        }



// Spacingプラスボタン
        findViewById<Button>(R.id.spacingPlusBtn).setOnClickListener {
            if (spacingFactor < 5.0f) {
                spacingFactor += 0.1f
                // 小数点第1位までに丸める
                spacingFactor = Math.round(spacingFactor * 10f) / 10f
                saveFloat(SettingsManager.SPACING_FACTOR, spacingFactor) // 保存用（DataStoreにKey追加が必要）
                updateSettings()
            }
        }

// Spacingマイナスボタン
        findViewById<Button>(R.id.spacingMinusBtn).setOnClickListener {
            if (spacingFactor > 1.0f) {
                spacingFactor -= 0.1f
                spacingFactor = Math.round(spacingFactor * 10f) / 10f
                saveFloat(SettingsManager.SPACING_FACTOR, spacingFactor)
                updateSettings()
            }
        }


// --- koch rate (newRate) の処理 ---
// koch rate (newRate) プラスボタン
        findViewById<Button>(R.id.newRatePlusBtn).setOnClickListener {
            if (kochRate < 50) {
                kochRate += 1
                saveInt(SettingsManager.KOCH_RATE, kochRate)
                updateSettings()
            }
        }

// koch rate (newRate) マイナスボタン
        findViewById<Button>(R.id.newRateMinusBtn).setOnClickListener {
            if (kochRate > 5) {
                kochRate -= 1
                saveInt(SettingsManager.KOCH_RATE, kochRate)
                updateSettings()
            }
        }



        // e boost プラスボタン
        findViewById<Button>(R.id.eBoostPlusBtn).setOnClickListener {
            if (eboost < 40) {
                eboost += 5
                saveInt(SettingsManager.EBOOST, eboost)
                updateSettings()
            }
        }

        // e boost マイナスボタン
        findViewById<Button>(R.id.eBoostMinusBtn).setOnClickListener {
            if (eboost > 0) {
                eboost -= 5
                saveInt(SettingsManager.EBOOST, eboost)
                updateSettings()
            }
        }


        // 音量変更ボタン
        findViewById<Button>(R.id.plusVolumeBtn).setOnClickListener {
            if (volumeLevel < 10) {
                volumeLevel++
                saveInt(SettingsManager.VOLUME_LEVEL, volumeLevel) // 保存
                updateSettings()
            }
        }
        findViewById<Button>(R.id.minusVolumeBtn).setOnClickListener {
            if (volumeLevel > 0) {
                volumeLevel--
                saveInt(SettingsManager.VOLUME_LEVEL, volumeLevel) // 保存
                updateSettings()
            }
        }

        // ループ開始、停止
        findViewById<View>(R.id.startstopButton).setOnClickListener {
            if (!isPlayingLoop) {
                // --- 開始処理 ---
                isPlayingLoop = true
                // ボタンの見た目を変えるならここで（例：テキストを「STOP」にする）
                (it as? Button)?.text = "STOP"
                playRandomMorseLoop()
            } else {
                // --- 停止処理 ---
                isPlayingLoop = false
                // ボタンの見た目を変えるならここで（例：テキストを「START」にする）
                (it as? Button)?.text = "START"

                // 現在実行待ちのスケジュール（次の音や次のループ）をすべてキャンセル
                handler.removeCallbacksAndMessages(null)

                // 再生中の音があれば強制停止
                soundPool.autoPause()
                morseAudioPlayer.stopImmediate()
            }
        }
    }


    private fun saveFloat(key: androidx.datastore.preferences.core.Preferences.Key<Float>, value: Float) {
        lifecycleScope.launch { settingsManager.saveSetting(key, value) }
    }

    private fun loadWordsFromFile() {
        try {
            // アプリ専用の外部ストレージフォルダを取得
            val folder = getExternalFilesDir(null)
            val file = File(folder, "word.txt")

            if (file.exists()) {
                // ファイルが存在すれば、1行ずつ読み込んでリスト化（空行や前後の空白を除去）
                val lines = file.readLines()
                    .map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }

                if (lines.isNotEmpty()) {
                    wordList = lines
                }
            } else {
                // ファイルがない場合は、雛形としてデフォルトのファイルを書き出しておく
                file.writeText("CQ\nQRA\nQRZ")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // エラー時はデフォルトのリストを使用
        }
    }

    private fun showWordSelectionDialog() {
        loadWordsFromFile()

        val wordItems = wordList.toTypedArray()
        // 1. 現在の selectedWords の状態を反映した配列を作る
        val checkedItems = BooleanArray(wordItems.size) { index ->
            selectedWords.contains(wordItems[index])
        }

        // 2. 「空なら全選択」にする以下のコードを削除しました
        /*
        if (selectedWords.isEmpty()) {
            selectedWords = wordList.toMutableList()
        }
        */

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("再生する単語を選択")

        builder.setMultiChoiceItems(wordItems, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
            val word = wordItems[which]
            if (isChecked) {
                if (!selectedWords.contains(word)) selectedWords.add(word)
            } else {
                selectedWords.remove(word)
            }
        }

        builder.setPositiveButton("決定") { dialog, _ ->
            saveSelectedWords()
            dialog.dismiss()
        }

        builder.setNeutralButton("すべて選択/解除") { _, _ -> }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            // 現在の選択数が全単語数と同じなら「全解除」、それ以外なら「全選択」
            val allSelectedBefore = (selectedWords.size == wordList.size)

            selectedWords.clear()
            if (!allSelectedBefore) {
                selectedWords.addAll(wordList)
            }

            val listView = dialog.listView
            for (i in wordItems.indices) {
                val shouldCheck = !allSelectedBefore
                checkedItems[i] = shouldCheck     // 内部状態を更新
                listView.setItemChecked(i, shouldCheck) // 表示を更新
            }
        }
    }

    // 保存用ヘルパー関数
    private fun saveSelectedWords() {
        // リストをカンマ区切りの文字列に変換して保存
        val joinedString = selectedWords.joinToString(",")
        lifecycleScope.launch {
            settingsManager.saveSetting(SettingsManager.SELECTED_WORDS, joinedString)
        }
    }
    private fun saveInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        lifecycleScope.launch { settingsManager.saveSetting(key, value) }
    }
    private fun saveBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        lifecycleScope.launch { settingsManager.saveSetting(key, value) }
    }
    private fun saveLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, value: Long) {
        lifecycleScope.launch { settingsManager.saveSetting(key, value) }
    }


    private fun updateSettings() {
        period = 1200L / wpm
//        volume = volumeLevel / 10f    // bit louder even set to 1
        volume = volumeLevel / 20f
        wpmValueText.text = wpm.toString()
        volumeValueText.text = volumeLevel.toString()
        spacingValueText.text = String.format("%.1f", spacingFactor)
        delayValueText.text = answerDelay.toString()
        numCharValueText.text = numChar.toString()

        // 1. リストから該当する文字を取得（インデックス調整のため -1）
        val charAtLevel = if (kochlevel in 1..lcwoList.size) {
            lcwoList[kochlevel - 1]
        } else {
            "" // 範囲外の場合の安全策
        }
        kochLevelText.text = "$kochlevel ($charAtLevel)"
        kochRateText.text = kochRate.toString()
        eboostText.text = eboost.toString()

        morseAudioPlayer.updateAudioBuffers(period, volume)
    }

    private fun playRandomMorseLoop() {
        if (!isPlayingLoop) return

        // WORDモードで選択された単語がない場合は停止させる
        if (currentMode == AppMode.WORD && selectedWords.isEmpty()) {
            isPlayingLoop = false
            handler.post {
                // 必要に応じてユーザーに通知
                morseResultText.text = "Select word!"
                findViewById<Button>(R.id.startstopButton).text = "START"
            }
            return
        }

        // リピートモードかつ、すでに前回の文字がある場合はそれを使う
        val selectedChars = if (isRepeatMode && lastSelectedChars.isNotEmpty()) {
            lastSelectedChars
        } else {
            // モードによってプールを切り替え
            val newChars = when (currentMode) {
                AppMode.KOCH -> {
                    // 1. 現在のレベルまでの全文字リスト
                    val currentPool = lcwoList.take(kochlevel.coerceIn(1, lcwoList.size))
                    // 2. 現在のレベルで追加された「最新の文字」
                    val latestChar = currentPool.last()

                    List(numChar) {
                        val rand = Random().nextFloat()

                        // --- eboost ロジック追加 ---
                        // eboostが0より大きく、かつプール内に 'E' が存在する場合
                        if (eboost > 0 && currentPool.contains("E") && rand < (eboost / 100f)) {
                            "E"
                        }
                        // --- 既存の Koch ロジック ---
                        else if (Random().nextFloat() < (kochRate / 100f)) {
                            latestChar
                        } else {
                            currentPool.random()
                        }
                    }
                }
                AppMode.RANDOM -> {
                    val currentPool = morseMap.keys.toList()
                    List(numChar) { currentPool.random() }
                }
                AppMode.WORD -> {
                    val pool = selectedWords
                    val randomWord = pool.randomOrNull() ?: ""
                    // .map { it.toString() } はスペースも維持するのでそのままでOK
                    randomWord.map { it.toString() }
                }
            }
            lastSelectedChars = newChars
            newChars
        }


        // 再生中は "?" を文字数分並べる
        morseResultText.text = "? ".repeat(numChar).trim()

        // ★ 複数文字を順番に再生する関数を呼び出す
        playMultipleMorseCodes(selectedChars, 0) {
            // 全符号が鳴り終わった後の処理
            handler.postDelayed({
                // アルファベットを一気に表示＆音声を順番に再生
                if (isAlphaON) {
                    // アルファベット表示＆音声を再生
                    playMultipleVoices(selectedChars, 0) {
                        // 全音声が終わったら1秒待って次のループへ
                        handler.postDelayed({ playRandomMorseLoop() }, 1000L)
                    }
                } else {
                    // 音声読み上げオフの場合：すぐに次のループへ
                    morseResultText.text = selectedChars.joinToString(" ")
                    handler.postDelayed({ playRandomMorseLoop() }, 1800L)
                }
            }, answerDelay)
        }
    }


    // 複数のモールス符号を順番に再生する再帰関数
    private fun playMultipleMorseCodes(chars: List<String>, index: Int, onAllComplete: () -> Unit) {
        if (index >= chars.size) {
            onAllComplete()
            return
        }

        val char = chars[index]
        val code = morseMap[chars[index]] ?: ""
        playMorseCode(code) {
            // スペース " " の場合は、playMorseCode 内で既に単語間無音を生成済みなので
            // handler 側での追加ディレイを 0 にする。
            // それ以外の通常の文字の場合は、文字間ディレイ（3単位）を入れる。
            val delay = if (char == " ") {
                0L
            } else {
                (period * 3 * spacingFactor).toLong()
            }
            // 文字間の空白を置いてから次の文字へ
            handler.postDelayed({
                playMultipleMorseCodes(chars, index + 1, onAllComplete)
            }, delay)
        }
    }

    // 複数の音声を順番に再生する再帰関数
    private fun playMultipleVoices(chars: List<String>, index: Int, onAllComplete: () -> Unit) {
        if (index == 0) morseResultText.text = "" // 最初の再生時にテキストをクリア

        if (index >= chars.size) {
            onAllComplete()
            return
        }

        val char = chars[index]
        morseResultText.append("$char ") // アルファベットを1文字ずつ追加表示

        val soundId = voiceSoundMap[char]
        if (soundId != null) {
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
        }

        // 音声の長さを待ってから次の文字へ
        handler.postDelayed({
            playMultipleVoices(chars, index + 1, onAllComplete)
        }, 600 )   // 600msec固定
    }



    // 修正後
    private fun playMorseCode(code: String, onComplete: () -> Unit) {
        // morseAudioPlayer側の定義に合わせて引数を減らす
        morseAudioPlayer.playMorseCode(code) {
            // AudioTrackは別スレッドで動くため、UI操作(onComplete)はHandlerで戻す
            handler.post { onComplete() }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        isPlayingLoop = false
        soundPool.release()
        morseAudioPlayer.release()
        handler.removeCallbacksAndMessages(null)
    }
}








class MorseAudioPlayer {
    private val sampleRate = 44100
    private val freq = 600.0
    private var audioTrack: AudioTrack? = null

    @Volatile private var dotSamples: ShortArray? = null
    @Volatile private var dashSamples: ShortArray? = null
    @Volatile private var blankSamples: ShortArray? = null

    private val fadeSamples = (sampleRate * 0.0015).toInt()

    fun init() {
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    fun updateAudioBuffers(period: Long, volume: Float) {
        dotSamples = generateSineWave(period.toInt(), volume)
        dashSamples = generateSineWave((period * 3).toInt(), volume)
        blankSamples = ShortArray((sampleRate * period / 1000).toInt())
    }

    private fun generateSineWave(durationMs: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000)
        val samples = ShortArray(numSamples)
        val maxAmplitude = (Short.MAX_VALUE * volume).toInt()

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freq)
            var amplitude = maxAmplitude.toDouble()
            if (i < fadeSamples) amplitude *= (i.toDouble() / fadeSamples)
            else if (i > numSamples - fadeSamples) amplitude *= ((numSamples - i).toDouble() / fadeSamples)
            samples[i] = (kotlin.math.sin(angle) * amplitude).toInt().toShort()
        }
        return samples
    }

    // 引数を code と onComplete のみに修正
    fun playMorseCode(code: String, onComplete: () -> Unit) {
        Thread {
            // 現在のバッファをローカル変数に固定（Smart Castを有効にするため）
            val currentDot = dotSamples
            val currentDash = dashSamples
            val currentBlank = blankSamples

            // 全てが揃っていない場合は中断
            if (currentDot == null || currentDash == null || currentBlank == null) {
                onComplete()
                return@Thread
            }

            if (code == " ") {
                // 単語間の空白（スペース自体が1文字として送られてきた場合）
                repeat(7) { audioTrack?.write(currentBlank, 0, currentBlank.size) }
            } else {
                for (symbol in code) {
                    when (symbol) {
                        '.' -> audioTrack?.write(currentDot, 0, currentDot.size)
                        '-' -> audioTrack?.write(currentDash, 0, currentDash.size)
                    }
                    // 各記号（・やー）の直後の1短点分の空白
                    audioTrack?.write(currentBlank, 0, currentBlank.size)
                }
            }
            onComplete()
        }.start()
    }

    fun stopImmediate() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}