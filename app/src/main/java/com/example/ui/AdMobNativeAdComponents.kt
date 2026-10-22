package com.example.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.example.R
import com.example.data.MusicTrack
import com.example.ui.theme.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob Configuration Constants
 * Official App ID: ca-app-pub-8155064094205693~4470440036 (declared in AndroidManifest.xml)
 * Approved Rewarded Ad Unit ID for DJ Crossfade Switching: ca-app-pub-8155064094205693/2082546817
 * Five Approved Native Advanced Ad Unit IDs:
 */
const val NATIVE_AD_UNIT_1 = "ca-app-pub-8155064094205693/7166498907" // Songs List
const val NATIVE_AD_UNIT_2 = "ca-app-pub-8155064094205693/8340601899" // Albums Grid
const val NATIVE_AD_UNIT_3 = "ca-app-pub-8155064094205693/6655363017" // Artists Grid
const val NATIVE_AD_UNIT_4 = "ca-app-pub-8155064094205693/3417071560" // Active Queue & Category/Folder Songs
const val NATIVE_AD_UNIT_5 = "ca-app-pub-8155064094205693/3662368162" // Playlists Bottom Card & Playlist Dialog

const val ADMOB_NATIVE_AD_UNIT_ID = NATIVE_AD_UNIT_2
const val ADMOB_SONGS_NATIVE_AD_UNIT_ID = NATIVE_AD_UNIT_1
const val ADMOB_REWARDED_AD_UNIT_ID = "ca-app-pub-8155064094205693/2082546817"

/**
 * Helper to locate the hosting Activity from Context.
 */
fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Singleton manager to preload and show AdMob Rewarded Ads when switching DJ Crossfade seconds.
 */
object CrossfadeRewardedAdManager {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun preload(context: Context) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context.applicationContext,
            ADMOB_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    Log.d("AdMobRewarded", "Crossfade rewarded ad preloaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Log.w("AdMobRewarded", "Failed to load crossfade rewarded ad: ${error.message}")
                }
            }
        )
    }

    fun showAd(context: Context, onComplete: () -> Unit) {
        val activity = context.findActivity()
        val ad = rewardedAd
        if (activity != null && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preload(context)
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    rewardedAd = null
                    preload(context)
                    onComplete()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d("AdMobRewarded", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            }
        } else {
            preload(context)
            onComplete()
        }
    }
}

/**
 * Singleton manager to preload, maintain warm cache, and manage Native Advanced Ads
 * across the 5 approved units without disappearing on screen changes.
 */
object AppNativeAdsManager {
    private const val TAG = "AppNativeAds"

    // Map from adUnitId to an observable list of loaded NativeAd objects
    private val adsMap = mutableMapOf<String, SnapshotStateList<NativeAd>>()
    private val loadingUnits = mutableSetOf<String>()

    fun getLoadedAds(adUnitId: String): SnapshotStateList<NativeAd> {
        return synchronized(this) {
            adsMap.getOrPut(adUnitId) { mutableStateListOf() }
        }
    }

    /**
     * Preloads initial native ads across all 5 units on app startup.
     */
    fun preloadInitialAds(context: Context) {
        val appContext = context.applicationContext
        val allUnits = listOf(
            NATIVE_AD_UNIT_1,
            NATIVE_AD_UNIT_2,
            NATIVE_AD_UNIT_3,
            NATIVE_AD_UNIT_4,
            NATIVE_AD_UNIT_5
        )
        for (unit in allUnits) {
            requestAds(appContext, unit, targetCount = 2)
        }
    }

    /**
     * Requests native ads for an ad unit up to targetCount smoothly without burst flooding.
     */
    fun requestAds(context: Context, adUnitId: String, targetCount: Int = 1) {
        val appContext = context.applicationContext
        val list = getLoadedAds(adUnitId)
        if (list.size >= targetCount) return
        if (loadingUnits.contains(adUnitId)) return

        loadingUnits.add(adUnitId)
        loadNextAd(appContext, adUnitId, targetCount)
    }

    private fun loadNextAd(context: Context, adUnitId: String, targetCount: Int) {
        val list = getLoadedAds(adUnitId)
        if (list.size >= targetCount) {
            loadingUnits.remove(adUnitId)
            return
        }

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                list.add(nativeAd)
                Log.d(TAG, "Native ad loaded for $adUnitId (pool size: ${list.size})")
                if (list.size < targetCount) {
                    loadNextAd(context, adUnitId, targetCount)
                } else {
                    loadingUnits.remove(adUnitId)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Native ad load failed for $adUnitId: ${loadAdError.message} (code ${loadAdError.code})")
                    loadingUnits.remove(adUnitId)
                    if (list.isEmpty()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            requestAds(context, adUnitId, targetCount)
                        }, 5000L)
                    }
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setReturnUrlsForImageAssets(false)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}

/**
 * Remembers and preloads AdMob Native Ads using the persistent AppNativeAdsManager.
 * Retains loaded ads across navigation backstack so they never disappear.
 */
@Composable
fun rememberNativeAds(
    itemCount: Int,
    adUnitId: String,
    cadence: Int = 3
): SnapshotStateList<NativeAd> {
    val context = LocalContext.current
    val loadedAds = remember(adUnitId) { AppNativeAdsManager.getLoadedAds(adUnitId) }
    val neededCount = if (itemCount < cadence) 0 else (itemCount / cadence).coerceIn(1, 10)

    LaunchedEffect(itemCount, adUnitId, neededCount) {
        if (neededCount > 0) {
            AppNativeAdsManager.requestAds(context, adUnitId, neededCount)
        }
    }
    return loadedAds
}

/**
 * State holder for backward-compatibility with screens referencing NativeAdsState.
 */
@Stable
class NativeAdsState(val rawList: SnapshotStateList<NativeAd> = mutableStateListOf()) {
    val failedAdIndices = mutableStateListOf<Int>()

    val loadedAds: Map<Int, NativeAd> = object : AbstractMap<Int, NativeAd>() {
        override val entries: Set<Map.Entry<Int, NativeAd>>
            get() = rawList.mapIndexed { index, ad ->
                java.util.AbstractMap.SimpleImmutableEntry(index, ad)
            }.toSet()

        override fun get(key: Int): NativeAd? {
            if (rawList.isEmpty()) return null
            return rawList.getOrNull(key) ?: rawList[key % rawList.size]
        }

        override fun containsKey(key: Int): Boolean = rawList.isNotEmpty()
        override val size: Int get() = rawList.size
        override fun isEmpty(): Boolean = rawList.isEmpty()
    }
}

@Composable
fun rememberNativeAdsState(
    itemCount: Int,
    adUnitId: String = ADMOB_NATIVE_AD_UNIT_ID
): NativeAdsState {
    val list = rememberNativeAds(itemCount = itemCount, adUnitId = adUnitId, cadence = 3)
    return remember(list) {
        NativeAdsState(rawList = list)
    }
}

@Composable
fun rememberSongNativeAdsState(
    itemCount: Int,
    adUnitId: String = ADMOB_SONGS_NATIVE_AD_UNIT_ID
): NativeAdsState {
    val list = rememberNativeAds(itemCount = itemCount, adUnitId = adUnitId, cadence = 10)
    return remember(list) {
        NativeAdsState(rawList = list)
    }
}

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
 * Combines real list items with Native Ad items according to AdMob placement policies.
 * Cadence rule: 1 ad after every 3 items in albums and artists (1, 2, 3, ad, 4, 5, 6, ad and more).
 */
fun <T> buildGridItemsWithAds(
    realItems: List<T>,
    failedAdIndices: Collection<Int> = emptyList()
): List<GridItemWrapper<T>> {
    if (realItems.isEmpty()) return emptyList()

    val count = realItems.size
    val maxAdsAllowed = if (count < 3) 0 else (count / 3).coerceAtMost(20)

    val result = mutableListOf<GridItemWrapper<T>>()
    var adsInserted = 0

    for (i in realItems.indices) {
        result.add(GridItemWrapper.RealItem(realItems[i]))
        val itemNum = i + 1

        if (itemNum % 3 == 0 && adsInserted < maxAdsAllowed) {
            result.add(GridItemWrapper.NativeAdItem(adsInserted))
            adsInserted++
        }
    }

    return result
}

/**
 * Combines real songs with Native Ads according to rules:
 * - If songs are below 10: do not show ad
 * - If songs >= 10: insert an ad after every 10 songs
 */
fun buildSongItemsWithAds(
    realItems: List<MusicTrack>,
    failedAdIndices: Collection<Int> = emptyList()
): List<SongListItemWrapper> {
    if (realItems.size < 10) {
        return realItems.map { SongListItemWrapper.SongItem(it) }
    }

    val result = mutableListOf<SongListItemWrapper>()
    var adIndex = 0

    for (i in realItems.indices) {
        result.add(SongListItemWrapper.SongItem(realItems[i]))
        val songNumber = i + 1

        if (songNumber % 10 == 0) {
            result.add(SongListItemWrapper.NativeAdItem(adIndex))
            adIndex++
        }
    }

    return result
}

/**
 * Album Card Native Ad layout matching exact size, shape, and style of Album cards.
 * Uses official Google NativeAdView wrapper and includes mandatory "Ad" badge.
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
            // Sleek loading placeholder card of identical dimensions
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
 * Artist Card Native Ad layout matching exact size, shape, and style of Artist cards.
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

    // Top media container (130dp height matching album cover)
    val mediaContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (130 * context.resources.displayMetrics.density).toInt()
        )
        clipToOutline = true
        val cornerRadiusPx = 12 * context.resources.displayMetrics.density
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    val fallbackImageView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = android.view.View.GONE
    }

    mediaContainer.addView(mediaView)
    mediaContainer.addView(fallbackImageView)

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
    nativeAdView.mediaView = mediaView
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = ctaView
    nativeAdView.imageView = fallbackImageView

    populateAlbumNativeAdView(nativeAdView, nativeAd)
    return nativeAdView
}

private fun populateAlbumNativeAdView(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline
    (nativeAdView.bodyView as? TextView)?.text = nativeAd.body ?: nativeAd.advertiser ?: "Sponsored content"
    (nativeAdView.callToActionView as? TextView)?.text = nativeAd.callToAction ?: "Learn More"

    // Set mediaContent so that MediaView renders the AdMob media asset
    nativeAdView.mediaView?.mediaContent = nativeAd.mediaContent

    val fallbackImageView = nativeAdView.imageView as? ImageView
    val imageDrawable = nativeAd.images.firstOrNull()?.drawable ?: nativeAd.icon?.drawable
    if (nativeAd.mediaContent == null || nativeAd.mediaContent?.aspectRatio == 0f) {
        if (imageDrawable != null) {
            fallbackImageView?.setImageDrawable(imageDrawable)
            fallbackImageView?.visibility = android.view.View.VISIBLE
        } else {
            fallbackImageView?.setImageResource(R.drawable.img_app_icon)
            fallbackImageView?.visibility = android.view.View.VISIBLE
        }
    } else {
        fallbackImageView?.visibility = android.view.View.GONE
    }

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
        clipToOutline = true
        val cornerRadiusPx = 36 * context.resources.displayMetrics.density
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
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

    val iconDrawable = nativeAd.icon?.drawable ?: nativeAd.images.firstOrNull()?.drawable
    val imageView = nativeAdView.iconView as? ImageView
    if (iconDrawable != null) {
        imageView?.setImageDrawable(iconDrawable)
    } else {
        imageView?.setImageResource(R.drawable.img_app_icon)
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

    // 44dp x 44dp cover thumbnail with 8dp rounded corner
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

    val iconDrawable = nativeAd.icon?.drawable ?: nativeAd.images.firstOrNull()?.drawable
    val imageView = nativeAdView.iconView as? ImageView
    if (iconDrawable != null) {
        imageView?.setImageDrawable(iconDrawable)
    } else {
        imageView?.setImageResource(R.drawable.img_app_icon)
    }
    nativeAdView.setNativeAd(nativeAd)
}

/**
 * Native Advanced Ad Card placed at the bottom of Playlists screen below promotional cards.
 */
@Composable
fun PlaylistNativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = NATIVE_AD_UNIT_5
) {
    val loadedAds = rememberNativeAds(itemCount = 1, adUnitId = adUnitId, cadence = 1)
    val nativeAd = loadedAds.firstOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f))
    ) {
        SongNativeAdRow(
            nativeAd = nativeAd,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
