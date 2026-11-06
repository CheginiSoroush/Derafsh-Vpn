package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.DerafshSourceManager
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

/**
 * Derafsh source switcher dialog: built-in free-config pools listed as neutral numbered
 * entries ("Derafsh 1", "Derafsh 2", …). Tapping a row switches the active source through
 * the regular [MainAction] pipeline — the active subscription is immediately refreshed and
 * only the active source is ever auto-updated.
 *
 * A separated premium entry sits below the free sources (task 6): it is never selectable —
 * [onPremiumClick] decides between the premium gate (no license) and the licensed path.
 */
@Composable
internal fun DerafshSourcesDialog(
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onPremiumClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * PICKER_MAX_HEIGHT_FRACTION
    val sources = List(DerafshSourceManager.count()) { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.derafsh_sources_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.derafsh_sources_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                        .verticalScrollbar(listState),
                ) {
                    itemsIndexed(
                        items = sources,
                        key = { _, index -> index },
                    ) { _, index ->
                        SourcePickerRow(
                            label = stringResource(R.string.derafsh_source_name, index + 1),
                            isSelected = index == activeIndex,
                            onSelect = { onSelect(index) },
                        )
                    }
                    item(key = "premium") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        PremiumSourceRow(onClick = onPremiumClick)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun SourcePickerRow(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The premium entry below the free sources: one selectable node (role Button, NOT a radio —
 * it never becomes the active source itself), labeled with the lock emoji per design §6.
 */
@Composable
private fun PremiumSourceRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = false,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.derafsh_sources_premium_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.derafsh_sources_premium_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Premium gate (design §6): shown when a locked premium entry is tapped without a valid
 * license. The primary action routes to the activation screen; the bot CTA appears only
 * while [AppConfig.DERAFSH_BOT_URL] is configured.
 */
@Composable
internal fun DerafshPremiumGateDialog(
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.derafsh_premium_gate_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.derafsh_premium_gate_text))
                if (AppConfig.DERAFSH_BOT_URL.isNotBlank()) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { Utils.openUri(context, AppConfig.DERAFSH_BOT_URL) }) {
                        Text(text = stringResource(R.string.derafsh_premium_gate_get_code))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onActivate) {
                Text(stringResource(R.string.license_activate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

private const val PICKER_MAX_HEIGHT_FRACTION = 0.6f
