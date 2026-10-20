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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 일본어 읽기를 소리 내는 곳. 기기의 TTS 를 그대로 빌린다 — 녹음을 담아 오면 낱말
 * 6만 7천 개 몫이라 앱이 감당할 크기가 아니다.
 *
 * 엔진은 처음 누를 때 깨운다. 설정을 꺼 둔 사람에게는 끝내 깨지 않는다.
 * 읽는 동안에는 [speaking] 에 그 히라가나가 서 있고, 화면 가운데의 캡슐이
 * 그것을 보고 떴다 진다.
 */
object Voice {
    /** 지금 소리 나는 읽기. 조용할 때는 null. */
    var speaking by mutableStateOf<String?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ready = false
    /** 엔진이 깨기 전에 누른 것. 깨면 곧바로 읽는다. */
    private var waiting: String? = null
    private val main = Handler(Looper.getMainLooper())

    fun say(c: Context, kana: String) {
        val app = c.applicationContext
        val t = tts
        if (t == null) {
            waiting = kana
            tts = TextToSpeech(app) { status ->
                main.post { woke(app, status) }
            }
            return
        }
        if (!ready) {
            waiting = kana
            return
        }
        t.speak(kana, TextToSpeech.QUEUE_FLUSH, null, kana)
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
                main.post { speaking = id }
            }

            override fun onDone(id: String?) {
                main.post { if (speaking == id) speaking = null }
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                main.post { if (speaking == id) speaking = null }
            }

            @Deprecated("API 21 부터 onError(String, Int)")
            override fun onError(id: String?) {
                main.post { if (speaking == id) speaking = null }
            }
        })
        ready = true
        waiting?.let { w ->
            waiting = null
            t.speak(w, TextToSpeech.QUEUE_FLUSH, null, w)
        }
    }
}

/**
 * 읽는 동안 화면 한가운데에 뜨는 캡슐 — 그 소리의 히라가나. 소리가 그치면 진다.
 * 손짓은 받지 않는다. 그 뒤의 것을 누르는 데 걸리지 않게.
 */
@Composable
fun VoiceCapsule() {
    val now = Voice.speaking
    // 지는 동안에도 글이 남아 있어야 한다. 마지막으로 선 읽기를 붙잡아 둔다.
    var last by remember { mutableStateOf("") }
    if (now != null) last = now
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = now != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.92f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f),
        ) {
            Text(
                last,
                fontSize = 30.sp,
                // 히라가나는 앱 서체(코레일체)에 없어 시스템 CJK 로 떨어진다. 굵기는 레귤러로 못 박는다.
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                color = Hak3.Text,
                modifier = Modifier
                    .background(Hak3.Surface.copy(alpha = 0.94f), CircleShape)
                    .padding(horizontal = 30.dp, vertical = 14.dp),
            )
        }
    }
}
