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

    /** 노란 판정 단추를 왼쪽에 둘 것인가. 기본은 오른쪽이다. */
    fun markOnLeft(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MARK_LEFT, false)

    fun setMarkOnLeft(c: Context, left: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MARK_LEFT, left).apply()
    }

    /** 마지막으로 열어 본 회차. 목록에서 그 줄에 알약을 두른다. 없으면 0. */
    fun lastRound(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LAST_ROUND, 0)

    fun setLastRound(c: Context, round: Int) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_ROUND, round).apply()
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
        listOf("marks", "collect", "seen").forEach {
            c.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_ROUND).apply()
    }
}
