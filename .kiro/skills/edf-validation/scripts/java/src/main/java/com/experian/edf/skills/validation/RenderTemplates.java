package com.experian.edf.skills.validation;

import static com.experian.edf.skills.validation.Verificaciones.list;
import static com.experian.edf.skills.validation.Verificaciones.map;
import static com.experian.edf.skills.validation.Verificaciones.strings;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Genera los artefactos de la etapa Validation (EDF/Oxygen) desde un source-spec.
 *
 * <pre>
 * Uso: java -jar scripts/java/target/render-templates.jar --spec source-spec.yaml --out carpeta [--solo-verificar]
 * </pre>
 *
 * Pasos: (1) carga y valida el source-spec contra el esquema; (2) verificaciones semánticas; (3) arma en
 * memoria las plantillas rellenas, los CSV de catálogo y manifest.json (archivos, sha256 y supuestos
 * derivados de las marcas ASSUMPTION); (4) falla sin escribir nada si queda algún hueco sin resolver, una
 * marca ASSUMPTION sin ID o un patrón prohibido (credenciales) en cualquiera de ellos; (5) si no hay
 * problemas, escribe todo. No ejecuta nada en el clúster ni modifica repositorios: solo escribe en --out.
 */
public final class RenderTemplates {

    public static final Path SKILL_DIR = findSkillDir();
    public static final Path TEMPLATES_DIR = SKILL_DIR.resolve("assets/templates");
    public static final Path SCHEMA_PATH = SKILL_DIR.resolve("assets/schema/source-spec.schema.yaml");

    static final Pattern PLACEHOLDER_RE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");
    /** ID de un supuesto de references/decisiones-y-abiertos.md (ADR-G1). */
    static final String ID_SUPUESTO = "F-\\d{2}|VAL-P[AT]\\d{2}|VAL-SP\\d{2}";
    /** Marca válida: ASSUMPTION seguido del ID en la misma línea. */
    static final Pattern MARCA_RE = Pattern.compile("\\bASSUMPTION[ \\t]+(" + ID_SUPUESTO + ")\\b");
    static final Pattern ASSUMPTION_RE = Pattern.compile("\\bASSUMPTION\\b");
    static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("settings-artifactory", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<password>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpassword\\s*[=:]\\s*\\S", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(api[_-]?key|secret|token)\\s*[=:]\\s*['\\\"]?[A-Za-z0-9/+]{12,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("AKIA[0-9A-Z]{16}"));

    /** Plantilla → ruta destino (relativa a --out). Los {{huecos}} de la ruta se rellenan con el contexto. */
    public static final Map<String, String> TEMPLATE_TARGETS = new LinkedHashMap<>();

    static {
        TEMPLATE_TARGETS.put("capa-a/FieldSpec.scala.tpl", "src/main/scala/{{ruta_comun}}/FieldSpec.scala");
        TEMPLATE_TARGETS.put("capa-a/FieldValidators.scala.tpl", "src/main/scala/{{ruta_comun}}/FieldValidators.scala");
        TEMPLATE_TARGETS.put("capa-a/FieldValidatorsSpec.scala.tpl", "src/test/scala/{{ruta_comun}}/FieldValidatorsSpec.scala");
        TEMPLATE_TARGETS.put("SourceSpec.scala.tpl", "src/main/scala/{{ruta_fuente}}/config/{{Fuente}}Spec.scala");
        TEMPLATE_TARGETS.put("ValidatedEvent.scala.tpl", "src/main/scala/{{ruta_fuente}}/{{Fuente}}ValidatedEvent.scala");
        TEMPLATE_TARGETS.put("FileMetadata.scala.tpl", "src/main/scala/{{ruta_fuente}}/validator/{{Fuente}}FileMetadata.scala");
        TEMPLATE_TARGETS.put("ValidationProcessor.scala.tpl",
                "src/main/scala/{{ruta_fuente}}/processor/{{Fuente}}ValidationProcessor.scala");
        TEMPLATE_TARGETS.put("SourceProcessorSpec.scala.tpl",
                "src/test/scala/{{ruta_fuente}}/{{Fuente}}ValidationProcessorSpec.scala");
        TEMPLATE_TARGETS.put("fixture.txt.tpl", "src/test/resources/{{fuente}}/{{fixture_nombre_archivo}}");
        TEMPLATE_TARGETS.put("validation-block.conf.tpl",
                "env/application-conf/innersource/propuesta/validation-configs-{{fuente}}.conf");
        TEMPLATE_TARGETS.put("lookups.conf.tpl", "env/application-conf/innersource/propuesta/lookups-{{fuente}}.conf");
        TEMPLATE_TARGETS.put("codigos-error.csv.tpl", "env/application-conf/innersource/propuesta/codigos-error-{{fuente}}.csv");
    }

    /** Resultado de rellenar un texto: el texto y los huecos que no tenían valor. */
    record Relleno(String text, Set<String> missing) {
    }

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

    /** Rellena los huecos en una sola pasada; devuelve el texto y los huecos sin valor. */
    static Relleno fill(String text, Map<String, String> context) {
        Set<String> missing = new TreeSet<>();
        Matcher m = PLACEHOLDER_RE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = context.get(name);
            if (value == null) {
                missing.add(name);
                value = m.group(0);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return new Relleno(out.toString(), missing);
    }

    static Set<String> findPlaceholders(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER_RE.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /** Huecos usados por cada plantilla (cuerpo + ruta destino). */
    public static Map<String, Set<String>> templatePlaceholders(Path templatesDir) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        TEMPLATE_TARGETS.forEach((rel, target) -> {
            Set<String> names = findPlaceholders(Verificaciones.readText(templatesDir.resolve(rel)));
            names.addAll(findPlaceholders(target));
            result.put(rel, names);
        });
        return result;
    }

    /** Huecos declarados en el esquema (claves x-placeholders). */
    public static Set<String> schemaPlaceholders(Path schemaPath) {
        Set<String> found = new TreeSet<>();
        walk(Verificaciones.loadYaml(schemaPath), found);
        return found;
    }

    private static void walk(Object node, Set<String> found) {
        if (node instanceof Map<?, ?> m) {
            Object ph = m.get("x-placeholders");
            if (ph != null) {
                list(ph).forEach(v -> found.add((String) v));
            }
            m.values().forEach(v -> walk(v, found));
        } else if (node instanceof List<?> l) {
            l.forEach(v -> walk(v, found));
        }
    }

    public static List<String> scanForbidden(String text) {
        List<String> out = new ArrayList<>();
        FORBIDDEN_PATTERNS.forEach(p -> {
            if (p.matcher(text).find()) {
                out.add(p.pattern());
            }
        });
        return out;
    }

    static void write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** IDs de las marcas {@code ASSUMPTION <ID>} del texto, en orden de aparición (ADR-G1). */
    static List<String> marcasSupuesto(String text) {
        List<String> ids = new ArrayList<>();
        Matcher m = MARCA_RE.matcher(text);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** Números de línea (desde 1) con una marca ASSUMPTION que no lleva un ID reconocible en la misma línea. */
    static List<Integer> marcasSinId(String text) {
        List<Integer> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher any = ASSUMPTION_RE.matcher(lines[i]);
            while (any.find()) {
                Matcher ok = MARCA_RE.matcher(lines[i]);
                if (!ok.find(any.start()) || ok.start() != any.start()) {
                    out.add(i + 1);
                }
            }
        }
        return out;
    }

    /**
     * Catálogos SINTÉTICOS para tests/ejecución local (no son datos oficiales). Arma ruta → contenido sin
     * escribir; renderAll los escribe solo si toda la salida pasa las verificaciones (AC-22, AC-23).
     */
    static Map<Path, String> lookupCsvs(Map<String, Object> spec, Path out) {
        Map<Path, String> csvs = new LinkedHashMap<>();
        Path base = out.resolve("src/test/resources").resolve((String) map(spec.get("fuente")).get("fuente")).resolve("lookup");
        for (Object o : list(spec.get("catalogos"))) {
            Map<String, Object> cat = map(o);
            String delim = (String) cat.get("delimitador");
            List<String> lines = new ArrayList<>();
            lines.add(String.join(delim, strings(cat.get("columnas"))));
            list(cat.get("filas_sinteticas")).forEach(r -> lines.add(String.join(delim, strings(r))));
            csvs.put(base.resolve((String) cat.get("archivo")), String.join("\n", lines) + "\n");
        }
        return csvs;
    }

    /**
     * DEPRECADO (2026-10-21, plan G #1): escribía cada CSV al armarlo, antes de saber si el resto de la salida
     * pasaba las verificaciones. renderAll ya no lo usa: arma con {@link #lookupCsvs} y escribe al final.
     * Se conserva por compatibilidad (ver scripts/DEPRECADOS.md).
     */
    @Deprecated
    static List<Path> writeLookupCsvs(Map<String, Object> spec, Path out) {
        Map<Path, String> csvs = lookupCsvs(spec, out);
        csvs.forEach(RenderTemplates::write);
        return new ArrayList<>(csvs.keySet());
    }

    /**
     * Genera en dos fases (AC-22, todo o nada). (a) Arma en memoria las plantillas rellenas, los CSV de
     * catálogo y manifest.json, y revisa todo: huecos sin valor o sin resolver, marcas ASSUMPTION sin ID
     * (AC-18a) y patrones prohibidos. (b) Si hubo algún problema lanza SpecError sin escribir nada; si no,
     * escribe cada archivo. manifest["supuestos"] es el conjunto ordenado de IDs de las marcas (ADR-G1).
     */
    public static Map<String, Object> renderAll(Map<String, Object> spec, Path out, Path templatesDir) {
        Map<String, String> context = Contexto.build(spec);
        Map<Path, String> files = new LinkedHashMap<>();
        Set<String> supuestos = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        // Fase (a): todo en memoria.
        for (Map.Entry<String, String> e : TEMPLATE_TARGETS.entrySet()) {
            String rel = e.getKey();
            Relleno body = fill(Verificaciones.readText(templatesDir.resolve(rel)), context);
            Relleno target = fill(e.getValue(), context);
            Set<String> missing = new TreeSet<>(body.missing());
            missing.addAll(target.missing());
            if (!missing.isEmpty()) {
                problems.add(rel + ": huecos sin valor " + Py.str(missing));
                continue;
            }
            Set<String> leftover = new TreeSet<>(findPlaceholders(body.text()));
            if (!leftover.isEmpty()) {
                problems.add(rel + ": quedaron huecos sin resolver " + Py.str(leftover));
            }
            List<String> forbidden = scanForbidden(body.text());
            if (!forbidden.isEmpty()) {
                problems.add(rel + ": patrón prohibido en la salida " + Py.str(forbidden));
            }
            List<Integer> sinId = marcasSinId(body.text());
            if (!sinId.isEmpty()) {
                problems.add(rel + ": marca ASSUMPTION sin ID en las líneas " + Py.str(sinId) + " (AC-18a)");
            }
            supuestos.addAll(marcasSupuesto(body.text()));
            files.put(out.resolve(target.text()), body.text());
        }
        for (Map.Entry<Path, String> csv : lookupCsvs(spec, out).entrySet()) {
            List<String> forbidden = scanForbidden(csv.getValue());
            if (!forbidden.isEmpty()) {
                problems.add(out.relativize(csv.getKey()).toString().replace('\\', '/') + ": patrón prohibido en la salida "
                        + Py.str(forbidden));
            }
            files.put(csv.getKey(), csv.getValue());
        }

        Map<String, Object> ambiente = map(spec.get("ambiente"));
        List<Object> archivos = new ArrayList<>();
        for (Map.Entry<Path, String> f : files.entrySet()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("ruta", out.relativize(f.getKey()).toString().replace('\\', '/'));
            a.put("sha256", Py.sha256(f.getValue().getBytes(StandardCharsets.UTF_8)));
            archivos.add(a);
        }
        Map<String, Object> requisitos = new LinkedHashMap<>();
        requisitos.put("oxygen.application.lambda", ambiente.get("lambda"));
        requisitos.put("oxygen.application.historicAttributes", ambiente.get("historic_attributes"));
        requisitos.put("oxygen.features.enable.defaultOutputStructure", ambiente.get("default_output_structure"));
        requisitos.put("oxygen.features.enable.emitStockMetrics", "debe existir (se lee con .get)");
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("skill", "edf-validation-stage");
        manifest.put("spec_version", spec.get("spec_version"));
        manifest.put("fuente", map(spec.get("fuente")).get("Fuente"));
        manifest.put("estado", "PROPUESTA - requiere aprobación del técnico antes de copiar al fork (Human-in-the-Loop)");
        manifest.put("compilacion", "NO CERTIFICADA (F-01: sin acceso al JAR del framework)");
        manifest.put("archivos", archivos);
        manifest.put("requisitos_de_ambiente", requisitos);
        // DEPRECADO (2026-10-21, ADR-G1): antes era la lista fija List.of("F-04", "F-07", "F-08", "F-09", "F-11",
        // "F-12", "F-13", "F-14", "VAL-PT01", "VAL-PT03", "VAL-PT04", "VAL-PA04"). Ahora se deriva de las marcas.
        manifest.put("supuestos", new ArrayList<>(supuestos));
        String manifestText = Py.jsonDumps(manifest, 2) + "\n";
        List<String> forbiddenManifest = scanForbidden(manifestText);
        if (!forbiddenManifest.isEmpty()) {
            problems.add("manifest.json: patrón prohibido en la salida " + Py.str(forbiddenManifest));
        }
        if (!problems.isEmpty()) {
            throw new SpecError(String.join("; ", problems)); // fase (b) sin escribir nada
        }
        // Fase (b): sin problemas, se escribe todo.
        files.forEach(RenderTemplates::write);
        write(out.resolve("manifest.json"), manifestText);
        return manifest;
    }

    public static Map<String, Object> renderAll(Map<String, Object> spec, Path out) {
        return renderAll(spec, out, TEMPLATES_DIR);
    }

    private static final String USO = "uso: render-templates --spec SPEC [--out OUT] [--solo-verificar]";

    /** Ejecuta la herramienta: 0 bien; 2 spec inválida, error de generación o de uso; 1 error inesperado. */
    public static int run(String[] argv, PrintStream out, PrintStream err) {
        Path specPath = null;
        Path outDir = null;
        boolean soloVerificar = false;
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (a.equals("--solo-verificar")) {
                soloVerificar = true;
            } else if ((a.equals("--spec") || a.equals("--out")) && i + 1 < argv.length) {
                Path value = Path.of(argv[++i]);
                if (a.equals("--spec")) {
                    specPath = value;
                } else {
                    outDir = value;
                }
            } else {
                err.println(USO);
                err.println("argumento no reconocido o sin valor: " + a);
                return 2;
            }
        }
        if (specPath == null) {
            err.println(USO);
            err.println("falta el argumento obligatorio --spec");
            return 2;
        }
        Map<String, Object> manifest;
        try {
            Map<String, Object> spec = Verificaciones.loadSpec(specPath);
            List<String> errors = Verificaciones.verifySpec(spec, SCHEMA_PATH);
            if (!errors.isEmpty()) {
                err.println("El source-spec NO pasa la verificación:");
                errors.forEach(e -> err.println("  - " + e));
                return 2;
            }
            out.println("source-spec OK (" + list(spec.get("campos")).size() + " campos, "
                    + Verificaciones.allCodes(spec).size() + " códigos).");
            if (soloVerificar) {
                return 0;
            }
            if (outDir == null) {
                err.println(USO);
                err.println("--out es obligatorio salvo con --solo-verificar");
                return 2;
            }
            manifest = renderAll(spec, outDir);
        } catch (SpecError | UncheckedIOException | YAMLException exc) {
            err.println("ERROR: " + exc.getMessage());
            return 2;
        } catch (RuntimeException exc) {
            err.println("ERROR inesperado: " + exc);
            return 1;
        }
        out.println("Generados " + list(manifest.get("archivos")).size() + " archivos en " + outDir + " (ver manifest.json).");
        out.println("Estado: PROPUESTA. Revisar el diff y aprobar antes de copiar al fork.");
        return 0;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }
}
