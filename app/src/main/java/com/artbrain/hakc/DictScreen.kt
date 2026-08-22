package com.artbrain.hakc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
    null -> "급수 밖"
    0 -> "특급"
    else -> "${grade}급"
}

/**
 * 사전 — 앱 맨 위에 늘 떠 있는 판.
 *
 * 01HAKA 를 그대로 옮겨 온 자리다. 입력 칸은 언제나 보이고, 찾은 것이 있으면 그 아래로
 * 카드가 펼쳐진다. 비어 있으면 판은 입력 칸 한 줄로 접히고 아래 회차 목록이 화면을
 * 다 쓴다. 결과가 길어도 회차 목록을 덮지 않도록 제 높이만큼만 차지한다.
 */
@Composable
fun DictPanel(dict: Dict, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Found>>(emptyList()) }
    val radius = (screenCornerRadius() - OUTER_D).coerceAtLeast(0.dp)

    LaunchedEffect(text) { found = dict.search(text) }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Hak3.Surface, CircleShape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Hak3.Text,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Hak3.Amber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            "한글을 넣으면 한자를 찾습니다",
                            fontSize = 15.sp,
                            color = Hak3.TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                },
            )
            if (text.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                        .background(Hak3.Rule, CircleShape)
                        .clickable { text = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 13.sp, color = Hak3.Text)
                }
            }
        }

        if (text.isNotBlank()) {
            Spacer(Modifier.height(OUTER_D))
            if (found.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Hak3.Surface, RoundedCornerShape(radius))
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("찾지 못했습니다.", fontSize = 15.sp, color = Hak3.TextDim)
                }
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(OUTER_D),
                ) {
                    items(found) { WordCard(it, radius) }
                }
            }
            Spacer(Modifier.height(OUTER_D))
        }
    }
}

@Composable
private fun WordCard(found: Found, radius: Dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .border(Dp.Hairline, Hak3.Rule, RoundedCornerShape(radius))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(found.ko, fontSize = 15.sp, color = Hak3.TextDim)
        Spacer(Modifier.height(10.dp))
        found.variants.forEach { v ->
            Text(
                v,
                fontFamily = ThinHanja,
                fontWeight = FontWeight.Thin,
                fontSize = if (v.length <= 3) 62.sp else 44.sp,
                lineHeight = if (v.length <= 3) 74.sp else 56.sp,
                color = Hak3.Hanja,
            )
        }
        if (found.glyphs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            found.glyphs.forEach { g ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        g.han,
                        fontFamily = ThinHanja,
                        fontWeight = FontWeight.Thin,
                        fontSize = 22.sp,
                        color = Hak3.Hanja,
                    )
                    Spacer(Modifier.width(8.dp))
                    // 급수는 글자 바로 옆에 둔다 — 訓音이 길어 줄이 접혀도 흔들리지 않는다
                    Text(
                        gradeName(g.grade),
                        fontSize = 11.sp,
                        color = gradeColor(g.grade),
                        modifier = Modifier.padding(top = 4.dp).width(38.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (g.hun.isEmpty()) "訓音 없음" else "${g.hun} ${g.eum}",
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        color = Hak3.Text,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        found.meaning?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 15.sp, lineHeight = 24.sp, color = Hak3.TextDim)
        }
    }
}

private val OUTER_D = 8.dp
