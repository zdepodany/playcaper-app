package cz.titulky.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import sh.calvin.reorderable.ReorderableColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Souhrn pro řádek v Nastavení: „DeepL → Google → … → offline“ (jen služby, které se opravdu zkusí). */
internal fun souhrnSluzeb(t: (String) -> String, poradi: List<String>, vypnute: Set<String>, maDeeplKlic: Boolean): String =
    (SluzbyPrekladu.aktivni(poradi, vypnute, maDeeplKlic).map(SluzbyPrekladu::nazev) + t("sluzbaOfflineKratce"))
        .joinToString(" → ")

/**
 * Podstránka Nastavení → Překlad → Služby: pořadí přetažením za úchyt, zapnutí přepínačem.
 * DeepL otevírá podstránku s klíči; bez klíče má místo přepínače tlačítko. Offline záloha je pevně poslední.
 * Čtečka obrazovky nabízí u řádku akce „Posunout nahoru / dolů“ (přetahování s ní nejde).
 */
@Composable
internal fun SluzbyObrazovka(
    t: (String) -> String,
    poradi: List<String>, vypnute: Set<String>, maDeeplKlic: Boolean,
    naZpet: () -> Unit, naPresun: (Int, Int) -> Unit, naZapnout: (String, Boolean) -> Unit, naDeepl: () -> Unit,
) {
    val haptika = LocalHapticFeedback.current
    ZpetKostra(t("sluzbyNadpis"), naZpet) {
        Text(
            t("sluzbyPopis"), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            ReorderableColumn(list = poradi, onSettle = naPresun, onMove = {
                haptika.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }) { i, id, tazeny ->
                val zvednuti by animateDpAsState(if (tazeny) 6.dp else 0.dp, label = "zvednuti")
                Surface(
                    color = if (tazeny) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    shadowElevation = zvednuti,
                ) {
                    Column {
                        if (i > 0 && !tazeny) HorizontalDivider()
                        val uchyt = Modifier.draggableHandle(onDragStarted = {
                            haptika.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                        RadekSluzby(
                            t, id, uchyt = uchyt,
                            zapnuto = id !in vypnute, maDeeplKlic = maDeeplKlic,
                            naZapnout = { naZapnout(id, it) }, naDeepl = naDeepl,
                            modifier = Modifier.semantics {
                                customActions = listOfNotNull(
                                    if (i > 0) CustomAccessibilityAction(t("sluzbaNahoru")) { naPresun(i, i - 1); true } else null,
                                    if (i < poradi.lastIndex) CustomAccessibilityAction(t("sluzbaDolu")) { naPresun(i, i + 1); true } else null,
                                )
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            // offline záloha: pevně poslední, nejde vypnout ani posunout
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Icon(painterResource(R.drawable.ic_sluzba_zarizeni), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                },
                headlineContent = { Text(t("sluzbaOffline")) },
                supportingContent = { Text(t("sluzbaOfflinePopis")) },
            )
        }
        Text(
            t("sluzbyPamet"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Logo služby v barvách značky; MyMemory nemá logo → obecná ikona překladu v barvě textu. */
@Composable
private fun LogoSluzby(id: String) {
    val logo = when (id) {
        SluzbyPrekladu.DEEPL -> R.drawable.ic_sluzba_deepl
        SluzbyPrekladu.GOOGLE -> R.drawable.ic_sluzba_google
        SluzbyPrekladu.MICROSOFT -> R.drawable.ic_sluzba_microsoft
        else -> null
    }
    if (logo != null) Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(24.dp))
    else Icon(painterResource(R.drawable.ic_sluzba_preklad), contentDescription = null, modifier = Modifier.size(24.dp))
}

@Composable
private fun RadekSluzby(
    t: (String) -> String, id: String, uchyt: Modifier,
    zapnuto: Boolean, maDeeplKlic: Boolean,
    naZapnout: (Boolean) -> Unit, naDeepl: () -> Unit, modifier: Modifier,
) {
    val deepl = id == SluzbyPrekladu.DEEPL
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_drag_handle), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = uchyt,
                )
                Spacer(Modifier.width(12.dp))
                LogoSluzby(id)
            }
        },
        headlineContent = { Text(SluzbyPrekladu.nazev(id)) },
        trailingContent = {
            // DeepL bez klíče: místo přepínače tlačítko na podstránku s klíči
            if (deepl && !maDeeplKlic) OutlinedButton(onClick = naDeepl) { Text(t("sluzbaVlozitKlic")) }
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = zapnuto, onCheckedChange = naZapnout)
                if (deepl) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
        },
        modifier = modifier.clickable { if (deepl) naDeepl() else naZapnout(!zapnuto) },
    )
}
