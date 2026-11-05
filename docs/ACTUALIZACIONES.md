# Actualizaciones integradas de FlashLight

FlashLight consulta **GitHub Releases público**, sin token ni contraseña. No consulta repositorios privados, no descarga APK sin consentimiento y nunca instala en silencio. La función se activa cuando existe una Release oficial que cumpla las reglas de abajo.

## Comportamiento

- Al abrir la pantalla principal se comprueban nuevas versiones sin bloquear el manejo de la linterna. Las consultas se limitan a una cada 12 horas, salvo cuando se usa la acción manual.
- El botón accesible **↻** del encabezado permite «Buscar actualizaciones ahora». En Android 13 o posterior también permite activar avisos del sistema.
- Android programa verificaciones periódicas con red mediante `JobScheduler`; sus horarios son aproximados, sujetos a batería y restricciones del sistema. No se descarga nada en segundo plano.
- Si hay una versión superior, se muestra «Actualizar» o «Más tarde». El aviso automático se muestra una vez por versión; la comprobación manual siempre puede volver a mostrarlo.
- Tras «Actualizar», la aplicación descarga la APK a su caché privada, valida tamaño, SHA-256, paquete, versión y certificado de firma respecto de la aplicación instalada. Solo entonces abre el instalador de Android y solicita los permisos/confirmaciones necesarios.
- Si falla la red, faltan metadatos o la firma no coincide, no cambia la instalación actual. Los modos Linterna, Estrobo, SOS y Morse funcionan sin conexión.

## Primera publicación y publicación de nuevas versiones

**Estado inicial al implementar:** FlashLight no tenía Releases oficiales; `app-debug.apk` de GitHub Actions es un artefacto de prueba, no se debe publicar como actualización oficial.

1. Guarda una **única clave de firma Release permanente** fuera del repositorio (en una ubicación segura y con copia de seguridad). No subas nunca `.jks`, contraseñas ni secretos al repositorio.
2. Versión inicial preparada por este cambio: `versionName "1.1.0"` y `versionCode 2`. Publica una APK Release firmada con esta identidad, utilizando:
   - **Tag:** `v1.1.0-build.2`
   - **Asset APK:** `FlashLight-1.1.0-build.2-release.apk`
   - **Destino:** <https://github.com/JavierRB35/FlashLight/releases>
3. Para compilar localmente en Windows, configura las variables de entorno `FLASHLIGHT_SIGN_RELEASE=true`, `FLASHLIGHT_KEYSTORE_FILE`, `FLASHLIGHT_KEYSTORE_PASSWORD`, `FLASHLIGHT_KEY_ALIAS` y `FLASHLIGHT_KEY_PASSWORD`. Ejecuta `./gradlew :app:assembleRelease` (PowerShell: `.\gradlew.bat :app:assembleRelease`). El APK resultante de compilación debe firmarse con **la misma clave que la versión instalada**.
4. Antes de publicar, verifica el APK con `apksigner verify --print-certs`, su nombre de paquete `com.javierrb35.flashlight`, `versionCode`, `versionName` y la huella SHA-256. Renombra la APK y súbela como asset binario de una Release pública **no draft, no prerelease**. Comprueba que la API GitHub de esa Release exponga `digest: "sha256:…"` para el asset.
5. En futuras versiones, **incrementa siempre** `versionCode` y la versión semántica en `app/build.gradle`. Ejemplo: `versionName "1.1.1"`, `versionCode 3`, tag `v1.1.1-build.3`, asset `FlashLight-1.1.1-build.3-release.apk`. Cada publicación debe conservar el certificado de la primera.
6. Comprueba desde otra instalación anterior con la misma firma: abrir la app → detectar novedad → «Más tarde» → comprobación manual → «Actualizar» → descargar → confirmar instalación Android → validar que los datos y funciones existentes continúan.

## Formato de Releases admitido

El detector recorre las últimas 20 Releases y acepta exclusivamente las que cumplan:

- Tag `vM.m.p-build.C`, siendo `C` el `versionCode` real de la APK.
- Asset `FlashLight-M.m.p-build.C-release.apk` en la ruta HTTPS oficial de Releases de **JavierRB35/FlashLight**.
- Asset publicado correctamente, tamaño 4 KiB–100 MiB y `digest` GitHub de SHA-256 (64 dígitos).
- Versión superior a la instalada, tanto por semántica como por `versionCode`.
- La APK debe tener el mismo ID de paquete y certificado de firma que la aplicación actualmente instalada.

**Importante:** las APK de depuración y las APK Release firmadas con claves distintas no pueden actualizarse entre sí. Android no permite sustituir una aplicación existente con una firma diferente. No desinstales la versión anterior sin revisar los datos que deban conservarse.

## Pruebas de aceptación

- Sin Releases: el botón manual indica que no hay versiones oficiales; se mantienen las funciones de linterna.
- Con Release válida superior: aviso en pantalla, opción «Más tarde», descarga solo tras aceptar y confirmación del instalador.
- Digest incorrecto, tamaño incorrecto, host distinto, versión antigua, APK de otro paquete o firma distinta: rechazo sin instalación.
- Sin conexión: comprobación manual informa del problema, sin bloquear las funciones locales.
- Avisos en segundo plano: notificación solo si Android lo permite; no instala automáticamente.
- Abrir notificación: entra en FlashLight y presenta la actualización una sola vez.

La descarga de APK e instalación en dispositivo requieren **validación real en Android** antes de considerar el flujo listo para distribución.
