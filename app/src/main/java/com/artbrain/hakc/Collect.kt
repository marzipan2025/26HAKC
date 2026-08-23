package com.artbrain.hakc

import android.content.Context

/**
 * 단어장 — 회차에서 표시한 문항에 나온 한자를 묶음별로 모아 둔 곳.
 *
 * 쌓아 두지 않는다. 노랑 묶음은 노랑으로 담은 모든 문항의 한자를, 초록 묶음은
 * 초록으로 담은 모든 문항의 한자를 그때그때 모아 낸다(중복은 한 번만). 회차에서
 * 표시를 바꾸면 묶음도 곧바로 따라 바뀐다.
 *
 * 손으로 지우는 것만 남는다. 묶음 안에서 뺀 글자는 `gone_A` · `gone_K` 에 적어
 * 두고 그 묶음에서만 감춘다 — 노랑에서 뺐다고 초록에서까지 사라지지는 않는다.
 */
object Collect {
    private const val PREFS = "collect"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tag(m: Mark) = if (m == Mark.AMBER) "A" else "K"

    private fun goneKey(m: Mark) = "gone_${tag(m)}"

    /** 그 묶음에서 손으로 뺀 글자들. */
    private fun gone(c: Context, bin: Mark): Set<String> =
        prefs(c).getString(goneKey(bin), "")!!.split(',').filter { it.isNotEmpty() }.toSet()

    /**
     * 한 묶음에 든 글자. 최근 회차부터, 회차 안에서는 문항 차례대로.
     * 여러 문항에 나온 글자는 처음 만난 자리에 한 번만 선다.
     */
    fun list(c: Context, db: ExamDb, bin: Mark): List<String> {
        val out = LinkedHashSet<String>()
        for (round in Marks.rounds(c)) {                     // 최신 회차부터
            Marks.of(c, round).entries
                .filter { it.value == bin }
                .sortedBy { it.key }
                .forEach { (no, _) ->
                    val (_, item) = db.pick(round, no) ?: return@forEach
                    out += db.hanjaOf(item)
                }
        }
        return out.toList() - gone(c, bin)
    }

    fun count(c: Context, db: ExamDb, bin: Mark): Int = list(c, db, bin).size

    /** 글자마다 어느 묶음인지. 양쪽에 다 들면 노랑이 이긴다 — 급한 쪽이 그쪽이다. */
    fun bins(c: Context, db: ExamDb): Map<String, Mark> = buildMap {
        list(c, db, Mark.KNOWN).forEach { put(it, Mark.KNOWN) }
        list(c, db, Mark.AMBER).forEach { put(it, Mark.AMBER) }
    }

    /** 묶음에서 빼거나 도로 넣는다. 회차의 표시는 건드리지 않는다. */
    fun keep(c: Context, han: String, bin: Mark, keep: Boolean) {
        val out = if (keep) gone(c, bin) - han else gone(c, bin) + han
        prefs(c).edit().putString(goneKey(bin), out.joinToString(",")).apply()
    }
}
