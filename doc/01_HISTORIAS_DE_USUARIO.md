# 📖 01. Historias de Usuario — GarecaTV

## HU-01: Zapping Rápido de Canales en Android TV
* **Como** aficionado al deporte en Smart TV,
* **Quiero** cambiar de canal con las teclas ◀ y ▶ del control remoto y ver la transmisión en menos de 1 segundo,
* **Para** disfrutar de una experiencia fluida e instantánea similar a la televisión por cable tradicional o TiviMate.
* **Criterios de Aceptación**:
  - TTFF (Time to First Frame) < 800 ms.
  - La pantalla no debe ponerse en negro ni parpadear destructivamente durante el zapping.
  - El banner informativo (`llChannelBanner`) muestra el nombre, número de canal y auto-desaparece a los 3.5s.

## HU-02: Agenda Deportiva Cronológica y Auto-Enfoque en Vivo
* **Como** usuario de GarecaTV,
* **Quiero** que al abrir la app la lista se posicione automáticamente en los partidos que se están jugando en vivo,
* **Para** ingresar directamente a la transmisión sin tener que scrollear manualmente por eventos pasados.
* **Criterios de Aceptación**:
  - Orden estricto cronológico por hora (`sortDailyMatchesChronologically`).
  - Auto-scroll y foco en el primer partido con estado `● EN VIVO`.

## HU-03: Control de Zoom y Formato de Pantalla con el D-Pad
* **Como** espectador con control remoto,
* **Quiero** cambiar entre Ajuste Original, Rellenar 16:9 y Zoom Recorte con la tecla Arriba o atajos dedicados,
* **Para** adaptar la transmisión a la pantalla de mi televisor sin barras negras deformadas.
* **Criterios de Aceptación**:
  - Solo disponible cuando hay frames de video válidos recibidos.
  - Cartel flotante HUD (`tvZoomHud`) confirmando el modo durante 2.5s.

## HU-04: Actualización Remota Over-The-Air (OTA)
* **Como** usuario de Android TV,
* **Quiero** ser notificado cuando exista una versión superior y poder actualizarla directamente con un botón,
* **Para** mantener la aplicación al día sin necesidad de reinstalar por USB o ADB.
