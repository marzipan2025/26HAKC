package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 급수 색. 특급이 가장 짙고 8급으로 갈수록 옅어진다. */
private fun gradeColor(grade: Int?) = when (grade) {
    0 -> Color(0xFFB388FF)
    1 -> Color(0xFF7C9CFF)
    2 -> Color(0xFF4FC3F7)
    3 -> Color(0xFF66BB6A)
    4 -> Color(0xFF9CCC65)
    5 -> Color(0xFFD4E157)
    6 -> Color(0xFFFFCA28)
    7 -> Color(0xFFFFA726)
    8 -> Color(0xFFFF7043)
    else -> Hak3.HanjaDim
}

private fun gradeName(grade: Int?) = when (grade) {
    null -> "밖"
    0 -> "특급"
    else -> "${grade}급"
}

// 01HAKA 의 창은 310×270 이고 그 안이 한자 84 · 訓音 나머지 · 입력 50 으로 나뉜다.
// 창 단추 자리(26)는 안드로이드에 올 것이 없으니 뺀 244 를 기준으로 삼는다.
private const val HEAD = 84f / 244f
private const val FOOT = 50f / 244f

/**
 * 사전 — 목록 맨 위에 앉는 정사각형 판.
 *
 * 01HAKA 를 그대로 옮겨 온 자리다. 위에서부터 한자 표시, 訓音, 입력 칸으로 나뉘고
 * 가운데 칸만 바탕을 한 겹 눌러 둔 것까지 같다(01HAKA 의 패널 바탕과 여기 경계선
 * 색이 원래 같은 값이다). 앱 안에 앱이 하나 더 있는 것처럼 보이도록 정사각형으로
 * 잡고, 아래 회차 목록과는 카드 사이 간격만큼만 띄운다.
 */
@Composable
fun DictPanel(dict: Dict, radius: Dp, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Found>>(emptyList()) }
    LaunchedEffect(text) { found = dict.search(text) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(radius))
            .background(Hak3.Surface)
    ) {
        val head = maxHeight * HEAD
        val foot = maxHeight * FOOT
        val glyph = (head.value * 0.62f).sp

        Column(Modifier.fillMaxSize()) {
            // 위 — 찾아낸 한자. 없으면 '漢字' 가 그 자리를 지킨다.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(head)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, top = 8.dp, end = 14.dp),
            ) {
                if (found.isEmpty()) {
                    Text(
                        if (text.isBlank()) "漢字" else "없다",
                        fontFamily = ThinHanja,
                        fontWeight = FontWeight.Thin,
                        fontSize = glyph,
                        lineHeight = glyph * 1.1f,
                        color = Hak3.HanjaDim,
                    )
                } else {
                    Column {
                        found.forEach { word ->
                            word.variants.forEach { v ->
                                Text(
                                    v,
                                    fontFamily = ThinHanja,
                                    fontWeight = FontWeight.Thin,
                                    fontSize = glyph,
                                    lineHeight = glyph * 1.1f,
                                    color = Hak3.Hanja,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // 가운데 — 訓音. 이 칸만 바탕을 한 겹 눌러 둔다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Hak3.Rule)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val glyphs = found.flatMap { it.glyphs }
                if (glyphs.isEmpty()) {
                    Text(
                        if (text.isBlank()) "한글을 넣으면 한자를 찾습니다" else "찾지 못했습니다.",
                        fontSize = 13.sp,
                        color = Hak3.TextDim,
                    )
                } else {
                    glyphs.forEach { g ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                g.han,
                                fontFamily = ThinHanja,
                                fontWeight = FontWeight.Thin,
                                fontSize = 20.sp,
                                color = Hak3.Hanja,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                gradeName(g.grade),
                                fontSize = 10.sp,
                                color = gradeColor(g.grade),
                                modifier = Modifier.padding(top = 4.dp).width(30.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (g.hun.isEmpty()) "訓音 없음" else "${g.hun} ${g.eum}",
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = Hak3.Text,
                            )
                        }
                    }
                }
                found.firstOrNull()?.meaning?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 20.sp, color = Hak3.TextDim)
                }
            }

            // 아래 — 입력 칸
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(foot)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Hak3.Text, fontSize = 18.sp),
                    cursorBrush = SolidColor(Hak3.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
                if (text.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(26.dp)
                            .background(Hak3.Rule, CircleShape)
                            .clickable { text = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", fontSize = 12.sp, color = Hak3.Text)
                    }
                }
            }
        }
    }
}
