package com.artbrain.hakc

import android.content.Context

/**
 * 어떤 한자를 몇 번이나 찾아봤는지.
 *
 * 01HAKA 에서 가져온 규칙이다. 거기서는 열 번마다 글자가 한 단씩 굵어졌는데,
 * 크게 띄운 한자는 얇은 맛으로 서 있는 것이라 굵기를 건드리면 그 맛이 사라진다.
 * 그래서 같은 계단을 밝기로 옮겼다 — 자주 만난 글자일수록 환하다.
 *
 * 적는 형식은 Collect 와 같은 결로 둔다. `濕:3,潤:12` 꼴이라 사람이 읽고 고칠 수 있다.
 */
object Seen {
    private const val PREFS = "seen"
    private const val KEY = "count"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): Map<String, Int> = buildMap {
        prefs(c).getString(KEY, "")!!.split(',').forEach { part ->
            val (han, n) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
            n.toIntOrNull()?.let { put(han, it) }
        }
    }

    /**
     * 한 번의 찾기를 적어 둔다. 같은 글자가 여러 표기에 걸쳐 나와도 한 번으로 센다 —
     * 세는 것은 글자가 아니라 만남이다.
     */
    fun record(c: Context, hanja: List<String>) {
        val chars = hanja.flatMap { it.toList() }.filter(::isHan).map(Char::toString).distinct()
        if (chars.isEmpty()) return
        val now = all(c).toMutableMap()
        chars.forEach { now[it] = (now[it] ?: 0) + 1 }
        prefs(c).edit()
            .putString(KEY, now.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    private fun isHan(ch: Char) =
        ch in '㐀'..'䶿' || ch in '一'..'鿿' || ch in '豈'..'﫿'
}
