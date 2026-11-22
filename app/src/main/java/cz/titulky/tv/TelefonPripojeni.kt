package cz.titulky.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Telefon: obrazovky „Připojit konzoli“ (PS5) v Compose Material 3. */

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

@Composable
internal fun PripojeniObrazovka(
    t: (String) -> String, st: PripojeniStav, hledani: HledaniStav,
    pin: String, naPin: (String) -> Unit,
    parovani: String, parovaniTon: Ton, parovaniProbiha: Boolean,
    naZpet: () -> Unit, naUcet: () -> Unit, naHledat: () -> Unit, naSparovat: () -> Unit,
) {
    ZpetKostra(t("pripojitKonzoli"), naZpet) {
        // krátký úvod: proč je potřeba druhý účet
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(t("ppUvod"), style = MaterialTheme.typography.bodyMedium)
                Text(t("ppUvodBezUctu"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Krok(1, t("ppK1Nadpis"), t("ppK1"), ikona = R.drawable.ic_playstation) {
            if (st.overuji) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(st.ucetText, color = tonBarva(st.ucetTon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = naUcet, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (st.maUcet) t("zmenitUcet") else t("prihlasit"))
            }
        }

        Krok(2, t("ppK2Nadpis"), t("ppK2")) {
            if (hledani.probiha) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(hledani.text, color = tonBarva(hledani.ton), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = naHledat, modifier = Modifier.padding(top = 12.dp)) { Text(t("hledatZnovu")) }
        }

        Krok(3, t("ppK3Nadpis"), t("ppK3")) {
            OutlinedTextField(
                value = pin, onValueChange = { naPin(it.filter(Char::isDigit).take(8)) },
                enabled = st.lzeParovat, singleLine = true, placeholder = { Text("12345678") },
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (parovaniProbiha) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            if (parovani.isNotEmpty()) Text(parovani, color = tonBarva(parovaniTon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = naSparovat, enabled = st.lzeParovat, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(t("sparovat")) }
        }
        Spacer(Modifier.height(8.dp))
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
