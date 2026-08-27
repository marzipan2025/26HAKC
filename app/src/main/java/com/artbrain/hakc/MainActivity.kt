package com.artbrain.hakc

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holdSplash()
        setContent {
            Hak3Theme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Hak3.Ground)
                        .systemBarsPadding()
                ) {
                    Root()
                }
            }
        }
    }

    /**
     * 첫 화면을 잠깐 붙잡아 둔다. 앱이 순식간에 서면 아이콘이 번쩍하고 지나가
     * 무엇이 지나갔는지 눈에 남지 않는다.
     *
     * 그리기 직전에 묻는 이에게 아직이라고 답하면 시스템이 첫 화면을 거두지 않는다.
     * 때가 되면 깨워서 다시 그리게 한다 — 그냥 기다리기만 하면 다음 프레임이
     * 오지 않아 영영 서 있을 수 있다.
     */
    private fun holdSplash() {
        val root = findViewById<View>(android.R.id.content)
        var ready = false
        root.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!ready) return false
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        )
        root.postDelayed({ ready = true; root.invalidate() }, HOLD)
    }
}

/** 첫 화면을 붙잡아 두는 참. (ms) */
private const val HOLD = 640L

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Root() {
    val context = LocalContext.current
    var db by remember { mutableStateOf<ExamDb?>(null) }
    var state by remember { mutableStateOf<DataFile.Result?>(null) }
    var reload by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf<Triple<Mark, Collect.Kind, Int>?>(null) }
    val dict = remember { Dict.open(context) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            DataFile.remember(context, uri, "tree")
            reload++
        }
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            DataFile.remember(context, uri, "file")
            reload++
        }
    }

    LaunchedEffect(reload) {
        val r = DataFile.sync(context)
        state = r
        db?.close()
        db = (r as? DataFile.Result.Ok)?.let { ExamDb.open(it.file) }
        // 단어장을 만들기 전에 찍어 둔 노랑도 살려 둔다
    }

    val ready = db
    val round = open
    val book = words
    // 지금 어느 자리에 서 있는가. 셋 다 판 하나를 나눠 가지므로 한 자리에서 갈린다.
    val where = when {
        ready == null -> Where.List
        book != null -> Where.Book(book.first, book.second, book.third)
        round != null -> Where.Round(round)
        else -> Where.List
    }
    // 목록과 상세는 판 하나를 나눠 갖는다. 회차를 누르면 목록의 판이 상세의
    // 카드 자리까지 늘어나고, 나머지는 그동안 지워졌다가 뒤이어 떠오른다.
    // 단어장도 같은 길로 연다 — 어깨의 단추에서 눌러도 판이 그렇게 늘어난다.
    SharedTransitionLayout {
            AnimatedContent(
                targetState = where,
                // 화면째 흐려지면 판까지 같이 흐려진다. 지우고 띄우는 일은
                // 조각마다 따로 맡긴다.
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                label = "where",
            ) { w ->
                val morph = Modifier.sharedBounds(
                    rememberSharedContentState("card"),
                    animatedVisibilityScope = this@AnimatedContent,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = { _, _ -> tween(GROW, easing = FastOutSlowInEasing) },
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    // 넘어가는 동안 둘이 같은 자리에 겹친다. 목록의 판을 아래에
                    // 두어야 카드 쪽 판이 색을 바꾸는 것이 보인다 — 위에 있는
                    // 것만 눈에 들기 때문이다.
                    zIndexInOverlay = if (w is Where.List) 0f else 1f,
                )
                // 나가는 것이 다 지워진 뒤에 들어오는 것이 뜬다
                val veil = Modifier.animateEnterExit(
                    enter = fadeIn(tween(WIPE, delayMillis = WIPE + 40)),
                    exit = fadeOut(tween(WIPE)),
                )
                when {
                    w is Where.Book && ready != null -> {
                        BackHandler { words = null }
                        WordScreen(
                            ready, w.bin, w.kind, w.start,
                            morph, veil, { if (isTransitionActive) 1f else 0f },
                            // 이 조각이 지금 자리를 내주는 중인가
                            leaving = w != where,
                        ) { words = null }
                    }
                    w is Where.Round && ready != null -> {
                        BackHandler { open = null }
                        // 판은 넘어오는 동안에만 그린다. 뒤에 남겨 두면 카드를
                        // 들췄을 때 그 뒤로 비쳐 카드가 겹쳐 있는 것처럼 보인다.
                        ExamScreen(
                            w.no, ready, morph, veil,
                            { if (isTransitionActive) 1f else 0f },
                            // 이 조각이 지금 자리를 내주는 중인가
                            leaving = w != where,
                        ) { open = null }
                    }
                    else -> {
                        Picker(state, reload, ready, dict, pickFolder, pickFile, morph, veil,
                            onPick = { open = it },
                            onWords = { bin, kind, at -> words = Triple(bin, kind, at) })
                    }
                }
            }
    }
}

/** 지금 서 있는 자리. 목록이거나, 한 회차이거나, 단어장 한 묶음이다. */
private sealed interface Where {
    data object List : Where
    data class Round(val no: Int) : Where
    /** [start] 는 묶음에서 먼저 펴 볼 자리. 어깨의 등에서 눌러 오면 그 글자다. */
    data class Book(val bin: Mark, val kind: Collect.Kind, val start: Int = 0) : Where
}

/** 지우고 띄우는 데 걸리는 참, 그리고 판이 늘어나는 참. (ms) */
private const val WIPE = 150

/** 판이 카드 자리까지 늘어나는 참. 판의 색이 바뀌는 것도 이 시간을 함께 쓴다. */
const val GROW = 340

@Composable
private fun Picker(
    state: DataFile.Result?,
    reload: Int,
    ready: ExamDb?,
    book: Dict?,
    pickFolder: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    pickFile: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    morph: Modifier,
    veil: Modifier,
    onPick: (Int) -> Unit,
    onWords: (Mark, Collect.Kind, Int) -> Unit,
) {
    RoundPicker(
        // 사전은 기출 데이터가 없어도 선다 — 앱 안에 든 자료라 남을 기다릴 것이 없다
        exams = ready?.exams() ?: emptyList(),
        db = ready,
        dict = book,
        built = ready?.meta()?.get("built"),
        trouble = if (ready == null) trouble(state) else null,
        onFolder = { pickFolder.launch(null) },
        onFile = { pickFile.launch(arrayOf("*/*")) },
        onPick = onPick,
        onWords = onWords,
        morph = morph,
        veil = veil,
    )
}

/** 데이터 파일을 못 읽었을 때 무엇이 잘못됐는지. 아직 고르지 않았으면 null. */
private fun trouble(state: DataFile.Result?): String = when (state) {
    DataFile.Result.NoFolder, null -> ""
    DataFile.Result.NoFile ->
        "No ${DataFile.PREFIX}…${DataFile.SUFFIX} in that folder."
    is DataFile.Result.Failed -> state.why
    is DataFile.Result.Ok -> "That file is not exam data."
}
