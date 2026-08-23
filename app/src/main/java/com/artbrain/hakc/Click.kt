package com.artbrain.hakc

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** 딸깍은 알림이 아니라 손맛이다. 낮게 깐다. */
private const val VOLUME = 0.35f

/**
 * 카드가 노랑·초록으로 넘어가는 순간의 딸깍.
 *
 * 파일은 [tools/click.py] 가 합성한 14ms 짜리다. SoundPool 은 짧은 소리를 미리
 * 풀어 두었다가 곧바로 내보내므로 손가락이 문턱을 넘는 그 순간에 맞는다 —
 * MediaPlayer 로는 첫 소리가 늦는다.
 */
@Composable
fun rememberClick(): () -> Unit {
    val context = LocalContext.current
    val pool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var ready by remember { mutableStateOf(false) }
    val id = remember {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        pool.load(context, R.raw.click, 1)
    }
    DisposableEffect(Unit) { onDispose { pool.release() } }
    return remember(id) {
        {
            // 아직 안 풀렸으면 그냥 넘긴다. 없는 소리를 기다리게 할 것은 없다.
            if (ready) pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
        }
    }
}
