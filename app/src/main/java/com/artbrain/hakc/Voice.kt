package com.artbrain.hakc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** 소리 낼 읽기 하나 — 히라가나와, 우리 한자와 다를 때의 일본 표기. */
data class Reading(val kana: String, val jp: String?)

/**
 * 일본어 읽기를 소리 내는 곳. 기기의 TTS 를 그대로 빌린다 — 녹음을 담아 오면 낱말
 * 6만 7천 개 몫이라 앱이 감당할 크기가 아니다.
 *
 * 엔진은 처음 누를 때 깨운다. 설정을 꺼 둔 사람에게는 끝내 깨지 않는다.
 * 읽는 동안에는 [speaking] 에 그 읽기가 서 있고, 화면 위 가운데의 캡슐이
 * 그것을 보고 떴다 진다.
 */
object Voice {
    /** 지금 소리 나는 읽기. 조용할 때는 null. */
    var speaking by mutableStateOf<Reading?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ready = false
    /** 엔진이 깨기 전에 누른 것. 깨면 곧바로 읽는다. */
    private var waiting: Reading? = null
    /** 마지막으로 읽으라 한 것. 엔진은 히라가나(발화 id)만 돌려주므로 여기서 짝을 찾는다. */
    private var asked: Reading? = null
    private val main = Handler(Looper.getMainLooper())

    fun say(c: Context, kana: String, jp: String?) {
        val app = c.applicationContext
        val r = Reading(kana, jp)
        val t = tts
        if (t == null) {
            waiting = r
            tts = TextToSpeech(app) { status ->
                main.post { woke(app, status) }
            }
            return
        }
        if (!ready) {
            waiting = r
            return
        }
        speak(t, r)
    }

    private fun speak(t: TextToSpeech, r: Reading) {
        asked = r
        t.speak(r.kana, TextToSpeech.QUEUE_FLUSH, null, r.kana)
    }

    private fun woke(app: Context, status: Int) {
        val t = tts ?: return
        val lang = if (status == TextToSpeech.SUCCESS) t.setLanguage(Locale.JAPAN) else -1
        if (lang < TextToSpeech.LANG_AVAILABLE) {
            // 일본어 음성이 없는 기기다. 다음에 누르면 다시 깨워 본다 —
            // 그사이 음성을 받아 두었을 수 있다.
            t.shutdown()
            tts = null
            waiting = null
            Toast.makeText(app, "일본어 음성이 없습니다 — 기기의 TTS 설정에서 받아 주세요",
                Toast.LENGTH_LONG).show()
            return
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                main.post { asked?.takeIf { it.kana == id }?.let { speaking = it } }
            }

            override fun onDone(id: String?) {
                main.post { if (speaking?.kana == id) speaking = null }
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                main.post { if (speaking?.kana == id) speaking = null }
            }

            @Deprecated("API 21 부터 onError(String, Int)")
            override fun onError(id: String?) {
                main.post { if (speaking?.kana == id) speaking = null }
            }
        })
        ready = true
        waiting?.let { w ->
            waiting = null
            speak(t, w)
        }
    }
}

/**
 * 읽는 동안 화면 위 가운데(위에서 [CAPSULE_TOP])에 뜨는 캡슐 — 그 소리의 히라가나.
 * 소리가 그치면 진다.
 * 손짓은 받지 않는다. 그 뒤의 것을 누르는 데 걸리지 않게.
 *
 * 일본 표기가 우리 한자와 다르면(學校 → 学校) 그 표기를 히라가나 윗줄에 세운다.
 * 모서리는 히라가나만 설 때의 캡슐 둥글기를
 * 그대로 따르므로, 두 줄이면 둥근 네모가 된다.
 */
@Composable
fun VoiceCapsule() {
    val now = Voice.speaking
    // 지는 동안에도 글이 남아 있어야 한다. 마지막으로 선 읽기를 붙잡아 둔다.
    var last by remember { mutableStateOf(Reading("", null)) }
    if (now != null) last = now
    // 히라가나 한 줄의 키. 캡슐의 둥글기가 이것으로 정해진다 — 한 줄일 때 반원이 되는 값.
    var kanaH by remember { mutableIntStateOf(0) }
    val radius = with(LocalDensity.current) { kanaH.toDp() } / 2 + CAPSULE_PAD_Y
    Box(
        Modifier.fillMaxSize().padding(top = CAPSULE_TOP),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = now != null,
            enter = fadeIn(tween(120)) +
                scaleIn(tween(160), initialScale = 0.92f, transformOrigin = CAPSULE_ORIGIN),
            exit = fadeOut(tween(220)) +
                scaleOut(tween(220), targetScale = 0.96f, transformOrigin = CAPSULE_ORIGIN),
        ) {
            Column(
                Modifier
                    // 맞춰 둔 치수는 그대로 두고 통째로 줄인다. 윗변을 축으로 줄여
                    // 위에서 [CAPSULE_TOP] 자리가 흔들리지 않게.
                    .graphicsLayer {
                        scaleX = CAPSULE_SCALE
                        scaleY = CAPSULE_SCALE
                        transformOrigin = CAPSULE_ORIGIN
                    }
                    .width(IntrinsicSize.Max)
                    .background(Hak3.Surface.copy(alpha = 0.9f), RoundedCornerShape(radius))
                    .padding(
                        top = CAPSULE_PAD_Y - if (last.jp == null) CAPSULE_ONE_LINE_LIFT else 0.dp,
                        bottom = CAPSULE_PAD_Y +
                            if (last.jp == null) CAPSULE_ONE_LINE_LIFT else CAPSULE_TWO_LINE_LIFT,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                last.jp?.let { jp ->
                    // 한자 줄은 잉크 아래로 비는 자리가 넉넉해, 그만큼 상자를 깎아
                    // 히라가나와의 잉크 사이가 [CAPSULE_LINE_GAP] 이 되게 한다.
                    val cut = with(LocalDensity.current) { CAPSULE_KANJI_CUT.roundToPx() }
                    Text(
                        jp,
                        style = CAPSULE_KANJI,
                        modifier = Modifier
                            .padding(horizontal = CAPSULE_PAD_X)
                            .layout { m, c ->
                                val p = m.measure(c)
                                layout(p.width, p.height - cut) { p.place(0, 0) }
                            },
                    )
                }
                Text(
                    last.kana,
                    style = CAPSULE_KANA,
                    modifier = Modifier
                        .padding(horizontal = CAPSULE_PAD_X)
                        .onSizeChanged { kanaH = it.height },
                )
            }
        }
    }
}

/**
 * 캡슐의 글. 히라가나도 일본 한자도 앱 서체(코레일체)에 없어 시스템 CJK 로 떨어진다.
 * 글자꼴은 일본 것으로 — 같은 学 이라도 한국 서체로 그리면 획이 조금씩 다르다.
 *
 * 히라가나는 아래 판 회차 번호의 잉크로, 한 단 얇게(라이트) 28sp. 한자는 노란 판의
 * 색으로, 레귤러 32sp. 둘 사이는 잉크끼리 [CAPSULE_LINE_GAP].
 */
/**
 * 히라가나의 Light. 폰의 CJK 서체는 레귤러 한 굵기뿐이라 Light 를 달라 해도 레귤러로
 * 그린다 — Noto Sans JP 를 300 으로 굳혀 가나만 담아 왔다([tools/make_kana_font.py]).
 */
private val KanaLight = FontFamily(Font(R.font.noto_sans_jp_kana_light, FontWeight.Light))

private val CAPSULE_KANA = TextStyle(
    fontSize = 28.sp,
    fontFamily = KanaLight,
    fontWeight = FontWeight.Light,
    color = Hak3.Hanja,
    localeList = LocaleList("ja-JP"),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CAPSULE_KANJI = CAPSULE_KANA.copy(
    fontFamily = FontFamily.Default,
    fontSize = 32.sp,
    fontWeight = FontWeight.Normal,
    color = Hak3.Panel,
)

/** 앱 화면(상태 표시줄 아래) 윗변에서 캡슐 윗변까지. */
private val CAPSULE_TOP = 36.dp

/** 캡슐 전체의 크기. 아래 치수들은 1 일 때의 것이다. */
private const val CAPSULE_SCALE = 0.94f

/** 줄이고 키우는 축 — 윗변 가운데. */
private val CAPSULE_ORIGIN = TransformOrigin(0.5f, 0f)

private val CAPSULE_PAD_X = 30.dp
private val CAPSULE_PAD_Y = 14.dp

/** 한자 줄의 상자에서 깎는 몫. 깎지 않으면 잉크 사이가 13dp 라 7dp 를 깎아 6dp 로. */
private val CAPSULE_KANJI_CUT = 7.dp

/**
 * 히라가나만 설 때 위에서 덜어 아래로 옮기는 몫. 가나는 잉크가 줄 상자의 아래로
 * 쏠려 앉아, 위아래를 같게 두르면 위가 23dp·아래가 20dp 로 섰다. 키는 그대로라
 * 캡슐의 둥글기도 그대로다.
 */
private val CAPSULE_ONE_LINE_LIFT = 2.dp

/** 두 줄일 때 아래에 더 두는 몫. 위(한자 위)와 아래(히라가나 아래)의 여백을 맞춘다. */
private val CAPSULE_TWO_LINE_LIFT = 2.dp

/** 한자와 히라가나의 잉크 사이. */
private val CAPSULE_LINE_GAP = 6.dp
