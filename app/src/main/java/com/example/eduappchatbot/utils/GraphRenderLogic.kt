package com.example.eduappchatbot.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.data.dataClass.GraphData
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary

/**
 * GraphRenderLogic - Handles all rendering operations for the concept map
 *
 * Manages:
 * - Node and edge drawing
 * - Text rendering
 * - Colors and styling
 * - Layout calculations
 */
class GraphRenderLogic(
    private val graphData: GraphData,
    private val nodeRadius: Float = 90f
) {
    /**
     * Paint objects for different text elements
     */
    @Composable
    fun createTitlePaint(): android.graphics.Paint {
        return remember {
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 56f
                color = TextPrimary.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
        }
    }

    @Composable
    fun createTextPaint(): android.graphics.Paint {
        return remember {
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 28f
                color = TextPrimary.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }
    }

    @Composable
    fun createEdgeLabelPaint(): android.graphics.Paint {
        return remember {
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 23f
                color = TextSecondary.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }
    }

    /**
     * Category-based color mapping for nodes
     */
    fun colorForCategory(category: String) = when (category) {
        "Core", "Main" -> Color(0xFF3B82F6)
        "Action" -> Color(0xFF10B981)
        "Outcome" -> Color(0xFFF59E0B)
        "Application" -> Color(0xFFEC4899)
        "Biological Feature" -> Color(0xFF8B5CF6)
        "Social Structure" -> Color(0xFF14B8A6)
        "History" -> Color(0xFF6366F1)
        "Biology" -> Color(0xFF10B981)
        "Nutrition" -> Color(0xFF84CC16)
        "Behavior" -> Color(0xFF06B6D4)
        "Health" -> Color(0xFFF43F5E)
        "Ecology" -> Color(0xFF22C55E)
        "Genetics" -> Color(0xFFA855F7)
        "Taxonomy" -> Color(0xFF3B82F6)
        "Agriculture" -> Color(0xFFF97316)
        "Secondary" -> Color(0xFF8B5CF6)
        "Leaf" -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }

    /**
     * Calculate hierarchical layout positions for nodes
     */
    fun calculateNodePositions(center: Offset): Map<String, Offset> {
        val nodePosition = mutableMapOf<String, Offset>()

        // Build adjacency list
        val adjacencyList = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()

        graphData.nodes.forEach { node ->
            adjacencyList[node.id] = mutableListOf()
            inDegree[node.id] = 0
        }

        graphData.edges.forEach { edge ->
            adjacencyList[edge.from]?.add(edge.to)
            inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1
        }

        // Find root nodes
        val rootNodes = graphData.nodes.filter { (inDegree[it.id] ?: 0) == 0 }
        val roots = if (rootNodes.isEmpty()) listOf(graphData.nodes.first()) else rootNodes

        // Layout parameters
        val startY = 180f
        val levelHeight = 380f
        val horizontalSpacing = 350f

        // BFS for level assignment
        val levels = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()

        roots.forEach { root ->
            queue.add(root.id to 0)
            visited.add(root.id)
        }

        while (queue.isNotEmpty()) {
            val (nodeId, level) = queue.removeFirst()
            levels[nodeId] = level

            adjacencyList[nodeId]?.forEach { childId ->
                if (childId !in visited) {
                    visited.add(childId)
                    queue.add(childId to level + 1)
                }
            }
        }

        // Position nodes based on level
        val nodesByLevel = levels.entries.groupBy({ it.value }, { it.key })

        nodesByLevel.forEach { (level, nodesInLevel) ->
            val y = startY + level * levelHeight
            val levelWidth = (nodesInLevel.size - 1) * horizontalSpacing
            val startX = center.x - levelWidth / 2

            nodesInLevel.forEachIndexed { index, nodeId ->
                val x = if (nodesInLevel.size == 1) {
                    center.x
                } else {
                    startX + index * horizontalSpacing
                }
                nodePosition[nodeId] = Offset(x, y)
            }
        }

        return nodePosition
    }

    /**
     * Draw all edges
     */
    fun DrawScope.drawEdges(
        nodePositions: Map<String, Offset>,
        visibleNodeIds: Set<String>,
        highlightedEdgeIds: Set<String>,
        edgeLabelPaint: android.graphics.Paint
    ) {
        graphData.edges.forEach { edge ->
            val fromPos = nodePositions[edge.from]
            val toPos = nodePositions[edge.to]

            // Skip edge if either endpoint is not visible
            if (!visibleNodeIds.contains(edge.from) ||
                !visibleNodeIds.contains(edge.to)) {
                return@forEach
            }

            if (fromPos != null && toPos != null) {
                val isHighlighted = highlightedEdgeIds.contains(edge.id)

                // Create curved path
                val path = Path().apply {
                    moveTo(fromPos.x, fromPos.y)
                    val controlX = (fromPos.x + toPos.x) / 2
                    val controlY = fromPos.y + (toPos.y - fromPos.y) * 0.3f
                    quadraticTo(controlX, controlY, toPos.x, toPos.y)
                }

                // Draw edge with highlighting
                drawPath(
                    path,
                    color = if (isHighlighted) {
                        Color(0xFF3B82F6).copy(alpha = 1f)
                    } else {
                        Color(0xFF6B7280).copy(alpha = 0.6f)
                    },
                    style = Stroke(width = if (isHighlighted) 5.dp.toPx() else 3.dp.toPx())
                )

                // Draw edge label
                val midX = (fromPos.x + toPos.x) / 2
                val midY = (fromPos.y + toPos.y) / 2 - 30

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(edge.label, midX, midY, edgeLabelPaint)
                }
            }
        }
    }

    /**
     * Draw all nodes
     */
    fun DrawScope.drawNodes(
        nodePositions: Map<String, Offset>,
        visibleNodeIds: Set<String>,
        highlightedNodeIds: Set<String>,
        selectedNodeId: String?,
        animationScale: Float,
        textPaint: android.graphics.Paint
    ) {
        graphData.nodes.forEach { node ->
            // Skip node if not visible
            if (!visibleNodeIds.contains(node.id)) {
                return@forEach
            }

            val pos = nodePositions[node.id] ?: return@forEach

            val isHighlighted = highlightedNodeIds.contains(node.id)
            val isSelected = selectedNodeId == node.id

            // Node color with highlighting
            val color = when {
                isSelected -> colorForCategory(node.category).copy(alpha = 0.9f)
                isHighlighted -> colorForCategory(node.category).copy(alpha = 1f)
                else -> colorForCategory(node.category).copy(alpha = 0.8f)
            }

            // Node radius with selection/highlight scaling
            val radius = when {
                isSelected -> nodeRadius * animationScale
                isHighlighted -> nodeRadius * 1.08f
                else -> nodeRadius
            }

            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = radius,
                center = pos + Offset(4f, 4f)
            )

            // Main node circle
            drawCircle(color, radius, center = pos)

            // Highlight ring
            if (isHighlighted) {
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                    radius = radius + 12f,
                    center = pos,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // Inner highlight circle
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = radius * 0.85f,
                center = pos
            )

            // Node label text
            drawMultiLineText(
                text = node.label,
                center = pos,
                textPaint = textPaint,
                maxWidth = radius * 1.6f
            )
        }
    }

    /**
     * Draw title
     */
    fun DrawScope.drawTitle(center: Offset, titlePaint: android.graphics.Paint) {
        drawIntoCanvas { canvas ->
            val titleY = 60f
            canvas.nativeCanvas.drawText(
                graphData.main_concept,
                center.x,
                titleY,
                titlePaint
            )
        }
    }

    /**
     * Draw multi-line text within a maximum width
     */
    private fun DrawScope.drawMultiLineText(
        text: String,
        center: Offset,
        textPaint: android.graphics.Paint,
        maxWidth: Float
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        // Build lines
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (textPaint.measureText(testLine) > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        // Draw lines
        val lineHeight = textPaint.textSize * 1.2f
        val totalHeight = lines.size * lineHeight
        val startY = center.y - (totalHeight / 2) + (lineHeight / 2)

        drawIntoCanvas { canvas ->
            lines.forEachIndexed { index, line ->
                val y = startY + (index * lineHeight)
                canvas.nativeCanvas.drawText(line, center.x, y, textPaint)
            }
        }
    }
}