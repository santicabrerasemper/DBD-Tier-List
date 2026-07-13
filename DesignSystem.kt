@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.santi.dbdmeta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE84A5F),
    secondary = Color(0xFFFFC857),
    tertiary = Color(0xFF69D2E7),
    background = Color(0xFF101114),
    surface = Color(0xFF17191F),
    surfaceVariant = Color(0xFF22252E),
    onPrimary = Color.White,
    onSecondary = Color(0xFF191919),
    onBackground = Color(0xFFEDEEF2),
    onSurface = Color(0xFFEDEEF2)
)

@Composable
fun DbdMetaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

fun Tier.color(): Color = when (this) {
    Tier.S -> Color(0xFFFFC857)
    Tier.A -> Color(0xFFE84A5F)
    Tier.B -> Color(0xFF8A7CFF)
    Tier.C -> Color(0xFF69D2E7)
    Tier.D -> Color(0xFF8E939E)
}

@Composable
fun TierBadge(tier: Tier, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tier.color()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tier.name,
            color = if (tier == Tier.S) Color(0xFF151515) else Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun MetaTagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TierItemRow(
    item: TierListItem,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierBadge(item.entry.tier)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = item.entry.score.toInt().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = item.entry.tier.color()
                    )
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB9BDC8)
                )
                Text(
                    text = item.entry.reason,
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.entry.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.entry.tags.take(4).forEach { MetaTagChip(it) }
                        if (item.entry.manual) MetaTagChip("manual")
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SectionHeader(tier: Tier, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tier.color())
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${tier.name} Tier",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB9BDC8)
        )
    }
}
