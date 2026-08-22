package com.artbrain.hakc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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

@Composable
private fun Root() {
    val context = LocalContext.current
    var db by remember { mutableStateOf<ExamDb?>(null) }
    var state by remember { mutableStateOf<DataFile.Result?>(null) }
    var reload by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf(false) }
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
        db?.let { Collect.seed(context, it) }
    }

    val ready = db
    val round = open
    when {
        words && ready != null -> {
            BackHandler { words = false }
            WordScreen(ready) { words = false }
        }
        round != null && ready != null -> {
            BackHandler { open = null }
            ExamScreen(round, ready) { open = null }
        }
        else -> RoundPicker(
            // 사전은 기출 데이터가 없어도 선다 — 앱 안에 든 자료라 남을 기다릴 것이 없다
            exams = ready?.exams() ?: emptyList(),
            dict = book,
            built = ready?.meta()?.get("built"),
            trouble = if (ready == null) trouble(state) else null,
            onFolder = { pickFolder.launch(null) },
            onFile = { pickFile.launch(arrayOf("*/*")) },
            onPick = { open = it },
            onWords = { words = true },
        )
    }
}

/** 데이터 파일을 못 읽었을 때 무엇이 잘못됐는지. 아직 고르지 않았으면 null. */
private fun trouble(state: DataFile.Result?): String = when (state) {
    DataFile.Result.NoFolder, null -> ""
    DataFile.Result.NoFile ->
        "그 폴더에서 ${DataFile.PREFIX}…${DataFile.SUFFIX} 파일을 찾지 못했습니다."
    is DataFile.Result.Failed -> state.why
    is DataFile.Result.Ok -> "파일을 읽었지만 기출 데이터 형식이 아닙니다."
}
