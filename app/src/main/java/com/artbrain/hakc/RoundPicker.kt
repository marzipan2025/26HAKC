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
 * 첫 화면은 회차 목록뿐이다. 이름도 판 번호도 적지 않는다 — 그런 것들은 설정에 있다.
 * 맨 앞에는 새 판 알림과 단어장이 회차와 같은 카드 모양으로 선다.
 */
@Composable
fun RoundPicker(
    exams: List<ExamRow>,
    dict: Dict?,
    onPick: (Int) -> Unit,
    onWords: () -> Unit,
) {
    val context = LocalContext.current
    // 상세 화면 카드와 같은 곡률
    val radius = (screenCornerRadius() - 8.dp).coerceAtLeast(0.dp)
    val words = remember { Collect.size(context) }

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전
    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }
    val fresh = (status as? Updater.Status.Available)?.release

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // 01HAKA 자리 — 사전은 늘 맨 위에 떠 있다
        if (dict != null) {
            Spacer(Modifier.height(8.dp))
            DictPanel(dict, radius)
            Spacer(Modifier.height(5.dp))     // 회차 칸 사이와 같은 간격
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(exams, key = { it.round }) { e -> RoundCell(e, radius, onPick) }
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
                if (live) e.date?.replace('-', '.') ?: "" else "본문 없음",
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
