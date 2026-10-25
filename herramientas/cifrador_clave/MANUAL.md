# 📖 Manual de Uso: Cifrado de Sobre para Claves de GarecaTV

Este módulo permite cambiar la clave secreta de la app remotamente desde GitHub **sin que nadie en Internet pueda verla ni copiarla**.

---

## 1. ¿Cómo Funciona?

1. En lugar de subir la clave de Errado/TevePlay en texto legible a GitHub, este script la envuelve en un **"Sobre Cifrado" (Envelope Encryption)** mediante AES-256-CBC con una semilla interna matemática.
2. Quien abra tu archivo en GitHub o intente sniffear la red con Wireshark solo verá una cadena de números hexadecimales aleatorios:
   ```json
   "active_key_enc": "c438b6d62437d66431292c8f5cac5f3ebcb1ff067d07a0b1..."
   ```
3. La aplicación de las Smart TVs descarga ese sobre, lo descifra de forma instantánea en su memoria RAM y continúa reproduciendo los partidos normalmente.

---

## 2. ¿Cómo Cambiar la Clave Cuando Sea Necesario?

### Opción Rápida (1 solo comando):
Abre tu terminal en la carpeta del proyecto y ejecuta:
```bash
python3 herramientas/cifrador_clave/encrypt_key.py "TU_NUEVA_CLAVE_AQUI" --update-json
```
*Este comando cifrará la clave y actualizará automáticamente el archivo `version.json` del proyecto.*

### Opción Manual:
1. Ejecuta el script con tu nueva clave:
   ```bash
   python3 herramientas/cifrador_clave/encrypt_key.py "TU_NUEVA_CLAVE_AQUI"
   ```
2. Copia la línea que te entrega en pantalla:
   ```json
   "active_key_enc": "...",
   ```
3. Abre tu archivo `version.json` y reemplaza la clave vieja por esta nueva línea.

---

## 3. ¿Dónde está el archivo en GitHub?

El archivo que lee la aplicación se encuentra en:
* **Para editar en GitHub**: [https://github.com/dasataca/ErradoTV/blob/main/version.json](https://github.com/dasataca/ErradoTV/blob/main/version.json)
* **Enlace directo (Raw)**: [https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json)

Una vez actualizado `version.json`, haz commit y push:
```bash
git add version.json
git commit -m "security: rotación de sobre cifrado"
git push origin main
```

---

## 4. ¿Con qué frecuencia leen la clave las Smart TVs?

* **Al encender / Abrir la app**: Cada vez que el usuario inicia GarecaTV en su televisor, consulta `version.json` para tener la clave más fresca.
* **Al refrescar el catálogo**: Cada vez que se consulta la agenda deportiva o se cambia de categoría principal.
* **Al iniciar sesión**: En la pantalla de login.
* **Memoria interna de respaldo**: Una vez descargada con éxito, la app guarda la clave en sus preferencias privadas (`SharedPreferences`), de modo que si por algún momento el televisor se queda sin internet o GitHub tiene una caída temporal, la TV sigue funcionando con normalidad.
