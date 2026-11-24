# Revisión F · Comparación con las implementaciones manuales

Esta parte de la auditoría no se puede hacer en el repo del framework: necesita acceso a Bitbucket (proyecto EEO) y a las ramas con implementación manual. Se hace en el workspace del ingeniero, con Kiro o con Claude Code.

## Cómo usarlo

1. Abre el workspace donde están clonados los repos de Oxygen (`oxygen-validation`, `oxygen-acceptance`, `oxygen-curation`, `oxygen-pinning`, `oxygen-synthesis`, `oxygen-statecalculation`) y `buro.colombia_innersource`.
2. Ten a mano lo que generó el framework para la ingesta (`expedientes/<id>/propuestas/<etapa>/`).
3. Envía el prompt de abajo, una etapa a la vez. Cambia `<ETAPA>`, `<REPO>` y `<RAMA>`.

| Etapa | Repo | Rama de referencia sugerida |
|---|---|---|
| Validation | `oxygen-validation` | `feature/colombia_ifr_java17` (SISBEN) |
| Acceptance | `oxygen-acceptance` | la rama con la configuración de SISBEN o de MURIC |
| Curation | `oxygen-curation` y `buro.colombia_innersource` | la rama con el processor de SISBEN o de MURIC |
| Pinning | `oxygen-pinning` y `buro.colombia_innersource` | la rama con `ColombiaIdentityMatcher` |
| Synthesis | `oxygen-synthesis` | la rama con el GDD de Colombia |
| State Calculation | `oxygen-statecalculation` | la rama con la sección `#sisben` del XPM |

## Prompt

```
Compara lo que generó el framework para <ETAPA> (expedientes/<id>/propuestas/<etapa>/) con la implementación
manual del repo <REPO>, rama <RAMA>. Extrae los archivos de esa rama sin tocar mi árbol de trabajo
(git show <RAMA>:<ruta>). No compiles ni ejecutes nada.

Para cada diferencia, da: archivo y línea en los dos lados, qué hace cada uno, y su clasificación:
- DEFECTO DEL FRAMEWORK: el generador contradice la spec o una decisión documentada de su skill.
- HUECO DEL FRAMEWORK: la spec no tiene cómo expresar lo que hace el manual (falta una clave en el esquema).
- PROBLEMA DE LA SPEC: el generador hizo lo que dice la spec, pero la spec difiere de lo que hace el manual.
- DEFECTO DEL MANUAL: la implementación manual está mal (para informar a Experian).
- DIFERENCIA DE CONTEXTO: no se puede saber cuál es la vigente (p. ej. otro layout); no se penaliza.

Severidad: P0 dato incorrecto sin aviso o contrato roto con la etapa siguiente; P1 obliga a editar a mano lo
generado; P2 documentación o comentarios.

Revisa en particular: contratos con las clases base del motor (miembros que el manual sobrescribe y el
generado no), manejo de errores (excepciones tragadas, valores por defecto silenciosos), nombres y tipos de
los campos que lee la etapa siguiente, y claves HOCON.

Entrega una tabla con: ID (REV-F-<ETAPA>-nn), clasificación, severidad, evidencia (archivo:línea en ambos
lados), esperado frente a real, y corrección propuesta. Al final, lista aparte los DEFECTOS DEL MANUAL.
```

## Qué hacer con el resultado

- **Defectos y huecos del framework:** se agregan al informe (`auditoria/informe-auditoria.md`) y se corrigen en la skill, con prueba de regresión, como el resto de la auditoría.
- **Problemas de la spec:** van a la spec de la ingesta, en una versión nueva.
- **Defectos del manual:** se informan a Experian.
