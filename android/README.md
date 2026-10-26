# Nexro TV Launcher

Premium Android TV launcher focused on apps actually installed on the device. It deliberately does not show fabricated TV-show, movie-library, recommendation, or genre categories.

## Build APK
The GitHub Actions workflow **Build Nexro TV Launcher APK** runs `gradle assembleDebug` and uploads `app-debug.apk` as the `nexro-tv-launcher-debug-apk` artifact. Run it from Actions, or wait for a push affecting `android/**`.

## Pairing bridge
The app shows its local IP address and a six-digit rotating PIN. The website connects to `http://TV-IP:8765`, exchanges the PIN for a bearer token, and sends supported commands over the local Wi-Fi network.

## Real limitations
Volume, launcher navigation, opening installed apps, settings and media-key requests are attempted on Android TV. Device vendors and foreground apps may limit their effect. Ordinary launcher apps cannot guarantee TV power-off, channel tuning or HDMI switching. The bridge is intended for a trusted home LAN; don't expose port 8765 to the public internet.