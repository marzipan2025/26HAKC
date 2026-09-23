package com.artbrain.hakc

import android.content.Context

/**
 * 손에 맞춰 두는 것들. 지금은 바닥 두 단추의 좌우 뿐이다.
 * 기록(Marks)과 달리 회차를 가리지 않으므로 한 자리에 둔다.
 */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_MARK_LEFT = "mark_left"
    private const val KEY_LAST_ROUND = "last_round"
    private const val KEY_KEYBOARD = "keyboard_px"
    private const val KEY_GRADE = "grade"

    /**
     * 폴더에 적어 둔 기록을 이 앱이 한 번 들였는가([UserData.restoreIfFresh]).
     * 다 지운 뒤에도 참으로 둔다 — 지운 것을 폴더에서 도로 들이면 지운 것이 아니다.
     * 이 기기에서만 뜻이 있는 값이라 폴더에 적지 않는다([UserData.DEVICE_ONLY]).
     */
    const val KEY_IMPORTED = "imported"

    /** 고를 수 있는 급수. 급수마다 기출 파일(hanja1·hanja3)이 따로 있다. */
    val GRADES = listOf(1, 3)

    /** 지금 보는 급수. 기본은 3급 — 앱이 처음 3급만 들고 나왔다. */
    fun grade(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_GRADE, 3)
            .takeIf { it in GRADES } ?: 3

    fun setGrade(c: Context, grade: Int) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_GRADE, grade).apply()
    }

    /**
     * 급수마다 따로 적는 기록의 이름. 회차 번호는 급수끼리 겹치므로(3급 113회와
     * 1급 113회) 한 곳에 적으면 표시가 남의 문항에 붙는다. 3급은 원래 이름 그대로
     * 두어 여태 쌓인 기록을 잇고, 다른 급수는 뒤에 번호를 단다 — marks → marks1.
     */
    fun scoped(c: Context, name: String): String =
        grade(c).let { if (it == 3) name else "$name$it" }

    fun imported(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_IMPORTED, false)

    fun setImported(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IMPORTED, true).apply()
    }

    /** 노란 판정 단추를 왼쪽에 둘 것인가. 기본은 오른쪽이다. */
    fun markOnLeft(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MARK_LEFT, false)

    fun setMarkOnLeft(c: Context, left: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MARK_LEFT, left).apply()
    }

    /** 마지막으로 열어 본 회차. 목록에서 그 줄에 알약을 두른다. 없으면 0. */
    fun lastRound(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(scoped(c, KEY_LAST_ROUND), 0)

    fun setLastRound(c: Context, round: Int) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(scoped(c, KEY_LAST_ROUND), round).apply()
    }

    /**
     * 여태 본 키보드의 높이(px). 기기와 자판이 정해지면 늘 같은 값이라, 한 번
     * 보아 둔 것을 적어 두었다가 다음부터는 인셋이 오기 전에 미리 셈한다.
     * 본 적이 없으면 0 — 그때는 인셋이 올 때까지 기다린다.
     */
    fun keyboard(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_KEYBOARD, 0)

    fun setKeyboard(c: Context, px: Int) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_KEYBOARD, px).apply()
    }

    /**
     * 그간 쌓인 사용 기록을 통으로 지운다 — 회차의 표시, 단어장에서 뺀 글자,
     * 사전에서 자주 찾은 횟수, 마지막으로 열어 본 회차까지.
     *
     * 데이터 파일을 어디서 읽는지와 노랑 단추의 좌우는 쓰임새의 기록이 아니라
     * 이 기기의 채비라 그대로 둔다.
     */
    fun wipe(c: Context) {
        // 급수마다 따로 적은 기록까지 모두 — 지금 보는 급수만이 아니다
        val scopedNames = GRADES.flatMap { g ->
            listOf("marks", "collect").map { if (g == 3) it else "$it$g" }
        }
        (scopedNames + "seen").forEach {
            c.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            GRADES.forEach { g -> remove(if (g == 3) KEY_LAST_ROUND else "$KEY_LAST_ROUND$g") }
            // 다 지운 뒤에도 '한 번 들였다' 를 세워 둔다. 그러지 않으면 기록이 비었다는
            // 이유로 폴더의 것을 도로 들여, 지운 것이 되살아나고 급수도 폴더에 적힌
            // 것으로 끌려간다 — 지우고 나서 급수를 바꿔도 곧장 되돌아오던 까닭이다.
            putBoolean(KEY_IMPORTED, true)
        }.apply()
    }
}
