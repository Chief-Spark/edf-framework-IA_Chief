package com.experian.edf.skills.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Pruebas del renderizador, de las verificaciones de la spec y del fixture sintético SISBEN. */
class RenderTemplatesTest {

    static final Path SKILL = RenderTemplates.SKILL_DIR;
    static final Path SPEC_PATH = SKILL.resolve("assets/examples/sisben/source-spec.sisben.yaml");
    static final Path GEN_PATH = SKILL.resolve("assets/examples/sisben/generated");
    static final Path TEMPLATES = RenderTemplates.DEFAULT_TEMPLATES;

    static Map<String, Object> spec;
    static Map<String, Object> schema;
    static Path out;
    static RenderTemplates.Reporte report;

    @TempDir
    static Path shared;

    @BeforeAll
    static void generar() {
        spec = RenderTemplates.loadYaml(SPEC_PATH);
        schema = RenderTemplates.loadYaml(RenderTemplates.DEFAULT_SCHEMA);
        out = shared.resolve("generated");
        report = RenderTemplates.generate(spec, out);
    }

    /** Copia profunda de la spec con un valor cambiado (la original no se toca). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> mutated(Map<String, Object> base, String path, Object value) {
        Map<String, Object> copy = (Map<String, Object>) deepCopy(base);
        String[] parts = path.split("\\.");
        Object node = copy;
        for (int i = 0; i < parts.length - 1; i++) {
            node = node instanceof List<?> l ? l.get(Integer.parseInt(parts[i])) : ((Map<String, Object>) node).get(parts[i]);
        }
        ((Map<String, Object>) node).put(parts[parts.length - 1], value);
        return copy;
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

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    static String render(String tpl, Map<String, Object> data) {
        return Plantillas.renderString(tpl, data, TEMPLATES, null);
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ motor de plantillas

    @Test
    void variableYFiltros() {
        Map<String, Object> data = mapOf("a", mapOf("b", "x\"y\\z"), "n", null, "l", List.of(1, 2));
        assertEquals("x\"y\\z", render("{{a.b}}", data));
        assertEquals("x\\\"y\\\\z", render("{{a.b|hocon}}", data));
        assertEquals("[1, 2]", render("{{l|json}}", data));
        assertEquals("null", render("{{n|json}}", data));
    }

    @Test
    void huecoInexistenteONuloFalla() {
        assertTrue(assertThrows(TemplateError.class, () -> render("{{no.existe}}", Map.of()))
                .getMessage().contains("sin resolver"));
        assertTrue(assertThrows(TemplateError.class, () -> render("{{n}}", mapOf("n", null)))
                .getMessage().contains("nulo"));
    }

    @Test
    void bloquesCadaYSiSinLineasVacias() {
        Map<String, Object> data = mapOf("xs", List.of(Map.of("v", 1), Map.of("v", 2)), "on", false, "k", "K");
        String tpl = "inicio\n{{#cada xs}}\n- {{v}}{{k}}\n{{/cada}}\n{{#si on}}\noculto\n{{/si}}\nfin\n";
        assertEquals("inicio\n- 1K\n- 2K\nfin\n", render(tpl, data));
    }

    @Test
    void bloqueMalCerradoYFiltroDesconocido() {
        assertThrows(TemplateError.class, () -> Plantillas.parse("{{#cada xs}}sin cierre"));
        assertThrows(TemplateError.class, () -> Plantillas.parse("{{x|yaml}}"));
        assertThrows(TemplateError.class, () -> Plantillas.parse("{{/cada}}"));
        assertThrows(TemplateError.class, () -> Plantillas.parse("{{#otro x}}{{/otro}}"));
        assertThrows(TemplateError.class, () -> Plantillas.parse("{{a b}}"));
    }

    @Test
    void cadaRequiereListaDeObjetos() {
        assertThrows(TemplateError.class, () -> render("{{#cada x}}{{/cada}}", mapOf("x", "no")));
        assertThrows(TemplateError.class, () -> render("{{#cada x}}{{/cada}}", mapOf("x", List.of(1))));
        assertThrows(TemplateError.class, () -> render("{{x}}", mapOf("x", List.of(1))));
    }

    @Test
    void parcialesFueraDelDirectorioOInexistentes() {
        assertTrue(assertThrows(TemplateError.class, () -> render("{{> ../SKILL.md}}", Map.of()))
                .getMessage().contains("fuera"));
        assertTrue(assertThrows(TemplateError.class, () -> render("{{> no-existe.tpl}}", Map.of()))
                .getMessage().contains("inexistente"));
    }

    @Test
    void literalConSaltoDeLineaRechazado() {
        assertTrue(assertThrows(TemplateError.class, () -> render("{{x|hocon}}", mapOf("x", "a\nb")))
                .getMessage().contains("salto de línea"));
    }

    @Test
    void salidaFueraDelDirectorioRechazada(@TempDir Path tmp) {
        assertTrue(assertThrows(TemplateError.class, () -> RenderTemplates.safeOutput(tmp, "../escape.conf"))
                .getMessage().contains("fuera"));
    }

    // ------------------------------------------------------------------ semántica de Python

    @Test
    void formatoDeValoresComoPython() {
        assertEquals("0.9", Py.floatRepr(0.9));
        assertEquals("1.0", Py.floatRepr(1.0));
        assertEquals("1e+16", Py.floatRepr(1e16));
        assertEquals("1e-05", Py.floatRepr(0.00001));
        assertEquals("-2.5", Py.floatRepr(-2.5));
        assertEquals("{\"a\": [1, 2.0, true, null], \"ñ\": \"x\\ny\"}",
                Py.jsonDumps(mapOf("a", Arrays.asList(1, 2.0, true, null), "ñ", "x\ny")));
        assertEquals("'10'", Py.repr("10"));
        assertEquals("True", Py.str(true));
        assertFalse(Py.truthy(""));
        assertFalse(Py.truthy(List.of()));
        assertFalse(Py.truthy(0));
        assertTrue(Py.truthy("x"));
    }

    // ------------------------------------------------------------------ schema y huecos

    @Test
    void specValidaContraSchema() {
        assertEquals(List.of(), Verificaciones.validateSchema(spec, schema));
    }

    @Test
    void schemaRechazaClaveExtraYUmbralNumerico() {
        assertFalse(Verificaciones.validateSchema(mutated(spec, "extra", 1), schema).isEmpty());
        assertFalse(Verificaciones.validateSchema(mutated(spec, "calidad.umbral", 0.9), schema).isEmpty());
    }

    @Test
    void todoHuecoDefinidoEnSchema() {
        List<Auditoria.Fila> rows = Auditoria.audit(spec, schema, TEMPLATES);
        assertFalse(rows.isEmpty(), "la auditoría no recorrió plantillas");
        assertEquals(List.of(), rows.stream().filter(r -> !r.enSchema()).collect(Collectors.toList()));
    }

    @Test
    void huecosDeRamasActivasLlenosEnSpec() {
        List<Auditoria.Fila> empty = Auditoria.audit(spec, schema, TEMPLATES).stream()
                .filter(r -> !r.enSpec().equals("lleno"))
                .filter(r -> !r.hueco().startsWith("intervencion_manual.manual") && !r.hueco().startsWith("contextual_override."))
                .collect(Collectors.toList());
        assertEquals(List.of(), empty);
    }

    @Test
    void manifestSigueOrdenDelInventario() {
        List<Map<String, Object>> manifest = RenderTemplates.loadManifest(TEMPLATES);
        List<Integer> pasos = manifest.stream().map(e -> (Integer) e.get("paso")).collect(Collectors.toList());
        assertEquals(pasos.stream().sorted().collect(Collectors.toList()), pasos);
        assertEquals(Set.of("ACC-TPL-01", "ACC-TPL-02", "ACC-TPL-03", "ACC-TPL-04", "ACC-TPL-05", "ACC-TPL-06"),
                manifest.stream().map(e -> (String) e.get("id")).collect(Collectors.toSet()));
    }

    // ------------------------------------------------------------------ verificaciones del inventario

    @Test
    void specSisbenPasaVerificaciones() {
        assertEquals(List.of(), Verificaciones.verifySpec(spec));
    }

    static Stream<Arguments> errores() {
        return Stream.of(
                Arguments.of("archivo.filePattern", "SISBEN_EXPERIAN.*", "empezar con .*"),
                Arguments.of("archivo.filePattern", ".*(", "no compila"),
                Arguments.of("fileActions.accept", "average(\"qs.RQS\") >= 90", "escala del umbral"),
                Arguments.of("fileActions.reject", "average(\"qs.RQS\") < 0.90", "sintaxis nueva"),
                Arguments.of("fileActions.reject", "anyOf(NO_DEFINIDA)", "no definidas"),
                Arguments.of("fileActions.reject", "anyOf(RQS_BAJO_UMBRAL_SISBEN) hasErrorlevelIn(1)", "severidades"),
                Arguments.of("registro.recordAcceptance", "errorlevelNotIn(\"1\") and RQS > 0.5", "ACC-R02"),
                Arguments.of("registro.recordAcceptance", "errorcodeNotIn(\"CODIGO_INVENTADO\")", "no existe en IN-05"),
                Arguments.of("registro.niveles_que_rechazan", List.of("10"), "ACC-R01"),
                Arguments.of("registro.codigos_que_rechazan", List.of("SISBEN_ERR_999"), "IN-05"),
                Arguments.of("condiciones.0.nombre", "RQS_BAJO_UMBRAL", "nombre de la fuente"),
                // Filas de CP-ACC-G-05 que el skill no probaba (10, 13, 14 y 15)
                Arguments.of("condiciones.0.expresion", "average(\"qs.RQS\") < 0.85", "escala del umbral"),
                Arguments.of("fileActions.accept", "average(\"qs.RQS\") >= 0.90 or isEmptyBodyFile", "isEmptyBodyFile"),
                Arguments.of("fileActions.accept", "min(\"qs.RQS\") >= 0.90", "quality score declarado"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("errores")
    void verificacionesDetectanErrores(String path, Object value, String expected) {
        List<String> problems = Verificaciones.verifySpec(mutated(spec, path, value));
        assertTrue(problems.stream().anyMatch(p -> p.contains(expected)), problems.toString());
    }

    @Test
    void manualSinExternalActionRechazado() {
        Map<String, Object> bad = mutated(spec, "intervencion_manual.habilitada", true);
        bad = mutated(bad, "intervencion_manual.manualReject", "average(\"qs.RQS\") < 0.5");
        List<String> problems = Verificaciones.verifySpec(bad);
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("manualReject") && p.contains("externalAction")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("manualAccept")), problems.toString());
    }

    /** CP-ACC-G-05 fila 12 · Regla "umbral en [0, 1]" (AC-04, P-3): 1.50 coherente en las tres expresiones. */
    @Test
    void umbralFueraDeRangoRechazado() {
        Map<String, Object> bad = mutated(spec, "calidad.umbral", "1.50");
        bad = mutated(bad, "fileActions.accept", "average(\"qs.RQS\") >= 1.50");
        bad = mutated(bad, "condiciones.0.expresion", "average(\"qs.RQS\") < 1.50");
        List<String> problems = Verificaciones.verifySpec(bad);
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("umbral en [0, 1]") && p.contains("'1.50'")), problems.toString());
        assertTrue(problems.stream().noneMatch(p -> p.contains("escala del umbral")), problems.toString());
        assertTrue(Verificaciones.validateSchema(bad, schema).stream().anyMatch(p -> p.startsWith("calidad/umbral")));
    }

    @Test
    void umbralEnRangoYFueraDeRango() {
        for (String ok : List.of("0.0", "0.90", "1.0", "1.00", "0", "1")) {
            assertEquals(null, Verificaciones.umbralFueraDeRango(ok), ok);
        }
        for (String malo : List.of("1.50", "-0.10", "1.01", "90")) {
            assertTrue(Verificaciones.umbralFueraDeRango(malo).contains("fuera de [0, 1]"), malo);
        }
        assertTrue(Verificaciones.umbralFueraDeRango("abc").contains("no es un número"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> conScore(Object score) {
        Map<String, Object> copy = (Map<String, Object>) deepCopy(spec);
        Map<String, Object> evento = (Map<String, Object>) ((List<Object>) ((Map<String, Object>)
                ((List<Object>) ((Map<String, Object>) copy.get("fixture")).get("archivos")).get(0)).get("eventos")).get(2);
        ((Map<String, Object>) ((List<Object>) evento.get("qualityScores")).get(0)).put("score", score);
        return copy;
    }

    /** CP-ACC-B-12 (c) · Regla "QS por registro binario" (AC-ACC-05, plan #2): un score 0.5 se rechaza. */
    @Test
    void qsPorRegistroNoBinarioRechazado() {
        Map<String, Object> bad = conScore(0.5);
        List<String> problems = Verificaciones.verifySpec(bad);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("QS por registro binario: RQS = 0.5 en fixture/archivos/0/eventos/2"),
                problems.toString());
        assertTrue(Verificaciones.validateSchema(bad, schema).stream()
                .anyMatch(p -> p.startsWith("fixture/archivos/0/eventos/2/qualityScores/0/score")));
    }

    @Test
    void qsPorRegistroBinarioAceptaCeroYUnoEnterosODecimales() {
        for (Object ok : List.of(0, 1, 0.0, 1.0)) {
            assertEquals(List.of(), Verificaciones.verifySpec(conScore(ok)), String.valueOf(ok));
            assertEquals(List.of(), Verificaciones.validateSchema(conScore(ok), schema), String.valueOf(ok));
        }
        for (Object malo : List.of(-1, 2, 0.99)) {
            assertFalse(Verificaciones.verifySpec(conScore(malo)).isEmpty(), String.valueOf(malo));
        }
    }

    @Test
    void cliRechazaQsNoBinario(@TempDir Path tmp) throws IOException {
        // La línea 3 del fixture (SINT-A-03) pasa de score 1.0 a 0.5; el resto de la spec queda igual.
        String original = read(SPEC_PATH);
        String bad = original.replaceFirst("(SINT-A-03,[^\\n]*score: )1\\.0", "$10.5");
        assertFalse(bad.equals(original));
        Path f = tmp.resolve("qs.yaml");
        Files.writeString(f, bad, StandardCharsets.UTF_8);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(1, cli(buf, "--spec", f.toString(), "--out", tmp.resolve("o").toString()));
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("QS por registro binario: RQS = 0.5"));
        assertFalse(Files.exists(tmp.resolve("o")));
    }

    @Test
    void contextualOverrideExigeDecisionHumana() {
        Map<String, Object> bad = mutated(spec, "contextual_override.decision_humana", true);
        assertTrue(Verificaciones.verifySpec(bad).stream().anyMatch(p -> p.contains("decisión humana")));
    }

    @Test
    void condicionesRepetidas() {
        List<Object> conds = new ArrayList<>((List<?>) spec.get("condiciones"));
        conds.add(conds.get(0));
        assertTrue(Verificaciones.verifySpec(mutated(spec, "condiciones", conds)).stream()
                .anyMatch(p -> p.contains("repetidos")));
    }

    @Test
    void claveDeArchivoDelMotor() {
        assertEquals("/v/X.txt", Verificaciones.engineFileKey("/v/X.txt.900001"));
        assertEquals("/v/X.txt", Verificaciones.engineFileKey("/v/X.txt.errorInHeader.900003"));
        assertEquals("/v.d/X", Verificaciones.removeExtension("/v.d/X"));
    }

    // ------------------------------------------------------------------ generación

    @Test
    void generaYOmiteLoEsperado() {
        List<String> ids = report.generados().stream().map(g -> g.get("id")).collect(Collectors.toList());
        assertEquals(5, ids.stream().filter("ACC-TPL-06"::equals).count());
        assertTrue(ids.containsAll(List.of("ACC-TPL-01", "ACC-TPL-02", "ACC-TPL-05")));
        assertEquals(Set.of("ACC-TPL-03"), report.omitidos().stream().map(o -> o.get("id")).collect(Collectors.toSet()));
    }

    @Test
    void sinHuecosSinResolver() throws IOException {
        try (Stream<Path> files = Files.walk(out)) {
            for (Path f : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                assertFalse(Pattern.compile("\\{\\{|\\}\\}").matcher(read(f)).find(), f.toString());
            }
        }
    }

    /** CP-ACC-G-04 · El ejemplo persistido es igual a una regeneración (árbol y contenido). */
    @Test
    void ejemploPersistidoIgualARegeneracion() throws IOException {
        assertEquals(arbol(GEN_PATH), arbol(out));
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

    static Config hocon(Path f) throws IOException {
        return ConfigFactory.parseString(read(f));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hoconGeneradoParseaConElParserDelMotorYConservaValores() throws IOException {
        Config cfg = hocon(out.resolve("hocon/sisben-acc-tpl-01-acceptance-block.conf"))
                .getConfigList("oxygen.stage.acceptance.configs").get(0);
        Map<String, Object> archivo = (Map<String, Object>) spec.get("archivo");
        Map<String, Object> fileActions = (Map<String, Object>) spec.get("fileActions");
        Map<String, Object> registro = (Map<String, Object>) spec.get("registro");
        assertEquals(archivo.get("filePattern"), cfg.getString("filePattern"));
        assertEquals(Map.of("reject", fileActions.get("reject"), "accept", fileActions.get("accept")),
                cfg.getConfig("fileActions").root().unwrapped());
        assertEquals(registro.get("recordAcceptance"), cfg.getString("recordAcceptance"));
        assertTrue(cfg.getBoolean("customReasonForAutomaticRejectionEnabled"));
        assertFalse(cfg.hasPath("contextualOverride") || cfg.hasPath("lookupMapping") || cfg.hasPath("processor"));
        Config cond = hocon(out.resolve("hocon/sisben-acc-tpl-02-file-conditions.conf"))
                .getConfigList("oxygen.stage.acceptance.fileConditions").get(0);
        assertEquals(Map.of("name", "RQS_BAJO_UMBRAL_SISBEN", "expression", "average(\"qs.RQS\") < 0.90",
                "reasonCode", "SISBFA00001", "isLogged", true), cond.root().unwrapped());
    }

    @Test
    void ramasOpcionalesCuandoSeHabilitan(@TempDir Path tmp) throws IOException {
        Map<String, Object> on = mutated(spec, "severidades.habilitado", true);
        on = mutated(on, "contextual_override.decision_humana", true);
        on = mutated(on, "contextual_override.decision_ref", "ACTA-SINTETICA-001");
        on = mutated(on, "contextual_override.inferDelete",
                mapOf("enabled", true, "lookBackPeriod", 6, "deletePredicates", List.of(List.of("subscriberRelation"))));
        on = mutated(on, "contextual_override.explicitDelete",
                mapOf("enabled", false, "lookBackPeriod", 1, "deletePredicates", List.of()));
        RenderTemplates.Reporte r = RenderTemplates.generate(on, tmp);
        assertTrue(r.generados().stream().anyMatch(g -> g.get("id").equals("ACC-TPL-03")));
        Config cfg = hocon(tmp.resolve("hocon/sisben-acc-tpl-01-acceptance-block.conf"))
                .getConfigList("oxygen.stage.acceptance.configs").get(0);
        assertEquals(Map.of("tableName", "SISBEN_ACC_REASON_SEVERITY_NH", "mapFromColumn", "reasonCode",
                "mapToColumn", "severity"), cfg.getConfig("lookupMapping").root().unwrapped());
        assertEquals(List.of(List.of("subscriberRelation")),
                cfg.getList("contextualOverride.inferDelete.deletePredicates").unwrapped());
        assertEquals("reasonCode,severity\nSISBFA00001,1\n", read(tmp.resolve("lookups/SISBEN_ACC_REASON_SEVERITY_NH.csv")));
        assertNotNull(hocon(tmp.resolve("hocon/sisben-acc-tpl-03-lookup.conf")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fixtureJsonValidoYSintetico() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Path> files;
        try (Stream<Path> s = Files.list(out.resolve("src/test/resources/sisben/validated"))) {
            files = s.map(d -> d.resolve("part-00000.json")).filter(Files::isRegularFile).collect(Collectors.toList());
        }
        assertEquals(((List<?>) ((Map<String, Object>) spec.get("fixture")).get("archivos")).size(), files.size());
        for (Path f : files) {
            for (String line : read(f).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode event = mapper.readTree(line);
                assertTrue(event.get("eventId").asText().startsWith("SINT-"));
                assertTrue(f.getParent().getFileName().toString().contains(event.get("lineage").get("fileName").asText()));
                JsonNode rec = mapper.readTree(event.get("validatedRecordJson").asText());
                if (rec.has("NUM_DOCUMENTO")) {
                    assertTrue(rec.get("NUM_DOCUMENTO").asText().startsWith("SINT"));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void lineageFilenameCoincideConFilePattern() {
        Pattern pattern = Pattern.compile((String) ((Map<String, Object>) spec.get("archivo")).get("filePattern"));
        for (Object a : (List<?>) ((Map<String, Object>) spec.get("fixture")).get("archivos")) {
            Map<String, Object> archivo = (Map<String, Object>) a;
            String lineage = (String) archivo.get("nombre_lineage");
            assertTrue(pattern.matcher(lineage).matches(), lineage);
            assertTrue(pattern.matcher(Verificaciones.engineFileKey("/h/validated/" + archivo.get("directorio"))).matches());
        }
    }

    // ------------------------------------------------------------------ línea de comandos

    static int cli(ByteArrayOutputStream buf, String... args) {
        return RenderTemplates.run(args, new PrintStream(buf, true, StandardCharsets.UTF_8));
    }

    @Test
    void cliGeneraYAudita(@TempDir Path tmp) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(0, cli(buf, "--spec", SPEC_PATH.toString(), "--out", tmp.toString(), "--audit"));
        String text = buf.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("schema=True  spec=lleno"), text);
        assertTrue(text.contains("generado  ACC-TPL-01  hocon/sisben-acc-tpl-01-acceptance-block.conf"), text);
        assertTrue(text.contains("omitido   ACC-TPL-03  reason-severity.csv.tpl  (severidades.habilitado = false)"), text);
    }

    @Test
    void cliSaleConUnoSiLaSpecNoPasa(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("mala.yaml");
        Files.writeString(bad, read(SPEC_PATH).replace("filePattern: '.*", "filePattern: '"), StandardCharsets.UTF_8);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(1, cli(buf, "--spec", bad.toString(), "--out", tmp.resolve("o").toString()));
        assertTrue(buf.toString(StandardCharsets.UTF_8).startsWith("ERROR: la spec no pasa las verificaciones:"));
        assertFalse(Files.exists(tmp.resolve("o")));
    }

    // ------------------------------------------------------------------ supuestos (AC-27, ADR-G2)

    static final Path DECISIONES = SKILL.resolve("references/decisiones-y-abiertos.md");
    static final Pattern LINEA_SUPUESTO = Pattern.compile("^supuesto  (S-ACC-\\d{2})  impacto=(.+)  donde=(.+)  texto=(.+)$");

    /** Texto sin backticks y con los espacios colapsados, para comparar la spec con la tabla Markdown. */
    static String normalizado(String s) {
        return s.replace("`", "").replaceAll("\\s+", " ").trim();
    }

    /** Filas S-ACC-xx de la tabla "Supuestos propios de este skill": id → [supuesto, impacto, dónde]. */
    static Map<String, List<String>> supuestosDelDocumento() throws IOException {
        String doc = read(DECISIONES);
        int from = doc.indexOf("## Supuestos propios de este skill");
        assertTrue(from >= 0, "falta la sección de supuestos en " + DECISIONES);
        int to = doc.indexOf("\n## ", from + 1);
        Map<String, List<String>> m = new TreeMap<>();
        for (String line : doc.substring(from, to < 0 ? doc.length() : to).split("\n")) {
            if (line.startsWith("| S-ACC-")) {
                String[] cells = line.split("\\|", -1);
                assertEquals(6, cells.length, "la fila debe tener Id | Supuesto | Impacto | Dónde: " + line);
                m.put(cells[1].trim(), List.of(cells[2].trim(), cells[3].trim(), cells[4].trim()));
            }
        }
        return m;
    }

    static List<String> lineasSupuesto(String text) {
        return text.lines().filter(l -> l.startsWith("supuesto  ")).collect(Collectors.toList());
    }

    /** Con --audit, una línea por supuesto de la spec, con impacto, después de las líneas de huecos. */
    @Test
    void auditListaLosSupuestosConImpacto(@TempDir Path tmp) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(0, cli(buf, "--spec", SPEC_PATH.toString(), "--out", tmp.toString(), "--audit"));
        List<String> lines = buf.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());
        List<String> sup = lineasSupuesto(String.join("\n", lines));
        assertEquals(List.of("S-ACC-01", "S-ACC-02", "S-ACC-03", "S-ACC-04", "S-ACC-05"),
                sup.stream().map(l -> l.split("  ")[1]).collect(Collectors.toList()));
        for (Auditoria.Supuesto s : Auditoria.supuestos(spec)) {
            assertFalse(s.impacto().isEmpty(), s.id());
            assertTrue(sup.contains(s.linea()), s.linea());
        }
        sup.forEach(l -> assertTrue(LINEA_SUPUESTO.matcher(l).matches(), l));
        int lastHueco = -1;
        int firstGenerado = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(" schema=")) {
                lastHueco = i;
            }
            if (lines.get(i).startsWith("generado  ") && i < firstGenerado) {
                firstGenerado = i;
            }
        }
        int firstSup = lines.indexOf(sup.get(0));
        assertTrue(lastHueco >= 0 && lastHueco < firstSup && lines.indexOf(sup.get(4)) < firstGenerado,
                "orden: huecos, supuestos, generado");
        // Con --audit los supuestos no se repiten en el reporte.
        assertEquals(5, sup.size());
    }

    /** Sin --audit, el reporte lista los mismos supuestos después de generado/omitido. */
    @Test
    void reporteListaLosSupuestos(@TempDir Path tmp) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(0, cli(buf, "--spec", SPEC_PATH.toString(), "--out", tmp.toString()));
        List<String> lines = buf.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());
        List<String> esperadas = Auditoria.supuestos(spec).stream().map(Auditoria.Supuesto::linea)
                .collect(Collectors.toList());
        assertEquals(5, esperadas.size());
        assertEquals(esperadas, lines.subList(lines.size() - 5, lines.size()));
        assertTrue(lines.subList(0, lines.size() - 5).stream()
                .allMatch(l -> l.startsWith("generado  ") || l.startsWith("omitido   ")), lines.toString());
        assertFalse(lines.stream().anyMatch(l -> l.contains(" schema=")));
    }

    /** El bloque es opcional: sin él, la spec valida, genera lo mismo y no imprime líneas de supuesto. */
    @Test
    void specSinSupuestosNoImprimeNiFalla(@TempDir Path tmp) throws IOException {
        Map<String, Object> sin = new LinkedHashMap<>(spec);
        assertNotNull(sin.remove("supuestos"));
        assertEquals(List.of(), Verificaciones.validateSchema(sin, schema));
        assertEquals(List.of(), Auditoria.supuestos(sin));
        Path f = tmp.resolve("sin-supuestos.yaml");
        Files.writeString(f, new org.yaml.snakeyaml.Yaml().dump(sin), StandardCharsets.UTF_8);
        for (boolean audit : List.of(false, true)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Path o = tmp.resolve("o-" + audit);
            String[] args = audit ? new String[] {"--spec", f.toString(), "--out", o.toString(), "--audit"}
                    : new String[] {"--spec", f.toString(), "--out", o.toString()};
            assertEquals(0, cli(buf, args));
            String text = buf.toString(StandardCharsets.UTF_8);
            assertEquals(List.of(), lineasSupuesto(text), text);
            assertTrue(text.contains("generado  ACC-TPL-05"), text);
            assertEquals(arbol(GEN_PATH), arbol(o));
        }
    }

    /** Schema: id con el patrón S-ACC-NN y los cuatro campos obligatorios en cada elemento. */
    @Test
    void schemaSupuestosExigeIdYCuatroCampos() {
        Map<String, Object> ok = mapOf("id", "S-ACC-09", "supuesto", "s", "impacto", "i", "donde", "d");
        assertEquals(List.of(), Verificaciones.validateSchema(mutated(spec, "supuestos", List.of(ok)), schema));
        assertEquals(List.of(), Verificaciones.validateSchema(mutated(spec, "supuestos", List.of()), schema));
        for (String bad : List.of("S-01", "S-ACC-1", "S-ACC-001", "s-acc-01", "VAL-SP01")) {
            Map<String, Object> m = new LinkedHashMap<>(ok);
            m.put("id", bad);
            assertFalse(Verificaciones.validateSchema(mutated(spec, "supuestos", List.of(m)), schema).isEmpty(), bad);
        }
        for (String campo : List.of("id", "supuesto", "impacto", "donde")) {
            Map<String, Object> m = new LinkedHashMap<>(ok);
            m.remove(campo);
            assertFalse(Verificaciones.validateSchema(mutated(spec, "supuestos", List.of(m)), schema).isEmpty(), campo);
        }
        Map<String, Object> extra = new LinkedHashMap<>(ok);
        extra.put("otro", "x");
        assertFalse(Verificaciones.validateSchema(mutated(spec, "supuestos", List.of(extra)), schema).isEmpty());
    }

    /** AC-27 / ADR-G2: la spec SISBEN declara los mismos supuestos que el documento, con el mismo impacto. */
    @Test
    void supuestosDeLaSpecIgualesALosDelDocumento() throws IOException {
        Map<String, List<String>> doc = supuestosDelDocumento();
        List<Auditoria.Supuesto> enSpec = Auditoria.supuestos(spec);
        assertEquals(doc.keySet(), enSpec.stream().map(Auditoria.Supuesto::id).collect(Collectors.toCollection(java.util.TreeSet::new)));
        assertEquals(enSpec.size(), doc.size(), "IDs repetidos en la spec");
        assertEquals(Set.of("S-ACC-01", "S-ACC-02", "S-ACC-03", "S-ACC-04", "S-ACC-05"), doc.keySet());
        for (Auditoria.Supuesto s : enSpec) {
            List<String> fila = doc.get(s.id());
            assertFalse(normalizado(fila.get(1)).isEmpty(), "sin impacto en el documento: " + s.id());
            assertEquals(normalizado(fila.get(1)), normalizado(s.impacto()), "impacto de " + s.id());
            assertEquals(normalizado(fila.get(2)), normalizado(s.donde()), "dónde de " + s.id());
        }
    }

    /** Toda marca ASSUMPTION de las plantillas y de la spec SISBEN cita un S-ACC-xx del documento. */
    @Test
    void marcasAssumptionCitanUnSupuestoDelDocumento() throws IOException {
        Set<String> ids = supuestosDelDocumento().keySet();
        Pattern marca = Pattern.compile("ASSUMPTION(?: (S-ACC-\\d{2}))?");
        List<Path> fuentes = new ArrayList<>(List.of(SPEC_PATH));
        try (Stream<Path> s = Files.list(TEMPLATES)) {
            s.filter(p -> p.toString().endsWith(".tpl")).forEach(fuentes::add);
        }
        Set<String> citados = new java.util.TreeSet<>();
        for (Path f : fuentes) {
            java.util.regex.Matcher m = marca.matcher(read(f));
            while (m.find()) {
                assertNotNull(m.group(1), "ASSUMPTION sin ID en " + f.getFileName());
                assertTrue(ids.contains(m.group(1)), m.group(1) + " no está en el documento");
                citados.add(m.group(1));
            }
        }
        // SPEC l. 92-99 → S-ACC-02; AcceptanceExpressionsSpec.scala.tpl:13 → S-ACC-04 (ADR-G2, punto 5).
        assertEquals(Set.of("S-ACC-02", "S-ACC-04"), citados);
    }

    @Test
    void cliSaleConDosSiFaltanArgumentos() {
        assertEquals(2, cli(new ByteArrayOutputStream(), "--spec"));
        assertEquals(2, cli(new ByteArrayOutputStream(), "--out", "x"));
        assertEquals(2, cli(new ByteArrayOutputStream(), "--otro", "x"));
    }
}
