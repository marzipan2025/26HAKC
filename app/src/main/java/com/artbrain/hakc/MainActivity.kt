package com.artbrain.hakc

import android.os.Bundle
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Root() {
    val context = LocalContext.current
    var db by remember { mutableStateOf<ExamDb?>(null) }
    var state by remember { mutableStateOf<DataFile.Result?>(null) }
    var reload by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf<Pair<Mark, Collect.Kind>?>(null) }
    // 묶음을 닫고 돌아오면 왼쪽 서랍이 열린 채로 선다 — 방금 있던 자리다.
    // 한 번 쓰고 잊는다. 그러지 않으면 회차를 보고 돌아올 때도 서랍이 열린다.
    var back by remember { mutableStateOf(Drawer.NONE) }
    val book = remember { Dict.open(context) }

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
    when {
        words != null && ready != null -> {
            BackHandler { words = null; back = Drawer.USER }
            WordScreen(ready, words!!.first, words!!.second) {
                words = null
                back = Drawer.USER
            }
        }
        // 목록과 상세는 판 하나를 나눠 갖는다. 회차를 누르면 목록의 판이 상세의
        // 카드 자리까지 늘어나고, 나머지는 그동안 지워졌다가 뒤이어 떠오른다.
        else -> SharedTransitionLayout {
            AnimatedContent(
                targetState = if (ready == null) null else round,
                // 화면째 흐려지면 판까지 같이 흐려진다. 지우고 띄우는 일은
                // 조각마다 따로 맡긴다.
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                label = "round",
            ) { r ->
                val morph = Modifier.sharedBounds(
                    rememberSharedContentState("card"),
                    animatedVisibilityScope = this@AnimatedContent,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = { _, _ -> tween(GROW, easing = FastOutSlowInEasing) },
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
                // 나가는 것이 다 지워진 뒤에 들어오는 것이 뜬다
                val veil = Modifier.animateEnterExit(
                    enter = fadeIn(tween(WIPE, delayMillis = WIPE + 40)),
                    exit = fadeOut(tween(WIPE)),
                )
                if (r == null || ready == null) {
                    Picker(state, reload, ready, book, pickFolder, pickFile, morph, veil,
                        onPick = { open = it }, onWords = { bin, kind -> words = bin to kind },
                        startDrawer = back)
                    LaunchedEffect(Unit) { back = Drawer.NONE }
                } else {
                    BackHandler { open = null }
                    // 판은 넘어오는 동안에만 그린다. 뒤에 남겨 두면 카드를
                    // 들췄을 때 그 뒤로 비쳐 카드가 겹쳐 있는 것처럼 보인다.
                    ExamScreen(r, ready, morph, veil, { if (isTransitionActive) 1f else 0f }) {
                        open = null
                    }
                }
            }
        }
    }
}

/** 지우고 띄우는 데 걸리는 참, 그리고 판이 늘어나는 참. (ms) */
private const val WIPE = 150
private const val GROW = 340

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
    onWords: (Mark, Collect.Kind) -> Unit,
    startDrawer: Drawer,
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
        startDrawer = startDrawer,
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
