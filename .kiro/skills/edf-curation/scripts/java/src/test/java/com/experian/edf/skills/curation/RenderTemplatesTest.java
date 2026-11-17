package com.experian.edf.skills.curation;

import static com.experian.edf.skills.curation.Py.list;
import static com.experian.edf.skills.curation.Py.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Renderizado: huecos declarados, rellenos, sin residuos; salida coherente y estable. */
class RenderTemplatesTest {

    static final Path SKILL = RenderTemplates.SKILL_ROOT;
    static final Path SPEC = SKILL.resolve("assets/examples/sisben/source-spec.sisben.yaml");
    static final Path SCHEMA = SKILL.resolve("assets/schema/source-spec.schema.yaml");
    static final Path TEMPLATES = SKILL.resolve("assets/templates");
    static final Path EXPECTED = SKILL.resolve("assets/examples/sisben/expected");
    static final Path GENERATED = SKILL.resolve("assets/examples/sisben/generated");
    static final String SCALA_FUENTE = "src/main/scala/com/experian/edf/oxygen/curation/source/sisben/";

    static Map<String, Object> spec;
    static Map<String, Object> schema;
    static Map<String, Object> manifest;

    @TempDir
    static Path shared;

    static Path out() {
        return shared.resolve("generated");
    }

    @BeforeAll
    static void generar() throws IOException {
        spec = RenderTemplates.cargarYaml(SPEC);
        schema = RenderTemplates.cargarYaml(SCHEMA);
        manifest = RenderTemplates.render(SPEC, out(), TEMPLATES, SCHEMA, null);
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> copia(Map<String, Object> base) {
        return (Map<String, Object>) deepCopy(base);
    }

    static Object deepCopy(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<Object, Object> c = new LinkedHashMap<>();
            m.forEach((k, v) -> c.put(k, deepCopy(v)));
            return c;
        }
        if (o instanceof List<?> l) {
            List<Object> c = new ArrayList<>();
            l.forEach(v -> c.add(deepCopy(v)));
            return c;
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    static Set<String> declarados() {
        return ((Map<String, Object>) schema.get("x-placeholders")).keySet();
    }

    // ------------------------------------------------------------------ huecos y plan

    @Test
    void todoHuecoDePlantillaEstaDeclaradoEnElEsquema() throws IOException {
        Set<String> usados = new HashSet<>();
        try (Stream<Path> s = Files.list(TEMPLATES)) {
            for (Path tpl : s.filter(p -> p.toString().endsWith(".tpl")).collect(Collectors.toList())) {
                Matcher m = RenderTemplates.HUECO.matcher(read(tpl));
                while (m.find()) {
                    usados.add(m.group(1));
                }
            }
        }
        assertTrue(declarados().containsAll(usados), "sin declarar");
        assertTrue(usados.containsAll(declarados()), "declarados sin uso");
    }

    @Test
    void todoHuecoDeclaradoSeRellenaDesdeElSpecSisben() {
        Map<String, Object> fixture = RenderTemplates.cargarYaml(SPEC.getParent().resolve((String) map(spec.get("fixture")).get("entrada")));
        List<Map<String, Object>> eventos = RenderTemplates.construirEventos(spec, fixture);
        RenderTemplates.Salida salida = RenderTemplates.salidaEsperada(spec, eventos);
        Map<String, Supplier<Object>> valores = RenderTemplates.huecos(spec, fixture, eventos, salida.curados());
        for (String nombre : declarados()) {
            assertTrue(valores.containsKey(nombre), nombre);
            assertFalse(Py.str(valores.get(nombre).get()).isBlank(), "hueco vacío: " + nombre);
        }
    }

    @Test
    void specValidaContraEsquema() {
        RenderTemplates.validarSpec(spec, schema);
    }

    @Test
    void plantillasInventarioCompleto() {
        Set<String> ids = RenderTemplates.plan(spec).stream().map(RenderTemplates.Paso::id).collect(Collectors.toCollection(TreeSet::new));
        Set<String> esperados = new TreeSet<>();
        for (int i = 1; i <= 12; i++) {
            esperados.add(String.format("CUR-TPL-%02d", i));
        }
        assertEquals(esperados, ids);
        RenderTemplates.plan(spec).forEach(p -> assertTrue(Files.exists(TEMPLATES.resolve(p.plantilla())), p.plantilla()));
    }

    // ------------------------------------------------------------------ salida

    @Test
    void sinHuecosSinResolver() throws IOException {
        assertEquals(12, list(manifest.get("archivos")).size());
        try (Stream<Path> s = Files.walk(out())) {
            for (Path f : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                assertFalse(read(f).contains("{{"), f.toString());
            }
        }
    }

    static Map<String, String> arbol(Path root) throws IOException {
        Map<String, String> m = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                m.put(root.relativize(f).toString().replace('\\', '/'), read(f));
            }
        }
        return m;
    }

    /** generated/ y expected/ versionados = lo que produce hoy el renderer (sin deriva). */
    @Test
    void salidaIgualALaVersionada() throws IOException {
        assertEquals(arbol(GENERATED), arbol(out()));
        for (String nombre : List.of("curated-events.json", "resumen.json")) {
            assertEquals(read(EXPECTED.resolve(nombre)), read(out().resolve("expected").resolve(nombre)));
        }
    }

    /** Parse con Typesafe Config 1.3.4, el parser HOCON del motor. */
    @Test
    void hoconBloqueCuration() throws IOException {
        Config conf = ConfigFactory.parseString(read(out().resolve("fragmentos/curation-common-application-inner.sisben.conf")));
        List<? extends Config> cfgs = conf.getConfigList("oxygen.stage.curation.configs");
        assertEquals(1, cfgs.size());
        assertEquals(Set.of("filePattern", "processor"), cfgs.get(0).root().keySet());
        assertEquals("com.experian.edf.oxygen.curation.source.sisben.processor.SISBENCurationProcessor", cfgs.get(0).getString("processor"));
        Map<String, Object> archivo = map(spec.get("archivo"));
        assertEquals(archivo.get("file_pattern"), cfgs.get(0).getString("filePattern")); // el escape HOCON se deshace bien
        Pattern pat = Pattern.compile(cfgs.get(0).getString("filePattern"));
        list(archivo.get("ejemplos_sinteticos")).forEach(e -> assertTrue(pat.matcher((String) e).matches()));
        Set<String> prohibidas = Set.of("runMode", "partitionSize", "partionSize", "enableSSRWrite", "includeQualityCodes",
                "includeErrorCodes", "excludes");
        Set<String> claves = new HashSet<>(conf.getConfig("oxygen.stage.curation").root().keySet());
        claves.retainAll(prohibidas);
        assertEquals(Set.of(), claves);
    }

    @Test
    void hoconLookups() throws IOException {
        Config conf = ConfigFactory.parseString(read(out().resolve("fragmentos/oxygen-lookup-inner.sisben.conf")))
                .resolveWith(ConfigFactory.parseString("oxygen.application.paths.lookupHome = \"LH\""));
        List<? extends Config> lk = conf.getConfigList("oxygen.lookups");
        assertEquals(List.of("202_SISBEN_PROCESS_ID_TYPE_HO"), lk.stream().map(c -> c.getString("tableName")).collect(Collectors.toList()));
        assertEquals("LH/sisben/202_SISBEN_PROCESS_ID_TYPE_HO.csv", lk.get(0).getString("file"));
        assertEquals(List.of(Map.of("index", "tipo_sisben")), lk.get(0).getList("keys").unwrapped());
    }

    @Test
    void domainDictionaryCoincideConClavesDelEvento() throws IOException {
        ObjectMapper json = new ObjectMapper();
        JsonNode dd = json.readTree(read(out().resolve("fragmentos/colombia-domain-dictionary-inner.sisben.json")));
        List<String> nombres = new ArrayList<>();
        dd.get("fields").forEach(f -> nombres.add(f.get("name").asText()));
        assertEquals(nombres.size(), new HashSet<>(nombres).size());
        JsonNode curados = json.readTree(read(out().resolve("expected/curated-events.json")));
        for (JsonNode c : curados) {
            List<String> claves = new ArrayList<>();
            c.get("curatedRecordJson").fieldNames().forEachRemaining(claves::add);
            assertTrue(nombres.containsAll(claves));
        }
        List<String> primero = new ArrayList<>();
        curados.get(0).get("curatedRecordJson").fieldNames().forEachRemaining(primero::add);
        assertEquals(new HashSet<>(nombres), new HashSet<>(primero));
        String scala = read(out().resolve(SCALA_FUENTE + "config/SISBENCurationSpec.scala"));
        nombres.forEach(n -> assertTrue(scala.contains("\"" + n + "\""), n));
    }

    @Test
    void lookup202CoincideConLaTablaEmbebida() throws IOException {
        List<String> csv = List.of(read(out().resolve("src/test/resources/sisben/lookups/202_SISBEN_PROCESS_ID_TYPE_HO.csv")).split("\n"));
        Map<String, String> filas = new LinkedHashMap<>();
        csv.subList(1, csv.size()).forEach(l -> filas.put(l.split(",")[0], l.split(",")[1]));
        Map<String, String> tabla = new LinkedHashMap<>();
        map(map(spec.get("homologacion_tipo_documento")).get("tabla")).forEach((k, v) -> tabla.put(Py.str(k), Py.str(v)));
        assertEquals(tabla, filas);
        assertFalse(filas.containsKey("7")); // salvoconducto: pendiente, sin código arbitrario
    }

    @Test
    void fixtureEsJsonlDeEventosSinteticos() throws IOException {
        ObjectMapper json = new ObjectMapper();
        String[] lineas = read(out().resolve("src/test/resources/sisben/accepted-events.json")).strip().split("\n");
        assertEquals(8, lineas.length);
        for (String l : lineas) {
            JsonNode ev = json.readTree(l);
            assertEquals("SINTETICO", ev.get("payload").get("fixture").asText());
            json.readTree(ev.get("validatedRecordJson").asText());
        }
    }

    /** AC-30 (plan #9): los lookups que van al fork llevan la marca de sintético en un archivo aparte. */
    @Test
    void lookupsGeneradosMarcadosComoSinteticos() throws IOException {
        String rel = map(spec.get("rutas_destino")).get("test_resources") + "lookups/";
        Path lk = out().resolve(rel);
        String marca = read(lk.resolve(RenderTemplates.MARCA_LOOKUPS));
        assertTrue(marca.contains("SINTÉTICO"), "la marca debe decir SINTÉTICO");
        assertEquals(List.of(rel + RenderTemplates.MARCA_LOOKUPS), manifest.get("marcas_sintetico"));
        assertEquals(12, list(manifest.get("archivos")).size()); // la marca no es un artefacto de plantilla
        try (Stream<Path> s = Files.list(lk)) {
            List<Path> csv = s.filter(p -> p.toString().endsWith(".csv")).collect(Collectors.toList());
            assertEquals(list(spec.get("catalogos")).size(), csv.size());
            for (Path c : csv) { // sin comentarios dentro del CSV: OxygenLookup lo carga con cabecera
                assertFalse(read(c).lines().anyMatch(l -> l.startsWith("#")), c.toString());
            }
        }
    }

    @Test
    void sinMarcaDeLookupsSeRechaza(@TempDir Path tmp) throws IOException {
        Path fx = SPEC.getParent().resolve("fixture");
        Path copia = tmp.resolve("fixture");
        Files.createDirectories(copia.resolve("lookups"));
        Files.copy(fx.resolve("accepted-events.input.yaml"), copia.resolve("accepted-events.input.yaml"));
        try (Stream<Path> s = Files.list(fx.resolve("lookups"))) {
            for (Path p : s.filter(p -> p.toString().endsWith(".csv")).collect(Collectors.toList())) {
                Files.copy(p, copia.resolve("lookups").resolve(p.getFileName()));
            }
        }
        Files.copy(SPEC, tmp.resolve("spec.yaml"));
        RenderError e = assertThrows(RenderError.class,
                () -> RenderTemplates.render(tmp.resolve("spec.yaml"), tmp.resolve("out"), TEMPLATES, SCHEMA, null));
        assertTrue(e.getMessage().contains("marca de sintético"), e.getMessage());
    }

    /**
     * AC-25 (ADR-G3): todo archivo en disco, salvo MANIFEST.json, está en exactamente una de archivos,
     * marcas_sintetico u otros_archivos, y el sha256 de otros_archivos coincide con el del archivo.
     */
    @Test
    void manifestListaTodosLosArchivos() throws IOException {
        JsonNode m = new ObjectMapper().readTree(read(out().resolve("MANIFEST.json")));
        List<String> listados = new ArrayList<>();
        m.get("archivos").forEach(a -> listados.add(a.get("archivo").asText()));
        m.get("marcas_sintetico").forEach(a -> listados.add(a.asText()));
        Map<String, String> hashes = new TreeMap<>();
        for (JsonNode o : m.get("otros_archivos")) {
            listados.add(o.get("ruta").asText());
            hashes.put(o.get("ruta").asText(), o.get("sha256").asText());
        }
        assertEquals(listados.size(), new HashSet<>(listados).size(), "un archivo aparece en más de una lista");
        Set<String> disco = new TreeSet<>(arbol(out()).keySet());
        assertTrue(disco.remove("MANIFEST.json"));
        assertFalse(listados.contains("MANIFEST.json"), "MANIFEST.json no se lista a sí mismo");
        assertEquals(disco, new TreeSet<>(listados));
        for (Map.Entry<String, String> h : hashes.entrySet()) {
            assertEquals(RenderTemplates.sha256(out().resolve(h.getKey())), h.getValue(), h.getKey());
            assertTrue(h.getValue().matches("[0-9a-f]{64}"), h.getKey());
        }
        // SISBEN: 23 en disco = 12 + 1 + 9 + MANIFEST.json
        assertEquals(12, m.get("archivos").size());
        assertEquals(1, m.get("marcas_sintetico").size());
        assertEquals(list(spec.get("catalogos")).size() + 2, m.get("otros_archivos").size());
        assertEquals(23, disco.size() + 1);
    }

    /** Copia la spec SISBEN y su fixture a tmp, con zona_horaria_fechas reemplazada (mismo cambio que G-07). */
    static Path specConZona(Path tmp, String zona) throws IOException {
        Path fx = SPEC.getParent().resolve("fixture");
        try (Stream<Path> s = Files.walk(fx)) {
            for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path destino = tmp.resolve("fixture").resolve(fx.relativize(p).toString());
                Files.createDirectories(destino.getParent());
                Files.copy(p, destino);
            }
        }
        String texto = read(SPEC);
        String linea = "  zona_horaria_fechas: UTC ";
        assertTrue(texto.contains(linea));
        Path copia = tmp.resolve("spec.yaml");
        Files.writeString(copia, texto.replace(linea, "  zona_horaria_fechas: " + zona + " "), StandardCharsets.UTF_8);
        return copia;
    }

    static final String ADVERTENCIA_SISBEN = "ADVERTENCIA: zona_horaria_fechas (UTC) difiere de la zona de asOfDate de la "
            + "etapa anterior (America/Bogota) (C-CUR-04)";

    /** AC-CUR-10 (ADR-G4): con la spec SISBEN (UTC) la advertencia sale en stderr y en el manifest; stdout termina en OK. */
    @Test
    void zonaDistintaDeAsOfDateAdvierte(@TempDir Path tmp) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(0, cli(out, err, "--spec", SPEC.toString(), "--out", tmp.resolve("out").toString()));
        assertEquals(List.of(ADVERTENCIA_SISBEN), err.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList()));
        List<String> stdout = out.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());
        assertEquals(1, stdout.size());
        assertTrue(stdout.get(0).startsWith("OK: 12 artefactos") && stdout.get(0).endsWith("(compilación no_certificada)"));
        JsonNode m = new ObjectMapper().readTree(read(tmp.resolve("out/MANIFEST.json")));
        assertEquals(ADVERTENCIA_SISBEN, m.get("advertencias").get(0).asText());
        assertEquals(1, m.get("advertencias").size());
        assertEquals("UTC", m.get("zona_horaria_fechas").get("valor").asText());
        assertEquals("supuesto", m.get("zona_horaria_fechas").get("origen").asText());
        assertEquals(List.of(ADVERTENCIA_SISBEN), manifest.get("advertencias")); // también en render()
    }

    /** AC-CUR-10 (ADR-G4): con la zona de asOfDate (America/Bogota, como el mock de G-07) no hay advertencia. */
    @Test
    void zonaIgualNoAdvierte(@TempDir Path tmp) throws IOException {
        Path copia = specConZona(tmp, "America/Bogota");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(0, cli(out, err, "--spec", copia.toString(), "--out", tmp.resolve("out").toString()));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("OK: 12 artefactos"));
        JsonNode m = new ObjectMapper().readTree(read(tmp.resolve("out/MANIFEST.json")));
        assertEquals(0, m.get("advertencias").size());
        assertEquals("America/Bogota", m.get("zona_horaria_fechas").get("valor").asText());
        // asOfDate no depende de zona_horaria_fechas: es el mismo con UTC y con Bogotá
        JsonNode bogota = new ObjectMapper().readTree(read(tmp.resolve("out/expected/curated-events.json")));
        JsonNode utc = new ObjectMapper().readTree(read(EXPECTED.resolve("curated-events.json")));
        assertEquals(utc.get(0).get("asOfDate"), bogota.get(0).get("asOfDate"));
    }

    /** ADR-G4: los dos parámetros nuevos son opcionales; sin ellos rigen America/Bogota y supuesto. */
    @Test
    void parametrosDeZonaPorDefecto() {
        Map<String, Object> sin = copia(spec);
        map(sin.get("parametros")).remove("zona_asofdate_etapa_anterior");
        map(sin.get("parametros")).remove("zona_horaria_fechas_origen");
        RenderTemplates.validarSpec(sin, schema);
        assertEquals("America/Bogota", RenderTemplates.zonaAsOfDate(sin));
        assertEquals("supuesto", RenderTemplates.origenZonaFechas(sin));
        assertEquals(List.of(ADVERTENCIA_SISBEN), RenderTemplates.advertenciasZona(sin));
        map(sin.get("parametros")).put("zona_horaria_fechas", "America/Bogota");
        assertEquals(List.of(), RenderTemplates.advertenciasZona(sin));
        map(sin.get("parametros")).put("zona_horaria_fechas", "UTC");
        map(sin.get("parametros")).put("zona_asofdate_etapa_anterior", "Etc/UTC"); // misma zona con otro nombre
        assertEquals(List.of(), RenderTemplates.advertenciasZona(sin));
    }

    static final Pattern ID_DECISION = Pattern.compile("\\b(C-CUR-\\d{2}|CUR-P[AT]\\d{2}|F-\\d{2}|D\\d{1,2}(?:-[a-z])?)\\b");

    /**
     * AC-35: cada marca ASSUMPTION del código generado (plantillas y generador) cita al menos un ID, y cada ID
     * está en references/decisiones-y-abiertos.md.
     */
    @Test
    void todaMarcaAssumptionTieneIdEnDecisiones() throws IOException {
        String decisiones = read(SKILL.resolve("references/decisiones-y-abiertos.md"));
        Set<String> enDecisiones = new TreeSet<>();
        Matcher d = ID_DECISION.matcher(decisiones);
        while (d.find()) {
            enDecisiones.add(d.group(1));
        }
        int marcas = 0;
        List<String> sinId = new ArrayList<>();
        Set<String> huerfanos = new TreeSet<>();
        for (Map.Entry<String, String> f : arbol(out()).entrySet()) {
            for (String linea : f.getValue().split("\n")) {
                int i = linea.indexOf("ASSUMPTION");
                if (i < 0) {
                    continue;
                }
                marcas++;
                Matcher m = ID_DECISION.matcher(linea.substring(i));
                boolean alguno = false;
                while (m.find()) {
                    alguno = true;
                    if (!enDecisiones.contains(m.group(1))) {
                        huerfanos.add(m.group(1) + " en " + f.getKey());
                    }
                }
                if (!alguno) {
                    sinId.add(f.getKey() + ": " + linea.strip());
                }
            }
        }
        assertTrue(marcas >= 30, "marcas encontradas: " + marcas);
        assertEquals(List.of(), sinId, "marcas ASSUMPTION sin ID");
        assertEquals(Set.of(), huerfanos, "IDs que no están en decisiones-y-abiertos.md");
    }

    @Test
    void processorCumpleContrato() throws IOException {
        String p = read(out().resolve(SCALA_FUENTE + "processor/SISBENCurationProcessor.scala"));
        assertTrue(p.contains("class SISBENCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context)"));
        assertTrue(p.contains("override val outputEventType: Int = 30"));
        assertTrue(p.contains("override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload"));
        assertTrue(p.contains("@transient private lazy val logger"));
        assertFalse(p.contains("println(") || p.contains("override def subscriberRelation") || p.contains("throw "));
    }

    // ------------------------------------------------------------------ E2E sisben-e2e: F-FW-10, F-FW-11, F-FW-13, F-FW-14

    /** Copia la spec SISBEN y su fixture a tmp y escribe la spec con el cambio aplicado. */
    static Path specConCambios(Path tmp, Consumer<Map<String, Object>> cambio) throws IOException {
        Path fx = SPEC.getParent().resolve("fixture");
        try (Stream<Path> s = Files.walk(fx)) {
            for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path destino = tmp.resolve("fixture").resolve(fx.relativize(p).toString());
                Files.createDirectories(destino.getParent());
                Files.copy(p, destino);
            }
        }
        Map<String, Object> modificada = copia(spec);
        cambio.accept(modificada);
        Path copia = tmp.resolve("spec.yaml");
        Files.writeString(copia, new org.yaml.snakeyaml.Yaml().dump(modificada), StandardCharsets.UTF_8);
        return copia;
    }

    static Map<String, Object> extensionRelacion(List<String> campos, String separador) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("campos", new ArrayList<>(campos));
        ext.put("separador", separador);
        return ext;
    }

    /** F-FW-10: el formato ISO solo fecha valida contra el esquema y el fixture lo entiende (asOfDate). */
    @Test
    void formatoIsoSoloFechaEsValidoYElFixtureLoEntiende() {
        Map<String, Object> iso = copia(spec);
        map(iso.get("parametros")).put("formatos_fecha", new ArrayList<>(List.of("uuuu-MM-dd")));
        RenderTemplates.validarSpec(iso, schema);
        assertEquals(RenderTemplates.epochMs("31/01/2026", "America/Bogota"), RenderTemplates.epochMs("2026-01-31", "America/Bogota"));
        assertEquals(RenderTemplates.epochMs("2026-01-31 00:00:00", "UTC"), RenderTemplates.epochMs("2026-01-31", "UTC"));
        assertEquals(RenderTemplates.epochMs("2026-01-05", "UTC"), RenderTemplates.epochMs("2026-1-5", "UTC"));
        assertThrows(RenderError.class, () -> RenderTemplates.epochMs("2026-02-30", "UTC"));
        // Un formato fuera del enum se sigue rechazando.
        map(iso.get("parametros")).put("formatos_fecha", new ArrayList<>(List.of("uuuu/MM/dd")));
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.validarSpec(iso, schema)).getMessage().contains("inválido"));
    }

    @Test
    void generaConFormatoIsoYLoDeclaraEnLaSpecScala(@TempDir Path tmp) throws IOException {
        Path copia = specConCambios(tmp, s -> map(s.get("parametros")).put("formatos_fecha", new ArrayList<>(List.of("uuuu-MM-dd"))));
        RenderTemplates.render(copia, tmp.resolve("out"), TEMPLATES, SCHEMA, null);
        assertTrue(read(tmp.resolve("out/" + SCALA_FUENTE + "config/SISBENCurationSpec.scala"))
                .contains("val FormatosFecha: Seq[String] = Seq(\"uuuu-MM-dd\")"));
    }

    /** F-FW-13: pendientes vacío (y niveles vacíos) se generan como Set() y no son un hueco sin valor. */
    @Test
    void listasVaciasSeGeneranComoSetVacio(@TempDir Path tmp) throws IOException {
        Path copia = specConCambios(tmp, s -> {
            map(s.get("homologacion_tipo_documento")).put("pendientes", new ArrayList<>());
            map(s.get("parametros")).put("niveles_anular", new ArrayList<>());
            map(s.get("parametros")).put("niveles_no_almacenar", new ArrayList<>());
        });
        RenderTemplates.render(copia, tmp.resolve("out"), TEMPLATES, SCHEMA, null);
        String cs = read(tmp.resolve("out/" + SCALA_FUENTE + "config/SISBENCurationSpec.scala"));
        assertTrue(cs.contains("val Pendientes: Set[String] = Set()"));
        assertTrue(cs.contains("val NivelesAnular: Set[String] = Set()"));
        assertTrue(cs.contains("val NivelesNoAlmacenar: Set[String] = Set()"));
        // El resto de los huecos sigue sin poder quedar vacío.
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.rellenar("{{campo_advertencias}}",
                Map.of("campo_advertencias", () -> ""), Set.of("campo_advertencias"), "x.tpl")).getMessage().contains("valor vacío"));
    }

    /** F-FW-14: la salida esperada del ejemplo ya sale en mayúsculas y no cambia (la muestra no trae minúsculas). */
    @Test
    void documentoEnMayusculasNoCambiaLaSalidaDelEjemplo() throws IOException {
        JsonNode curados = new ObjectMapper().readTree(read(EXPECTED.resolve("curated-events.json")));
        for (JsonNode c : curados) {
            JsonNode n = c.get("curatedRecordJson").get("numeroDocumento");
            if (n != null && !n.isNull()) {
                assertEquals(n.asText().toUpperCase(java.util.Locale.ROOT), n.asText());
            }
        }
    }

    /** F-FW-11 (D14): con extension_subscriber_relation el processor sobrescribe subscriberRelation y lo prueba. */
    @Test
    void extensionSubscriberRelationGeneraOverrideYPruebas(@TempDir Path tmp) throws IOException {
        Path copia = specConCambios(tmp, s -> s.put("extension_subscriber_relation",
                extensionRelacion(List.of("tipoDocumento", "numeroDocumento"), "_")));
        RenderTemplates.render(copia, tmp.resolve("out"), TEMPLATES, SCHEMA, null);
        Path out = tmp.resolve("out");
        String p = read(out.resolve(SCALA_FUENTE + "processor/SISBENCurationProcessor.scala"));
        assertTrue(p.contains("override def subscriberRelation(event: TaggedValidatedEvent): String = {"));
        assertTrue(p.contains("private val CamposRelacion: Seq[String] = Seq(\"tipoDocumento\", \"numeroDocumento\")"));
        assertTrue(p.contains("private val SeparadorRelacion: String = \"_\""));
        assertTrue(p.contains("SISBENCurationProcessor.relacion(event.validatedRecordJson, fileName, errores, homologacion, event.subscriberRelation)"));
        assertTrue(p.contains("catch { case NonFatal(_) => copiado }"));
        assertFalse(p.contains("println(") || p.contains("throw "));
        assertFalse(p.contains("{{"));
        String t = read(out.resolve("src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala"));
        assertTrue(t.contains("SISBENCurationProcessor.relacion("));
        assertFalse(t.contains("{{"));

        // La salida esperada sigue la regla: tipo_numero si ambos existen; si no, el subscriberRelation aceptado.
        ObjectMapper json = new ObjectMapper();
        JsonNode curados = json.readTree(read(out.resolve("expected/curated-events.json")));
        JsonNode base = json.readTree(read(EXPECTED.resolve("curated-events.json")));
        assertEquals(base.size(), curados.size());
        int unidos = 0;
        int copiados = 0;
        for (int i = 0; i < curados.size(); i++) {
            JsonNode rec = curados.get(i).get("curatedRecordJson");
            JsonNode tipo = rec.get("tipoDocumento");
            JsonNode num = rec.get("numeroDocumento");
            boolean ambos = tipo != null && !tipo.isNull() && num != null && !num.isNull() && !num.asText().isBlank();
            String esperado = ambos ? tipo.asText() + "_" + num.asText() : base.get(i).get("subscriberRelation").asText();
            assertEquals(esperado, curados.get(i).get("subscriberRelation").asText(), curados.get(i).get("eventId").asText());
            unidos += ambos ? 1 : 0;
            copiados += ambos ? 0 : 1;
            assertEquals(base.get(i).get("curatedRecordJson"), rec, "la extensión no cambia el registro curado");
        }
        assertTrue(unidos > 0, "el fixture debe ejercitar la unión");
        assertTrue(unidos + copiados == curados.size());
    }

    /** El ejemplo SISBEN no declara la extensión: el processor sigue sin override (D3) y el hueco queda en comentario. */
    @Test
    void sinExtensionNoHayOverrideYElHuecoNoQuedaVacio() throws IOException {
        String p = read(out().resolve(SCALA_FUENTE + "processor/SISBENCurationProcessor.scala"));
        assertFalse(p.contains("override def subscriberRelation"));
        assertTrue(p.contains("// subscriberRelation no se sobrescribe"));
        assertFalse(p.contains("CamposRelacion"));
        assertTrue(read(out().resolve("src/test/scala/com/experian/edf/oxygen/curation/source/sisben/SISBENCurationProcessorSpec.scala"))
                .contains("// Sin extension_subscriber_relation"));
    }

    // ------------------------------------------------------------------ spec incoherente

    static Stream<Arguments> incoherentes() {
        Consumer<Map<String, Object>> repetido = s -> list(s.get("campos_curados")).add(deepCopy(list(s.get("campos_curados")).get(0)));
        Consumer<Map<String, Object>> origen = s -> map(list(s.get("campos_curados")).get(0)).put("origen", "NO_EXISTE");
        Consumer<Map<String, Object>> patron = s -> map(s.get("archivo")).put("file_pattern", ".*NADA");
        Consumer<Map<String, Object>> abierto = s -> {
            Map<String, Object> campo = new LinkedHashMap<>();
            campo.put("destino", "puntajeSisben");
            campo.put("origen", "");
            campo.put("estado", "abierto");
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("tipo", "constante");
            t.put("valor", null);
            campo.put("transformacion", t);
            campo.put("tipo_salida", "texto");
            list(s.get("campos_curados")).add(campo);
        };
        Consumer<Map<String, Object>> noSintetico = s -> map(s.get("fixture")).put("sintetico", false);
        Consumer<Map<String, Object>> advertencias = s -> map(s.get("parametros"))
                .put("campo_advertencias", map(list(s.get("campos_curados")).get(0)).get("destino"));
        Consumer<Map<String, Object>> contraejemplo = s -> map(s.get("archivo")).put("contraejemplos_sinteticos",
                List.of(list(map(s.get("archivo")).get("ejemplos_sinteticos")).get(0)));
        Consumer<Map<String, Object>> zonaAsOf = s -> map(s.get("parametros")).put("zona_asofdate_etapa_anterior", "Marte/Olimpo");
        Consumer<Map<String, Object>> origenZona = s -> map(s.get("parametros")).put("zona_horaria_fechas_origen", "decidido");
        Consumer<Map<String, Object>> relacionSinPublicar = s -> s.put("extension_subscriber_relation",
                extensionRelacion(List.of("tipoDocumento", "noExiste"), "_"));
        Consumer<Map<String, Object>> relacionSinCampos = s -> s.put("extension_subscriber_relation",
                extensionRelacion(List.of(), "_"));
        Consumer<Map<String, Object>> relacionSinSeparador = s -> s.put("extension_subscriber_relation",
                extensionRelacion(List.of("tipoDocumento"), ""));
        return Stream.of(
                Arguments.of(relacionSinPublicar, "no se publican"),
                Arguments.of(relacionSinCampos, "inválido"),
                Arguments.of(relacionSinSeparador, "inválido"),
                Arguments.of(zonaAsOf, "zona inválida"),
                Arguments.of(origenZona, "inválido"),
                Arguments.of(repetido, "repetidos"),
                Arguments.of(origen, "campos_entrada"),
                Arguments.of(patron, "ejemplo"),
                Arguments.of(abierto, "abiertos"),
                Arguments.of(noSintetico, "inválido"),
                Arguments.of(advertencias, "campo de advertencias"),
                Arguments.of(contraejemplo, "contraejemplo"));
    }

    @ParameterizedTest
    @MethodSource("incoherentes")
    void specIncoherenteSeRechaza(Consumer<Map<String, Object>> cambio, String mensaje) {
        Map<String, Object> malo = copia(spec);
        cambio.accept(malo);
        RenderError e = assertThrows(RenderError.class, () -> RenderTemplates.validarSpec(malo, schema));
        assertTrue(e.getMessage().contains(mensaje), e.getMessage());
    }

    @Test
    void reglasDeTransformacionIncoherentes() {
        Map<String, Object> base = copia(spec);
        Map<String, Object> primero = map(list(base.get("campos_curados")).get(0));
        Map<String, Object> t = map(primero.get("transformacion"));
        t.put("tipo", "constante");
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.validarCoherencia(base)).getMessage().contains("no lleva origen"));
        t.put("tipo", "padding_izquierda");
        t.remove("longitud");
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.validarCoherencia(base)).getMessage().contains("requiere longitud"));
        t.put("tipo", "copia_de");
        primero.put("origen", "");
        t.put("campo", "posterior");
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.validarCoherencia(base)).getMessage().contains("campo anterior"));
    }

    @Test
    void utilidades() {
        assertEquals("\"a\\\"b\"", RenderTemplates.q("a\"b"));
        assertEquals("\"\"", RenderTemplates.q(null));
        assertThrows(RenderError.class, () -> RenderTemplates.tq("x\"\"\"y"));
        assertEquals("curation-common-application-inner", RenderTemplates.stem("env/a/curation-common-application-inner.conf"));
        // 2024-03-06 08:00 en -05:00 = 13:00 UTC
        assertEquals(1709730000000L, RenderTemplates.fechaRecepcion("X.20240306080000-0500.txt"));
        assertThrows(RenderError.class, () -> RenderTemplates.fechaRecepcion("sin-marca.txt"));
        assertEquals(RenderTemplates.epochMs("01/02/2024", "UTC"), RenderTemplates.epochMs("1/2/2024", "UTC"));
        assertEquals(RenderTemplates.epochMs("2024-02-01 00:00:00", "UTC"), RenderTemplates.epochMs("01/02/2024", "UTC"));
        assertThrows(RenderError.class, () -> RenderTemplates.epochMs("31/02/2024", "UTC"));
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.rellenar("{{nuevo}}", Map.of(), Set.of(), "x.tpl"))
                .getMessage().contains("no declarados"));
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.rellenar("{{a}}", Map.of(), Set.of("a"), "x.tpl"))
                .getMessage().contains("sin cálculo"));
        assertTrue(assertThrows(RenderError.class, () -> RenderTemplates.rellenar("{{a}}", Map.of("a", () -> " "), Set.of("a"), "x.tpl"))
                .getMessage().contains("valor vacío"));
    }

    // ------------------------------------------------------------------ línea de comandos

    static int cli(ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return RenderTemplates.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void cliDevuelveErrorConSpecInvalido(@TempDir Path tmp) throws IOException {
        Path malo = tmp.resolve("spec.yaml");
        Files.writeString(malo, "schema_version: '1.0'\n", StandardCharsets.UTF_8);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(1, cli(new ByteArrayOutputStream(), err, "--spec", malo.toString(), "--out", tmp.resolve("out").toString(),
                "--schema", SCHEMA.toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).startsWith("ERROR: source-spec inválido"));
    }

    @Test
    void cliOkYWriteExpected(@TempDir Path tmp) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, cli(out, new ByteArrayOutputStream(), "--spec", SPEC.toString(), "--out", tmp.resolve("out").toString(),
                "--write-expected", tmp.resolve("golden").toString()));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("OK: 12 artefactos"));
        assertEquals(read(EXPECTED.resolve("resumen.json")), read(tmp.resolve("golden/resumen.json")));
    }

    @Test
    void cliErroresDeUso() {
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(), "--spec"));
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(), "--otro", "x"));
    }
}
