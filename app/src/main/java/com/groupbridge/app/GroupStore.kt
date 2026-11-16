package com.groupbridge.app

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Group(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var members: List<Member> = emptyList(),
)

/** Saves groups and settings on the device (SharedPreferences, nothing leaves the phone). */
class GroupStore(context: Context) {

    private val prefs = context.getSharedPreferences("group_bridge", Context.MODE_PRIVATE)

    var myNumber: String
        get() = prefs.getString(KEY_MY_NUMBER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MY_NUMBER, v).apply()

    var countryCode: String
        get() = prefs.getString(KEY_COUNTRY, "1") ?: "1"
        set(v) = prefs.edit().putString(KEY_COUNTRY, v.filter { it.isDigit() }.ifEmpty { "1" }).apply()

    /** "auto", ";" or "," — how numbers are joined when opening the texting app. */
    var separatorSetting: String
        get() = prefs.getString(KEY_SEPARATOR, "auto") ?: "auto"
        set(v) = prefs.edit().putString(KEY_SEPARATOR, v).apply()

    val separator: String
        get() = when (separatorSetting) {
            ";", "," -> separatorSetting
            // Samsung Messages expects commas; Google Messages and most others use semicolons.
            else -> if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) "," else ";"
        }

    var includeEmailsInText: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_EMAILS, false)
        set(v) = prefs.edit().putBoolean(KEY_INCLUDE_EMAILS, v).apply()

    fun all(): List<Group> {
        val raw = prefs.getString(KEY_GROUPS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Group? = all().firstOrNull { it.id == id }

    fun save(group: Group) {
        val groups = all().toMutableList()
        val i = groups.indexOfFirst { it.id == group.id }
        if (i >= 0) groups[i] = group else groups.add(group)
        write(groups)
    }

    fun delete(id: String) = write(all().filterNot { it.id == id })

    private fun write(groups: List<Group>) {
        val arr = JSONArray()
        groups.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    private fun toJson(g: Group) = JSONObject().apply {
        put("id", g.id)
        put("name", g.name)
        put("members", JSONArray().apply {
            g.members.forEach { m -> put(JSONObject().put("address", m.address).put("kind", m.kind.name)) }
        })
    }

    private fun fromJson(o: JSONObject): Group {
        val arr = o.optJSONArray("members") ?: JSONArray()
        val members = (0 until arr.length()).mapNotNull {
            val m = arr.getJSONObject(it)
            val kind = runCatching { AddressKind.valueOf(m.getString("kind")) }.getOrNull()
            kind?.let { k -> Member(m.getString("address"), k) }
        }
        return Group(o.getString("id"), o.optString("name"), members)
    }

    companion object {
        private const val KEY_GROUPS = "groups"
        private const val KEY_MY_NUMBER = "my_number"
        private const val KEY_COUNTRY = "country_code"
        private const val KEY_SEPARATOR = "separator"
        private const val KEY_INCLUDE_EMAILS = "include_emails"
    }
}
