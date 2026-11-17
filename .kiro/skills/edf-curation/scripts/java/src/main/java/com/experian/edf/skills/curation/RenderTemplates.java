package com.experian.edf.skills.curation;

import static com.experian.edf.skills.curation.Py.list;
import static com.experian.edf.skills.curation.Py.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Genera los artefactos de Curation (CUR-TPL-01..12) desde un source-spec.
 *
 * <pre>
 * Uso (desde la raíz del skill):
 *   java -jar scripts/java/target/render-templates.jar \
 *       --spec assets/examples/sisben/source-spec.sisben.yaml --out assets/examples/sisben/generated
 * </pre>
 *
 * Valida el source-spec contra assets/schema/source-spec.schema.yaml, calcula los huecos declarados en
 * x-placeholders, rellena cada plantilla de assets/templates/, verifica que no quede ningún {{hueco}} sin
 * resolver y escribe:
 * <pre>
 *   &lt;out&gt;/&lt;ruta del repo destino&gt;   código Scala y recursos de test (mismas rutas del fork)
 *   &lt;out&gt;/fragmentos/               HOCON y domain dictionary a FUSIONAR (regla aditiva)
 *   &lt;out&gt;/expected/                 salida esperada del fixture (implementación de referencia)
 *   &lt;out&gt;/MANIFEST.json             qué plantilla generó qué archivo y adónde va en el fork
 * </pre>
 * Con --write-expected DIR copia además la salida esperada a DIR (golden versionado). Sin red.
 * ATENCIÓN: --out se borra y se vuelve a crear.
 */
public final class RenderTemplates {

    public static final Path SKILL_ROOT = findSkillDir();
    /** Marca de sintético de la carpeta de lookups (AC-30): se copia del fixture a los lookups generados. */
    static final String MARCA_LOOKUPS = "LEEME.md";
    /** Zona de asOfDate de la etapa anterior si la spec no la declara (D3; ADR-G4). */
    static final String ZONA_ASOFDATE_POR_DEFECTO = "America/Bogota";
    /** Origen de zona_horaria_fechas si la spec no lo declara (ADR-G4). */
    static final String ORIGEN_ZONA_POR_DEFECTO = "supuesto";
    static final Pattern HUECO =Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");
    static final Map<String, String> SCALA_TIPO = Map.of("texto", "String", "entero", "Int", "largo", "Long",
            "decimal", "Double", "booleano", "Boolean");
    static final Map<String, String> TIPO_SALIDA_SCALA = Map.of("texto", "Texto", "entero", "Entero", "largo", "Largo",
            "decimal", "Decimal", "booleano", "Booleano");
    static final Map<String, String> DD_TIPO = Map.of("texto", "string", "entero", "integer", "largo", "long",
            "decimal", "double", "booleano", "boolean");
    static final Map<String, String> TRANSF_SCALA = Map.of(
            "texto", "Transformacion.Texto",
            "original", "Transformacion.Original",
            "nombre", "Transformacion.Nombre",
            "fecha_epoch", "Transformacion.FechaEpoch",
            "homologacion", "Transformacion.Homologacion",
            "documento", "Transformacion.Documento");
    private static final List<String> SIN_ORIGEN = List.of("desde_archivo", "copia_de", "constante");
    // strptime de Python: %Y = 4 dígitos; %m, %d, %H, %M y %S admiten uno o dos (y %d un espacio inicial).
    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;
    private static final String P_Y = "(\\d\\d\\d\\d)";
    private static final String P_M = "(1[0-2]|0[1-9]|[1-9])";
    private static final String P_D = "(3[01]|[12]\\d|0[1-9]|[1-9]| [1-9])";
    private static final String P_HH = "(2[0-3]|[0-1]\\d|\\d)";
    private static final String P_MI = "([0-5]\\d|\\d)";
    private static final String P_S = "(6[0-1]|[0-5]\\d|\\d)";
    private static final Pattern STRPTIME_FECHA_HORA = Pattern.compile(
            P_Y + "-" + P_M + "-" + P_D + "\\s+" + P_HH + ":" + P_MI + ":" + P_S, U | Pattern.CASE_INSENSITIVE);
    private static final Pattern STRPTIME_FECHA = Pattern.compile(P_D + "/" + P_M + "/" + P_Y, U | Pattern.CASE_INSENSITIVE);
    // F-FW-10: "%Y-%m-%d" (ISO, solo fecha).
    private static final Pattern STRPTIME_FECHA_ISO = Pattern.compile(P_Y + "-" + P_M + "-" + P_D, U | Pattern.CASE_INSENSITIVE);
    /**
     * Huecos cuyo valor puede quedar vacío porque representan una lista vacía legítima dentro de un {@code Set(...)}
     * de Scala (F-FW-13). Cualquier otro hueco vacío sigue siendo un error.
     */
    static final Set<String> PUEDEN_QUEDAR_VACIOS = Set.of("pendientes_scala", "niveles_no_almacenar_scala", "niveles_anular_scala");
    private static final Pattern STRPTIME_RECEPCION = Pattern.compile(
            P_Y + P_M + P_D + P_HH + P_MI + P_S + "([+-])(\\d\\d):?([0-5]\\d)", U | Pattern.CASE_INSENSITIVE);
    private static final Pattern MARCA_TIEMPO = Pattern.compile("\\.(\\d{14})([+-]\\d{4})", U);

    private RenderTemplates() {
    }

    private static Path findSkillDir() {
        try {
            Path p = Path.of(RenderTemplates.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath();
            for (Path d = p; d != null; d = d.getParent()) {
                if (Files.isDirectory(d.resolve("assets/templates")) && Files.isRegularFile(d.resolve("SKILL.md"))) {
                    return d;
                }
            }
        } catch (URISyntaxException | SecurityException e) {
            // se usa el directorio actual
        }
        return Path.of("").toAbsolutePath();
    }

    // ---------------------------------------------------------------- utilidades

    /** Literal de cadena Scala/JSON entre comillas dobles. */
    static String q(Object s) {
        return Py.jsonDumps(s == null ? "" : Py.str(s));
    }

    /** Literal Scala triple-comillas (para JSON y regex). */
    static String tq(String s) {
        if (s.contains("\"\"\"")) {
            throw new RenderError("el texto contiene comillas triples");
        }
        return "\"\"\"" + s + "\"\"\"";
    }

    /** JSON compacto, mismo formato que Gson (serializeNulls, disableHtmlEscaping). */
    static String compacto(Object obj) {
        return Py.jsonCompact(obj);
    }

    static String leerTexto(Path ruta) {
        try {
            return Files.readString(ruta, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> cargarYaml(Path ruta) {
        return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(leerTexto(ruta));
    }

    static void escribir(Path ruta, String texto) {
        try {
            Files.createDirectories(ruta.getParent());
            Files.writeString(ruta, texto, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Path(texto).name y Path(texto).stem de Python (separadores / y \). */
    static String nombreArchivo(String ruta) {
        String[] partes = ruta.split("[\\\\/]", -1);
        return partes[partes.length - 1];
    }

    static String stem(String ruta) {
        String nombre = nombreArchivo(ruta);
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    static String listaRepr(Set<String> valores) {
        return Py.str(new ArrayList<>(new TreeSet<>(valores)));
    }

    // ---------------------------------------------------------------- validación

    public static void validarSpec(Map<String, Object> spec, Map<String, Object> schema) {
        Map<String, Object> esquema = new LinkedHashMap<>(schema);
        esquema.remove("x-placeholders");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.valueToTree(esquema);
        JsonSchema validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        List<String[]> errores = new ArrayList<>();
        for (ValidationMessage msg : validator.validate(mapper.valueToTree(spec))) {
            // "$.campos_curados[3].estado" -> "campos_curados/3/estado"
            String path = msg.getInstanceLocation().toString().replaceFirst("^\\$\\.?", "")
                    .replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
            errores.add(new String[] {path, (path.isEmpty() ? "(raíz)" : path) + ": " + msg.getError()});
        }
        if (!errores.isEmpty()) {
            errores.sort(Comparator.comparing(e -> e[0]));
            String detalle = errores.stream().limit(10).map(e -> e[1]).collect(Collectors.joining("; "));
            throw new RenderError("source-spec inválido: " + detalle);
        }
        validarCoherencia(spec);
    }

    /** Reglas que el JSON Schema no expresa (CUR-RC05, CUR-TPL-03). */
    static void validarCoherencia(Map<String, Object> spec) {
        List<Object> cc = list(spec.get("campos_curados"));
        List<String> destinos = cc.stream().map(m -> (String) map(m).get("destino")).collect(Collectors.toList());
        Set<String> duplicados = destinos.stream().filter(d -> destinos.indexOf(d) != destinos.lastIndexOf(d))
                .collect(Collectors.toSet());
        if (!duplicados.isEmpty()) {
            throw new RenderError("nombres publicados repetidos: " + listaRepr(duplicados));
        }
        if (destinos.contains(map(spec.get("parametros")).get("campo_advertencias"))) {
            throw new RenderError("el campo de advertencias no puede ser también un campo curado");
        }
        Set<Object> entradas = new LinkedHashSet<>(list(spec.get("campos_entrada")));
        Set<Object> vistos = new LinkedHashSet<>();
        for (Object o : cc) {
            Map<String, Object> m = map(o);
            Map<String, Object> t = map(m.get("transformacion"));
            Object destino = m.get("destino");
            Object origen = m.get("origen");
            Object tipo = t.get("tipo");
            if (Py.truthy(origen) && !entradas.contains(origen)) {
                throw new RenderError(destino + ": origen " + origen + " no está en campos_entrada");
            }
            if (SIN_ORIGEN.contains(tipo) && Py.truthy(origen)) {
                throw new RenderError(destino + ": " + tipo + " no lleva origen");
            }
            if (!SIN_ORIGEN.contains(tipo) && !Py.truthy(origen)) {
                throw new RenderError(destino + ": falta origen");
            }
            if ("padding_izquierda".equals(tipo) && !t.containsKey("longitud")) {
                throw new RenderError(destino + ": padding_izquierda requiere longitud");
            }
            if ("desde_archivo".equals(tipo) && !t.containsKey("atributo")) {
                throw new RenderError(destino + ": desde_archivo requiere atributo");
            }
            if ("copia_de".equals(tipo) && !vistos.contains(t.get("campo"))) {
                throw new RenderError(destino + ": copia_de debe referirse a un campo anterior");
            }
            vistos.add(destino);
        }
        Set<String> abiertos = new TreeSet<>();
        list(spec.get("campos_abiertos")).forEach(a -> abiertos.add((String) map(a).get("destino")));
        abiertos.retainAll(destinos);
        if (!abiertos.isEmpty()) {
            throw new RenderError("campos abiertos no pueden generarse: " + listaRepr(abiertos));
        }
        // F-FW-11: los campos de extension_subscriber_relation deben ser campos curados publicados.
        if (spec.get("extension_subscriber_relation") != null) {
            Set<String> sinPublicar = new TreeSet<>();
            list(map(spec.get("extension_subscriber_relation")).get("campos")).forEach(c -> {
                if (!destinos.contains(c)) {
                    sinPublicar.add((String) c);
                }
            });
            if (!sinPublicar.isEmpty()) {
                throw new RenderError("extension_subscriber_relation: campos que no se publican: " + listaRepr(sinPublicar));
            }
        }
        for (Map.Entry<String, Object> e : map(spec.get("codigos_error")).entrySet()) {
            if (!entradas.contains(e.getValue())) {
                throw new RenderError("código " + e.getKey() + ": campo " + e.getValue() + " no está en campos_entrada");
            }
        }
        String zonaAsOf = zonaAsOfDate(spec);
        try {
            Referencia.zona(zonaAsOf);
        } catch (DateTimeException e) {
            throw new RenderError("parametros.zona_asofdate_etapa_anterior: zona inválida " + zonaAsOf);
        }
        Map<String, Object> archivo = map(spec.get("archivo"));
        Pattern pat = Pattern.compile((String) archivo.get("file_pattern"));
        for (Object ej : list(archivo.get("ejemplos_sinteticos"))) {
            if (!pat.matcher((String) ej).matches()) {
                throw new RenderError("filePattern no cubre el ejemplo " + ej);
            }
        }
        for (Object ej : list(archivo.get("contraejemplos_sinteticos"))) {
            if (pat.matcher((String) ej).matches()) {
                throw new RenderError("filePattern cubre el contraejemplo " + ej);
            }
        }
    }

    // ---------------------------------------------------------------- zona (ADR-G4, AC-CUR-10)

    /** parametros.zona_asofdate_etapa_anterior, o America/Bogota (D3) si la spec no la declara. */
    static String zonaAsOfDate(Map<String, Object> spec) {
        return Py.str(get(map(spec.get("parametros")), "zona_asofdate_etapa_anterior", ZONA_ASOFDATE_POR_DEFECTO));
    }

    /** parametros.zona_horaria_fechas_origen (requisito | supuesto), o supuesto si la spec no lo declara. */
    static String origenZonaFechas(Map<String, Object> spec) {
        return Py.str(get(map(spec.get("parametros")), "zona_horaria_fechas_origen", ORIGEN_ZONA_POR_DEFECTO));
    }

    /** Advertencias para stderr y MANIFEST.json: hoy, la zona de fechas distinta de la de asOfDate (C-CUR-04). */
    static List<String> advertenciasZona(Map<String, Object> spec) {
        String zonaFechas = Py.str(map(spec.get("parametros")).get("zona_horaria_fechas"));
        String zonaAsOf = zonaAsOfDate(spec);
        if (Referencia.zona(zonaFechas).normalized().equals(Referencia.zona(zonaAsOf).normalized())) {
            return List.of();
        }
        return List.of("ADVERTENCIA: zona_horaria_fechas (" + zonaFechas + ") difiere de la zona de asOfDate de la etapa "
                + "anterior (" + zonaAsOf + ") (C-CUR-04)");
    }

    // ---------------------------------------------------------------- fixture

    private static int entero(String digitos) {
        return Integer.parseInt(Referencia.asciiDigitos(digitos.strip()));
    }

    /** Fecha del fixture (strptime: "%Y-%m-%d %H:%M:%S", "%d/%m/%Y" o "%Y-%m-%d") a epoch ms en la zona dada. */
    static long epochMs(String texto, String zona) {
        Matcher m = STRPTIME_FECHA_HORA.matcher(texto);
        if (m.matches()) {
            try {
                LocalDateTime dt = LocalDateTime.of(anio(m.group(1)), entero(m.group(2)), entero(m.group(3)),
                        entero(m.group(4)), entero(m.group(5)), entero(m.group(6)));
                return dt.atZone(Referencia.zona(zona)).toInstant().toEpochMilli();
            } catch (DateTimeException e) {
                // siguiente formato
            }
        }
        m = STRPTIME_FECHA.matcher(texto);
        if (m.matches()) {
            try {
                LocalDateTime dt = LocalDateTime.of(anio(m.group(3)), entero(m.group(2)), entero(m.group(1)), 0, 0);
                return dt.atZone(Referencia.zona(zona)).toInstant().toEpochMilli();
            } catch (DateTimeException e) {
                // siguiente formato
            }
        }
        m = STRPTIME_FECHA_ISO.matcher(texto);
        if (m.matches()) {
            try {
                LocalDateTime dt = LocalDateTime.of(anio(m.group(1)), entero(m.group(2)), entero(m.group(3)), 0, 0);
                return dt.atZone(Referencia.zona(zona)).toInstant().toEpochMilli();
            } catch (DateTimeException e) {
                // sin más formatos
            }
        }
        throw new RenderError("fecha de fixture inválida: " + texto);
    }

    private static int anio(String digitos) {
        int a = entero(digitos);
        if (a < 1) {
            throw new DateTimeException("año fuera de rango");
        }
        return a;
    }

    /** receivedDate: marca AAAAMMDDhhmmss±hhmm del nombre del archivo, en epoch ms. */
    static long fechaRecepcion(String archivo) {
        Matcher m = MARCA_TIEMPO.matcher(archivo);
        if (!m.find()) {
            throw new RenderError("nombre de fixture sin marca de tiempo: " + archivo);
        }
        Matcher p = STRPTIME_RECEPCION.matcher(m.group(1) + m.group(2));
        if (!p.matches()) {
            throw new IllegalArgumentException("marca de tiempo no reconocida: " + m.group(1) + m.group(2));
        }
        int signo = p.group(7).equals("-") ? -1 : 1;
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(signo * entero(p.group(8)), signo * entero(p.group(9)));
        OffsetDateTime dt = OffsetDateTime.of(anio(p.group(1)), entero(p.group(2)), entero(p.group(3)),
                entero(p.group(4)), entero(p.group(5)), entero(p.group(6)), 0, offset);
        return dt.toInstant().toEpochMilli();
    }

    /** Eventos TaggedValidatedEvent sintéticos (forma de 2404, campos que usa Curation). */
    public static List<Map<String, Object>> construirEventos(Map<String, Object> spec, Map<String, Object> fixture) {
        Map<String, Object> fuente = map(spec.get("fuente"));
        List<Map<String, Object>> eventos = new ArrayList<>();
        for (Object o : list(fixture.get("eventos"))) {
            Map<String, Object> e = map(o);
            Map<String, Object> registro = new LinkedHashMap<>(map(fixture.get("base_registro")));
            if (Py.truthy(e.get("registro"))) {
                registro.putAll(map(e.get("registro")));
            }
            String cns = (String) registro.get("FECHA_ACTUALIZACION_CNS");
            long recibido = fechaRecepcion((String) e.get("archivo"));
            Map<String, Object> lineage = new LinkedHashMap<>();
            lineage.put("dataSource", fuente.get("datasource"));
            lineage.put("receivedDate", recibido);
            lineage.put("fileName", e.get("archivo"));
            lineage.put("subscriber", fuente.get("subscriber"));
            lineage.put("fileAsOfDate", recibido);
            lineage.put("datasetId", "ds-sintetico-0001");
            lineage.put("recordHash", "hash-" + Py.str(e.get("id")));
            List<Object> errorCodes = new ArrayList<>();
            for (Object err : list(e.get("errores"))) {
                Map<String, Object> ec = new LinkedHashMap<>();
                ec.put("errorCodeName", map(err).get("codigo"));
                ec.put("errorCodeValue", map(err).get("codigo"));
                ec.put("errorLevel", map(err).get("nivel"));
                ec.put("messageKey", "");
                ec.put("errorType", "event");
                errorCodes.add(ec);
            }
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("lineage", lineage);
            ev.put("eventId", e.get("id"));
            ev.put("validatedRecordJson", compacto(registro));
            ev.put("payload", new LinkedHashMap<>(Map.of("fixture", "SINTETICO")));
            Map<String, Object> qs = new LinkedHashMap<>();
            qs.put("name", "RQS");
            qs.put("score", 1.0);
            ev.put("qualityScores", List.of(qs));
            ev.put("errorCodes", errorCodes);
            ev.put("eventType", fuente.get("output_event_type"));
            // Guard-rails de Validation (D3): As-Of-Date = FECHA_ACTUALIZACION_CNS en la zona de la etapa anterior
            // (parametros.zona_asofdate_etapa_anterior, por defecto America/Bogota; ADR-G4).
            ev.put("asOfDate", epochMs(cns, zonaAsOfDate(spec)));
            ev.put("subscriberRelation", ((String) registro.get("NUM_DOCUMENTO")).strip());
            ev.put("subscriber", fuente.get("subscriber"));
            ev.put("headerErrors", List.of());
            ev.put("footerErrors", List.of());
            ev.put("keys", "");
            ev.put("fileTag", "");
            ev.put("_caso", e.get("caso"));
            eventos.add(ev);
        }
        return eventos;
    }

    static Map<String, Object> sinMeta(Map<String, Object> evento) {
        Map<String, Object> out = new LinkedHashMap<>();
        evento.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** Eventos curados y resumen de la salida esperada del fixture. */
    public record Salida(List<Map<String, Object>> curados, Map<String, Object> resumen) {
    }

    public static Salida salidaEsperada(Map<String, Object> spec, List<Map<String, Object>> eventos) {
        List<Map<String, Object>> curados = new ArrayList<>();
        List<Object> descartados = new ArrayList<>();
        for (Map<String, Object> ev : eventos) {
            Map<String, Object> r = Referencia.curarEvento(sinMeta(ev), spec);
            if (r == null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("eventId", ev.get("eventId"));
                d.put("fileName", map(ev.get("lineage")).get("fileName"));
                d.put("motivo", "sin processor para lineage.fileName (CUR-AP02, descarte sin log)");
                descartados.add(d);
            } else {
                curados.add(r);
            }
        }
        String campoAdv = (String) map(spec.get("parametros")).get("campo_advertencias");
        List<Object> cc = list(spec.get("campos_curados"));
        Map<String, Object> advertencias = new LinkedHashMap<>();
        Map<String, Object> omitidas = new LinkedHashMap<>();
        Map<String, Object> homologacion = new LinkedHashMap<>();
        int conAdvertencias = 0;
        for (Map<String, Object> c : curados) {
            Map<String, Object> rec = map(c.get("curatedRecordJson"));
            String id = Py.str(c.get("eventId"));
            if (Py.truthy(rec.get(campoAdv))) {
                conAdvertencias++;
                advertencias.put(id, rec.get(campoAdv));
            }
            List<Object> faltan = cc.stream().map(m -> map(m).get("destino")).filter(d -> !rec.containsKey(d))
                    .collect(Collectors.toList());
            if (!faltan.isEmpty()) {
                omitidas.put(id, faltan);
            }
            Map<String, Object> entrada = eventos.stream().filter(e -> e.get("eventId").equals(c.get("eventId")))
                    .findFirst().orElseThrow();
            Object tip = map(Referencia.leerJson((String) entrada.get("validatedRecordJson"))).get("TIP_DOCUMENTO");
            homologacion.put(id, java.util.Arrays.asList(tip, rec.get("tipoDocumento")));
        }
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("sintetico", true);
        resumen.put("eventos_entrada", eventos.size());
        resumen.put("eventos_curados", curados.size());
        resumen.put("eventos_descartados_por_motor", descartados);
        resumen.put("eventos_con_advertencias", conAdvertencias);
        resumen.put("advertencias", advertencias);
        resumen.put("claves_omitidas_por_severidad", omitidas);
        resumen.put("homologacion_tipo_documento", homologacion);
        return new Salida(curados, resumen);
    }

    // ---------------------------------------------------------------- huecos

    static String mapeoScala(Map<String, Object> m, boolean ultimo) {
        Map<String, Object> t = map(m.get("transformacion"));
        String tipo = (String) t.get("tipo");
        String tr;
        if (TRANSF_SCALA.containsKey(tipo)) {
            tr = TRANSF_SCALA.get(tipo);
        } else if (tipo.equals("padding_izquierda")) {
            tr = "Transformacion.PaddingIzquierda(" + Py.toInt(t.get("longitud")) + ")";
        } else if (tipo.equals("desde_archivo")) {
            tr = "Transformacion.DesdeArchivo(" + q(t.get("atributo")) + ")";
        } else if (tipo.equals("copia_de")) {
            tr = "Transformacion.CopiaDe(" + q(t.get("campo")) + ")";
        } else {
            tr = t.get("valor") == null ? "Transformacion.Constante(None)"
                    : "Transformacion.Constante(Some(" + q(t.get("valor")) + "))";
        }
        String salida = "TipoSalida." + TIPO_SALIDA_SCALA.get(m.get("tipo_salida"));
        Object cat = m.get("catalogo");
        String catS = Py.truthy(cat) ? "Some(Catalogo(" + q(map(cat).get("tabla")) + ", " + q(map(cat).get("columna")) + "))" : "None";
        Object estado = m.get("estado");
        // AC-35 (2026-10-21): toda marca ASSUMPTION lleva un ID de decisiones-y-abiertos.md; el supuesto de un
        // FieldMapping es el nombre publicado (C-CUR-02).
        String marca = "cerrado".equals(estado) ? ""
                : "  // " + ("supuesto".equals(estado) ? "ASSUMPTION: C-CUR-02 (supuesto)" : Py.str(estado));
        String coma = ultimo ? "" : ",";
        return "    FieldMapping(" + q(m.get("destino")) + ", " + q(m.get("origen")) + ", " + tr + ", " + salida + ", "
                + catS + ")" + coma + marca;
    }

    static String reglaScala(Map<String, Object> r) {
        String pad = Py.truthy(r.get("padding")) ? "Some(" + Py.toInt(r.get("padding")) + ")" : "None";
        return "ReglaDocumento(quitarCerosIzquierda = " + Py.str(r.get("quitar_ceros_izquierda")).toLowerCase()
                + ", soloDigitos = " + Py.str(r.get("solo_digitos")).toLowerCase()
                + ", conservarOriginal = " + Py.str(r.get("conservar_original")).toLowerCase()
                + ", padding = " + pad + ")";
    }

    static String mapaScala(Map<?, ?> d, String sangria, boolean porLinea) {
        List<String> pares = d.entrySet().stream().map(e -> q(e.getKey()) + " -> " + q(e.getValue()))
                .collect(Collectors.toList());
        return porLinea ? pares.stream().map(p -> sangria + p).collect(Collectors.joining(",\n")) : String.join(", ", pares);
    }

    static String listaScala(List<?> xs) {
        return xs.stream().map(RenderTemplates::q).collect(Collectors.joining(", "));
    }

    static Object get(Map<String, Object> d, String clave, Object porDefecto) {
        return d.containsKey(clave) ? d.get(clave) : porDefecto;
    }

    static String supuestos(Map<String, Object> spec) {
        List<String> lineas = new ArrayList<>();
        for (Object o : list(spec.get("campos_curados"))) {
            Map<String, Object> m = map(o);
            if (!"cerrado".equals(m.get("estado"))) {
                lineas.add("//   - " + m.get("destino") + ": " + Py.str(m.get("estado")));
            }
        }
        lineas.add("//   - paquete compartido: " + Py.str(get(map(spec.get("paquetes")), "compartido_estado", "cerrado")) + " (CUR-PT01)");
        lineas.add("//   - reglas de documento: " + Py.str(get(map(spec.get("reglas_documento")), "estado", "cerrado")) + " (RF-CUR-08)");
        lineas.add("//   - outputEventType: " + Py.str(get(map(spec.get("fuente")), "output_event_type_estado", "cerrado")) + " (CUR-PA04)");
        lineas.add("//   - convención de nombre de archivo: " + Py.str(get(map(spec.get("archivo")), "estado", "cerrado")) + " (D8)");
        return String.join("\n", lineas);
    }

    static String abiertos(Map<String, Object> spec) {
        List<Object> items = list(spec.get("campos_abiertos"));
        if (items.isEmpty()) {
            return "  // Sin campos abiertos.";
        }
        List<String> lineas = new ArrayList<>();
        lineas.add("  // Campos pedidos por la frontera que NO se generan hasta cerrar la decisión:");
        items.forEach(a -> lineas.add("  //   - " + map(a).get("destino") + ": " + map(a).get("motivo")));
        return String.join("\n", lineas);
    }

    static String lookupsHocon(Map<String, Object> spec) {
        List<String> bloques = new ArrayList<>();
        for (Object o : list(spec.get("catalogos"))) {
            Map<String, Object> c = map(o);
            if (!Py.truthy(c.get("registrar_en_curation"))) {
                continue;
            }
            List<String> claves = list(c.get("claves")).stream().map(Py::str).collect(Collectors.toList());
            bloques.add(String.join("\n",
                    "    {",
                    "      tableName = " + q(c.get("tabla")),
                    "      file = ${oxygen.application.paths.lookupHome}" + q("/" + c.get("archivo")),
                    "      delimiter = " + q(c.get("delimitador")),
                    "      keys = [",
                    "        { index = " + q(String.join("+", claves)) + " }",
                    "      ]",
                    "    }"));
        }
        if (bloques.isEmpty()) {
            throw new RenderError("ningún catálogo con registrar_en_curation = true");
        }
        return String.join(",\n", bloques);
    }

    static String domainDictionary(Map<String, Object> spec) {
        List<String> filas = new ArrayList<>();
        for (Object o : list(spec.get("campos_curados"))) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", map(o).get("destino"));
            f.put("type", DD_TIPO.get(map(o).get("tipo_salida")));
            f.put("nullable", true);
            filas.add("    " + Py.jsonDumps(f));
        }
        Map<String, Object> adv = new LinkedHashMap<>();
        adv.put("name", map(spec.get("parametros")).get("campo_advertencias"));
        adv.put("type", "array<string>");
        adv.put("nullable", false);
        filas.add("    " + Py.jsonDumps(adv));
        return String.join(",\n", filas);
    }

    // ---------------------------------------------------------------- subscriberRelation (F-FW-11, D14)

    /** Miembro override del processor; sin la extensión en la spec, solo un comentario (el hueco no puede quedar vacío). */
    static String relacionOverrideScala(Map<String, Object> spec) {
        Object ext = spec.get("extension_subscriber_relation");
        if (ext == null) {
            return "  // subscriberRelation no se sobrescribe: se copia del evento aceptado (D3).";
        }
        String clase = (String) map(spec.get("processor")).get("nombre_clase");
        String componentes = list(map(ext).get("campos")).stream().map(Py::str)
                .collect(Collectors.joining(Py.str(map(ext).get("separador"))));
        return String.join("\n",
                "  /**",
                "   * subscriberRelation = " + componentes + " (extension_subscriber_relation). Usa el mismo núcleo que",
                "   * curateEvent; si falta algún componente conserva el del evento aceptado. Nunca lanza.",
                "   * ASSUMPTION: D14 — firma del gancho CurationProcessor.subscriberRelation sin verificar contra el JAR (F-01).",
                "   */",
                "  override def subscriberRelation(event: TaggedValidatedEvent): String = {",
                "    val errores = Option(event.errorCodes).map(_.toSeq).getOrElse(Seq.empty)",
                "      .map(e => (e.errorCodeValue, e.errorLevel))",
                "    val fileName = Option(event.lineage).map(_.fileName).orNull",
                "    " + clase + ".relacion(event.validatedRecordJson, fileName, errores, homologacion, event.subscriberRelation)",
                "  }");
    }

    /** Núcleo puro en el objeto del processor: se prueba sin Spark. */
    static String relacionHelperScala(Map<String, Object> spec) {
        Object ext = spec.get("extension_subscriber_relation");
        if (ext == null) {
            return "  // subscriberRelation: sin extension_subscriber_relation en la spec (D3).";
        }
        return String.join("\n",
                "  /** Campos curados que forman subscriberRelation y su separador (extension_subscriber_relation, D14). */",
                "  private val CamposRelacion: Seq[String] = Seq(" + listaScala(list(map(ext).get("campos"))) + ")",
                "  private val SeparadorRelacion: String = " + q(map(ext).get("separador")),
                "",
                "  /** subscriberRelation = campos curados unidos; si falta alguno, el del evento aceptado (`copiado`). Nunca lanza. */",
                "  def relacion(json: String, fileName: String, errores: Seq[(String, String)], homologacion: Map[String, String],",
                "               copiado: String): String =",
                "    try {",
                "      val m = curar(json, fileName, errores, homologacion).toJavaMap",
                "      val partes = CamposRelacion.map(c => Option(m.get(c)).map(_.toString.trim).filter(_.nonEmpty))",
                "      if (partes.forall(_.isDefined)) partes.flatten.mkString(SeparadorRelacion) else copiado",
                "    } catch { case NonFatal(_) => copiado }");
    }

    /** Pruebas de subscriberRelation sobre el fixture; el valor esperado sale de {@link Referencia#subscriberRelation}. */
    static String casosRelacionScala(Map<String, Object> spec, List<Map<String, Object>> eventos, List<Map<String, Object>> curados) {
        if (spec.get("extension_subscriber_relation") == null) {
            return "  // Sin extension_subscriber_relation: subscriberRelation se copia del evento aceptado.";
        }
        String clase = (String) map(spec.get("processor")).get("nombre_clase");
        String fuente = (String) map(spec.get("fuente")).get("Fuente");
        Map<Object, Map<String, Object>> porId = new LinkedHashMap<>();
        curados.forEach(c -> porId.put(c.get("eventId"), c));
        List<String> lineas = new ArrayList<>();
        lineas.add("  test(\"subscriberRelation: campos curados unidos; si falta alguno, el del evento aceptado\") {");
        for (Map<String, Object> ev : eventos) {
            Map<String, Object> c = porId.get(ev.get("eventId"));
            if (c == null) {
                continue;
            }
            String errores = list(ev.get("errorCodes")).stream()
                    .map(e -> "(" + q(map(e).get("errorCodeValue")) + ", " + q(map(e).get("errorLevel")) + ")")
                    .collect(Collectors.joining(", "));
            String esperado = Referencia.subscriberRelation(map(c.get("curatedRecordJson")), spec, "COPIADO");
            lineas.add("    assert(" + clase + ".relacion(" + tq((String) ev.get("validatedRecordJson")) + ", "
                    + q(map(ev.get("lineage")).get("fileName")) + ", Seq(" + errores + "), " + fuente
                    + "CurationSpec.Homologacion, \"COPIADO\") == " + q(esperado) + ")  // " + Py.str(ev.get("eventId")));
        }
        lineas.add("  }");
        return String.join("\n", lineas);
    }

    static String casosScala(List<Map<String, Object>> eventos, List<Map<String, Object>> curados) {
        Map<Object, Map<String, Object>> porId = new LinkedHashMap<>();
        curados.forEach(c -> porId.put(c.get("eventId"), c));
        List<String> bloques = new ArrayList<>();
        for (Map<String, Object> ev : eventos) {
            Map<String, Object> c = porId.get(ev.get("eventId"));
            if (c == null) {
                continue; // descartado por el motor: se documenta en el resumen, no aplica al núcleo
            }
            String errores = list(ev.get("errorCodes")).stream()
                    .map(e -> "(" + q(map(e).get("errorCodeValue")) + ", " + q(map(e).get("errorLevel")) + ")")
                    .collect(Collectors.joining(", "));
            bloques.add(String.join("\n",
                    "  test(" + q(Py.str(ev.get("eventId")) + ": " + Py.str(ev.get("_caso"))) + ") {",
                    "    val esperado = " + tq(compacto(c.get("curatedRecordJson"))),
                    "    assert(curarJson(" + tq((String) ev.get("validatedRecordJson")) + ", "
                            + q(map(ev.get("lineage")).get("fileName")) + ", Seq(" + errores + ")) == esperado)",
                    "  }"));
        }
        return String.join("\n\n", bloques);
    }

    /** Valor (perezoso) de cada hueco declarado en x-placeholders. */
    public static Map<String, Supplier<Object>> huecos(Map<String, Object> spec, Map<String, Object> fixture,
                                                       List<Map<String, Object>> eventos, List<Map<String, Object>> curados) {
        Map<String, Object> f = map(spec.get("fuente"));
        Map<String, Object> p = map(spec.get("parametros"));
        Map<String, Object> a = map(spec.get("archivo"));
        Map<String, Object> hom = map(spec.get("homologacion_tipo_documento"));
        Map<String, Object> rd = map(spec.get("reglas_documento"));
        List<Object> cc = list(spec.get("campos_curados"));
        Map<String, Object> paquetes = map(spec.get("paquetes"));
        Map<String, Object> processor = map(spec.get("processor"));
        Map<String, Object> rutas = map(spec.get("rutas_destino"));
        Object adv = p.get("campo_advertencias");
        Map<String, Supplier<Object>> v = new LinkedHashMap<>();
        v.put("version_skill", () -> map(spec.get("meta")).get("version_skill"));
        v.put("Fuente", () -> f.get("Fuente"));
        v.put("fuente", () -> f.get("fuente"));
        v.put("output_event_type", () -> Py.str(f.get("output_event_type")));
        v.put("paquete_fuente", () -> paquetes.get("fuente"));
        v.put("paquete_compartido", () -> paquetes.get("compartido"));
        v.put("processor_clase", () -> processor.get("nombre_clase"));
        v.put("processor_fqcn", () -> paquetes.get("fuente") + ".processor." + processor.get("nombre_clase"));
        v.put("file_pattern_hocon", () -> ((String) a.get("file_pattern")).replace("\\", "\\\\").replace("\"", "\\\""));
        v.put("regex_derivacion_scala", () -> a.get("regex_derivacion"));
        v.put("tipo_por_prefijo_scala", () -> mapaScala(map(a.get("tipo_por_prefijo")), "", false));
        v.put("archivo_base", () -> list(a.get("ejemplos_sinteticos")).get(0));
        v.put("zona_fechas", () -> p.get("zona_horaria_fechas"));
        v.put("formatos_fecha_scala", () -> listaScala(list(p.get("formatos_fecha"))));
        v.put("conservar_enie", () -> Py.str(p.get("conservar_enie")).toLowerCase());
        v.put("niveles_no_almacenar_scala", () -> listaScala(list(p.get("niveles_no_almacenar"))));
        v.put("niveles_anular_scala", () -> listaScala(list(p.get("niveles_anular"))));
        v.put("campo_advertencias", () -> adv);
        v.put("campos_entrada_scala", () -> listaScala(list(spec.get("campos_entrada"))));
        v.put("codigos_error_scala", () -> mapaScala(map(spec.get("codigos_error")), "    ", true));
        v.put("tabla_homologacion", () -> map(hom.get("catalogo")).get("tabla"));
        v.put("columna_clave_homologacion", () -> map(hom.get("catalogo")).get("columna_clave"));
        v.put("columna_valor_homologacion", () -> map(hom.get("catalogo")).get("columna_valor"));
        v.put("homologacion_scala", () -> mapaScala(map(hom.get("tabla")), "", false));
        v.put("claves_homologacion_scala", () -> listaScala(new ArrayList<>(map(hom.get("tabla")).keySet())));
        v.put("pendientes_scala", () -> listaScala(list(hom.get("pendientes"))));
        v.put("campo_tipo_documento", () -> rd.get("campo_tipo"));
        v.put("regla_documento_defecto_scala", () -> reglaScala(map(rd.get("por_defecto"))));
        v.put("reglas_documento_scala", () -> map(rd.get("por_tipo")).entrySet().stream()
                .map(e -> "    " + q(e.getKey()) + " -> " + reglaScala(map(e.getValue()))).collect(Collectors.joining(",\n")));
        v.put("mapeos_scala", () -> {
            List<String> lineas = new ArrayList<>();
            for (int i = 0; i < cc.size(); i++) {
                lineas.add(mapeoScala(map(cc.get(i)), i == cc.size() - 1));
            }
            return String.join("\n", lineas);
        });
        v.put("claves_publicadas_scala", () -> {
            List<Object> claves = cc.stream().map(m -> map(m).get("destino")).collect(Collectors.toList());
            claves.add(adv);
            return listaScala(claves);
        });
        v.put("campos_record_scala", () -> cc.stream()
                .map(m -> "  " + map(m).get("destino") + ": Option[" + SCALA_TIPO.get(map(m).get("tipo_salida")) + "]")
                .collect(Collectors.joining(",\n")));
        v.put("campos_to_map_scala", () -> cc.stream()
                .map(m -> "    if (!omitidos(" + q(map(m).get("destino")) + ")) m.put(" + q(map(m).get("destino")) + ", "
                        + map(m).get("destino") + ".map(_.asInstanceOf[AnyRef]).orNull)")
                .collect(Collectors.joining("\n")));
        v.put("campos_from_map_scala", () -> cc.stream()
                .map(m -> "      " + map(m).get("destino") + " = valores.get(" + q(map(m).get("destino"))
                        + ").flatten.collect { case v: " + SCALA_TIPO.get(map(m).get("tipo_salida")) + " => v }")
                .collect(Collectors.joining(",\n")));
        v.put("campos_abiertos_comentario", () -> abiertos(spec));
        v.put("supuestos_comentario", () -> supuestos(spec));
        v.put("lookups_hocon_entradas", () -> lookupsHocon(spec));
        v.put("domain_dictionary_entradas", () -> domainDictionary(spec));
        v.put("eventos_jsonl", () -> eventos.stream().map(e -> compacto(sinMeta(e))).collect(Collectors.joining("\n")));
        v.put("casos_spec_scala", () -> casosScala(eventos, curados));
        v.put("evento_base_json_scala", () -> tq(compacto(fixture.get("base_registro"))));
        v.put("subscriber_relation_override_scala", () -> relacionOverrideScala(spec));
        v.put("subscriber_relation_helper_scala", () -> relacionHelperScala(spec));
        v.put("casos_relacion_scala", () -> casosRelacionScala(spec, eventos, curados));
        v.put("ruta_hocon", () -> rutas.get("hocon"));
        v.put("ruta_lookups_conf", () -> rutas.get("lookups_conf"));
        v.put("ruta_domain_dictionary", () -> rutas.get("domain_dictionary"));
        return v;
    }

    /** Entrada del plan: id de plantilla, archivo de plantilla y ruta de salida relativa a --out. */
    public record Paso(String id, String plantilla, String destino) {
    }

    /** Orden de curation_template.config. */
    public static List<Paso> plan(Map<String, Object> spec) {
        Map<String, Object> r = map(spec.get("rutas_destino"));
        String fuenteMay = (String) map(spec.get("fuente")).get("Fuente");
        String fuente = (String) map(spec.get("fuente")).get("fuente");
        String clase = (String) map(spec.get("processor")).get("nombre_clase");
        return List.of(
                new Paso("CUR-TPL-01", "FieldMapping.scala.tpl", r.get("raiz_compartido") + "FieldMapping.scala"),
                new Paso("CUR-TPL-02", "Transformations.scala.tpl", r.get("raiz_compartido") + "Transformaciones.scala"),
                new Paso("CUR-TPL-10", "TransformationsSpec.scala.tpl", r.get("raiz_tests_compartido") + "TransformacionesSpec.scala"),
                new Paso("CUR-TPL-03", "CurationSpec.scala.tpl", r.get("raiz_fuente") + "config/" + fuenteMay + "CurationSpec.scala"),
                new Paso("CUR-TPL-04", "CuratedEvent.scala.tpl", r.get("raiz_fuente") + fuenteMay + "CuratedEvent.scala"),
                new Paso("CUR-TPL-05", "AcceptedEventReader.scala.tpl", r.get("raiz_fuente") + fuenteMay + "AcceptedRecord.scala"),
                new Paso("CUR-TPL-08", "domain-dictionary.json.tpl",
                        "fragmentos/" + stem((String) r.get("domain_dictionary")) + "." + fuente + ".json"),
                new Paso("CUR-TPL-12", "accepted-events.json.tpl", r.get("test_resources") + "accepted-events.json"),
                new Paso("CUR-TPL-06", "CurationProcessor.scala.tpl", r.get("raiz_fuente") + "processor/" + clase + ".scala"),
                new Paso("CUR-TPL-11", "SourceProcessorSpec.scala.tpl", r.get("raiz_tests") + clase + "Spec.scala"),
                new Paso("CUR-TPL-07", "curation-block.conf.tpl", "fragmentos/" + stem((String) r.get("hocon")) + "." + fuente + ".conf"),
                new Paso("CUR-TPL-09", "lookups.conf.tpl",
                        "fragmentos/" + stem((String) r.get("lookups_conf")) + "." + fuente + ".conf"));
    }

    static String rellenar(String texto, Map<String, Supplier<Object>> valores, Set<String> declarados, String nombre) {
        Set<String> usados = new TreeSet<>();
        Matcher m = HUECO.matcher(texto);
        while (m.find()) {
            usados.add(m.group(1));
        }
        Set<String> sinDeclarar = new TreeSet<>(usados);
        sinDeclarar.removeAll(declarados);
        if (!sinDeclarar.isEmpty()) {
            throw new RenderError(nombre + ": huecos no declarados en x-placeholders: " + Py.str(new ArrayList<>(sinDeclarar)));
        }
        Set<String> sinValor = new TreeSet<>(usados);
        sinValor.removeAll(valores.keySet());
        if (!sinValor.isEmpty()) {
            throw new RenderError(nombre + ": huecos sin cálculo en el renderer: " + Py.str(new ArrayList<>(sinValor)));
        }
        Map<String, String> cache = new LinkedHashMap<>();
        usados.forEach(k -> cache.put(k, Py.str(valores.get(k).get())));
        List<String> vacios = cache.entrySet().stream()
                .filter(e -> e.getValue().strip().isEmpty() && !PUEDEN_QUEDAR_VACIOS.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (!vacios.isEmpty()) {
            throw new RenderError(nombre + ": huecos con valor vacío: " + Py.str(vacios));
        }
        String salida = HUECO.matcher(texto).replaceAll(r -> Matcher.quoteReplacement(cache.get(r.group(1))));
        if (salida.contains("{{") && HUECO.matcher(salida).find()) {
            throw new RenderError(nombre + ": quedaron huecos sin resolver");
        }
        return salida;
    }

    static String csvHomologacion(Map<String, Object> spec) {
        Map<String, Object> hom = map(spec.get("homologacion_tipo_documento"));
        Object tabla = map(hom.get("catalogo")).get("tabla");
        Map<String, Object> cat = list(spec.get("catalogos")).stream().map(Py::map)
                .filter(c -> c.get("tabla").equals(tabla)).findFirst().orElseThrow();
        String sep = (String) cat.get("delimitador");
        Map<String, Object> desc = hom.containsKey("descripciones") ? map(hom.get("descripciones")) : Map.of();
        List<String> filas = new ArrayList<>();
        filas.add(list(cat.get("columnas")).stream().map(Py::str).collect(Collectors.joining(sep)));
        map(hom.get("tabla")).forEach((k, v) ->
                filas.add(String.join(sep, Py.str(k), Py.str(v), desc.containsKey(k) ? Py.str(desc.get(k)) : "")));
        return String.join("\n", filas) + "\n";
    }

    // ---------------------------------------------------------------- principal

    static void borrarArbol(Path raiz) throws IOException {
        if (!Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> s = Files.walk(raiz)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(p);
            }
        }
    }

    /** SHA-256 en hexadecimal (minúsculas) de los bytes del archivo. */
    static String sha256(Path archivo) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Entrada de otros_archivos (ADR-G3): ruta relativa a --out y su SHA-256. */
    static Map<String, Object> otroArchivo(Path out, String ruta) throws IOException {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("ruta", ruta);
        o.put("sha256", sha256(out.resolve(ruta)));
        return o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> render(Path specPath, Path out, Path templates, Path schemaPath, Path writeExpected)
            throws IOException {
        Map<String, Object> spec = cargarYaml(specPath);
        Map<String, Object> schema = cargarYaml(schemaPath);
        validarSpec(spec, schema);
        Object ph = schema.get("x-placeholders");
        Set<String> declarados = new TreeSet<>(ph instanceof Map ? ((Map<String, Object>) ph).keySet()
                : list(ph).stream().map(x -> (String) x).collect(Collectors.toList()));
        Map<String, Object> fixtureSpec = map(spec.get("fixture"));
        Path fixturePath = specPath.getParent().resolve((String) fixtureSpec.get("entrada")).toAbsolutePath().normalize();
        Map<String, Object> fixture = cargarYaml(fixturePath);
        List<Map<String, Object>> eventos = construirEventos(spec, fixture);
        Salida salida = salidaEsperada(spec, eventos);
        Map<String, Supplier<Object>> valores = huecos(spec, fixture, eventos, salida.curados());
        Set<String> faltan = new TreeSet<>(declarados);
        faltan.removeAll(valores.keySet());
        if (!faltan.isEmpty()) {
            throw new RenderError("x-placeholders sin cálculo en el renderer: " + Py.str(new ArrayList<>(faltan)));
        }

        borrarArbol(out);
        Map<String, Object> meta = map(spec.get("meta"));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("skill", "edf-curation-stage");
        manifest.put("version", meta.get("version_skill"));
        manifest.put("compilacion", meta.get("compilacion"));
        manifest.put("sintetico", true);
        List<Object> archivos = new ArrayList<>();
        manifest.put("archivos", archivos);
        for (Paso paso : plan(spec)) {
            String contenido = rellenar(leerTexto(templates.resolve(paso.plantilla())), valores, declarados, paso.plantilla());
            escribir(out.resolve(paso.destino()), contenido);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("plantilla", paso.id());
            a.put("archivo", paso.destino());
            a.put("fusionar", paso.destino().startsWith("fragmentos/"));
            archivos.add(a);
        }

        // Lookups sintéticos de prueba (CUR-TPL-12 complementa): 202 desde el spec, el resto copiado.
        Map<String, Object> rutas = map(spec.get("rutas_destino"));
        Path lkOut = out.resolve((String) rutas.get("test_resources")).resolve("lookups");
        Files.createDirectories(lkOut);
        Path lkIn = specPath.getParent().resolve((String) fixtureSpec.get("entrada")).getParent()
                .resolve((String) fixtureSpec.get("lookups_dir"));
        Object tablaHom = map(map(spec.get("homologacion_tipo_documento")).get("catalogo")).get("tabla");
        List<String> otras = new ArrayList<>();
        for (Object o : list(spec.get("catalogos"))) {
            Map<String, Object> c = map(o);
            String nombre = nombreArchivo((String) c.get("archivo"));
            otras.add(rutas.get("test_resources") + "lookups/" + nombre);
            if (c.get("tabla").equals(tablaHom)) {
                escribir(lkOut.resolve(nombre), csvHomologacion(spec));
            } else if (Files.exists(lkIn.resolve(nombre))) {
                Files.copy(lkIn.resolve(nombre), lkOut.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RenderError("falta el lookup sintético " + nombre);
            }
        }
        // AC-30 (plan #9, 2026-10-21): marca de sintético de los lookups entregados al fork. Va en un archivo
        // aparte: un comentario dentro del CSV podría romper la carga de OxygenLookup (lee con cabecera).
        if (!Files.exists(lkIn.resolve(MARCA_LOOKUPS))) {
            throw new RenderError("falta la marca de sintético de los lookups: " + fixtureSpec.get("lookups_dir") + "/"
                    + MARCA_LOOKUPS);
        }
        Files.copy(lkIn.resolve(MARCA_LOOKUPS), lkOut.resolve(MARCA_LOOKUPS), StandardCopyOption.REPLACE_EXISTING);
        manifest.put("marcas_sintetico", List.of(rutas.get("test_resources") + "lookups/" + MARCA_LOOKUPS));

        Path exp = out.resolve("expected");
        escribir(exp.resolve("curated-events.json"), Py.jsonDumps(salida.curados(), 2) + "\n");
        escribir(exp.resolve("resumen.json"), Py.jsonDumps(salida.resumen(), 2) + "\n");
        // AC-25 (ADR-G3, 2026-10-21): el manifest cubre todo lo generado. archivos (plantillas) y marcas_sintetico
        // no cambian; los CSV de lookups y expected/ van con su hash. MANIFEST.json no se lista a sí mismo.
        otras.add("expected/curated-events.json");
        otras.add("expected/resumen.json");
        List<Object> otrosArchivos = new ArrayList<>();
        for (String ruta : otras) {
            otrosArchivos.add(otroArchivo(out, ruta));
        }
        manifest.put("otros_archivos", otrosArchivos);
        // AC-CUR-10 (ADR-G4): valor y origen de la zona; advertencia si difiere de la de asOfDate (run() la escribe
        // en stderr).
        Map<String, Object> zona = new LinkedHashMap<>();
        zona.put("valor", map(spec.get("parametros")).get("zona_horaria_fechas"));
        zona.put("origen", origenZonaFechas(spec));
        manifest.put("zona_horaria_fechas", zona);
        manifest.put("advertencias", new ArrayList<Object>(advertenciasZona(spec)));
        if (writeExpected != null) {
            Files.createDirectories(writeExpected);
            for (String nombre : List.of("curated-events.json", "resumen.json")) {
                Files.copy(exp.resolve(nombre), writeExpected.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        escribir(out.resolve("MANIFEST.json"), Py.jsonDumps(manifest, 2) + "\n");
        return manifest;
    }

    private static final String USO = "uso: render-templates [--spec SPEC] [--out OUT] [--templates TEMPLATES] "
            + "[--schema SCHEMA] [--write-expected DIR]";

    /** Ejecuta la herramienta: 0 bien; 1 spec inválida o error de generación; 2 error de uso. */
    public static int run(String[] argv, PrintStream out, PrintStream err) {
        Path spec = SKILL_ROOT.resolve("assets/examples/sisben/source-spec.sisben.yaml");
        Path outDir = SKILL_ROOT.resolve("assets/examples/sisben/generated");
        Path templates = SKILL_ROOT.resolve("assets/templates");
        Path schema = SKILL_ROOT.resolve("assets/schema/source-spec.schema.yaml");
        Path writeExpected = null;
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (i + 1 >= argv.length || !List.of("--spec", "--out", "--templates", "--schema", "--write-expected").contains(a)) {
                err.println(USO);
                err.println("argumento no reconocido o sin valor: " + a);
                return 2;
            }
            Path value = Path.of(argv[++i]);
            switch (a) {
                case "--spec" -> spec = value;
                case "--out" -> outDir = value;
                case "--templates" -> templates = value;
                case "--schema" -> schema = value;
                default -> writeExpected = value;
            }
        }
        Map<String, Object> manifest;
        try {
            manifest = render(spec, outDir, templates, schema, writeExpected);
        } catch (RenderError | IOException | UncheckedIOException e) {
            err.println("ERROR: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("ERROR inesperado: " + e);
            return 1;
        }
        list(manifest.get("advertencias")).forEach(err::println); // stderr: stdout sigue terminando en OK (ADR-G4)
        out.println("OK: " + list(manifest.get("archivos")).size() + " artefactos en " + outDir
                + " (compilación " + Py.str(manifest.get("compilacion")) + ")");
        return 0;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }
}
