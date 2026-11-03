package com.v2ray.ang.ui.main

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar

/**
 * Server picker shown from the main connect screen: every server across all groups, flattened by
 * the ViewModel, keyed by GUID. Tapping a row selects it through the regular [MainAction]
 * pipeline and closes the dialog.
 */
@Composable
internal fun DerafshServerPickerDialog(
    servers: List<ServerRowUiModel>,
    selectedGuid: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * PICKER_MAX_HEIGHT_FRACTION

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_server)) },
        text = {
            if (servers.isEmpty()) {
                Text(
                    text = stringResource(R.string.toast_none_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                        .verticalScrollbar(listState),
                ) {
                    itemsIndexed(
                        items = servers,
                        key = { _, row -> row.guid },
                    ) { _, row ->
                        ServerPickerRow(
                            row = row,
                            isSelected = row.guid == selectedGuid,
                            onSelect = { onSelect(row.guid) },
                        )
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
private fun ServerPickerRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.Button,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Derafsh privacy: rows are identified by their location flag only — config
        // names and hostnames are never shown.
        Text(
            text = row.flag,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = row.typeDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        ServerDelayText(testDelayMillis = row.testDelayMillis)
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            // Selection state is already announced by the selectable row node.
            Icon(
                painter = painterResource(R.drawable.ic_action_done),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Delay readout shared with the selected-server card, using the same formatting and colors as the
 * server rows: 0 means untested (nothing shown), negative values are failures.
 */
@Composable
internal fun ServerDelayText(
    testDelayMillis: Long,
    modifier: Modifier = Modifier,
) {
    if (testDelayMillis == 0L) return
    Text(
        text = stringResource(R.string.server_test_delay_value, testDelayMillis),
        style = MaterialTheme.typography.bodySmall,
        color = if (testDelayMillis < 0L) colorPingRed else colorPing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private const val PICKER_MAX_HEIGHT_FRACTION = 0.7f
