package com.example.eduappchatbot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.data.dataClass.GraphData
import com.example.eduappchatbot.utils.*
import com.example.eduappchatbot.ui.theme.BackgroundPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary
import com.example.eduappchatbot.ui.theme.textFieldBackgroundColor
import com.example.eduappchatbot.utils.DebugLogger
import com.example.eduappchatbot.utils.GraphUtils
import kotlinx.serialization.json.Json
import kotlin.apply
import kotlin.collections.mapIndexed
import kotlin.collections.take

/**
 * ConceptMapModel - Audio-Synchronized Knowledge Graph Visualization
 *
 * This composable renders an interactive concept map that synchronizes with audio playback.
 * Now refactored into four specialized components:
 * 1. GraphAudioSyncLogic - Handles audio synchronization
 * 2. GraphInteractionHandler - Manages user interactions
 * 3. GraphRenderLogic - Handles all rendering
 * 4. ConceptMapModel - Main orchestrator
 *
 * @param json JSON string containing graph data (nodes, edges, audioSegments)
 * @param currentAudioTime Current audio playback position in seconds
 * @param isAudioPlaying Boolean indicating if audio is currently playing
 */
@Composable
fun ConceptMapModel(
    json: String,
    currentAudioTime: Float = 0f,
    isAudioPlaying: Boolean = false
) {
    val TAG = "ConceptMapModel"
    val graphUtils = GraphUtils()

    /**
     * JSON Parsing with Error Handling
     */
    val graphData = remember(json) {
        try {
            DebugLogger.debugLog(TAG, "Raw JSON received: $json")
            val parsedData = Json.decodeFromString<GraphData>(json)

            // Compute startTime and endTime for audio segments if not provided
            val processedAudioSegments = parsedData.audioSegments.mapIndexed { index, segment ->
                if (segment.startTime == 0f && segment.endTime == 0f) {
                    val previousSegments = parsedData.audioSegments.take(index)
                    segment.computeTimes(previousSegments)
                } else {
                    segment
                }
            }

            val processedData = parsedData.copy(audioSegments = processedAudioSegments)

            DebugLogger.debugLog(TAG, "Successfully parsed GraphData:")
            DebugLogger.debugLog(TAG, "  - Main concept: ${processedData.main_concept}")
            DebugLogger.debugLog(TAG, "  - Nodes count: ${processedData.nodes.size}")
            DebugLogger.debugLog(TAG, "  - Edges count: ${processedData.edges.size}")
            DebugLogger.debugLog(TAG, "  - Audio segments count: ${processedData.audioSegments.size}")

            processedData
        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "Error parsing JSON: $e")
            DebugLogger.errorLog(TAG, "JSON content: $json")
            graphUtils.createDefaultGraphData()
        }
    }

    /**
     * Initialize specialized handlers
     */
    val audioSyncLogic = remember(graphData, currentAudioTime, isAudioPlaying) {
        GraphAudioSyncLogic(graphData, currentAudioTime, isAudioPlaying)
    }

    val interactionHandler = remember { GraphInteractionHandler() }

    val renderLogic = remember(graphData) {
        GraphRenderLogic(graphData)
    }

    /**
     * Create paint objects
     */
    val titlePaint = renderLogic.createTitlePaint()
    val textPaint = renderLogic.createTextPaint()
    val edgeLabelPaint = renderLogic.createEdgeLabelPaint()

    /**
     * Node positions map
     */
    val nodePosition = remember(json) { mutableStateMapOf<String, Offset>() }

    /**
     * Audio Synchronization State
     */
    val currentSegment = audioSyncLogic.getCurrentSegment()
    val visibleNodeIds = audioSyncLogic.getVisibleNodeIds(currentSegment)
    val highlightedNodeIds = audioSyncLogic.getHighlightedNodeIds(currentSegment)
    val highlightedEdgeIds = audioSyncLogic.getHighlightedEdgeIds(currentSegment)

    /**
     * Debug logging
     */
    audioSyncLogic.LogSyncState(
        currentSegment = currentSegment,
        visibleNodeIds = visibleNodeIds,
        highlightedNodeIds = highlightedNodeIds,
        highlightedEdgeIds = highlightedEdgeIds
    )
    audioSyncLogic.LogAudioSegments()

    /**
     * Animation scale for selected nodes
     */
    val animationScale = interactionHandler.getAnimationScale()

    /**
     * Main UI Layout
     */
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        /**
         * Canvas - Graph Rendering Surface
         */
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)

                /**
                 * Gesture Handler: Pinch-to-zoom
                 */
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        interactionHandler.updateZoom(zoom)
                        interactionHandler.updatePan(pan)
                    }
                }

                /**
                 * Gesture Handler: Node dragging
                 */
                .pointerInput(interactionHandler.scale, interactionHandler.offset, interactionHandler.canvasCenter) {
                    awaitEachGesture {
                        val down = awaitFirstDown()

                        // Convert touch position to graph space
                        val adjustedTouch = interactionHandler.screenToGraphSpace(down.position)

                        // Find touched node
                        val touchedNodeId = interactionHandler.findNodeAtPosition(
                            adjustedTouch,
                            nodePosition
                        )

                        if (touchedNodeId != null) {
                            interactionHandler.startDragging(touchedNodeId)

                            // Track drag movement
                            drag(down.id) { change ->
                                val dragAmount = change.positionChange()
                                interactionHandler.updateNodePosition(
                                    touchedNodeId,
                                    dragAmount,
                                    nodePosition
                                )
                                change.consume()
                            }

                            interactionHandler.stopDragging()
                        }
                    }
                }
        ) {
            // Calculate and store canvas center
            val center = Offset(size.width / 2, size.height / 2)
            interactionHandler.updateCanvasCenter(center)

            /**
             * Safety check - exit if no data
             */
            if (graphData.nodes.isEmpty()) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText("No data found", center.x, center.y, textPaint)
                }
                return@Canvas
            }

            /**
             * Calculate node positions (only once)
             */
            if (nodePosition.isEmpty()) {
                val calculatedPositions = renderLogic.calculateNodePositions(center)
                nodePosition.putAll(calculatedPositions)
            }

            /**
             * Apply zoom and pan transformation
             */
            withTransform({
                scale(scaleX = interactionHandler.scale, scaleY = interactionHandler.scale, pivot = center)
                translate(left = interactionHandler.offset.x, top = interactionHandler.offset.y)
            }) {
                /**
                 * Draw edges
                 */
                renderLogic.apply {
                    drawEdges(
                        nodePositions = nodePosition,
                        visibleNodeIds = visibleNodeIds,
                        highlightedEdgeIds = highlightedEdgeIds,
                        edgeLabelPaint = edgeLabelPaint
                    )

                    /**
                     * Draw nodes
                     */
                    drawNodes(
                        nodePositions = nodePosition,
                        visibleNodeIds = visibleNodeIds,
                        highlightedNodeIds = highlightedNodeIds,
                        selectedNodeId = interactionHandler.selectedNodeId,
                        animationScale = animationScale,
                        textPaint = textPaint
                    )
                }
            }

            /**
             * Draw title (not affected by zoom/pan)
             */
            renderLogic.apply {
                drawTitle(center, titlePaint)
            }
        }

        /**
         * Zoom Controls - Floating Action Buttons
         */
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            // Zoom In Button (+)
            FloatingActionButton(
                onClick = {
                    interactionHandler.updateZoom(1.25f)
                },
                modifier = Modifier.size(37.dp),
                shape = RectangleShape,
                containerColor = textFieldBackgroundColor,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = TextSecondary
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            // Zoom Out Button (-)
            FloatingActionButton(
                onClick = {
                    interactionHandler.updateZoom(1 / 1.25f)
                },
                modifier = Modifier.size(37.dp),
                shape = RectangleShape,
                containerColor = textFieldBackgroundColor,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = TextSecondary
                )
            }
        }
    }
}