# Alcance y glosario

## Qué es la etapa Validation

Primera etapa del pipeline EDF/Oxygen. Responde: ¿qué tan bien cumplen el archivo y cada registro el
contrato de datos de la fuente? Es **diagnóstica, no dispone**: todo registro sale a `validated/` con su
diagnóstico (códigos de error con severidad, quality scores, trazabilidad). No existe `rejected/` en
Validation; aceptar o rechazar es trabajo de Acceptance.

El motor (`SparkValidator` / `SparkValidatorCustomMode`) no se modifica. La fuente se enchufa con una
clase Scala cargada por reflexión (clave `processor` del HOCON) más un bloque HOCON aditivo.

## Hasta dónde llega el skill

| Incluido | Fuera de alcance |
|---|---|
| Leer y revisar el source-spec aprobado de `edf-analisis` (el ejemplo SISBEN es solo de forma) | Escribir en el fork sin aprobación del técnico |
| Generar Capa A, Capa B, evento, metadatos, processor, tests, fixture, HOCON, lookups | Modificar `*-common-application.conf` o los motores |
| Verificar el spec y la salida (códigos, huecos, PII, credenciales) | Compilar contra el JAR (F-01: no hay acceso) |
| Pre-validar localmente un archivo (simulación) | Reportes `Log_Sisben/RPT_*`, notificación al DNP |
| Runbook de ejecución e interpretación de la salida | Anti-duplicado por nombre de archivo (Preparation) |
| Contrato de entrega a Acceptance | Configurar Acceptance o Curation |

## Etapas internas del motor (E1–E15)

E1 descubrimiento · E2 selección de configuración por `filePattern` (ruta completa) · E3 lectura ·
E4 instanciación del processor · E5 archivo vacío · E6 nombre · E7 header · E8 footer · E9 cuerpo ·
E10 contextual · E11 nueva relación suscriptor · E12 duplicados · E13 métricas · E14 atributos
históricos · E15 publicación. En SISBEN se apagan E10, E11 y E14.

## Glosario

| Término | Significado |
|---|---|
| source-spec | YAML que describe la fuente; única entrada del generador |
| Capa A | Tipos y validadores genéricos puros (sin Spark ni framework); se generan una vez |
| Capa B | Especificación de la fuente como datos (`<Fuente>Spec`) |
| Capa C | Processor de la fuente; conecta A + B con los hooks del framework |
| `ObjectError` | Diagnóstico del framework: `errorCodeName`, `errorCodeValue` (el código), `errorLevel`, `messageKey`, `errorType` |
| Severidad / nivel | `"1"` error (anula RQS), `"3"` inconsistencia. Texto de un carácter |
| RQS | Quality score de registro: 1.0 sin error de nivel 1; 0.0 con alguno |
| Marcador | Evento de archivo: `errorInFileName` (-1002), `errorInHeader` (-1003), `errorInFooter` (-1004), `errorInEmptyFileBody` (-1005) |
| `OxygenDuplicate` | Código que marca un duplicado no conservado (nivel `""`) |
| Guard-rails | `dataSource`, `subscriber`, `subscriberRelation`, `asOfDate`, `receivedDate` |
| Regla aditiva | Solo se agregan elementos nuevos en los archivos *inner*; nada existente se edita |
| HITL | Human-in-the-Loop: nada se copia ni ejecuta sin aprobación explícita del técnico |
| ASSUMPTION | Valor provisional sin evidencia en código; se marca en código, HOCON y reporte |
