package com.netmirror.netmirrortv.network

import com.netmirror.netmirrortv.model.EpisodesPageResponse
import com.netmirror.netmirrortv.model.NavbarResponse
import com.netmirror.netmirrortv.model.OtpResponse
import com.netmirror.netmirrortv.model.PlayerResponse
import com.netmirror.netmirrortv.model.RecentPlayResponse
import com.netmirror.netmirrortv.model.SearchResponse
import com.netmirror.netmirrortv.model.TokenResponse
import com.netmirror.netmirrortv.ui.home.HomeResponse
import com.netmirror.netmirrortv.ui.ott.OttResponse
import com.yourapp.data.model.TitleDetailsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {

    @GET
    suspend fun checkServer(
        @Url url: String
    ): Response<TokenResponse>

    // Mirrors the RN screen's axios.get(`${apiUrl}/newtv/otp.php`, { headers: { Otp: ... } }) —
    // same cache-busting headers and User-Agent, `Otp` is the only per-call value.
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0",
        "X-Requested-With: NetmirrorNewTV v1.0",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.Gatu v1.0"
    )
    @GET("newtv/otp.php")
    suspend fun checkOtp(
        @Header("Otp") otp: String
    ): Response<OtpResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0",
        "X-Requested-With: NetmirrorNewTV v1.0",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    )
    @GET("newtv/ott.php")
    suspend fun getOtts(
        @Header("Usertoken") userToken: String = ""
    ): Response<OttResponse>

    @GET("newtv/navbar.php")
    suspend fun getNavbar(
        @Header("Ott") ott: String,
        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): NavbarResponse

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("newtv/main.php")
    suspend fun getHome(
        @Header("Ott") ott: String,

        @Header("Page")
        page: String = "all",

        @Header("Recentplay")
        recentPlay: String = "",

        @Header("Watchlist")
        watchlist: String = "",

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): HomeResponse

    @GET("newtv/search.php")
    suspend fun search(

        @Header("Ott")
        ott: String,

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",

        @Query("s")
        text: String?

    ): SearchResponse

    @GET("newtv/post.php")
    suspend fun getDetails(
        @Query("id")
        id: String,

        @Header("Ott")
        ott: String,

        @Header("Recentplay")
        recentPlay: String = "",

        @Header("Lastep")
        lastEp: String = "",

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",

    ): TitleDetailsResponse


    @GET("newtv/episodes.php")
    suspend fun getEpisodes(
        @Query("id")
        id: String,

        @Query("page")
        page: Int? = null,

        @Header("Ott")
        ott: String,

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",

        ): EpisodesPageResponse

    @GET("newtv/player.php")
    suspend fun getPlayer(
        @Query("id")
        id: String,

        @Header("Ott")
        ott: String,

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): PlayerResponse

    @GET("newtv/recentplay.php")
    suspend fun sendRecentPlay(
        @Header("Recentplay")
        recentPlay: String,

        @Header("Ott")
        ott: String,

        @Header("Videoid")
        videoId: String,

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): Response<RecentPlayResponse>

    @GET("newtv/recentplay.php")
    suspend fun deleteRecentPlay(
        @Header("Recentplay")
        recentPlay: String,

        @Header("Ott")
        ott: String,

        @Header("Deleteid")
        deleteId: String,

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): Response<RecentPlayResponse>

    @GET("newtv/watchlist.php")
    suspend fun toggleWatchlist(
        @Header("Watchlist")
        watchlist: String,

        @Header("Ott")
        ott: String,

        @Header("Videoid")
        videoId: String,

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): Response<RecentPlayResponse>

    @GET("newtv/watchlist.php")
    suspend fun deleteWatchlist(
        @Header("Watchlist")
        watchlist: String,

        @Header("Ott")
        ott: String,

        @Header("Deleteid")
        deleteId: String,

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0"
    ): Response<RecentPlayResponse>
}