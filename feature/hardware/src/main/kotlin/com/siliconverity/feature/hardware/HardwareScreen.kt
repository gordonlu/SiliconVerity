package com.siliconverity.feature.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siliconverity.core.designsystem.SvFormat
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvThermalStatus
import com.siliconverity.core.model.Confidence
import com.siliconverity.core.model.HardwareFact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(
    hardwareState: HardwareUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.hw_title), style = MaterialTheme.typography.labelLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.hw_back))
                        }
                    },
                    actions = {
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.hw_refresh)) }
                    },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = SvSpacing.PageHorizontal, vertical = SvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
        ) {
            if (hardwareState.loading && hardwareState.facts.isEmpty()) {
                item { androidx.compose.material3.CircularProgressIndicator() }
            }
            hardwareState.error?.let {
                item { Text(stringResource(R.string.hw_error, it), color = MaterialTheme.colorScheme.error) }
            }
            items(hardwareState.facts, key = { it.key }) { fact ->
                FactCard(fact)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactCard(fact: HardwareFact) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    fact.key,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = SvSpacing.Sm),
                )
                Text(
                    friendlyDisplay(fact),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            if (fact.key == "thermal.status") {
                Text(
                    SvThermalStatus.detail(fact.displayValue ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    fact.sourceType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfidenceChip(fact.confidence)
            }
            if (expanded) {
                Spacer(Modifier.height(SvSpacing.Sm))
                Text(stringResource(R.string.hw_source_id, fact.sourceId ?: ""), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.hw_raw_value, fact.rawValue ?: ""), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.hw_collected_at, fact.collectedAt), style = MaterialTheme.typography.bodySmall)
                fact.capabilityStatus?.let {
                    Text(stringResource(R.string.hw_capability, it.toString()), style = MaterialTheme.typography.bodySmall)
                }
                if (fact.warnings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.hw_warnings, fact.warnings.joinToString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (fact.conflictingEvidence.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.hw_conflicts), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    fact.conflictingEvidence.forEach { ev ->
                        Text(
                            "  - [${ev.sourceType}] ${ev.sourceId} = ${ev.rawValue}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Confidence) {
    val color = when (confidence) {
        Confidence.HIGH -> MaterialTheme.colorScheme.primary
        Confidence.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Confidence.LOW, Confidence.UNKNOWN, Confidence.CONFLICTED -> MaterialTheme.colorScheme.error
    }
    Text(
        confidence.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 把存储/调试用原始值映射为 UI 友好文案 (布尔、字节数、温度、百分比等)。 */
@Composable
private fun friendlyDisplay(fact: HardwareFact): String {
    val v = fact.displayValue ?: return stringResource(R.string.hw_unknown)
    return when (fact.key) {
        "thermal.status" -> SvThermalStatus.short(v)
        "memory.totalMem", "memory.availMem", "memory.threshold",
        "storage.fs.total", "storage.fs.available", "storage.fs.free", "storage.fs.block_size" ->
            SvFormat.bytes(v) ?: v
        "memory.lowMemory" -> if (v == "true") stringResource(R.string.hw_low_memory) else stringResource(R.string.hw_memory_ok)
        "battery.charging" -> if (v == "true") stringResource(R.string.hw_charging) else stringResource(R.string.hw_not_charging)
        "battery.level" -> v.toIntOrNull()?.let { "$it%" } ?: v
        "battery.temperature" -> v.toDoubleOrNull()?.let { "%.1f°C".format(it) } ?: v
        else -> v
    }
}
