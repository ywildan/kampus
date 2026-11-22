package id.personal.campus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Satu tujuan di Dock. */
data class DockItem(val label: String, val icon: ImageVector)

/**
 * Dock pil espresso (KarsaDock): empat tab biasa berupa pil oranye saat aktif, dan tombol
 * tengah (indeks [centerIndex], yaitu Asisten) berupa lingkaran oranye yang menonjol keluar pil
 * dengan cincin kertas di sekelilingnya.
 */
@Composable
fun KarsaDock(
    items: List<DockItem>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    centerIndex: Int = 2,
) {
    Row(
        modifier
            .fillMaxWidth()
            .popShadow(RoundedCornerShape(41.dp))
            .background(Espresso, RoundedCornerShape(41.dp))
            .height(82.dp)
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            val on = selected == index
            val labelColor by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.55f), label = "dockLabel")
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (index == centerIndex) {
                    val fill by animateColorAsState(if (on) Brand400 else Brand500, label = "dockCenter")
                    Box(
                        Modifier
                            .size(60.dp)
                            .brandShadow(CircleShape)
                            .clip(CircleShape)
                            .background(fill)
                            .border(4.dp, Paper, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Text(
                        item.label, Modifier.padding(top = 2.dp), color = labelColor,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center,
                    )
                } else {
                    val pill by animateColorAsState(if (on) Brand500 else Color.Transparent, label = "dockPill")
                    Box(
                        Modifier.width(56.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = labelColor, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        item.label, Modifier.padding(top = 4.dp), color = labelColor,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
