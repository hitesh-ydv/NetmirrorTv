package com.yourapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response shape for GET /newtv/post.php?id={id}
 * Covers both movie ("m") and series ("t") payloads — the fields that
 * only apply to series (season, episodes, last_ep, nextPage*) are nullable.
 */
data class TitleDetailsResponse(
    val status: String,
    val d_lang: String?,
    val ott: String?,
    val usertoken: String?,
    val main_poster: String?,
    val ep_poster: String?,
    val morelike_poster: String?,
    val ott_logo: OttLogo?,
    val title_name: String?,
    val row: String?,
    val img_referer: String?,
    val title: String,
    val main_id: String,
    val year: String?,
    val ua: String?,
    val runtime: String?,
    val hdsd: String?,
    val type: String?, // "m" = movie, "t" = series
    val desc: String?,
    val moredetails: List<MoreDetail>?,
    val season: List<Season>?,
    val episodes: List<Episode?>?,
    val lang: List<Lang>?,
    val nextPageShow: Int?,
    val nextPage: Int?,
    val nextPageSeason: String?,
    val last_ep: String?,
    val resume_title: String?,
    val suggest: List<Suggest>?,
    val error: String?
) {
    val isSeries: Boolean get() = type == "t"
    val isMovie: Boolean get() = type == "m"

    /**
     * main_poster comes back as a template like:
     *   https://imgcdn.kim/poster/h/------------------.jpg
     * with a run of dashes standing in for the real id. Swap it for main_id
     * to get a loadable URL.
     */
    val posterUrl: String?
        get() = main_poster?.let { resolveTemplateUrl(it, main_id) }
}

private val DASH_PLACEHOLDER = Regex("-{4,}")

private fun resolveTemplateUrl(template: String, id: String): String =
    template.replace(DASH_PLACEHOLDER, id)

data class OttLogo(
    val logo: String,
    val maxh: Int?,
    val maxw: Int?
)

data class MoreDetail(
    val k: String,
    val v: String
)

data class Lang(
    val l: String,
    val s: String
)

data class Season(
    val s: String,
    val id: String,
    val selected: Boolean?
)

data class Episode(
    val complate: String?,
    val id: String,
    val t: String?,
    val ep: String?,
    val ep_desc: String?,
    val info: List<String>?
)

data class Suggest(
    val id: String
)