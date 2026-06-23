package org.aarchdroid.dragonterminal.ui.term

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

private const val PREFS_NAME = "recent_tools"
private const val KEY_JSON = "keys"
private const val MAX_SIZE = 20

fun saveRecentTool(context: Context, toolKey: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_JSON, "[]") ?: "[]"
    val arr = try { JSONArray(json) } catch (_: JSONException) { JSONArray() }
    val list = mutableListOf<String>()
    for (i in 0 until arr.length()) list.add(arr.getString(i))
    list.remove(toolKey)
    list.add(0, toolKey)
    while (list.size > MAX_SIZE) list.removeAt(list.lastIndex)
    prefs.edit().putString(KEY_JSON, JSONArray(list.toList()).toString()).apply()
}

fun getRecentTools(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_JSON, "[]") ?: "[]"
    val arr = try { JSONArray(json) } catch (_: JSONException) { JSONArray() }
    val list = mutableListOf<String>()
    for (i in 0 until arr.length()) list.add(arr.getString(i))
    return list
}
