package com.example.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.example.data.MusicTrack
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * AdMob Configuration Constants
 * Requirement: Official App ID ca-app-pub-8155064094205693~4470440036 declared in AndroidManifest
 * Native Advanced Ad Unit ID for Album & Artist Grids: ca-app-pub-8155064094205693/4658142293
 * Native Advanced Ad Unit ID for Songs List: ca-app-pub-8155064094205693/9416731068
 */
const val ADMOB_NATIVE_AD_UNIT_ID = "ca-app-pub-8155064094205693/4658142293"
const val ADMOB_SONGS_NATIVE_AD_UNIT_ID = "ca-app-pub-8155064094205693/9416731068"

/**
 * Sealed wrapper class to insert Native Ads into the Songs LazyColumn alongside real song tracks.
 */
sealed class SongListItemWrapper {
    data class SongItem(val track: MusicTrack) : SongListItemWrapper()
    data class NativeAdItem(val adIndex: Int) : SongListItemWrapper()
}

/**
 * Sealed wrapper class to insert Native Ads into LazyVerticalGrid alongside real data items.
 */
sealed class GridItemWrapper<out T> {
    data class RealItem<T>(val item: T) : GridItemWrapper<T>()
    data class NativeAdItem(val adIndex: Int) : GridItemWrapper<Nothing>()
}

/**
 * State holder for preloading and caching Native Ads.
 * Requirement 10: Loads Native Ads in advance and reuses them during scrolling.
 * Requirement 11: Tracks failed ads so placeholders can be automatically removed.
 */
@Stable
class NativeAdsState {
    val loadedAds = mutableStateMapOf<Int, NativeAd>()
    val failedAdIndices = mutableStateListOf<Int>()
}

/**
 * Remembers and preloads AdMob Native Ads based on item count rules.
 * Requirement 8: Maximum of 3 Native Ads per screen.
 * Requirement 9:
 *   - < 10 items: 1 ad
 *   - 10-20 items: 2 ads
 *   - 21+ items: 3 ads
 */
@Composable
fun rememberNativeAdsState(
    itemCount: Int,
    adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID
): NativeAdsState {
    val context = LocalContext.current
    val state = remember { NativeAdsState() }

    val requiredAdsCount = when {
        itemCount < 10 -> 1
        itemCount in 10..20 -> 2
        else -> 3
    }

    LaunchedEffect(itemCount, adUnitId) {
        state.loadedAds.clear()
        state.failedAdIndices.clear()

        for (i in 0 until requiredAdsCount) {
            val adIndex = i
            val adLoader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { nativeAd ->
                    state.loadedAds[adIndex] = nativeAd
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdMobNative", "Ad $adIndex failed to load: ${loadAdError.message}")
                        state.failedAdIndices.add(adIndex)
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            state.loadedAds.values.forEach { it.destroy() }
            state.loadedAds.clear()
        }
    }

    return state
}

/**
 * Combines real list items with Native Ad items according to AdMob placement policies.
 * Requirement 7: Inserts one Native Ad after every 5 real items.
 * Requirement 8: Max 3 Native Ads per screen.
 * Requirement 11: Automatically removes ad placeholders if ad loading fails.
 */
fun <T> buildGridItemsWithAds(
    realItems: List<T>,
    failedAdIndices: Collection<Int>
): List<GridItemWrapper<T>> {
    if (realItems.isEmpty()) return emptyList()

    val count = realItems.size
    val maxAdsAllowed = when {
        count < 10 -> 1
        count in 10..20 -> 2
        else -> 3
    }

    val result = mutableListOf<GridItemWrapper<T>>()
    var adsInserted = 0

    for (i in realItems.indices) {
        result.add(GridItemWrapper.RealItem(realItems[i]))
        val itemNum = i + 1

        // Insert Native Ad after every 5 real items
        if (itemNum % 5 == 0 && adsInserted < maxAdsAllowed) {
            val currentAdIndex = adsInserted
            adsInserted++
            // Requirement 11: If ad failed to load, omit placeholder to keep grid seamless
            if (!failedAdIndices.contains(currentAdIndex)) {
                result.add(GridItemWrapper.NativeAdItem(currentAdIndex))
            }
        }
    }

    // Edge Case: If items < 5 (e.g. 1-4 items), requirement 9 requires 1 ad for < 10 items
    if (adsInserted == 0 && maxAdsAllowed > 0) {
        if (!failedAdIndices.contains(0)) {
            result.add(GridItemWrapper.NativeAdItem(0))
        }
    }

    return result
}

/**
 * Remembers and preloads AdMob Native Ads for Songs list.
 * Requirement:
 *   - If songs are below 10: 0 ads (don't show ad)
 *   - If songs >= 10: 1 ad for every 10 songs
 * Uses Songs Unit ID: ca-app-pub-8155064094205693/9416731068
 */
@Composable
fun rememberSongNativeAdsState(
    itemCount: Int,
    adUnitId: String = ADMOB_SONGS_NATIVE_AD_UNIT_ID
): NativeAdsState {
    val context = LocalContext.current
    val state = remember { NativeAdsState() }

    // If songs are below 10, don't load or show any ads.
    // If 10 or more, load 1 ad after every 10 songs (capped at 8 for optimal performance).
    val requiredAdsCount = if (itemCount < 10) 0 else (itemCount / 10).coerceAtMost(8)

    LaunchedEffect(itemCount, adUnitId) {
        state.loadedAds.clear()
        state.failedAdIndices.clear()

        if (requiredAdsCount == 0) return@LaunchedEffect

        for (i in 0 until requiredAdsCount) {
            val adIndex = i
            val adLoader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { nativeAd ->
                    state.loadedAds[adIndex] = nativeAd
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdMobNative", "Song Native Ad $adIndex failed to load: ${loadAdError.message}")
                        state.failedAdIndices.add(adIndex)
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            state.loadedAds.values.forEach { it.destroy() }
            state.loadedAds.clear()
        }
    }

    return state
}

/**
 * Combines real songs with Native Ads according to rules:
 * - If songs are below 10: do not show ad
 * - If songs >= 10: insert an ad after every 10 songs
 * - Automatically omit failed ads to preserve smooth listing
 */
fun buildSongItemsWithAds(
    realItems: List<MusicTrack>,
    failedAdIndices: Collection<Int>
): List<SongListItemWrapper> {
    if (realItems.size < 10) {
        return realItems.map { SongListItemWrapper.SongItem(it) }
    }

    val result = mutableListOf<SongListItemWrapper>()
    var adIndex = 0

    for (i in realItems.indices) {
        result.add(SongListItemWrapper.SongItem(realItems[i]))
        val songNumber = i + 1

        // Show ad after every 10 songs
        if (songNumber % 10 == 0) {
            val currentAdIndex = adIndex
            adIndex++
            if (!failedAdIndices.contains(currentAdIndex)) {
                result.add(SongListItemWrapper.NativeAdItem(currentAdIndex))
            }
        }
    }

    return result
}

/**
 * Requirement 5 & 6 & 12: Album Card Native Ad layout matching exact size, shape, and style of Album cards.
 * Uses official Google NativeAdView wrapper and includes mandatory "Ad" / "Sponsored" badge.
 */
@Composable
fun AlbumNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (nativeAd == null) {
            // Shimmer / Loading placeholder card of identical dimensions
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sponsored",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.5f)
                    )
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                }
            }
        } else {
            AndroidView(
                factory = { context ->
                    createAlbumNativeAdView(context, nativeAd)
                },
                update = { nativeAdView ->
                    populateAlbumNativeAdView(nativeAdView, nativeAd)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Requirement 5 & 6 & 12: Artist Card Native Ad layout matching exact size, shape, and style of Artist cards.
 * Uses official Google NativeAdView wrapper with mandatory "Ad" badge.
 */
@Composable
fun ArtistNativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (nativeAd == null) {
            // Loading placeholder of identical artist card dimensions
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ad",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                )
            }
        } else {
            AndroidView(
                factory = { context ->
                    createArtistNativeAdView(context, nativeAd)
                },
                update = { nativeAdView ->
                    populateArtistNativeAdView(nativeAdView, nativeAd)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Song Native Ad Row matching the exact appearance of a song list row/card.
 * Features 44dp rounded cover thumbnail, headline, "Ad" badge + sponsor info,
 * action call-to-action button, and horizontal divider.
 */
@Composable
fun SongNativeAdRow(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (nativeAd == null) {
            // Placeholder matching song row layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ad",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(11.dp)
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                )
            }
        } else {
            AndroidView(
                factory = { context ->
                    createSongNativeAdView(context, nativeAd)
                },
                update = { nativeAdView ->
                    populateSongNativeAdView(nativeAdView, nativeAd)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider(
            color = SpaceCardBg.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// Private layout builders for Google AdMob NativeAdView compliance

private fun createAlbumNativeAdView(context: Context, nativeAd: NativeAd): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Media / Image container (130dp height matching album cover)
    val mediaContainer = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (130 * context.resources.displayMetrics.density).toInt()
        )
    }

    // Details container (padding 12dp matching album details)
    val paddingPx = (12 * context.resources.displayMetrics.density).toInt()
    val detailsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
    }

    // Top Row: Ad badge + Headline
    val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        val bgDrawable = GradientDrawable().apply {
            setColor(TealPrimary.toArgb())
            cornerRadius = 4 * context.resources.displayMetrics.density
        }
        background = bgDrawable
        val pxH = (5 * context.resources.displayMetrics.density).toInt()
        val pxV = (2 * context.resources.displayMetrics.density).toInt()
        setPadding(pxH, pxV, pxH, pxV)
    }

    val headlineView = TextView(context).apply {
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TextLight.toArgb())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (6 * context.resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
    }

    topRow.addView(adBadge)
    topRow.addView(headlineView)

    // Body / Advertiser
    val bodyView = TextView(context).apply {
        textSize = 12f
        setTextColor(TextMuted.toArgb())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (2 * context.resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
    }

    // Call to Action
    val ctaView = TextView(context).apply {
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TealPrimary.toArgb())
        maxLines = 1
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (4 * context.resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
    }

    detailsLayout.addView(topRow)
    detailsLayout.addView(bodyView)
    detailsLayout.addView(ctaView)

    rootLayout.addView(mediaContainer)
    rootLayout.addView(detailsLayout)
    nativeAdView.addView(rootLayout)

    // Register asset views with Google NativeAdView
    nativeAdView.mediaView = mediaContainer
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = ctaView

    populateAlbumNativeAdView(nativeAdView, nativeAd)
    return nativeAdView
}

private fun populateAlbumNativeAdView(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline
    (nativeAdView.bodyView as? TextView)?.text = nativeAd.body ?: nativeAd.advertiser ?: "Sponsored content"
    (nativeAdView.callToActionView as? TextView)?.text = nativeAd.callToAction ?: "Learn More"
    nativeAdView.setNativeAd(nativeAd)
}

private fun createArtistNativeAdView(context: Context, nativeAd: NativeAd): NativeAdView {
    val nativeAdView = NativeAdView(context)

    val paddingPx = (16 * context.resources.displayMetrics.density).toInt()
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Circle Icon (72dp x 72dp)
    val iconSizePx = (72 * context.resources.displayMetrics.density).toInt()
    val iconView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
            bottomMargin = (8 * context.resources.displayMetrics.density).toInt()
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
        val clipDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.TRANSPARENT)
        }
        background = clipDrawable
    }

    // Ad Badge
    val adBadge = TextView(context).apply {
        text = "Sponsored"
        textSize = 10f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TealPrimary.toArgb())
        val bgDrawable = GradientDrawable().apply {
            setColor(SpaceDarkBg.toArgb())
            cornerRadius = 4 * context.resources.displayMetrics.density
        }
        background = bgDrawable
        val pxH = (6 * context.resources.displayMetrics.density).toInt()
        val pxV = (2 * context.resources.displayMetrics.density).toInt()
        setPadding(pxH, pxV, pxH, pxV)
    }

    // Headline
    val headlineView = TextView(context).apply {
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TextLight.toArgb())
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (6 * context.resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
    }

    // Body / CTA
    val ctaView = TextView(context).apply {
        textSize = 12f
        setTextColor(TextMuted.toArgb())
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (2 * context.resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
    }

    rootLayout.addView(iconView)
    rootLayout.addView(adBadge)
    rootLayout.addView(headlineView)
    rootLayout.addView(ctaView)
    nativeAdView.addView(rootLayout)

    nativeAdView.iconView = iconView
    nativeAdView.headlineView = headlineView
    nativeAdView.callToActionView = ctaView

    populateArtistNativeAdView(nativeAdView, nativeAd)
    return nativeAdView
}

private fun populateArtistNativeAdView(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline
    (nativeAdView.callToActionView as? TextView)?.text = nativeAd.callToAction ?: nativeAd.advertiser ?: "Visit Site"

    val icon = nativeAd.icon
    if (icon != null && icon.drawable != null) {
        (nativeAdView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
    } else {
        (nativeAdView.iconView as? ImageView)?.setImageResource(com.example.R.drawable.img_default_album_art)
    }
    nativeAdView.setNativeAd(nativeAd)
}

private fun createSongNativeAdView(context: Context, nativeAd: NativeAd): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val density = context.resources.displayMetrics.density

    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = (16 * density).toInt()
        val padV = (10 * density).toInt()
        setPadding(padH, padV, padH, padV)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // 44dp x 44dp cover thumbnail with 8dp rounded corner matching real track album art
    val iconSizePx = (44 * density).toInt()
    val iconView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        val cornerRadiusPx = 8 * density
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    val spacer = android.view.View(context).apply {
        layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), 1)
    }

    // Text details matching song title and artist/album line
    val centerCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val headlineView = TextView(context).apply {
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TextLight.toArgb())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    val subRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (2 * density).toInt()
        }
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 9f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        val bgDrawable = GradientDrawable().apply {
            setColor(TealPrimary.toArgb())
            cornerRadius = 3 * density
        }
        background = bgDrawable
        val pxH = (4 * density).toInt()
        val pxV = (1 * density).toInt()
        setPadding(pxH, pxV, pxH, pxV)
    }

    val bodyView = TextView(context).apply {
        textSize = 12f
        setTextColor(TextMuted.toArgb())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = (6 * density).toInt()
        }
        layoutParams = lp
    }

    subRow.addView(adBadge)
    subRow.addView(bodyView)

    centerCol.addView(headlineView)
    centerCol.addView(subRow)

    // Call To Action button styled as sleek action button
    val ctaView = TextView(context).apply {
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(TealPrimary.toArgb())
        gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = (8 * density).toInt()
        }
        layoutParams = lp
        val bgDrawable = GradientDrawable().apply {
            setColor(SpaceCardBg.toArgb())
            cornerRadius = 12 * density
            setStroke((1 * density).toInt(), TealPrimary.copy(alpha = 0.5f).toArgb())
        }
        background = bgDrawable
        val pxH = (10 * density).toInt()
        val pxV = (4 * density).toInt()
        setPadding(pxH, pxV, pxH, pxV)
    }

    rootLayout.addView(iconView)
    rootLayout.addView(spacer)
    rootLayout.addView(centerCol)
    rootLayout.addView(ctaView)

    nativeAdView.addView(rootLayout)

    nativeAdView.iconView = iconView
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = ctaView

    populateSongNativeAdView(nativeAdView, nativeAd)
    return nativeAdView
}

private fun populateSongNativeAdView(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline
    (nativeAdView.bodyView as? TextView)?.text = nativeAd.advertiser ?: nativeAd.body ?: "Sponsored"
    (nativeAdView.callToActionView as? TextView)?.text = nativeAd.callToAction?.uppercase() ?: "OPEN"

    val icon = nativeAd.icon
    if (icon != null && icon.drawable != null) {
        (nativeAdView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
    } else {
        val image = nativeAd.images.firstOrNull()
        if (image != null && image.drawable != null) {
            (nativeAdView.iconView as? ImageView)?.setImageDrawable(image.drawable)
        } else {
            (nativeAdView.iconView as? ImageView)?.setImageResource(com.example.R.drawable.img_default_album_art)
        }
    }
    nativeAdView.setNativeAd(nativeAd)
}
