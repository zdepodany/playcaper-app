package cz.titulky.tv

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Přihlášení k PSN přes běžný prohlížeč telefonu.
 * Sony blokuje přihlášení v prohlížeči uvnitř aplikace ("Can't connect to the server"),
 * proto aplikace spustí malou webovou stránku jen pro tento telefon (127.0.0.1). Člověk se
 * přihlásí v normálním prohlížeči a vloží adresu, kde skončil → kód přijde do aplikace.
 */
internal class PrihlaseniTelefonem(private val cesky: Boolean, private val pokus: PsnPokus) {

    private val relace = java.util.UUID.randomUUID().toString()
    val cesta = "/?p=$relace"
    @Volatile private var bezi = true
    @Volatile private var server: ServerSocket? = null

    fun spust() {
        Thread {
            runCatching {
                ServerSocket().use { s ->
                    s.reuseAddress = true
                    // jen tento telefon – stránka není vidět z domácí sítě
                    s.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT))
                    server = s
                    while (bezi) {
                        val c = s.accept()
                        Thread { obsluz(c) }.apply { isDaemon = true }.start()
                    }
                }
            }.onFailure { if (bezi) pokus.chybaStranky() }
        }.apply { isDaemon = true }.start()
    }

    fun zastav() {
        bezi = false
        runCatching { server?.close() }
    }

    private fun obsluz(c: Socket) {
        runCatching {
            c.use {
                c.soTimeout = 15000
                val r = c.getInputStream().bufferedReader()
                val prvni = r.readLine() ?: return
                val cesta = prvni.split(" ").getOrNull(1) ?: "/"
                while (true) { val h = r.readLine() ?: break; if (h.isEmpty()) break }

                // Obnovená karta nebo formulář minulého pokusu nesmí znovu poslat starý Sony kód.
                val aktualni = Regex("[?&]p=([^&]*)").find(cesta)?.groupValues?.get(1) == relace
                val html = if (cesta.substringBefore('?') == "/kod" && aktualni) {
                    val kod = runCatching {
                        val vlozeno = Regex("[?&]u=([^&]*)").find(cesta)?.groupValues?.get(1)
                            ?.let { URLDecoder.decode(it, "UTF-8") }?.trim() ?: ""
                        PsnPrihlaseni.kodZAdresy(vlozeno)
                            ?: Regex("code=([^&\\s]+)").find(vlozeno)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }
                    }.getOrNull()
                    if (!kod.isNullOrBlank()) {
                        pokus.prihlas(kod)
                        c.getOutputStream().apply {
                            write("HTTP/1.1 303 See Other\r\nLocation: /stav?p=$relace\r\nCache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            flush()
                        }
                        return
                    } else stranka(chyba = true)
                } else stranka()

                val telo = html.toByteArray()
                val o = c.getOutputStream()
                o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${telo.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
                o.write(telo)
                o.flush()
            }
        }
    }

    internal fun stranka(chyba: Boolean = false): String {
        val T = if (cesky) CS else EN
        val vysledek = pokus.vysledek
        val stav = vysledek.stav
        val obsah = when (stav) {
            PsnPokus.Stav.HOTOVO -> "<div class=ok>${T[0]}</div>"
            PsnPokus.Stav.OVERUJE -> "<div class=pending>${if (cesky) "Ověřuji přihlášení u PlayStation… Počkej na výsledek." else "Verifying your PlayStation sign-in… Please wait for the result."}</div>"
            else -> (if (stav == PsnPokus.Stav.CHYBA) "<div class=err>${if (cesky) vysledek.popisChyby("cs") else EN_CHYBY[vysledek.chyba] + (vysledek.http?.let { " (HTTP $it)" } ?: "")}</div>"
                else if (chyba) "<div class=err>${T[1]}</div>" else "") + """
                <h2>1.</h2>
                <p>${T[2]}</p>
                <a class=btn href="${PsnPrihlaseni.ADRESA_PRIHLASENI}">${T[3]}</a>
                <h2>2.</h2>
                <p>${T[4]}</p>
                <div class=dva>
                  <div class=tel><div class=nad>iPhone</div>
                    <div class=bila>${T[7]}</div>
                    <div class=sipka>⬇ ${T[6]}</div>
                    <div class=lista><div class=adr>remoteplay.dl…</div></div>
                  </div>
                  <div class=tel><div class=nad>Android</div>
                    <div class=lista><div class=adr>remoteplay.dl…</div></div>
                    <div class=sipka>⬆ ${T[6]}</div>
                    <div class=bila>${T[7]}</div>
                  </div>
                </div>
                <p>${T[8]}</p>
                <h2>3.</h2>
                <p>${T[9]}</p>
                <form id=f action="/kod" method="get">
                  <input type="hidden" name="p" value="$relace">
                  <textarea id=u name="u" rows="4" placeholder="${T[10]}"></textarea>
                  <button class=btn type="submit">${T[5]}</button>
                </form>
                <script>
                  // jakmile se vloží adresa s kódem, rovnou ji odeslat
                  var u=document.getElementById('u');
                  u.addEventListener('input',function(){ if(u.value.indexOf('code=')>=0) document.getElementById('f').submit(); });
                </script>"""
        }
        return """<!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            ${if (stav == PsnPokus.Stav.OVERUJE) "<meta http-equiv=refresh content=\"1;url=/stav?p=$relace\">" else ""}
            <title>PlayCaption</title><style>
            body{background:#111;color:#eee;font-family:sans-serif;margin:0;padding:20px;max-width:560px}
            h1{color:#e5091a;margin:0 0 6px}h2{margin:22px 0 4px;color:#e5091a}
            p{line-height:1.45}
            .btn{display:block;width:100%;box-sizing:border-box;background:#e5091a;color:#fff;border:0;border-radius:14px;
                 padding:16px;font-size:18px;font-weight:bold;text-align:center;text-decoration:none;margin-top:8px}
            textarea{width:100%;box-sizing:border-box;font-size:15px;border-radius:10px;padding:10px;background:#222;color:#fff;border:1px solid #555}
            .ok{background:#14532d;padding:20px;border-radius:14px;font-size:20px;margin-top:20px}
            .pending{background:#25304d;padding:20px;border-radius:14px;font-size:20px;margin-top:20px}
            .err{background:#5b1111;padding:14px;border-radius:12px;margin-top:12px}
            .dva{display:flex;gap:12px;margin:10px 0}
            .tel{flex:1;border:3px solid #555;border-radius:22px;overflow:hidden;background:#fff}
            .nad{background:#333;color:#fff;text-align:center;font-weight:bold;padding:4px;font-size:13px}
            .lista{background:#e9e9ee;padding:10px}
            .adr{background:#fff;border:3px solid #e5091a;border-radius:10px;padding:8px;color:#333;font-size:13px;text-align:center}
            .sipka{color:#e5091a;font-weight:bold;text-align:center;padding:6px;background:#fff}
            .bila{height:70px;display:flex;align-items:center;justify-content:center;color:#999;font-size:14px;background:#fff}
            </style></head><body><h1>PlayCaption</h1>$obsah</body></html>"""
    }

    companion object {
        const val PORT = 8765
        private val EN_CHYBY = mapOf(
            "psnKod" to "Sony rejected the sign-in code. Sign in again and paste the new address.",
            "psnSony" to "Sony rejected account verification. Try signing in again later.",
            "psnLimit" to "Sony received too many requests. Wait a while before signing in again.",
            "psnCas" to "Sony did not respond in time. Check your internet connection and sign in again.",
            "psnSit" to "Could not connect to Sony. Check the internet connection on the device running PlayCaption.",
            "psnOdpoved" to "Sony returned an invalid account response. The account was not saved; sign in again.",
            "psnUlozeni" to "Could not save the account on the device running PlayCaption. Try again.",
            "psnStranka" to "Could not open the local sign-in page. Close other PlayCaption instances and open sign-in again."
        )

        private val CS = listOf(
            "✔ Hotovo! Účet je uložený. Zavři prohlížeč a vrať se do aplikace PlayCaption.",
            "✘ V adrese jsem nenašel kód. Zkopíruj prosím celou adresu z řádku s adresou (musí obsahovat „code=“).",
            "Přihlas se k PlayStation <b>DRUHÝM účtem z PS5 – ne tím hlavním, na kterém hraješ</b>.",
            "Přihlásit se k PlayStation",
            "Po přihlášení se může ukázat <b>BÍLÁ stránka</b> nebo chyba. Pokud adresa obsahuje <b>code=</b>, pokračuj níže. Účet ještě není uložený. Stránka může vypadat takhle:",
            "Ověřit a uložit účet",
            "1. klepni sem",
            "bílá stránka",
            "Klepni na <b>řádek s adresou</b> a dej <b>Kopírovat</b>.<br>" +
                "• <b>iPhone (Safari):</b> adresa je <b>DOLE</b>. Klepni na ni → ikona <b>Kopírovat</b> (dva čtverečky).<br>" +
                "• <b>Android (Chrome):</b> adresa je <b>NAHOŘE</b>. Klepni na ni → ikona <b>Kopírovat</b> (dva čtverečky).",
            "<b>Vrať se do aplikace PlayCaption a klepni znovu na „Otevřít v prohlížeči“</b> – otevře se zase tahle stránka. Do pole dole podrž prst → <b>Vložit</b>. Odešle se to samo.",
            "Sem podrž prst a dej Vložit"
        )
        private val EN = listOf(
            "✔ Done! The account is saved. Close the browser and go back to the PlayCaption app.",
            "✘ No code found in the address. Please copy the whole address from the bar at the top (it must contain “code=”).",
            "Sign in with your <b>SECOND PS5 account – not the main one you play on</b>.",
            "Sign in to PlayStation",
            "After signing in you may see a <b>WHITE page</b> or an error. If the address contains <b>code=</b>, continue below. The account has not been saved yet. The page may look like this:",
            "Verify and save account",
            "1. tap here",
            "white page",
            "Tap the <b>address bar</b> and choose <b>Copy</b>.<br>" +
                "• <b>iPhone (Safari):</b> the address is at the <b>BOTTOM</b>. Tap it → <b>Copy</b> icon (two squares).<br>" +
                "• <b>Android (Chrome):</b> the address is at the <b>TOP</b>. Tap it → <b>Copy</b> icon (two squares).",
            "<b>Go back to the PlayCaption app and tap “Open in browser” again</b> – this page opens again. Long-press the box below → <b>Paste</b>. It sends itself.",
            "Long-press here and choose Paste"
        )
    }
}
