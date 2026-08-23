package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/** 글자 위아래에 붙는 서체 여백을 걷어낸다 — 칸을 꽉 채워 보이게. */
private val FLUSH = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * 급수 기호와 색. 색은 01HAKA 그대로 특급~3급만 준다 — 다 칠하면 어느 것이 높은
 * 급수인지 되레 안 보인다. 기호는 빈 원으로 통일했다. 채운 원과 빈 원을 섞으면
 * 무게가 달라 급수와 상관없이 몇 개만 튀어 보인다.
 */
private val GRADE_ICON = listOf(
    R.drawable.ic_grade_0, R.drawable.ic_grade_1, R.drawable.ic_grade_2,
    R.drawable.ic_grade_3, R.drawable.ic_grade_4, R.drawable.ic_grade_5,
    R.drawable.ic_grade_6, R.drawable.ic_grade_7, R.drawable.ic_grade_8,
)

/** 급수를 모르는 글자는 빈 동그라미. 특급(0)과 한 칸으로 쓰면 金 같은 글자가 특급이 된다. */
private fun gradeIcon(grade: Int?) =
    if (grade == null) R.drawable.ic_grade_none else GRADE_ICON[grade.coerceIn(0, 8)]

/** 訓 앞에 끼워 넣는 급수 표시의 이름. 글 흐름을 타야 해서 인라인으로 둔다. */
private const val GRADE_SLOT = "grade"

private fun gradeColor(grade: Int?) = when (grade) {
    null -> Hak3.Text.copy(alpha = 0.3f)       // 급수를 모르는 글자는 흐리게
    0 -> Color.White
    1 -> Color(0xFF2999D1)
    2 -> Color(0xFFFFEB3B)
    3 -> Color(0xFF507D2A)
    else -> Hak3.Text
}

// 01HAKA 의 창은 310×270 이고 그 안이 한자 84 · 訓音 나머지 · 입력 50 으로 나뉜다.
// 창 단추 자리(26)는 안드로이드에 올 것이 없으니 뺀 244 를 기준으로 삼는다.
private const val HEAD = 84f / 244f
private const val FOOT = 50f / 244f * 0.72f    // 입력 칸

/** 실선이 벽에서 물러나는 거리. */
private val WALL = 16.dp

/** 글이 벽에서 물러나는 거리. 모서리가 둥그니 실선보다 더 안쪽에서 시작한다. */
private val TEXT_WALL = 26.dp
private val RULE_GAP = 10.dp

/** 訓音 줄에서 음이 차지하는 너비. 음은 늘 한 글자라 이만큼이면 넉넉하다. */
private val EUM = 27.dp

/** 訓音 자리는 두 줄에 한 줄 남짓 여백까지는 있어야 읽을 만하다. */
private val MID_MIN = 72.dp

/** 한자 자리는 제 높이의 1/4 까지 줄어든다. */
private const val HEAD_FLOOR = 0.25f

/**
 * 가장 낮췄을 때의 높이. 한자 한 줄과 訓音 두 줄은 남는다.
 * 입력 칸만 남기는 자리까지 접어 보았으나 칸이 서로 밀려 자꾸 어그러졌다.
 */
fun dictMin(square: Dp): Dp = square * 0.53f

/** 가장 키웠을 때의 높이. 폰 화면의 60% 까지만. */
fun dictMax(screen: Dp): Dp = screen * 0.60f

/** 목록 쪽에서 판의 높이를 셈할 때 쓴다. */
const val DICT_FOOT = FOOT
const val DICT_HEAD = HEAD

/** 위 한자 줄에 늘어놓는 한 칸 — 어느 낱말의 몇 번째 표기인지까지 안다. */
private class Slot(val word: Found, val index: Int, val variant: Variant, val many: Boolean)

/**
 * 사전 — 목록 맨 위에 앉는 정사각형 판.
 *
 * 01HAKA 를 그대로 옮겨 온 자리다. 위에서부터 한자, 訓音, 입력이고 한자와 입력만
 * 캡슐로 떼어 판 위에 얹는다. 동음이의어는 위 캡슐에서 좌우로 훑고, 훑는 대로 아래
 * 訓音과 뜻이 따라온다 — 한자를 고르면 그 표기의 풀이가 바로 아래 서 있게.
 */
@Composable
fun DictPanel(dict: Dict, radius: Dp, onFocus: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    // 자주 찾은 글자일수록 환하게. 세는 것은 엔터를 눌렀을 때뿐이다 —
    // 글자마다 도는 찾기까지 세면 한 낱말 적는 사이에 열 번이 지나간다.
    var seen by remember { mutableStateOf(Seen.all(context)) }
    // 노랑으로 담아 둔 문항에서 쌓인 글자. 사전에서 만나면 노랑으로 알린다 —
    // 기출에서 걸렸던 글자가 여기 또 나왔다는 뜻이다.
    val kept = remember { Collect.order(context).toSet() }
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Found>>(emptyList()) }
    LaunchedEffect(text) { found = dict.search(text) }

    val slots = remember(found) {
        found.flatMap { w ->
            w.variants.mapIndexed { i, v -> Slot(w, i, v, w.variants.size > 1) }
        }
    }
    val top = rememberLazyListState()
    val mid = rememberLazyListState()
    // 위 한자 줄과 아래 訓音 줄은 한 자리를 함께 본다. 어느 쪽을 끌든 다른 쪽이
    // 따라온다 — 끄는 쪽이 자리를 정하고, 정해진 자리로 나머지가 움직인다.
    // 손으로 끄는 것만 자리를 정할 수 있다. 따라가는 움직임까지 자리를 정하면
    // 둘이 서로를 밀어 끝없이 튄다.
    var active by remember(slots) { mutableIntStateOf(0) }
    val topHeld by top.interactionSource.collectIsDraggedAsState()
    val midHeld by mid.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(slots) {
        snapshotFlow { topHeld to top.firstVisibleItemIndex }
            .collect { (held, i) -> if (held) active = i }
    }
    LaunchedEffect(slots) {
        snapshotFlow { midHeld to mid.firstVisibleItemIndex }
            .collect { (held, i) -> if (held) active = i }
    }
    LaunchedEffect(active, slots) {
        if (slots.isEmpty()) return@LaunchedEffect
        val i = active.coerceIn(slots.indices)
        if (!topHeld && top.firstVisibleItemIndex != i) top.animateScrollToItem(i)
        if (!midHeld && mid.firstVisibleItemIndex != i) mid.animateScrollToItem(i)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(Hak3.Surface)
            .background(Hak3.Rule)          // 01HAKA 의 패널 바탕 한 겹
    ) {
        val square = maxWidth                       // 정사각형이었을 때의 한 변
        val foot = square * FOOT
        val full = square * HEAD                    // 한자 자리의 제 높이
        // 판이 줄면 訓音 자리부터 좁히고, 그 자리가 최소에 닿으면 한자 자리가 줄어든다
        val room = (maxHeight - foot - 2.dp).coerceAtLeast(0.dp)
        val head = when {
            room >= full + MID_MIN -> full
            // 한자 자리는 제 높이의 1/4 까지만 버틴다. 그 아래로는 아예 접는다 —
            // 접힌 판에는 입력 칸만 남아야 하므로 실선도 訓音 자리도 두지 않는다.
            room >= full * HEAD_FLOOR + MID_MIN -> (room - MID_MIN).coerceAtMost(full)
            else -> 0.dp
        }
        val folded = head <= 0.dp
        val glyph = (head.value * 0.68f).sp

        Column(Modifier.fillMaxSize()) {
            // 위 — 동음이의어를 좌우로 훑는다
            if (head > 0.dp) {
                Box(
                    Modifier.fillMaxWidth().height(head),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (slots.isEmpty()) {
                        // 한자와 같은 크기로 세운다. 넘치면 좌우로 훑어 읽는다 —
                        // 찾아낸 한자를 훑는 것과 같은 몸짓이다.
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (text.isBlank()) "漢字" else "Nope, not here.",
                                // 한자 자리에 서는 글이니 서체도 얇기도 한자와 같이
                                fontFamily = ThinHanja,
                                fontWeight = FontWeight.ExtraLight,
                                fontSize = glyph,
                                color = Hak3.HanjaDim,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = TEXT_WALL),
                            )
                        }
                    } else {
                        LazyRow(
                            state = top,
                            contentPadding = PaddingValues(horizontal = TEXT_WALL),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(slots) { i, s ->
                                // 표시는 글자와 한 줄에 두지 않는다. 같은 글 안에서 위로
                                // 올리면 줄 상자가 그만큼 부풀어 글자가 칸 밖으로 밀린다.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { active = i },
                                ) {
                                    Text(
                                        buildAnnotatedString {
                                            s.variant.hanja.forEach { ch ->
                                                // 담아 둔 글자는 노랑으로. 훑고 지나가는
                                                // 표기에서는 흐리게 두어 지금 보는 것이
                                                // 어느 표기인지는 그대로 알아보게 한다.
                                                val mine = ch.toString() in kept
                                                withStyle(
                                                    SpanStyle(
                                                        color = when {
                                                            mine && i == active -> Hak3.Amber
                                                            mine -> Hak3.Amber.copy(alpha = 0.45f)
                                                            i != active -> Hak3.HanjaDim
                                                            else -> hanjaLit(seen[ch.toString()] ?: 0)
                                                        }
                                                    )
                                                ) { append(ch) }
                                            }
                                        },
                                        fontFamily = ThinHanja,
                                        fontWeight = FontWeight.ExtraLight,
                                        fontSize = glyph,
                                        maxLines = 1,
                                    )
                                    if (s.many) {
                                        Text(
                                            if (s.index == 0) "●" else "${s.index}",
                                            fontSize = glyph * 0.16f,
                                            color = if (s.index == 0) Hak3.Red else Hak3.HanjaDim,
                                            modifier = Modifier
                                                .align(Alignment.Top)
                                                .padding(start = 2.dp, top = (head.value * 0.15f).dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Rule()
            }

            // 가운데 — 위에서 고른 표기의 訓音과 뜻
            if (!folded) Box(Modifier.fillMaxWidth().weight(1f)) {
                if (slots.isNotEmpty()) {
                    LazyColumn(
                        state = mid,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(TEXT_WALL + 6.dp, RULE_GAP, TEXT_WALL, RULE_GAP),
                    ) {
                        itemsIndexed(slots) { _, s -> VariantBlock(s, kept) }
                    }
                }
            }

            if (!folded) Rule()

            // 아래 — 입력. 01HAKA 처럼 안내 문구를 두지 않는다.
            Box(
                Modifier.fillMaxWidth().height(foot).padding(start = TEXT_WALL, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Hak3.Text, fontSize = 19.sp, fontFamily = Korail),
                    cursorBrush = SolidColor(Hak3.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // 키보드의 찾기 단추도 엔터와 같은 일을 한다
                    keyboardActions = KeyboardActions(onSearch = {
                        Seen.record(context, found.flatMap { w -> w.variants.map { it.hanja } })
                        seen = Seen.all(context)
                        focus.clearFocus()
                        ime?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 52.dp)
                        .onFocusChanged { onFocus(it.isFocused) },
                )
                // 오른쪽 끝 — 엔터. 찾기는 글자마다 이미 돌고 있으니 여기서 할 일은
                // 키보드를 접는 것뿐이다. 판은 그만큼 제자리로 돌아간다.
                Icon(
                    painterResource(R.drawable.ic_enter),
                    contentDescription = null,
                    tint = if (text.isEmpty()) Hak3.Rule else Hak3.Text,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(foot * 0.42f)
                        .clickable {
                            Seen.record(context, found.flatMap { w -> w.variants.map { it.hanja } })
                            seen = Seen.all(context)
                            focus.clearFocus()
                            ime?.hide()
                        },
                )
            }
        }
    }
}

/**
 * 세 부분을 가르는 실선. 벽에서 16dp 물러난다.
 * 높이는 1물리픽셀 — Dp.Hairline 은 0dp 라서 칸으로 쓰면 아무것도 안 그려진다.
 */
@Composable
private fun Rule() {
    val one = with(LocalDensity.current) { 1.toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WALL)
            .height(one)
            .background(Hak3.Rule)
    )
}

/**
 * 표기 하나의 풀이 — 글자마다 `음 : 급수 훈`, 그 아래 뜻.
 * [kept] 는 노랑으로 담아 둔 문항에서 쌓인 글자들이다.
 */
@Composable
private fun VariantBlock(s: Slot, kept: Set<String>) {
    Column(Modifier.padding(bottom = 12.dp)) {
        s.variant.hanja.forEachIndexed { i, ch ->
            val g = s.word.chars[ch.toString()] ?: return@forEachIndexed
            // 음과 훈은 밑선을 맞춘다. 위끝을 맞추면 오른쪽 글에 낀 급수 표시가
            // 줄 상자를 부풀려 두 글이 어긋난다.
            Row {
                Text(
                    "${g.eum} :",
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    // 흰 글씨는 판에서 가장 밝아 한자보다 앞으로 나온다.
                    // 訓音은 한자에 딸린 말이니 한자와 같은 색을 쓴다.
                    // 담아 둔 글자만 노랑으로 도드라진다.
                    color = if (ch.toString() in kept) Hak3.Amber else Hak3.Hanja,
                    modifier = Modifier.width(EUM).alignByBaseline(),
                )
                Text(
                    buildAnnotatedString {
                        appendInlineContent(GRADE_SLOT)
                        append(" ")
                        append(if (g.hun.isEmpty()) "訓 없음" else g.hun)
                        // 여러 표기가 있을 때, 첫 글자에 어느 표기인지 표시를 단다
                        if (s.many && i == 0) {
                            withStyle(
                                SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift(0.6f))
                            ) { append(if (s.index == 0) "  ●" else "  ${s.index}") }
                        }
                    },
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    color = Hak3.Hanja,
                    modifier = Modifier.alignByBaseline(),
                    inlineContent = mapOf(
                        GRADE_SLOT to InlineTextContent(
                            Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)
                        ) {
                            Icon(
                                painterResource(gradeIcon(g.grade)),
                                contentDescription = null,
                                tint = gradeColor(g.grade),
                            )
                        }
                    ),
                )
            }
        }
        s.variant.meaning?.let { body ->
            // 01HAKA 의 더보기 규칙 그대로 — 길면 한 줄로 접고 +/− 로 여닫는다.
            // 짧으면 표시를 두지 않되 그 자리는 비워 글 시작선이 흔들리지 않게 한다.
            var open by remember(s.variant.hanja) { mutableStateOf(false) }
            val long = body.length > 25
            Row(
                Modifier
                    .padding(start = EUM, top = 3.dp)
                    .clickable(enabled = long) { open = !open },
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    if (!long) "" else if (open) "−" else "+",
                    fontSize = 15.sp,
                    color = Hak3.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    body,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    color = Hak3.TextSoft,
                    maxLines = if (open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
