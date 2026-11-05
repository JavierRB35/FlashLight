# FlashLight

Aplicación Android para usar el LED del teléfono como linterna continua o como
estrobo ajustable de 1 a 6 destellos por segundo, y una señal SOS repetida en
código Morse. La frecuencia del estrobo se puede modificar mientras está
activo. La interfaz está diseñada para mostrar los controles principales en
una sola pantalla.

## Uso

- **Linterna:** toca el control principal para encender o apagar el flash.
- **Estrobo:** selecciona ese modo, ajusta la velocidad con el deslizador y
  confirma antes de iniciar cada sesión. El deslizador también funciona durante
  el estrobo.
- **SOS:** emite el patrón Morse estándar (tres cortos, tres largos y tres
  cortos) en bucle; requiere confirmación antes de cada sesión.
- Al salir de la aplicación, el flash se apaga automáticamente.
- El permiso de cámara solo se usa para controlar el LED; la app no toma fotos
  ni graba vídeo.

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