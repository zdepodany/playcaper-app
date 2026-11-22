package cz.titulky.tv

/** LG potřebuje telefon jako zdroj OCR i se zhasnutým displejem. */
internal object TelefonPozadi {
    fun pokracovatNaLg(aktivni: Boolean, cilIp: String?, platforma: TvPlatforma?): Boolean =
        aktivni && !cilIp.isNullOrBlank() && platforma == TvPlatforma.WEBOS
}
