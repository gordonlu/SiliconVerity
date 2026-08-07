package com.siliconverity.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SvPanel(
    modifier: Modifier = Modifier,
    border: Boolean = true,
    translucent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = SvShapes.Panel,
        color = if (translucent) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.surface,
        border = if (border) BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline) else null,
    ) {
        Column(content = content)
    }
}
