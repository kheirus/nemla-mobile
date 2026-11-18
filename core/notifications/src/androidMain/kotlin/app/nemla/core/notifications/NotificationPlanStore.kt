package app.nemla.core.notifications

import android.content.Context
import app.nemla.core.domain.model.AppLanguage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class PlannedNotice(
    val requestCode: Int,
    val group: String,
    val channel: NoticeChannel,
    val language: AppLanguage,
    val title: String,
    val body: String,
    val atMillis: Long,
) {
    val text: NoticeText get() = NoticeText(title, body)

    override fun toString(): String = "PlannedNotice(requestCode=$requestCode, group=$group, atMillis=$atMillis)"
}

internal class NotificationPlanStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): List<PlannedNotice> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toPlanned() }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    @Synchronized
    fun find(requestCode: Int): PlannedNotice? = read().firstOrNull { it.requestCode == requestCode }

    @Synchronized
    fun replaceGroup(
        group: String,
        planned: List<PlannedNotice>,
    ) {
        write(read().filterNot { it.group == group } + planned)
    }

    @Synchronized
    fun keepFuture(nowMillis: Long): List<PlannedNotice> {
        val future = read().filter { it.atMillis > nowMillis }
        write(future)
        return future
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private fun write(planned: List<PlannedNotice>) {
        val array = JSONArray()
        planned.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private fun PlannedNotice.toJson(): JSONObject =
        JSONObject()
            .put(REQUEST_CODE, requestCode)
            .put(GROUP, group)
            .put(CHANNEL, channel.id)
            .put(LANGUAGE, language.tag)
            .put(TITLE, title)
            .put(BODY, body)
            .put(AT, atMillis)

    private fun JSONObject.toPlanned(): PlannedNotice? {
        if (!has(REQUEST_CODE) || !has(AT)) return null
        return PlannedNotice(
            requestCode = optInt(REQUEST_CODE),
            group = optString(GROUP),
            channel = NoticeChannel.fromId(optString(CHANNEL)),
            language = AppLanguage.fromTag(optString(LANGUAGE)),
            title = optString(TITLE),
            body = optString(BODY),
            atMillis = optLong(AT),
        )
    }

    private companion object {
        const val FILE = "nemla.notifications"
        const val KEY = "plan.v1"
        const val REQUEST_CODE = "requestCode"
        const val GROUP = "group"
        const val CHANNEL = "channel"
        const val LANGUAGE = "language"
        const val TITLE = "title"
        const val BODY = "body"
        const val AT = "at"
    }
}
