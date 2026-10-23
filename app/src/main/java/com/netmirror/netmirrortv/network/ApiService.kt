package com.netmirror.netmirrortv.network

import com.netmirror.netmirrortv.model.EpisodesPageResponse
import com.netmirror.netmirrortv.model.NavbarResponse
import com.netmirror.netmirrortv.model.OtpResponse
import com.netmirror.netmirrortv.model.PlayerResponse
import com.netmirror.netmirrortv.model.RecentPlayResponse
import com.netmirror.netmirrortv.model.SearchResponse
import com.netmirror.netmirrortv.model.TokenResponse
import com.netmirror.netmirrortv.model.UpdateMessageResponse
import com.netmirror.netmirrortv.ui.home.HomeResponse
import com.netmirror.netmirrortv.ui.ott.OttResponse
import com.yourapp.data.model.TitleDetailsResponse
import com.netmirror.netmirrortv.utils.Constants
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
        "X-Requested-With: NetmirrorNewTV v1.0"
    )
    @GET("otp.php")
    suspend fun checkOtp(
        @Header("Otp") otp: String,
        @Header("User-Agent") userAgent: String = Constants.DEFAULT_USER_AGENT
    ): Response<OtpResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0",
        "X-Requested-With: NetmirrorNewTV v1.0"
    )
    @GET("ott.php")
    suspend fun getOtts(
        @Header("Usertoken") userToken: String = "",
        @Header("User-Agent") userAgent: String = Constants.DEFAULT_USER_AGENT
    ): Response<OttResponse>

    @GET("navbar.php")
    suspend fun getNavbar(
        @Header("Ott") ott: String,
        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            Constants.DEFAULT_USER_AGENT
    ): NavbarResponse

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("main.php")
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
            Constants.DEFAULT_USER_AGENT
    ): HomeResponse

    @GET("search.php")
    suspend fun search(

        @Header("Ott")
        ott: String,

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            Constants.DEFAULT_USER_AGENT,

        @Query("s")
        text: String?

    ): SearchResponse

    @GET("post.php")
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
            Constants.DEFAULT_USER_AGENT,

    ): TitleDetailsResponse


    @GET("episodes.php")
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
            Constants.DEFAULT_USER_AGENT,

        ): EpisodesPageResponse

    @GET("player.php")
    suspend fun getPlayer(
        @Query("id")
        id: String,

        @Header("Ott")
        ott: String,

        @Header("server")
        server: String = "S1",

        @Header("Usertoken")
        userToken: String = "",

        @Header("X-Requested-With")
        request: String = "NetmirrorNewTV v1.0",

        @Header("User-Agent")
        userAgent: String =
            Constants.DEFAULT_USER_AGENT
    ): PlayerResponse

    @GET("recentplay.php")
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
            Constants.DEFAULT_USER_AGENT
    ): Response<RecentPlayResponse>

    @GET("recentplay.php")
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
            Constants.DEFAULT_USER_AGENT
    ): Response<RecentPlayResponse>

    @GET("watchlist.php")
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
            Constants.DEFAULT_USER_AGENT
    ): Response<RecentPlayResponse>

    @GET("watchlist.php")
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
            Constants.DEFAULT_USER_AGENT
    ): Response<RecentPlayResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0",
        "X-Requested-With: NetmirrorNewTV v1.0"
    )
    @GET("update-msg.php")
    suspend fun getUpdateMessage(
        @Header("User-Agent") userAgent: String = Constants.DEFAULT_USER_AGENT
    ): Response<UpdateMessageResponse>
}