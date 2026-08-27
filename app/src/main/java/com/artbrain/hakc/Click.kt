package com.artbrain.hakc

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext

/** 소리는 알림이 아니라 손맛이다. 낮게 깐다. */
private const val VOLUME = 0.35f

/**
 * 카드를 넘길 때 나는 두 소리.
 *
 *  [yes] 한 칸 넘어갔다 — 담기든 풀리든 자리가 바뀌었으면 이 소리다
 *  [no]  축의 끝이라 더 갈 데가 없다
 *
 * 파형은 [tools/sound.py] 가 합성한다. SoundPool 은 짧은 소리를 미리 풀어 두었다가
 * 곧바로 내보내므로 손가락이 문턱을 넘는 그 순간에 맞는다 — MediaPlayer 로는
 * 첫 소리가 늦는다.
 */
class Clicks(private val pool: SoundPool, private val ids: List<Int>, private val ready: List<Int>) {
    fun yes() = play(0)
    fun no() = play(1)

    private fun play(which: Int) {
        val id = ids[which]
        // 아직 안 풀렸으면 그냥 넘긴다. 없는 소리를 기다리게 할 것은 없다.
        if (id in ready) pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }
}

@Composable
fun rememberClicks(): Clicks {
    val context = LocalContext.current
    val ready = remember { mutableStateListOf<Int>() }
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
            .apply {
                setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
            }
    }
    val ids = remember {
        listOf(pool.load(context, R.raw.click, 1), pool.load(context, R.raw.nope, 1))
    }
    DisposableEffect(Unit) { onDispose { pool.release() } }
    return Clicks(pool, ids, ready)
}


/**
 * 손끝의 톡. 카드에 색이 얹히는 그 순간에만 한 번 울린다.
 *
 * 처음에는 컴포즈의 `TextHandleMove` 햅틱을 썼는데 아무것도 울리지 않았다.
 * 기기의 진동 기록(`dumpsys vibrator_manager`)을 보니 그 신호가
 * `ignored_unsupported` 로 버려지고 있었다 — 이 폰이 실제로 지원하는 것은
 * CLICK·TICK 처럼 미리 짜인 효과뿐이다. 그래서 진동기를 직접 불러 그중 가장
 * 가벼운 TICK 을 낸다. 쓰임새를 TOUCH 로 밝혀 두어 기기의 '터치 진동' 세기를
 * 그대로 따른다.
 */
class Buzz(private val vib: Vibrator?) {
    fun tick() {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                v.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(15)
        }
    }
}

@Composable
fun rememberBuzz(): Buzz {
    val context = LocalContext.current
    return remember {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        Buzz(v)
    }
}
