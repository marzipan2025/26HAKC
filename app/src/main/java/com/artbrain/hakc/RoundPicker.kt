package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 첫 화면. 맨 위에 사전 판이 앉고, 그 아래 이름·판 번호 줄, 그 아래로 회차 목록이 붙는다.
 * 셋을 한 격자 안에 넣어 두었으므로 화면 전체가 함께 움직인다.
 */
@Composable
fun RoundPicker(
    exams: List<ExamRow>,
    dict: Dict?,
    built: String?,
    trouble: String?,
    onFolder: () -> Unit,
    onFile: () -> Unit,
    onPick: (Int) -> Unit,
    onWords: () -> Unit,
) {
    val context = LocalContext.current
    // 상세 화면 카드와 같은 곡률
    val radius = (screenCornerRadius() - 8.dp).coerceAtLeast(0.dp)
    val words = remember { Collect.size(context) }

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전
    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }
    val fresh = (status as? Updater.Status.Available)?.release

    var settings by remember { mutableStateOf(false) }
    var markOnLeft by remember { mutableStateOf(Settings.markOnLeft(context)) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (dict != null) {
                item(key = "dict", span = { GridItemSpan(maxLineSpan) }) {
                    DictPanel(dict, radius)
                }
            }
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Header(built) { settings = true }
            }
            if (fresh != null) {
                item(key = "update") {
                    Cell(
                        radius = radius,
                        big = when {
                            progress == -2f -> "새 판"
                            progress < 0f -> "받는 중"
                            progress < 1f -> "${(progress * 100).toInt()}%"
                            else -> "설치"
                        },
                        small = if (progress == -2f) fresh.version else "누르면 열립니다",
                        color = Hak3.Amber,
                        enabled = progress == -2f,
                    ) {
                        val url = fresh.apkUrl
                        if (url == null) {
                            Updater.openReleasesPage(context)
                            return@Cell
                        }
                        progress = -1f
                        scope.launch {
                            val apk = Updater.download(context, url, fresh.version) { progress = it }
                            if (apk == null) {
                                progress = -2f
                                Updater.openReleasesPage(context)
                            } else {
                                progress = 1f
                                Updater.install(context, apk)
                            }
                        }
                    }
                }
            }
            if (words > 0) {
                item(key = "words") {
                    Cell(radius, "$words", "단어장", Hak3.Hanja, true, onWords)
                }
            }
            if (trouble != null) {
                item(key = "setup", span = { GridItemSpan(maxLineSpan) }) {
                    Setup(radius, trouble, onFolder, onFile)
                }
            }
            items(exams, key = { it.round }) { e -> RoundCell(e, radius, onPick) }
        }

        if (settings) {
            BackHandler { settings = false }
            SettingsPanel(
                built = built,
                markOnLeft = markOnLeft,
                onMarkSide = { left ->
                    markOnLeft = left
                    Settings.setMarkOnLeft(context, left)
                },
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(40.dp)
                    .clickable { settings = false },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 18.sp, color = Hak3.Text)
            }
        }
    }
}

/** 기출 데이터를 아직 못 읽었을 때 목록 자리에 서는 카드. 사전은 그동안에도 쓴다. */
@Composable
private fun Setup(radius: Dp, trouble: String, onFolder: () -> Unit, onFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .padding(20.dp),
    ) {
        Text("기출 데이터가 아직 없습니다", fontSize = 16.sp, color = Hak3.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "다운로드 폴더에 26HAKC 폴더를 만들고 그 안에 hanja3.db 를 둔 뒤, " +
                "그 폴더를 지정해 주세요. 사전은 그동안에도 쓸 수 있습니다.",
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = Hak3.TextDim,
        )
        if (trouble.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(trouble, fontSize = 13.sp, color = Hak3.Red)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Text(
                "폴더 지정",
                fontSize = 14.sp,
                color = Hak3.Amber,
                modifier = Modifier
                    .border(1.dp, Hak3.Amber, RoundedCornerShape(10.dp))
                    .clickable(onClick = onFolder)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "파일 고르기",
                fontSize = 14.sp,
                color = Hak3.TextDim,
                modifier = Modifier
                    .border(1.dp, Hak3.Rule, RoundedCornerShape(10.dp))
                    .clickable(onClick = onFile)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

/** 이름과 판 번호, 그리고 설정으로 드는 톱니. 사전 판과 회차 목록 사이에 선다. */
@Composable
private fun Header(built: String?, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("26HAKC", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Hak3.Text)
            Text(
                "version ${BuildConfig.VERSION_NAME}" + (built?.let { " · 데이터 $it" } ?: ""),
                fontSize = 13.sp,
                color = Hak3.TextDim,
            )
        }
        Box(
            Modifier.size(40.dp).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", fontSize = 24.sp, color = Hak3.TextDim)
        }
    }
}

/** 회차 칸과 같은 얼굴을 한 칸. 큰 줄과 작은 줄만 밖에서 정한다. */
@Composable
private fun Cell(
    radius: Dp,
    big: String,
    small: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(radius))
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            big,
            fontFamily = ThinHanja,
            fontWeight = FontWeight.Thin,
            fontSize = 40.sp,
            color = color,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(small, fontSize = 13.sp, color = Hak3.TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RoundCell(e: ExamRow, radius: Dp, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val counts = remember(e.round) { Marks.counts(context, e.round) }
    val live = e.complete || e.items > 0
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hak3.Rule, RoundedCornerShape(radius))
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .clickable(enabled = live) { onPick(e.round) }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${e.round}",
            fontFamily = ThinHanja,
            fontWeight = FontWeight.Thin,
            fontSize = 40.sp,
            color = if (live) Hak3.Hanja else Hak3.HanjaDim,
        )
        Spacer(Modifier.height(6.dp))
        // 갈라 둔 게 있으면 날짜 자리에 그 개수를 대신 적는다 — 셀 높이는 늘 같다
        if (counts.any) {
            // 노랑 몇 · 초록 몇. 한쪽만 있으면 빗금도 없다.
            Text(
                buildAnnotatedString {
                    if (counts.amber > 0) {
                        withStyle(SpanStyle(color = Hak3.Amber)) { append("${counts.amber}") }
                    }
                    if (counts.amber > 0 && counts.known > 0) {
                        withStyle(SpanStyle(color = Hak3.TextDim)) { append(" / ") }
                    }
                    if (counts.known > 0) {
                        withStyle(SpanStyle(color = Hak3.Green)) { append("${counts.known}") }
                    }
                },
                fontSize = 13.sp,
            )
        } else {
            Text(
                if (live) e.date?.replace('-', '.') ?: "" else "본문 없음",
                fontSize = 11.sp,
                color = Hak3.TextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}


/**
 * 판 번호를 적어 두고, 새 판이 있으면 눌러서 받도록 한다.
 * 받은 뒤에는 시스템 설치 화면이 뜨고, 설치할지는 거기서 사용자가 정한다.
 */
