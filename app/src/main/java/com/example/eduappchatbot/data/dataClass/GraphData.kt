package com.example.eduappchatbot.data.dataClass

import kotlinx.serialization.Serializable
import kotlin.collections.sumOf

@Serializable
data class GraphData(
    val visualization_type: String,
    val main_concept: String,
    val nodes: List<Node>,
    val edges: List<Edge>,
    val audioSegments: List<AudioSegment> = emptyList()
) {
    @Serializable
    data class Node(
        val id: String,
        val label: String,
        val category: String
    )

    @Serializable
    data class Edge(
        val from: String,
        val to: String,
        val label: String,
        val id: String = "$from->$to"
    )

    @Serializable
    data class AudioSegment(
        val segmentIndex: Int = 0,  // Add this field that's in the JSON
        val startTime: Float = 0f,   // Keep for backward compatibility
        val endTime: Float = 0f,     // Keep for backward compatibility
        val spokenText: String,
        val estimatedDuration: Float,
        val highlightNodeIds: List<String> = emptyList(),  // Fixed typo: was highlightNodeId
        val highlightEdgeIds: List<String> = emptyList(),
        val showNodeIds: List<String> = emptyList(),
        val action: String = "introduce"
    ) {
        // Compute startTime and endTime if not provided
        fun computeTimes(previousSegments: List<AudioSegment>): AudioSegment {
            if (startTime > 0f && endTime > 0f) return this

            val computedStart = previousSegments.sumOf { it.estimatedDuration.toDouble() }.toFloat()
            val computedEnd = computedStart + estimatedDuration

            return copy(startTime = computedStart, endTime = computedEnd)
        }
    }
}