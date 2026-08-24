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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.zIndex
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
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
/** 판과 목록이 벌어지는 만큼. 기출 상세의 카드와 바닥 줄 사이와 같은 값이다. */
private val GAP = 8.dp

/** 판이 화면 가장자리에서 물러나 있는 만큼. */
private val CARD = 8.dp

/** 평소의 잉크. 아이콘 대신 점 하나만 남겼다 — 예전 21dp 의 40% 다. */
private val DOT = 8.4.dp

/** 그 잉크를 누를 수 있는 자리. 틈보다 크므로 판 위로 걸쳐 앉는다. */
private val TOUCH = 40.dp

/**
 * 키보드가 올라와 있는 동안의 틈. 판이 키보드에 딱 붙으면 잡을 데가 없어 키보드를
 * 내릴 길이 막힌다. 잡는 자리(TOUCH)가 통째로 키보드 위에 오르도록 벌리고, 그
 * 안에 손잡이 막대를 세운다 — 여기서만 보이는 막대다.
 */
private val TYPING = TOUCH + GAP

/**
 * 점의 한가운데가 앉는 자리. 점의 바깥선이 판의 바깥선과 나란히 만난다 —
 * 선 위에 걸터앉히면 반쪽이 판 밖으로 나가 정렬이 아니라 어긋남으로 보인다.
 */
private val TUCK = CARD + DOT / 2

/** 손잡이 막대. 키보드가 올라와 틈이 벌어졌을 때만 선다. */
private val BAR = 38.dp

/** 틈에 박히는 점. 서랍이 나와 있든 아니든 늘 같은 얼굴이다. */
@Composable
private fun Dot() {
    Box(Modifier.size(DOT).background(Hak3.TextDim, CircleShape))
}

@Composable
fun RoundPicker(
    exams: List<ExamRow>,
    db: ExamDb?,
    dict: Dict?,
    built: String?,
    trouble: String?,
    onFolder: () -> Unit,
    onFile: () -> Unit,
    onPick: (Int) -> Unit,
    onWords: (Mark, Collect.Kind) -> Unit,
    startDrawer: Drawer = Drawer.NONE,
    morph: Modifier = Modifier,
    veil: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    // 상세 화면 카드와 같은 곡률
    val radius = (screenCornerRadius() - 8.dp).coerceAtLeast(0.dp)
    // 묶음은 회차의 표시에서 그때그때 모아 낸다. 쌓아 두지 않으므로 표시를
    // 바꾸고 돌아오면 수도 따라 바뀌어 있다. 넷 — 낱글자 둘, 문제 둘.
    val counts = remember(db) {
        Mark.entries.associateWith { bin ->
            Collect.Kind.entries.associateWith { kind ->
                db?.let { Collect.count(context, it, bin, kind) } ?: 0
            }
        }
    }
    // 사전에서 알릴 글자 — 어느 묶음의 글자인지까지 함께 본다
    val bins = remember(db) { db?.let { Collect.bins(context, it) } ?: emptyMap() }

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전
    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }
    val fresh = (status as? Updater.Status.Available)?.release

    // 묶음을 열었다 닫고 돌아오면 서랍이 열린 채로 다시 선다 — 방금 있던 자리다
    var drawer by remember { mutableStateOf(startDrawer) }
    var last by remember { mutableIntStateOf(Settings.lastRound(context)) }
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

        // 키보드 높이는 기기와 자판이 정해지면 늘 같다. 여태 본 것 중 가장 큰
        // 값을 적어 두었다가, 인셋이 오기 전에 판을 얼마나 키울지 미리 셈한다.
        var kb by remember { mutableIntStateOf(Settings.keyboard(context)) }
        LaunchedEffect(keyboard) {
            if (keyboard > kb) {
                kb = keyboard
                Settings.setKeyboard(context, keyboard)
            }
        }

        val minH = dictMin(square)                          // 한자 한 줄과 訓音 두 줄은 남는다
        val maxH = dictMax(with(density) { screenPx.toDp() })
        val minPx = with(density) { minH.toPx() }
        val maxPx = with(density) { maxH.toPx() }
        var height by remember(square) { mutableFloatStateOf(with(density) { square.toPx() }) }
        LaunchedEffect(minPx, maxPx) { height = height.coerceIn(minPx, maxPx) }

        // 입력 칸에 포커스가 가면 판을 키워 목록을 키보드 아래로 완전히 밀어낸다.
        // 벌어진 틈까지가 딱 키보드 위끝이다 — 목록은 한 줄도 올라오지 않고, 틈은
        // 손이 들어갈 만큼 남는다. 인셋이 한 프레임씩 오므로 키보드가 오르는 대로
        // 따라 붙는다.
        //
        // 여기서는 60% 를 넘겨도 좋다 — 그 한계는 손으로 여닫을 때의 것이고, 찾는
        // 동안에는 틈을 키보드 위에 붙이는 쪽이 먼저다.
        var typing by remember { mutableStateOf(false) }
        // 키보드가 올라와 있는 동안만 틈이 벌어진다. 벌어지고 오므라드는 사이에
        // 아래 판이 따라 내려갔다 도로 올라붙는다 — 그 사이가 곧 이 모션이다.
        val bandTo = if (typing) TYPING else GAP
        val band by animateDpAsState(bandTo, QUICK_DP, "band")
        // 막대는 틈이 벌어질 때 배어 나오고, 오므라들 때 스러진다
        val bar by animateFloatAsState(
            if (typing) 1f else 0f,
            tween(DISSOLVE, easing = LinearEasing),
            label = "bar",
        )
        // 다시 걸릴 때 넘겨 줄 속도. 이것이 있어야 이어 달린다.
        var speed by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(typing, usable, bandTo, kb) {
            if (!typing) {
                speed = 0f
                return@LaunchedEffect
            }
            // 인셋은 손가락이 닿고 한 박자 뒤에 온다. 그때까지 기다리면 틈과 막대만
            // 먼저 움직이고 판이 뒤늦게 따라와 뚝 끊겨 보인다. 여태 본 키보드
            // 높이로 지레 셈해, 셋이 같은 순간에 함께 떠나게 한다. 인셋이 오면
            // 그때의 참값으로 갈아타되 속도를 넘겨받아 이어 달린다.
            val room = if (usable < screenPx) usable else screenPx - kb
            if (room >= screenPx) return@LaunchedEffect     // 짐작할 값이 없다
            val goal = (room - with(density) { (bandTo + 4.dp).toPx() })
                .coerceAtLeast(minPx)
            animate(height, goal, speed, QUICK) { v, dv ->
                height = v
                speed = dv
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

        // 서랍은 판을 통째로 밀어내고 그 자리에 선다. 왼쪽 것은 아직 비었고,
        // 오른쪽 것에는 설정이 든다. 남는 판 1/3 은 돌아가는 길로만 쓴다.
        val open = drawer != Drawer.NONE
        val wide = maxWidth                                 // 안쪽 Box 에서는 가려진다
        val roomDp = wide * (1f - STRIP)
        val room = with(density) { roomDp.toPx() }
        val shift = remember { Animatable(0f) }
        LaunchedEffect(drawer, room) { shift.animateTo(anchor(drawer, room), SNAP) }
        if (open) BackHandler { drawer = Drawer.NONE }

        // 단추를 누르지 않고 끌어서도 연다. 손짓은 화면 전체에서 받되, 가로로 넘기는
        // 자리(위 한자 줄)가 먼저 집어 가는 것은 그대로 둔다.
        val drag = rememberDraggableState { dx ->
            scope.launch { shift.snapTo((shift.value + dx).coerceIn(-room, room)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = drag,
                    // 사전에 무언가 적는 중에는 서랍이 끌려 나오지 않는다
                    enabled = !typing,
                    onDragStopped = { v ->
                        val s = shift.value
                        val next = when {
                            v > FLING -> if (s > 0f) Drawer.USER else Drawer.NONE
                            v < -FLING -> if (s < 0f) Drawer.SETTINGS else Drawer.NONE
                            s > room * COMMIT -> Drawer.USER
                            s < -room * COMMIT -> Drawer.SETTINGS
                            else -> Drawer.NONE
                        }
                        // 서랍이 그대로면 LaunchedEffect 가 돌지 않는다. 여기서 앉힌다.
                        if (next != drawer) drawer = next
                        else shift.animateTo(anchor(next, room), SNAP)
                    },
                )
        ) {
        // 밀려난 만큼 어두워진다 — 남은 자락이 지금 쓸 수 없는 것임을 그렇게 알린다.
        // 손잡이 줄의 화살표만 이 층을 벗어나 제 밝기로 선다.
        val dim: GraphicsLayerScope.() -> Unit = {
            alpha = 1f - (1f - DIM) * (abs(shift.value) / room).coerceIn(0f, 1f)
        }
        Box(Modifier.fillMaxSize().offset { IntOffset(shift.value.roundToInt(), 0) }) {
            // 서랍 둘은 판의 양옆에 붙어 함께 밀린다. 제자리에 두면 판이 그 위를
            // 덮고 지나가는데, 덮는 것이 아니라 밀려나야 한다.
            // 왼쪽 서랍 — 단어장이 여기 산다
            Box(Modifier.offset(x = -roomDp).width(roomDp).fillMaxHeight()) {
                // 단어장은 넷이다. 좌우로 색이 갈리고, 위아래로 갈래가 갈린다 —
                // 윗줄은 낱글자, 아랫줄은 문제.
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(4.dp))
                    for (kind in Collect.Kind.entries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (bin in Mark.entries) {
                                val n = counts[bin]?.get(kind) ?: 0
                                Cell(
                                    radius = radius,
                                    big = "$n",
                                    small = if (kind == Collect.Kind.CHARS) "letters" else "cards",
                                    color = if (bin == Mark.AMBER) Hak3.Amber else Hak3.Green,
                                    enabled = n > 0,
                                    modifier = Modifier.weight(1f),
                                ) { onWords(bin, kind) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Box(Modifier.offset(x = wide).width(roomDp).fillMaxHeight()) {
                SettingsPanel(
                    built = built,
                    markOnLeft = markOnLeft,
                    onMarkSide = { left ->
                        markOnLeft = left
                        Settings.setMarkOnLeft(context, left)
                    },
                    // 지운 뒤에는 화면을 처음부터 다시 짓는다 — 여기저기 붙잡아 둔
                    // 수와 표시를 하나씩 되돌리는 것보다 이 편이 틀림없다.
                    onWipe = {
                        Settings.wipe(context)
                        (context as? android.app.Activity)?.recreate()
                    },
                )
            }
        Column(Modifier.fillMaxSize()) {
            if (dict != null) {
                Spacer(Modifier.height(4.dp))
                DictPanel(
                    dict,
                    kept = bins,
                    radius = radius,
                    onFocus = { typing = it },
                    modifier = Modifier
                        .graphicsLayer(dim)
                        .then(veil)
                        .padding(horizontal = 8.dp)
                        .height(with(density) { height.toDp() }),
                )
            }

            // 판과 목록 사이는 8dp 만 벌어진다. 점 둘은 그 틈 한가운데에 서되
            // 판의 좌우 선에 바깥선을 맞춰 앉는다. 틈의 높이에서는 둥근 모서리가
            // 비켜나 있어 걸리지 않는다. 누르는 자리는 틈보다 크므로 층을 올려
            // 판 위로 얹는다.
            Box(Modifier.fillMaxWidth().height(band).zIndex(1f).then(veil)) {
                // 손잡이 막대는 걷었어도 잡는 자리는 남는다. 틈이 좁으니 잡히는
                // 높이만 넉넉히 넓혀 두 판에 걸쳐 둔다. 점 둘은 이 뒤에 서므로
                // 그 자리를 누르는 것은 점이 먼저 가져간다.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .requiredHeight(maxOf(TOUCH, band))
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { dy ->
                                height = (height + dy).coerceIn(minPx, maxPx)
                            },
                            // 틈을 잡으면 입력 칸이 포커스를 놓고 키보드가 내려간다.
                            // 판을 손으로 여닫겠다는 뜻이니 키보드가 남아 있을 까닭이 없다.
                            onDragStarted = {
                                focus.clearFocus()
                                ime?.hide()
                            },
                        )
                )
                // 틈이 벌어져 있을 때만 막대가 선다. 잡을 데가 여기라고 알리는 표시고,
                // 잡아 내리면 키보드가 함께 내려간다.
                if (bar > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(BAR, 4.dp)
                            .alpha(bar)
                            .background(Hak3.Rule, RoundedCornerShape(2.dp))
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .requiredSize(TOUCH)
                        .offset(x = TUCK - TOUCH / 2)
                        .clickable {
                            // 적는 중이었으면 키보드가 내려가며 함께 움직인다
                            if (typing) { focus.clearFocus(); ime?.hide() }
                            drawer = if (drawer == Drawer.USER) Drawer.NONE else Drawer.USER
                        },
                    contentAlignment = Alignment.Center,
                ) { Dot() }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .requiredSize(TOUCH)
                        .offset(x = TOUCH / 2 - TUCK)
                        .clickable {
                            if (typing) { focus.clearFocus(); ime?.hide() }
                            drawer = if (drawer == Drawer.SETTINGS) Drawer.NONE else Drawer.SETTINGS
                        },
                    contentAlignment = Alignment.Center,
                ) { Dot() }
            }

            // 새 판 안내는 목록과 같은 얼굴로, 그러나 제 영역에 따로 선다
            if (fresh != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(veil)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer(dim)
                        .background(Hak3.Surface, RoundedCornerShape(radius))
                ) {
                    UpdateRow(
                        label = when {
                            progress == -2f -> "Update"
                            progress < 0f -> "Fetching"
                            progress < 1f -> "${(progress * 100).toInt()}%"
                            else -> "Install"
                        },
                        note = if (progress == -2f) fresh.version else "tap to open",
                        enabled = progress == -2f,
                    ) {
                        val url = fresh.apkUrl
                        if (url == null) {
                            Updater.openReleasesPage(context)
                            return@UpdateRow
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
                // 위로 벌어진 틈과 같은 간격으로 아래도 띄운다
                Spacer(Modifier.height(GAP))
            }

            // 회차는 칸을 늘어놓지 않고 큰 판 하나 안에서 굴러간다
            val rounds = rememberLazyListState()
            LaunchedEffect(exams.size) {
                // 마지막으로 열어 본 회차가 보이게. 맨 위에 붙이지 않고 두 줄쯤 내려서 앉힌다.
                val i = exams.indexOfFirst { it.round == last }
                if (i >= 0) rounds.scrollToItem((i - 2).coerceAtLeast(0))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .graphicsLayer(dim)
                    .then(morph)
                    .background(Hak3.Surface, RoundedCornerShape(radius))
            ) {
                // 목록이 키보드 밑으로 다 내려간 뒤에야 알맹이를 비운다. 판이 딱
                // 맞아떨어지지 않아 한 줄쯤 삐져나올 때가 있는데, 그때 글자가 반쯤
                // 잘려 보이느니 판 색 한 겹으로 서는 편이 낫다. 내려가는 동안까지
                // 비우면 미끄러지기도 전에 글자가 사라져 버린다.
                val sunk = typing &&
                    with(density) { (4.dp + band).toPx() } + height >= usable
                if (!sunk) LazyColumn(
                    Modifier.fillMaxSize().then(veil).nestedScroll(nested),
                    state = rounds,
                    contentPadding = PaddingValues(vertical = 18.dp),
                ) {
                    if (trouble != null) {
                        item(key = "setup") { Setup(radius, trouble, onFolder, onFile) }
                    }
                    items(exams, key = { it.round }) { e ->
                        RoundRow(e, e.round == last, wide / 3) {
                            last = it
                            Settings.setLastRound(context, it)
                            onPick(it)
                        }
                    }
                }
            }
        }

        // 밀려나 있는 동안 판은 손짓을 받지 않는다. 손잡이 줄만 비워 두어 화살표로
        // 돌아갈 수 있게 하고, 나머지는 이 층이 다 삼킨다. 옆으로 쓸면 닫힌다.
        if (open) Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { drawer = Drawer.NONE } }
        )
        }
        }
    }
}

/** 진행 눈금의 두께. 실선 한 줄만큼만 남긴다. */
private val GAUGE = 1.dp

/** 눈금의 바탕. 경계선에서 한 겹 더 물러난다. */
private val GAUGE_TRACK = Hak3.Rule.copy(alpha = Hak3.Rule.alpha / 2)

/** 차오르는 쪽. 다 차기 전까지는 물러나 있다. */
private val GAUGE_FILL = Hak3.HanjaDim.copy(alpha = Hak3.HanjaDim.alpha * 0.6f)

/** 끝까지 간 것. 흰빛이되 반만 — 온전한 흰색은 이 자리에 너무 세다. */
private val GAUGE_DONE = Color.White.copy(alpha = 0.5f)

/** 회차 숫자를 알약 한가운데로 내리는 값. 잉크의 가운데를 재어 잡았다. */
private val INK = 2.dp

/** 번호의 글자 상자 위끝에서 잉크 위끝까지. 옆의 수를 그 선에 맞출 때 쓴다. */
private val SHOULDER = 5.4.dp

/** 번호 옆의 작은 수. 줄 상자를 글자에 바짝 붙여 위아래 여백을 없앤다. */
private val COUNT = TextStyle(
    fontSize = 13.sp,
    lineHeight = 11.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** 서랍이 저마다 앉는 자리. */
private fun anchor(drawer: Drawer, room: Float) = when (drawer) {
    Drawer.USER -> room
    Drawer.SETTINGS -> -room
    Drawer.NONE -> 0f
}

/** 서랍이 열렸을 때 남는 판의 자락. 돌아가는 길로만 쓰는 자리라 좁게 둔다. */
private const val STRIP = 0.15f

/** 여기까지 끌면 놓아도 그쪽으로 앉는다. 자락이 좁아진 만큼 문턱도 낮췄다. */
private const val COMMIT = 0.22f

/** 이만큼 빠르게 튕기면 끌린 거리와 상관없이 그쪽으로 앉힌다. (px/s) */
private const val FLING = 400f

/** 밀려난 판의 밝기. */
private const val DIM = 0.48f

/**
 * 판이 자라고 틈이 벌어지는 결. 튕김 없이 곧장 붙는다.
 *
 * 트윈이 아니라 스프링이다. 키보드 인셋은 한 프레임씩 오고 그때마다 애니메이션이
 * 다시 걸리는데, 트윈은 걸릴 때마다 처음의 느린 구간으로 되돌아가 한참 멈칫하는
 * 것처럼 보였다. 스프링에 직전의 속도를 넘겨 주면 그 자리에서 이어 달린다.
 */
private val QUICK = spring<Float>(dampingRatio = 1f, stiffness = 1200f)

/** 틈이 쓰는 같은 결. */
private val QUICK_DP = spring<Dp>(dampingRatio = 1f, stiffness = 1200f)

/** 막대가 배어 나오고 스러지는 결. 자리가 잡히는 것보다 조금 빠르다. */
private const val DISSOLVE = 120

/** 서랍이 앉는 결. 튕기지 않으면서 짧게 끊어 붙는다. 붙는 힘을 좀 더 주었다. */
private val SNAP = spring<Float>(dampingRatio = 0.9f, stiffness = 2800f)

/** 서랍이 문 자리. 왼쪽은 아직 비었다. */
enum class Drawer { NONE, USER, SETTINGS }

/** 기출 데이터를 아직 못 읽었을 때 목록 자리에 서는 카드. 사전은 그동안에도 쓴다. */
@Composable
private fun Setup(radius: Dp, trouble: String, onFolder: () -> Unit, onFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .padding(20.dp),
    ) {
        Text("No exam data yet", fontSize = 20.sp, color = Hak3.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Put hanja3.db in a 26HAKC folder inside Downloads, then point the app " +
                "at that folder. The dictionary works meanwhile.",
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = Hak3.TextSoft,
        )
        if (trouble.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(trouble, fontSize = 15.sp, color = Hak3.Red)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Text(
                "Choose folder",
                fontSize = 15.sp,
                color = Hak3.Amber,
                modifier = Modifier
                    .border(1.dp, Hak3.Amber, RoundedCornerShape(10.dp))
                    .clickable(onClick = onFolder)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Choose file",
                fontSize = 15.sp,
                color = Hak3.TextDim,
                modifier = Modifier
                    .border(1.dp, Hak3.Rule, RoundedCornerShape(10.dp))
                    .clickable(onClick = onFile)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 묶음 안의 카드와 같이 앞면이 통째로 그 색이고, 그 위의 글은 검정이다.
    // 비어 있는 묶음은 색을 죽여 지금 열 것이 없음을 알린다.
    val face = if (enabled) color else Hak3.Knob
    val ink = if (enabled) Color.Black else Hak3.TextDim
    Column(
        modifier
            .fillMaxWidth()
            .background(face, RoundedCornerShape(radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 한자가 아니므로 회차 줄과 같은 코레일체로. 얇은 한자 서체는 큰 한자에만 쓴다.
        Text(
            big,
            fontFamily = Korail,
            fontWeight = FontWeight.Light,
            fontSize = 40.sp,
            color = ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            small,
            fontSize = 13.sp,
            color = ink.copy(alpha = if (enabled) 0.55f else 1f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 회차 한 줄. 번호를 크게 적고 담아 둔 수를 그 어깨에 붙인다.
 * 마지막으로 열어 본 줄에는 알약을 두른다.
 */
@Composable
private fun RoundRow(e: ExamRow, on: Boolean, pill: Dp, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val counts = remember(e.round) { Marks.counts(context, e.round) }
    // 그 회차에서 마지막으로 보고 있던 문항. 한 번도 안 들어갔으면 0 이다.
    val seen = remember(e.round) { Marks.lastSeen(context, e.round) }
    val live = e.complete || e.items > 0
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = live) { onPick(e.round) }
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 알약은 글자를 감싸는 것이 아니라 화면 가운데 1/3 을 차지한다
        Row(
            Modifier
                .width(pill)
                .background(if (on) Hak3.Rule else Color.Transparent, CircleShape)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            // 숫자는 글자 상자 안에서 위로 쏠려 앉는다(내림자가 없다). 잰 만큼
            // 내려 잉크의 가운데를 알약의 가운데에 맞춘다. 자리는 그대로다 —
            // offset 은 그리는 자리만 옮기므로 알약 높이가 흔들리지 않는다.
            Text(
                "${e.round}",
                fontFamily = Korail,
                fontWeight = FontWeight.Light,
                fontSize = 44.sp,
                color = if (live) Hak3.Hanja else Hak3.HanjaDim,
                modifier = Modifier.offset(y = INK),
            )
            Spacer(Modifier.width(7.dp))
            // 담아 둔 수는 번호의 윗선에 맞춘다 — 한 개든 두 개든 같은 자리에서
            // 시작한다. 둘일 때는 아래 것을 3dp 끌어올려 한 덩이로 보이게 한다.
            Column(Modifier.offset(y = INK).padding(top = SHOULDER)) {
                // 줄 상자를 글자에 바짝 붙여 둘이 한 덩이로 보이게 한다
                if (counts.amber > 0) {
                    Text("${counts.amber}", style = COUNT, color = Hak3.Amber)
                }
                if (counts.known > 0) {
                    Text("${counts.known}", style = COUNT, color = Hak3.Green)
                }
                if (!live) Text("no text", fontSize = 13.sp, color = Hak3.TextDim)
            }
        }

        // 어디까지 갔는지를 알약 오른쪽에 눈금 하나로. 알약 끝에서 카드 벽까지의
        // 자리에서 25% 부터 65% 까지를 쓴다 — 알약에도 벽에도 붙지 않는다.
        // 한 번도 들어가지 않은 회차에는 눈금 자체가 서지 않는다.
        if (seen > 0 && e.items > 0) {
            val room = (maxWidth - pill) / 2
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = room * 0.35f)
                    .width(room * 0.40f)
                    .height(GAUGE)
                    .background(GAUGE_TRACK, CircleShape)
            ) {
                // 끝까지 간 회차만 흰빛으로 선다 — 다 봤다는 말은 그만한 값이다
                val done = seen >= e.items
                Box(
                    Modifier
                        .fillMaxWidth((seen.toFloat() / e.items).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (done) GAUGE_DONE else GAUGE_FILL, CircleShape)
                )
            }
        }
    }
}

/** 새 판 안내. 회차 줄과 같은 얼굴로 제 영역에 선다. */
@Composable
private fun UpdateRow(label: String, note: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(label, fontFamily = Korail, fontSize = 44.sp, color = Hak3.Amber, maxLines = 1)
            Spacer(Modifier.width(7.dp))
            Text(
                note,
                fontSize = 13.sp,
                color = Hak3.Amber,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}
