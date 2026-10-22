package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.*

const val MUSIC_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=co.musicplay.ap"
const val JOBS_REPORT_STORE_URL = "https://play.google.com/store/apps/details?id=co.jobsreport.ap"

/**
 * Copies the MusicPlay link to clipboard and opens the share sheet.
 */
fun shareMusicPlayApp(context: Context, toastMsg: String = "Link copied to clipboard!") {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("MusicPlay Link", MUSIC_PLAY_STORE_URL)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "$toastMsg\n$MUSIC_PLAY_STORE_URL", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}

    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MusicPlay - Offline Music Player")
            putExtra(Intent.EXTRA_TEXT, MUSIC_PLAY_STORE_URL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(shareIntent, "Share MusicPlay").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    } catch (_: Exception) {}
}

/**
 * Opens any external Play Store or web link safely.
 */
fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open link: $url", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Professional Share App Card placed directly after playlists.
 */
@Composable
fun ShareAppCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("share_app_card")
            .clickable { shareMusicPlayApp(context, strings.linkCopiedToast) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Professional Share App Icon badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = strings.shareAppTitle,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.shareAppTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextLight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TealPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = strings.offlinePlayerBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.shareAppSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row with copy link button
            Button(
                onClick = { shareMusicPlayApp(context, strings.linkCopiedToast) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_app_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = OnTeal
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Link",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.copyShareLink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Recommended App component for "JobsReport" with custom Blue-Purple JR icon.
 */
@Composable
fun RecommendedJobsReportCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recommended_jobsreport_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header badge: RECOMMENDED APP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.recommendedAppBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5B4FC),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = "Free on Google Play",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Identity Row: Blue-Purple JR Icon + Title + Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Blue Purple JR Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1D4ED8), // Deep Blue
                                    Color(0xFF3B82F6), // Royal Blue
                                    Color(0xFF7C3AED), // Indigo / Purple
                                    Color(0xFF9333EA)  // Vibrant Purple
                                ),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .border(
                            BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JR",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.jobsReportAppName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.jobsReportTagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA5B4FC),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description required: "Hire, find various Jobs opportunities for free via JobsReport App"
            Text(
                text = strings.jobsReportDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = TextLight.copy(alpha = 0.92f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Install Button: opens https://play.google.com/store/apps/details?id=co.jobsreport.ap
            Button(
                onClick = { openUrl(context, JOBS_REPORT_STORE_URL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("install_jobsreport_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Install JobsReport",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.installButton,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

const val WHATSAPP_CHANNEL_URL = "https://whatsapp.com/channel/0029Vb9C8npHAdNeSfsCpa1v"

/**
 * Professional Join WhatsApp Channel card for Playlists screen.
 * Links to official channel: https://whatsapp.com/channel/0029Vb9C8npHAdNeSfsCpa1v
 */
@Composable
fun JoinWhatsAppChannelCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("join_whatsapp_channel_card")
            .clickable { openUrl(context, WHATSAPP_CHANNEL_URL) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
        border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp Icon Badge in WhatsApp Green gradient
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF25D366), Color(0xFF128C7E))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = strings.whatsAppChannelTitle,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.whatsAppChannelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextLight,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "COMMUNITY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF25D366),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MusicPlay - Offline Personal DJ App",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF86EFAC),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description: "Follow the MusicPlay - Offline Personal DJ App channel on WhatsApp: https://whatsapp.com/channel/0029Vb9C8npHAdNeSfsCpa1v"
            Text(
                text = strings.whatsAppChannelSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextLight.copy(alpha = 0.92f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Follow button
            Button(
                onClick = { openUrl(context, WHATSAPP_CHANNEL_URL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("follow_whatsapp_channel_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = strings.joinWhatsAppButton,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.joinWhatsAppButton,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}
