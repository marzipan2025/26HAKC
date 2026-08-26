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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * 판 위쪽에 얹는 빛. 흰빛 5% 로 시작해 판 높이의 75% 에서 스러진다.
 * 있는 줄 모르고 지나칠 만큼만 — 판이 위에서 조금 들린 것처럼 보이게 하는 몫이다.
 */
private val GLOW = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.05f),
    0.75f to Color.Transparent,
)

/** 실선이 벽에서 물러나는 거리. */
private val WALL = 16.dp

/** 글이 벽에서 물러나는 거리. 모서리가 둥그니 실선보다 더 안쪽에서 시작한다. */
private val TEXT_WALL = 26.dp
private val RULE_GAP = 10.dp

/** 訓音 줄에서 음이 차지하는 너비. 음은 늘 한 글자라 이만큼이면 넉넉하다. */
private val EUM = 30.dp

/** 訓音 자리는 두 줄에 한 줄 남짓 여백까지는 있어야 읽을 만하다. */
private val MID_MIN = 76.dp

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
/**
 * 사전에 적어 둔 글과 그 결과. 회차를 열었다 돌아오면 판이 조합에서 빠졌다가 다시
 * 서는데, remember 로 붙잡아 둔 것은 그때 함께 사라진다. 적은 것은 앱이 살아 있는
 * 동안 남아야 하므로 여기에 둔다 — 앱을 다시 켜면 비어 있다.
 */
object DictInput {
    var text by mutableStateOf("")
    var found by mutableStateOf<List<Found>>(emptyList())

    /** 엔터를 누른 횟수. 같은 글을 그대로 두고 눌러도 찾기가 새로 돌게 한다. */
    var again by mutableIntStateOf(0)

    /** 지금 담긴 결과가 어느 글의 몇 번째 찾기인지. 돌아왔을 때 헛돌지 않게. */
    var done: Pair<String, Int>? = null

    /** 지우개를 눌렀을 때. 적은 것도 결과도 함께 걷는다. */
    fun clear() {
        text = ""
        found = emptyList()
        done = null
    }
}

/** 단어장 묶음의 색. */
private fun binColor(m: Mark) = if (m == Mark.AMBER) Hak3.Pink else Hak3.Green

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DictPanel(
    dict: Dict,
    kept: Map<String, Mark>,
    radius: Dp,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    // 자주 찾은 글자일수록 환하게. 세는 것은 엔터를 눌렀을 때뿐이다 —
    // 글자마다 도는 찾기까지 세면 한 낱말 적는 사이에 열 번이 지나간다.
    var seen by remember { mutableStateOf(Seen.all(context)) }
    val haptic = LocalHapticFeedback.current
    val text = DictInput.text
    val found = DictInput.found
    val again = DictInput.again
    // 찾기는 곁줄에서 돈다. 한자로 찾으면 낱말 30만 줄을 훑으므로 화면을 붙잡고
    // 있을 일이 아니다. 이미 그 글로 찾아 둔 것이면 다시 돌지 않는다 — 회차를
    // 보고 돌아왔을 때 헛되이 한 번 더 캐지 않으려는 것이다.
    LaunchedEffect(text, again) {
        val key = text to again
        if (DictInput.done == key) return@LaunchedEffect
        DictInput.found = withContext(Dispatchers.IO) { dict.search(text) }
        DictInput.done = key
    }

    val slots = remember(found) {
        // 한 벌로 줄지어 선 것에는 몇 번째인지를 붙인다. 한자로 찾으면 낱말이
        // 잔뜩 서는데 그 사이에서 지금 어디를 보고 있는지는 번호가 알려 준다.
        // 여기서는 1 부터 센다 — 한 낱말의 여러 표기가 아니라 저마다 다른 낱말이라
        // 첫 자리를 ● 로 둘 까닭이 없다.
        val many = found.size > 1 && found.all { it.series }
        var n = 0
        found.flatMap { w ->
            w.variants.mapIndexed { i, v ->
                if (many) Slot(w, ++n, v, true) else Slot(w, i, v, w.variants.size > 1)
            }
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
            .background(Hak3.Panel)
            .background(Hak3.Rule)          // 01HAKA 의 패널 바탕 한 겹
            .background(GLOW)               // 위쪽에 얹히는 아주 옅은 빛 한 겹
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
                        // 못 찾았다고 따로 말하지 않는다. 빈 자리에 선 이 두 글자가
                        // 아무것도 걸리지 않았다는 뜻이다 — 치는 동안 이 자리에서
                        // 글자가 나타났다 사라지는 것만으로 넉넉하다.
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "漢字",
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
                                    // 길게 누르면 그 표기가 통째로 클립보드에 얹힌다
                                    modifier = Modifier.combinedClickable(
                                        onClick = { active = i },
                                        onLongClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                            copy(context, s.variant.hanja)
                                        },
                                    ),
                                ) {
                                    Text(
                                        buildAnnotatedString {
                                            s.variant.hanja.forEach { ch ->
                                                // 담아 둔 글자는 노랑으로. 훑고 지나가는
                                                // 표기에서는 흐리게 두어 지금 보는 것이
                                                // 어느 표기인지는 그대로 알아보게 한다.
                                                val bin = kept[ch.toString()]
                                                withStyle(
                                                    SpanStyle(
                                                        color = when {
                                                            bin != null && i == active -> binColor(bin)
                                                            bin != null ->
                                                                binColor(bin).copy(alpha = 0.45f)
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
                                            // 표시는 제 글자를 따라 밝아진다 — 훑고
                                            // 지나가는 표기에서는 글자와 함께 물러난다
                                            color = when {
                                                s.index == 0 && i == active -> Hak3.Red
                                                s.index == 0 -> Hak3.Red.copy(alpha = 0.45f)
                                                i == active -> Hak3.Hanja
                                                else -> Hak3.HanjaDim
                                            },
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

            if (!folded) Rule(INPUT_RULE)

            // 아래 — 입력. 01HAKA 처럼 안내 문구를 두지 않는다.
            Box(
                Modifier.fillMaxWidth().height(foot).padding(start = TEXT_WALL, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { DictInput.text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Hak3.Text, fontSize = 22.sp, fontFamily = Korail),
                    cursorBrush = SolidColor(Hak3.Pink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // 키보드의 찾기 단추도 엔터와 같은 일을 한다
                    keyboardActions = KeyboardActions(onSearch = {
                        Seen.record(context, found.flatMap { w -> w.variants.map { it.hanja } })
                        seen = Seen.all(context)
                        DictInput.again++
                        active = 0
                        focus.clearFocus()
                        ime?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 지우개가 서면 그만큼 글자가 설 자리를 물린다
                        .padding(end = if (text.isEmpty()) 52.dp else 52.dp + WIPE_ROOM)
                        .onFocusChanged { onFocus(it.isFocused) },
                )
                // 오른쪽 끝 — 지우개와 엔터. 엔터의 첫 번째 누름은 키보드를 접고,
                // 그다음부터는 찾기를 다시 돌린다. 데스크톱에서 엔터가 하던 일이 그것이다.
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 적기 시작해야 선다. 누르면 적은 것도 결과도 함께 걷힌다.
                    if (text.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable {
                                    DictInput.clear()
                                    active = 0
                                }
                                .padding(
                                    start = 7.dp,
                                    top = 7.dp,
                                    bottom = 7.dp,
                                    end = 7.dp + WIPE_SHIFT,
                                )
                        ) { Wipe(foot * 0.34f) }
                    }
                    Icon(
                        painterResource(R.drawable.ic_enter),
                        contentDescription = null,
                        tint = if (text.isEmpty()) Hak3.Rule else Hak3.Text,
                        modifier = Modifier
                            .size(foot * 0.42f)
                            .clickable {
                                Seen.record(
                                    context,
                                    found.flatMap { w -> w.variants.map { it.hanja } },
                                )
                                seen = Seen.all(context)
                                DictInput.again++
                                active = 0
                                focus.clearFocus()
                                ime?.hide()
                            },
                    )
                }
            }
        }
    }
}

/** 지우개가 차지하는 폭 — 표와 그 둘레의 여백, 그리고 엔터와의 사이. */
private val WIPE_ROOM = 40.dp

/** 지우개를 엔터에서 이만큼 더 떼어 놓는다. */
private val WIPE_SHIFT = 6.dp

/** 지우개의 잉크. 곁들이는 글보다 한 겹 더 물러난다. */
private val WIPE_INK = Hak3.TextDim.copy(alpha = Hak3.TextDim.alpha / 2)

/**
 * 적은 것을 통째로 지우는 표. 면을 채우고 그 위에 ✕ 를 도려낸다 — 흔히 보는
 * 그 꼴이다. 도려내려면 제 층에서 그려야 하므로 층을 따로 판다.
 */
@Composable
private fun Wipe(size: Dp) {
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        drawCircle(WIPE_INK)
        val arm = this.size.minDimension * 0.22f
        val c = center
        listOf(1f, -1f).forEach { way ->
            drawLine(
                Color.Black,
                Offset(c.x - arm, c.y - arm * way),
                Offset(c.x + arm, c.y + arm * way),
                strokeWidth = this.size.minDimension * 0.10f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Clear,
            )
        }
    }
}

/**
 * 한자를 클립보드에 얹는다. 안드로이드 13부터는 얹힌 것을 시스템이 제 손으로
 * 알려 주므로 우리는 잠자코 있는다.
 */
private fun copy(c: Context, han: String) {
    val clip = c.getSystemService(ClipboardManager::class.java) ?: return
    clip.setPrimaryClip(ClipData.newPlainText("hanja", han))
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(c, han, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 세 부분을 가르는 실선. 벽에서 16dp 물러난다.
 * 높이는 1물리픽셀 — Dp.Hairline 은 0dp 라서 칸으로 쓰면 아무것도 안 그려진다.
 */
@Composable
private fun Rule(color: Color = Hak3.Rule, thick: Dp? = null) {
    val one = with(LocalDensity.current) { 1.toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WALL)
            .height(thick ?: one)
            // 두께가 있으면 양 끝이 둥글게 말린다. 한 픽셀짜리에서는 보이지 않는다.
            .background(color, CircleShape)
    )
}

/**
 * 입력 칸 바로 위의 실선. 적는 자리를 가르는 선이라 또렷하게 둔다.
 * 다른 실선은 비치는 14% 인데, 이 선만 40% 로 짙다.
 */
private val INPUT_RULE = Hak3.Rule.copy(alpha = 0.40f)

/**
 * 표기 하나의 풀이 — 글자마다 `음 : 급수 훈`, 그 아래 뜻.
 * [kept] 는 단어장에 든 글자와 그 묶음이다.
 */
@Composable
private fun VariantBlock(s: Slot, kept: Map<String, Mark>) {
    Column(Modifier.padding(bottom = 12.dp)) {
        s.variant.hanja.forEachIndexed { i, ch ->
            val g = s.word.chars[ch.toString()] ?: return@forEachIndexed
            // 음과 훈은 밑선을 맞춘다. 위끝을 맞추면 오른쪽 글에 낀 급수 표시가
            // 줄 상자를 부풀려 두 글이 어긋난다.
            Row {
                Text(
                    "${g.eum} :",
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    // 흰 글씨는 판에서 가장 밝아 한자보다 앞으로 나온다.
                    // 訓音은 한자에 딸린 말이니 한자와 같은 색을 쓴다.
                    // 담아 둔 글자만 노랑으로 도드라진다.
                    color = kept[ch.toString()]?.let(::binColor) ?: Hak3.Hanja,
                    // 너비를 못 채우면 줄이 갈리므로 한 줄로 못 박는다
                    maxLines = 1,
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
                                SpanStyle(fontSize = 13.sp, baselineShift = BaselineShift(0.5f))
                            ) { append(if (s.index == 0) "  ●" else "  ${s.index}") }
                        }
                    },
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    color = Hak3.Hanja,
                    modifier = Modifier.alignByBaseline(),
                    inlineContent = mapOf(
                        GRADE_SLOT to InlineTextContent(
                            Placeholder(19.sp, 19.sp, PlaceholderVerticalAlign.TextCenter)
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
            // 01HAKA 의 더보기 규칙 그대로 — 넘치면 한 줄로 접고 +/− 로 여닫는다.
            // 넘치는지는 글자 수로 어림하지 않고 그려 본 뒤에 안다. 어림하면 스물몇
            // 자짜리가 판 폭에 따라 한 줄을 넘길 때 말줄임만 남고 + 가 서지 않는다.
            // 표시가 없어도 그 자리는 비워 둔다 — 글 시작선이 흔들리지 않게.
            var open by remember(s.variant.hanja) { mutableStateOf(false) }
            var long by remember(body) { mutableStateOf(false) }
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
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    color = Hak3.TextSoft,
                    maxLines = if (open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    // 접힌 채로만 잰다. 펼치고 나면 넘칠 일이 없어 그때의 답은 늘
                    // 거짓이고, 그것을 믿으면 펼친 순간 +/− 가 사라진다.
                    onTextLayout = { if (!open) long = it.hasVisualOverflow },
                )
            }
        }
    }
}
