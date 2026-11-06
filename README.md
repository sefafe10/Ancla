# Ancla

MVP Android nativo de apoyo emocional, offline-first y sin analítica.

## Incluido

- Onboarding con selección de España, México y Colombia, más configuración manual de una línea y el número de emergencias local.
- Acceso persistente a ayuda con `ACTION_DIAL`; la app nunca inicia llamadas directamente.
- Ejercicio de respiración visual y grounding 5–4–3–2–1.
- Plan de seguridad personal de seis apartados.
- Preferencias y plan guardados como un único bloque AES-GCM; la clave AES vive en Android Keystore.
- Directorio de apoyo cercano con tarjetas de profesionales de salud mental, centros de salud, hospitales y recursos comunitarios.
- Radio seleccionable de 1 a 20 km, con 3 km por defecto; al cambiarlo después de buscar, pulsa «Aplicar radio» para actualizar.
- El permiso de ubicación aproximada se solicita solo tras pulsar «Buscar cerca de mí»; también se puede buscar una ciudad manualmente.
- Sin permisos de ubicación precisa, llamadas directas, SMS ni contactos. Las llamadas se entregan al marcador y las rutas abren Google Maps.

## Abrir y compilar

Abre la carpeta raíz en Android Studio con Android SDK 36 y JDK 17 instalados. El proyecto no incluye Gradle Wrapper todavía. El primer inicio solicita una línea de apoyo y un número de emergencias locales antes de permitir continuar.

## Alcance de esta versión

El directorio de apoyo usa Nominatim para buscar localidades y centros cercanos, Photon para buscar recursos etiquetados y tres instancias públicas de Overpass como respaldo. Permite filtrar por categoría y elegir un radio de 1 a 20 km. Las tarjetas muestran la información disponible (dirección y vías de contacto) y abren rutas en Google Maps. La ubicación aproximada o el texto buscado se envían a estos servicios; Ancla no guarda el historial de ubicaciones. Las peticiones a Nominatim se limitan a una por segundo. Los datos abiertos pueden estar incompletos, desactualizados o temporalmente saturados.

Los datos de las líneas nacionales se limitan a recursos revisados en los sitios oficiales enlazados en `EmergencyHotlines.kt`. Antes de publicar, hace falta validar disponibilidad y alcance territorial con personas usuarias y profesionales de salud mental, revisar accesibilidad y completar pruebas de dispositivo y la revisión de las condiciones de uso de los proveedores del directorio. La aplicación ofrece herramientas de apoyo, no atención clínica ni respuesta de emergencia.
