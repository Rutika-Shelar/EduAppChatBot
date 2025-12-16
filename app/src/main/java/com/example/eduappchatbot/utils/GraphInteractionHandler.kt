package com.example.eduappchatbot.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
     * Clamp the current offset so the transformed graph bounding box stays inside the canvas.
     *
     * Calculation notes:
     * - Node positions are expressed in graph coordinate space.
     * - After transform, a node at position p maps to screen:
     *     screen(p) = scale * (p - canvasCenter) + canvasCenter + offset
     * - We compute the bounding box of all nodes in graph space and then compute the allowed
     *   range for `offset` so that the bounding box intersects or fits inside the canvas.
     *
     * This method should be called from the composable drawing scope where `canvasCenter` and
     * `size` are known (for example, inside the Canvas draw block).
     */
    fun clampToBounds(nodePositions: Map<String, Offset>, canvasSize: Size) {
        if (nodePositions.isEmpty()) return

        // Compute graph bounding box
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for ((_, pos) in nodePositions) {
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
        }

        val cX = canvasCenter.x
        val cY = canvasCenter.y
        val canvasW = canvasSize.width
        val canvasH = canvasSize.height

        // Transformed bbox: screen = scale*(graph - center) + center + offset
        // We want: minScreenX >= 0  and maxScreenX <= canvasW
        val leftBound = -scale * (minX - cX) - cX
        val rightBound = canvasW - (scale * (maxX - cX) + cX)

        // For Y axis
        val topBound = -scale * (minY - cY) - cY
        val bottomBound = canvasH - (scale * (maxY - cY) + cY)

        // Determine allowed ranges (min <= max). If graph is larger than canvas when scaled,
        // the allowed interval is inverted; handle by taking min/max accordingly so coerceIn works.
        val allowedMinX = minOf(leftBound, rightBound)
        val allowedMaxX = maxOf(leftBound, rightBound)
        val allowedMinY = minOf(topBound, bottomBound)
        val allowedMaxY = maxOf(topBound, bottomBound)

        val clampedX = offset.x.coerceIn(allowedMinX, allowedMaxX)
        val clampedY = offset.y.coerceIn(allowedMinY, allowedMaxY)

        if (clampedX != offset.x || clampedY != offset.y) {
            offset = Offset(clampedX, clampedY)
            DebugLogger.debugLog(TAG, "Offset clamped to: $offset")
        }
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
