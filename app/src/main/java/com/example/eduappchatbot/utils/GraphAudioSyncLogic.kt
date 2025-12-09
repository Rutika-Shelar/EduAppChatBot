package com.example.eduappchatbot.utils


import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.eduappchatbot.data.dataClass.GraphData
import com.example.eduappchatbot.data.dataClass.GraphData.AudioSegment
import kotlin.collections.filter
import kotlin.collections.find
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.map
import kotlin.collections.maxByOrNull
import kotlin.collections.take
import kotlin.collections.toSet
import kotlin.let
import kotlin.run
import kotlin.text.trimIndent

/**
 * GraphAudioSyncLogic - Handles audio synchronization for concept map
 *
 * Manages:
 * - Finding active audio segments
 * - Determining visible nodes based on audio progress
 * - Tracking highlighted nodes and edges
 */
class GraphAudioSyncLogic(
    private val graphData: GraphData,
    private val currentAudioTime: Float,
    private val isAudioPlaying: Boolean
) {
    private val TAG = "GraphAudioSyncLogic"

    /**
     * Find the current active audio segment based on playback time
     * Returns null if no segment is active
     *
     * Logic: Tstart <= currentTime <= Tend
     */
    @Composable
    fun getCurrentSegment(): AudioSegment? {
        return remember(currentAudioTime.toInt(), graphData.audioSegments) {
            if (isAudioPlaying && currentAudioTime > 0f) {
                graphData.audioSegments.find { segment ->
                    currentAudioTime >= segment.startTime &&
                            currentAudioTime <= segment.endTime
                }
            } else {
                null
            }
        }
    }

    /**
     * Build cumulative visibility set - nodes that should be visible
     * Based on all segments up to and including the current one
     *
     * FIX: Always show all nodes that have been introduced so far
     * This prevents nodes from disappearing between segments
     */
    @Composable
    fun getVisibleNodeIds(currentSegment: AudioSegment?): Set<String> {
        return remember(currentSegment, graphData.audioSegments, isAudioPlaying, currentAudioTime) {
            val visible = mutableSetOf<String>()

            if (isAudioPlaying) {
                if (currentSegment != null) {
                    // Get index of current segment
                    val currentIndex = graphData.audioSegments.indexOf(currentSegment)

                    // Accumulate all showNodeIds from segment 0 to current
                    graphData.audioSegments.take(currentIndex + 1).forEach { segment ->
                        visible.addAll(segment.showNodeIds)
                    }
                } else if (currentAudioTime > 0f) {
                    // FIX: When between segments, show all nodes from completed segments
                    // Find the last completed segment
                    val lastCompletedSegment = graphData.audioSegments
                        .filter { it.endTime <= currentAudioTime }
                        .maxByOrNull { it.endTime }

                    if (lastCompletedSegment != null) {
                        val lastIndex = graphData.audioSegments.indexOf(lastCompletedSegment)
                        graphData.audioSegments.take(lastIndex + 1).forEach { segment ->
                            visible.addAll(segment.showNodeIds)
                        }
                    }
                }
            } else {
                // When not playing, show all nodes
                visible.addAll(graphData.nodes.map { it.id })
            }

            DebugLogger.debugLog(TAG, "Visible nodes at ${currentAudioTime}s: ${visible.size} nodes")
            visible
        }
    }

    /**
     * Get nodes to highlight in current segment
     * These nodes get visual emphasis (thicker border, pulse effect)
     */
    @Composable
    fun getHighlightedNodeIds(currentSegment: AudioSegment?): Set<String> {
        return remember(currentSegment) {
            currentSegment?.highlightNodeIds?.toSet() ?: emptySet()
        }
    }

    /**
     * Get edges to highlight in current segment
     * These edges get emphasized (thicker line, brighter color)
     */
    @Composable
    fun getHighlightedEdgeIds(currentSegment: AudioSegment?): Set<String> {
        return remember(currentSegment) {
            currentSegment?.highlightEdgeIds?.toSet() ?: emptySet()
        }
    }

    /**
     * Debug logging for audio synchronization
     */
    @Composable
    fun LogSyncState(
        currentSegment: AudioSegment?,
        visibleNodeIds: Set<String>,
        highlightedNodeIds: Set<String>,
        highlightedEdgeIds: Set<String>
    ) {
        LaunchedEffect(currentSegment, currentAudioTime) {
            currentSegment?.let { segment ->
                DebugLogger.debugLog(TAG, """
                    ═══════════════════════════════════════════════════════
                    AUDIO SYNC - Segment Active
                    ═══════════════════════════════════════════════════════
                    Time: ${currentAudioTime}s (${segment.startTime}s - ${segment.endTime}s)
                    Spoken: "${segment.spokenText}"
                    Action: ${segment.action}
                    ───────────────────────────────────────────────────────
                    Visible Nodes: ${visibleNodeIds.size} → $visibleNodeIds
                    Highlighted Nodes: ${highlightedNodeIds.size} → $highlightedNodeIds
                    Highlighted Edges: ${highlightedEdgeIds.size} → $highlightedEdgeIds
                    ═══════════════════════════════════════════════════════
                """.trimIndent())
            } ?: run {
                if (isAudioPlaying && currentAudioTime > 0f) {
                    DebugLogger.debugLog(TAG, """
                        Between segments at ${currentAudioTime}s
                        Visible nodes maintained: ${visibleNodeIds.size}
                    """.trimIndent())
                }
            }
        }
    }

    /**
     * Log audio segments data for debugging
     */
    @Composable
    fun LogAudioSegments() {
        LaunchedEffect(graphData.audioSegments) {
            DebugLogger.debugLog(TAG, "Audio segments loaded: ${graphData.audioSegments.size} segments")
            graphData.audioSegments.forEachIndexed { index, segment ->
                DebugLogger.debugLog(TAG, "Segment $index: ${segment.startTime}s-${segment.endTime}s, action: ${segment.action}, nodes: ${segment.showNodeIds}")
            }
        }
    }
}