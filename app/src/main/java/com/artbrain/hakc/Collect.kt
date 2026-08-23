package com.artbrain.hakc

import android.content.Context

/**
 * 노랑으로 담은 문항에 나온 한자들을 한자리에 쌓아 둔 곳.
 *
 * 회차를 가로질러 한 글자씩 모으고, 이미 있는 글자는 다시 넣지 않는다. 새 글자는
 * 맨 앞에 붙으므로 최근에 만난 것부터 보인다. 여기의 노랑·일반·초록은 회차의 표시와
 * 아무 상관이 없다 — 회차에서 노랑을 풀어도 여기 쌓인 글자는 그대로 남는다.
 *
 * 적는 형식은 Marks 와 같은 결로 둔다. `order` 는 글자를 쉼표로 이은 줄,
 * `marks` 는 `濕:A,潤:K` 꼴이다. 사람이 읽고 고칠 수 있는 편이 뒤탈이 적다.
 */
object Collect {
    private const val PREFS = "collect"
    private const val KEY_ORDER = "order"
    private const val KEY_MARKS = "marks"
    private const val KEY_GONE = "gone"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 쌓인 순서. 앞이 최근에 들어온 글자다. */
    fun order(c: Context): List<String> =
        prefs(c).getString(KEY_ORDER, "")!!.split(',').filter { it.isNotEmpty() }

    /** 한 번 손으로 지운 글자. 회차를 다시 훑어도 이것들은 돌아오지 않는다. */
    private fun gone(c: Context): Set<String> =
        prefs(c).getString(KEY_GONE, "")!!.split(',').filter { it.isNotEmpty() }.toSet()

    /** 없던 글자만 앞에 붙인다. 여럿이면 넘겨준 순서를 지킨다. */
    fun register(c: Context, hanja: List<String>) {
        val had = order(c)
        val out = gone(c)
        val fresh = hanja.filter { it.isNotEmpty() && it !in had && it !in out }.distinct()
        if (fresh.isEmpty()) return
        prefs(c).edit().putString(KEY_ORDER, (fresh + had).joinToString(",")).apply()
    }

    fun marks(c: Context): Map<String, Mark> = buildMap {
        prefs(c).getString(KEY_MARKS, "")!!.split(',').forEach { part ->
            val (han, tag) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
            when (tag) {
                "A" -> Mark.AMBER
                "K" -> Mark.KNOWN
                else -> null
            }?.let { put(han, it) }
        }
    }

    /**
     * 어느 묶음에 든 글자인가. 표시가 없는 글자는 노랑으로 친다 —
     * 여기 쌓인 글자는 모두 노랑으로 담은 문항에서 온 것이라 그 자리가 맞다.
     */
    fun mark(c: Context, han: String): Mark = marks(c)[han] ?: Mark.AMBER

    /** 한 묶음에 든 글자만, 쌓인 차례대로. */
    fun list(c: Context, mark: Mark): List<String> {
        val m = marks(c)
        return order(c).filter { (m[it] ?: Mark.AMBER) == mark }
    }

    fun count(c: Context, mark: Mark): Int = list(c, mark).size

    /**
     * 표시를 옮긴다. 푸는 것은 곧 단어장에서 빼는 것이다 — 어느 묶음에도 들지 않는
     * 글자는 볼 자리가 없으니 남겨 둘 까닭이 없다. 다시 돌아오지 않도록 따로 적어 둔다.
     */
    fun set(c: Context, han: String, mark: Mark?) {
        if (mark == null) {
            drop(c, han)
            return
        }
        val m = marks(c).toMutableMap()
        m[han] = mark
        // 축은 노랑 ← 일반 → 초록이라 묶음을 건너가는 길은 일반을 지난다. 지나는
        // 참에 빠졌던 글자도 다시 색을 얻으면 돌아온다 — 지운 것으로 두지 않는다.
        val had = order(c)
        prefs(c).edit()
            .putString(KEY_MARKS, write(m))
            .putString(KEY_ORDER, (if (han in had) had else listOf(han) + had).joinToString(","))
            .putString(KEY_GONE, (gone(c) - han).joinToString(","))
            .apply()
    }

    private fun drop(c: Context, han: String) {
        val m = marks(c).toMutableMap()
        m.remove(han)
        prefs(c).edit()
            .putString(KEY_MARKS, write(m))
            .putString(KEY_ORDER, order(c).filter { it != han }.joinToString(","))
            .putString(KEY_GONE, (gone(c) + han).joinToString(","))
            .apply()
    }

    private fun write(m: Map<String, Mark>) = m.entries.joinToString(",") {
        "${it.key}:${if (it.value == Mark.AMBER) "A" else "K"}"
    }

    /**
     * 이미 노랑으로 담아 둔 문항들을 훑어 빠진 글자를 채운다.
     * 단어장을 만들기 전에 찍어 둔 표시를 살리기 위한 것이라, 앱을 열 때 한 번만 돈다.
     * 없던 글자만 들어가므로 여러 번 돌아도 달라지는 것은 없다.
     */
    fun seed(c: Context, db: ExamDb) {
        val found = mutableListOf<String>()
        for (round in Marks.rounds(c)) {                     // 최신 회차부터
            for ((no, mark) in Marks.of(c, round)) {
                if (mark != Mark.AMBER) continue
                val (_, item) = db.pick(round, no) ?: continue
                found += db.hanjaOf(item)
            }
        }
        register(c, found)
    }
}
