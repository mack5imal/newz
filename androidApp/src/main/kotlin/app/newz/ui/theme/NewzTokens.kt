package app.newz.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.newz.R
import app.newz.store.TextSize

/*
 * Newz design tokens — the single theme file (SPEC S32). Every value is transcribed from
 * design/HANDOFF.md §Design tokens; screens import from here and never hard-code a colour.
 */

// region Colour

/** One value per colour token; [NewzColors.Light] and [NewzColors.Dark] are separate designs. */
@Immutable
data class NewzPalette(
    val paper: Color,
    val ink: Color,
    val inkMuted: Color,
    val navy: Color,
    val sky: Color,
    val card: Color,
    val field: Color,
    val line: Color,
    val lineSubtle: Color,
    val strip: Color,
    val tile: Color,
    val wordmarkTile: Color,
    val wordmarkLetter: Color,
    val wordmarkRule: Color,
    val imagePlaceholder: Color,
    val imageHatch: Color,
    val button: Color,
    val onButton: Color,
    val disabled: Color,
    val onDisabled: Color,
    val icon: Color,
    val alert: Color,
    val scrim: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val isDark: Boolean,
)

object NewzColors {
    val Light =
        NewzPalette(
            paper = Color(0xFFDCE5F2),
            ink = Color(0xFF1D3A5F),
            inkMuted = Color(0xFF4A6385),
            navy = Color(0xFF1D3A5F),
            sky = Color(0xFFDCE5F2),
            card = Color(0xFFF5F8FC),
            field = Color(0xFFEDF2F9),
            line = Color(0xFF1D3A5F),
            lineSubtle = Color(0xFFB3C3D9),
            strip = Color(0xFF1D3A5F),
            tile = Color(0xFF33557F),
            wordmarkTile = Color(0xFFDCE5F2),
            wordmarkLetter = Color(0xFF1D3A5F),
            wordmarkRule = Color(0xFF6F8DB5),
            imagePlaceholder = Color(0xFFC5D3E6),
            imageHatch = Color(0xFFB8C8DD),
            button = Color(0xFF1D3A5F),
            onButton = Color(0xFFDCE5F2),
            disabled = Color(0xFFB3C3D9),
            onDisabled = Color(0xFF4A6385),
            icon = Color(0xFF1D3A5F),
            alert = Color(0xFFB3261E),
            scrim = Color(red = 13, green = 27, blue = 46).copy(alpha = 0.55f),
            snackbar = Color(0xFF1D3A5F),
            onSnackbar = Color(0xFFDCE5F2),
            isDark = false,
        )

    val Dark =
        NewzPalette(
            paper = Color(0xFF1D3A5F),
            ink = Color(0xFFF3F6FB),
            inkMuted = Color(0xFFB7C8DE),
            navy = Color(0xFF1D3A5F),
            sky = Color(0xFFDCE5F2),
            card = Color(0xFF24456F),
            field = Color(0xFF24456F),
            line = Color(0xFF6F8DB5),
            lineSubtle = Color(0xFF36597F),
            strip = Color(0xFF142A47),
            tile = Color(0xFF2E5283),
            wordmarkTile = Color(0xFFDCE5F2),
            wordmarkLetter = Color(0xFF1D3A5F),
            wordmarkRule = Color(0xFF6F8DB5),
            imagePlaceholder = Color(0xFF2C4D78),
            imageHatch = Color(0xFF33568A),
            button = Color(0xFFDCE5F2),
            onButton = Color(0xFF1D3A5F),
            disabled = Color(0xFF36597F),
            onDisabled = Color(0xFFB7C8DE),
            icon = Color(0xFFB7C8DE),
            alert = Color(0xFFFFB4A9),
            scrim = Color(red = 5, green = 12, blue = 22).copy(alpha = 0.62f),
            snackbar = Color(0xFFDCE5F2),
            onSnackbar = Color(0xFF1D3A5F),
            isDark = true,
        )
}

/** A drop shadow: x/y offset and blur radius in dp. */
@Immutable
data class NewzShadow(
    val offsetX: Dp,
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
)

/** The palette in effect; provided by [NewzTheme]. */
val LocalNewzPalette = staticCompositionLocalOf { NewzColors.Light }

// endregion

// region Fonts

/** `font-signage`: Big Shoulders Display 700 and 800, bundled under res/font (OFL 1.1). */
val fontSignage =
    FontFamily(
        Font(R.font.big_shoulders_display_bold, FontWeight.Bold),
        Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
    )

/** `font-text`: Schibsted Grotesk 400/500/600/700, bundled under res/font (OFL 1.1). */
val fontText =
    FontFamily(
        Font(R.font.schibsted_grotesk_regular, FontWeight.Normal),
        Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
        Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
        Font(R.font.schibsted_grotesk_bold, FontWeight.Bold),
    )

// endregion

// region Type and text scale

/**
 * A design type style. [caps] marks signage styles (shown in capitals, scaled by
 * `text-scale-signage`); the others are `font-text` styles scaled by `text-scale`.
 * [scales] is false for the app-bar wordmark and nav buttons, which never scale.
 */
@Immutable
data class NewzType(
    val textStyle: TextStyle,
    val caps: Boolean,
    val scales: Boolean = true,
) {
    /** This style at the given text-scale factors. */
    fun resolve(scale: NewzTextScale): TextStyle {
        if (!scales) return textStyle
        val factor = if (caps) scale.signage else scale.text
        if (factor == 1f) return textStyle
        return textStyle.copy(
            fontSize = textStyle.fontSize.times(factor),
            lineHeight = textStyle.lineHeight.timesIfSpecified(factor),
        )
    }
}

private fun TextUnit.timesIfSpecified(factor: Float): TextUnit = if (isSpecified) times(factor) else this

/** The in-app text-scale factors for content text (`text-scale`) and signage (`text-scale-signage`). */
@Immutable
data class NewzTextScale(
    val text: Float,
    val signage: Float,
) {
    companion object {
        val Default = NewzTextScale(text = 1f, signage = 1f)

        fun of(size: TextSize): NewzTextScale =
            NewzTextScale(
                text = NewzTokens.textScale.getValue(size),
                signage = NewzTokens.textScaleSignage.getValue(size),
            )
    }
}

/** The text-scale factors in effect; provided by [NewzTheme] from the user's `TextSize`. */
val LocalNewzTextScale = staticCompositionLocalOf { NewzTextScale.Default }

/** This style resolved against [LocalNewzTextScale]. Sizes stay in sp, so they stack with the system font scale. */
@Composable
@ReadOnlyComposable
fun NewzType.scaled(): TextStyle = resolve(LocalNewzTextScale.current)

private fun signage(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    tracking: TextUnit = TextUnit.Unspecified,
    scales: Boolean = true,
) = NewzType(
    textStyle =
        TextStyle(
            fontFamily = fontSignage,
            fontWeight = weight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = tracking,
        ),
    caps = true,
    scales = scales,
)

private fun text(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    tracking: TextUnit = TextUnit.Unspecified,
) = NewzType(
    textStyle =
        TextStyle(
            fontFamily = fontText,
            fontWeight = weight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = tracking,
        ),
    caps = false,
)

// endregion

/** Type, text scale, space/size, radius/border and motion tokens. */
object NewzTokens {
    // Type styles (size/line height in sp, tracking in em).
    val typeWordmark = signage(FontWeight.ExtraBold, 40.sp, 40.sp, 0.04.em, scales = false)
    val typeScreenTitle = signage(FontWeight.ExtraBold, 28.sp, 30.sp, 0.04.em)
    val typeSheetTitle = signage(FontWeight.ExtraBold, 28.sp, 30.sp, 0.03.em)
    val typeStateTitle = signage(FontWeight.ExtraBold, 34.sp, 36.sp, 0.02.em)
    val typeChip = signage(FontWeight.Bold, 19.sp, tracking = 0.06.em)
    val typeNav = signage(FontWeight.Bold, 18.sp, tracking = 0.08.em, scales = false)
    val typeOutlet = signage(FontWeight.Bold, 18.sp, 22.sp, 0.08.em)
    val typeButton = signage(FontWeight.ExtraBold, 19.sp, tracking = 0.08.em)
    val typePanelHeader = signage(FontWeight.Bold, 17.sp, tracking = 0.10.em)
    val typeMeta = signage(FontWeight.Bold, 15.sp, tracking = 0.06.em)
    val typeLabel = signage(FontWeight.Bold, 15.sp, tracking = 0.10.em)
    val typeSegment = signage(FontWeight.Bold, 16.sp, 16.sp, 0.05.em)
    val typeTag = signage(FontWeight.Bold, 14.sp, tracking = 0.08.em)
    val typeTile = signage(FontWeight.ExtraBold, 24.sp)
    val typeHeadline = text(FontWeight.Bold, 21.sp, 27.sp, (-0.01).em)
    val typeHeading = text(FontWeight.Bold, 24.sp, 30.sp)
    val typeBody = text(FontWeight.Normal, 16.sp, 24.sp)
    val typeRowTitle = text(FontWeight.SemiBold, 16.sp, 22.sp)
    val typeRowSub = text(FontWeight.Normal, 14.sp, 20.sp)
    val typeIntro = text(FontWeight.Normal, 15.sp, 22.sp)
    val typeField = text(FontWeight.Normal, 17.sp)
    val typeMessage = text(FontWeight.SemiBold, 16.sp, 22.sp)

    // Text size: factors keyed by the user's TextSize preference.
    val textScale: Map<TextSize, Float> =
        mapOf(
            TextSize.SMALL to 0.875f,
            TextSize.DEFAULT to 1.0f,
            TextSize.LARGE to 1.15f,
            TextSize.EXTRA_LARGE to 1.33f,
        )
    val textScaleSignage: Map<TextSize, Float> =
        mapOf(
            TextSize.SMALL to 0.9f,
            TextSize.DEFAULT to 1.0f,
            TextSize.LARGE to 1.12f,
            TextSize.EXTRA_LARGE to 1.25f,
        )

    // Space and size.
    val space2xs = 2.dp
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceCardGap = 14.dp
    val spaceL = 16.dp
    val spaceXl = 20.dp
    val space2xl = 28.dp
    val touchTarget = 48.dp
    val statusInset = 28.dp
    val appBarHeight = 64.dp
    val stripHeight = 48.dp
    val stripHeightSaved = 40.dp
    val chipHeight = 48.dp
    val buttonHeight = 48.dp
    val fieldHeight = 56.dp
    val fieldHeightSearch = 52.dp
    val rowHeight = 64.dp
    val sheetRowHeight = 56.dp
    val panelHeaderHeight = 40.dp
    val tileSize = DpSize(24.dp, 32.dp)
    val tileSizeSmall = DpSize(18.dp, 24.dp)
    val tileSizeWordmark = DpSize(28.dp, 38.dp)
    val tileGap = 3.dp
    val imageRatio = 16f / 9f
    val noImageHeight = 104.dp
    val thumbSize = 72.dp
    val switchSize = DpSize(56.dp, 32.dp)
    val switchThumb = 20.dp
    val checkboxSize = 24.dp
    val iconSize = 22.dp
    val iconSizeInControl = 20.dp
    val iconSizeState = 40.dp
    val snackbarMinHeight = 60.dp
    val snackbarBottom = 24.dp
    val imageHatchSpacing = 11.dp
    val imageHatchSpacingThumb = 9.dp

    // Radius and border.
    val radiusControl = 3.dp
    val radiusSwitch = 4.dp
    val radiusSwitchThumb = 2.dp
    val radiusSheet = 8.dp
    val radiusGrabber = 2.dp
    val grabberSize = DpSize(40.dp, 4.dp)
    val radiusNone = 0.dp
    val radiusAppIcon = 5.dp
    val borderControl = 2.dp
    val borderTag = 1.5.dp
    val divider = 1.dp
    val rule = 2.dp

    // Shadow: `shadow-snackbar` 0 8 24 rgba(8,18,32,0.35), the only shadow in the app.
    val shadowSnackbar =
        NewzShadow(
            offsetX = 0.dp,
            offsetY = 8.dp,
            blur = 24.dp,
            color = Color(red = 8, green = 18, blue = 32).copy(alpha = 0.35f),
        )

    // Motion (milliseconds).
    val flapFlip = 180
    val flapFlipEasing: Easing = FastOutLinearInEasing
    val sheet = 250
    val sheetEasing: Easing = LinearOutSlowInEasing
    val cardCollapse = 200
    val snackbarTimeout = 6_000L
    val spinnerTurn = 900

    // Component measurements: values design/HANDOFF.md §Components gives per component that
    // have no shared token above. Components read these, never literals.
    val typeOutletSaved = signage(FontWeight.Bold, 16.sp, 20.sp, 0.08.em)
    val typeHeadlineSaved = text(FontWeight.Bold, 18.sp, 24.sp, (-0.01).em)
    val typeThumbTopic = signage(FontWeight.ExtraBold, 20.sp, 20.sp)
    val typeBanner = text(FontWeight.SemiBold, 15.sp, 20.sp)
    val typeSnackbarAction = signage(FontWeight.ExtraBold, 18.sp, tracking = 0.08.em)

    // Button `compact` (Settings' Clear reading history): drawn 36 tall, padding 0 14, 16 sp text,
    // inside the usual 48 dp touch target.
    val typeButtonCompact = signage(FontWeight.ExtraBold, 16.sp, tracking = 0.08.em)
    val buttonCompactHeight = 36.dp
    val buttonCompactPaddingH = 14.dp
    val gapIconLabel = 6.dp
    val navButtonPaddingH = 10.dp
    val chipRowTopLight = 10.dp
    val stripPaddingXl = 10.dp
    val tileRuleOffset = 15.dp
    val tileRuleOffsetSmall = 11.dp
    val groupButtonPaddingH = 14.dp
    val groupButtonGap = 10.dp
    val snackbarPadding = 6.dp
    val snackbarActionPaddingH = 14.dp
    val statePanelBodyGap = 10.dp
    val boardTilesGap = 18.dp
    val skeletonBarSmall = 12.dp
    val skeletonBar = 16.dp
    val skeletonPaddingV = 18.dp
    val skeletonGap = 10.dp
    val sheetGrabberTop = 10.dp
    val checkboxIcon = 16.dp
    val tagHeight = 24.dp
    val tagPaddingH = 7.dp
    val savedActionsHeight = 52.dp
    val bottomBarPaddingBottom = 24.dp
    val appIconSize = 56.dp

    // onboarding: the header's 18 dp gaps and 22 dp bottom padding, and its 46/46 wordmark.
    val typeWordmarkOnboarding = signage(FontWeight.ExtraBold, 46.sp, 46.sp, 0.04.em, scales = false)
    val onboardingGap = 18.dp
    val onboardingHeaderBottom = 22.dp

    // story-group `--many`: the sheet's top edge sits 92 dp down; its grabber area takes 18 dp more.
    val storySheetTopGap = 110.dp
}

// region Icons

private const val ICON_VIEWPORT = 24f
private const val ICON_STROKE = 2f

/** SVG `<circle>` as a path of two arcs. */
private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private class IconPath(
    val d: String,
    val alpha: Float = 1f,
)

private fun lineIcon(
    name: String,
    vararg paths: IconPath,
    filled: Boolean = false,
): ImageVector {
    val builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = NewzTokens.iconSize,
            defaultHeight = NewzTokens.iconSize,
            viewportWidth = ICON_VIEWPORT,
            viewportHeight = ICON_VIEWPORT,
        )
    val ink = SolidColor(Color.Black)
    for (path in paths) {
        if (filled) {
            builder.addPath(pathData = addPathNodes(path.d), fill = ink, fillAlpha = path.alpha)
        } else {
            builder.addPath(
                pathData = addPathNodes(path.d),
                stroke = ink,
                strokeAlpha = path.alpha,
                strokeLineWidth = ICON_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return builder.build()
}

private fun lineIcon(
    name: String,
    vararg d: String,
    filled: Boolean = false,
): ImageVector = lineIcon(name, *d.map { IconPath(it) }.toTypedArray(), filled = filled)

private const val BOOKMARK = "M6.5 3.5h11v17l-5.5-4-5.5 4z"
private const val RSS = "M5 4.5a14.5 14.5 0 0 1 14.5 14.5M5 10.5a8.5 8.5 0 0 1 8.5 8.5"
private const val SHIELD = "M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z"

/**
 * The Newz line icons (`icon-*`), drawn from the design's SVG markup: 24 viewport, stroke 2,
 * round caps and joins, no fill; tint them with the `icon` colour. Built in code, never fetched.
 */
object NewzIcons {
    val iconBack by lazy { lineIcon("back", "M20 12H5M11 6l-6 6 6 6") }
    val iconSettings by lazy {
        lineIcon("settings", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    }
    val iconBookmark by lazy { lineIcon("bookmark", BOOKMARK) }

    /** `icon-bookmark` drawn filled (Unsave). */
    val iconBookmarkFilled by lazy { lineIcon("bookmark-filled", BOOKMARK, filled = true) }
    val iconShare by lazy {
        lineIcon(
            "share",
            circle(18f, 5.5f, 2.5f),
            circle(6f, 12f, 2.5f),
            circle(18f, 18.5f, 2.5f),
            "M8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1",
        )
    }
    val iconMore by lazy {
        lineIcon("more", circle(12f, 5f, 2f), circle(12f, 12f, 2f), circle(12f, 19f, 2f), filled = true)
    }
    val iconArrowRight by lazy { lineIcon("arrow-right", "M4 12h15M13 6l6 6-6 6") }
    val iconChevron by lazy { lineIcon("chevron", "M9 6l6 6-6 6") }
    val iconEyeOff by lazy {
        lineIcon(
            "eye-off",
            "M3 3l18 18M10.6 6.1A9 9 0 0 1 12 6c5 0 9 6 9 6a15.5 15.5 0 0 1-2.6 3.3" +
                "M6.3 7.6C4.2 9.2 3 12 3 12s4 6 9 6c1.5 0 2.9-.4 4.1-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2",
        )
    }
    val iconArrowUp by lazy { lineIcon("arrow-up", "M12 19V6M6 12l6-6 6 6") }
    val iconArrowDown by lazy { lineIcon("arrow-down", "M12 5v13M6 12l6 6 6-6") }
    val iconBan by lazy { lineIcon("ban", circle(12f, 12f, 9f), "M5.6 5.6l12.8 12.8") }
    val iconMinusCircle by lazy { lineIcon("minus-circle", circle(12f, 12f, 9f), "M8 12h8") }
    val iconMute by lazy { lineIcon("mute", "M4 9h4l5-4v14l-5-4H4zM17 9.5l5 5M22 9.5l-5 5") }
    val iconLock by lazy {
        lineIcon(
            "lock",
            // rect x=5 y=10.5 width=14 height=10 rx=1
            "M6 10.5h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z",
            "M8 10.5V7.5a4 4 0 0 1 8 0v3",
        )
    }
    val iconCheck by lazy { lineIcon("check", "M5 12.5l4.5 4.5L19 7.5") }
    val iconClose by lazy { lineIcon("close", "M6 6l12 12M18 6L6 18") }
    val iconPlus by lazy { lineIcon("plus", "M12 5v14M5 12h14") }
    val iconRestore by lazy { lineIcon("restore", "M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-4") }
    val iconRetry by lazy { lineIcon("retry", "M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5") }
    val iconWarning by lazy { lineIcon("warning", "M12 3.5l9.5 17h-19zM12 10v4.5M12 17.5v.01") }
    val iconWifiOff by lazy {
        lineIcon(
            "wifi-off",
            "M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 4.6-2.6M14.4 10.4A10 10 0 0 1 19 13" +
                "M2 9.5a15 15 0 0 1 4.3-2.7M10 5.1A15 15 0 0 1 22 9.5",
            circle(12f, 20f, 1f),
        )
    }
    val iconRss by lazy { lineIcon("rss", RSS, circle(6f, 18f, 1.5f)) }
    val iconRssOff by lazy { lineIcon("rss-off", RSS, circle(6f, 18f, 1.5f), "M15 4l5 5M20 4l-5 5") }
    val iconSearch by lazy { lineIcon("search", circle(10.5f, 10.5f, 6.5f), "M15.5 15.5L21 21") }
    val iconShield by lazy { lineIcon("shield", SHIELD) }
    val iconShieldCheck by lazy { lineIcon("shield-check", SHIELD, "M9 12l2 2 4-4") }
    val iconTag by lazy { lineIcon("tag", "M3.5 12.5V4h8.5l8.5 8.5-8.5 8.5z", circle(8f, 8.5f, 1.5f)) }
    val iconCompass by lazy { lineIcon("compass", circle(12f, 12f, 9f), "M15.5 8.5l-2 5-5 2 2-5z") }
    val iconClock by lazy { lineIcon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val iconImage by lazy { lineIcon("image", "M3.5 5h17v14h-17z", circle(9f, 10f, 2f), "M20.5 15l-5-5-9.5 9") }
    val iconTextSize by lazy { lineIcon("text-size", "M4 18L9 6l5 12M5.8 14h6.4M15 18l3-7 3 7M15.9 16h4.2") }
    val iconDownload by lazy { lineIcon("download", "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val iconUpload by lazy { lineIcon("upload", "M12 15V4M7 9l5-5 5 5M5 20h14") }
    val iconOpen by lazy { lineIcon("open", "M14 4h6v6M20 4l-9 9M19 14v6H4V5h6") }

    /** The track is drawn at 0.25 opacity; the arc rotates one turn per [NewzTokens.spinnerTurn] ms. */
    val iconSpinner by lazy {
        lineIcon("spinner", IconPath(circle(12f, 12f, 9f), alpha = 0.25f), IconPath("M21 12a9 9 0 0 0-9-9"))
    }
}

// endregion
