package io.github.monochrome0xd.customanimationalert

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * 자체 프로모 배너 — Custom Animation Wallpaper / Keyboard 홍보용.
 * 첨부 PNG 2장(투명 배경)을 그대로 표시. 클릭 없음.
 */
@Composable
fun PromoBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 두 아이콘 살짝 겹쳐서 stack
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                Image(
                    painter = painterResource(R.drawable.promo_keyboard),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Image(
                    painter = painterResource(R.drawable.promo_image),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Custom Animation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Wallpaper & Keyboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "키보드와 배경도 원하는대로 (예정)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
