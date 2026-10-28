package ws.crandell.fafo.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.crandell.fafo.camera.IncidentSidecar
import ws.crandell.fafo.camera.PhotoMeta
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.StateBadge
import ws.crandell.fafo.ui.theme.FafoColors
import java.io.File
import java.text.DateFormat
import java.util.Date

fun closeLabel(s: IncidentSidecar): String = when (s.closeReason) {
    null -> "Open"
    "CANCELLED" -> "Cancelled (plugged back in)"
    "UNLOCKED" -> "Ended by unlock"
    "DISARMED" -> "Disarmed"
    "RESET_REMOTE" -> "Reset by ${s.closedBy ?: "a trusted device"}"
    "DISARMED_REMOTE" -> "Disarmed by ${s.closedBy ?: "a trusted device"}"
    else -> s.closeReason
}

private fun closeColor(s: IncidentSidecar): Color = when (s.closeReason) {
    null -> FafoColors.RedText
    "RESET_REMOTE", "DISARMED_REMOTE" -> FafoColors.Guard
    "CANCELLED" -> FafoColors.Amber
    else -> FafoColors.Steel
}

@Composable
private fun JpegImage(file: File, sampleSize: Int, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }
    Box(modifier.background(FafoColors.Surface2), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(FafoIcons.Camera, contentDescription = null, tint = FafoColors.Outline, modifier = Modifier.size(28.dp))
        }
    }
}

/** Incidents list, photo viewer, delete and user-initiated share (AC5.6). Nothing is ever sent automatically. */
@Composable
fun IncidentsScreen(
    incidents: List<IncidentSidecar>,
    openIncident: String?,
    openPhoto: String?,
    photoFile: (String, String) -> File,
    onOpenIncident: (String?) -> Unit,
    onOpenPhoto: (String?) -> Unit,
    onDeletePhoto: (String, String) -> Unit,
    onDeleteIncident: (String) -> Unit,
    onShare: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val inc = incidents.firstOrNull { it.id == openIncident }
    Column(modifier.fillMaxSize().padding(horizontal = FafoDims.Gutter, vertical = 12.dp)) {
        when {
            inc != null && openPhoto != null -> {
                val f = photoFile(inc.id, openPhoto)
                JpegImage(f, 1, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onShare(f) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = FafoColors.White)
                        Spacer(Modifier.width(8.dp)); Text("Share", color = FafoColors.White)
                    }
                    OutlinedButton(onClick = { onDeletePhoto(inc.id, openPhoto); onOpenPhoto(null) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = FafoColors.RedText)
                        Spacer(Modifier.width(8.dp)); Text("Delete", color = FafoColors.RedText)
                    }
                }
            }
            inc != null -> {
                Text(fmt.format(Date(inc.startedAt)), style = MaterialTheme.typography.titleLarge, color = FafoColors.White)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StateBadge(closeLabel(inc), closeColor(inc))
                }
                Text("${inc.photos.size} photos · kept on this phone (never sent)", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
                if (inc.photos.isEmpty()) {
                    Text("No photos (camera off, failed, or the phone had rebooted).", style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted, modifier = Modifier.padding(top = 12.dp))
                }
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.weight(1f).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(inc.photos, key = PhotoMeta::file) { p ->
                        JpegImage(photoFile(inc.id, p.file), 8, Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)).clickable { onOpenPhoto(p.file) })
                    }
                }
                OutlinedButton(onClick = { onDeleteIncident(inc.id); onOpenIncident(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)) {
                    Text("Delete incident", color = FafoColors.RedText)
                }
            }
            else -> {
                if (incidents.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(FafoIcons.ShieldCheck, contentDescription = null, tint = FafoColors.Steel, modifier = Modifier.size(72.dp))
                        Text("No incidents yet.", style = MaterialTheme.typography.titleLarge, color = FafoColors.White, modifier = Modifier.padding(top = 12.dp).testTag("noIncidents"))
                        Text("Every alarm is saved here with its photos.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(incidents, key = IncidentSidecar::id) { s ->
                        FafoCard(Modifier.testTag("incident_${s.id}"), accent = closeColor(s), onClick = { onOpenIncident(s.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val first = s.photos.firstOrNull()
                                JpegImage(
                                    if (first != null) photoFile(s.id, first.file) else File(""), 8,
                                    Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                                    Text(closeLabel(s), style = MaterialTheme.typography.bodyMedium, color = closeColor(s))
                                    Text("${s.photos.size} photos · on this phone", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
