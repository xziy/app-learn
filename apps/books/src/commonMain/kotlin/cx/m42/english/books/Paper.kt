package cx.m42.english.books

import androidx.compose.ui.graphics.Color
import cx.m42.superizer.theme.Tokens

/**
 * The page a book is read on. Superizer has one light set of tokens and no dark one, and a reader wants a warm page
 * and a dark one too, so these are colours of the app's own; the host's tokens are made over from them, so its widgets
 * on the page follow.
 */
internal enum class Paper(val key: String) {
    Light("light"),
    Sepia("sepia"),
    Dark("dark"),
    ;

    companion object {
        fun of(key: String?): Paper = entries.find { it.key == key } ?: Sepia
    }
}

internal data class PaperColors(
    val background: Color,
    /** Of what lies over the page: the bars, a sheet, a card. */
    val surface: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    /** The line of the English way of thinking, so it is told from the Russian at a glance. */
    val thought: Color,
    /** Under a sentence whose other lines are open. */
    val open: Color,
    /**
     * The nouns of a sentence, one colour each and the same in every line: `name` in the English, «имя» in the thought
     * and «имени» in the Russian are one colour, the next noun another.
     */
    val nouns: List<Color>,
) {
    fun noun(index: Int): Color = nouns[index % nouns.size]
}

internal fun Paper.colors(): PaperColors = when (this) {
    Paper.Light -> PaperColors(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF4F4F5),
        text = Color(0xFF1B1B1F),
        muted = Color(0xFF6E6E78),
        border = Color(0xFFE4E4E7),
        thought = Color(0xFF3D5A80),
        open = Color(0xFFEFF3F8),
        nouns = listOf(
            Color(0xFF1D64C4), Color(0xFF1E7F45), Color(0xFFB94A0C),
            Color(0xFF7D3CB5), Color(0xFFAD2156), Color(0xFF0B727B),
        ),
    )
    Paper.Sepia -> PaperColors(
        background = Color(0xFFF6EFDF),
        surface = Color(0xFFEDE3CC),
        text = Color(0xFF3A2E22),
        muted = Color(0xFF85745D),
        border = Color(0xFFDCCCAD),
        thought = Color(0xFF4A6470),
        open = Color(0xFFEDE4CF),
        nouns = listOf(
            Color(0xFF1C5BA3), Color(0xFF2C7038), Color(0xFFAD4A0B),
            Color(0xFF743A97), Color(0xFF9E2E4C), Color(0xFF22706E),
        ),
    )
    Paper.Dark -> PaperColors(
        background = Color(0xFF16171C),
        surface = Color(0xFF202229),
        text = Color(0xFFE3E0D8),
        muted = Color(0xFF8D909A),
        border = Color(0xFF2D3038),
        thought = Color(0xFF9FB6D6),
        open = Color(0xFF22252D),
        nouns = listOf(
            Color(0xFF79AEFF), Color(0xFF6BCB8E), Color(0xFFF1A766),
            Color(0xFFC9A0F2), Color(0xFFF28DAB), Color(0xFF63CACA),
        ),
    )
}

/** The host's tokens on this page: its buttons and switches take the page's colours. */
internal fun Tokens.on(colors: PaperColors): Tokens = copy(
    background = colors.background,
    foreground = colors.text,
    primary = colors.text,
    primaryForeground = colors.background,
    muted = colors.muted,
    border = colors.border,
    surface = colors.surface,
    menuBackground = colors.surface,
    pageBackground = colors.background,
    disabled = colors.muted.copy(alpha = 0.6f),
)
