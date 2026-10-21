# 0.9.21 (pieza «panel de carga más grande») — El panel flotante de carga, un poco más grande

Fecha: 2026-10-21. Estado: aprobado por el usuario (21/10). **Pieza de la 0.9.21**: sube a producción con la 0.9.21
completa (decisión del usuario: sin subida aparte). **Solo web.**

## 1. Qué pasa

El panel flotante «Carga de técnicos» de la 0.9.20 es la mitad de ancho que el modal de antes, y los nombres se cortan
antes:

| | Modal de antes (0.9.19) | Panel de la 0.9.20 |
|---|---|---|
| Ancho | 680 px | 340 px |
| Alto de la lista | 460 px | 400 px (o menos, si la ventana es baja) |
| Nombre del técnico | 110 px, letra de 13 | 72 px, letra de 12,5 |

## 2. Cambio (decisión del usuario: «un poco más grande, y si tapa algo se minimiza»)

Un solo tamaño fijo, sin botón ni arrastre para cambiarlo:
- **ancho 440 px** (en un PC de 1366 px, un tercio de la pantalla);
- **nombre del técnico 100 px, letra de 13** (y los textos del panel, de 12,5 a 13);
- **lista hasta 460 px de alto**, sin pasar del alto de la ventana (`min(460px, 100vh − 140px)`);
- la pastilla minimizada, el anclaje a las esquinas, el arrastre y lo que recuerda el navegador no cambian.

## 3. Código y pruebas

- `src/modules/taller/asignaciones/panel/PanelCarga.tsx` y `FilaTecnico.tsx`: las clases de ancho, alto y letra.
- E2E `panel-carga`: el comentario de la zona neutra («unos 340 px» → «unos 440 px»); la zona (700, 450) sigue fuera.
- Sin tests unitarios nuevos (solo cambian medidas). Se comprueba con la suite, la E2E `panel-carga` en preprod y una
  captura a 1366×768 y a 1920×1080.
- CHANGELOG de la web y `NOVEDADES-v0.9.21.md`: una línea cada uno.
