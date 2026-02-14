package chat.stoat.markdown.jbm.sequentialparsers

import chat.stoat.markdown.jbm.RSMElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

/**
 * Parses ||spoiler|| syntax (double-pipe delimiters).
 * Produces a SPOILER node that the renderer can style as hidden/revealed text.
 */
class SpoilerParser : SequentialParser {
    override fun parse(
        tokens: TokensCache,
        rangesToGlue: List<IntRange>
    ): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            // Look for two consecutive pipe characters '||'
            if (isPipe(iterator) && isPipe(iterator, 1)) {
                val start = iterator.index
                // Advance past the opening '||'
                iterator = iterator.advance()
                iterator = iterator.advance()

                // Scan for closing '||'
                var found = false
                while (iterator.type != null) {
                    if (isPipe(iterator) && isPipe(iterator, 1)) {
                        // Found closing '||' — create spoiler node
                        // Advance past the second pipe of closing delimiter
                        iterator = iterator.advance()
                        result.withNode(
                            SequentialParser.Node(
                                start..iterator.index + 1,
                                RSMElementTypes.SPOILER
                            )
                        )
                        found = true
                        break
                    }
                    iterator = iterator.advance()
                }

                if (!found) {
                    // No closing delimiter found — delegate everything from start
                    delegateIndices.put(start)
                }
            } else {
                delegateIndices.put(iterator.index)
            }
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    /** Check if the character at offset from current position is a pipe '|'. */
    private fun isPipe(iterator: TokensCache.Iterator, offset: Int = 0): Boolean {
        return iterator.charLookup(offset) == '|'
    }
}
