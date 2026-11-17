package com.experian.edf.skills.acceptance;

import com.experian.edf.skills.acceptance.Plantillas.Block;
import com.experian.edf.skills.acceptance.Plantillas.Node;
import com.experian.edf.skills.acceptance.Plantillas.Text;
import com.experian.edf.skills.acceptance.Plantillas.Var;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auditoría de huecos: cada {{...}} de cada plantilla, si está en el schema y si la spec lo llena. También
 * lee los supuestos declarados en la spec ({@link #supuestos}), que el CLI lista con los huecos (AC-27).
 */
public final class Auditoria {

    /** Una fila de la auditoría ({@code en_spec}: lleno, nulo o ausente). */
    public record Fila(String plantilla, String hueco, boolean enSchema, String enSpec) {
    }

    private Auditoria() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaChild(Object node, String part) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        Object props = map.get("properties");
        if (!(props instanceof Map<?, ?> p)) {
            return null;
        }
        Object child = p.get(part);
        return child instanceof Map ? (Map<String, Object>) child : null;
    }

    static Map<String, Object> schemaLookup(String path, List<Object> scopes) {
        String[] parts = path.split("\\.");
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Object scope = scopes.get(i);
            if (schemaChild(scope, parts[0]) == null) {
                continue;
            }
            Object node = scope;
            for (String part : parts) {
                node = schemaChild(node, part);
                if (node == null) {
                    return null;
                }
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> found = (Map<String, Object>) node;
            return found;
        }
        return null;
    }

    private static String pathOf(Node node) {
        return node instanceof Var v ? v.path() : ((Block) node).path();
    }

    /** Recorre TODAS las ramas (incluidas las deshabilitadas) contra el schema y contra la spec. */
    static void auditNodes(List<Node> nodes, List<Object> schemaScopes, List<Object> dataScopes,
                           String template, List<Fila> rows) {
        for (Node node : nodes) {
            if (node instanceof Text) {
                continue;
            }
            String path = pathOf(node);
            Map<String, Object> schemaNode = schemaLookup(path, schemaScopes);
            Object value;
            try {
                value = dataScopes.isEmpty() ? Plantillas.MISSING : Plantillas.lookup(path, dataScopes);
            } catch (TemplateError e) {
                value = Plantillas.MISSING;
            }
            String status = value == Plantillas.MISSING ? "ausente" : value == null ? "nulo" : "lleno";
            rows.add(new Fila(template, path, schemaNode != null, status));
            if (node instanceof Block b && b.kind().equals("cada")) {
                Object itemsSchema = schemaNode == null ? Map.of() : schemaNode.getOrDefault("items", Map.of());
                Object first = value instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map ? l.get(0) : null;
                List<Object> childData = new ArrayList<>();
                if (first != null) {
                    childData.addAll(dataScopes);
                    childData.add(first);
                }
                List<Object> childSchema = new ArrayList<>(schemaScopes);
                childSchema.add(itemsSchema);
                auditNodes(b.children(), childSchema, childData, template, rows);
            } else if (node instanceof Block b) {
                auditNodes(b.children(), schemaScopes, dataScopes, template, rows);
            }
        }
    }

    /** Copia del schema con los $ref locales (#/$defs/...) sustituidos. */
    @SuppressWarnings("unchecked")
    static Object inlineRefs(Object node, Map<String, Object> root) {
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String r && r.startsWith("#/$defs/")) {
                Map<String, Object> defs = (Map<String, Object>) root.get("$defs");
                Object target = defs.get(r.substring(r.lastIndexOf('/') + 1));
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) inlineRefs(target, root));
                map.forEach((k, v) -> {
                    if (!"$ref".equals(k)) {
                        merged.put((String) k, v);
                    }
                });
                Map<String, Object> out = new LinkedHashMap<>();
                merged.forEach((k, v) -> out.put(k, inlineRefs(v, root)));
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), inlineRefs(v, root)));
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(inlineRefs(v, root)));
            return out;
        }
        return node;
    }

    /**
     * Un supuesto del bloque opcional {@code supuestos} de la spec (AC-27, ADR-G2): id S-ACC-xx, texto,
     * impacto y dónde está.
     */
    public record Supuesto(String id, String supuesto, String impacto, String donde) {

        /** Línea del reporte: {@code supuesto  S-ACC-01  impacto=…  donde=…  texto=…}. */
        public String linea() {
            return "supuesto  " + id + "  impacto=" + impacto + "  donde=" + donde + "  texto=" + supuesto;
        }
    }

    private static String unaLinea(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    /**
     * Supuestos declarados en la spec, en su orden. Una spec sin el bloque (o con otra forma) da una lista
     * vacía: el bloque es opcional y no hace fallar la generación.
     */
    public static List<Supuesto> supuestos(Map<String, Object> spec) {
        List<Supuesto> out = new ArrayList<>();
        if (spec != null && spec.get("supuestos") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add(new Supuesto(unaLinea(m.get("id")), unaLinea(m.get("supuesto")),
                            unaLinea(m.get("impacto")), unaLinea(m.get("donde"))));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Fila> audit(Map<String, Object> spec, Map<String, Object> schema, Path templatesDir) {
        Map<String, Object> inlined = (Map<String, Object>) inlineRefs(schema, schema);
        List<Fila> rows = new ArrayList<>();
        for (Map<String, Object> entry : RenderTemplates.loadManifest(templatesDir)) {
            String plantilla = (String) entry.get("plantilla");
            List<Node> nodes = Plantillas.loadTemplate(plantilla, templatesDir);
            List<Object> schemaScopes = new ArrayList<>(List.of(inlined));
            List<Object> dataScopes = new ArrayList<>(List.of(spec));
            if (Py.truthy(entry.get("por_cada"))) {
                String porCada = (String) entry.get("por_cada");
                Map<String, Object> found = schemaLookup(porCada, schemaScopes);
                Object itemsSchema = found == null ? Map.of() : found.getOrDefault("items", Map.of());
                List<Object> items = (List<Object>) Plantillas.lookup(porCada, dataScopes);
                schemaScopes.add(itemsSchema);
                dataScopes.add(items.get(0));
            }
            if (Py.truthy(entry.get("salida"))) {
                auditNodes(Plantillas.parse((String) entry.get("salida")), schemaScopes, dataScopes,
                        plantilla + "#salida", rows);
            }
            auditNodes(nodes, schemaScopes, dataScopes, plantilla, rows);
        }
        return rows;
    }
}
