package com.example.eduappchatbot.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eduappchatbot.ui.theme.AccentBlue
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.viewModels.speechModels.TextToSpeech
import kotlin.math.roundToInt

@Composable
fun ScrollingSubtitle(
    modifier: Modifier = Modifier,
    text: String,
    ttsController: TextToSpeech = viewModel(),
    maxVisibleLines: Int = 5
) {
    val currentWordIndex by ttsController.currentWordIndex.collectAsState()
    val ttsState by ttsController.state.collectAsState()
    val isSpeaking = ttsState.isSpeaking

    // Use the text parameter directly - it will be the translated version
    val (cleanText, words, boldRanges) = remember(text) {
        extractWordsAndBold(text)
    }

    if (words.isEmpty()) {
        Text(
            text = text,
            color = TextPrimary,
            modifier = modifier.padding(4.dp)
        )
        return
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Auto-scroll to current word
    LaunchedEffect(currentWordIndex, isSpeaking, textLayout) {
        if (!isSpeaking || currentWordIndex < 0 || textLayout == null) return@LaunchedEffect
        val layout = textLayout!!
        val word = words.getOrNull(currentWordIndex) ?: return@LaunchedEffect
        val lineIndex = layout.getLineForOffset(word.start)
        val lineTop = layout.getLineTop(lineIndex)
        val target = (lineTop - 40f).coerceAtLeast(0f)
        scrollState.animateScrollTo(target.roundToInt(), tween(300))
    }

    val annotatedText = buildAnnotatedString {
        append(cleanText)

        boldRanges.forEach { range ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
        }

        if (isSpeaking && currentWordIndex in words.indices) {
            val word = words[currentWordIndex]

            addStyle(
                style = SpanStyle(
                    color = TextPrimary,
                    fontWeight = FontWeight.Normal,
                    background = Color.Transparent
                ),
                start = word.start,
                end = word.end + 1
            )
            addStyle(
                style = SpanStyle(
                    background = AccentBlue,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                start = word.start,
                end = word.end + 1
            )
        }
    }

    val lineHeight = textLayout?.let { it.getLineBottom(0) - it.getLineTop(0) } ?: 0f
    val maxHeight = if (lineHeight > 0f) {
        with(density) { (lineHeight * maxVisibleLines).toDp() + 16.dp }
    } else 120.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = annotatedText,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .padding(8.dp),
            onTextLayout = { textLayout = it }
        )
    }
}
@Immutable
data class WordRange(val start: Int, val end: Int)

private fun extractWordsAndBold(text: String): Triple<String, List<WordRange>, List<IntRange>> {
    val clean = StringBuilder()
    val words = mutableListOf<WordRange>()
    val boldRanges = mutableListOf<IntRange>()

    var boldStart: Int? = null
    var wordStart = -1
    var i = 0

    while (i < text.length) {
        when {
            // Handle **bold** (Markdown style)
            i + 1 < text.length && text.substring(i, i + 2) == "**" -> {
                if (boldStart != null) {
                    boldRanges.add(boldStart until clean.length)
                    boldStart = null
                } else {
                    boldStart = clean.length
                }
                i += 2
                continue
            }

            // Handle single * (could be italic or bold in some implementations)
            // We're treating single * as bold toggle for simplicity (common in many apps)
            text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                if (boldStart != null) {
                    boldRanges.add(boldStart until clean.length)
                    boldStart = null
                } else {
                    boldStart = clean.length
                }
                i += 1
                continue
            }

            else -> {
                val currentChar = text[i]
                clean.append(currentChar)

                // Improved character classification for multilingual support
                val isWordChar = when {
                    // English: letters, digits, apostrophes, hyphens
                    currentChar.isLetterOrDigit() -> true
                    currentChar in "'’-–—" -> true  // common punctuation in words

                    // Kannada script range (Unicode)
                    currentChar.code in 0x0C80..0x0CFF -> true  // Kannada block
                    currentChar.code in 0x1CD0..0x1CFF -> true  // Vedic extensions (sometimes used)
                    currentChar == '\u200D' -> true  // Zero Width Joiner (critical for Kannada!)
                    currentChar == '\u0CCD' -> true  // Kannada virama (halant)

                    else -> false
                }

                if (isWordChar) {
                    if (wordStart == -1) {
                        wordStart = clean.length - 1  // start of word in clean text
                    }
                } else {
                    // End of word detected (space, punctuation, newline, etc.)
                    if (wordStart != -1) {
                        words.add(WordRange(wordStart, clean.length - 2)) // end is inclusive
                        wordStart = -1
                    }
                }

                i += 1
            }
        }
    }

    // Add final word if exists
    if (wordStart != -1) {
        words.add(WordRange(wordStart, clean.length - 1))
    }

    // Close any unclosed bold
    boldStart?.let {
        boldRanges.add(it until clean.length)
    }

    return Triple(clean.toString(), words, boldRanges)
}