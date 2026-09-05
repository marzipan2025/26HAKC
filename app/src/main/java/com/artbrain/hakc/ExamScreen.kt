package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon

private val HANJA = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

/**
 * 줄은 띄어쓰기에서만 갈린다.
 *
 * 한글은 글자 하나하나가 줄바꿈 자리가 될 수 있어서 그냥 두면 '바 / 람직한' 처럼
 * 낱말 한가운데가 갈린다. 글자 사이에 이음쇠(U+2060)를 끼워 그 자리를 막으면
 * 남는 자리는 띄어쓰기뿐이다. 이음쇠는 폭이 없어 눈에도, 글자 수에도 잡히지 않는다.
 * 한 낱말이 한 줄보다 길면 그때는 안드로이드가 알아서 잘라 준다.
 */
private const val GLUE = '\u2060'

/** 문제에서 눈에 띄어야 할 말. 무엇을 묻는 유형인지가 여기서 갈린다. */
private val LOUD = listOf("略字", "部首")

/**
 * 문제 한 줄. 略字·部首 만 흰색으로 도드라지게 하고 나머지는 흐린 색 그대로 둔다.
 * 이음쇠는 토막마다 넣는다 — 다 붙인 뒤에 찾으면 글자 사이가 벌어져 안 잡힌다.
 */
private fun instruction(s: String, tone: Color): AnnotatedString = buildAnnotatedString {
    val loud = SpanStyle(color = tone)
    var i = 0
    var prev = ' '
    while (i < s.length) {
        val hit = LOUD.firstOrNull { s.startsWith(it, i) }
        if (hit != null) {
            withStyle(loud) { append(glue(hit, prev)) }
            prev = hit.last()
            i += hit.length
            continue
        }
        var j = i + 1
        while (j < s.length && LOUD.none { s.startsWith(it, j) }) j++
        val chunk = s.substring(i, j)
        append(glue(chunk, prev))
        prev = chunk.last()
        i = j
    }
}

private fun glue(s: String, before: Char = ' '): String = buildString {
    var prev = before
    for (c in s) {
        if (!c.isWhitespace() && !prev.isWhitespace()) append(GLUE)
        append(c)
        prev = c
    }
}
private val OUTER = 8.dp

/** 위 캡슐과 아래 바닥 줄이 한 박자 어긋나 드나드는 만큼. (ms) */
private const val LAG = 160

/**
 * 카드 한 장. `id` 는 표시를 걸어 두는 이름이다 — 회차에서는 `113:7`, 단어장에서는
 * 글자 그 자체가 된다. 이렇게 두면 같은 화면으로 둘 다 넘길 수 있다.
 */
private class Page(val round: Int, val section: Section, val item: Item, val id: String)

private fun borderColor(m: Mark?) = when (m) {
    Mark.AMBER -> Hak3.Pink
    Mark.KNOWN -> Hak3.Green
    null -> Hak3.Rule
}

/**
 * 표시는 한 축 위에 놓인다 — 노랑(애매) ← 일반 → 초록(외움).
 * 위로 밀면 초록 쪽으로 한 칸, 아래로 밀면 노랑 쪽으로 한 칸.
 * 양 끝에서 더 밀어도 그 자리에 머문다.
 */
private fun step(m: Mark?, up: Boolean): Mark? = if (up) when (m) {
    Mark.AMBER -> null
    null -> Mark.KNOWN
    Mark.KNOWN -> Mark.KNOWN
} else when (m) {
    Mark.KNOWN -> null
    null -> Mark.AMBER
    Mark.AMBER -> Mark.AMBER
}

/**
 * 문항을 '묻는 식'과 '딸린 설명'으로 가른다.
 *   收穫            + 밑줄 친 지문
 *   竊(  )          + 밑줄 친 지문
 *   銳(  ) ↔ 鈍濁    (설명 없음)
 *   漸入佳(  )       + 들어갈수록 점점 경치가 좋음.
 * 앞쪽은 크게 세우고 뒤쪽은 본문 크기로 붙인다.
 */
/** 지문 속에 밑줄로 그어 둔 말. */
private val MARKED = Regex("<u>(.*?)</u>", RegexOption.DOT_MATCHES_ALL)

private fun split(item: Item): Pair<String, String?> {
    val body = item.html ?: item.question
    val plain = body.replace(Regex("</?u>"), "")
    // 묻는 것으로 세울 만한 말인가. 데이터에 '만둠' 같은 토막이 섞여 들어오는데,
    // 그대로 세우면 카드 한가운데에 뜻 없는 말이 크게 걸린다. 묻는 것은 한자이거나
    // 채워 넣을 괄호가 있는 꼴이다.
    val target = item.target?.takeIf { s ->
        s.any { it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' } || '(' in s
    }
    // target 칸이 비어도 지문에 밑줄이 그어져 있으면 그것이 곧 묻는 말이다.
    // 正字로 쓰는 유형은 밑줄 친 말이 한글이라 target 칸에 담겨 오지 않는다.
    // 낱글자로 토막 난 밑줄(<u>微</u><u>溫</u>)은 이어 붙여 한 말로 본다.
        ?: MARKED.find(body.replace("</u><u>", ""))?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    // 지문 안의 한 낱말을 묻는 문항 — 낱말이 머리, 지문이 꼬리
    if (target != null && plain != target) return target to body
    // '식 : 뜻풀이' / '식 - 뜻풀이' 로 적힌 문항
    Regex("\\s[:\\-]\\s").find(plain)?.let { m ->
        return plain.take(m.range.first).trim() to plain.substring(m.range.last + 1).trim()
    }
    return plain to null
}

/** 마주 세우는 표. 앞뒤 여백까지 함께 집는다. */
private val TURN = Regex("\\s*↔\\s*")

/** 둘째 보기부터의 동그라미 숫자. 앞의 여백까지 함께 집는다. */
private val PICK = Regex("\\s*([②-⑳])")

/**
 * 큰 자리에 설 글을 줄 나누기 좋게 손본다.
 *
 * **고르는 문제**(① 監督 ② 減毒)는 둘째 보기부터 줄을 바꾼다. 한 줄에 나란히
 * 두면 어디까지가 첫 보기인지 눈으로 갈리지 않는다. 보기가 넷이면 넷 다 제 줄에
 * 선다.
 *
 * **마주 세우는 문제**(( )奮 ↔ 鎭靜)는 화살표를 ⤶ 로 바꾸고 그 뒤에서 줄을
 * 바꾼다 — 꺾여 내려가는 화살표가 곧 다음 줄로 넘어간다는 표다.
 */
private fun shape(text: String): String =
    text.replace(TURN, " ⤶\n").replace(PICK, "\n$1").trim()

/** 묻는 것이 서는 크기. 문장을 통째로 묻는 유형만 예문 크기로 내려간다. */
private val HEAD = 80.sp


/** 한 회차를 넘긴다. 표시는 그 회차에 남는다. */
@Composable
fun ExamScreen(
    round: Int,
    db: ExamDb,
    morph: Modifier = Modifier,
    veil: Modifier = Modifier,
    morphLit: () -> Float = { 0f },
    /** 이 화면이 지금 자리를 내주는 중인가. 위·아래 줄을 되돌려 보내려고 본다. */
    leaving: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val all = remember(round) {
        db.sections(round).flatMap { s -> s.items.map { Page(round, s, it, "$round:${it.no}") } }
    }
    val book = remember(round) { Marks(context.applicationContext, round) }
    val marks = remember(round) {
        mutableStateMapOf<String, Mark>().apply {
            book.state.forEach { (no, m) -> put("$round:$no", m) }
        }
    }
    val start = remember(round, all) {
        val no = Marks.lastSeen(context, round)
        all.indexOfFirst { it.item.no == no }.coerceAtLeast(0)
    }
    Deck(
        all = all,
        title = "Round ${roundNo(round)}",
        subOf = { p -> "問 ${p.section.start}–${p.section.end}" },
        marks = marks,
        numbered = true,
        start = start,
        morph = morph,
        veil = veil,
        morphLit = morphLit,
        leaving = leaving,
        onMark = { p, m ->
            book.set(p.item.no, m)
            if (m == null) marks.remove(p.id) else marks[p.id] = m
            // 노랑으로 담은 문항의 한자는 단어장에 쌓는다. 이미 있는 글자는 그냥 넘어간다.
        },
        onSeen = { p -> Marks.setLastSeen(context, round, p.item.no) },
        onBack = onBack,
    )
}

/**
 * 단어장. 담은 문항을 두 갈래로 넘긴다.
 *
 *   낱글자   문항에 나온 한자를 한 글자씩. 여기서 표시를 풀면 그 묶음에서 빠지고,
 *            **제 글자가 모두 빠진 문항은 그때 표시가 해제된다.**
 *   문제     문항을 통째로. 회차가 달라도 글이 같으면 한 장이고, 여기서 표시를
 *            바꾸면 **같은 글의 모든 회차분이 함께 간다.**
 */
@Composable
fun WordScreen(
    db: ExamDb,
    bin: Mark,
    kind: Collect.Kind,
    /** 묶음에서 먼저 펴 볼 자리. 묶음이 그새 줄었으면 있는 데까지만 간다. */
    start: Int = 0,
    morph: Modifier = Modifier,
    veil: Modifier = Modifier,
    morphLit: () -> Float = { 0f },
    leaving: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val chars = kind == Collect.Kind.CHARS
    val all = remember(bin, kind) {
        if (chars) {
            Collect.list(context, db, bin).mapIndexedNotNull { i, han ->
                val meaning = db.hunmeum(han) ?: return@mapIndexedNotNull null
                val section = Section(0, 0, 0, "다음 漢字의 訓과 音을 쓰시오.", emptyList())
                val item = Item(
                    no = i + 1,
                    spanEnd = 0,
                    question = han,
                    html = null,
                    target = han,
                    answer = meaning,
                )
                Page(0, section, item, han)
            }
        } else {
            Collect.cards(context, db, bin).mapNotNull { (round, no) ->
                val (section, item) = db.pick(round, no) ?: return@mapNotNull null
                Page(round, section, item, "$round-$no")
            }
        }
    }
    // 묶음에 든 것은 모두 그 묶음의 색이다.
    val marks = remember(bin, kind) {
        mutableStateMapOf<String, Mark>().apply { all.forEach { put(it.id, bin) } }
    }
    Deck(
        all = all,
        // 서랍의 이름과 같은 꼴로 적는다 — 거기서 눌러 온 자리다
        title = (if (bin == Mark.AMBER) "Pink " else "Green ") + if (chars) "Letters" else "Cards",
        // 문제 묶음의 카드는 제 회차에서 온 것이라 어느 회차인지 밝혀 둔다
        subOf = { p -> if (chars) null else "第 ${roundNo(p.round)} 回" },
        marks = marks,
        numbered = false,
        start = start.coerceIn(0, (all.size - 1).coerceAtLeast(0)),
        morph = morph,
        veil = veil,
        morphLit = morphLit,
        face = if (bin == Mark.AMBER) Hak3.Pink else Hak3.Green,
        leaving = leaving,
        onMark = { p, m ->
            if (chars) {
                Collect.keep(context, db, p.id, bin, m != null)
                if (m == null) marks.remove(p.id) else marks[p.id] = bin
            } else {
                Collect.markCards(context, db, p.round, p.item.no, m)
                if (m == null) marks.remove(p.id) else marks[p.id] = m
            }
        },
        onSeen = {},
        onBack = onBack,
    )
}

/**
 * 카드 묶음 하나를 넘기는 화면. 회차든 단어장이든 이 화면을 쓴다.
 * 어디에 표시를 적어 두는지만 밖에서 정해 준다.
 */
@Composable
private fun Deck(
    all: List<Page>,
    title: String,
    subOf: (Page) -> String?,
    marks: SnapshotStateMap<String, Mark>,
    numbered: Boolean,
    start: Int,
    morph: Modifier,
    veil: Modifier,
    morphLit: () -> Float,
    /** 단어장이면 그 묶음의 색. 회차면 null 이고 카드는 판 색 그대로다. */
    face: Color? = null,
    /** 이 화면이 지금 빠져나가는 중인가. 색을 되돌리며 줄어들게 하려고 본다. */
    leaving: Boolean = false,
    onMark: (Page, Mark?) -> Unit,
    onSeen: (Page) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sound = rememberClicks()
    val buzz = rememberBuzz()
    var filter by remember(all) { mutableStateOf<Mark?>(null) }
    val open = remember(all) { mutableStateMapOf<String, Boolean>() }

    // 목록을 열면 그때의 구성을 붙잡아 둔다. 판정으로 목록에서 빠져도 카드는 그 자리에
    // 남아, 노랑을 한 번 올려 일반으로, 한 번 더 올려 초록으로 이어 갈 수 있다.
    // 담긴 수는 위 필터 원의 숫자가 곧바로 알려 준다.
    var listed by remember(all) { mutableStateOf<List<Page>?>(null) }
    val pages = listed ?: all
    val pager = rememberPagerState(initialPage = start, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val radius = (screenCornerRadius() - OUTER).coerceAtLeast(0.dp)
    // 카드를 들고 있는 동안만 카드층을 맨 위로. 그동안 두 줄은 눌리지 않는다.
    var lifted by remember(all) { mutableStateOf(false) }
    var markOnLeft by remember(all) { mutableStateOf(Settings.markOnLeft(context)) }
    // 카드에 핑크나 초록이 얹히는 순간에는 손끝에도 가볍게 한 번 — 푸는 쪽은
    // 소리만으로 넉넉하다. 이미 그 색이면 바뀐 것이 없으니 울리지 않는다.
    val mark: (Page, Mark?) -> Unit = { p, m ->
        if (m != null && marks[p.id] != m) buzz.tick()
        onMark(p, m)
    }
    val index = pager.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(index)
    // 다음에 열 때 여기서부터 보여 준다
    LaunchedEffect(all, page?.id) { page?.let(onSeen) }

    // 카드가 캡슐 위로 날아가야 하므로 pager 를 화면 전체로 깔고, 카드만 캡슐·바닥 줄
    // 안쪽으로 밀어 둔다. pager 는 제 영역 밖을 잘라내기 때문에 이렇게 하지 않으면
    // 카드가 캡슐께에서 잘려 사라진다. 바닥 줄은 pager 뒤에 두어 조작을 뺏기지 않는다.
    // 위로는 캡슐과 OUTER 만큼, 아래로도 바닥 줄과 OUTER 만큼. 같은 간격이다.
    val inset = PaddingValues(top = TOP + OUTER, bottom = BAR + OUTER)
    // 위 캡슐과 아래 바닥 줄은 화면 밖에서 미끄러져 들어온다. 나갈 때는 온 길로
    // 되돌아간다. 판이 카드 자리까지 늘어나는 참(GROW)을 그대로 쓴다 — 판과 줄이
    // 한 몸으로 움직인다. 1 이 화면 밖, 0 이 제자리다.
    //
    // 둘은 한 박자 어긋나 움직인다. 들어올 때는 위가 먼저 서고 아래가 [LAG] 만큼
    // 늦게 따라오며, 나갈 때는 그 순서를 뒤집어 아래가 먼저 빠지고 위가 뒤따른다.
    val slideTop = remember { Animatable(1f) }
    val slideFoot = remember { Animatable(1f) }
    LaunchedEffect(leaving) {
        val ease = FastOutSlowInEasing
        val to = if (leaving) 1f else 0f
        if (leaving) {
            launch { slideFoot.animateTo(to, tween(GROW, easing = ease)) }
            slideTop.animateTo(to, tween(GROW, delayMillis = LAG, easing = ease))
        } else {
            launch { slideTop.animateTo(to, tween(GROW, easing = ease)) }
            slideFoot.animateTo(to, tween(GROW, delayMillis = LAG, easing = ease))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Hak3.Ground)
            .padding(OUTER)
    ) {
        // 목록의 판이 늘어나 앉는 자리. 카드와 똑같은 사각형이라 넘어오는 동안
        // 이어져 보인다. 넘어온 뒤에는 지운다 — 카드를 들추면 그 뒤로 이것이
        // 비쳐 카드가 겹쳐 있는 것처럼 보인다.
        //
        // 단어장으로 갈 때는 늘어나는 동안 색도 함께 바뀐다. 다 늘어난 뒤에 색이
        // 갈리면 카드가 두 장인 것처럼 보인다 — 판이 서고 그 위에 색 카드가
        // 얹히는 꼴이다. 늘어나는 결과 같은 시간을 쓴다.
        // 들어올 때는 판 색에서 묶음 색으로, 나갈 때는 거꾸로. 줄어들면서 색이
        // 도로 돌아와야 돌아가는 길도 카드 한 장으로 보인다.
        val grown = remember { Animatable(0f) }
        LaunchedEffect(leaving) {
            grown.animateTo(if (leaving) 0f else 1f, tween(GROW, easing = FastOutSlowInEasing))
        }
        val plate = if (face == null) Hak3.Card else lerp(Hak3.Card, face, grown.value)
        Box(
            Modifier
                .fillMaxSize()
                .padding(inset)
                .then(morph)
                .graphicsLayer { alpha = morphLit() }
                .background(plate, RoundedCornerShape(radius))
        )
        if (pages.isEmpty()) {
            EmptyList(filter, Modifier.fillMaxSize().padding(inset).then(veil), radius)
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().then(veil).zIndex(if (lifted) 1f else 0f),
                pageSpacing = 6.dp,
            ) { i ->
                pages.getOrNull(i)?.let { p ->
                    Box(Modifier.fillMaxSize().padding(inset)) {
                        QuestionPage(
                            page = p,
                            revealed = open[p.id] == true,
                            mark = marks[p.id],
                            face = face != null,
                            radius = radius,
                            onLifted = { lifted = it },
                            // 담기든 풀리든 자리가 바뀌면 딸깍
                            onMark = { m ->
                                sound.yes()
                                mark(p, m)
                            },
                            // 축의 끝에서 더 밀었다 — 갈 데가 없다고 알린다
                            onEnd = sound::no,
                            // 표시가 실제로 바뀌었을 때만 넘어간다. 양 끝에서 더 민
                            // 경우(초록을 또 위로)는 바뀐 게 없으니 그 자리에 머문다.
                            onAdvance = {
                                if (i < pages.size - 1) {
                                    scope.launch { pager.animateScrollToPage(i + 1) }
                                }
                            },
                        ) { open[p.id] = open[p.id] != true }
                    }
                }
            }
        }

        // 캡슐과 바닥 줄은 pager 뒤에 둔다 — 앞에 두면 pager 가 눌림을 가로챈다.
        // pager 는 화면 전체를 차지하므로 카드는 잘리지 않고 이 줄들 아래로 미끄러져 나간다.
        TopBar(
            // 제 키에 바깥 여백까지 더해 밀어내면 화면 위로 온전히 사라진다
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { translationY = -slideTop.value * (size.height + OUTER.toPx()) },
            title = title,
            sub = page?.let(subOf),
            filter = filter,
            hollow = face != null,
            amber = marks.count { it.value == Mark.AMBER },
            known = marks.count { it.value == Mark.KNOWN },
            onFilter = { f ->
                val next = if (filter == f) null else f
                filter = next
                listed = next?.let { m -> all.filter { marks[it.id] == m } }
                scope.launch { pager.scrollToPage(0) }
            },
        )


        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { translationY = slideFoot.value * (size.height + OUTER.toPx()) },
            // 단어장에서 이미 풀린 카드는 뺄 것이 없으니 단추도 물러난다
            enabled = page != null && (face == null || marks[page.id] != null),
            markOnLeft = markOnLeft,
            label = page?.item?.label ?: "",
            lastNo = if (numbered && filter == null) all.lastOrNull()?.item?.spanEnd else null,
            index = index,
            total = pages.size,
            onSeek = { scope.launch { pager.scrollToPage(it) } },
            onBack = onBack,
            // 단어장에서는 그 카드의 색을 그대로 입고 서서 묶음에서 빼는 자리가 된다
            hue = if (face != null) page?.let { marks[it.id] }?.let(::borderColor) ?: Hak3.Knob else null,
        ) {
            val p = page ?: return@BottomBar
            if (face != null) {
                // 묶음에서 뺀다. 카드는 그 자리에 남아 색만 벗는다 — 무엇이
                // 풀렸는지 보고 나서 넘어가라고 자리를 옮기지 않는다.
                if (marks[p.id] == null) sound.no() else sound.yes()
                mark(p, null)
                return@BottomBar
            }
            // 이미 노랑이면 단추를 눌러도 자리가 바뀌지 않는다
            if (marks[p.id] == Mark.AMBER) sound.no() else sound.yes()
            mark(p, Mark.AMBER)
            if (index < pages.size - 1) {
                scope.launch { pager.animateScrollToPage(index + 1) }
            }
        }

    }
}

/**
 * 설정. 왼쪽 서랍의 단어장 칸과 같은 자리에서 시작한다 — 같은 16dp 벽, 같은 4dp
 * 어깨, 같은 폭이다. 두 서랍이 한 자에서 재어진 것처럼 보이게 하려는 것이다.
 *
 * 맨 위에 앱의 사인을 폭 절반으로 걸고, 그 아래 이름의 내력을 작게 적는다.
 * 나머지는 그 아래로 왼쪽에 붙여 세운다. 적는 말은 영어로 둔다 — 짧고,
 * 줄바꿈에 흔들리지 않는다.
 */
@Composable
fun SettingsPanel(
    radius: Dp,
    face: Color,
    gap: Dp,
    /** 서랍이 열려 있는가. 열려 있는 동안에만 사인의 글자 띠가 돈다. */
    spinning: Boolean,
    built: String?,
    markOnLeft: Boolean,
    onMarkSide: (Boolean) -> Unit,
    onWipe: () -> Unit,
) {
    // 한 번에 지워지지 않는다. 물음이 그 자리에 서고, 대답해야 지운다.
    var asking by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // 뒤의 카드가 눌리지 않게 이 층에서 손짓을 삼킨다
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // 위에서 물러나는 4dp 와 두 덩이 사이의 틈은 본 화면의 두 판에서 그대로
        // 받아 온다.
        Spacer(Modifier.height(DRAWER_TOP))
        // 위 덩이 — 사인은 오른위 귀에 못 박히고, 자료 날짜와 판 번호는 왼쪽에
        // 붙어 선다. 수와 판 번호가 서는 자리라 글씨는 폭이 고른 [Mono] 다.
        //
        // 키는 사인이 정한다. 안쪽 마진이 사방으로 같으므로 사인의 위아래 여백도
        // 같아지고, 그러면 덩이의 세로 한가운데가 곧 사인의 한가운데다 — 글은
        // 그 자리에 서기만 하면 사인과 눈높이가 맞는다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(DRAWER_PAD * 2 + DRAWER_SIGN)
                .background(face, RoundedCornerShape(radius))
                .padding(DRAWER_PAD),
        ) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("Data ${day(built)}", fontFamily = Mono, fontSize = DRAWER_INK,
                     color = Hak3.TextSoft)
                Text("Version ${BuildConfig.VERSION_NAME}", fontFamily = Mono,
                     fontSize = DRAWER_INK, color = Hak3.TextSoft)
            }
            Sign(Modifier.align(Alignment.TopEnd), spinning)
        }

        Spacer(Modifier.height(gap))

        // 아래 덩이 — 손댈 것 둘. 표시 단추가 설 쪽과, 다 지우는 자리다.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(face, RoundedCornerShape(radius))
                .padding(DRAWER_PAD),
        ) {
            Label("MARK BUTTON")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Side("Left", markOnLeft) { onMarkSide(true) }
                Side("Right", !markOnLeft) { onMarkSide(false) }
            }

            Spacer(Modifier.height(36.dp))
            Label("RESET")
            Spacer(Modifier.height(12.dp))
            if (!asking) {
                Key("Erase everything", Hak3.Knob, Hak3.TextDim) { asking = true }
            } else {
                Text(
                    "Marks, wordbook and search history\nwill be gone for good.",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Hak3.TextSoft,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 되돌릴 수 없는 쪽만 붉은 면으로. 검정 글이 그 위에 앉는다.
                    Key("Erase", Hak3.Red, Color.Black) { asking = false; onWipe() }
                    Key("Cancel", Hak3.Knob, Hak3.TextDim) { asking = false }
                }
            }
        }
    }
}

/** 첫 덩이가 위에서 물러나는 만큼. 본 화면에서 사전 판이 물러난 것과 같다. */
private val DRAWER_TOP = 4.dp

/** 덩이 안쪽의 마진. 덩이가 넓으므로 판 위의 글보다 넉넉히 둔다. */
private val DRAWER_PAD = 24.dp

/**
 * 사인. 글자 띠와 안의 표를 따로 그려, 서랍이 열려 있는 동안 띠만 천천히
 * 시계방향으로 돈다 — 안의 표는 제자리다. 둘은 캔버스가 같아 겹치면 한 그림이다.
 *
 * 각은 [Animatable] 로 이어 간다. 서랍을 닫으면 코루틴이 끊기면서 그 각에 그대로
 * 멈추고, 다시 열면 거기서부터 돈다 — 열고 닫을 때마다 처음으로 튀지 않는다.
 */
@Composable
private fun Sign(modifier: Modifier, spinning: Boolean) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            turn.animateTo(turn.value + 360f, tween(RING_TURN, easing = LinearEasing))
            // 한 바퀴마다 각을 0..360 으로 되접는다. 그대로 두면 수가 끝없이 자라
            // 언젠가 부동소수의 눈금이 성겨진다.
            turn.snapTo(turn.value % 360f)
        }
    }
    Box(modifier.size(DRAWER_SIGN)) {
        Image(
            painterResource(R.drawable.ring_text),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().rotate(turn.value),
        )
        Image(
            painterResource(R.drawable.ring_mark),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 글자 띠가 한 바퀴 도는 데 걸리는 참. (ms) */
private const val RING_TURN = 30_000

/**
 * 위 덩이 오른위 귀에 못 박히는 사인. 그림이 정사각에 가까워 한 변으로 잡는다.
 * 64dp 이던 것을 112% 로 키웠다 — 글 곁에 나란히 서던 때보다 제 자리를 넓게 쓴다.
 */
private val DRAWER_SIGN = 64.dp * 1.12f

/** 날짜와 판 번호의 글자 크기. 20 에서 세 걸음(2·2·3sp) 물러났다. */
private val DRAWER_INK = 13.sp

/** 날짜는 YYYY.MM.DD 로 적는다. 데이터는 하이픈으로 적어 오므로 그것만 바꾼다. */
private fun day(s: String?): String = s?.replace('-', '.') ?: "unknown"

/** 묶음의 이름. 성기게 적고 흐리게 두어 아래 것들과 층을 가른다. */
@Composable
private fun Label(text: String) {
    Text(text, fontSize = 15.sp, letterSpacing = 1.6.sp, color = Hak3.TextDim)
}

/**
 * 서랍의 단추. 테가 아니라 면으로 서고 꼴은 알약이다 — 단어장 셀과 같은 결이다.
 * 면이 색이면 글은 검정으로 앉는다.
 */
@Composable
private fun Key(label: String, face: Color, ink: Color, onPick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = ink,
        modifier = Modifier
            .background(face, CircleShape)
            .clickable(onClick = onPick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** 고른 쪽만 앰버로 채운다. */
@Composable
private fun Side(label: String, on: Boolean, onPick: () -> Unit) {
    Key(
        label,
        face = if (on) Hak3.Pink else Hak3.Knob,
        ink = if (on) Color.Black else Hak3.TextDim,
        onPick = onPick,
    )
}

@Composable
private fun TopBar(
    modifier: Modifier,
    title: String,
    sub: String?,
    filter: Mark?,
    amber: Int,
    known: Int,
    /** 단어장에서는 원을 검게 두고 숫자만 제 색으로 남긴다. */
    hollow: Boolean = false,
    onFilter: (Mark) -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP)
            .background(Hak3.Surface, CircleShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterDot(
                if (amber > 0) Hak3.Pink else Hak3.Knob,
                filter == Mark.AMBER,
                amber,
                hollow,
            ) { onFilter(Mark.AMBER) }
        }
        // 제목과 곁줄은 한 덩이로 캡슐 한가운데. 둘은 밑선으로 맞춘다 —
        // 한자가 낀 쪽은 줄 상자가 달라 가운데로 맞추면 글이 어긋나 보인다.
        Row {
            Text(
                title,
                fontSize = 15.sp,
                color = Hak3.Text,
                modifier = Modifier.alignByBaseline(),
            )
            if (sub != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    sub,
                    fontSize = 15.sp,
                    color = Hak3.Hanja,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        // 오른쪽은 초록. 왼쪽 노랑과 같은 단추이고 모아 보이는 것만 다르다.
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 담긴 것이 없으면 바닥 줄의 손잡이와 같은 색으로 물러나 있는다
            FilterDot(
                if (known > 0) Hak3.Green else Hak3.Knob,
                filter == Mark.KNOWN,
                known,
                hollow,
            ) { onFilter(Mark.KNOWN) }
        }
    }
}

/**
 * 목록 필터 단추. 원 한가운데에 담긴 문항 수를 적는다.
 *
 * 단어장에서는 카드가 통째로 노랑이거나 초록이라, 같은 색 원이 그 위에 서면
 * 서로 묻힌다. 그때는 원을 검게 두고 숫자만 제 색으로 남긴다.
 */
@Composable
private fun FilterDot(color: Color, on: Boolean, count: Int, hollow: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(DOT)
            .background(if (hollow) Hak3.Ground else color, CircleShape)
            // 켜져 있으면 흰 테를 두른다 — 아래 판정 원과 색이 같으므로 상태는 테로 가른다
            .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Text("$count", fontSize = 13.sp, color = if (hollow) color else Hak3.Ground)
        }
    }
}

@Composable
private fun EmptyList(filter: Mark?, modifier: Modifier, radius: Dp) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Hak3.Surface, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (filter) {
                Mark.AMBER -> "Nothing marked yellow."
                else -> "Nothing marked green."
            },
            fontSize = 15.sp,
            color = Hak3.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuestionPage(
    page: Page,
    revealed: Boolean,
    mark: Mark?,
    face: Boolean,
    radius: Dp,
    onLifted: (Boolean) -> Unit,
    onMark: (Mark?) -> Unit,
    onEnd: () -> Unit,
    onAdvance: () -> Unit,
    onTap: () -> Unit,
) {
    val item = page.item
    // 앞면이 통째로 색면인 단어장 카드에서는 그 위의 모든 글과 부호를 검정으로
    // 뒤집는다. 제 짙기(알파)는 지킨다 — 흐리게 둔 것은 흐린 채로 흐리다.
    val onFace = face && mark != null
    fun ink(c: Color) = if (onFace) Color.Black.copy(alpha = c.alpha) else c
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    /*
     * 카드를 얼마나 들어 올렸는가. 문항이 바뀌면 처음부터.
     *
     * 손짓은 코루틴을 거치지 않고 이 값에 곧바로 쌓는다. 한 손짓마다 코루틴을 띄워
     * 옮기면, 손을 뗀 뒤에야 도착한 마지막 손짓이 제자리로 돌아가던 애니메이션을
     * 밀어내고 카드를 어중간한 자리에 세워 놓는다.
     */
    var lift by remember(item.no) { mutableFloatStateOf(0f) }
    var wide by remember(item.no) { mutableFloatStateOf(1000f) }
    // 잡은 손이 카드 가운데에서 얼마나 치우쳤는가. -1(왼쪽 끝) ~ +1(오른쪽 끝).
    var arm by remember(item.no) { mutableFloatStateOf(0f) }
    // 한 번 끄는 동안 판정은 한 번뿐이다. 문턱을 넘나들며 색이 뒤집히지 않게.
    var settled by remember(item.no) { mutableStateOf(false) }
    // 제자리로 돌아가는 중인 몸짓. 카드를 다시 잡으면 멈춘다.
    var homing by remember(item.no) { mutableStateOf<Job?>(null) }
    val reach = with(density) { REACH.toPx() }
    val tilt = with(density) { TILT.toPx() }
    // 잡은 자리를 축 삼아 도는 시늉. 왼쪽 아래를 잡고 올리면 오른쪽으로 기운다.
    // 문턱과 따로 두어 기울기는 예전 손맛 그대로다.
    val spin = (arm * (lift / tilt) * 5f).coerceIn(-12f, 12f)

    /** 어느 자리에 있든 0으로 돌려놓는다. 돌아가던 것이 있으면 그것부터 접는다. */
    fun home(spec: AnimationSpec<Float>, then: (suspend () -> Unit)? = null) {
        homing?.cancel()
        homing = scope.launch {
            animate(lift, 0f, animationSpec = spec) { v, _ -> lift = v }
            lift = 0f
            then?.invoke()
        }
    }

    // 들려 있는 동안에는 캡슐과 바닥 줄 위로 올라온다
    LaunchedEffect(lift != 0f) { onLifted(lift != 0f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = lift
                rotationZ = spin
            }
            .background(Hak3.Card, RoundedCornerShape(radius))
            // 단어장에서는 테두리만이 아니라 앞면까지 그 색이다 — 테두리와 같은
            // 색, 같은 짙기로. 그 위에 놓이는 색 글씨는 검정으로 뒤집는다.
            .then(
                if (face && mark != null)
                    Modifier.background(borderColor(mark), RoundedCornerShape(radius))
                else Modifier
            )
            // 표시가 없으면 1픽셀, 담긴 카드는 1dp. 어느 쪽이든 카드 경계 안쪽에 붙는다.
            .border(if (mark == null) Dp.Hairline else 1.dp, borderColor(mark), RoundedCornerShape(radius))
            .onSizeChanged { wide = it.width.toFloat() }
            // 위로 밀면 초록 쪽으로, 아래로 밀면 노랑 쪽으로 한 칸. 카드는 제자리로 돌아온다.
            .draggable(
                state = rememberDraggableState { dy ->
                    if (settled) return@rememberDraggableState
                    lift += dy
                    // 판정은 손가락이 문턱을 넘는 그 자리에서 바로.
                    if (kotlin.math.abs(lift) > reach) {
                        settled = true
                        val turned = step(mark, lift < 0f)
                        if (turned != mark) onMark(turned) else onEnd()
                        home(RETURN) {
                            // 제자리에 앉는 것을 보고 나서 넘긴다
                            if (turned != mark) {
                                delay(HOLD)
                                onAdvance()
                            }
                        }
                    }
                },
                orientation = Orientation.Vertical,
                onDragStarted = { at ->
                    // 돌아가는 중이더라도 다시 잡으면 손을 따른다
                    homing?.cancel()
                    arm = ((at.x / wide) * 2f - 1f).coerceIn(-1f, 1f)
                },
                onDragStopped = {
                    // 문턱을 못 넘고 손을 뗐으면 제자리로. 넘었으면 이미 돌아가는 중이다.
                    if (!settled) home(SETTLE)
                    settled = false
                },
            )
            .pointerInput(item.no) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 무엇을 묻는 문제인지는 카드 맨 위 한가운데 따로 세운다.
        // 아래 것들과 한 흐름으로 두지 않는다 — 자리도 정렬도 따로 간다.
        Text(
            instruction(page.section.instruction, ink(Hak3.Text)),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = ink(Hak3.TextDim),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.7f)
                .padding(top = 14.dp),
        )
        val (raw, tail) = split(item)
        // 큰 자리에 설 글은 줄을 나눠 세운다 — 고르는 문제와 마주 세우는 문제.
        val head = shape(raw)
        // 묻는 것이 한 낱말이 아니라 글인 유형. 크게 세울 것이 아니다.
        val prose = raw.count { it in '가'..'힣' } >= 5 && raw.length > 14
        Column(
            Modifier
                .fillMaxWidth()
                // 기준점을 위로. 카드 한가운데가 아니라 그보다 60dp 높은 자리에 선다.
                // 글을 통째로 묻는 유형은 그럴 것 없이 한가운데 그대로 둔다.
                .offset(y = if (prose) 0.dp else -LEAD)
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            // 어느 목록에 담겼는지는 번호에 입힌 색으로 알린다. 앞면이 이미 그
            // 색인 단어장에서는 뒤집어 검정으로 적는다.
            // 큰 한자만 제자리에 두고 나머지는 조금 안쪽에서 시작한다
            Text(
                item.label,
                fontSize = 22.sp,
                color = ink(if (mark != null) borderColor(mark) else Hak3.TextDim),
                modifier = Modifier.padding(start = SHIFT),
            )
            Spacer(Modifier.height(6.dp))

            // 묻는 것은 언제나 같은 자리, 같은 글꼴로 크게. 길이에 따라 크기만 준다.
            // 한자부터 아래로는 한 덩이로 끌어올린다 — 번호와의 사이를 좁히기 위해서다.
            Column(Modifier.offset(y = if (prose) 0.dp else -TIGHTEN)) {
                // 묻는 것은 한 크기로 선다 — 유형마다 크기가 달라지면 첫인상이
                // 흔들린다. 글을 통째로 묻는 유형만 예문과 같은 크기로 낮추고,
                // 밑줄 친 말을 밝게 그어 어디를 묻는지 보인다.
                if (prose) {
                    // 예문 문단과 같은 틀로 세운다 — 크기도 행간도 시작점도.
                    // 이쪽이 곧 묻는 것이라 예문보다 밝게 두고, 밑줄 친 말만
                    // 한 겹 더 밝다.
                    Text(
                        underlined(shape(item.html ?: raw), ink(Hak3.Text)),
                        fontSize = 22.sp,
                        lineHeight = 36.sp,
                        color = ink(Hak3.Hanja),
                        modifier = Modifier.padding(start = SHIFT),
                    )
                } else Text(
                    head,
                    fontFamily = ThinHanja,
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = HEAD,
                    lineHeight = HEAD * 1.18f,
                    color = ink(Hak3.Hanja),
                )
                if (tail != null) {
                    // 지문만 위로 당긴다. 아래 정답 자리는 그만큼 도로 벌려 두어
                    // 점과 정답의 좌표는 그대로 있게 한다.
                    Spacer(Modifier.height(2.dp))
                    Text(
                        underlined(tail, ink(Hak3.Hanja)),
                        fontSize = 22.sp,
                        lineHeight = 36.sp,
                        color = ink(Hak3.TextDim),
                        modifier = Modifier.padding(start = SHIFT),
                    )
                }

                Spacer(Modifier.height(if (tail != null) 38.dp else 26.dp))
                Box(Modifier.padding(start = SHIFT)) { AnswerSlot(item, revealed, ::ink) }
            }
        }
    }
}

@Composable
private fun AnswerSlot(item: Item, revealed: Boolean, ink: (Color) -> Color) {
    val a = item.answer
    val hanja = a != null && HANJA.containsMatchIn(a)
    // 답이 없다는 말도 한자 자리에 서는 글이니 같은 얇기로 적는다
    val notice = a == null
    // 동그라미 숫자(①②③)는 잉크가 한자·한글과 다른 자리에 앉는다. 큰 자리에서는
    // 위로 솟고, 작은 자리에 홀로 있으면 되레 낮게 앉는다. 재어 잡은 값이다.
    val ring = a != null && a.first() in '①'..'⑳'
    val nudge = when {
        ring && hanja -> RING_BIG
        ring -> RING_SMALL
        else -> 0.dp
    }
    // 원은 언제나 같은 크기로 자리를 지킨다. 정답은 그 아래에 겹쳐 그리므로
    // 펼쳐도 위의 한자와 지문이 밀리지 않는다.
    Box(
        Modifier
            .size(DOT)
            .background(ink(if (revealed) Hak3.Neon else Hak3.Rule), CircleShape)
    ) {
        if (!revealed) return@Box
        // 폭도 높이도 없는 자리를 하나 두고, 그 안에서만 제 크기를 갖게 한다.
        // 원의 28dp 제약에 갇히면 큰 글자가 잘린다.
        Box(
            Modifier
                .offset(
                    x = DOT + 18.dp,
                    y = DROP - (if (hanja) INK_HANJA else INK_HANGUL) + nudge,
                )
                .size(0.dp)
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
        ) {
        Column(Modifier.width(ANSWER)) {
            Text(
                a ?: "no answer",
                fontFamily = if (hanja || notice) ThinHanja else Korail,
                fontWeight = if (hanja || notice) FontWeight.ExtraLight else FontWeight.Normal,
                fontSize = if (hanja) 56.sp else 30.sp,
                lineHeight = if (hanja) 70.sp else 40.sp,
                color = ink(if (a != null) Hak3.Neon else Hak3.TextDim),
                style = FLUSH_TOP,
            )
            item.gloss?.let { g ->
                Spacer(Modifier.height(6.dp))
                Text(
                    g,
                    fontSize = 22.sp,
                    lineHeight = 35.sp,
                    color = ink(Hak3.Neon.copy(alpha = 0.66f)),
                )
            }
        }
        }
    }
}

/**
 * 바닥 줄의 높이. 양옆 두 단추는 이 값을 한 변으로 하는 정사각이라 지름이 곧
 * 이것이고, 가운데 슬라이더의 알약도 이 높이를 따른다. 그러므로 이 하나가
 * 줄 전체의 크기다. 노랑·초록 단추만 [PICK_TRIM] 만큼 작게 서서 50dp 다.
 *
 * 위 캡슐([TOP])과 같은 값이다 — 화면 위아래에 같은 두께로 한 켤레가 선다.
 * 58dp 이던 것을 낮춘 값이고, 안에 앉는 글씨(슬라이더 15sp, ✕ 22sp)는 제
 * 크기로 따로 박혀 있으므로 줄만 얇아지고 글자는 그대로다.
 */
private val BAR = 52.dp
private val DOT = 28.dp
private val TOP = 52.dp

/** 기준점을 카드 한가운데보다 이만큼 위로 올린다. */
private val LEAD = 60.dp

/** 번호와 한자 사이를 이만큼 좁힌다. 한자 아래의 것들도 함께 딸려 올라온다. */
private val TIGHTEN = 22.dp

/**
 * 첫 줄 위에 붙는 여백을 걷어낸다. 글자 상자의 윗변이 곧 글자의 윗선이 되어
 * 옆에 놓인 점과 눈으로 수평이 맞는다.
 */
private val FLUSH_TOP = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Top,
        trim = LineHeightStyle.Trim.FirstLineTop,
    ),
)

/**
 * 글자 상자의 윗변과 글자의 실제 윗선 사이에 남는 만큼. 서체가 글자 위에 두는
 * 여백이라 크기마다 다르다. 화면에서 재어 맞춘 값이다.
 */
/** 큰 한자를 뺀 나머지가 안쪽으로 물러나는 만큼. */
private val SHIFT = 4.dp

/** 동그라미 숫자가 큰 자리(한자와 함께)에서 더 솟는 만큼. */
private val RING_BIG = 1.4.dp

/** 작은 자리에 동그라미 숫자만 있을 때는 되레 낮게 앉는다. */
private val RING_SMALL = (-2.2).dp

private val INK_HANJA = 23.dp
private val INK_HANGUL = 6.7.dp

/** 윗선을 맞춘 뒤 눈에 맞게 조금 내려 앉히는 만큼. */
private val DROP = 6.dp

/** 펼친 정답이 쓰이는 폭. 원 아래에 겹쳐 그리므로 제 폭을 스스로 정한다. */
private val ANSWER = 260.dp

/** 이만큼 밀어야 판정이 한 칸 움직인다. */
private val REACH = 120.dp

/** 기울기의 기준. 문턱과 따로 두어 도는 맛은 예전 그대로 둔다. */
private val TILT = 60.dp

/** 판정하고 제자리로. 색이 바뀐 것을 보고 나서 천천히 내려앉는다. */
private val RETURN = tween<Float>(450, easing = FastOutSlowInEasing)

/** 문턱을 못 넘고 손을 뗐을 때. */
private val SETTLE = tween<Float>(320, easing = FastOutSlowInEasing)

/** 카드가 다 돌아온 뒤 다음 문항으로 넘어가기까지 쉬는 참. */
private const val HOLD = 260L

/**
 * 화면 바닥에 붙는 한 줄 — 왼쪽 설정, 가운데 슬라이더, 오른쪽 노랑 단추.
 * 셋이 너비를 꽉 채운다. 문항 번호는 슬라이더 한가운데에 얹는다.
 *
 * 초록(외웠음)은 단추로 두지 않는다. 카드를 위로 미는 것이 그 자리다.
 */
@Composable
private fun BottomBar(
    modifier: Modifier,
    enabled: Boolean,
    markOnLeft: Boolean,
    label: String,
    lastNo: Int?,
    index: Int,
    total: Int,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit,
    /** 단어장에서는 그 카드의 색. 회차에서는 null 이고 늘 노랑 단추가 선다. */
    hue: Color? = null,
    onAmber: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(BAR),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pick = @Composable { PickDot(enabled, hue, onAmber) }
        // 노랑 단추 건너편이 닫기다. 남색 설정 단추가 있던 자리를 도로 쓴다.
        if (markOnLeft) pick() else CloseDot(onBack)
        Scrubber(
            Modifier.weight(1f),
            index = index,
            total = total,
            text = if (lastNo != null) "$label / $lastNo" else "$label · ${index + 1} / $total",
            onSeek = onSeek,
        )
        if (markOnLeft) CloseDot(onBack) else pick()
    }
}

/**
 * ✕ 는 글자라 줄 상자 안에서 밑선 위에 앉는다. 상자를 한가운데 놓아도 잉크는
 * 그만큼 처져 보이므로 이만큼 끌어올린다 — 화면에서 재어 잡았다(22sp 에서 5px).
 */
private val CROSS_LIFT = 1.7.dp

/** 원 한가운데에 앉는 ✕. 닫기와 묶음에서 빼기가 같은 잉크를 쓴다. */
@Composable
private fun Cross(color: Color) {
    Text(
        "✕",
        fontSize = 22.sp,
        color = color,
        modifier = Modifier.offset(y = -CROSS_LIFT),
    )
}

/** 회차를 닫는다. 노랑 단추 건너편에 같은 크기로, 슬라이더와 같은 바탕으로 선다. */
@Composable
private fun CloseDot(onClick: () -> Unit) {
    Box(
        Modifier
            .size(BAR)
            .background(Hak3.Surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Cross(Hak3.Text)
    }
}

/**
 * 오른쪽 단추. 회차에서는 노랑을 찍는 자리이고, 단어장에서는 그 카드를 묶음에서
 * 빼는 자리다 — 카드와 같은 색 위에 검은 ✕ 를 얹어 닫기와 같은 뜻으로 읽힌다.
 */
@Composable
private fun PickDot(enabled: Boolean, hue: Color?, onPick: () -> Unit) {
    if (hue == null) {
        AmberDot(enabled, onPick)
        return
    }
    Box(
        Modifier
            .size(BAR)
            .padding(PICK_TRIM / 2)
            .background(if (enabled) hue else hue.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Cross(if (enabled) Color.Black else Hak3.TextDim)
    }
}

@Composable
private fun AmberDot(enabled: Boolean, onPick: () -> Unit) {
    Box(
        Modifier
            .size(BAR)
            .padding(PICK_TRIM / 2)
            .background(if (enabled) Hak3.Pink else Hak3.Pink.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onPick)
    )
}

/**
 * 노랑·초록 단추가 다른 것들보다 지름을 줄이는 만큼. 꽉 찬 색면은 같은 크기라도
 * 더 커 보이므로, 이만큼 깎아야 건너편 ✕ 나 가운데 슬라이더와 한 크기로 읽힌다.
 *
 * 자리(BAR)는 그대로 두고 그리는 면만 사방으로 절반씩 물린다 — 줄 안에서 차지하는
 * 칸이 흔들리지 않아 건너편 단추와 좌우가 어긋나지 않는다.
 */
private val PICK_TRIM = 2.dp

/**
 * 채워진 부분이 0%에서는 높이와 같은 지름의 정원, 100%에서는 가운데 영역을
 * 꽉 채우는 알약이 된다. 손잡이를 따로 두지 않고 채워진 끝이 곧 위치다.
 */
@Composable
private fun Scrubber(
    modifier: Modifier,
    index: Int,
    total: Int,
    text: String,
    onSeek: (Int) -> Unit,
) {
    // 채워진 부분이 노랑 단추와 같은 크기라야 한 줄로 읽힌다. 테두리는 두지 않는다.
    val inset = 0.dp
    BoxWithConstraints(
        modifier
            .height(BAR)
            .background(Hak3.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val d = LocalDensity.current
        val knob = BAR - inset * 2         // 0%일 때의 정원 지름
        val span = (maxWidth - inset * 2 - knob).coerceAtLeast(0.dp)
        val frac = if (total <= 1) 0f else index.toFloat() / (total - 1)
        val insetPx = with(d) { inset.toPx() }
        val knobPx = with(d) { knob.toPx() }
        val spanPx = with(d) { span.toPx() }
        val seek: (Float) -> Unit = { x ->
            val at = if (spanPx <= 0f) 0f else (x - insetPx - knobPx) / spanPx
            onSeek((at.coerceIn(0f, 1f) * (total - 1)).roundToInt())
        }
        Box(
            Modifier
                .matchParentSize()
                .padding(inset),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .width(knob + span * frac)
                    .height(knob)
                    .background(Hak3.Knob, CircleShape)
            )
        }
        Text(text, fontSize = 15.sp, color = Hak3.Text)
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(total, maxWidth) {
                    detectTapGestures { seek(it.x) }
                }
                .pointerInput(total, maxWidth) {
                    detectHorizontalDragGestures { change, _ -> seek(change.position.x) }
                }
        )
    }
}

/** 다음 <u> 또는 </u> 가 시작되는 자리. 없으면 끝. */
private fun nextTag(s: String, from: Int): Int {
    var k = from
    while (k < s.length) {
        if (s.startsWith("<u>", k) || s.startsWith("</u>", k)) return k
        k++
    }
    return s.length
}

/**
 * question_html 의 <u> 표시를 그대로 밑줄로 옮긴다.
 * 이음쇠는 뒤따르는 토막과 같은 꾸밈으로 붙여 밑줄이 끊기지 않게 한다.
 */
private fun underlined(html: String, mark: Color): AnnotatedString = buildAnnotatedString {
    val line = SpanStyle(color = mark, textDecoration = TextDecoration.Underline)
    var i = 0
    var open = false
    var prev = ' '
    while (i < html.length) {
        when {
            html.startsWith("<u>", i) -> { open = true; i += 3 }
            html.startsWith("</u>", i) -> { open = false; i += 4 }
            else -> {
                val j = nextTag(html, i)
                val chunk = html.substring(i, j)
                val glued = glue(chunk, prev)
                if (open) withStyle(line) { append(glued) } else append(glued)
                prev = chunk.lastOrNull() ?: prev
                i = j
            }
        }
    }
}

/**
 * 라이선스. 오른쪽 서랍에 든다.
 *
 * 설정과 달리 덩이가 하나다 — 읽을 것이 길게 이어지는 자리라 중간에 선을 그으면
 * 읽던 눈이 끊긴다. 판 하나가 위아래를 다 받고 그 안에서 글이 굴러간다.
 *
 * 적는 말은 영어로 둔다. 원문이 영어인 조항을 한국어로 옮겨 적으면 그 옮김이
 * 곧 또 하나의 주장이 되고, 서체와 자료의 이름은 어차피 영어다.
 */
@Composable
fun LicensePanel(radius: Dp, face: Color) {
    Column(
        Modifier
            .fillMaxSize()
            // 뒤의 카드가 눌리지 않게 이 층에서 손짓을 삼킨다
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // 설정 서랍과 같은 자리에서 시작하고 같은 자리에서 끝난다 — 두 서랍이
        // 한 자에서 재어진 것처럼 보이려면 위아래 벽이 같아야 한다.
        Spacer(Modifier.height(DRAWER_TOP))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(face, RoundedCornerShape(radius))
                .verticalScroll(rememberScrollState())
                .padding(DRAWER_PAD),
        ) {
            Label("LICENSES")
            Spacer(Modifier.height(16.dp))
            Text(
                LICENSE_TEXT,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Hak3.TextSoft,
            )
            // 판 끝에서 글이 잘려 보이지 않도록 아래에 한 뼘 더 둔다
            Spacer(Modifier.height(DRAWER_PAD))
        }
    }
}

/**
 * 서랍에 적는 글. 앱이 남의 것을 무엇 무엇 빌려 썼는지, 그 조건이 무엇인지다.
 *
 * 조항 전문을 다 옮기지는 않는다 — 어느 것을 어떤 이름으로 빌렸고 그 조건이
 * 무엇인지가 여기서 할 말이고, 전문은 그 이름으로 찾을 수 있다. 다만 OFL 과
 * CC BY-SA 처럼 '이렇게 밝히라' 고 요구하는 것은 그 요구를 그대로 지킨다.
 */
private val LICENSE_TEXT = """
26HAKC — Hanja dictionary and Grade 3 past papers
Copyright © ARTBRAIN / MARZIPAN 2025

This app bundles the third-party fonts and data listed below. Each remains the property of its authors, under the terms named with it.


FONTS

Source Han Sans KR
Copyright © 2014–2025 Adobe (http://www.adobe.com), with Reserved Font Name 'Source'. Source is a trademark of Adobe in the United States and/or other countries.
Licensed under the SIL Open Font License, Version 1.1. This Font Software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the license for the specific language governing permissions and limitations.
http://scripts.sil.org/OFL
Used for the large hanja glyphs only.

Korail Font (코레일체)
Copyright © Korea Railroad Corporation. Released by KORAIL for free public use, including commercial use, provided the font itself is not sold. Used for all Korean and Latin text in the app.

IBM Plex Mono
Copyright © 2017 IBM Corp. with Reserved Font Name "Plex". Licensed under the SIL Open Font License, Version 1.1, and distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
http://scripts.sil.org/OFL
Used for figures and technical readouts.


DICTIONARY DATA

Hanja readings — libhangul
Copyright © Choe Hwanjin and contributors. Licensed under the BSD 3-Clause License. The hanja table is redistributed with its copyright notice and disclaimer intact.
https://github.com/libhangul/libhangul

Definitions — 우리말샘
Copyright © National Institute of Korean Language. Licensed under Creative Commons Attribution-ShareAlike 2.0 Korea (CC BY-SA 2.0 KR). Definitions have been shortened to sentence boundaries; their wording is otherwise unchanged. Being ShareAlike material, the definitions carried in this app remain under the same license.
https://creativecommons.org/licenses/by-sa/2.0/kr/

Definitions — 위키낱말사전 (Wiktionary)
Copyright © Wikimedia Foundation and contributors. Licensed under Creative Commons Attribution-ShareAlike (CC BY-SA). Used as the first source for definitions, with the same shortening.
https://ko.wiktionary.org


SOFTWARE

Android Jetpack and AndroidX
Copyright © The Android Open Source Project. Licensed under the Apache License, Version 2.0.
http://www.apache.org/licenses/LICENSE-2.0


Past paper questions are the property of their examining body. They are read from a file you supply; none are distributed with this app.
""".trimIndent()
