// Lucide rotate-cw, ISC; see assets/lucide-icons-LICENSE.txt.
package io.github.zopulus.coogle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ControlIcons {
    // Fixed ColorOS Settings magic_cube_ic artwork; Icon supplies Coogle's theme tint.
    val ActionButton: ImageVector by lazy {
        ImageVector.Builder("ActionButton", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(addPathNodes("M15.299,5.305H22V21.351C22,21.709 21.737,22 21.414,22H15.299V5.305Z"),
                fill = SolidColor(Color.Black), fillAlpha = 0.4f)
            addPath(addPathNodes("M14,22H12.7C12.313,22 12,21.687 12,21.3V4.8C12,3.253 13.253,2 14.8,2H21.3C21.687,2 22,2.313 22,2.7V4H14.8C14.358,4 14,4.358 14,4.8V22Z"),
                fill = SolidColor(Color.Black))
            addPath(addPathNodes("M10,9V13"), stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round)
            addPath(addPathNodes("M7.689,10.283L3.818,7.041C3.427,6.713 2.903,7.133 3.015,7.689C3.153,8.372 3.266,9.162 3.316,10.042H0.957C0.428,10.042 0,10.47 0,10.999C0,11.527 0.428,11.956 0.957,11.956H3.313C3.275,12.632 3.194,13.42 3.017,14.302C2.905,14.857 3.427,15.284 3.817,14.957C5.108,13.877 6.398,12.796 7.689,11.715C8.104,11.368 8.104,10.63 7.689,10.283Z"),
                fill = SolidColor(Color.Black))
        }.build()
    }

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
