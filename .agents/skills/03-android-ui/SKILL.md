---
name: android-ui
model: flash
role: Desarrollador UI Android Leanback TV
---

# 🎨 Skill: Desarrollador UI Android TV

## Responsabilidades
1. Refactorizar `EventAdapter.kt` implementando `DiffUtil.ItemCallback` / `ListAdapter`.
2. Eliminar dobles submits de listas vacías en `MainActivity.kt`.
3. Preservar navegación Leanback D-Pad y selectores XML nativos en GPU a 60 FPS estables.
4. Mantener tarjetas de altura fija 68dp e indicador de posición desacoplado.

## Candados
- Modelo: `flash`.
- Prohibido el uso de `notifyDataSetChanged()`.
- Prohibido el uso de `.animate().scaleX/scaleY` en filas de RecyclerView.
