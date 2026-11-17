package com.experian.edf.skills.acceptance;

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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Renderiza las plantillas del skill edf-acceptance-stage a partir de una source-spec.
 *
 * <pre>
 * Uso: java -jar scripts/java/target/render-templates.jar --spec &lt;source-spec.yaml&gt; --out &lt;dir&gt;
 *                [--schema &lt;schema.yaml&gt;] [--templates &lt;dir&gt;] [--audit]
 * </pre>
 *
 * Sale con código 1 si la spec no valida, si una verificación del inventario falla o si queda algún
 * hueco sin resolver. La sintaxis de las plantillas está en {@link Plantillas}.
 *
 * <p>Si la spec declara el bloque opcional {@code supuestos} (AC-27, ADR-G2), imprime una línea
 * {@code supuesto  S-ACC-xx  impacto=…  donde=…  texto=…} por supuesto: con {@code --audit}, después de
 * los huecos; sin {@code --audit}, después de {@code generado}/{@code omitido}.
 */
public final class RenderTemplates {

    /** Raíz del skill: el directorio que contiene assets/templates, buscado desde el jar o las clases. */
    public static final Path SKILL_DIR = findSkillDir();
    public static final Path DEFAULT_TEMPLATES = SKILL_DIR.resolve("assets/templates");
    public static final Path DEFAULT_SCHEMA = SKILL_DIR.resolve("assets/schema/source-spec.schema.yaml");

    /** Resultado de la generación: archivos generados y plantillas omitidas con su motivo. */
    public record Reporte(List<Map<String, String>> generados, List<Map<String, String>> omitidos) {
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(Path path) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yaml.load(Plantillas.readText(path));
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadManifest(Path templatesDir) {
        return (List<Map<String, Object>>) loadYaml(templatesDir.resolve("manifest.yaml")).get("plantillas");
    }

    static Path safeOutput(Path outDir, String relative) {
        Path base = outDir.toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new TemplateError("ruta de salida fuera del directorio: " + relative);
        }
        return target;
    }

    /** Genera los artefactos en el orden del manifiesto. */
    @SuppressWarnings("unchecked")
    public static Reporte generate(Map<String, Object> spec, Path outDir, Path templatesDir) {
        Reporte report = new Reporte(new ArrayList<>(), new ArrayList<>());
        for (Map<String, Object> entry : loadManifest(templatesDir)) {
            if (Py.truthy(entry.get("parcial"))) {
                continue;
            }
            String id = (String) entry.get("id");
            String plantilla = (String) entry.get("plantilla");
            String condicion = (String) entry.get("condicion");
            if (Py.truthy(condicion) && !Py.truthy(Plantillas.lookup(condicion, List.of(spec)))) {
                report.omitidos().add(Map.of("id", id, "plantilla", plantilla, "motivo", condicion + " = false"));
                continue;
            }
            List<Plantillas.Node> nodes = Plantillas.loadTemplate(plantilla, templatesDir);
            List<Object> items = new ArrayList<>();
            if (Py.truthy(entry.get("por_cada"))) {
                items.addAll((List<Object>) Plantillas.lookup((String) entry.get("por_cada"), List.of(spec)));
            } else {
                items.add(null);
            }
            for (Object item : items) {
                List<Object> scopes = item == null ? List.of(spec) : List.of(spec, item);
                String relative = Plantillas.render(Plantillas.parse((String) entry.get("salida")), scopes);
                String content = Plantillas.render(nodes, scopes);
                Matcher leftover = Plantillas.TAG_RE.matcher(content);
                if (leftover.find()) {
                    throw new TemplateError("hueco sin resolver en " + relative + ": " + leftover.group(0));
                }
                Path target = safeOutput(outDir, relative);
                try {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                report.generados().add(Map.of("id", id, "archivo", relative));
            }
        }
        return report;
    }

    public static Reporte generate(Map<String, Object> spec, Path outDir) {
        return generate(spec, outDir, DEFAULT_TEMPLATES);
    }

    private static final String USO = "uso: render-templates --spec SPEC --out OUT [--schema SCHEMA] "
            + "[--templates TEMPLATES] [--audit]";

    /** Ejecuta la herramienta y devuelve el código de salida (0 bien, 1 spec o plantilla inválida, 2 uso). */
    public static int run(String[] argv, PrintStream out) {
        Path spec = null;
        Path outDir = null;
        Path schema = DEFAULT_SCHEMA;
        Path templates = DEFAULT_TEMPLATES;
        boolean audit = false;
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (a.equals("--audit")) {
                audit = true;
                continue;
            }
            if (!List.of("--spec", "--out", "--schema", "--templates").contains(a) || i + 1 >= argv.length) {
                out.println(USO);
                out.println("argumento no reconocido o sin valor: " + a);
                return 2;
            }
            Path value = Path.of(argv[++i]);
            switch (a) {
                case "--spec" -> spec = value;
                case "--out" -> outDir = value;
                case "--schema" -> schema = value;
                default -> templates = value;
            }
        }
        if (spec == null || outDir == null) {
            out.println(USO);
            out.println("faltan argumentos obligatorios: --spec y --out");
            return 2;
        }

        Map<String, Object> specData = loadYaml(spec);
        Map<String, Object> schemaData = loadYaml(schema);
        List<String> problems = new ArrayList<>(Verificaciones.validateSchema(specData, schemaData));
        try {
            problems.addAll(Verificaciones.verifySpec(specData));
        } catch (RuntimeException e) {
            // Una spec que no cumple el schema puede no tener la forma que esperan las verificaciones.
            if (problems.isEmpty()) {
                problems.add("la spec no tiene la forma esperada: " + e);
            }
        }
        if (!problems.isEmpty()) {
            out.println("ERROR: la spec no pasa las verificaciones:");
            problems.forEach(p -> out.println("  - " + p));
            return 1;
        }
        // AC-27 / ADR-G2: los supuestos de la spec se listan con --audit (después de los huecos) o, sin
        // --audit, en el reporte (después de generado/omitido). Una spec sin el bloque no imprime nada.
        List<Auditoria.Supuesto> supuestos = Auditoria.supuestos(specData);
        if (audit) {
            List<Auditoria.Fila> rows = Auditoria.audit(specData, schemaData, templates);
            long undefined = rows.stream().filter(r -> !r.enSchema()).count();
            for (Auditoria.Fila r : rows) {
                out.println(String.format("%-40s %-55s schema=%-5s spec=%s",
                        r.plantilla(), r.hueco(), r.enSchema() ? "True" : "False", r.enSpec()));
            }
            supuestos.forEach(s -> out.println(s.linea()));
            if (undefined > 0) {
                out.println("ERROR: " + undefined + " huecos no definidos en el schema");
                return 1;
            }
        }
        Reporte report;
        try {
            report = generate(specData, outDir, templates);
        } catch (TemplateError exc) {
            out.println("ERROR: " + exc.getMessage());
            return 1;
        }
        report.generados().forEach(g -> out.println("generado  " + g.get("id") + "  " + g.get("archivo")));
        report.omitidos().forEach(o -> out.println(
                "omitido   " + o.get("id") + "  " + o.get("plantilla") + "  (" + o.get("motivo") + ")"));
        if (!audit) {
            supuestos.forEach(s -> out.println(s.linea()));
        }
        return 0;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.exit(run(args, out));
    }
}
