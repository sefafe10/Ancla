package com.ancla.safespace.domain.model

data class CountryHotline(
    val code: String,
    val country: String,
    val service: String,
    val number: String,
    val emergencyNumber: String,
    val sourceUrl: String,
)

/** Recursos revisados en fuentes oficiales. Los demás países pueden guardar un número local. */
object EmergencyHotlines {
    val countries = listOf(
        CountryHotline("ES", "España", "Línea 024", "024", "112", "https://www.sanidad.gob.es/linea024/home.htm"),
        CountryHotline("MX", "México", "Línea de la Vida", "800 911 2000", "911", "https://www.gob.mx/conasama/articulos/linea-de-la-vida-800-911-2000"),
        CountryHotline("CO", "Colombia", "Línea nacional 106", "106", "123", "https://www.minsalud.gov.co/salud/publica/salud-mental/Paginas/linea-106.aspx"),
    )

    val custom = CountryHotline("OT", "Otro país / personalizar", "Línea local de ayuda", "", "", "")
}
