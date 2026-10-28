package ws.crandell.fafo.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.crandell.fafo.camera.IncidentSidecar
import ws.crandell.fafo.camera.PhotoMeta
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

@Composable
private fun JpegImage(file: File, sampleSize: Int, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }
    Box(modifier) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
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
    Column(modifier.fillMaxSize().padding(16.dp)) {
        when {
            inc != null && openPhoto != null -> {
                val f = photoFile(inc.id, openPhoto)
                ScreenTitle("Photo")
                JpegImage(f, 1, Modifier.fillMaxWidth().weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onShare(f) }) { Text("Share") }
                    OutlinedButton(onClick = { onDeletePhoto(inc.id, openPhoto); onOpenPhoto(null) }) { Text("Delete") }
                }
            }
            inc != null -> {
                ScreenTitle(fmt.format(Date(inc.startedAt)))
                Text("${closeLabel(inc)} · ${inc.photos.size} photos · kept on this phone (never sent)")
                OutlinedButton(onClick = { onDeleteIncident(inc.id); onOpenIncident(null) }) { Text("Delete incident") }
                if (inc.photos.isEmpty()) Text("No photos (camera off, failed, or the phone had rebooted).")
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(inc.photos, key = PhotoMeta::file) { p ->
                        JpegImage(photoFile(inc.id, p.file), 8, Modifier.aspectRatio(0.75f).clickable { onOpenPhoto(p.file) })
                    }
                }
            }
            else -> {
                ScreenTitle("Incidents")
                if (incidents.isEmpty()) Text("No incidents yet.", Modifier.testTag("noIncidents"))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(incidents, key = IncidentSidecar::id) { s ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenIncident(s.id) }.testTag("incident_${s.id}")) {
                            Column(Modifier.padding(12.dp)) {
                                Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.titleMedium)
                                Text("${closeLabel(s)} · ${s.photos.size} photos")
                            }
                        }
                    }
                }
            }
        }
    }
}
