package com.v2ray.ang.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay

/**
 * Derafsh house ads (ADS_PROVIDER = "house"): first-party promo surfaces that stand in for
 * AdMob placements until production ad units are wired. First-party = zero third-party data
 * sharing, no SDK spin-up, works offline, and still exercises the same UX slots users will
 * see once real ads are enabled.
 */

/** Auto-dismiss delay for the full-screen house ad. */
private const val HOUSE_INTERSTITIAL_DURATION_MS = 4_000L

/**
 * Branded banner card shown in the main screen's ad slot. Tapping it opens the Telegram bot
 * (v1.2.52: the GitHub project page is gone from the app). Deliberately static (no looping
 * animation) to respect reduced-motion accessibility.
 */
@Composable
fun DerafshHouseBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val a11y = stringResource(R.string.house_banner_title)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (AppConfig.DERAFSH_BOT_URL.isNotBlank()) {
                    Utils.openUri(context, AppConfig.DERAFSH_BOT_URL)
                }
            }
            .semantics { contentDescription = a11y },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_derafsh_emblem),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.house_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.house_banner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Full-screen house ad content (hosted by HouseAdActivity). Auto-closes after a short delay
 * and always offers an immediate Continue button — a house ad must never trap the user.
 */
@Composable
fun DerafshHouseInterstitial(onClosed: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_derafsh_emblem),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.house_interstitial_tagline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onClosed) {
                Text(stringResource(R.string.house_interstitial_cta))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.house_interstitial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(HOUSE_INTERSTITIAL_DURATION_MS)
        onClosed()
    }
}
