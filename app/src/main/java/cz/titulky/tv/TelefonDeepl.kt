package cz.titulky.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Stav jednoho uloženého klíče pod polem: text a jestli jde o problém (červeně). */
internal data class StavKlice(val text: String, val chyba: Boolean)

/**
 * Podstránka Nastavení → DeepL: krátký popis a až [Nastaveni.MAX_DEEPL_KLICU] klíčů.
 * [stavy] = stav limitu podle klíče (chybí = zatím se načítá / klíč není uložený).
 */
@Composable
internal fun DeeplObrazovka(
    t: (String) -> String,
    ulozene: List<String>, stavy: Map<String, StavKlice>,
    naZpet: () -> Unit, naZiskat: () -> Unit, naUlozit: (List<String>) -> Unit,
) {
    ZpetKostra(t("deeplRadek"), naZpet) {
        Column(Modifier.imePadding()) {
            // --- jak to funguje
            Text(
                t("deeplHlJak").uppercase(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("deeplJak"), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = naZiskat, modifier = Modifier.padding(top = 4.dp)) { Text(t("deeplZiskat")) }
                }
            }

            // --- klíče
            Text(
                t("deeplHlKlice").uppercase(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            val pole = remember(ulozene) {
                mutableStateListOf<String>().apply {
                    repeat(Nastaveni.MAX_DEEPL_KLICU) { add(ulozene.getOrNull(it) ?: "") }
                }
            }
            // vždy jedno prázdné pole navíc pro další klíč (do maxima)
            val viditelnych = (pole.indexOfLast { it.isNotBlank() } + 2).coerceIn(1, Nastaveni.MAX_DEEPL_KLICU)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until viditelnych) {
                        val stav = stavy[pole[i].trim()]?.takeIf { pole[i].trim() in ulozene }
                        val nacita = stav == null && pole[i].trim().let { it.isNotEmpty() && it in ulozene }
                        OutlinedTextField(
                            value = pole[i], onValueChange = { pole[i] = it },
                            label = { Text(t("deeplKlicN").replace("{n}", (i + 1).toString())) },
                            placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:fx") },
                            singleLine = true,
                            isError = stav?.chyba == true,
                            supportingText = when {
                                stav != null -> { { Text(stav.text) } }
                                nacita -> { { Text(t("deeplLimitNacitam")) } }
                                else -> null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        t("deeplKlicePopis"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { naUlozit(pole.toList()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("deeplUlozit"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
