package com.artbrain.hakc

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** 한자 한 글자 — 訓과 音, 그리고 급수. */
data class Glyph(val han: String, val hun: String, val eum: String, val grade: Int?)

/** 같은 소리로 적히는 한자 표기 하나. */
data class Variant(val hanja: String, val meaning: String?)

/** 찾아낸 낱말 하나. 같은 소리에 여러 한자가 있으면 variants 에 모두 담긴다. */
data class Found(
    val ko: String,
    val variants: List<Variant>,
    val chars: Map<String, Glyph>,
)

/**
 * 한자 사전. libhangul 자료를 구운 `dict.db` 를 읽는다.
 *
 * 기출 데이터와 달리 이 사전은 앱 안에 넣는다 — 공개된 자료이고 바뀔 일이 없다.
 * SQLite 는 실제 파일이어야 열리므로 처음 한 번만 앱 안으로 베껴 둔다.
 */
class Dict private constructor(private val db: SQLiteDatabase) {

    /**
     * 입력한 글에서 한자로 적히는 낱말을 찾는다.
     *
     * 앞에서부터 가장 긴 낱말을 먼저 집는다 — '고사성어' 가 '고사' 와 '성어' 로
     * 갈리지 않게 하려는 것이다. 한자를 그대로 넣으면 글자마다 訓音을 돌려준다.
     */
    fun search(text: String): List<Found> {
        val clean = text.trim().replace('（', '(').replace('）', ')')
        if (clean.isEmpty()) return emptyList()
        if (clean.startsWith("/")) return reverse(clean.drop(1).trim(), clean)
        // 데스크톱(01HAKA)에서 쓰던 괄호도 그대로 받는다
        if (clean.startsWith("(") && clean.endsWith(")"))
            return reverse(clean.drop(1).dropLast(1).trim(), clean)
        if (clean.length == 1 && isHangul(clean[0])) return byEum(clean)
        if (clean.any(::isHan)) return listOf(ofHanja(clean))

        val out = mutableListOf<Found>()
        var i = 0
        while (i < clean.length) {
            if (!isHangul(clean[i])) {
                i++
                continue
            }
            var hit: Found? = null
            var len = minOf(MAX_WORD, clean.length - i)
            while (len >= MIN_WORD) {
                val piece = clean.substring(i, i + len)
                if (piece.all(::isHangul)) {
                    val found = lookup(piece)
                    if (found != null) {
                        hit = found
                        break
                    }
                }
                len--
            }
            if (hit != null) {
                out.add(hit)
                i += hit.ko.length
            } else {
                i++
            }
        }
        return out
    }

    /**
     * 한글 낱말 하나를 찾는다. 없으면 null.
     *
     * 원본 파일에 적힌 차례를 그대로 지킨다 — 그 순서가 곧 쓸모의 차례라서
     * '이사' 를 넣으면 二四 가 아니라 移徙 가 먼저 온다.
     */
    private fun lookup(ko: String): Found? = db.rawQuery(
        "SELECT hanja, meaning FROM words WHERE ko=? ORDER BY seq",
        arrayOf(ko)
    ).use { c ->
        val variants = mutableListOf<Variant>()
        while (c.moveToNext()) {
            variants.add(Variant(c.getString(0), c.getString(1)?.takeIf { it.isNotBlank() }))
        }
        if (variants.isEmpty()) return@use null
        Found(ko, variants, glyphs(variants.joinToString("") { it.hanja }))
    }

    /**
     * 거꾸로 찾기 — 낱말이 아니라 글자를 찾는다. 01HAKA 는 괄호로 감쌌지만
     * 폰에서는 양쪽을 맞추기가 번거로워 맨 앞의 슬래시 하나로 대신한다.
     *
     *     /어미 모    훈과 음을 함께 넣으면 그 글자를 콕 집는다
     *     /어미       훈만 넣으면 훈에 그 말이 든 한자를 모두 모은다
     */
    private fun reverse(inner: String, shown: String): List<Found> {
        val parts = inner.split(' ').filter { it.isNotEmpty() }
        return pack(shown, when {
            parts.size >= 2 -> byHunEum(parts.dropLast(1).joinToString(" "), parts.last())
            parts.size == 1 -> byHun(parts[0])
            else -> emptyList()
        })
    }

    /**
     * 한글 한 글자 — 그 음으로 읽는 한자를 모두. 잔뜩 걸리는 것이 이 길의 쓸모다.
     * 8급이 가장 흔하니 그쪽부터 세우고, 급수 없는 글자를 맨 뒤로 보낸다.
     */
    private fun byEum(eum: String): List<Found> = pack(eum, db.rawQuery(
        "SELECT han, hun, eum, grade FROM chars WHERE eum=? " +
            "ORDER BY grade IS NULL, grade DESC",
        arrayOf(eum)
    ).use { c -> buildList { while (c.moveToNext()) add(row(c)) } })

    /**
     * 찾아낸 글자들을 한 낱말의 여러 표기처럼 담는다 — 위 줄에서 좌우로 훑으면
     * 아래 訓音이 따라오는 그 얼개를 그대로 쓰려는 것이다.
     */
    private fun pack(shown: String, hits: List<Glyph>): List<Found> =
        if (hits.isEmpty()) emptyList()
        else listOf(Found(shown, hits.map { Variant(it.han, null) }, hits.associateBy { it.han }))

    /**
     * 훈과 음을 함께 준 경우. 음이 같은 글자만 보고 훈을 맞춘다.
     * 꼭 맞는 것을 앞에, 스쳐 맞는 것(어머니↔어미)을 뒤에 둔다.
     */
    private fun byHunEum(hun: String, eum: String): List<Glyph> {
        val exact = mutableListOf<Glyph>()
        val near = mutableListOf<Glyph>()
        db.rawQuery(
            "SELECT han, hun, eum, grade FROM chars WHERE eum=?", arrayOf(eum)
        ).use { c ->
            while (c.moveToNext()) {
                val g = row(c)
                val parts = g.hun.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                when {
                    parts.any { it == hun } -> exact.add(g)
                    parts.any { it.contains(hun) || hun.contains(it) } -> near.add(g)
                }
            }
        }
        // 8급이 가장 흔하다. 아는 글자를 앞에 세운다.
        return (exact + near).sortedByDescending { it.grade ?: -1 }
    }

    /** 훈만 준 경우. 훈에 그 말이 든 글자를 다 모은다. 흔한 글자(8급)부터. */
    private fun byHun(hun: String): List<Glyph> = db.rawQuery(
        "SELECT han, hun, eum, grade FROM chars WHERE hun LIKE ? " +
            "ORDER BY grade IS NULL, grade DESC",
        arrayOf("%$hun%")
    ).use { c ->
        buildList { while (c.moveToNext()) add(row(c)) }
    }

    private fun row(c: android.database.Cursor) = Glyph(
        c.getString(0),
        c.getString(1) ?: "",
        c.getString(2) ?: "",
        if (c.isNull(3)) null else c.getInt(3),
    )

    /** 한자를 그대로 넣었을 때 — 글자마다 訓音만 돌려준다. */
    private fun ofHanja(text: String): Found {
        val only = text.filter(::isHan)
        return Found(text, listOf(Variant(only, null)), glyphs(only))
    }

    /** 글자마다 訓音과 급수를. 같은 글자는 한 번만. */
    private fun glyphs(source: String): Map<String, Glyph> {
        val seen = LinkedHashSet<Char>()
        source.filter(::isHan).forEach { seen.add(it) }
        return seen.associate { ch ->
            ch.toString() to db.rawQuery(
                "SELECT hun, eum, grade FROM chars WHERE han=?", arrayOf(ch.toString())
            ).use { c ->
                if (c.moveToNext()) {
                    Glyph(
                        ch.toString(),
                        c.getString(0) ?: "",
                        c.getString(1) ?: "",
                        if (c.isNull(2)) null else c.getInt(2),
                    )
                } else {
                    Glyph(ch.toString(), "", "", null)
                }
            }
        }
    }

    companion object {
        private const val MAX_WORD = 8

        /** 한 글자짜리는 낱말로 치지 않는다 — '한' 같은 것이 잔뜩 걸린다. */
        private const val MIN_WORD = 2

        private fun isHan(c: Char) =
            c in '㐀'..'䶿' || c in '一'..'鿿' || c in '豈'..'﫿'

        private fun isHangul(c: Char) = c in '가'..'힣'

        /**
         * SQLite 는 실제 파일이어야 열리므로 앱 안으로 한 번 베껴 둔다.
         * 판이 바뀌면 사전도 바뀌었을 수 있으니 그때는 다시 베낀다 — 안 그러면
         * 예전 사본이 남아 새 자료를 못 읽는다.
         */
        fun open(context: Context): Dict? = try {
            val out = File(context.filesDir, "dict.db")
            val stamp = File(context.filesDir, "dict.stamp")
            val now = BuildConfig.VERSION_CODE.toString()
            if (!out.exists() || out.length() == 0L ||
                !stamp.exists() || stamp.readText() != now
            ) {
                context.assets.open("dict.db").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                stamp.writeText(now)
            }
            Dict(SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY))
        } catch (_: Exception) {
            null
        }
    }
}
