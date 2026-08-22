package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import kotlinx.coroutines.launch

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
private val GRADE_MARK = listOf("◉", "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧")

private fun gradeMark(grade: Int?) = GRADE_MARK[(grade ?: 0).coerceIn(0, 8)]

private fun gradeColor(grade: Int?) = when (grade) {
    0 -> Color.White
    1 -> Color(0xFF2999D1)
    2 -> Color(0xFFFFEB3B)
    3 -> Color(0xFF507D2A)
    else -> Hak3.Text
}

// 01HAKA 의 창은 310×270 이고 그 안이 한자 84 · 訓音 나머지 · 입력 50 으로 나뉜다.
// 창 단추 자리(26)는 안드로이드에 올 것이 없으니 뺀 244 를 기준으로 삼는다.
private const val HEAD = 84f / 244f
private const val FOOT = 50f / 244f * 0.6f     // 입력 칸은 예전의 60%

/** 목록 쪽에서 판의 최소 높이를 셈할 때 쓴다. */
const val DICT_FOOT = FOOT
private val PAD = 9.dp

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
fun DictPanel(dict: Dict, radius: Dp, modifier: Modifier = Modifier) {
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
    val scope = rememberCoroutineScope()
    // 위 줄에서 맨 앞에 걸린 칸이 곧 지금 보고 있는 표기다
    val active by remember { derivedStateOf { top.firstVisibleItemIndex } }
    LaunchedEffect(active, slots) {
        if (slots.isNotEmpty()) mid.animateScrollToItem(active.coerceIn(slots.indices))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(Hak3.Surface)
            .background(Hak3.Rule)          // 01HAKA 의 패널 바탕 한 겹
            .padding(PAD)
    ) {
        // 안쪽 캡슐은 판 라운딩에서 여백만큼 뺀다 — 화면·카드와 같은 동심원 규칙
        val inner = (radius - PAD).coerceAtLeast(0.dp)
        val square = maxWidth                       // 정사각형이었을 때의 한 변
        val foot = square * FOOT
        val head = (maxHeight - foot - PAD * 2).coerceIn(0.dp, square * HEAD)
        val glyph = (head.value * 0.68f).sp

        Column(Modifier.fillMaxSize()) {
            // 위 캡슐 — 동음이의어를 좌우로 훑는다
            if (head > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(head)
                        .clip(RoundedCornerShape(inner))
                        .background(Hak3.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (slots.isEmpty()) {
                        Text(
                            if (text.isBlank()) "漢字" else "없다",
                            fontFamily = ThinHanja,
                            fontWeight = FontWeight.Thin,
                            fontSize = glyph,
                            color = Hak3.HanjaDim,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        )
                    } else {
                        LazyRow(
                            state = top,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(slots) { i, s ->
                                // 표시는 글자와 한 줄에 두지 않는다. 같은 글 안에서 위로
                                // 올리면 줄 상자가 그만큼 부풀어 글자가 칸 밖으로 밀린다.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        scope.launch { top.animateScrollToItem(i) }
                                    },
                                ) {
                                    Text(
                                        s.variant.hanja,
                                        fontFamily = ThinHanja,
                                        fontWeight = FontWeight.Thin,
                                        fontSize = glyph,
                                        color = if (i == active) Hak3.Hanja else Hak3.HanjaDim,
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
            }

            if (head > 0.dp) Spacer(Modifier.height(PAD))

            // 가운데 — 위에서 고른 표기의 訓音과 뜻
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(0.dp))) {
                if (slots.isEmpty()) {
                    Text(
                        if (text.isBlank()) "한글을 넣으면 한자를 찾습니다" else "찾지 못했습니다.",
                        fontSize = 13.sp,
                        color = Hak3.TextDim,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                } else {
                    LazyColumn(
                        state = mid,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        itemsIndexed(slots) { _, s -> VariantBlock(s) }
                    }
                }
            }

            Spacer(Modifier.height(PAD))

            // 아래 캡슐 — 입력
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(foot)
                    .clip(RoundedCornerShape(inner))
                    .background(Hak3.Surface)
                    .padding(start = 18.dp, end = 7.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Hak3.Text, fontSize = 19.sp),
                    cursorBrush = SolidColor(Hak3.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
                )
                if (text.isEmpty()) {
                    Text("한글을 넣으세요", fontSize = 17.sp, color = Hak3.TextDim)
                } else {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(foot * 0.62f)
                            .background(Hak3.Text, CircleShape)
                            .clickable { text = "" },
                    )
                }
            }
        }
    }
}

/** 표기 하나의 풀이 — 글자마다 `음 : 급수 훈`, 그 아래 뜻. */
@Composable
private fun VariantBlock(s: Slot) {
    Column(Modifier.padding(bottom = 12.dp)) {
        s.variant.hanja.forEachIndexed { i, ch ->
            val g = s.word.chars[ch.toString()] ?: return@forEachIndexed
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${g.eum} :",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Hak3.Text,
                    modifier = Modifier.width(34.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = gradeColor(g.grade))) {
                            append(gradeMark(g.grade))
                        }
                        append(" ")
                        append(if (g.hun.isEmpty()) "訓 없음" else g.hun)
                        // 여러 표기가 있을 때, 첫 글자에 어느 표기인지 표시를 단다
                        if (s.many && i == 0) {
                            withStyle(
                                SpanStyle(fontSize = 9.sp, baselineShift = BaselineShift(0.6f))
                            ) { append(if (s.index == 0) "  ●" else "  ${s.index}") }
                        }
                    },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Hak3.Text,
                )
            }
        }
        s.variant.meaning?.let {
            Text(
                it,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Hak3.TextDim,
                modifier = Modifier.padding(start = 38.dp, top = 3.dp),
            )
        }
    }
}
