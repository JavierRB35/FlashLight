# FlashLight

Aplicación Android para usar el LED del teléfono como linterna continua o como
estrobo ajustable de 1 a 4 destellos por segundo.

## Uso

- **Linterna:** toca el control principal para encender o apagar el flash.
- **Estrobo:** selecciona ese modo, ajusta la velocidad con el deslizador y
  confirma antes de iniciar cada sesión.
- Al salir de la aplicación, el flash se apaga automáticamente.
- El permiso de cámara solo se usa para controlar el LED; la app no toma fotos
  ni graba vídeo.

> **Precaución:** los destellos pueden provocar convulsiones fotosensibles. No
> actives el estrobo cerca de personas con epilepsia o sensibilidad a destellos,
> y no mires directamente al LED. La app solicita confirmación cada vez.

## Compilar

Requiere Java 17 y Android SDK 34:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.