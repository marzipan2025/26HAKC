package com.artbrain.hakc

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** 한자 한 글자 — 訓과 音, 그리고 급수. */
data class Glyph(val han: String, val hun: String, val eum: String, val grade: Int?)

/** 찾아낸 낱말 하나. 같은 소리에 여러 한자가 있으면 variants 에 모두 담긴다. */
data class Found(
    val ko: String,
    val variants: List<String>,
    val glyphs: List<Glyph>,
    val meaning: String?,
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
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
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

    /** 한글 낱말 하나를 찾는다. 없으면 null. */
    private fun lookup(ko: String): Found? = db.rawQuery(
        "SELECT hanja, meaning FROM words WHERE ko=? ORDER BY length(hanja), hanja",
        arrayOf(ko)
    ).use { c ->
        val variants = mutableListOf<String>()
        var meaning: String? = null
        while (c.moveToNext()) {
            variants.add(c.getString(0))
            if (meaning == null) meaning = c.getString(1)
        }
        if (variants.isEmpty()) return@use null
        Found(ko, variants, glyphs(variants.joinToString("")), meaning)
    }

    /** 한자를 그대로 넣었을 때 — 글자마다 訓音만 돌려준다. */
    private fun ofHanja(text: String): Found {
        val only = text.filter(::isHan)
        return Found(text, listOf(only), glyphs(only), null)
    }

    /** 글자마다 訓音과 급수를. 같은 글자는 한 번만. */
    private fun glyphs(source: String): List<Glyph> {
        val seen = LinkedHashSet<Char>()
        source.filter(::isHan).forEach { seen.add(it) }
        return seen.mapNotNull { ch ->
            db.rawQuery("SELECT hun, eum, grade FROM chars WHERE han=?", arrayOf(ch.toString()))
                .use { c ->
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

        fun open(context: Context): Dict? = try {
            val out = File(context.filesDir, "dict.db")
            if (!out.exists() || out.length() == 0L) {
                context.assets.open("dict.db").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            Dict(SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY))
        } catch (_: Exception) {
            null
        }
    }
}
