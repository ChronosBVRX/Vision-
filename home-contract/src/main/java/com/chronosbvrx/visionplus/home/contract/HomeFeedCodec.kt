package com.chronosbvrx.visionplus.home.contract

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialización del feed. Tolera campos faltantes (una versión vieja de un lado nunca rompe
 * al otro) y rechaza esquemas que no conoce: el launcher prefiere su propia caché antes que
 * pintar datos a medias.
 */
object HomeFeedCodec {

    fun encode(feed: HomeFeed): String = JSONObject().apply {
        put("meta", JSONObject().apply {
            put("version", feed.meta.version)
            put("generatedAt", feed.meta.generatedAt)
            put("profile", feed.meta.profile)
            put("profiles", feed.meta.profiles)
            put("online", feed.meta.online)
            put("appVersion", feed.meta.appVersion)
        })
        put("hero", JSONArray().apply {
            feed.hero.forEach { item ->
                put(JSONObject().apply {
                    put("ref", item.ref)
                    put("title", item.title)
                    put("poster", item.poster)
                    put("year", item.year)
                    put("rating", item.rating)
                    put("kind", item.kind)
                    put("synopsis", item.synopsis)
                    put("durationMin", item.durationMin)
                    put("badge", item.badge)
                    put("series", item.series)
                    put("openLink", item.openLink)
                    put("playLink", item.playLink)
                })
            }
        })
        put("continue", JSONArray().apply {
            feed.continueWatching.forEach { item ->
                put(JSONObject().apply {
                    put("key", item.key)
                    put("title", item.title)
                    put("poster", item.poster)
                    put("progress", item.progress)
                    put("positionMs", item.positionMs)
                    put("durationMs", item.durationMs)
                    put("episode", item.episode)
                    put("playLink", item.playLink)
                })
            }
        })
        put("live", JSONArray().apply {
            feed.live.forEach { item ->
                put(JSONObject().apply {
                    put("code", item.code)
                    put("name", item.name)
                    put("logo", item.logo)
                    put("group", item.group)
                    put("now", item.now)
                    put("next", item.next)
                    put("progress", item.progress)
                    put("playLink", item.playLink)
                })
            }
        })
        put("rows", JSONArray().apply {
            feed.rows.forEach { row ->
                put(JSONObject().apply {
                    put("id", row.id)
                    put("label", row.label)
                    put("kind", row.kind)
                    put("items", JSONArray().apply {
                        row.items.forEach { item ->
                            put(JSONObject().apply {
                                put("ref", item.ref)
                                put("title", item.title)
                                put("poster", item.poster)
                                put("year", item.year)
                                put("rating", item.rating)
                                put("kind", item.kind)
                                put("badge", item.badge)
                                put("series", item.series)
                                put("openLink", item.openLink)
                                put("playLink", item.playLink)
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    /** Devuelve null cuando el JSON no es un feed de una versión conocida. */
    fun decode(raw: String?): HomeFeed? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val metaJson = root.optJSONObject("meta") ?: JSONObject()
            val version = metaJson.optInt("version", HomeContract.VERSION)
            if (version != HomeContract.VERSION) return null
            HomeFeed(
                meta = FeedMeta(
                    version = version,
                    generatedAt = metaJson.optLong("generatedAt", 0L),
                    profile = metaJson.optString("profile"),
                    profiles = metaJson.optInt("profiles", 1),
                    online = metaJson.optBoolean("online", true),
                    appVersion = metaJson.optString("appVersion"),
                ),
                hero = root.optJSONArray("hero").objects { o ->
                    HeroItem(
                        ref = o.optString("ref"),
                        title = o.optString("title"),
                        poster = o.optString("poster"),
                        year = o.optInt("year"),
                        rating = o.optDouble("rating", 0.0),
                        kind = o.optString("kind"),
                        synopsis = o.optString("synopsis"),
                        durationMin = o.optInt("durationMin"),
                        badge = o.optString("badge"),
                        series = o.optBoolean("series", false),
                        openLink = o.optString("openLink"),
                        playLink = o.optString("playLink"),
                    )
                },
                continueWatching = root.optJSONArray("continue").objects { o ->
                    ContinueItem(
                        key = o.optString("key"),
                        title = o.optString("title"),
                        poster = o.optString("poster"),
                        progress = o.optInt("progress").coerceIn(0, 100),
                        positionMs = o.optLong("positionMs"),
                        durationMs = o.optLong("durationMs"),
                        episode = o.optString("episode"),
                        playLink = o.optString("playLink"),
                    )
                },
                live = root.optJSONArray("live").objects { o ->
                    LiveItem(
                        code = o.optString("code"),
                        name = o.optString("name"),
                        logo = o.optString("logo"),
                        group = o.optString("group"),
                        now = o.optString("now"),
                        next = o.optString("next"),
                        progress = o.optInt("progress").coerceIn(0, 100),
                        playLink = o.optString("playLink"),
                    )
                },
                rows = root.optJSONArray("rows").objects { o ->
                    FeedRow(
                        id = o.optString("id"),
                        label = o.optString("label"),
                        kind = o.optString("kind").ifBlank { RowKind.POSTERS },
                        items = o.optJSONArray("items").objects { item ->
                            FeedItem(
                                ref = item.optString("ref"),
                                title = item.optString("title"),
                                poster = item.optString("poster"),
                                year = item.optInt("year"),
                                rating = item.optDouble("rating", 0.0),
                                kind = item.optString("kind"),
                                badge = item.optString("badge"),
                                series = item.optBoolean("series", false),
                                openLink = item.optString("openLink"),
                                playLink = item.optString("playLink"),
                            )
                        },
                    )
                },
            )
        }.getOrNull()
    }

    private inline fun <T> JSONArray?.objects(crossinline make: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            runCatching { make(o) }.getOrNull()?.let { out += it }
        }
        return out
    }
}
