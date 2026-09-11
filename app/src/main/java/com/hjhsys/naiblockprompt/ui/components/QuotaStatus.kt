package com.hjhsys.naiblockprompt.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.ui.SubscriptionUiState

internal data class QuotaStatusValues(
    val anlas: Int? = null,
    val opusPercent: Int? = null,
    val loading: Boolean = false,
)

internal fun SubscriptionUiState.toQuotaStatusValues(): QuotaStatusValues = when (this) {
    SubscriptionUiState.Unavailable -> QuotaStatusValues()
    SubscriptionUiState.Loading -> QuotaStatusValues(loading = true)
    is SubscriptionUiState.Available -> QuotaStatusValues(anlas, opusPercent)
}

@Composable
fun QuotaStatus(
    status: SubscriptionUiState,
    modifier: Modifier = Modifier,
) {
    val values = remember(status) { status.toQuotaStatusValues() }
    val unavailable = stringResource(R.string.quota_unavailable)
    val loading = stringResource(R.string.quota_loading)
    val anlasValue = if (values.loading) loading else values.anlas?.toString() ?: unavailable
    val opusValue = if (values.loading) loading else values.opusPercent?.let { "$it%" } ?: unavailable

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${stringResource(R.string.quota_anlas, anlasValue)}, ${stringResource(R.string.quota_opus, opusValue)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun quotaStatusText(status: SubscriptionUiState): String {
    val values = remember(status) { status.toQuotaStatusValues() }
    val unavailable = stringResource(R.string.quota_unavailable)
    val loading = stringResource(R.string.quota_loading)
    val anlasValue = if (values.loading) loading else values.anlas?.toString() ?: unavailable
    val opusValue = if (values.loading) loading else values.opusPercent?.let { "$it%" } ?: unavailable
    return "${stringResource(R.string.quota_anlas, anlasValue)}, ${stringResource(R.string.quota_opus, opusValue)}"
}
