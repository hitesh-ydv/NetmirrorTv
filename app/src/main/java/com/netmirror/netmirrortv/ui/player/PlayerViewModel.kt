package com.netmirror.netmirrortv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.model.PlayerResponse
import com.netmirror.netmirrortv.model.ServerItem
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.AppSession
import com.netmirror.netmirrortv.utils.RecentPlayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Success(
        val response: PlayerResponse,
        val server: String = "S1",
        val servers: List<ServerItem> = response.getEffectiveServers(server)
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadedId: String? = null
    var currentServer: String = "S1"
        private set

    fun loadStream(id: String, server: String = currentServer, force: Boolean = false) {
        if (!force && id == loadedId && server == currentServer && _uiState.value is PlayerUiState.Success) return

        currentServer = server
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            val baseUrl = AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = AppSession.currentOtt().ifBlank { prefs.getOtt() }

            if (baseUrl.isBlank()) {
                _uiState.value = PlayerUiState.Error("Server URL is not set up.")
                return@launch
            }

            val userToken = TokenStore.getUserToken(getApplication()) ?: ""
            val api = ApiClient.create(baseUrl)

            runCatching {
                api.getPlayer(
                    id = id,
                    ott = ott,
                    server = server,
                    userToken = userToken
                )
            }.onSuccess { response ->
                val streamUrl = response.playUrl
                if (!streamUrl.isNullOrBlank()) {
                    loadedId = id
                    val activeServer = response.server?.takeIf { it.isNotBlank() }
                        ?: response.servers?.firstOrNull { it.active }?.headerCode
                        ?: server
                    currentServer = activeServer
                    val serverList = response.getEffectiveServers(activeServer)
                    _uiState.value = PlayerUiState.Success(
                        response = response,
                        server = activeServer,
                        servers = serverList
                    )
                } else if (!response.error.isNullOrBlank()) {
                    _uiState.value = PlayerUiState.Error(response.error)
                } else {
                    _uiState.value = PlayerUiState.Error("Stream URL not found.")
                }
            }.onFailure { e ->
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to load stream.")
            }
        }
    }

    /**
     * Executes GET /newtv/recentplay.php after 30 seconds of video playback
     * and logs the video ID and server response.
     * Videoid header formula: type === 't' ? `SE_${sid}` : idx
     */
    fun logRecentPlay(id: String, mainId: String = "", type: String = "") {
        viewModelScope.launch {
            val baseUrl = AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = AppSession.currentOtt().ifBlank { prefs.getOtt() }

            if (baseUrl.isBlank()) {
                Log.w("PlayerRecentPlay", "Cannot send recentPlay: Base URL is empty")
                return@launch
            }

            // Videoid: type === 't' ? `SE_${sid}` : idx
            val isTv = type.equals("t", ignoreCase = true)
            val sid = mainId.ifBlank { id }
            val idx = mainId.ifBlank { id }
            val videoId = if (isTv) "SE_$sid" else idx

            // 1. Read the previous value (synchronous)
            val storedRecent = RecentPlayStore.getRecentPlay(getApplication(), ott)

            // Log ID after 30 sec when API runs
            Log.d("PlayerRecentPlay", "30s played in player! Calling recentplay.php with Videoid: '$videoId' (type='$type', sid/mainId='$sid', idx='$idx', playId='$id') | OTT: $ott | Stored Recentplay: '$storedRecent'")

            val api = ApiClient.create(baseUrl)

            // 2. Make the request
            runCatching {
                api.sendRecentPlay(
                    recentPlay = storedRecent,
                    ott = ott,
                    videoId = videoId
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    val data = response.body()
                    Log.d("PlayerRecentPlay", "recentPlay API successful for Videoid: $videoId | Response: key=${data?.key}, value=${data?.value}")

                    // 3. Persist whatever the backend returns
                    if (data != null && !data.key.isNullOrBlank() && !data.value.isNullOrBlank()) {
                        RecentPlayStore.saveRecentPlay(getApplication(), data.key, data.value)
                        Log.d("PlayerRecentPlay", "Saved recentplay to storage: [${data.key} = ${data.value}]")
                    }
                } else {
                    Log.w("PlayerRecentPlay", "recentPlay API returned error code: ${response.code()} for Videoid: $videoId")
                }
            }.onFailure { err ->
                Log.e("PlayerRecentPlay", "recentPlay request failed for Videoid: $videoId: ${err.message}", err)
            }
        }
    }
}
