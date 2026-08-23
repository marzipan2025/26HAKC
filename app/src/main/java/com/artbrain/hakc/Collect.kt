package com.artbrain.hakc

import android.content.Context

/**
 * 단어장 — 회차에서 표시한 문항을 두 갈래로 모아 둔 곳.
 *
 *   낱글자   담은 문항에 나온 한자를 낱개로. 유비무환 하나면 유·비·무·환 넷이다.
 *   문제     담은 문항을 통째로. 회차가 달라도 글이 같으면 한 장이다.
 *
 * 어느 쪽도 쌓아 두지 않는다. 회차의 표시에서 그때그때 모아 내므로 표시를 바꾸면
 * 묶음도 곧바로 따라 바뀐다.
 *
 * **푸는 길이 갈래마다 다르다.** 문제 묶음에서 푸는 것은 곧 표시를 푸는 것이라
 * 같은 글의 모든 회차분이 함께 풀린다. 낱글자 묶음은 글자와 문항이 하나씩 맞물리지
 * 않으므로, 푼 글자를 `gone_A` · `gone_K` 에 적어 그 묶음에서만 감춘다 — 그러다
 * **제 글자가 모두 풀린 문항은 그때 표시가 해제된다.** 유·비만 풀면 유비무환은
 * 노랑 그대로고, 넷을 다 풀어야 놓아 준다.
 */
object Collect {
    private const val PREFS = "collect"

    /** 묶음의 갈래. */
    enum class Kind { CHARS, CARDS }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tag(m: Mark) = if (m == Mark.AMBER) "A" else "K"

    private fun goneKey(m: Mark) = "gone_${tag(m)}"

    /** 낱글자 묶음에서 손으로 뺀 글자들. */
    private fun gone(c: Context, bin: Mark): Set<String> =
        prefs(c).getString(goneKey(bin), "")!!.split(',').filter { it.isNotEmpty() }.toSet()

    /** 그 표시가 붙은 문항들. 최신 회차부터, 회차 안에서는 문항 차례대로. */
    private fun marked(c: Context, bin: Mark): List<Pair<Int, Int>> = buildList {
        for (round in Marks.rounds(c)) {
            Marks.of(c, round).entries
                .filter { it.value == bin }
                .sortedBy { it.key }
                .forEach { add(round to it.key) }
        }
    }

    /** 낱글자 묶음. 여러 문항에 나온 글자는 처음 만난 자리에 한 번만 선다. */
    fun list(c: Context, db: ExamDb, bin: Mark): List<String> {
        val out = LinkedHashSet<String>()
        for ((round, no) in marked(c, bin)) {
            val (_, item) = db.pick(round, no) ?: continue
            out += db.hanjaOf(item)
        }
        return out.toList() - gone(c, bin)
    }

    /**
     * 문제 묶음. 회차를 가로질러 같은 글은 한 번만 — 그 글이 처음 나온 회차를 세운다.
     * 글이 비어 있는 문항(보기 묶음형)은 볼 것이 없으므로 세지 않는다.
     */
    fun cards(c: Context, db: ExamDb, bin: Mark): List<Pair<Int, Int>> {
        val seen = HashSet<String>()
        return buildList {
            for ((round, no) in marked(c, bin)) {
                val (_, item) = db.pick(round, no) ?: continue
                val key = twinKey(item) ?: continue
                if (seen.add(key)) add(round to no)
            }
        }
    }

    fun count(c: Context, db: ExamDb, bin: Mark, kind: Kind): Int =
        if (kind == Kind.CHARS) list(c, db, bin).size else cards(c, db, bin).size

    /** 글자마다 어느 묶음인지. 양쪽에 다 들면 노랑이 이긴다 — 급한 쪽이 그쪽이다. */
    fun bins(c: Context, db: ExamDb): Map<String, Mark> = buildMap {
        list(c, db, Mark.KNOWN).forEach { put(it, Mark.KNOWN) }
        list(c, db, Mark.AMBER).forEach { put(it, Mark.AMBER) }
    }

    /**
     * 낱글자를 묶음에서 빼거나 도로 넣는다. 뺀 뒤에는 제 글자가 모두 빠진 문항이
     * 있는지 훑어 그 표시를 놓아 준다 — 유비무환의 네 글자를 다 풀었을 때다.
     *
     * 훑는 것은 **푸는 그 순간뿐**이다. 이미 푼 글자로만 이루어진 문항을 나중에 새로
     * 담았다고 담자마자 풀려서는 안 된다.
     */
    fun keep(c: Context, db: ExamDb, han: String, bin: Mark, keep: Boolean) {
        val out = if (keep) gone(c, bin) - han else gone(c, bin) + han
        prefs(c).edit().putString(goneKey(bin), out.joinToString(",")).apply()
        if (!keep) release(c, db, bin, out)
    }

    private fun release(c: Context, db: ExamDb, bin: Mark, out: Set<String>) {
        val done = buildList {
            for ((round, no) in marked(c, bin)) {
                val (_, item) = db.pick(round, no) ?: continue
                val han = db.hanjaOf(item)
                // 한자가 하나도 없는 문항은 놓아 줄 셈이 서지 않는다
                if (han.isNotEmpty() && han.all { it in out }) add(round to no)
            }
        }
        if (done.isNotEmpty()) Marks.setAll(c, done, null)
    }

    /**
     * 문제 묶음에서 표시를 바꾼다. 글이 같은 문항은 회차를 가리지 않고 함께 간다 —
     * 한 문제를 풀었으면 그 문제는 어느 회차에 실렸든 푼 것이다.
     */
    fun markCards(c: Context, db: ExamDb, round: Int, no: Int, mark: Mark?) {
        val (_, item) = db.pick(round, no) ?: return
        val twins = if (twinKey(item) == null) listOf(round to no) else db.twins(item)
        Marks.setAll(c, twins, mark)
    }

    /** 회차가 달라도 이 글이면 같은 문제다. 글이 비었으면 짝지을 것이 없다. */
    fun twinKey(item: Item): String? {
        val q = item.question?.trim().orEmpty()
        if (q.isEmpty() || q.none { it.isLetterOrDigit() }) return null
        return q + "\t" + item.target.orEmpty()
    }
}
