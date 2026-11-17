package com.experian.edf.skills.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Pruebas del generador de Validation. Datos SINTÉTICOS (source-spec de ejemplo de SISBEN). */
class RenderTemplatesTest {

    static final Path SKILL = RenderTemplates.SKILL_DIR;
    static final Path SPEC_PATH = SKILL.resolve("assets/examples/sisben/source-spec.sisben.yaml");
    static final Path GEN_PATH = SKILL.resolve("assets/examples/sisben/generated");
    static final Path SCHEMA = RenderTemplates.SCHEMA_PATH;
    static final String PROPUESTA = "env/application-conf/innersource/propuesta/";

    Map<String, Object> spec;

    @BeforeEach
    void cargar() {
        spec = Verificaciones.loadSpec(SPEC_PATH);
    }

    @SuppressWarnings("unchecked")
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
    Map<String, Object> copia() {
        return (Map<String, Object>) deepCopy(spec);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> m(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> l(Object o) {
        return (List<Object>) o;
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    static boolean contiene(List<String> errores, String texto) {
        return errores.stream().anyMatch(e -> e.contains(texto));
    }

    // ------------------------------------------------------------------ verificación del spec

    @Test
    void specSisbenPasaLaVerificacion() {
        assertEquals(List.of(), Verificaciones.verifySpec(spec, SCHEMA));
    }

    @Test
    void todoHuecoDePlantillaEstaEnElEsquemaYEnElContexto() {
        Set<String> declared = RenderTemplates.schemaPlaceholders(SCHEMA);
        Set<String> context = Contexto.build(spec).keySet();
        RenderTemplates.templatePlaceholders(RenderTemplates.TEMPLATES_DIR).forEach((template, names) -> {
            assertTrue(declared.containsAll(names), template + ": huecos sin declarar en el esquema");
            assertTrue(context.containsAll(names), template + ": huecos sin valor");
        });
    }

    @Test
    void elEsquemaNoDeclaraHuecosHuerfanos() {
        Set<String> used = new HashSet<>();
        RenderTemplates.templatePlaceholders(RenderTemplates.TEMPLATES_DIR).values().forEach(used::addAll);
        Set<String> huerfanos = new HashSet<>(RenderTemplates.schemaPlaceholders(SCHEMA));
        huerfanos.removeAll(used);
        assertEquals(Set.of(), huerfanos);
    }

    @Test
    void codigoDeLongitudDistintaFalla() {
        Map<String, Object> bad = copia();
        m(l(bad.get("campos")).get(0)).put("codigo", "SISBEN_ERR_1");
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "longitud fija"));
    }

    @Test
    void colisionDeSubcadenaFalla() {
        Map<String, Object> bad = copia();
        l(bad.get("campos")).forEach(f -> m(f).put("codigo", m(f).get("codigo") + "X"));
        m(l(bad.get("campos")).get(1)).put("codigo", "SISBEN_ERR_00");
        m(l(bad.get("campos")).get(0)).put("codigo", "SISBEN_ERR_001");
        assertTrue(contiene(Verificaciones.checkCodes(bad), "subcadena"));
    }

    @Test
    void codigoDe11CaracteresFalla() {
        Map<String, Object> bad = copia();
        List<Object> campos = l(bad.get("campos"));
        for (int i = 0; i < campos.size(); i++) {
            m(campos.get(i)).put("codigo", String.format("SISBENE%04d", i));
        }
        List<Object> reglas = l(bad.get("reglas_entre_campos"));
        for (int i = 0; i < reglas.size(); i++) {
            m(reglas.get(i)).put("codigo", String.format("SISBENR%04d", i));
        }
        Map<String, Object> especiales = new LinkedHashMap<>();
        int i = 0;
        for (String k : m(bad.get("codigos_especiales")).keySet()) {
            especiales.put(k, String.format("SISBENS%04d", i++));
        }
        bad.put("codigos_especiales", especiales);
        l(m(bad.get("fixture")).get("casos")).forEach(c -> m(m(c).get("esperado")).put("codigos", List.of()));
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "11 caracteres"));
    }

    @Test
    void filePatternSinPrefijoFalla() {
        Map<String, Object> bad = copia();
        m(bad.get("archivo")).put("file_pattern", "SISBEN_EXPERIAN_.*");
        assertFalse(Verificaciones.verifySpec(bad, SCHEMA).isEmpty());
    }

    @Test
    void duplicadosSinClaveFalla() {
        Map<String, Object> bad = copia();
        m(bad.get("duplicados")).put("clave", List.of());
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "clave no vacía"));
    }

    @Test
    void columnaDeCatalogoFueraDeKeysFalla() {
        Map<String, Object> bad = copia();
        m(m(l(bad.get("campos")).get(1)).get("catalogo")).put("columna", "descripcion");
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "keys"));
    }

    @Test
    void nombreDeQualityScoreEnExpresionFalla() {
        Map<String, Object> bad = copia();
        m(l(bad.get("quality_scores")).get(0)).put("nombre", "error");
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "quality score"));
    }

    @Test
    void footerSinRegexFalla() {
        Map<String, Object> bad = copia();
        m(bad.get("archivo")).put("filas_footer", 1);
        assertTrue(contiene(Verificaciones.verifySpec(bad, SCHEMA), "footer_regex"));
    }

    @Test
    void historicAttributesTrueFallaPorEsquema() {
        Map<String, Object> bad = copia();
        m(bad.get("ambiente")).put("historic_attributes", true);
        assertFalse(Verificaciones.verifySpec(bad, SCHEMA).isEmpty());
    }

    @Test
    void reglasSemanticasDetectanReferenciasYFixture() {
        Map<String, Object> bad = copia();
        List<Object> campos = l(bad.get("campos"));
        m(campos.get(0)).put("posicion", 99);
        m(campos.get(1)).put("nombre", m(campos.get(2)).get("nombre"));
        m(bad.get("guard_rails")).put("subscriber_relation", "NO_EXISTE");
        Map<String, Object> cat = m(l(bad.get("catalogos")).get(0));
        cat.put("keys", List.of("no_es_columna"));
        cat.put("filas_sinteticas", List.of(List.of("solo-un-valor")));
        Map<String, Object> fixture = m(bad.get("fixture"));
        fixture.put("registro_base", List.of("x"));
        List<Object> casos = l(fixture.get("casos"));
        casos.add(deepCopy(casos.get(0)));
        m(casos.get(0)).put("cambios", Map.of("NO_EXISTE", "1"));
        m(m(casos.get(0)).get("esperado")).put("codigos", List.of("SISBEN_ERR_999"));
        List<String> errores = new ArrayList<>();
        errores.addAll(Verificaciones.checkFields(bad));
        errores.addAll(Verificaciones.checkReferences(bad));
        errores.addAll(Verificaciones.checkFixture(bad));
        for (String esperado : List.of("posiciones", "nombres de campo repetidos", "campo inexistente NO_EXISTE",
                "no es una columna", "fila sintética", "un valor por campo", "código esperado no declarado",
                "ids de casos del fixture repetidos")) {
            assertTrue(contiene(errores, esperado), esperado + " en " + errores);
        }
    }

    @Test
    void contratoDeArchivoDetectaRegexYNombre() {
        Map<String, Object> bad = copia();
        Map<String, Object> archivo = m(bad.get("archivo"));
        m(m(archivo.get("nombre")).get("grupos")).put("prefijo", 99);
        m(bad.get("fixture")).put("nombre_archivo", "otro/nombre.txt");
        List<String> errores = Verificaciones.checkFileContract(bad);
        assertTrue(contiene(errores, "excede los grupos"), errores.toString());
        assertTrue(contiene(errores, "no cumple archivo.nombre.regex"), errores.toString());
        assertTrue(contiene(errores, "comillas, barras"), errores.toString());
        archivo.put("file_pattern", "(");
        assertTrue(contiene(Verificaciones.checkFileContract(bad), "regex inválido"));
    }

    // ------------------------------------------------------------------ utilidades

    @Test
    void escapes() {
        assertEquals("a\\\"b\\\\c", Contexto.scalaStr("a\"b\\c"));
        assertEquals("average(\\\"qs.RQS\\\")", Contexto.hoconStr("average(\"qs.RQS\")"));
        assertEquals("fechaActualizacionCns", Contexto.camel("FECHA_ACTUALIZACION_CNS"));
        assertEquals("%Y-%m-%d %H:%M:%S", Verificaciones.javaToPyDate("yyyy-MM-dd HH:mm:ss"));
        assertThrows(SpecError.class, () -> Verificaciones.javaToPyDate("dd/MMM/yyyy"));
    }

    @Test
    void scanForbiddenDetectaCredenciales() {
        assertFalse(RenderTemplates.scanForbidden("password = algo").isEmpty());
        assertFalse(RenderTemplates.scanForbidden("ver settings-artifactory.xml").isEmpty());
        assertFalse(RenderTemplates.scanForbidden("clave AKIAABCDEFGHIJKLMNOP").isEmpty());
        assertFalse(RenderTemplates.scanForbidden("token=SintTok3nLarg0Xyz12").isEmpty());
        assertEquals(List.of(), RenderTemplates.scanForbidden("texto normal sin secretos"));
    }

    @Test
    void fillReportaHuecosFaltantes() {
        RenderTemplates.Relleno r = RenderTemplates.fill("{{a}} y {{b}}", Map.of("a", "1"));
        assertEquals("1 y {{b}}", r.text());
        assertEquals(Set.of("b"), r.missing());
    }

    @Test
    void fixtureLineBuilder() {
        Map<String, Object> caso = new LinkedHashMap<>(Map.of("id", "X", "descripcion", "",
                "cambios", Map.of("TIP_DOCUMENTO", "PPT"), "columnas", 14, "esperado", Map.of()));
        String line = Contexto.buildFixtureLine(spec, caso);
        String[] parts = line.split(";", -1);
        assertEquals("PPT", parts[parts.length - 1]);
        assertEquals(14, parts.length);
        Map<String, Object> archivo = m(spec.get("archivo"));
        assertTrue(Pattern.compile((String) m(archivo.get("nombre")).get("regex"))
                .matcher((String) m(spec.get("fixture")).get("nombre_archivo")).matches());
        assertEquals("a;b", Contexto.buildFixtureLine(spec, Map.of("linea", "a;b")));
    }

    // ------------------------------------------------------------------ generación

    @Test
    void renderNoDejaHuecosYEscribeManifiesto(@TempDir Path tmp) throws IOException {
        Map<String, Object> manifest = RenderTemplates.renderAll(spec, tmp);
        assertTrue(((String) manifest.get("compilacion")).startsWith("NO CERTIFICADA"));
        List<Object> archivos = l(manifest.get("archivos"));
        assertEquals(RenderTemplates.TEMPLATE_TARGETS.size() + l(spec.get("catalogos")).size(), archivos.size());
        for (Object a : archivos) {
            String text = read(tmp.resolve((String) m(a).get("ruta")));
            assertFalse(RenderTemplates.PLACEHOLDER_RE.matcher(text).find(), (String) m(a).get("ruta"));
            assertEquals(List.of(), RenderTemplates.scanForbidden(text));
        }
        assertTrue(read(tmp.resolve("manifest.json")).contains("\"fuente\": \"SISBEN\""));
    }

    /** El ejemplo persistido (assets/examples/sisben/generated) es igual a una regeneración. */
    @Test
    void ejemploPersistidoIgualARegeneracion(@TempDir Path tmp) throws IOException {
        RenderTemplates.renderAll(spec, tmp);
        assertEquals(arbol(GEN_PATH), arbol(tmp));
    }

    static Map<String, String> arbol(Path root) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                out.put(root.relativize(f).toString().replace('\\', '/'), read(f));
            }
        }
        return out;
    }

    @Test
    void bloqueHoconUsaClavesVerificadas(@TempDir Path tmp) throws IOException {
        RenderTemplates.renderAll(spec, tmp);
        String conf = read(tmp.resolve(PROPUESTA + "validation-configs-sisben.conf"));
        assertTrue(conf.contains("headerRows = 1") && conf.contains("footerRows = 0"));
        assertFalse(conf.contains("numberOfHeaderRows") || conf.contains("numberOfFooterRows"));
        assertTrue(conf.contains("deDupVersion = \"EXTN\"") && conf.contains("computeMetrics = true"));
        assertFalse(conf.contains("\nencoding") || conf.contains("  encoding ="));
        assertTrue(conf.contains("filePattern = \".*"));
        assertTrue(conf.contains("contexual"));
    }

    /** Parse con Typesafe Config 1.3.4 (el parser del motor), como verificar_hocon.py hacía con pyhocon. */
    @Test
    void hoconGeneradoParseaConElParserDelMotor(@TempDir Path tmp) throws IOException {
        RenderTemplates.renderAll(spec, tmp);
        Config block = ConfigFactory.parseString("configs = [\n" + read(tmp.resolve(PROPUESTA + "validation-configs-sisben.conf")) + "\n]")
                .getConfigList("configs").get(0);
        for (String k : List.of("filePattern", "processor", "headerRows", "footerRows", "computeMetrics", "validate")) {
            assertTrue(block.hasPath(k), "falta la clave " + k);
        }
        for (String k : List.of("numberOfHeaderRows", "numberOfFooterRows", "historicAttributeArg", "rejectedDir")) {
            assertFalse(block.hasPath(k), "clave ignorada por el parser: " + k);
        }
        Pattern filePattern = Pattern.compile(block.getString("filePattern"));
        String fixture = (String) m(spec.get("fixture")).get("nombre_archivo");
        assertTrue(filePattern.matcher("/home/app/validation/input/" + fixture).matches());
        assertTrue(filePattern.matcher("/home/app/validation/input/RT_" + fixture).matches());
        assertEquals("errorlevelNotIn(\"1\")", block.getConfigList("validate").get(0).getString("fileName"));
        List<? extends Config> lookups = ConfigFactory.parseString("lookups = " + read(tmp.resolve(PROPUESTA + "lookups-sisben.conf")))
                .getConfigList("lookups");
        assertEquals(l(spec.get("catalogos")).size(), lookups.size());
        lookups.forEach(lk -> assertTrue(lk.hasPath("tableName") && lk.hasPath("keys")));
    }

    @Test
    void processorNoUsaApisProhibidas(@TempDir Path tmp) throws IOException {
        RenderTemplates.renderAll(spec, tmp);
        Path proc;
        try (Stream<Path> s = Files.walk(tmp)) {
            proc = s.filter(p -> p.getFileName().toString().equals("SISBENValidationProcessor.scala")).findFirst().orElseThrow();
        }
        String text = read(proc);
        assertTrue(text.contains("class SISBENValidationProcessor(processorContext: ProcessorContext) extends ValidationProcessor"));
        for (String forbidden : List.of("println", "decodeErrorObject", "HistoricAttributes", "throw ")) {
            assertFalse(text.contains(forbidden), forbidden);
        }
        assertTrue(text.contains("@transient private lazy val logger"));
    }

    static List<String> entradas(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> !p.equals(dir)).map(p -> dir.relativize(p).toString()).sorted().collect(Collectors.toList());
        }
    }

    /**
     * Los 4 patrones de CP-VAL-G-07 (Discovery/13_casos_de_prueba/datos_mock/validation/06_specs_negativas/G-07*.yaml),
     * como copias equivalentes en memoria: valores falsos armados aquí, no copiados de los datos mock.
     */
    static Stream<String> credencialesSinteticas() {
        return Stream.of("password=" + "X".repeat(14), "token=" + "Y".repeat(20), "AKIA" + "Z".repeat(16),
                "settings-artifactory.xml");
    }

    /** AC-22 (CP-VAL-G-07): la generación falla completa y el directorio de salida queda vacío. */
    @ParameterizedTest
    @MethodSource("credencialesSinteticas")
    void credencialEnElSpecDetieneLaGeneracion(String credencial, @TempDir Path tmp) throws IOException {
        Map<String, Object> bad = copia();
        bad.put("spec_version", "sisben-v4-0.1.0 " + credencial);
        SpecError e = assertThrows(SpecError.class, () -> RenderTemplates.renderAll(bad, tmp));
        assertTrue(e.getMessage().contains("patrón prohibido"));
        assertEquals(List.of(), entradas(tmp));
    }

    /** AC-22: también los CSV de catálogo se revisan antes de escribir. */
    @Test
    void credencialEnUnCatalogoSinteticoDetieneLaGeneracion(@TempDir Path tmp) throws IOException {
        Map<String, Object> bad = copia();
        l(m(l(bad.get("catalogos")).get(0)).get("filas_sinteticas")).add(List.of("16", "token=" + "W".repeat(20)));
        SpecError e = assertThrows(SpecError.class, () -> RenderTemplates.renderAll(bad, tmp));
        assertTrue(e.getMessage().contains("lookup/SISBEN_HOME_ID_CLASIFICATION_NH.csv: patrón prohibido"), e.getMessage());
        assertEquals(List.of(), entradas(tmp));
    }

    /** writeLookupCsvs (DEPRECADO) escribe los mismos CSV que arma lookupCsvs. */
    @Test
    @SuppressWarnings("deprecation")
    void writeLookupCsvsDeprecadoEscribeLoQueArmaLookupCsvs(@TempDir Path tmp) throws IOException {
        Map<Path, String> armados = RenderTemplates.lookupCsvs(spec, tmp);
        assertEquals(new ArrayList<>(armados.keySet()), RenderTemplates.writeLookupCsvs(spec, tmp));
        for (Map.Entry<Path, String> e : armados.entrySet()) {
            assertEquals(e.getValue(), read(e.getKey()));
        }
    }

    // ------------------------------------------------------------------ supuestos (AC-18a, AC-18b, ADR-G1)

    static final Pattern ID = Pattern.compile("F-\\d{2}|VAL-P[AT]\\d{2}|VAL-SP\\d{2}");
    static final Pattern MARCA = Pattern.compile("ASSUMPTION[ \\t]+(" + ID.pattern() + ")\\b");

    /** Archivos generados (sin manifest.json): ruta relativa → texto. */
    static Map<String, String> salida(Path root) throws IOException {
        Map<String, String> out = arbol(root);
        out.remove("manifest.json");
        return out;
    }

    @Test
    void todaMarcaAssumptionTieneId(@TempDir Path tmp) throws IOException {
        RenderTemplates.renderAll(spec, tmp);
        int marcas = 0;
        List<String> sinId = new ArrayList<>();
        for (Map.Entry<String, String> f : salida(tmp).entrySet()) {
            String[] lines = f.getValue().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                int desde = 0;
                int pos;
                while ((pos = lines[i].indexOf("ASSUMPTION", desde)) >= 0) {
                    Matcher mm = MARCA.matcher(lines[i].substring(pos));
                    if (mm.lookingAt()) {
                        marcas++;
                    } else {
                        sinId.add(f.getKey() + ":" + (i + 1));
                    }
                    desde = pos + 1;
                }
            }
        }
        assertEquals(List.of(), sinId);
        assertTrue(marcas > 0);
    }

    @Test
    void supuestosDelManifestIgualesALasMarcas(@TempDir Path tmp) throws IOException {
        Map<String, Object> manifest = RenderTemplates.renderAll(spec, tmp);
        Set<String> enCodigo = new TreeSet<>();
        for (String text : salida(tmp).values()) {
            Matcher mm = MARCA.matcher(text);
            while (mm.find()) {
                enCodigo.add(mm.group(1));
            }
        }
        List<Object> supuestos = l(manifest.get("supuestos"));
        assertEquals(new ArrayList<>(enCodigo), supuestos, "conjunto ordenado, sin repetidos");
        assertTrue(read(tmp.resolve("manifest.json")).contains("\"VAL-SP01\""));
    }

    /** Filas "sí" de la columna "En el código generado" de decisiones-y-abiertos.md. */
    static Set<String> supuestosActivosDelDocumento(Path doc) throws IOException {
        Set<String> activos = new TreeSet<>();
        Set<String> vistos = new TreeSet<>();
        int col = -1;
        for (String line : read(doc).split("\n")) {
            if (!line.startsWith("|")) {
                col = -1;
                continue;
            }
            String[] cells = line.split("\\|", -1);
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cells[i].trim();
                if (cells[i].equals("En el código generado")) {
                    col = i;
                }
            }
            if (col > 0 && cells.length > col && ID.matcher(cells[1]).matches()) {
                assertTrue(vistos.add(cells[1]), "ID repetido en el documento: " + cells[1]);
                if (cells[col].startsWith("sí")) {
                    activos.add(cells[1]);
                }
            }
        }
        return activos;
    }

    @Test
    void supuestosDelManifestEnDecisiones(@TempDir Path tmp) throws IOException {
        Map<String, Object> manifest = RenderTemplates.renderAll(spec, tmp);
        Set<String> activos = supuestosActivosDelDocumento(SKILL.resolve("references/decisiones-y-abiertos.md"));
        Set<String> enManifest = new TreeSet<>();
        l(manifest.get("supuestos")).forEach(s -> enManifest.add((String) s));
        Set<String> soloManifest = new TreeSet<>(enManifest);
        soloManifest.removeAll(activos);
        Set<String> soloDocumento = new TreeSet<>(activos);
        soloDocumento.removeAll(enManifest);
        assertEquals(Set.of(), soloManifest, "en el manifest y no activos en el documento");
        assertEquals(Set.of(), soloDocumento, "activos en el documento y no en el manifest");
    }

    @Test
    void marcaSinIdDetieneLaGeneracion(@TempDir Path tmp) throws IOException {
        Path templates = tmp.resolve("tpl");
        for (String rel : RenderTemplates.TEMPLATE_TARGETS.keySet()) {
            Path dst = templates.resolve(rel);
            Files.createDirectories(dst.getParent());
            Files.writeString(dst, rel.equals("lookups.conf.tpl") ? "# ASSUMPTION F-12: ok\n# ASSUMPTION: sin id\n" : "x",
                    StandardCharsets.UTF_8);
        }
        Path out = tmp.resolve("out");
        SpecError e = assertThrows(SpecError.class, () -> RenderTemplates.renderAll(spec, out, templates));
        assertTrue(e.getMessage().contains("lookups.conf.tpl: marca ASSUMPTION sin ID en las líneas [2]"), e.getMessage());
        assertFalse(Files.exists(out));
        assertEquals(List.of("F-12", "F-12"), RenderTemplates.marcasSupuesto("ASSUMPTION F-12: a\n(ASSUMPTION F-12)"));
        assertEquals(List.of(1, 2), RenderTemplates.marcasSinId("ASSUMPTION (F-07)\nASSUMPTION\nASSUMPTION VAL-SP01: b"));
    }

    // ------------------------------------------------------------------ métricas (AC-VAL-54a)

    @Test
    void metricaConQualityScoreNoDeclaradoFalla() {
        Map<String, Object> bad = copia();
        l(bad.get("metricas")).add(new LinkedHashMap<>(Map.of("id", "AverageFQS", "expr", "average(\"qs.FQS\")")));
        List<String> errores = Verificaciones.verifySpec(bad, SCHEMA);
        assertEquals(List.of("métrica AverageFQS usa el quality score no declarado FQS (AC-VAL-54a)"), errores);
    }

    @Test
    void metricaSinQualityScorePasa() {
        Map<String, Object> ok = copia();
        ok.put("metricas", List.of(new LinkedHashMap<>(Map.of("id", "TotalRegistros", "expr", "count(*)"))));
        assertEquals(List.of(), Verificaciones.checkMetrics(ok));
        assertEquals(List.of(), Verificaciones.verifySpec(ok, SCHEMA));
    }

    @Test
    void huecoSinValorSeReporta(@TempDir Path tmp) throws IOException {
        Path templates = tmp.resolve("tpl");
        for (String rel : RenderTemplates.TEMPLATE_TARGETS.keySet()) {
            Path dst = templates.resolve(rel);
            Files.createDirectories(dst.getParent());
            Files.writeString(dst, rel.equals("fixture.txt.tpl") ? "{{no_existe}}" : "x", StandardCharsets.UTF_8);
        }
        SpecError e = assertThrows(SpecError.class, () -> RenderTemplates.renderAll(spec, tmp.resolve("out"), templates));
        assertTrue(e.getMessage().contains("fixture.txt.tpl: huecos sin valor ['no_existe']"), e.getMessage());
    }

    // ------------------------------------------------------------------ línea de comandos

    static int cli(ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return RenderTemplates.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void cliSoloVerificarYError(@TempDir Path tmp) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, cli(out, new ByteArrayOutputStream(), "--spec", SPEC_PATH.toString(), "--solo-verificar"));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("source-spec OK (22 campos"));
        Path bad = tmp.resolve("malo.yaml");
        Files.writeString(bad, read(SPEC_PATH).replace("fuente: sisben", "fuente: SISBEN"), StandardCharsets.UTF_8);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(2, cli(new ByteArrayOutputStream(), err, "--spec", bad.toString(), "--out", tmp.resolve("out").toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).startsWith("El source-spec NO pasa la verificación:"));
        assertFalse(Files.exists(tmp.resolve("out")));
    }

    @Test
    void cliRender(@TempDir Path tmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, cli(out, new ByteArrayOutputStream(), "--spec", SPEC_PATH.toString(), "--out", tmp.toString()));
        assertTrue(Files.exists(tmp.resolve("manifest.json")));
        // 12 plantillas + 7 CSV de catálogo (manifest.json no se cuenta a sí mismo)
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Generados 19 archivos"));
    }

    @Test
    void cliErroresDeUso(@TempDir Path tmp) {
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(), "--out", "x"));
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(), "--otro"));
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(), "--spec", SPEC_PATH.toString()));
        assertEquals(2, cli(new ByteArrayOutputStream(), new ByteArrayOutputStream(),
                "--spec", tmp.resolve("no-existe.yaml").toString()));
    }
}
