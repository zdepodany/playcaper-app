package cz.titulky.tv

import android.os.Build
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * Telefon / tablet: obrazovky v Jetpack Compose s Material 3 (dynamické barvy Material You).
 */

/** Stav spojení a PS5; druh řádku konzole: ready / wait / chyba / neutral / off. */
internal data class StavDomu(
    val uroven: UrovenSpojeni,
    val nadpis: String,
    val podtext: String,
    val ps: Pair<String, String>,
)

internal enum class TonTv { NEUTRALNI, PRENOS, CHYBA }

/** Karta „Titulky“ v Nastavení: kam se překlad posílá. */
internal data class TvKarta(val nazev: String, val platforma: String, val stav: String, val ton: TonTv)

@Composable
internal fun PlayCaperTema(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val tmave = isSystemInDarkTheme()
    val schema: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (tmave) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        tmave -> darkColorScheme(primary = Color(0xFFFF535C), onPrimary = Color(0xFF3A0006))
        else -> lightColorScheme(primary = Color(0xFFE5091A))
    }
    MaterialTheme(colorScheme = schema, content = content)
}

/** Společný rám: obsah + spodní navigační lišta M3 (0 = Domů, 1 = Nastavení). */
@Composable
internal fun TelefonKostra(
    vybrana: Int, t: (String) -> String,
    naDomu: () -> Unit, naNastaveni: () -> Unit,
    obsah: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vybrana == 0, onClick = { if (vybrana != 0) naDomu() },
                    icon = { Icon(painterResource(R.drawable.ic_nav_domu), contentDescription = null) },
                    label = { Text(t("navDomu")) })
                NavigationBarItem(
                    selected = vybrana == 1, onClick = { if (vybrana != 1) naNastaveni() },
                    icon = { Icon(painterResource(R.drawable.ic_nav_nastaveni), contentDescription = null) },
                    label = { Text(t("navNastaveni")) })
            }
        },
    ) { vnitrek ->
        Column(
            Modifier.padding(vnitrek).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) { obsah() }
    }
}

@Composable
private fun Nadpis(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
)

// ---------------------------------------------------------------------------------- Domů

@Composable
internal fun DomuObrazovka(
    t: (String) -> String,
    nazevAplikace: String, verze: String,
    stavNactiti: () -> StavDomu,
    naLogo: () -> Unit,
    naPs: () -> Unit, naHrat: () -> Unit,
) {
    // stav spojení se čte každou vteřinu (jako dřív pruh na hlavní obrazovce)
    var stav by remember { mutableStateOf(stavNactiti()) }
    LaunchedEffect(Unit) { while (true) { stav = stavNactiti(); delay(1000) } }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            nazevAplikace, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).clickable(onClick = naLogo),
        )
        Text(verze, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(16.dp))
    StavKarta(stav)

    Nadpis(t("hlKonzole"))
    KonzoleRadek("PlayStation", stav.ps, t, naPs)

    Spacer(Modifier.height(24.dp))
    Button(onClick = naHrat, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Text(t("hratBtn"), style = MaterialTheme.typography.titleLarge)
    }
    Text(
        t("tipPripojeni").trimStart { !it.isLetterOrDigit() },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

@Composable
private fun StavKarta(stav: StavDomu) {
    val s = MaterialTheme.colorScheme
    val (pozadi, text) = when (stav.uroven) {
        UrovenSpojeni.OK -> s.primaryContainer to s.onPrimaryContainer
        UrovenSpojeni.CEKANI -> s.secondaryContainer to s.onSecondaryContainer
        UrovenSpojeni.CHYBA -> s.errorContainer to s.onErrorContainer
        UrovenSpojeni.VYPNUTO -> s.surfaceContainerHigh to s.onSurface
    }
    Card(colors = CardDefaults.cardColors(containerColor = pozadi, contentColor = text), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (stav.uroven) {
                UrovenSpojeni.CEKANI -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = text)
                UrovenSpojeni.OK -> Text("✓", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                UrovenSpojeni.CHYBA -> Text("!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                else -> Text("•", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(stav.nadpis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (stav.podtext.isNotBlank()) Text(stav.podtext, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
        }
        if (stav.uroven == UrovenSpojeni.CEKANI) LinearProgressIndicator(Modifier.fillMaxWidth(), color = text)
    }
}

@Composable
private fun KonzoleRadek(nazev: String, stav: Pair<String, String>, t: (String) -> String, akce: () -> Unit) {
    val (druh, popis) = stav
    val s = MaterialTheme.colorScheme
    val pozadi = when (druh) {
        "ready" -> s.primaryContainer
        "wait" -> s.secondaryContainer
        "chyba" -> s.errorContainer
        else -> s.surfaceContainerHigh
    }
    val sparovano = druh != "off"
    Card(colors = CardDefaults.cardColors(containerColor = pozadi), modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(nazev, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(popis.replaceFirstChar { it.uppercase() }) },
            trailingContent = {
                if (sparovano) TextButton(onClick = akce) { Text(t("konZnovu")) }
                else Button(onClick = akce) { Text(t("pripojit")) }
            },
        )
    }
}

// ---------------------------------------------------------------------------------- Nastavení

@Composable
internal fun NastaveniObrazovka(
    t: (String) -> String,
    jazykNazev: String, deeplPruh: (android.content.Context) -> View?,
    tv: () -> TvKarta, lgStav: (android.content.Context) -> View,
    dialogy: Boolean, naDialogy: (Boolean) -> Unit,
    naJazyk: () -> Unit, naDeepl: () -> Unit, naVystup: () -> Unit,
) {
    Text(t("navNastaveni"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

    // --- jazyk
    Nadpis(t("hlJazyk"))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(jazykNazev) },
            supportingContent = { Text(t("zdrojKratce")) },
            trailingContent = { TextButton(onClick = naJazyk) { Text(t("tvZmenitKratce")) } },
        )
        Column(Modifier.clickable(onClick = naDeepl).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val ctx = LocalContext.current
            val barva = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            val maPruh = remember { deeplPruh(ctx) != null }
            if (maPruh) AndroidView(factory = { deeplPruh(it)!!.also { v -> (v as? android.widget.TextView)?.setTextColor(barva) } })
            else Text(t("deeplPridat"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }

    // --- výstup titulků
    Nadpis(t("hlVystup"))
    var tvKarta by remember { mutableStateOf(tv()) }
    LaunchedEffect(Unit) { while (true) { tvKarta = tv(); delay(1000) } }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(tvKarta.nazev, maxLines = 2) },
            supportingContent = {
                Column {
                    Text(tvKarta.platforma)
                    if (tvKarta.stav.isNotEmpty()) Text(
                        tvKarta.stav,
                        color = when (tvKarta.ton) {
                            TonTv.CHYBA -> MaterialTheme.colorScheme.error
                            TonTv.PRENOS -> MaterialTheme.colorScheme.tertiary
                            TonTv.NEUTRALNI -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    AndroidView(factory = { lgStav(it) })
                }
            },
            trailingContent = { TextButton(onClick = naVystup) { Text(t("tvZmenitKratce")) } },
        )
    }

    // --- co překládat
    Nadpis(t("coPrekladat"))
    var dialogyZap by remember { mutableStateOf(dialogy) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(t("dialogy")) },
            supportingContent = { Text(t("dialogyPopis")) },
            trailingContent = { Switch(checked = dialogyZap, onCheckedChange = { dialogyZap = it; naDialogy(it) }) },
            modifier = Modifier.clickable { dialogyZap = !dialogyZap; naDialogy(dialogyZap) },
        )
    }
    Text(
        t("napovedyHint"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
    )
}

// ---------------------------------------------------------------------------------- Výběr jazyka

@Composable
internal fun JazykObrazovka(jazyky: List<Jazyk>, pocatecni: String, text: (String, String) -> String, naPokracovat: (String) -> Unit) {
    var vybrany by remember { mutableStateOf(pocatecni) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { vnitrek ->
        Column(
            Modifier.padding(vnitrek).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text(vybrany, "vyberJazyk"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                text(vybrany, "vyberJazykPopis"), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            jazyky.forEach { j ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = vybrany == j.kod, role = Role.RadioButton, onClick = { vybrany = j.kod })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vybrany == j.kod, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text("${j.vlajka}   ${j.nazev}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(onClick = { naPokracovat(vybrany) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(text(vybrany, "pokracovat"))
            }
        }
    }
}
