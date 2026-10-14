// Lucide rotate-cw, ISC; see assets/lucide-icons-LICENSE.txt.
package io.github.zopulus.coogle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ControlIcons {
    val Restart: ImageVector by lazy {
        ImageVector.Builder("Restart", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(21f, 12f)
                arcToRelative(9f, 9f, 0f, true, true, -9f, -9f)
                curveToRelative(2.52f, 0f, 4.93f, 1f, 6.74f, 2.74f)
                lineTo(21f, 8f)
                moveTo(21f, 3f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(-5f)
            }
        }.build()
    }
}
