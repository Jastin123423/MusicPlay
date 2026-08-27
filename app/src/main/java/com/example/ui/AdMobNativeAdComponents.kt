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
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * AdMob Configuration Constants
 * Requirement 1: Official App ID ca-app-pub-8155064094205693~4470440036 declared in AndroidManifest
 * Requirement 1: Native Advanced Ad Unit ID ca-app-pub-8155064094205693/4658142293
 */
const val ADMOB_NATIVE_AD_UNIT_ID = "ca-app-pub-8155064094205693/4658142293"

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
