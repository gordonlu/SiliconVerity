package com.siliconverity.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object SvShapes {
    val Panel = RoundedCornerShape(2.dp)
    val Button = RoundedCornerShape(3.dp)

    val MaterialShapes = Shapes(
        small = Panel,
        medium = Panel,
        large = Panel,
    )
}
