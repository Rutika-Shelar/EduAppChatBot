package com.example.eduappchatbot.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.ranges.coerceIn

/**
 * GraphInteractionHandler - Manages user interactions with the graph
 *
 * Handles:
 * - Node selection and dragging
 * - Zoom level management
 * - Pan offset management
 * - Touch position calculations
 */
class GraphInteractionHandler(
    private val nodeRadius: Float = 90f
) {
    private val TAG = "GraphInteractionHandler"

    var selectedNodeId by mutableStateOf<String?>(null)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var canvasCenter by mutableStateOf(Offset.Zero)
        private set

    /**
     * Smooth animation when a node is selected (dragged)
     * Selected nodes grow to 115% of normal size
     */
    @Composable
    fun getAnimationScale(): Float {
        val animationScale by animateFloatAsState(
            targetValue = if (selectedNodeId != null) 1.15f else 1f,
            label = "node_scale_animation"
        )
        return animationScale
    }

    /**
     * Update zoom level with constraints
     */
    fun updateZoom(zoomFactor: Float) {
        scale = (scale * zoomFactor).coerceIn(0.25f, 4f)
        DebugLogger.debugLog(TAG, "Zoom updated: scale = $scale")
    }

    /**
     * Update pan offset
     */
    fun updatePan(panDelta: Offset) {
        offset += panDelta / scale
    }

    /**
     * Update canvas center
     */
    fun updateCanvasCenter(center: Offset) {
        canvasCenter = center
    }

    /**
     * Convert touch position from screen space to graph space
     */
    fun screenToGraphSpace(touchPosition: Offset): Offset {
        return (touchPosition - offset - canvasCenter) / scale + canvasCenter
    }

    /**
     * Find which node (if any) is at the given position
     */
    fun findNodeAtPosition(
        position: Offset,
        nodePositions: Map<String, Offset>
    ): String? {
        return nodePositions.entries.find { (_, pos) ->
            position.getDistanceTo(pos) <= nodeRadius
        }?.key
    }

    /**
     * Start dragging a node
     */
    fun startDragging(nodeId: String) {
        selectedNodeId = nodeId
        DebugLogger.debugLog(TAG, "Started dragging node: $nodeId")
    }

    /**
     * Update node position during drag
     */
    fun updateNodePosition(
        nodeId: String,
        dragDelta: Offset,
        nodePositions: MutableMap<String, Offset>
    ) {
        nodePositions[nodeId]?.let { currentPosition ->
            nodePositions[nodeId] = currentPosition + (dragDelta / scale)
        }
    }

    /**
     * Stop dragging
     */
    fun stopDragging() {
        selectedNodeId?.let { nodeId ->
            DebugLogger.debugLog(TAG, "Stopped dragging node: $nodeId")
        }
        selectedNodeId = null
    }

    /**
     * Check if a node is currently selected
     */
    fun isNodeSelected(nodeId: String): Boolean {
        return selectedNodeId == nodeId
    }

    /**
     * Reset all transformations
     */
    fun reset() {
        scale = 1f
        offset = Offset.Zero
        selectedNodeId = null
        DebugLogger.debugLog(TAG, "Reset all transformations")
    }
}

/**
 * Extension function: Calculate distance between two points
 * Uses Pythagorean theorem: d = √((x2-x1)² + (y2-y1)²)
 */
private fun Offset.getDistanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.hypot(dx, dy)
}
