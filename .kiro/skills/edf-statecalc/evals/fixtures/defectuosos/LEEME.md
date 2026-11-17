# Fixtures defectuosos - SINTETICOS
SINTETICO: cada carpeta contiene artefactos con UN defecto deliberado (15 carpetas, 01 a 15), para comprobar que `verificar-artefacto.ps1` los rechaza con el motivo correcto. NO son salidas reales del skill. Ningun dato representa personas o reglas de negocio reales.
El mapa carpeta -> defecto -> codigo de verificacion esperado esta en `Diseño/casos-prueba-skill-statecalc.md` (seccion "Validacion del arnes") y se ejecuta de forma automatica con `evals/probar-arnes.ps1`.
