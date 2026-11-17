package com.experian.edf.skills.acceptance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de plantillas del skill (subconjunto propio):
 * <pre>
 *   {{ruta.al.valor}}                valor escalar; falla si no existe o es null
 *   {{ruta|hocon}} / {{ruta|scala}}  escapa \ y " para un literal entre comillas
 *   {{ruta|json}}                    serializa el valor como JSON
 *   {{#cada ruta}}...{{/cada}}       repite el bloque por cada elemento (objeto) de la lista
 *   {{#si ruta}}...{{/si}}           incluye el bloque si el valor es verdadero
 *   {{> archivo.tpl}}                incluye otra plantilla (parcial)
 * </pre>
 * Las etiquetas de bloque solas en su línea no dejan línea en blanco. Las rutas se buscan primero en
 * el elemento actual del bucle y después en los ámbitos exteriores, hasta la raíz de la spec.
 */
public final class Plantillas {

    static final Pattern TAG_RE = Pattern.compile("\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
    private static final Pattern STANDALONE_RE = Pattern.compile(
            "^[ \\t]*(\\{\\{[#/>][^}]*\\}\\})[ \\t]*\\r?\\n", Pattern.MULTILINE | Pattern.UNIX_LINES);
    private static final Pattern PARTIAL_RE = Pattern.compile("\\{\\{>\\s*([^}]+?)\\s*\\}\\}");
    private static final Pattern PATH_RE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final List<String> FILTERS = List.of("hocon", "scala", "json");
    private static final int MAX_PARTIAL_DEPTH = 5;

    /** Valor ausente (distinto de null, que es un valor presente y nulo). */
    static final Object MISSING = new Object();

    sealed interface Node permits Text, Var, Block {
    }

    record Text(String value) implements Node {
    }

    record Var(String path, String filter) implements Node {
    }

    record Block(String kind, String path, List<Node> children) implements Node {
    }

    private Plantillas() {
    }

    // ------------------------------------------------------------------------------------- parseo

    static String readText(Path file) {
        try {
            // Igual que read_text de Python: los saltos \r\n y \r llegan como \n.
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String expandPartials(String source, Path templatesDir, int depth) {
        if (depth > MAX_PARTIAL_DEPTH) {
            throw new TemplateError("parciales anidados demasiado profundos");
        }
        String flattened = STANDALONE_RE.matcher(source).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        Path dir = templatesDir.toAbsolutePath().normalize();
        Matcher m = PARTIAL_RE.matcher(flattened);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).strip();
            Path partial = dir.resolve(name).normalize();
            if (!dir.equals(partial.getParent())) {
                throw new TemplateError("parcial fuera del directorio de plantillas: " + name);
            }
            if (!Files.isRegularFile(partial)) {
                throw new TemplateError("parcial inexistente: " + name);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(expandPartials(readText(partial), templatesDir, depth + 1)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Convierte el texto de la plantilla en un árbol inmutable de nodos. */
    static List<Node> parse(String source) {
        List<Node> root = new ArrayList<>();
        Deque<Object[]> stack = new ArrayDeque<>(); // {kind, path, parent}
        List<Node> current = root;
        int pos = 0;
        Matcher m = TAG_RE.matcher(source);
        while (m.find()) {
            if (m.start() > pos) {
                current.add(new Text(source.substring(pos, m.start())));
            }
            pos = m.end();
            String tag = m.group(1).strip();
            if (tag.startsWith("#")) {
                String rest = tag.substring(1);
                int sp = rest.indexOf(' ');
                String kind = sp < 0 ? rest : rest.substring(0, sp);
                String path = sp < 0 ? "" : rest.substring(sp + 1);
                if (!(kind.equals("cada") || kind.equals("si")) || path.strip().isEmpty()) {
                    throw new TemplateError("bloque desconocido: {{" + tag + "}}");
                }
                stack.push(new Object[] {kind, path.strip(), current});
                current = new ArrayList<>();
            } else if (tag.startsWith("/")) {
                if (stack.isEmpty() || !stack.peek()[0].equals(tag.substring(1).strip())) {
                    throw new TemplateError("cierre inesperado: {{" + tag + "}}");
                }
                Object[] top = stack.pop();
                @SuppressWarnings("unchecked")
                List<Node> parent = (List<Node>) top[2];
                parent.add(new Block((String) top[0], (String) top[1], List.copyOf(current)));
                current = parent;
            } else {
                int bar = tag.indexOf('|');
                String path = bar < 0 ? tag : tag.substring(0, bar);
                String filt = bar < 0 ? "" : tag.substring(bar + 1).strip();
                if (filt.isEmpty()) {
                    filt = null;
                }
                if (filt != null && !FILTERS.contains(filt)) {
                    throw new TemplateError("filtro desconocido: " + filt);
                }
                if (!PATH_RE.matcher(path.strip()).matches()) {
                    throw new TemplateError("hueco inválido: {{" + tag + "}}");
                }
                current.add(new Var(path.strip(), filt));
            }
        }
        if (!stack.isEmpty()) {
            throw new TemplateError("bloque sin cerrar: " + stack.peek()[0] + " " + stack.peek()[1]);
        }
        if (pos < source.length()) {
            current.add(new Text(source.substring(pos)));
        }
        return List.copyOf(root);
    }

    static List<Node> loadTemplate(String name, Path templatesDir) {
        return parse(expandPartials(readText(templatesDir.resolve(name)), templatesDir, 0));
    }

    // ------------------------------------------------------------------------------------- render

    static Object resolveIn(Object scope, String[] parts) {
        Object value = scope;
        for (String part : parts) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return MISSING;
            }
            value = map.get(part);
        }
        return value;
    }

    static Object lookup(String path, List<Object> scopes) {
        String[] parts = path.split("\\.");
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Object scope = scopes.get(i);
            if (scope instanceof Map<?, ?> map && map.containsKey(parts[0])) {
                Object value = resolveIn(scope, parts);
                if (value == MISSING) {
                    throw new TemplateError("hueco sin resolver: " + path);
                }
                return value;
            }
        }
        throw new TemplateError("hueco sin resolver: " + path);
    }

    static String formatValue(Object value, String filt, String path) {
        if ("json".equals(filt)) {
            return Py.jsonDumps(value);
        }
        if (value == null) {
            throw new TemplateError("hueco con valor nulo: " + path);
        }
        String text;
        if (value instanceof Boolean b) {
            text = b ? "true" : "false";
        } else if (value instanceof String || value instanceof Double || Py.isInteger(value)) {
            text = Py.str(value);
        } else {
            throw new TemplateError("hueco no escalar sin filtro json: " + path);
        }
        if ("hocon".equals(filt) || "scala".equals(filt)) {
            if (text.contains("\n") || text.contains("\r")) {
                throw new TemplateError("salto de línea no permitido en literal: " + path);
            }
            text = text.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        return text;
    }

    static String render(List<Node> nodes, List<Object> scopes) {
        StringBuilder out = new StringBuilder();
        for (Node node : nodes) {
            if (node instanceof Text t) {
                out.append(t.value());
            } else if (node instanceof Var v) {
                out.append(formatValue(lookup(v.path(), scopes), v.filter(), v.path()));
            } else if (node instanceof Block b && b.kind().equals("si")) {
                if (Py.truthy(lookup(b.path(), scopes))) {
                    out.append(render(b.children(), scopes));
                }
            } else if (node instanceof Block b) {
                Object items = lookup(b.path(), scopes);
                if (!(items instanceof List<?> list)) {
                    throw new TemplateError("#cada requiere una lista: " + b.path());
                }
                for (Object item : list) {
                    if (!(item instanceof Map)) {
                        throw new TemplateError("#cada requiere objetos: " + b.path());
                    }
                    List<Object> inner = new ArrayList<>(scopes);
                    inner.add(item);
                    out.append(render(b.children(), inner));
                }
            }
        }
        return out.toString();
    }

    public static String renderString(String source, Map<String, Object> spec, Path templatesDir,
                                      Map<String, Object> extraScope) {
        List<Object> scopes = new ArrayList<>();
        scopes.add(spec);
        if (extraScope != null) {
            scopes.add(extraScope);
        }
        return render(parse(expandPartials(source, templatesDir, 0)), scopes);
    }
}
