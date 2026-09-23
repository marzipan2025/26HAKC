package com.artbrain.hakc

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 쓰는 이의 기록을 기출 파일이 든 폴더에 함께 적어 둔다.
 *
 * 기록은 앱 안(SharedPreferences)에 사는데, 앱을 지우거나 서명이 다른 판으로 갈아
 * 끼우면 함께 사라진다. 기출 파일처럼 폰의 폴더에 한 벌 두면 앱이 몇 번을 새로
 * 깔려도 그 폴더를 다시 가리키는 순간 기록이 돌아온다.
 *
 *   적기 — 앱이 화면에서 내려갈 때마다 통째로 덮어쓴다. 몇 KB 라 나눠 쓸 까닭이 없다.
 *   읽기 — 앱 안의 기록이 하나도 없을 때(새로 깔렸을 때)만 폴더의 것을 들인다.
 *          기록이 있는데 들이면 방금 쌓은 것을 옛것으로 덮는다.
 *
 * 형식은 JSON 이다 — 사람이 열어 보고 고칠 수 있는 편이 뒤탈이 적다.
 */
object UserData {

    /** 폴더에 두는 이름. hanja 로 시작하지 않아야 기출 파일로 잘못 집히지 않는다. */
    const val NAME = "26HAKC-userdata.json"

    private const val VERSION = 1

    /** 기록이 든 칸들. 급수마다 따로 적는 것([Settings.scoped])은 급수별 이름을 모두 싣는다. */
    private fun prefNames(): List<String> =
        Settings.GRADES.flatMap { g ->
            listOf("marks", "collect").map { if (g == 3) it else "$it$g" }
        } + listOf("seen", "dict", "settings")

    /** 이 기기에서만 뜻이 있는 값. 다른 폰에 들이면 틀린 값이 된다. */
    private val DEVICE_ONLY = setOf("keyboard_px")

    /** 기록으로 셈하는 칸 — 이것들이 모두 비었으면 새로 깔린 앱이다. */
    private fun records(): List<String> = prefNames() - listOf("dict", "settings")

    private fun prefs(c: Context, name: String) =
        c.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** 지금의 기록을 폴더에 적는다. 적을 수 없는 자리면 조용히 넘어간다. */
    suspend fun save(c: Context): Boolean = withContext(Dispatchers.IO) {
        val dir = DataFile.writableFolder(c) ?: return@withContext false
        val json = JSONObject()
            .put("version", VERSION)
            .put("app", BuildConfig.VERSION_NAME)
            .put("saved", System.currentTimeMillis())
            .put("prefs", JSONObject().apply {
                prefNames().forEach { put(it, dump(prefs(c, it))) }
            })
        runCatching {
            val file = dir.findFile(NAME) ?: dir.createFile("application/json", NAME)
                ?: return@runCatching false
            c.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(json.toString(1).toByteArray())
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /**
     * 앱 안의 기록이 비어 있고 폴더에 적어 둔 것이 있으면 들인다.
     * 들였으면 참 — 그때는 급수 같은 설정도 바뀌었을 수 있다.
     */
    suspend fun restoreIfFresh(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (records().any { prefs(c, it).all.isNotEmpty() }) return@withContext false
        val dir = DataFile.readableFolder(c) ?: return@withContext false
        val file = dir.findFile(NAME) ?: return@withContext false
        val json = runCatching {
            c.contentResolver.openInputStream(file.uri)?.use { JSONObject(it.reader().readText()) }
        }.getOrNull() ?: return@withContext false
        val all = json.optJSONObject("prefs") ?: return@withContext false
        prefNames().forEach { name ->
            all.optJSONObject(name)?.let { load(prefs(c, name), it) }
        }
        true
    }

    private fun dump(p: SharedPreferences): JSONObject = JSONObject().apply {
        p.all.forEach { (k, v) ->
            if (k in DEVICE_ONLY) return@forEach
            val typed = when (v) {
                is Boolean -> JSONObject().put("b", v)
                is Int -> JSONObject().put("i", v)
                is Long -> JSONObject().put("l", v)
                is Float -> JSONObject().put("f", v.toDouble())
                is String -> JSONObject().put("s", v)
                is Set<*> -> JSONObject().put("ss", JSONArray(v.map { it.toString() }))
                else -> null
            }
            typed?.let { put(k, it) }
        }
    }

    private fun load(p: SharedPreferences, o: JSONObject) {
        val e = p.edit()
        o.keys().forEach { k ->
            val v = o.optJSONObject(k) ?: return@forEach
            when {
                v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                v.has("i") -> e.putInt(k, v.getInt("i"))
                v.has("l") -> e.putLong(k, v.getLong("l"))
                v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                v.has("s") -> e.putString(k, v.getString("s"))
                v.has("ss") -> e.putStringSet(k, v.getJSONArray("ss").let { a ->
                    (0 until a.length()).map { a.getString(it) }.toSet()
                })
            }
        }
        e.commit()
    }
}
