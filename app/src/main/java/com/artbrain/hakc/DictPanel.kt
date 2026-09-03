package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.drawWithContent
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

/**
 * 노랑 판 위의 잉크 한 벌. 어두운 판에서 쓰던 흰 글씨를 그대로 뒤집었다 — 짙기는
 * 그대로 두고 색만 검정으로 바꾼다. 노랑 위의 흰 글씨는 대비가 1.6:1 밖에 되지
 * 않아 읽히지 않는다. 어두운 판(회차 판·기출 카드)은 팔레트의 흰 잉크를 그대로 쓴다.
 */
private val INK = Color.Black.copy(alpha = 0.95f)           // 본문 · 적는 글자
private val INK_SOFT = Color.Black.copy(alpha = 0.57f)      // 물러나 읽히는 글
/**
 * 訓音 아랫줄의 뜻풀이. 검은 겹을 덮는 대신 판 색을 그대로 깊게 내린 갈색이다 —
 * 바탕 노랑과 같은 색상(41°)에 채도를 올리고 밝기를 0.40 으로 눌렀다.
 * 판 위에서 70% 로 얹혀 訓 뒤로 한 겹 물러선다 — 訓 이 먼저 읽히는 자리다.
 */
private val INK_GLOSS = Color(0xFF66490A).copy(alpha = 0.70f)
private val INK_DIM = Color.Black.copy(alpha = 0.38f)       // 곁들이는 글
private val INK_RULE = Color.Black.copy(alpha = 0.14f)      // 실선 · 꺼진 것
/** 입력 칸 오른쪽의 들임표. 실선보다 짙어 빈 칸에서도 눈에 든다. */
private val INK_ICON = Color.Black.copy(alpha = 0.40f)
private val INK_HANJA = Color.Black.copy(alpha = 0.75f)     // 한자
private val INK_HANJA_DIM = Color.Black.copy(alpha = 0.40f) // 훑고 지나가는 한자

/** 訓 앞에 끼워 넣는 급수 표시의 이름. 글 흐름을 타야 해서 인라인으로 둔다. */
private const val GRADE_SLOT = "grade"

private fun gradeColor(grade: Int?) = when (grade) {
    null -> INK.copy(alpha = 0.3f)             // 급수를 모르는 글자는 흐리게
    // 흰 표시는 노랑 판 위에서 뜨지 않는다 — 글씨와 같이 뒤집는다.
    // 색이 있는 급수(1·2·3급)는 제 색 그대로 둔다.
    0 -> INK
    1 -> Color(0xFF2999D1)
    2 -> Color(0xFFFFEB3B)
    3 -> Color(0xFF507D2A)
    else -> INK
}

// 01HAKA 의 창은 310×270 이고 그 안이 한자 84 · 訓音 나머지 · 입력 50 으로 나뉜다.
// 창 단추 자리(26)는 안드로이드에 올 것이 없으니 뺀 244 를 기준으로 삼는다.
private const val HEAD = 84f / 244f
private const val FOOT = 50f / 244f * 0.72f    // 입력 칸
/** 訓 오른쪽 표기 번호가 앉는 높이. 글자 위로 얼마나 뜨는지의 비율이다. */
private const val MARK_LIFT = 0.34f

/**
 * 한자 자리에 까는 면의 높이. 실선으로 가르던 자리를 이 면이 대신 쥔다.
 * 판 폭을 따라 자라는 한자 자리(폰에서 119dp 남짓)와 달리 못 박힌 값이라,
 * 면은 늘 그 안쪽에서 끝난다.
 */
private val HEAD_PLATE = 110.dp

/**
 * 면에 배어드는 잉크. 파랑이 0 이라 HSV 채도가 1 인, 바탕과 같은 결의 노랑이다.
 * 흰빛을 얹어 바탕보다 밝히던 것을 뒤집어 이제는 바탕보다 어두워진다.
 */
private val HEAD_PLATE_INK = Color(0xFFFFAF00)

/**
 * 그 잉크가 가장 짙은 자리(아래끝)의 알파. 위끝은 0 이라 바탕 노랑 그대로다.
 *
 * 밝기 차이는 밝히던 때와 같게 두고 방향만 뒤집었다. 폰에서 잰 값으로 —
 * 바탕 (255,191,53), 밝히던 면의 아래끝 (255,197,73), 상대 밝기로 +5.74 였다.
 * 같은 만큼 어두운 (255,185,33) 을 겨냥하는데, 잉크의 채도를 최대로 두면
 * (파랑 0) 알파가 저절로 정해진다: 1 − 33/53 = 0.377. 초록도 이 알파에서
 * 191·0.623 + 175·0.377 = 185 로 맞아떨어진다.
 */
private const val HEAD_PLATE_ALPHA = 0.377f

/**
 * 한자의 잉크가 제 줄상자 한가운데에서 아래로 치우친 만큼 — 자리 높이에 대한
 * 비율이다. 폰에서 재어 잡았다: 자리 119dp 일 때 잉크가 위로 32.00dp,
 * 아래로 24.67dp 였으니 어긋남의 절반이 3.665dp, 곧 119 의 0.0308 이다.
 */
private const val HEAD_INK_BIAS = 0.0308f

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
 * 동안 남아야 하므로 여기에 둔다.
 *
 * 앱을 껐다 켜도 보던 자리는 남는다 — 글만 적어 두었다가 다시 찾는다. 결과는
 * 담아 두지 않는다. 30만 줄을 다시 훑는 편이 그것을 통째로 적어 두는 것보다 싸고,
 * 자료가 바뀌어도 어긋나지 않는다.
 */
object DictInput {
    private const val PREFS = "dict"
    private const val KEY_QUERY = "query"
    private const val KEY_FROZEN = "frozen"

    var text by mutableStateOf("")
    var found by mutableStateOf<List<Found>>(emptyList())

    /** 엔터를 누른 횟수. 같은 글을 그대로 두고 눌러도 찾기가 새로 돌게 한다. */
    var again by mutableIntStateOf(0)

    /**
     * 지우개를 눌러 화면을 그대로 굳혀 두었는가. 굳은 동안 화면은 한 색으로
     * 물들고 손짓도 받지 않는다 — 찍어 둔 한 장처럼 선다.
     */
    var frozen by mutableStateOf(false)
        private set

    /** 굳힐 때 입력 칸에서 걷어 낸 글. 굳은 화면은 이 글의 결과다. */
    private var held by mutableStateOf("")

    /** 지금 화면이 딛고 선 글. 굳어 있으면 걷어 낸 그 글이다. */
    val query: String get() = if (frozen) held else text

    /** 지금 담긴 결과가 어느 글의 몇 번째 찾기인지. 돌아왔을 때 헛돌지 않게. */
    var done: Pair<String, Int>? = null

    /**
     * 지우개 — 적은 것만 걷고 화면은 그대로 굳힌다. 걷어 낸 글은 [held] 에
     * 남으므로 [query] 가 흔들리지 않고, 따라서 결과를 다시 캐지도 않는다.
     */
    fun freeze() {
        if (text.isEmpty()) return
        held = text
        text = ""
        frozen = true
    }

    /**
     * 입력 칸에 적힐 때. 한 글자라도 적히면 굳은 것이 풀리고 화면도 그 자리에서
     * 비워진다 — 새 결과가 오기 전까지 굳었던 글이 살아 있는 척 서 있지 않도록.
     */
    fun type(s: String) {
        if (frozen) {
            frozen = false
            held = ""
            found = emptyList()
            done = null
        }
        text = s
    }

    private var loaded = false

    /** 앱을 켤 때 한 번. 판은 회차를 다녀올 때마다 다시 서므로 한 번만 읽는다. */
    fun restore(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        frozen = p.getBoolean(KEY_FROZEN, false)
        val q = p.getString(KEY_QUERY, "").orEmpty()
        if (frozen) held = q else text = q
    }

    /**
     * 딛고 선 글이 바뀔 때마다. 짧은 글 한 줄이라 그때그때 적어도 무겁지 않다.
     * 되살리기 전에는 적지 않는다 — 아직 읽지 않은 것을 빈 글로 덮지 않으려는 빗장이다.
     */
    fun save(c: Context) {
        if (!loaded) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUERY, query)
            .putBoolean(KEY_FROZEN, frozen)
            .apply()
    }
}

/**
 * 굳은 화면. 그려진 것을 한 겹 떠 놓고 그 위를 한 색으로 덮는다 — SrcIn 이라
 * 획이 있는 자리만 갈리고 짙기는 그대로 남는다. 글마다 다르던 색이 하나로 모여
 * 찍어 둔 한 장처럼 보인다.
 */
private fun Modifier.frozen(on: Boolean): Modifier =
    if (!on) this else this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(FROZEN_INK, blendMode = BlendMode.SrcIn)
        }

/** 굳은 글의 색. 판의 노랑(#FFBD2E)을 78% 로 눌러 앉힌 것이다. */
private val FROZEN_INK = Color(0xFFC79324)

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
    // 적어 둔 것을 되살리는 일은 조합 안에서 곧바로 한다. LaunchedEffect 에
    // 두면 첫 판이 빈 글로 먼저 지나가 적어 둔 것을 덮어 버린다. 안에 빗장이
    // 있어 여러 번 불러도 한 번만 읽는다.
    remember { DictInput.restore(context) }
    val text = DictInput.text
    val found = DictInput.found
    val again = DictInput.again
    val frozen = DictInput.frozen
    // 화면이 딛고 선 글. 굳어 있으면 걷어 낸 그 글이라, 지우개를 눌러도 결과가
    // 그대로 남는다.
    val query = DictInput.query
    LaunchedEffect(query, frozen) { DictInput.save(context) }
    // 찾기는 곁줄에서 돈다. 한자로 찾으면 낱말 30만 줄을 훑으므로 화면을 붙잡고
    // 있을 일이 아니다. 이미 그 글로 찾아 둔 것이면 다시 돌지 않는다 — 회차를
    // 보고 돌아왔을 때 헛되이 한 번 더 캐지 않으려는 것이다.
    LaunchedEffect(query, again) {
        val key = query to again
        if (DictInput.done == key) return@LaunchedEffect
        DictInput.found = withContext(Dispatchers.IO) { dict.search(query) }
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
    // 위 한자 줄과 아래 訓音 줄은 한 자리를 함께 본다. 손이 닿은 쪽이 앞장서고
    // 나머지가 그 자리로 따라온다.
    //
    // **앞장선 쪽은 어떤 일이 있어도 프로그램으로 건드리지 않는다.** 손을 뗀 뒤
    // 목록이 흐르는(fling) 동안에도 마찬가지다. 전에는 손이 얹혔는지만 보고
    // 따라가게 두었는데, 톡 튀기면 손을 떼는 그 참에 남아 있던 자리로 되돌리는
    // 명령이 흐름과 맞부딪쳐 목록이 제멋대로 튀었다. 앞장선 쪽을 끝까지 놓아
    // 두면 부딪칠 일 자체가 없다.
    var active by remember(slots) { mutableIntStateOf(0) }
    val topHeld by top.interactionSource.collectIsDraggedAsState()
    val midHeld by mid.interactionSource.collectIsDraggedAsState()
    // 손이 닿는 순간 그쪽이 앞장선다. 흐름이 잦아들어도 다음에 다른 쪽을 잡기
    // 전까지는 그 쪽이 앞장선 채다.
    var lead by remember(slots) { mutableStateOf<LazyListState?>(null) }
    LaunchedEffect(slots) { snapshotFlow { topHeld }.collect { if (it) lead = top } }
    LaunchedEffect(slots) { snapshotFlow { midHeld }.collect { if (it) lead = mid } }
    // 앞장선 쪽이 어디에 있든 그 자리를 따라간다 — 끄는 동안에도, 흐르는 동안에도.
    LaunchedEffect(slots) {
        snapshotFlow { lead?.firstVisibleItemIndex }.collect { i ->
            val boss = lead
            if (boss == null || i == null || slots.isEmpty()) return@collect
            active = i.coerceIn(slots.indices)
            val other = if (boss === top) mid else top
            if (other.firstVisibleItemIndex != active) other.scrollToItem(active)
        }
    }
    // 손이 아니라 톡 눌러 자리를 옮겼을 때(또는 새로 찾았을 때)만 둘을 함께 옮긴다.
    // 어느 한쪽이라도 움직이는 중이면 그것이 정하는 중이니 비켜선다.
    LaunchedEffect(active, slots) {
        if (slots.isEmpty()) return@LaunchedEffect
        if (top.isScrollInProgress || mid.isScrollInProgress) return@LaunchedEffect
        val i = active.coerceIn(slots.indices)
        if (top.firstVisibleItemIndex != i) top.animateScrollToItem(i)
        if (mid.firstVisibleItemIndex != i) mid.animateScrollToItem(i)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(Hak3.Panel)
            // 판 색 위에 겹을 얹지 않는다 — 얹으면 노랑이 그만큼 흐려진다.
            // 위쪽의 아주 옅은 빛 한 겹만 그대로 둔다.
            .background(PanelGlow)
    ) {
        val square = maxWidth                       // 정사각형이었을 때의 한 변
        // 입력 칸은 재어 잡은 높이보다 2dp 더 자란다 — 자라는 쪽은 위다. 실선이
        // 그만큼 따라 오르고, 칸 안에 세로로 가운데 선 것들은 그 절반인 1dp 오른다.
        val sole = square * FOOT               // 지우개와 들임표는 이 높이를 따른다
        val foot = sole + 2.dp
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
        // 한자가 앉는 면. 자리가 이보다 좁아지면 면도 자리만큼만 깔린다.
        val plate = minOf(HEAD_PLATE, head)
        // 한자를 자리 한가운데에 정확히 세우려고 걷어 내는 몫. 한자의 잉크가 제
        // 줄상자 안에서 아래로 치우친 만큼이다. 글자 크기에 비례하므로 자리가
        // 아니라 glyph 를 정하는 head 에 비례한다 — 못 박힌 dp 로 두면 판을
        // 줄였을 때 지나치게 올라간다.
        val lift = head * HEAD_INK_BIAS

        // 한자 자리를 가르던 실선을 걷고, 판 위끝에서 아래로 흰빛 한 겹을 깐다.
        // 선 하나로 나누는 대신 면이 그 자리를 통째로 쥔다. 판이 둥글게 잘려
        // 있으므로 위 두 모서리는 저절로 따라 말린다. 한자 자리가 이 면보다
        // 좁아지면 면도 거기서 끝난다 — 訓音 자리로 넘어가지 않는다.
        //
        // 고르게 깔면 자리가 너무 도드라진다. 위끝은 바탕 노랑 그대로 두고
        // 아래로 내려오며 잉크가 배어들게 한다 — 투명한 검정이 아니라 '투명한
        // 제 색'에서 시작해야 중간이 잿빛으로 돌지 않는다.
        if (!folded) Box(
            Modifier
                .fillMaxWidth()
                .height(plate)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HEAD_PLATE_INK.copy(alpha = 0f),
                            HEAD_PLATE_INK.copy(alpha = HEAD_PLATE_ALPHA),
                        )
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            // 위 — 동음이의어를 좌우로 훑는다
            if (head > 0.dp) {
                // 자리의 높이는 면의 높이(plate)를 그대로 받는다. 재어 잡은 head 로
                // 두면 색이 갈리는 선은 110dp 인데 아래 訓音 이 잘리는 선은 119dp 라
                // 둘이 9dp 어긋난다. 글자 크기만 head 를 따르고 자리는 면에 맞춘다.
                Box(
                    Modifier.fillMaxWidth().height(plate).offset(y = -lift).frozen(frozen),
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
                                color = INK_HANJA_DIM,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = TEXT_WALL),
                            )
                        }
                    } else {
                        LazyRow(
                            state = top,
                            userScrollEnabled = !frozen,
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
                                                            i != active -> INK_HANJA_DIM
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
                                            fontFamily = Mono,
                                            fontSize = (glyph.value * 0.16f + 2f).sp,
                                            // 표시는 제 글자를 따라 밝아진다 — 훑고
                                            // 지나가는 표기에서는 글자와 함께 물러난다
                                            color = when {
                                                s.index == 0 && i == active -> Hak3.Red
                                                s.index == 0 -> Hak3.Red.copy(alpha = 0.45f)
                                                i == active -> INK_HANJA
                                                else -> INK_HANJA_DIM
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
            }

            // 가운데 — 위에서 고른 표기의 訓音과 뜻
            if (!folded) Box(Modifier.fillMaxWidth().weight(1f).frozen(frozen)) {
                if (slots.isNotEmpty()) {
                    LazyColumn(
                        state = mid,
                        userScrollEnabled = !frozen,
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
                    onValueChange = { DictInput.type(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = INK, fontSize = 22.sp, fontFamily = Korail),
                    // 커서는 적히는 글자와 같은 색이다
                    cursorBrush = SolidColor(INK),
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
                                // 적은 것만 걷고 화면은 그대로 굳힌다. 보고 있던
                                // 표기도 그 자리에 남아야 하므로 active 는 두었다.
                                .clickable { DictInput.freeze() }
                                .padding(
                                    start = 7.dp,
                                    top = 7.dp,
                                    bottom = 7.dp,
                                    end = 7.dp + WIPE_SHIFT,
                                )
                        ) { Wipe(sole * 0.34f) }
                    }
                    Icon(
                        painterResource(R.drawable.ic_enter),
                        contentDescription = null,
                        tint = if (text.isEmpty()) INK_ICON else INK,
                        modifier = Modifier
                            .size(sole * 0.42f)
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
private val WIPE_INK = INK_DIM.copy(alpha = INK_DIM.alpha / 2)

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
private fun Rule(color: Color = INK_RULE, thick: Dp? = null) {
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
 * 다른 실선은 비치는 14% 인데, 이 선만 58% 로 짙다 — 노랑 위에 앉은 색이
 * 전보다 30% 어두워지는 자리다.
 */
private val INPUT_RULE = Color.Black.copy(alpha = 0.58f)

/**
 * 訓音 줄의 글자 크기. 급수 표시가 앉는 자리도 이 값을 따라간다 — 표시가 글보다
 * 크면 줄 상자가 부풀어 음과 훈의 밑선이 어긋난다.
 */
private val HUNEUM = 18.sp

/** 그 아래 뜻풀이. 訓音보다 한 걸음 더 물러나 딸린 말로 읽힌다. */
private val GLOSS = 17.4.sp

/**
 * 표기 하나의 풀이 — 글자마다 `음 : 급수 훈`, 그 아래 뜻.
 * [kept] 는 단어장에 든 글자와 그 묶음이다.
 */
@Composable
private fun VariantBlock(s: Slot, kept: Map<String, Mark>) {
    Column(Modifier.padding(bottom = 15.dp)) {
        s.variant.hanja.forEachIndexed { i, ch ->
            val g = s.word.chars[ch.toString()] ?: return@forEachIndexed
            // 음과 훈은 밑선을 맞춘다. 위끝을 맞추면 오른쪽 글에 낀 급수 표시가
            // 줄 상자를 부풀려 두 글이 어긋난다.
            Row {
                Text(
                    "${g.eum} :",
                    fontSize = HUNEUM,
                    lineHeight = 27.sp,
                    // 흰 글씨는 판에서 가장 밝아 한자보다 앞으로 나온다.
                    // 訓音은 한자에 딸린 말이니 한자와 같은 색을 쓴다.
                    // 담아 둔 글자만 노랑으로 도드라진다.
                    color = kept[ch.toString()]?.let(::binColor) ?: INK_HANJA,
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
                            // 앞의 빈 칸 둘을 글자 사이 간격으로 각각 2sp 씩 줄여
                            // 표시를 4sp 왼쪽으로 당긴다 — 빈 칸을 하나 지우면
                            // 서체 폭만큼(7.8sp) 통째로 움직여 너무 붙는다.
                            withStyle(SpanStyle(letterSpacing = (-2).sp)) { append("  ") }
                            withStyle(
                                SpanStyle(
                                    fontFamily = Mono,
                                    fontSize = 13.sp,
                                    baselineShift = BaselineShift(MARK_LIFT),
                                )
                            ) { append(if (s.index == 0) "●" else "${s.index}") }
                        }
                    },
                    fontSize = HUNEUM,
                    lineHeight = 27.sp,
                    color = INK_HANJA,
                    modifier = Modifier.alignByBaseline(),
                    inlineContent = mapOf(
                        GRADE_SLOT to InlineTextContent(
                            Placeholder(HUNEUM, HUNEUM, PlaceholderVerticalAlign.TextCenter)
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
                    // 접고 펴는 자국은 그리지 않는다 — 줄이 판 너비만큼 넓어
                    // 자국이 뜨면 뜻풀이 한 줄이 통째로 번쩍인다.
                    .clickable(
                        enabled = long,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { open = !open },
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    if (!long) "" else if (open) "−" else "+",
                    fontSize = 15.sp,
                    color = INK_DIM,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    body,
                    fontSize = GLOSS,
                    lineHeight = 27.sp,
                    color = INK_GLOSS,
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
