package com.artbrain.hakc

import android.content.Context
import java.time.LocalDate
import org.json.JSONObject

/**
 * 하루하루의 쓰임새. 아래 판의 그래프가 이것을 그린다.
 *
 *   시간 — 앱이 화면에 올라와 있던 초
 *   카드 — 넘겨 본 카드의 수 (문제 화면과 단어장을 가리지 않는다)
 *   정답 — 초록(외웠음)으로 새로 처리한 카드의 수
 *
 * 급수마다 따로 적는다 — 급수를 바꾸면 그때부터의 시간과 카드는 새 급수의 몫이다.
 * 앱이 화면에 올라와 있는 동안 모았다가 내려갈 때 오늘 칸에 더한다. 적는 곳은 급수별
 * 칸(daylog · daylog1 · daylog2)이고 날마다 `2026-09-28 → "초,카드,정답"` 한 칸이다.
 * 폴더의 기록([UserData])에도 실린다.
 */
object DayLog {

    data class Day(val date: LocalDate, val seconds: Long, val cards: Int, val known: Int)

    /** 급수별 칸의 이름. 3급은 꾸밈 없는 이름이다 — 다른 기록들과 같은 규칙. */
    fun prefsName(grade: Int) = if (grade == 3) "daylog" else "daylog$grade"

    /** 폴더에 두는 첫 기록. 앱이 하루 기록을 하나도 모르고 있을 때 한 번 들인다. */
    const val SEED = "26HAKC-daylog-seed.json"

    // 화면에 올라와 있는 동안 모으는 것. 급수마다 따로.
    private class Acc { var ms = 0L; var cards = 0; var known = 0 }
    private val open = mutableMapOf<Int, Acc>()
    private var grade = 3
    private var since = 0L

    private fun acc(g: Int) = open.getOrPut(g) { Acc() }

    private fun settle(now: Long) {
        if (since > 0) acc(grade).ms += now - since
        since = now
    }

    /** 앱이 화면에 올라왔다. */
    fun start(c: Context) {
        open.clear()
        grade = Settings.grade(c)
        since = System.currentTimeMillis()
    }

    /** 급수를 바꿨다. 여기까지의 시간은 앞 급수의 몫으로 닫는다. */
    fun gradeChanged(g: Int) {
        settle(System.currentTimeMillis())
        grade = g
    }

    /** 카드 한 장을 넘겨 보았다. */
    fun card() { acc(grade).cards++ }

    /** 카드 한 장을 정답(초록)으로 처리했다. */
    fun known() { acc(grade).known++ }

    /** 앱이 화면에서 내려갔다. 모은 것을 급수마다 오늘 칸에 더한다. 자정을 넘긴 몫도 오늘이다. */
    fun stop(c: Context) {
        if (since == 0L) return
        settle(System.currentTimeMillis())
        since = 0L
        val key = LocalDate.now().toString()
        open.forEach { (g, a) ->
            val s = a.ms / 1000
            if (s == 0L && a.cards == 0 && a.known == 0) return@forEach
            val p = c.getSharedPreferences(prefsName(g), Context.MODE_PRIVATE)
            val (s0, n0, k0) = parse(p.getString(key, null))
            p.edit().putString(key, "${s0 + s},${n0 + a.cards},${k0 + a.known}").apply()
        }
        open.clear()
    }

    private fun parse(v: String?): Triple<Long, Int, Int> {
        val f = v?.split(',') ?: return Triple(0L, 0, 0)
        return Triple(
            f.getOrNull(0)?.toLongOrNull() ?: 0L,
            f.getOrNull(1)?.toIntOrNull() ?: 0,
            f.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    /**
     * 그 급수의 첫 기록일부터 하루도 빠짐없이. 쉰 날은 0 으로 채운다 — 그래프의 가로가
     * 늘 전체 날수로 나뉘어야 쉰 날이 쉰 만큼 보인다. 어제까지는 늘 넣고, 오늘은 적힌
     * 것이 있을 때만 넣는다 — 오늘 몫은 앱이 내려갈 때 적히므로, 열자마자 들여다보면
     * 오늘이 0 으로 떨어져 보인다.
     */
    fun days(c: Context, g: Int): List<Day> {
        val all = c.getSharedPreferences(prefsName(g), Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                val d = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@mapNotNull null
                d to parse(v as? String)
            }.toMap()
        val first = all.keys.minOrNull() ?: return emptyList()
        val today = LocalDate.now()
        val last = if (today in all) today else today.minusDays(1)
        val out = mutableListOf<Day>()
        var d = first
        while (!d.isAfter(last)) {
            val (s, n, k) = all[d] ?: Triple(0L, 0, 0)
            out += Day(d, s, n, k)
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * 폴더에 첫 기록([SEED])이 있고 앱이 아직 하루 기록을 하나도 모르면 들인다.
     * 형식: `{"grades": {"3": {"2026-08-13": [초, 카드, 정답], …}, "2": {…}, "1": {…}}}`
     */
    fun importSeedIfEmpty(c: Context): Boolean {
        if (Settings.GRADES.any { days(c, it).isNotEmpty() }) return false
        val dir = DataFile.readableFolder(c) ?: return false
        val file = dir.findFile(SEED) ?: return false
        val json = runCatching {
            c.contentResolver.openInputStream(file.uri)?.use { JSONObject(it.reader().readText()) }
        }.getOrNull() ?: return false
        val grades = json.optJSONObject("grades") ?: return false
        Settings.GRADES.forEach { g ->
            val o = grades.optJSONObject("$g") ?: return@forEach
            val e = c.getSharedPreferences(prefsName(g), Context.MODE_PRIVATE).edit()
            o.keys().forEach { date ->
                val r = o.optJSONArray(date) ?: return@forEach
                e.putString(date, "${r.optLong(0)},${r.optInt(1)},${r.optInt(2)}")
            }
            e.commit()
        }
        return true
    }
}
