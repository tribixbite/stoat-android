package chat.stoat.markdown.jbm.sequentialparsers

import chat.stoat.markdown.jbm.RSMElementTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

/**
 * Parses @everyone and @here mass-mention syntax.
 * Produces MASS_MENTION nodes that the renderer styles as highlighted mentions.
 */
class MassMentionParser : SequentialParser {
    companion object {
        private val KEYWORDS = listOf("everyone", "here")
    }

    override fun parse(
        tokens: TokensCache,
        rangesToGlue: List<IntRange>
    ): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            val keyword = matchKeyword(iterator)
            if (keyword != null) {
                val start = iterator.index
                // Advance past the keyword characters (after the '@')
                var endIterator = iterator
                repeat(keyword.length) {
                    if (endIterator.advance().type != null) {
                        endIterator = endIterator.advance()
                    }
                }
                result.withNode(
                    SequentialParser.Node(
                        start..endIterator.index + 1,
                        RSMElementTypes.MASS_MENTION
                    )
                )
                iterator = endIterator.advance()
                continue
            }
            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    /** Check if current position starts with @keyword at a word boundary. */
    private fun matchKeyword(iterator: TokensCache.Iterator): String? {
        if (charAt(iterator, 0) != '@') return null
        for (keyword in KEYWORDS) {
            var matches = true
            for (i in keyword.indices) {
                val c = charAt(iterator, i + 1)
                if (c == null || c != keyword[i]) {
                    matches = false
                    break
                }
            }
            if (!matches) continue
            // Word boundary: char after keyword should not continue the word
            val afterChar = charAt(iterator, keyword.length + 1)
            if (afterChar != null && (afterChar.isLetterOrDigit() || afterChar == '_')) continue
            return keyword
        }
        return null
    }

    /** Safe character lookup — returns null if out of bounds. */
    private fun charAt(iterator: TokensCache.Iterator, offset: Int): Char? {
        return try {
            iterator.charLookup(offset)
        } catch (_: Exception) {
            null
        }
    }
}
