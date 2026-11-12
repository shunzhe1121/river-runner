package com.riverrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.Changelog
import com.riverrunner.i18n.LocalStrings

/** Colour of a changelog section, keyed on its English title. */
private fun sectionColor(title: Changelog.Item): Color = when (title.en.lowercase()) {
    "added" -> Palette.Green
    "changed" -> Palette.Gold
    "fixed" -> Palette.Blue
    else -> Palette.TextDim
}

/** The changelog card: newest release first. [onShowAll] adds an "All updates" button. */
@Composable
fun ChangelogPanel(
    releases: List<Changelog.Release>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    val st = LocalStrings.current
    val zh = st.isZh
    Column(
        modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 340.dp)
            .clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
            .border(1.dp, Palette.GoldDim, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(st.whatsNew, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            releases.forEachIndexed { ri, rel ->
                item(key = "v${rel.version}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = if (ri == 0) 0.dp else 8.dp),
                    ) {
                        Text(
                            rel.version, color = Palette.Ink, fontSize = 14.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Gold).padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                        if (rel.date.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(rel.date, color = Palette.TextDim, fontSize = 12.sp, fontFamily = Numeric)
                        }
                    }
                }
                rel.sections.forEachIndexed { si, sec ->
                    item(key = "v${rel.version}-s$si") {
                        val c = sectionColor(sec.title)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(sec.title.text(zh), color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            for (entry in sec.items) {
                                Row {
                                    Box(
                                        Modifier.padding(top = 7.dp, end = 8.dp).size(5.dp)
                                            .clip(CircleShape).background(c),
                                    )
                                    Text(entry.text(zh), color = Palette.Text, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onShowAll != null) SmallButton(st.allUpdates, onShowAll)
            Spacer(Modifier.weight(1f))
            ActionButton(st.gotIt, Palette.Gold, Modifier.widthIn(min = 110.dp).heightIn(min = 40.dp), textColor = Palette.Ink, onClick = onClose)
        }
    }
}

/** "What's new" dialog over any screen. */
@Composable
fun WhatsNewDialog(releases: List<Changelog.Release>, onDismiss: () -> Unit, onShowAll: (() -> Unit)?) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ChangelogPanel(releases, onDismiss, Modifier.padding(16.dp), onShowAll)
    }
}
