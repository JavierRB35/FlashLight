# FlashLight

Aplicación Android para usar el LED del teléfono como linterna continua o como
estrobo ajustable de 1 a 6 destellos por segundo, y una señal SOS repetida en
código Morse. La frecuencia del estrobo se puede modificar mientras está
activo. La interfaz está diseñada para mostrar los controles principales en
una sola pantalla.

## Identidad y arranque

- Al abrir **Flashlight** se muestra durante **cuatro segundos de tiempo visible** una pantalla de bienvenida con la mascota de linterna, nombre de la aplicación, autor, correo electrónico y mes/año en español calculados al iniciar. No hay botón que pulsar.
- La transición abre automáticamente la pantalla principal. El temporizador conserva el tiempo restante ante rotaciones o interrupciones, y el splash no requiere permisos ni enciende el LED.
- El icono del escritorio Android, el encabezado y el botón principal utilizan la mascota. Los controles comparten el azul marino, amarillo, coral y turquesa de la imagen original, respetando las advertencias y confirmaciones de seguridad de estrobo y SOS.

## Actualizaciones

La app comprueba **GitHub Releases** al abrir y de forma periódica (aproximadamente cada 12 horas, según Android). El icono **↻** del encabezado permite comprobar manualmente y activar avisos en Android 13 o posterior.

Las actualizaciones son voluntarias: solo después de pulsar **Actualizar** se descarga una APK. Se verifican SHA-256, tamaño, versión, paquete y firma antes de abrir el instalador Android. La linterna y Morse funcionan sin conexión.

**El canal requiere una primera Release APK firmada**; los artefactos de depuración de GitHub Actions no son actualizaciones oficiales. Consulta [publicación, firma y pruebas del actualizador](docs/ACTUALIZACIONES.md).

## Uso

- **Linterna:** toca el control principal para encender o apagar el flash.
- **Estrobo:** selecciona ese modo, ajusta la velocidad con el deslizador y
  confirma antes de iniciar cada sesión. El deslizador también funciona durante
  el estrobo.
- **SOS:** emite el patrón Morse estándar (tres cortos, tres largos y tres
  cortos) en bucle; requiere confirmación antes de cada sesión.
- **Enviar:** escribe un mensaje y pulsa **Enviar con flash**. La app muestra
  una vista previa del texto normalizado y pide confirmación antes de emitirlo.
  Inicia primero la recepción en el otro teléfono y centra el flash emisor en
  su vista de cámara. La barra muestra el avance exacto por pulsos de luz
  completados (cada punto o raya cuenta como un destello).
- **Recibir:** concede el permiso de cámara al iniciar la recepción. La vista
  previa analiza la luz y transcribe las letras detectadas en el editor; pulsa
  **Detener recepción** para cerrar la cámara y finalizar el último carácter.
- El envío usa Morse internacional a 200 ms por punto y 600 ms por raya. Las
  letras y palabras se separan con pausas estándar. Se admiten A–Z, números y
  los signos `. , ? !`; los acentos se normalizan (por ejemplo, `Á` se convierte
  en `A`) y la app avisa si debe omitir caracteres no compatibles.
- La recepción se basa en el brillo de la imagen de cámara. La distancia,
  iluminación ambiental, enfoque y velocidad de fotogramas pueden afectar a la
  precisión; para mejores resultados, mantén ambos teléfonos estables y el
  emisor centrado en la vista previa.
- Al salir de la aplicación, el flash se apaga automáticamente.
- La cámara se activa al iniciar la recepción y se cierra al detenerla o salir.
  No se toman fotos ni se graba vídeo.

> **Precaución:** los destellos pueden provocar convulsiones fotosensibles. No
> actives el estrobo ni la señal SOS cerca de personas con epilepsia o
> sensibilidad a destellos, y no mires directamente al LED. La app solicita
> confirmación para cada sesión intermitente.

## Compilar

Requiere Java 17 y Android SDK 34:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.