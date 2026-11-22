package cz.titulky.tv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Telefon: průvodce „Připojit ke konzoli“ (PS5) ve spodním panelu a přihlášení přes prohlížeč, Compose Material 3. */

internal enum class Ton { BEZNY, OK, CHYBA }

internal data class PripojeniStav(
    val ucetText: String, val ucetTon: Ton, val overuji: Boolean, val maUcet: Boolean, val lzeParovat: Boolean,
)

/** Hledání PS5 v síti (text už obsahuje ✔ / ✘). */
internal data class HledaniStav(val text: String, val ton: Ton, val probiha: Boolean)

@Composable
private fun tonBarva(ton: Ton): Color = when (ton) {
    Ton.OK -> MaterialTheme.colorScheme.primary
    Ton.CHYBA -> MaterialTheme.colorScheme.error
    Ton.BEZNY -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZpetKostra(nadpis: String, naZpet: () -> Unit, obsah: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(nadpis) },
                navigationIcon = {
                    IconButton(onClick = naZpet) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) }
                },
            )
        },
    ) { vnitrek ->
        Column(Modifier.padding(vnitrek).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) { obsah() }
    }
}

@Composable
private fun Krok(cislo: Int, nadpis: String, popis: String, ikona: Int? = null, obsah: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$cislo", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Text(nadpis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f))
                if (ikona != null) Icon(painterResource(ikona), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(24.dp))
            }
            Text(popis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            obsah()
        }
    }
}

/** Hotový krok průvodce: jen řádek s ✔, shrnutím a akcí pro změnu. */
@Composable
private fun HotovyKrok(nadpis: String, popis: String, akce: String, naAkci: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("✔", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(nadpis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (popis.isNotEmpty()) Text(popis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = naAkci) { Text(akce) }
    }
}

/**
 * Průvodce připojením PS5 ve spodním panelu: účet → hledání PS5 → PIN.
 * Další krok se ukáže, až je předchozí hotový; hotové kroky zůstanou jako řádek s ✔.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PripojeniSheet(
    t: (String) -> String, st: PripojeniStav, hledani: HledaniStav,
    pin: String, naPin: (String) -> Unit,
    parovani: String, parovaniTon: Ton, parovaniProbiha: Boolean,
    naZavrit: () -> Unit, naUcet: () -> Unit, naHledat: () -> Unit, naSparovat: () -> Unit,
) {
    val psNalezena = hledani.ton == Ton.OK && !hledani.probiha
    val krok = when {
        !st.lzeParovat -> 1
        !psNalezena -> 2
        else -> 3
    }
    ModalBottomSheet(onDismissRequest = naZavrit, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).animateContentSize(),
        ) {
            Text(t("pripojitKonzoli"), style = MaterialTheme.typography.headlineSmall)
            Text(t("ppKrokZ").replace("{n}", "$krok"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) {
                for (i in 1..3) Box(
                    Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (i <= krok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            // hotové kroky
            if (krok > 1) HotovyKrok(t("ppK1Nadpis"), st.ucetText.trimStart('✔', ' '), t("zmenitUcet"), naUcet)
            if (krok > 2) HotovyKrok(t("ppK2Nadpis"), hledani.text.trimStart('✔', ' '), t("hledatZnovu"), naHledat)
            if (krok > 1) Spacer(Modifier.height(4.dp))

            // aktuální krok
            when (krok) {
                1 -> {
                    // krátký úvod: proč je potřeba druhý účet
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t("ppUvod"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Krok(1, t("ppK1Nadpis"), t("ppK1"), ikona = R.drawable.ic_playstation) {
                        if (st.overuji) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                        Text(st.ucetText, color = tonBarva(st.ucetTon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = naUcet, enabled = !st.overuji, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(if (st.maUcet) t("zmenitUcet") else t("prihlasit"))
                        }
                    }
                }
                2 -> Krok(2, t("ppK2Nadpis"), t("ppK2")) {
                    if (hledani.probiha) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    if (hledani.text.isNotEmpty()) Text(hledani.text, color = tonBarva(hledani.ton), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = naHledat, enabled = !hledani.probiha, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(t("hledatZnovu")) }
                }
                else -> Krok(3, t("ppK3Nadpis"), t("ppK3")) {
                    OutlinedTextField(
                        value = pin, onValueChange = { naPin(it.filter(Char::isDigit).take(8)) },
                        enabled = !parovaniProbiha, singleLine = true, placeholder = { Text("12345678") },
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    if (parovaniProbiha) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    if (parovani.isNotEmpty()) Text(parovani, color = tonBarva(parovaniTon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = naSparovat, enabled = !parovaniProbiha && pin.length == 8, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(t("sparovat")) }
                }
            }
        }
    }
}

/** Přihlášení k PS účtu přes webovou stránku (otevře se v prohlížeči telefonu). */
@Composable
internal fun PrihlaseniQrObrazovka(
    t: (String) -> String, stav: String, naZpet: () -> Unit, naOtevrit: () -> Unit, naProhlizec: () -> Unit,
) {
    ZpetKostra(t("qrNadpis"), naZpet) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("qrPopisTelefon"), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = naOtevrit, modifier = Modifier.fillMaxWidth()) { Text(t("qrOtevrit")) }
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stav, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = naProhlizec, modifier = Modifier.padding(top = 8.dp)) { Text(t("qrVProhlizeci")) }
    }
}
