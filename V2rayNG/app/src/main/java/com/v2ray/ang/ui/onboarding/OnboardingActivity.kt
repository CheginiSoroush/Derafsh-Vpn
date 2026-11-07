package com.v2ray.ang.ui.onboarding

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.OnboardingManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

/**
 * First-launch onboarding for Derafsh: a five-slide intro carousel that presents the app
 * (welcome, smart speed test, privacy, free-with-ads) and collects explicit consent to
 * the Privacy Policy and Terms of Use before the app is used — the disclosure Google Play
 * expects from ad-supported apps. "Skip" jumps to the consent slide; consent is required,
 * never bypassed. Completion is persisted through [OnboardingManager].
 */
class OnboardingActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
        var consentAccepted by remember { mutableStateOf(false) }

        BackHandler(enabled = pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (pagerState.currentPage < PAGE_CONSENT) {
                        TextButton(onClick = {
                            // Skip never bypasses consent: it lands on the consent slide.
                            scope.launch { pagerState.animateScrollToPage(PAGE_CONSENT) }
                        }) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    OnboardingPage(
                        page = page,
                        consentAccepted = consentAccepted,
                        onConsentChanged = { consentAccepted = it },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(PAGE_COUNT) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                        )
                    }
                }

                val isLast = pagerState.currentPage == PAGE_COUNT - 1
                Button(
                    onClick = {
                        if (isLast && consentAccepted) {
                            OnboardingManager.markCompleted()
                            context.startActivity(Intent(context, MainActivity::class.java))
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    // v1.2.54: consent is now the final slide — the start button stays
                    // disabled until the checkbox is ticked instead of bouncing back.
                    enabled = !isLast || consentAccepted,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .height(52.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isLast) R.string.onboarding_start else R.string.onboarding_next
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    @Composable
    private fun OnboardingPage(
        page: Int,
        consentAccepted: Boolean,
        onConsentChanged: (Boolean) -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        ) {
            if (page < SLIDES.size) {
                val slide = SLIDES[page]
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    if (page == 0) {
                        Image(
                            painter = painterResource(R.drawable.ic_derafsh_emblem),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(64.dp),
                        )
                    } else {
                        Text(text = slide.emoji, style = MaterialTheme.typography.displayLarge)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(slide.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(slide.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                ConsentSlide(consentAccepted = consentAccepted, onConsentChanged = onConsentChanged)
            }
        }
    }

    @Composable
    private fun ConsentSlide(consentAccepted: Boolean, onConsentChanged: (Boolean) -> Unit) {
        val context = LocalContext.current
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(text = "🛡️", style = MaterialTheme.typography.displayLarge)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_5_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_5_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }) {
            Text(text = stringResource(R.string.onboarding_read_policy))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consentAccepted, onCheckedChange = onConsentChanged)
            Text(
                text = stringResource(R.string.onboarding_consent_checkbox),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    private data class OnboardingSlide(
        val emoji: String,
        val titleRes: Int,
        val bodyRes: Int,
    )

    private companion object {
        const val PAGE_COUNT = 5

        /** The consent slide IS the final slide; consent can never be skipped past. */
        const val PAGE_CONSENT = 4

        /** Intro slides; the consent screen closes the carousel. */
        val SLIDES = listOf(
            OnboardingSlide("", R.string.onboarding_1_title, R.string.onboarding_1_body),
            OnboardingSlide("⚡", R.string.onboarding_2_title, R.string.onboarding_2_body),
            OnboardingSlide("🔒", R.string.onboarding_3_title, R.string.onboarding_3_body),
            OnboardingSlide("🎁", R.string.onboarding_4_title, R.string.onboarding_4_body),
        )
    }
}
