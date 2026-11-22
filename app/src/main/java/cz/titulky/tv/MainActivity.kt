package cz.titulky.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * PlayCaper – hlavní obrazovka telefonu / tabletu (Jetpack Compose, Material 3).
 * Úvod s načítáním → výběr jazyka (jen poprvé) → Domů / Nastavení.
 */
class MainActivity : ComponentActivity() {

    private val nast by lazy { Nastaveni(this) }
    private val udaje by lazy { RemotePlayUdaje(this) }
    private val ui = Handler(Looper.getMainLooper())
    private var obrazovka = ""
    private val jazyk get() = nast.jazykNeboCs
    private fun t(klic: String) = Texty.t(jazyk, klic)

    private val CERVENA = Color.parseColor("#E5091A")

    companion object {
        /** Jazyky, které jdou zatím vybrat (beta pro českou a slovenskou komunitu). */
        val AKTIVNI_JAZYKY = setOf("cs", "sk")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Pady.hlidej(this)
        prevezmiAccountId(intent)
        ukazUvod()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        prevezmiAccountId(intent)
    }

    /** Záložní cesta: skript psn-account-id.ps1 v počítači pošle Account ID sem. */
    private fun prevezmiAccountId(intent: Intent?) {
        val id = intent?.getStringExtra("psn_account_id") ?: return
        udaje.accountId = id
    }

    override fun onResume() {
        super.onResume()
        // když běží spojení s konzolí, nenechat displej zhasnout ani tady
        // (zhasnutý displej = za minutu konec spojení, Wi-Fi usne)
        if (RemotePlayService.aktivni)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (obrazovka == "hlavni") ukazHlavni() else if (obrazovka == "nastaveni") ukazNastaveni()
    }

    override fun onDestroy() {
        zastavServer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (obrazovka) {
            "psn", "psnqr" -> ukazPripojeni()
            "nastaveni" -> ukazHlavni()
            "jazyk" -> if (nast.jazyk != null) { obnovAktualni(jazykZNastaveni); jazykZNastaveni = false } else super.onBackPressed()
            "pripojeni" -> if (nast.jazyk != null) ukazHlavni() else super.onBackPressed()
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ úvod

    private fun ukazUvod() {
        obrazovka = "uvod"
        val bar = View(this).apply { setBackgroundColor(CERVENA) }
        val drazka = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#26262E")); cornerRadius = 999f * resources.displayMetrics.density }
            addView(bar, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                textSize = 40f * SKALA
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            addView(drazka, LinearLayout.LayoutParams(dp(340), dp(10)).apply { topMargin = dp(36) })
        }
        setContentView(koren)
        // animace načítání ~1,6 s
        val start = System.currentTimeMillis()
        val krok = object : Runnable {
            override fun run() {
                if (obrazovka != "uvod") return
                val podil = ((System.currentTimeMillis() - start) / 1600f).coerceAtMost(1f)
                bar.layoutParams = bar.layoutParams.apply { width = (drazka.width * podil).toInt() }
                bar.requestLayout()
                if (podil < 1f) ui.postDelayed(this, 30)
                else pokracuj()
            }
        }
        ui.post(krok)
    }

    private fun pokracuj() { if (nast.jazyk == null) ukazJazyk() else ukazHlavni() }

    private val verze by lazy { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }

    // ------------------------------------------------------------------ výběr jazyka

    private fun ukazJazyk() {
        obrazovka = "jazyk"
        setContent {
            PlayCaperTema {
                JazykObrazovka(
                    jazyky = Texty.JAZYKY.filter { it.kod in AKTIVNI_JAZYKY },
                    pocatecni = nast.jazyk?.takeIf { it in AKTIVNI_JAZYKY } ?: "cs",
                    text = { kod, klic -> Texty.t(kod, klic) },
                    naPokracovat = { kod ->
                        nast.jazyk = kod
                        obnovAktualni(jazykZNastaveni)
                        jazykZNastaveni = false
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------------ Domů a Nastavení

    /** Odkud se otevřel výběr jazyka (z Nastavení se po něm vracíme tam). */
    private var jazykZNastaveni = false

    /** Překreslí hlavní obrazovku, nebo Nastavení. */
    private fun obnovAktualni(nastaveni: Boolean) { if (nastaveni) ukazNastaveni() else ukazHlavni() }

    /** Domů: stav, konzole, tlačítko Hrát. */
    private fun ukazHlavni() {
        zastavServer()
        obrazovka = "hlavni"
        setContent {
            PlayCaperTema {
                TelefonKostra(0, ::t, naDomu = {}, naNastaveni = { ukazNastaveni() }) {
                    DomuObrazovka(
                        t = ::t, nazevAplikace = getString(R.string.app_name), verze = "BETA $verze",
                        stavNactiti = { spocitejStav() },
                        naLogo = { Zaznam.ukaz(this) },
                        naPs = { ukazPripojeni() },
                        naHrat = { if (udaje.sparovano) startActivity(Intent(this, TelefonActivity::class.java)) else ukazPripojeni() },
                    )
                }
            }
        }
    }

    /** Stránka Nastavení: jazyk a klíče DeepL, výstup titulků, co překládat. */
    private fun ukazNastaveni() {
        zastavServer()
        obrazovka = "nastaveni"
        nast.napovedy = false // menu a nápovědy se nepřekládají samy – žlutý rámeček při hraní
        val j = Texty.JAZYKY.firstOrNull { it.kod == jazyk } ?: Texty.JAZYKY[0]
        setContent {
            PlayCaperTema {
                TelefonKostra(1, ::t, naDomu = { ukazHlavni() }, naNastaveni = {}) {
                    NastaveniObrazovka(
                        t = ::t, jazykNazev = "${j.vlajka}  ${j.nazev}",
                        deeplPruh = { DeeplLimit.pruh(it, 13f, Color.GRAY) },
                        tv = { tvKarta() }, lgStav = { lgStavView() },
                        dialogy = nast.dialogy, naDialogy = { nast.dialogy = it },
                        naJazyk = { jazykZNastaveni = true; ukazJazyk() },
                        naDeepl = { zadejDeeplKlic() }, naVystup = { vyberVystupTv() },
                    )
                }
            }
        }
    }

    /** Stav spojení a PS5 pro obrazovku Domů. */
    private fun spocitejStav(): StavDomu {
        val sluzba = RemotePlayService.aktivni
        val spojeni = if (!udaje.sparovano) PrehledSpojeni("stavNesparovano", UrovenSpojeni.VYPNUTO)
            else RemotePlayService.prehledSpojeni()
        // hraje se v obrazovce Hrát – tady spárovaná konzole = připravená ke hraní
        val uroven = if (udaje.sparovano && !sluzba) UrovenSpojeni.OK else spojeni.uroven
        val podrobnosti = Texty.spojeni(nast.jazykNeboCs, spojeni)
        val (nadpis, podtext) = when (uroven) {
            UrovenSpojeni.OK -> t("banOkPs") to t("banOkText")
            UrovenSpojeni.CEKANI -> t("banCekaPs") to podrobnosti
            UrovenSpojeni.CHYBA -> t("banChybaPs") to podrobnosti
            else -> t(spojeni.klic) to ""
        }
        val ps = when {
            !udaje.sparovano -> "off" to t("konNepripojeno")
            uroven == UrovenSpojeni.OK -> "ready" to t("konPripraveno")
            uroven == UrovenSpojeni.CEKANI -> "wait" to t("konPripravuji")
            uroven == UrovenSpojeni.CHYBA -> "chyba" to t("konChyba")
            else -> "neutral" to t("konSparovano")
        }
        return StavDomu(uroven = uroven, nadpis = nadpis, podtext = podtext, ps = ps)
    }

    /** Karta „Titulky“: kam telefon posílá překlad (televize / jen tento telefon). */
    private fun tvKarta(): TvKarta {
        val profily = TvProfily(this)
        val posila = Propojeni.Vysilac.cilIp != null
        val profil = (if (posila) TvProfily.aktivniId else profily.posledniId)?.let { profily.najdi(it) }
        val lokalni = !posila && profily.pouzeTelefon
        val nazev = when {
            posila -> Propojeni.Vysilac.cilJmeno.ifBlank { profil?.jmeno ?: t("tvZadnaVybrana") }
            lokalni -> t("tvJenZarizeni")
            else -> profil?.jmeno ?: t("tvZadnaVybrana")
        }.let(::bezIdTv)
        val platforma = if (lokalni || profil == null) t("tvNastaveniPopis") else when (profil.platforma) {
            TvPlatforma.WEBOS -> "LG webOS"
            TvPlatforma.SAMSUNG -> "Samsung (Tizen)"
            else -> t("tvPlatformaNeznama")
        }
        val stav = when {
            posila -> Texty.prenos(nast.jazykNeboCs, Propojeni.Vysilac.stavPrenosu)
            lokalni -> t("tvJenZde")
            profil != null -> "" // vybraná TV bez přenosu – obecná věta navíc nic neříká
            else -> t("tvVyberVystup")
        }
        val ton = when {
            !posila -> TonTv.NEUTRALNI
            Propojeni.Vysilac.stavPrenosu.chyba != null -> TonTv.CHYBA
            else -> TonTv.PRENOS
        }
        return TvKarta(nazev, platforma, stav, ton)
    }

    /** „LG PlayCaption · 41CCCAF73407“ → „LG PlayCaption“ (interní ID přijímače uživateli nic neříká). */
    private fun bezIdTv(jmeno: String) = jmeno.replace(Regex("\\s*·\\s*[0-9A-Fa-f]{8,}$"), "")

    /** Stav instalace přijímače na LG (Developer Mode), vykreslený starou komponentou. */
    private fun lgStavView(): View = LgDevModeView(this) {
        val p = TvProfily(this)
        if (p.pouzeTelefon && Propojeni.Vysilac.cilIp == null) null
        else (if (Propojeni.Vysilac.cilIp != null) TvProfily.aktivniId else p.posledniId)?.let { p.najdi(it) }
    }.apply { textSize = 13f * SKALA }

    /**
     * Nepovinné vlastní klíče DeepL (až [Nastaveni.MAX_DEEPL_KLICU], každý člověk svůj).
     * Používají se v pořadí; překladač je čte při každé větě, změna platí hned.
     */
    private fun zadejDeeplKlic() {
        val ulozene = nast.deeplKlice
        val obsah = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0) }
        val pole = mutableListOf<EditText>()
        val stavy = mutableListOf<TextView>()
        fun plnaSirka() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        for (i in 0 until Nastaveni.MAX_DEEPL_KLICU) {
            val p = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = true
                textSize = 16f
                hint = if (i == 0) "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:fx"
                    else t("deeplKlicDalsi").replace("{n}", (i + 1).toString())
                setText(ulozene.getOrNull(i) ?: "")
            }
            // stav limitu pod každým uloženým klíčem
            val s = TextView(this).apply {
                textSize = 13f
                text = t("deeplLimitNacitam")
                visibility = if (ulozene.getOrNull(i) != null) View.VISIBLE else View.GONE
            }
            pole += p; stavy += s
            obsah.addView(p, plnaSirka().apply { if (i > 0) topMargin = dp(8) })
            obsah.addView(s, plnaSirka().apply { leftMargin = dp(4) })
        }
        if (ulozene.isNotEmpty()) Thread {
            val nactene = ulozene.map { Prekladac.nactiDeeplVyuziti(it) }
            runOnUiThread {
                nactene.forEachIndexed { i, stav -> stavy[i].text = DeeplLimit.popisKlice(stav, jazyk) }
                DeeplLimit.uloz(ulozene, nactene)
            }
        }.start()
        android.app.AlertDialog.Builder(this)
            .setTitle(t("deeplNadpis"))
            .setMessage(t("deeplText"))
            .setView(android.widget.ScrollView(this).apply { addView(obsah) })
            .setPositiveButton(t("deeplUlozit")) { _, _ ->
                nast.deeplKlice = pole.map { it.text.toString() }
                obnovAktualni(obrazovka == "nastaveni")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun vyberVystupTv() {
        TvVyber(this, { tv, platforma ->
            val profily = TvProfily(this)
            profily.zapamatuj(tv.id, tv.jmeno, tv.ip, platforma)
            profily.zapamatujVyber(tv.id)
            if (Propojeni.Vysilac.cilIp != null) android.widget.Toast.makeText(this, t("tvUlozena").replace("{tv}", tv.jmeno), android.widget.Toast.LENGTH_LONG).show()
        }, {
            TvProfily(this).pouzeTelefon = true
        }, { obnovAktualni(obrazovka == "nastaveni") }).ukaz()
    }

    // ------------------------------------------------------------------ Připojit konzoli (PS5)

    private var nalezenaIp: String? = null
    private var psnRevize by androidx.compose.runtime.mutableIntStateOf(0)
    private var pinText by androidx.compose.runtime.mutableStateOf("")
    private var hledaniText by androidx.compose.runtime.mutableStateOf("")
    private var hledaniTon by androidx.compose.runtime.mutableStateOf(Ton.BEZNY)
    private var hledaniProbiha by androidx.compose.runtime.mutableStateOf(false)
    private var parovaniText by androidx.compose.runtime.mutableStateOf("")
    private var parovaniTon by androidx.compose.runtime.mutableStateOf(Ton.BEZNY)
    private var parovaniProbiha by androidx.compose.runtime.mutableStateOf(false)
    private var psnQrText by androidx.compose.runtime.mutableStateOf("")

    private fun pripojeniStav(): PripojeniStav {
        val maUcet = udaje.accountIdBajty() != null
        val prihlaseni = psnPokus?.vysledek
        val overuji = prihlaseni?.stav == PsnPokus.Stav.OVERUJE
        val chyba = prihlaseni?.takeIf { it.stav == PsnPokus.Stav.CHYBA }?.popisChyby(jazyk)
        return PripojeniStav(
            ucetText = if (overuji) t("psnOveruji") else chyba ?: if (maUcet) t("ppPrihlasen") else t("ppNeprihlasen"),
            ucetTon = if (chyba != null) Ton.CHYBA else if (maUcet && !overuji) Ton.OK else Ton.BEZNY,
            overuji = overuji, maUcet = maUcet,
            lzeParovat = psnPokus?.lzeParovat(maUcet) ?: maUcet,
        )
    }

    private fun hledejPs() {
        hledaniProbiha = true; hledaniText = t("hledam"); hledaniTon = Ton.BEZNY
        Thread {
            val seznam = runCatching { HledacPS.hledej() }.getOrDefault(emptyList())
            runOnUiThread {
                val ps = seznam.firstOrNull { it.typ == "PS5" } ?: seznam.firstOrNull()
                nalezenaIp = ps?.ip
                hledaniProbiha = false
                hledaniText = if (ps != null) "✔ ${t("nalezena")}: ${ps.jmeno} (${ps.ip})" else "✘ ${t("nenalezena")}"
                hledaniTon = if (ps != null) Ton.OK else Ton.CHYBA
            }
        }.start()
    }

    private fun sparujPs() {
        val ip = nalezenaIp ?: udaje.ipKonzole
        val cislo = pinText.filter { it.isDigit() }
        fun chyba(zprava: String) { parovaniText = zprava; parovaniTon = Ton.CHYBA }
        when {
            udaje.accountIdBajty() == null || psnPokus?.lzeParovat(true) == false -> chyba(t("nejdrivUcet"))
            ip == null -> chyba(t("nenalezena"))
            cislo.length != 8 -> chyba(t("pinChyba"))
            else -> {
                parovaniText = t("paruji"); parovaniTon = Ton.BEZNY; parovaniProbiha = true
                Parovani.sparuj(this, ip, cislo.toInt(), log = {}, hotovo = { ok, zprava ->
                    runOnUiThread {
                        parovaniProbiha = false
                        if (ok) {
                            parovaniText = "✔ ${t("sparovano")}"; parovaniTon = Ton.OK
                            ui.postDelayed({ ukazHlavni() }, 1200)
                        } else chyba("✘ $zprava")
                    }
                })
            }
        }
    }

    private fun ukazPripojeni(ukoncitPrihlaseni: Boolean = true) {
        if (ukoncitPrihlaseni) zastavServer()
        obrazovka = "pripojeni"
        if (ukoncitPrihlaseni) { pinText = ""; parovaniText = ""; parovaniTon = Ton.BEZNY; parovaniProbiha = false; hledaniText = "" }
        setContent {
            PlayCaperTema {
                val revize = psnRevize // změna přihlášení → přepočítat stav
                PripojeniObrazovka(
                    t = ::t, st = remember(revize) { pripojeniStav() },
                    hledani = HledaniStav(hledaniText, hledaniTon, hledaniProbiha),
                    pin = pinText, naPin = { pinText = it },
                    parovani = parovaniText, parovaniTon = parovaniTon, parovaniProbiha = parovaniProbiha,
                    naZpet = { ukazHlavni() },
                    naUcet = { ukazPsnTelefonem() }, naHledat = { hledejPs() }, naSparovat = { sparujPs() },
                )
            }
        }
        if (ukoncitPrihlaseni || hledaniText.isEmpty()) hledejPs()
    }

    // ------------------------------------------------------------------ přihlášení PSN (prohlížeč)

    @SuppressLint("SetJavaScriptEnabled")
    private fun ukazPsn() {
        val pokus = pripravPsnPokus()
        obrazovka = "psn"
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.setAcceptCookie(true)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            // Sony někdy odmítá vestavěný prohlížeč ("Can't connect to the server") → tvářit se jako běžný Chrome
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
            isFocusable = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    zachytKod(request.url.toString(), pokus)

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    zachytKod(url, pokus)
                }
            }
        }
        cookies.setAcceptThirdPartyCookies(web, true)
        setContentView(web)
        web.requestFocus()
        web.loadUrl(PsnPrihlaseni.ADRESA_PRIHLASENI)
    }

    /** Po přihlášení Sony přesměruje na adresu s kódem → vyměnit za Account ID. */
    private fun zachytKod(adresa: String, pokus: PsnPokus): Boolean {
        val kod = PsnPrihlaseni.kodZAdresy(adresa) ?: return false
        pokus.prihlas(kod)
        return true
    }

    private fun pripravPsnPokus(): PsnPokus {
        zastavServer()
        lateinit var pokus: PsnPokus
        pokus = PsnPokus(PsnPrihlaseni::accountId, udaje::ulozAccountId) {
            runOnUiThread {
                if (psnPokus !== pokus || isFinishing || isDestroyed) return@runOnUiThread
                if (pokus.stav == PsnPokus.Stav.OVERUJE && obrazovka != "pripojeni") psnQrText = t("psnOveruji")
                else if (obrazovka == "pripojeni") psnRevize++ // jen přepočítat, ať se nesmaže PIN
                else if (obrazovka in listOf("psn", "psnqr")) ukazPripojeni(ukoncitPrihlaseni = false)
            }
        }
        psnPokus = pokus
        return pokus
    }

    // ------------------------------------------------------------------ přihlášení přes místní stránku

    private var server: PrihlaseniTelefonem? = null
    private var psnPokus: PsnPokus? = null

    private fun zastavServer() {
        psnPokus?.zrus()
        psnPokus = null
        server?.zastav()
        server = null
    }

    private fun ukazPsnTelefonem() {
        val pokus = pripravPsnPokus()
        obrazovka = "psnqr"
        val cesky = nast.jazykNeboCs in listOf("cs", "sk")
        val stranka = PrihlaseniTelefonem(cesky, pokus)
        server = stranka.also { it.spust() }
        val adresa = "http://127.0.0.1:${PrihlaseniTelefonem.PORT}${stranka.cesta}"
        psnQrText = t("qrCekam")
        setContent {
            PlayCaperTema {
                PrihlaseniQrObrazovka(
                    t = ::t, stav = psnQrText, naZpet = { ukazPripojeni() },
                    naOtevrit = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adresa))) } },
                    naProhlizec = { ukazPsn() },
                )
            }
        }
    }

    // ------------------------------------------------------------------ pomocníci

    /** Zmenšení písma starých komponent (úvod, LG stav). */
    private val SKALA = 0.72f
    private fun dp(x: Int) = (x * SKALA * resources.displayMetrics.density).toInt()
}
