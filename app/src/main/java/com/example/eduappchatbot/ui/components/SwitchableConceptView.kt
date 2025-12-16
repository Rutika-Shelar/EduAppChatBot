package com.example.eduappchatbot.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.eduappchatbot.ui.theme.BackgroundSecondary
import com.example.eduappchatbot.ui.theme.BrandPrimary
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import org.json.JSONObject

data class ContentTab(val title: String, val icon: ImageVector, val content: @Composable () -> Unit)

@Composable
fun SwitchableConceptView(
    json: String,
    currentAudioTime: Float,
    isAudioPlaying: Boolean,
    imageUrl: String? = null,
    videoUrl: String? = null,
    imageDescription: String? = null
) {

    val tabs = buildList {
        if (isValidConceptMap(json)) {
            add(ContentTab("Concept Map", Icons.Default.Map) {
                ConceptMapModel(json, currentAudioTime, isAudioPlaying)
            })
        }
        videoUrl?.let { url ->
            extractYoutubeId(url)?.let {
                add(ContentTab("Video", Icons.Default.VideoLibrary) {
                    VideoPlayer(url, currentAudioTime, isAudioPlaying)
                })
            }
        }
        if (!imageUrl.isNullOrBlank() && imageUrl != "null") {
            add(ContentTab("Image", Icons.Default.Image) {
                ImageViewer(imageUrl, imageDescription)
            })
        }
    }

    // Don't render anything if no tabs available
    if (tabs.isEmpty()) return

    var selectedTab by remember { mutableIntStateOf(0) }

    // Reset selected tab if it's out of bounds
    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundSecondary)) {
        if (tabs.size > 1) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundSecondary,
                contentColor = BrandPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = BrandPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, null) },
                        text = { Text(tab.title) },
                        selectedContentColor = BrandPrimary,
                        unselectedContentColor = BrandPrimary.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            tabs[selectedTab].content()
        }
    }
}

@Composable
private fun VideoPlayer(videoUrl: String, currentAudioTime: Float, isAudioPlaying: Boolean) {
    val videoId = extractYoutubeId(videoUrl) ?: return
    val lifecycle = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    lifecycle.lifecycle.addObserver(this)
                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                            isLoading = false
                            if (currentAudioTime > 0f) youTubePlayer.loadVideo(videoId, currentAudioTime)
                            else youTubePlayer.cueVideo(videoId, 0f)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = BrandPrimary)
    }

    LaunchedEffect(currentAudioTime, isAudioPlaying) {
        if (isAudioPlaying) player?.seekTo(currentAudioTime)
    }

    DisposableEffect(lifecycle) {
        onDispose { player = null }
    }
}

@Composable
private fun ImageViewer(imageUrl: String, description: String?) {
    var isLoading by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false // Hide zoom buttons, keep pinch-to-zoom
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadUrl(imageUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) CircularProgressIndicator(color = BrandPrimary)
    }
}

private fun extractYoutubeId(url: String): String? {
    val patterns = listOf(
        "(?:youtube\\.com/watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]{11})",
        "youtube\\.com/embed/([A-Za-z0-9_-]{11})"
    )
    patterns.forEach { pattern ->
        Regex(pattern).find(url)?.groupValues?.get(1)?.let { return it }
    }
    return if (url.length == 11 && url.matches(Regex("[A-Za-z0-9_-]{11}"))) url else null
}

/**
 * Validates if the concept map JSON is valid and not a default/empty map
 */
private fun isValidConceptMap(json: String): Boolean {
    if (json.isBlank()) return false

    try {
        val jsonObj = JSONObject(json)

        // Check if it has the required fields
        if (!jsonObj.has("main_concept") || !jsonObj.has("nodes")) {
            return false
        }

        val mainConcept = jsonObj.optString("main_concept", "").trim()
        val nodes = jsonObj.optJSONArray("nodes")

        // Filter out default concept maps by main_concept text
        val defaultConcepts = setOf(
            "Loading...",
            "Chat for a Concept Map",
            "loading...", // lowercase variant
            "chat for a concept map" // lowercase variant
        )

        if (mainConcept.lowercase() in defaultConcepts.map { it.lowercase() }) {
            return false
        }

        // Check if nodes array exists and is not empty
        if (nodes == null || nodes.length() == 0) {
            return false
        }

        // If there's only one node, check if it's a default placeholder node
        if (nodes.length() == 1) {
            val singleNode = nodes.getJSONObject(0)
            val nodeId = singleNode.optString("id", "").trim()
            val nodeLabel = singleNode.optString("label", "").trim()
            val nodeCategory = singleNode.optString("category", "").trim()

            // Check against the exact default node: {"id": "A", "label": "Concept", "category": "Core"}
            if (nodeId == "A" &&
                (nodeLabel.equals("Concept", ignoreCase = true) ||
                        nodeLabel.equals("Loading...", ignoreCase = true)) &&
                nodeCategory.equals("Core", ignoreCase = true)) {
                return false
            }
        }

        // Valid concept map should have at least 2 nodes for meaningful visualization
        return nodes.length() >= 2

    } catch (e: Exception) {
        // If JSON parsing fails, consider it invalid
        return false
    }
}

fun hasValidTabs(
    json: String,
    imageUrl: String?,
    videoUrl: String?
): Boolean {
    var tabCount = 0

    if (isValidConceptMap(json)) {
        tabCount++
    }

    videoUrl?.let { url ->
        if (extractYoutubeId(url) != null) {
            tabCount++
        }
    }

    if (!imageUrl.isNullOrBlank() && imageUrl != "null") {
        tabCount++
    }

    return tabCount > 0
}