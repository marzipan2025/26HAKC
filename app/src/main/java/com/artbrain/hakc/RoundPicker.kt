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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
 * 첫 화면. 위에 사전 판이 붙박여 있고 아래로 회차 목록이 흐른다.
 *
 * 목록을 내리면 판이 최소로 접힐 때까지 줄고, 그 뒤에야 목록이 움직인다. 거꾸로
 * 올리면 판이 최대까지 자란 뒤 목록이 따라 온다. 사이의 손잡이를 잡아 직접 여닫아도
 * 된다. 최대는 화면의 60%, 최소는 한자 한 줄과 訓音 두 줄이 남는 높이다. 입력 칸에
 * 포커스가 가면 키보드 바로 위까지 자란다.
 */
/** 판과 목록 사이의 손잡이 줄 높이. 키보드 위에 남길 자리를 셈할 때도 쓴다. */
private val HANDLE = 46.dp

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
    val density = LocalDensity.current
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val square = maxWidth - 16.dp                       // 정사각형이었을 때의 한 변

        // 지금 실제로 쓸 수 있는 높이. 키보드가 뜰 때 창이 줄어드는 기기가 있고
        // 인셋만 오는 기기가 있어서 양쪽을 다 빼 둔다 — 한쪽은 늘 0이다.
        val availPx = with(density) { maxHeight.toPx() }
        val keyboard = (WindowInsets.ime.getBottom(density) -
            WindowInsets.navigationBars.getBottom(density)).coerceAtLeast(0)
        val usable = availPx - keyboard

        // 폰의 높이는 여태 받아 본 것 중 가장 큰 값으로 친다. 키보드가 올라와 있는
        // 동안에도 최댓값이 따라 줄어들면 판이 저 혼자 오그라든다.
        var screenPx by remember { mutableFloatStateOf(availPx) }
        LaunchedEffect(usable) { if (usable > screenPx) screenPx = usable }

        val minH = dictMin(square)                          // 한자 한 줄과 訓音 두 줄은 남는다
        val maxH = dictMax(with(density) { screenPx.toDp() })
        val minPx = with(density) { minH.toPx() }
        val maxPx = with(density) { maxH.toPx() }
        var height by remember(square) { mutableFloatStateOf(with(density) { square.toPx() }) }
        LaunchedEffect(minPx, maxPx) { height = height.coerceIn(minPx, maxPx) }

        // 입력 칸에 포커스가 가면 키보드 바로 위까지 키운다 — 손잡이 줄만 남기고
        // 아래 카드는 가린다. 인셋이 한 프레임씩 오므로 키보드가 오르는 대로 따라 붙는다.
        // 60% 를 넘겨서까지 키우지는 않는다.
        var typing by remember { mutableStateOf(false) }
        LaunchedEffect(typing, usable) {
            if (typing && usable < screenPx) {
                height = (usable - with(density) { (HANDLE + 4.dp).toPx() })
                    .coerceIn(minPx, maxPx)
            }
        }

        // 목록의 스크롤을 먼저 판이 받아 먹는다
        val nested = remember(minPx, maxPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy >= 0f) return Offset.Zero        // 내릴 때만 먼저 먹는다
                    val used = (-dy).coerceAtMost(height - minPx)
                    height -= used
                    return Offset(0f, -used)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = available.y
                    if (dy <= 0f) return Offset.Zero        // 목록이 맨 위에 닿은 뒤에 키운다
                    val used = dy.coerceAtMost(maxPx - height)
                    height += used
                    return Offset(0f, used)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (dict != null) {
                Spacer(Modifier.height(4.dp))
                DictPanel(
                    dict,
                    radius = radius,
                    onFocus = { typing = it },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(with(density) { height.toDp() }),
                )
            }

            // 손잡이 줄 — 왼쪽 차림표, 가운데 손잡이, 오른쪽 설정
            Row(
                Modifier.fillMaxWidth().height(HANDLE).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clickable { },     // 아직 열 것이 없다. 자리만.
                    contentAlignment = Alignment.Center,
                ) {
                    Text("☰", fontSize = 20.sp, color = Hak3.TextDim)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { dy ->
                                height = (height + dy).coerceIn(minPx, maxPx)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .background(Hak3.Rule, RoundedCornerShape(2.dp))
                    )
                }
                Box(
                    Modifier.size(40.dp).clickable { settings = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", fontSize = 22.sp, color = Hak3.TextDim)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.nestedScroll(nested),
                contentPadding = PaddingValues(8.dp, 0.dp, 8.dp, 32.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (fresh != null) {
                    item(key = "update") {
                        Cell(
                            radius = radius,
                            big = when {
                                progress == -2f -> "Update"
                                progress < 0f -> "Fetching"
                                progress < 1f -> "${(progress * 100).toInt()}%"
                                else -> "Install"
                            },
                            small = if (progress == -2f) fresh.version else "tap to open",
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
                        Cell(radius, "$words", "words", Hak3.Hanja, true, onWords)
                    }
                }
                if (trouble != null) {
                    item(key = "setup", span = { GridItemSpan(maxLineSpan) }) {
                        Setup(radius, trouble, onFolder, onFile)
                    }
                }
                items(exams, key = { it.round }) { e -> RoundCell(e, radius, onPick) }
            }
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
        Text("No exam data yet", fontSize = 16.sp, color = Hak3.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Put hanja3.db in a 26HAKC folder inside Downloads, then point the app " +
                "at that folder. The dictionary works meanwhile.",
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
                "Choose folder",
                fontSize = 14.sp,
                color = Hak3.Amber,
                modifier = Modifier
                    .border(1.dp, Hak3.Amber, RoundedCornerShape(10.dp))
                    .clickable(onClick = onFolder)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Choose file",
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
                if (live) e.date?.replace('-', '.') ?: "" else "no text",
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
