package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.foundation.layout.defaultMinSize
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
/** 판과 목록이 벌어지는 만큼. */
private val GAP = 6.dp

/** 판이 화면 가장자리에서 물러나 있는 만큼. */
private val CARD = 8.dp

/** 두 판 사이에서 잡을 수 있는 자리. 틈보다 크므로 판 위로 걸쳐 앉는다. */
private val TOUCH = 40.dp

/**
 * 키보드가 올라와 있는 동안의 틈. 판이 키보드에 딱 붙으면 잡을 데가 없어 키보드를
 * 내릴 길이 막힌다. 잡는 자리(TOUCH)가 통째로 키보드 위에 오르도록 벌리고, 그
 * 안에 손잡이 막대를 세운다 — 여기서만 보이는 막대다.
 */
private val TYPING = TOUCH + GAP

/** 손잡이 막대. 키보드가 올라와 틈이 벌어졌을 때만 선다. */
private val BAR = 38.dp

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
    onWords: (Mark, Collect.Kind, Int) -> Unit,
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
    // 어깨의 등에 띄울 글자. 못 외운 낱글자가 열 자를 넘으면 그 안에서 뽑아 제
    // 글자를 돌려 보이고, 아직 몇 자 안 되면 3급 배정한자 전체에서 뽑는다.
    val pink = remember(db) { db?.let { Collect.list(context, it, Mark.AMBER) } ?: emptyList() }
    val pool = remember(pink, dict) {
        if (pink.size > LANTERN_MIN) pink else dict?.grade3 ?: emptyList()
    }
    // 핑크 묶음에 담긴 것이 하나라도 있는가 — 낱글자든 문제든.
    val hasPink = (counts[Mark.AMBER]?.values?.sum() ?: 0) > 0

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전
    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }
    val fresh = (status as? Updater.Status.Available)?.release

    // 묶음을 열었다 닫고 돌아오면 서랍이 열린 채로 다시 선다 — 방금 있던 자리다
    var drawer by remember { mutableStateOf(Drawer.NONE) }
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
        // 앱을 열면 사전 판이 다 펼쳐진 채로 선다. 목록을 굴리면 그때부터 줄어든다.
        var height by remember(square) { mutableFloatStateOf(with(density) { maxH.toPx() }) }
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

        // 서랍은 가로로 끌어 열지 않는다. 판 왼쪽 아래의 설정 문으로만 열고,
        // 열린 뒤에는 남은 자락을 눌러 닫는다 — 드나드는 길이 하나뿐이라야
        // 사전을 적다가 손이 옆으로 스쳐도 판이 밀려나지 않는다.
        Box(Modifier.fillMaxSize()) {
        // 밀려난 만큼 어두워진다 — 남은 자락이 지금 쓸 수 없는 것임을 그렇게 알린다.
        // 손잡이 줄의 화살표만 이 층을 벗어나 제 밝기로 선다.
        val dim: GraphicsLayerScope.() -> Unit = {
            alpha = 1f - (1f - DIM) * (abs(shift.value) / room).coerceIn(0f, 1f)
        }
        Box(Modifier.fillMaxSize().offset { IntOffset(shift.value.roundToInt(), 0) }) {
            // 서랍 둘은 판의 양옆에 붙어 함께 밀린다. 제자리에 두면 판이 그 위를
            // 덮고 지나가는데, 덮는 것이 아니라 밀려나야 한다.
            // 서랍은 왼쪽 하나뿐이다 — 오른쪽에 있던 설정이 이리로 옮겨 왔다.
            // 자리는 비워 두고, 그 안에서 덩이 둘이 제 면을 갖는다. 면의 색은
            // 밀려난 판이 어두워진 그 색이다 — 검은 바탕 위에 얹히므로 판에
            // 씌우는 것과 같은 알파를 주면 오른쪽 자락과 꼭 같은 색이 된다.
            Box(Modifier.offset(x = -roomDp).width(roomDp).fillMaxHeight()) {
                SettingsPanel(
                    radius = radius,
                    face = Hak3.Card.copy(alpha = DIM),
                    head = with(density) { height.toDp() },
                    gap = band,
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
            }

            // 새 판 안내는 목록과 같은 얼굴로, 그러나 제 영역에 따로 선다
            if (fresh != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(veil)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer(dim)
                        // 아래 판과 같은 얼굴이다 — 회차 칸과 같은 꼴로 서되
                        // 제 영역에 따로 선다
                        .background(Hak3.Card, RoundedCornerShape(radius))
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

            // 회차는 칸을 늘어놓지 않고 큰 판 하나 안에서 굴러간다. 열면 늘
            // 맨 위 — 가장 최근 회차부터다. 마지막으로 열어 본 회차를 찾아가
            // 앉히던 것은 걷었다: 어디에 서 있는지 알 수 없게 어중간히 굴러
            // 있었고, 그 회차가 어느 줄인지는 흰 번호가 이미 말해 준다.
            val rounds = rememberLazyListState()
            // 오른쪽 장식의 c 는 설정 문과 아랫선을 맞춘다. 문의 자리는 등이 섰는지,
            // 단추의 수가 몇인지에 따라 달라지므로 재어서 따라간다.
            var panelTop by remember { mutableFloatStateOf(0f) }
            var doorBottom by remember { mutableFloatStateOf(0f) }
            // 아래는 판 하나다. 단어장 넷이 왼쪽 어깨에 얹히고, 회차가 그 아래로
            // 굴러간다. 늘어나 카드가 되는 것도 이 판이다.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .graphicsLayer(dim)
                    .then(morph)
                    .background(Hak3.Card, RoundedCornerShape(radius))
                    // 위 판과 같은 빛 한 겹, 세기는 그 절반 — 판이 위에서 조금
                    // 들린 것처럼 보인다
                    .background(CardGlow, RoundedCornerShape(radius))
                    .onGloballyPositioned { panelTop = it.positionInRoot().y }
            ) {
                // 목록이 키보드 밑으로 다 내려간 뒤에야 알맹이를 비운다. 판이 딱
                // 맞아떨어지지 않아 한 줄쯤 삐져나올 때가 있는데, 그때 글자가 반쯤
                // 잘려 보이느니 판 색 한 겹으로 서는 편이 낫다.
                val sunk = typing &&
                    with(density) { (4.dp + band).toPx() } + height >= usable


                // 왼쪽 어깨 — 등 하나와 단추 넷. 단추를 빗나간 손짓은 이 자리에서
                // 삼킨다. 그러지 않으면 그 손짓이 목록으로 새어 회차가 열린다.
                // 아래로 흘러 판 밖으로 나가는 단추는 그대로 둔다 — 판이 커지면
                // 그만큼 더 보인다.
                if (!sunk) Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        // 판 아래로 흘러나간 단추는 판 밖으로 삐져나오지 않고
                        // 판 끝에서 잘린다 — 판이 커지면 그만큼 더 보인다
                        .fillMaxHeight()
                        .clipToBounds()
                        .padding(top = ROOF)
                        .offset(x = -LANTERN_PULL)
                        .then(veil)
                        // 어깨를 끌어도 목록이 굴러가고 판이 함께 자란다 —
                        // 오른쪽 목록에서 끄는 것과 똑같이 움직인다. 끌기는
                        // 이렇게 넘기고, 단추를 빗나간 톡 소리만 삼킨다.
                        .nestedScroll(nested)
                        .scrollable(rounds, Orientation.Vertical, reverseDirection = true)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .zIndex(1f),
                ) {
                    // 높이를 판에 맞춰 묶지 않는다. 묶으면 마지막 단추가 제 칸을
                    // 다 받지 못해 판 끝보다 한참 위에서 잘린다 — 오른쪽 회차는
                    // 판 끝에 딱 맞춰 잘리는데 왼쪽만 떠 보였다. 넘치게 두고
                    // 자르는 것은 바깥의 판 끝에 맡긴다.
                    Column(
                        Modifier
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .offset(y = -SHOULDER_LIFT),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 핑크 묶음이 둘 다 비었으면 등은 자리째 빠진다 —
                        // 돌려 보일 것도, 눌러 갈 데도 없는 자리다.
                        if (hasPink) Lantern(pool, open = pink.isNotEmpty()) { han ->
                            // 등에 뜬 그 글자가 선 자리로 곧장 편다. 묶음 밖의
                            // 글자면(아직 몇 자 안 될 때다) 첫 장부터 편다.
                            onWords(
                                Mark.AMBER,
                                Collect.Kind.CHARS,
                                pink.indexOf(han).coerceAtLeast(0),
                            )
                        }
                        if (hasPink) Spacer(Modifier.height(TALLY_TOP))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(TALLY_GAP),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            for (bin in Mark.entries) {
                                // 색마다 둘씩 — 낱글자가 먼저, 문제가 뒤다
                                for (kind in Collect.Kind.entries) {
                                    Tally(
                                        title = tallyName(bin, kind),
                                        n = counts[bin]?.get(kind) ?: 0,
                                        color = if (bin == Mark.AMBER) Hak3.Pink else Hak3.Green,
                                        solid = kind == Collect.Kind.CHARS,
                                    ) { onWords(bin, kind, 0) }
                                }
                            }
                        }
                        // 설정으로 드는 문 — 서랍을 여는 길은 이것 하나뿐이다.
                        // 어깨의 다른 단추와 같은 잉크로 서되, 수가 붙지 않는
                        // 자리라 마름모를 앞에 세운다. 마지막 단추에서 유난히 멀리
                        // 떨어져 서므로, 판이 다 자랐을 때에만 판 안으로 들어와
                        // 마지막 회차와 나란히 선다.
                        Spacer(Modifier.height(GEAR_TOP))
                        Row(
                            Modifier
                                .offset(y = SHOULDER_LIFT + DOOR_DROP)
                                .onGloballyPositioned {
                                    doorBottom = it.positionInRoot().y + it.size.height
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { drawer = Drawer.SETTINGS },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 마름모 둘은 회차 번호와 같은 잉크로 서고, 글자만
                            // 온전한 흰빛이다 — 누르는 곳이 어디인지 그것이 말한다
                            Box(Modifier.size(GEAR).rotate(45f).background(Hak3.Hanja))
                            Spacer(Modifier.width(GEAR_GAP))
                            Text("SETTINGS", style = TALLY_NAME, color = Color.White, maxLines = 1)
                            Spacer(Modifier.width(GEAR_GAP))
                            Box(Modifier.size(GEAR).rotate(45f).background(Hak3.Hanja))
                        }
                    }
                }

                if (!sunk) LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(start = LIST_SHIFT)
                        .then(veil)
                        .nestedScroll(nested),
                    state = rounds,
                    contentPadding = PaddingValues(top = ROOF + DECO_A_DROP - LIST_INK, bottom = LIST_FLOOR),
                ) {
                    if (trouble != null) {
                        item(key = "setup") { Setup(radius, trouble, onFolder, onFile) }
                    }
                    items(exams, key = { it.round }) { e ->
                        RoundRow(e, e.round == last) {
                            last = it
                            Settings.setLastRound(context, it)
                            onPick(it)
                        }
                    }
                }

                // 오른쪽 어깨의 장식. 위에서부터 a·b·c 이고, c 는 설정 문과
                // 아랫선을 맞춘다 — 문이 판 아래에 있는 동안에는 함께 잘려
                // 보이지 않다가, 판이 다 자라면 문과 나란히 들어온다.
                // b 는 a 의 아랫선과 c 의 윗선 한가운데다.
                if (!sunk) Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .clipToBounds()
                        .padding(top = ROOF, end = SIDE + DECO_PULL),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    val cTop = with(density) {
                        (doorBottom - panelTop - ROOF.toPx() - (DECO_W + DECO_C_WIDE).toPx() / DECO_C +
                            DECO_C_DROP.toPx()).coerceAtLeast(0f)
                    }
                    val aTop = with(density) { DECO_A_DROP.toPx() }
                    val aBottom = aTop + with(density) { DECO_W.toPx() / DECO_A }
                    // b 는 a 의 아랫선에서 늘 같은 만큼 떨어져 선다 — c 가 판 밖에
                    // 있든 안에 있든 자리가 흔들리지 않는다.
                    val bTop = aBottom + with(density) { DECO_AB_GAP.toPx() }
                    Deco(R.drawable.deco_a, DECO_A, aTop)
                    Deco(R.drawable.deco_b, DECO_B, bTop)
                    Deco(R.drawable.deco_c, DECO_C, cTop, DECO_W + DECO_C_WIDE, DECO_C_PUSH)
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

/**
 * 오른쪽 어깨의 장식. haku_deco 의 svg 셋을 그대로 옮긴 것이라 그림 안의 글자는
 * 이미 윤곽선이고, 색도 원본이 들고 있다. 여기서는 자리와 크기만 정한다.
 *
 * 셋의 가로세로 비 — 폭 하나를 정하면 높이는 이 비로 따라온다.
 */
private const val DECO_A = 258f / 476f
private const val DECO_B = 259f / 49f
private const val DECO_C = 260f / 184f

/** 장식이 차지하는 폭. 원본은 258 이나 그대로 두면 판의 절반을 넘는다. */
private val DECO_W = 96.dp

/** 장식이 판 벽에서 한 뼘 더 물러나는 만큼. */
private val DECO_PULL = 8.dp

/** c 만 더 넓게 선다. 오른끝에 붙어 있으므로 넓어지는 쪽은 왼쪽이다. */
private val DECO_C_WIDE = 7.dp

/**
 * c 가 문의 아랫선에서 더 내려앉는 만큼. 문을 따라 서되 이만큼 어긋난다.
 * 8dp 였던 것을 다시 8dp 걷어 지금은 문의 아랫선에 그대로 맞춰 선다.
 */
private val DECO_C_DROP = 0.dp

/** c 만 오른쪽으로 더 나가는 만큼. 셋 중 이것만 벽에 더 붙어 선다. */
private val DECO_C_PUSH = 3.dp

/** a 와 b 사이. b 는 이 거리에 못 박혀 c 를 따라 움직이지 않는다. */
private val DECO_AB_GAP = 52.dp

/** 맨 위 장식만 조금 내려 앉는 만큼. */
private val DECO_A_DROP = 4.dp

/**
 * 회차 번호가 제 글자 상자 안에서 아래로 내려앉는 만큼(어센트). 장식은 그림이라
 * 여백이 없으므로, 두 윗선을 눈으로 맞추려면 목록을 이만큼 더 끌어올려야 한다.
 * 어센트는 7dp 인데 눈으로는 한 뼘 낮아 보여 1dp 를 더 얹었다.
 */
private val LIST_INK = 8.dp

/** 회차 목록이 통째로 오른쪽으로 물러나는 만큼. */
private val LIST_SHIFT = 24.dp

/**
 * 목록을 끝까지 올렸을 때 마지막 회차가 판 아래끝에서 물러나 멈추는 만큼.
 * 위쪽 처마(ROOF 18dp)보다 10dp 얕다 — 그만큼 마지막 줄이 더 내려가 선다.
 * ROOF 를 빼서 쓰지 못하는 것은 그것이 이 파일 아래쪽에 서 있기 때문이다.
 */
private val LIST_FLOOR = 8.dp




/** 장식 한 벌. 기둥 안에서 제 자리(px)만 받아 앉는다. */
@Composable
private fun Deco(res: Int, ratio: Float, top: Float, width: Dp = DECO_W, push: Dp = 0.dp) {
    Image(
        painterResource(res),
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(0, top.roundToInt()) }
            .offset(x = push)
            .width(width)
            .aspectRatio(ratio),
    )
}

/** 속을 비운 묶음이 두르는 테의 두께. */
private val RING = 2.dp

/** 진행 눈금의 두께. */
private val GAUGE = 1.dp

/**
 * 눈금의 길이. 세 자리 번호의 잉크가 가장 넓게 서는 줄(111)을 폰에서 재어
 * 잡은 60dp 다. 어느 줄에서나 이 길이로 서서, 번호 아래에 번호만큼 깔린다.
 */
private val GAUGE_W = 60.dp

/**
 * 번호가 서는 자리의 너비. 세 자리 번호의 글자 상자를 재어 잡았다. 줄마다 이
 * 자리를 그대로 쓰므로 어깨의 수가 늘 같은 x 에서 왼쪽 맞춤으로 선다. 번호가
 * 이 자리보다 넓어져도 잘리지 않고 양쪽으로 고르게 넘친다.
 */
private val NUM_W = 64.dp

/** 번호 자리와 그 어깨의 수 사이. */
private val COUNT_GAP = 11.dp

/** 그 수만 번호 쪽으로 더 당겨지는 만큼. 번호가 비워 둔 자리는 그대로 둔다. */
private val COUNT_PULL = 2.dp

/** 어깨의 수가 서는 자리의 너비. 세 자리까지 든다. */
private val COUNT_W = 20.dp

/**
 * 회차 덩이가 판 오른벽에서 물러나는 만큼. 왼쪽에서 한자가 물러난 26dp 보다
 * 6dp 더 붙어 선다 — 어깨의 수가 한두 자리라 잉크는 그보다 더 안쪽에서 끝난다.
 */
private val WALL = 17.dp

/** 눈금의 바탕. 경계선에서 한 겹 더 물러난다. */
private val GAUGE_TRACK = Hak3.Rule.copy(alpha = Hak3.Rule.alpha / 2)

/** 차오르는 쪽. 다 차기 전까지는 물러나 있다. */
private val GAUGE_FILL = Hak3.HanjaDim.copy(alpha = Hak3.HanjaDim.alpha * 0.6f)

/** 끝까지 간 것. 흰빛이되 옅게 — 온전한 흰색은 이 자리에 너무 세다. */
private val GAUGE_DONE = Color.White.copy(alpha = 0.32f)

/** 마지막으로 열어 본 줄의 바닥이 판 벽에서 물러나는 만큼. */
private val INSET = 8.dp


/**
 * 회차 숫자를 알약 한가운데에 앉히는 값. 잉크의 가운데를 재어 잡았다.
 * 코레일체에서는 2dp 내려야 맞았는데, 본문 서체가 바뀌며 되레 3dp 올려야 맞는다.
 */
private val INK = (-3).dp

/**
 * 번호의 글자 상자 위끝에서 잉크 위끝까지. 옆의 수를 그 선에 맞출 때 쓴다.
 * 코레일체에서 5.4dp 였는데, 번호가 Source Han 으로 바뀌며 8dp 더 내려앉았다.
 * 거기서 3dp 더 — 눈으로 보고 잡은 자리다. 깜박이는 점도 이 자를 함께 본다.
 */
private val SHOULDER = 7.4.dp

/** 번호 옆의 작은 수. 줄 상자를 글자에 바짝 붙여 위아래 여백을 없앤다. */
private val COUNT = TextStyle(
    fontFamily = Mono,
    fontSize = 13.sp,
    lineHeight = 11.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** 서랍이 앉는 자리. 왼쪽에서 나오므로 판은 오른쪽으로 밀린다. */
private fun anchor(drawer: Drawer, room: Float) = when (drawer) {
    Drawer.SETTINGS -> room
    Drawer.NONE -> 0f
}

/** 서랍이 열렸을 때 남는 판의 자락. 돌아가는 길로만 쓰는 자리라 좁게 둔다. */
private const val STRIP = 0.15f

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
enum class Drawer { NONE, SETTINGS }

/** 기출 데이터를 아직 못 읽었을 때 목록 자리에 서는 카드. 사전은 그동안에도 쓴다. */
@Composable
private fun Setup(radius: Dp, trouble: String, onFolder: () -> Unit, onFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // 판 위에 얹히는 자리라 판보다 한 겹 어둡게 판다
            .background(SETUP_FACE, RoundedCornerShape(radius))
            .padding(20.dp),
    ) {
        Text("No exam data yet", fontSize = 22.sp, color = Hak3.Text)
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
            // 폴더 쪽이 제 길이라 색면으로, 파일 쪽은 물러난 색면으로 선다
            Capsule("Choose folder", Hak3.Pink, Color.Black, onFolder)
            Spacer(Modifier.width(8.dp))
            Capsule("Choose file", Hak3.Knob, Hak3.TextDim, onFile)
        }
    }
}

/** 새 판 안내의 잉크. 알림이되 소리치지 않는 자리라 흰빛에서 한 겹 물러난다. */
private val UPDATE_INK = Color(0xFFDDDDDD)

/** 자료를 고르는 칸의 바탕 — 판보다 한 겹 어둡다. */
private val SETUP_FACE = Color.Black.copy(alpha = 0.18f)

/** 색면 캡슐 단추. 기출 화면의 단추와 같은 꼴이다. */
@Composable
private fun Capsule(label: String, face: Color, ink: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = ink,
        modifier = Modifier
            .background(face, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** 회차 칸과 같은 얼굴을 한 칸. 큰 줄과 작은 줄만 밖에서 정한다. */
@Composable
private fun Cell(
    big: String,
    small: String,
    color: Color,
    enabled: Boolean,
    solid: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 채운 쪽은 묶음 안의 카드와 같이 앞면이 통째로 그 색이고 글은 검정이다.
    // 비운 쪽은 테와 글만 그 색으로 서고 속은 바탕 그대로 둔다.
    // 어느 쪽이든 비어 있는 묶음은 색을 죽여 지금 열 것이 없음을 알린다.
    val face = if (enabled) color else Hak3.Knob
    val ink = when {
        !enabled -> Hak3.TextDim
        solid -> Color.Black
        else -> color
    }
    Column(
        modifier
            // 네모가 아니라 정원이다. 폭이 곧 지름이고, 글은 그 한가운데 앉는다.
            .aspectRatio(1f)
            .then(
                if (solid) Modifier.background(face, CircleShape)
                else Modifier.border(RING, face, CircleShape)
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 한자가 아니므로 회차 줄과 같은 코레일체로. 얇은 한자 서체는 큰 한자에만 쓴다.
        Text(
            big,
            fontFamily = Korail,
            fontWeight = FontWeight.Light,
            fontSize = 44.sp,
            color = ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            small,
            fontSize = 20.sp,
            color = ink.copy(alpha = if (enabled) 0.55f else 1f),
            textAlign = TextAlign.Center,
        )
    }
}

/** 회차 목록이 판 위에서 물러나는 만큼. 어깨의 위 여백도 이 자에서 잰다. */
private val ROOF = 18.dp

/**
 * 왼쪽 어깨가 판 벽에서 물러나는 만큼. 여기에 등의 여백 10dp 가 더해져 등의
 * 글자 상자가 사전 판의 한자와 같은 26dp 선에 선다 — 두 판의 한자가 한 줄로
 * 떨어지는 자리다. 폰에서 재어 보니 잉크도 둘 다 벽에서 29.7dp 였다.
 */
private val SIDE = 16.dp

/**
 * 등의 글자가 첫 회차 번호와 윗선을 맞추려고 올라앉는 만큼. 폰에서 재어 잡았다 —
 * 번호의 잉크는 판 위끝에서 29.3dp, 한자의 잉크는 40.3dp 에 있었다.
 * 올리는 것은 그리는 자리뿐이라 아래 단추는 따라 오르지 않는다.
 */
private val LANTERN_LIFT = 6.dp

/** 등이 제 자리에서 오른쪽으로 물러나는 만큼. */
private val LANTERN_SHIFT = 3.dp

/** 등과 네 단추가 함께 올라앉는 만큼. 문은 이 몫을 되돌리고 제 길로 간다. */
private val SHOULDER_LIFT = 2.dp

/** 설정 문이 제자리에서 더 내려앉는 만큼. 오른쪽 c 도 문을 따라 내려간다. */
private val DOOR_DROP = 18.dp

/** 등과 그 아래 것들이 한 덩이로 왼쪽에 물러나 서는 만큼. */
private val LANTERN_PULL = 11.dp


/**
 * 등과 첫 단추 사이. 예전에는 22dp 를 두고 단추 묶음을 8dp 끌어올려 그렸는데,
 * 그리는 자리만 올리면 판 끝의 자르는 선은 그대로라 마지막 단추가 8dp 일찍
 * 잘렸다. 이제 그만큼을 이 자에서 덜어 낸다 — 자리도 그림도 한 자리다.
 */
private val TALLY_TOP = 14.dp

/** 단추끼리 벌어지는 만큼. */
private val TALLY_GAP = 14.dp


/**
 * 설정 문 앞에 서는 표. 예전에 두 판 사이에 박혀 있던 그 표다 — 네모를 45도
 * 돌려 세운 마름모, 한 변은 점 지름 8.4dp 의 80% 다.
 */
private val GEAR = 8.4.dp * 0.8f
private val GEAR_GAP = 8.dp
/**
 * 마지막 단추(Known Cards)와 설정 문 사이. 판이 다 자랐을 때 문의 아랫선이
 * 마지막 회차의 아랫선과 나란히 놓이도록 폰에서 재어 잡은 값이다.
 */
private val GEAR_TOP = 118.3.dp

/** 단추 이름의 잉크. 회차 번호와 같은 색을 한 겹 더 물린다. */
private val TALLY_INK = Hak3.Hanja.copy(alpha = Hak3.Hanja.alpha * 0.7f)

/** 이름과 수 사이. 한 덩이로 읽히도록 바짝 붙인다. */
private val TALLY_TIGHT = 4.dp

/**
 * 이름이 수 쪽으로 내려앉는 만큼. 내리는 것은 그리는 자리뿐이라 수도, 아래
 * 단추도 그대로 있고 둘 사이의 틈만 그만큼 좁아진다.
 */
private val TALLY_LABEL_DROP = 2.dp

/** 등의 글자가 제 자리에서 두르는 여백. 바탕은 두지 않고 자리만 잡는다. */
private val LANTERN_PAD = 10.dp

/** 등에 한 글자가 머무는 참. (ms) */
private const val BEAT = 1500L

/** 못 외운 낱글자가 이보다 많을 때만 그 안에서 뽑는다. 그 아래로는 3급 전체에서. */
private const val LANTERN_MIN = 10

/** 등의 글자. 줄 상자를 글자에 바짝 붙여 등이 글자만큼만 서게 한다. */
private val LANTERN_INK = TextStyle(
    fontFamily = ThinHanja,
    fontWeight = FontWeight.ExtraLight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** 단추의 이름. */
private val TALLY_NAME = TextStyle(
    fontFamily = Korail,
    fontSize = 9.sp,
    lineHeight = 9.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** 단추의 수. 이름 바로 아래에 한 뼘 크게 선다. */
private val TALLY_NUM = TextStyle(
    fontFamily = Mono,
    fontSize = 32.sp,
    lineHeight = 32.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * 단추에 적는 이름. 어느 묶음인지와 무엇이 담겼는지를 그대로 적되, 두 낱말을
 * 위아래로 나눠 적는다 — 한 줄로 눕히면 이름이 아래 수보다 넓어져, 가운데
 * 어깨가 제 폭보다 벌어진다.
 */
private fun tallyName(bin: Mark, kind: Collect.Kind): String = when {
    bin == Mark.AMBER && kind == Collect.Kind.CHARS -> "Unsure\nLetters"
    bin == Mark.AMBER -> "Unsure\nCards"
    kind == Collect.Kind.CHARS -> "Known\nLetters"
    else -> "Known\nCards"
}

/**
 * 판 어깨의 등. 못 외운 낱글자를 0.8초에 한 자씩 돌려 보인다 — 아직 몇 자 안
 * 될 때는 3급 배정한자에서 아무 글자나 뽑아 그 자리를 채운다.
 * 뜬 글자를 누르면 그 글자가 선 자리로 낱글자 묶음이 펴진다. 펼 묶음이 비어
 * 있으면([open] 이 거짓이면) 등은 보이기만 하고 눌리지 않는다.
 *
 * 글자는 HAKA 의 한자가 가장 클 때와 같은 크기다. 등은 그 글자 상자에 사방
 * 여백을 더한 정사각이라, 글자가 커지면 등도 따라 커진다.
 */
@Composable
private fun Lantern(pool: List<String>, open: Boolean, onOpen: (String) -> Unit) {
    val square = LocalConfiguration.current.screenWidthDp.dp - CARD * 2
    val base = square.value * DICT_HEAD * 0.68f
    // 사전 판의 큰 한자가 다 자랐을 때와 같은 크기다. 서체도 굵기도 같으니
    // 두 자리의 한자가 한 벌로 읽힌다.
    val glyph = base.sp
    // 자리는 사전 판의 한자 크기에 사방 여백을 더한 만큼 — 한자는 가로세로가
    // 같으니 등도 정사각이다.
    val side = with(LocalDensity.current) { base.sp.toDp() } + LANTERN_PAD * 2
    // 첫 글자부터 아무 글자다 — 처음 뜨는 것이 늘 묶음의 첫 자면 돌리는 맛이 없다
    var han by remember(pool) { mutableStateOf(pool.randomOrNull().orEmpty()) }
    LaunchedEffect(pool) {
        while (pool.isNotEmpty()) {
            delay(BEAT)
            // 같은 글자가 두 번 이어 서면 등이 멈춘 것처럼 보인다
            han = if (pool.size > 1) (pool - han).random() else pool.first()
        }
    }
    Box(
        Modifier
            .size(side)
            .offset(x = LANTERN_SHIFT, y = -LANTERN_LIFT)
            .clickable(enabled = open && han.isNotEmpty()) { onOpen(han) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            han,
            style = LANTERN_INK.copy(fontSize = glyph, lineHeight = glyph),
            // 못 외운 글자를 돌려 보이는 자리라, 그 묶음의 색으로 선다
            color = Hak3.Pink,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true),
        )
    }
}

/**
 * 단어장 단추. 무엇이 담겼는지를 작은 영문으로 적고, 그 바로 아래에 담긴 수를
 * 한 뼘 크게 세운다. 둘이 한 덩이로 눌린다 — 색이 어느 묶음인지, 굵기가 어느
 * 갈래인지 말해 준다. 낱글자가 굵고 문제가 보통이다.
 */
@Composable
private fun Tally(title: String, n: Int, color: Color, solid: Boolean, onClick: () -> Unit) {
    val on = n > 0
    val ink = if (on) color else Hak3.TextDim
    Column(
        Modifier.clickable(enabled = on, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 이름은 회차 번호와 같은 잉크다 — 색은 수에만 준다.
        // 두 줄이고 행간은 글자 크기 그대로(100%)라, 두 낱말이 한 덩이로 붙는다.
        Text(
            title,
            style = TALLY_NAME,
            color = if (on) TALLY_INK else Hak3.TextDim,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = TALLY_LABEL_DROP),
        )
        Spacer(Modifier.height(TALLY_TIGHT))
        Text(
            "$n",
            style = TALLY_NUM.copy(
                fontWeight = if (solid) FontWeight.Bold else FontWeight.Normal,
            ),
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 회차 한 줄. 번호를 크게 적고 담아 둔 수를 그 어깨에 붙인다.
 * 마지막으로 열어 본 줄에는 번호 왼쪽 위에 흰 점이 천천히 깜박인다.
 */
@Composable
private fun RoundRow(e: ExamRow, on: Boolean, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val counts = remember(e.round) { Marks.counts(context, e.round) }
    // 그 회차에서 마지막으로 보고 있던 문항. 한 번도 안 들어갔으면 0 이다.
    val seen = remember(e.round) { Marks.lastSeen(context, e.round) }
    val live = e.complete || e.items > 0
    Box(
        Modifier
            .fillMaxWidth()
            // 눌린 자국을 그리지 않는다 — 줄 하나가 판 너비 그대로라, 자국이
            // 뜨면 판 전체가 번쩍이는 것처럼 보인다.
            .clickable(
                enabled = live,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onPick(e.round) },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 번호와 어깨의 수가 한 덩이로 판 오른벽에서 48dp 물러나 선다. 덩이의
        // 너비가 고정이라 수가 몇 자리든 번호는 늘 같은 자리에 있다. 어깨의
        // 수는 번호 자리 오른쪽에서 늘 같은 거리로 왼쪽 맞춤으로 서고, 눈금은
        // 번호 아래에 번호만큼 깔린다.
        Column(
            Modifier
                .padding(end = COUNT_GAP + COUNT_W + WALL)
                .width(NUM_W)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                // 숫자는 글자 상자 안에서 위로 쏠려 앉는다(내림자가 없다). 잰 만큼
                // 올려 잉크가 줄 한가운데에 오게 한다.
                Text(
                    roundNo(e.round),
                    // 숫자만 서는 자리라 폭이 고른 서체로
                    fontFamily = Mono,
                    fontSize = 42.sp,
                    // 마지막으로 열어 본 회차만 온전한 흰빛으로 선다. 나머지는
                    // 한 겹 물러나고, 아직 문항이 없는 회차는 더 물러난다.
                    color = when {
                        !live -> Hak3.HanjaDim
                        on -> Color.White
                        else -> Hak3.Hanja
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .wrapContentWidth(unbounded = true)
                        .offset(y = INK),
                )
                // 담아 둔 수는 번호의 윗선에 맞춘다 — 한 개든 두 개든 같은 자리에서
                // 시작한다. 옆으로 미는 것은 그리는 자리만이라 번호 자리의 너비는
                // 그대로고, 번호는 한가운데를 지킨다.
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = NUM_W + COUNT_GAP - COUNT_PULL, y = INK)
                        .padding(top = SHOULDER)
                ) {
                    if (counts.amber > 0) {
                        // 노란 수만 2dp 더 올라간다. 자리는 그대로라 초록은 따라오지 않는다.
                        Text(
                            "${counts.amber}",
                            style = COUNT,
                            color = Hak3.Pink,
                            modifier = Modifier.offset(y = (-2).dp),
                        )
                    }
                    if (counts.known > 0) {
                        Text("${counts.known}", style = COUNT, color = Hak3.Green)
                    }
                    if (!live) Text("no text", fontSize = 13.sp, color = Hak3.TextDim)
                }
            }
            // 어디까지 갔는지 — 번호 덩이 바로 아래, 그 너비만큼.
            // 한 번도 들어가지 않은 회차에는 눈금 자체가 서지 않는다.
            if (seen > 0 && e.items > 0) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        // 줄마다 같은 길이로 선다. 3dp 올라앉되 올리는 것은
                        // 그리는 자리만이라 줄 높이는 그대로다.
                        .width(GAUGE_W)
                        .offset(y = (-3).dp)
                        // 눈금은 어느 줄에서나 같은 규칙으로 선다 — 어디까지
                        // 갔는지만 말하고, 어느 줄인지는 번호가 말한다
                        .height(GAUGE)
                        .background(GAUGE_TRACK, CircleShape)
                ) {
                    // 끝까지 간 회차만 흰빛으로 선다 — 다 봤다는 말은 그만한 값이다
                    val done = seen >= e.items
                    Box(
                        Modifier
                            .fillMaxWidth((seen.toFloat() / e.items).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                            if (done) GAUGE_DONE else GAUGE_FILL,
                            CircleShape,
                        )
                    )
                }
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
            Text(
                label,
                // 받는 동안 뜨는 몫(72% 같은)은 수만 서는 자리라 폭이 고른 서체로
                fontFamily = if (label.endsWith("%")) Mono else Korail,
                fontSize = 44.sp,
                color = UPDATE_INK,
                maxLines = 1,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                note,
                // 곁의 글은 판 번호이거나 안내 문구다. 번호일 때만 고른 폭으로.
                fontFamily = if (note.firstOrNull()?.isDigit() == true) Mono else Korail,
                fontSize = 13.sp,
                color = UPDATE_INK,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}
