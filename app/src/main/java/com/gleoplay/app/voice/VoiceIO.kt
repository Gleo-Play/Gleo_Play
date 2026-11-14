package com.gleoplay.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import com.gleoplay.app.core.Ear
import com.gleoplay.app.core.Speaker
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * 음성 입출력.
 * - 기본: Pleos TextToSpeech / SpeechToText SDK (ON_DEVICE — 앱 등록·네트워크 불필요)
 * - Pleos 서비스가 없는 기기(일반 에뮬레이터 등)에서는 Android 기본 TTS·음성 인식으로 대체
 * - 낭독 전 오디오 포커스를 요청하고, 어떤 이유로든 포커스를 잃으면 즉시 멈춘다(내비·Gleo 우선)
 */
class VoiceIO(
    private val context: Context,
    /** 시연 빌드: 마이크·음성 인식이 없어도 '대답 듣기' 상태를 열어 두고 시연 도구 입력을 기다린다 */
    private val demoMode: Boolean = false,
    private val onInterrupted: () -> Unit,
) : Speaker, Ear {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null

    // ── TTS ──
    private var pleosTts: TextToSpeech? = null
    private var pleosTtsReady = false
    private var androidTts: android.speech.tts.TextToSpeech? = null
    private var androidTtsReady = false
    private var onDoneCb: (() -> Unit)? = null

    // ── STT ──
    private var pleosStt: SpeechToText? = null
    private var pleosSttReady = false
    private var androidStt: SpeechRecognizer? = null
    private var onResultCb: ((String?) -> Unit)? = null
    private var onPartialCb: ((String) -> Unit)? = null
    private val listenTimeout: Runnable = Runnable {
        // 말하는 중(방금 중간 결과가 들어옴)이면 끊지 않고 조금 더 듣는다. 한 구간에 두 번까지
        val speaking = android.os.SystemClock.uptimeMillis() - lastPartialAt < SPEAKING_MS
        if (speaking && extendLeft > 0) {
            extendLeft--
            main.postDelayed(listenTimeoutRef, EXTEND_MS)
        } else deliverAnswer(null, force = true)
    }
    private val listenTimeoutRef: Runnable get() = listenTimeout
    private var lastPartialAt = 0L
    private var extendLeft = 0
    /** 이번 듣기 구간에서 들어온 가장 큰 마이크 음량(진단용 로그) */
    private var maxRms = 0.0
    private var rmsCount = 0
    /** 시연용 녹음 음성을 STT에 넣는 중(마이크 세션 종료 신호는 무시) */
    @Volatile private var injecting = false

    /** 마이크·음성 인식을 쓸 수 없는 이유(화면 안내용) */
    override var problem: String? = null
        private set

    /** 화면 하단 표시용 */
    var engineLabel: String = "준비 중"
        private set

    fun start() {
        initPleosTts()
        initPleosStt()
    }

    fun release() {
        stop(); cancel()
        runCatching { pleosTts?.release() }
        runCatching { pleosStt?.release() }
        runCatching { androidTts?.shutdown() }
        runCatching { androidStt?.destroy() }
    }

    // ───────────────────────── Speaker ─────────────────────────
    override fun speak(text: String, onDone: () -> Unit) {
        stop()
        if (!requestFocus()) { onInterrupted(); return }
        onDoneCb = onDone
        val now = android.os.SystemClock.uptimeMillis()
        when {
            pleosTtsReady -> runCatching { pleosTts?.speak(text, true) }.onFailure { finishSpeech() }
            androidTtsReady -> {
                val r = androidTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "gleoplay")
                if (r != android.speech.tts.TextToSpeech.SUCCESS) { Log.w(TAG, "Android TTS 낭독 실패: $r"); finishSpeech() }
            }
            else -> {
                // 음성 엔진이 아직 없으면 글자 길이만큼 기다린 뒤 끝난 것으로 처리(화면 시연용)
                main.postAtTime({ finishSpeech() }, SPEECH_TOKEN, now + 120L * text.length)
                return
            }
        }
        // 안전망: 음성 엔진이 '끝났다' 신호를 주지 않아도(한국어 음성 없음·엔진 오류 등) 게임이 멈추지 않게 한다
        main.postAtTime({
            if (onDoneCb != null) { Log.w(TAG, "TTS 완료 신호가 오지 않아 시간 초과로 다음 단계로 넘어가요"); finishSpeech() }
        }, SPEECH_TOKEN, now + SPEECH_GUARD_MS + SPEECH_MS_PER_CHAR * text.length)
    }

    override fun stop() {
        onDoneCb = null
        main.removeCallbacksAndMessages(SPEECH_TOKEN)
        runCatching { pleosTts?.stop() }
        runCatching { androidTts?.stop() }
        abandonFocus()
    }

    private fun finishSpeech() = main.post {
        main.removeCallbacksAndMessages(SPEECH_TOKEN)
        val cb = onDoneCb
        onDoneCb = null
        abandonFocus()
        cb?.invoke()
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0) { // LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK 모두 즉시 멈춤
                    stop(); cancel(); onInterrupted()
                }
            }, main)
            .build()
        focusRequest = req
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun initPleosTts() {
        val tts = runCatching { TextToSpeech(context, TtsMode.ON_DEVICE) }.getOrNull()
        if (tts == null) { initAndroidTts(); return }
        pleosTts = tts
        runCatching {
            tts.initialize(object : TtsInit {
                override fun onSuccess() = main.post {
                    runCatching { tts.setLocale(Locale.KOREA); tts.setAudioAttributes(attrs) }
                    pleosTtsReady = true
                    engineLabel = "Pleos TTS·STT"
                }.let { }
                override fun onFailure(errorType: TtsError) = main.post {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $errorType"); initAndroidTts()
                }.let { }
            })
            tts.addEventListener(object : EventListener {
                override fun onReady() {}
                override fun onStart() {}
                override fun onUpdatedRms(rms: Double) {}
                override fun onDone() { finishSpeech() }
                override fun onStop() {}
                override fun onError(errorType: TtsError) { Log.w(TAG, "TTS 오류: $errorType"); finishSpeech() }
            })
        }.onFailure { Log.w(TAG, "Pleos TTS 사용 불가: $it"); initAndroidTts() }
    }

    private fun initAndroidTts() {
        if (androidTts != null) return
        androidTts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                androidTts?.language = Locale.KOREA
                androidTts?.setAudioAttributes(attrs)
                androidTtsReady = true
                if (!pleosTtsReady) engineLabel = "Android TTS(대체)"
            }
        }.also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { finishSpeech() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { finishSpeech() }
            })
        }
    }

    // ───────────────────────── Ear ─────────────────────────
    private val micReady: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (pleosSttReady || SpeechRecognizer.isRecognitionAvailable(context))

    override val available: Boolean get() = micReady || demoMode

    override fun listenOnce(onPartial: (String) -> Unit, onResult: (String?) -> Unit) {
        cancel()
        onResultCb = onResult
        onPartialCb = onPartial
        maxRms = 0.0; rmsCount = 0; lastPartialAt = 0L; extendLeft = 2
        Log.d(TAG, "듣기 시작 (Pleos STT ${if (pleosSttReady) "준비됨" else "준비 안 됨"})")
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            problem = "마이크 권한이 꺼져 있어요"
        }
        if (!micReady) { main.postDelayed(listenTimeout, DEMO_LISTEN_MS); return } // 시연: 입력만 기다림
        main.postDelayed(listenTimeout, LISTEN_MS)
        if (pleosSttReady) {
            runCatching { pleosStt?.request() }.onFailure { deliverAnswer(null) }
        } else {
            startAndroidStt()
        }
    }

    override fun cancel() {
        main.removeCallbacks(listenTimeout)
        onResultCb = null
        onPartialCb = null
        injecting = false
        runCatching { pleosStt?.stop() }
        runCatching { androidStt?.cancel() }
    }

    /** 시연 도구: 마이크 대신 '가족의 대답'을 글자로 넣는다 */
    fun injectAnswer(text: String): Boolean {
        if (onResultCb == null) return false
        deliverAnswer(text); return true
    }

    /**
     * 시연 안전망: 녹음한 대답(16kHz·16bit·mono PCM)을 Pleos STT(ON_DEVICE)에 직접 넣어 실제로 인식시킨다.
     * PT 현장 소음·마이크 문제에도 '진짜 음성 인식' 경로를 보여 줄 수 있다. 듣는 중일 때만 동작한다.
     */
    fun recognizeAudio(pcm: ByteArray): Boolean {
        val stt = pleosStt ?: return false
        if (!pleosSttReady || onResultCb == null) return false
        injecting = true
        main.removeCallbacks(listenTimeout)
        main.postDelayed(listenTimeout, LISTEN_MS + 4_000)
        runCatching { stt.stop() }
        Thread {
            runCatching {
                Thread.sleep(300)
                var i = 0
                while (i < pcm.size && injecting) {
                    val end = minOf(pcm.size, i + CHUNK)
                    stt.sendAudio(pcm.copyOfRange(i, end))
                    i = end
                    Thread.sleep(CHUNK_MS) // 실제 말하는 속도에 맞춰 넣는다
                }
                stt.completeAudioSend()
            }.onFailure { Log.w(TAG, "녹음 음성 주입 실패: $it"); main.post { deliverAnswer(null, force = true) } }
        }.start()
        return true
    }

    val isListening: Boolean get() = onResultCb != null

    /**
     * 진단용(시연 빌드): 안드로이드 기본 녹음(AudioRecord)으로 입력 장치와 소스별 마이크 크기를 2초씩 재서 로그에 남긴다.
     * Pleos STT 가 무음만 받을 때, 마이크 경로 중 어디가 끊겼는지 가려내는 데 쓴다.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun micTest() {
        Thread {
            runCatching {
                val devs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
                Log.d(TAG, "마이크 진단: 입력 장치 ${devs.size}개 " + devs.joinToString { "[type=${it.type} addr=${it.address} ch=${it.channelCounts.toList()} name=${it.productName}]" })
                val sources = listOf(
                    "MIC" to android.media.MediaRecorder.AudioSource.MIC,
                    "VOICE_RECOGNITION" to android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    "DEFAULT" to android.media.MediaRecorder.AudioSource.DEFAULT,
                    "UNPROCESSED" to android.media.MediaRecorder.AudioSource.UNPROCESSED,
                )
                val rate = 16_000
                val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                for ((name, src) in sources) {
                    val rec = runCatching { android.media.AudioRecord(src, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400)) }.getOrNull()
                    if (rec == null || rec.state != android.media.AudioRecord.STATE_INITIALIZED) { Log.d(TAG, "마이크 진단: $name 열기 실패"); rec?.release(); continue }
                    val buf = ShortArray(1600); var peak = 0; var sum = 0L; var n = 0L
                    rec.startRecording()
                    val routed = rec.routedDevice
                    val end = android.os.SystemClock.uptimeMillis() + 2_000
                    while (android.os.SystemClock.uptimeMillis() < end) {
                        val r = rec.read(buf, 0, buf.size)
                        for (i in 0 until r) { val v = kotlin.math.abs(buf[i].toInt()); if (v > peak) peak = v; sum += v; n++ }
                    }
                    rec.stop(); rec.release()
                    Log.d(TAG, "마이크 진단: $name → 최대 $peak / 32767, 평균 ${if (n > 0) sum / n else 0}, 표본 $n, 장치 type=${routed?.type} addr=${routed?.address}")
                }
            }.onFailure { Log.w(TAG, "마이크 진단 실패: $it") }
        }.start()
    }

    private fun deliverAnswer(text: String?, force: Boolean = false) {
        // 녹음 주입 중에는 앞서 끊은 마이크 세션의 '빈 종료' 신호를 무시한다
        if (injecting && text == null && !force) return
        main.removeCallbacks(listenTimeout)
        val cb = onResultCb ?: return
        onResultCb = null
        onPartialCb = null
        injecting = false
        Log.d(TAG, "듣기 끝: 결과=${text ?: "없음"} · 최대 음량=${"%.1f".format(maxRms)} (음량 신호 ${rmsCount}번)")
        if (text != null) problem = null
        runCatching { pleosStt?.stop() }
        runCatching { androidStt?.cancel() }
        main.post { cb(text) }
    }

    private fun sttProblem(e: SttError): String? = when (e) {
        SttError.MIC_ERROR, SttError.AUDIO_ERROR -> "마이크에 문제가 있어요"
        SttError.PERMISSION_DENIED -> "마이크 권한이 꺼져 있어요"
        SttError.ON_DEVICE_ENGINE_ERROR, SttError.UNAVAILABLE_LANGUAGE, SttError.NOT_SUPPORTED_API -> "음성 인식을 쓸 수 없어요"
        else -> null // TIMEOUT 등은 다시 들으면 된다
    }

    private fun initPleosStt() {
        val stt = runCatching { SpeechToText(context, SttMode.ON_DEVICE) }.getOrNull() ?: return
        pleosStt = stt
        runCatching {
            stt.initialize(object : SttInit {
                override fun onSuccess() = main.post {
                    runCatching { stt.setLocale(Locale.KOREA) }
                    pleosSttReady = true
                    Log.d(TAG, "Pleos STT 준비 완료")
                }.let { }
                override fun onFailure(errorType: SttError) { Log.w(TAG, "Pleos STT 초기화 실패: $errorType") }
            })
            stt.addListener(object : ResultListener {
                override fun onReady() {}
                override fun onStartedRecognition() { Log.d(TAG, "STT 말소리 시작 감지") }
                override fun onUpdatedEpdData(on: Long, off: Long) { Log.d(TAG, "STT 말 구간: on=$on off=$off") }
                override fun onUpdatedRms(rms: Double) { rmsCount++; if (rms > maxRms) maxRms = rms }
                override fun onUpdated(stt: String, completed: Boolean) {
                    Log.d(TAG, "STT ${if (completed) "최종" else "중간"}: $stt")
                    main.post {
                        if (completed) deliverAnswer(stt)
                        else { if (stt.isNotBlank()) lastPartialAt = android.os.SystemClock.uptimeMillis(); onPartialCb?.invoke(stt) }
                    }
                }
                override fun onEndedRecognition() { main.post { deliverAnswer(null) } }
                override fun onError(errorType: SttError) {
                    Log.w(TAG, "STT 오류: $errorType")
                    main.post {
                        sttProblem(errorType)?.let { problem = it }
                        deliverAnswer(null, force = sttProblem(errorType) != null)
                    }
                }
            })
        }.onFailure { Log.w(TAG, "Pleos STT 사용 불가: $it") }
    }

    private fun startAndroidStt() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { deliverAnswer(null); return }
        val r = androidStt ?: SpeechRecognizer.createSpeechRecognizer(context).also { androidStt = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                deliverAnswer(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onError(error: Int) { deliverAnswer(null) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onPartialCb?.invoke(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    companion object {
        private const val TAG = "VoiceIO"
        /**
         * 한 번 듣는 길이. 듣기를 다시 시작할 때마다 마이크가 열리기까지 0.3~1.5초가 걸려(에뮬레이터 실측)
         * 그 사이의 말을 놓치므로, 너무 짧게 끊지 않는다.
         */
        private const val LISTEN_MS = 12_000L
        /** 마지막 중간 결과가 이 시간 안에 들어왔으면 '말하는 중'으로 본다 */
        private const val SPEAKING_MS = 2_500L
        /** 말하는 중일 때 듣기를 늘려 주는 시간 */
        private const val EXTEND_MS = 4_000L
        private const val DEMO_LISTEN_MS = 20_000L
        private const val CHUNK = 3_200          // 16kHz·16bit·mono 100ms
        private const val CHUNK_MS = 100L
        private val SPEECH_TOKEN = Any()
        /**
         * TTS 완료 신호를 기다리는 최대 시간 = 12초 + 글자당 0.35초.
         * 정상일 때는 완료 신호로 넘어가므로 이 시간은 엔진이 멈췄을 때만 쓰인다.
         * 느린 기기에서는 합성 시작까지 4초쯤 걸릴 수 있어(에뮬레이터 실측), 말끝이 잘리지 않게 넉넉히 둔다.
         */
        private const val SPEECH_GUARD_MS = 12_000L
        private const val SPEECH_MS_PER_CHAR = 350L
    }
}
