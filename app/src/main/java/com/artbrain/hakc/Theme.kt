package com.artbrain.hakc

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.view.RoundedCorner

/**
 * 세 화면(사전·목록·기출 상세)이 함께 쓰는 색. 01haka 다크 모드에서 가져왔다.
 *
 * 같은 구실에는 같은 색을 쓴다 — 이름이 곧 구실이다. 쓰이지 않는 이름은 두지 않는다.
 */
object Hak3 {
    // 바탕
    val Ground = Color(0xFF000000)          // 기기 화면과 이어지는 검은 바탕
    val Surface = Color(0xFF21252D)         // 캡슐·바닥 줄처럼 판 위에 얹히는 것
    val Panel = Color(0xFF121522)           // 위의 사전 판
    val Card = Color(0xFF172A2C)            // 아래의 회차 판, 그리고 그것이 늘어난 카드
    val Knob = Color(0xFF2E323A)            // 그 위에 얹히는 것 — 바탕보다 6% 밝다
    val Rule = Color(0x24BAD0E2)            // 경계선·손잡이·꺼진 것 (#BAD0E2 @14%)

    // 글
    val Text = Color(0xF2FFFFFF)            // 본문 (백색 95%)
    val TextSoft = Color(0x92FFFFFF)        // 뜻풀이처럼 읽히되 물러나는 글 (57%)
    val TextDim = Color(0x61FFFFFF)         // 곁들이는 글 — 날짜·판 번호·라벨 (38%)

    // 한자
    val Hanja = Color(0xD9A8BAD6)           // 한자 (#A8BAD6 @85%)
    val HanjaDim = Color(0xCC647185)        // 고르지 않은 한자

    // 01haka 신호등. 애매/외움을 가르고, 알림에도 그대로 쓴다.
    val Amber = Color(0xFFFFBD2E)           // 애매하게 모름 · 새 판 알림
    val Green = Color(0xFF29C745)           // 외웠음
    val Neon = Color(0xFF3DFF6E)            // 펼쳐진 정답 — 형광 녹색
    val Red = Color(0xFFFF6157)             // 잘못된 것을 알릴 때
}

/**
 * 기기 화면의 실제 라운딩 반경. Android 12부터 WindowInsets로 읽을 수 있다.
 * 카드를 outerMargin 만큼 안으로 들이면 반경도 그만큼 줄여야 화면 곡률과 동심원이 된다.
 *
 * 창이 붙기 전에는 insets 가 비어 있다. 첫 조합에서 한 번만 읽고 말면 그 빈 값이
 * 굳어 fallback 이 그대로 남으므로, 값이 올 때까지 몇 프레임 더 들여다본다.
 * 기기 곡률이 fallback 보다 큰 폰에서는 이 차이가 눈에 띈다.
 */
@Composable
fun screenCornerRadius(fallback: Dp = 32.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val px by produceState<Int?>(null, view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@produceState
        repeat(60) {
            val r = view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            if (r != null && r > 0) {
                value = r
                return@produceState
            }
            withFrameNanos { }
        }
    }
    return px?.let { with(density) { it.toDp() } } ?: fallback
}

/**
 * 자주 찾은 한자가 오르는 밝기 계단. 열 번마다 한 단이고 넉 단이 끝이다.
 * 01HAKA 는 같은 계단을 굵기로 올렸는데, 크게 띄운 한자는 얇은 맛으로 서 있는 것이라
 * 굵기를 건드리면 그 맛이 사라진다.
 */
private val LIT = listOf(
    Hak3.Hanja,             // 0 — 처음 만나는 글자
    Color(0xE6BECCE2),      // 10회
    Color(0xF2D5DEEE),      // 20회
    Color(0xFFEAF0F9),      // 30회
    Color(0xFFFFFFFF),      // 40회 넘게
)

/** 찾은 횟수에 맞는 한자 밝기. */
fun hanjaLit(count: Int) = LIT[(count / 10).coerceIn(0, LIT.lastIndex)]

/**
 * 한자를 크게 띄우는 자리에만 쓰는 얇은 서체 — 본고딕(Source Han Sans) KR ExtraLight.
 *
 * 앞서 쓰던 Noto Sans KR Thin 에는 글리프가 3,386 자뿐이라 우리 사전 7,374 자 중
 * 5,507 자가 시스템 서체(보통 굵기)로 떨어졌다. 王·七·六·州 같은 흔한 글자도 없어서
 * 한 줄에 얇은 글자와 두꺼운 글자가 섞였다. 이 파일은 23,185 자를 담아 빠지는 것이
 * 넉 자뿐이고(屮嵇氺爫, 모두 부수라 문제에 나오지 않는다) 급수 있는 글자는 다 있다.
 *
 * 굵기 축을 가진 통짜 파일도 있었으나 축 데이터만 3.9MB 라, 한 굵기짜리 고정본을
 * 골랐다. 파일이 4.1MB 이므로 큰 한자 자리에만 쓴다 — 나머지 글은 코레일체다.
 */
val ThinHanja = FontFamily(Font(R.font.source_han_kr, FontWeight.ExtraLight))

/**
 * 앱의 기본 글씨 — 코레일체.
 *
 * 한자 글리프가 하나도 없는 한글 서체다(한글 11,172자와 숫자뿐). 글 속의 한자는
 * 안드로이드가 알아서 시스템 CJK 로 떨군다. 크게 띄우는 한자는 [ThinHanja] 를
 * 그대로 두는데, 코레일체에는 그만한 얇은 굵기(w100)도 없다.
 *
 * 굵기는 L·M·B 셋. 회차 번호처럼 크게 앉는 글자는 L 로 얇게 세운다.
 */
val Korail = FontFamily(
    Font(R.font.korail_l, FontWeight.Light),
    Font(R.font.korail_m, FontWeight.Normal),
    Font(R.font.korail_b, FontWeight.Bold),
)

/**
 * 자간을 좁히는 값. 코레일체의 한글은 글자 하나마다 좌우로 0.094em 이 비는데,
 * 그 빈 자리를 70% 로 줄인 만큼(0.3 × 0.094)을 도로 당긴다. em 으로 두어야
 * 글자 크기를 따라간다.
 */
private val KORAIL_TRACK = (-0.028f).em

private val scheme = darkColorScheme(
    primary = Hak3.Hanja,
    background = Hak3.Ground,
    surface = Hak3.Surface,
    onBackground = Hak3.Text,
    onSurface = Hak3.Text,
)

private val type = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 13.sp, letterSpacing = 1.0.sp),
)

@Composable
fun Hak3Theme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()  // 앱은 다크 톤 하나로 간다
    MaterialTheme(colorScheme = scheme, typography = type) {
        // 글씨를 하나하나 지정하는 대신 여기서 한 번 깔아 둔다. 서체를 따로 부르는
        // 자리(큰 한자)만 제 것을 쓴다.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(
                fontFamily = Korail,
                letterSpacing = KORAIL_TRACK,
            ),
            content = content,
        )
    }
}
