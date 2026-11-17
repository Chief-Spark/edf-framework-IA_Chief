package com.experian.edf.skills.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Verificaciones de la spec: schema (draft 2020-12), inventario (ACC-TPL-01/02/04) y riesgos ACC-R01..R03. */
public final class Verificaciones {

    private static final Pattern NUM_RE = Pattern.compile(
            "(?<![\\w.])\\d+\\.\\d+|(?<![\\w.])\\d+(?![\\w.])", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LITERAL_RE = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern IDENT_RE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern REJECT_RE = Pattern.compile(
            "(anyOf|allOf)\\([A-Za-z0-9_, ]+\\)( hasErrorlevel(Not)?In\\([0-9, ]+\\))?");
    private static final Set<String> DSL_WORDS = Set.of("anyOf", "allOf", "hasErrorlevelIn", "hasErrorlevelNotIn");

    private Verificaciones() {
    }

    /** Equivalente a FilenameUtils.removeExtension: quita desde el último punto del nombre. */
    public static String removeExtension(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return dot > sep ? path.substring(0, dot) : path;
    }

    /** Clave de archivo de E3: una extensión menos; dos si la ruta contiene un marcador errorIn*. */
    public static String engineFileKey(String path) {
        String stripped = removeExtension(path);
        return path.contains("errorIn") ? removeExtension(stripped) : stripped;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o == null ? List.of() : (List<Object>) o;
    }

    private static List<String> findAll(Pattern p, String text, int group) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(m.group(group));
        }
        return out;
    }

    private static String pyList(Iterable<String> items) {
        List<String> reprs = new ArrayList<>();
        items.forEach(s -> reprs.add(Py.repr(s)));
        return "[" + String.join(", ", reprs) + "]";
    }

    /** Verificaciones del inventario (ACC-TPL-01/02/04) y de los riesgos ACC-R01..R03. */
    public static List<String> verifySpec(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        String fuente = (String) spec.get("Fuente");
        Map<String, Object> archivo = map(spec.get("archivo"));
        String pattern = (String) archivo.get("filePattern");
        if (!pattern.startsWith(".*")) {
            errors.add("filePattern debe empezar con .* (RF-ACC-01)");
        }
        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException exc) {
            errors.add("filePattern no compila: " + exc.getDescription());
            return errors;
        }
        for (Object c : list(archivo.get("claves_de_prueba"))) {
            Map<String, Object> caso = map(c);
            // Las claves "ruta_*" son rutas del directorio de validados: se les aplica el mismo recorte
            // de extensión que hace el motor en E3 antes de comparar (String.matches = coincidencia total).
            String valor = (String) caso.get("valor");
            String value = ((String) caso.get("clave")).startsWith("ruta_") ? engineFileKey(valor) : valor;
            boolean esperado = Boolean.TRUE.equals(caso.get("debe_coincidir"));
            if (regex.matcher(value).matches() != esperado) {
                errors.add("filePattern vs " + caso.get("clave") + ": se esperaba "
                        + Py.str(caso.get("debe_coincidir")) + " (ACC-R03)");
            }
        }

        Map<String, Object> calidad = map(spec.get("calidad"));
        Map<String, Object> fileActions = map(spec.get("fileActions"));
        List<Object> condiciones = list(spec.get("condiciones"));
        Object umbral = calidad.get("umbral");
        String fueraDeRango = umbralFueraDeRango(umbral);
        if (fueraDeRango != null) {
            errors.add(fueraDeRango);
        }
        List<String> exprs = new ArrayList<>();
        exprs.add((String) fileActions.get("accept"));
        condiciones.forEach(c -> exprs.add((String) map(c).get("expresion")));
        for (String expr : exprs) {
            List<String> nums = findAll(NUM_RE, expr, 0);
            if (!(nums.size() == 1 && nums.get(0).equals(umbral))) {
                errors.add("escala del umbral distinta de " + Py.str(umbral) + " en: " + expr);
            }
        }
        String accept = (String) fileActions.get("accept");
        if (!accept.contains("average(\"qs." + Py.str(calidad.get("quality_score")) + "\")")) {
            errors.add("accept no agrega el quality score declarado");
        }
        if (accept.contains("isEmptyBodyFile")
                && !Py.truthy(map(spec.get("rechazo_automatico")).get("aceptar_cuerpo_vacio"))) {
            errors.add("accept contiene isEmptyBodyFile pero aceptar_cuerpo_vacio es false");
        }

        List<Object> names = new ArrayList<>();
        List<Object> codes = new ArrayList<>();
        condiciones.forEach(c -> {
            names.add(map(c).get("nombre"));
            codes.add(map(c).get("reasonCode"));
        });
        if (new LinkedHashSet<>(names).size() != names.size() || new LinkedHashSet<>(codes).size() != codes.size()) {
            errors.add("nombres o reasonCode de condiciones repetidos");
        }
        for (Object name : names) {
            if (!((String) name).contains(fuente)) {
                errors.add("la condición " + name + " no lleva el nombre de la fuente (unicidad global)");
            }
        }
        String reject = (String) fileActions.get("reject");
        Set<String> cited = new TreeSet<>(findAll(IDENT_RE, reject, 0));
        cited.removeAll(DSL_WORDS);
        Set<String> undefined = new TreeSet<>(cited);
        undefined.removeAll(names);
        if (cited.isEmpty() || !undefined.isEmpty()) {
            errors.add("reject cita condiciones no definidas: " + pyList(undefined));
        }
        if (!REJECT_RE.matcher(reject).matches()) {
            errors.add("reject no está en la sintaxis nueva: caería en silencio a la antigua (ACC-P09)");
        }
        if (reject.contains("hasErrorlevel") && !Py.truthy(map(spec.get("severidades")).get("habilitado"))) {
            errors.add("reject usa hasErrorlevel* sin tabla de severidades (ACC-TPL-03)");
        }

        Map<String, Object> registro = map(spec.get("registro"));
        String record = (String) registro.get("recordAcceptance");
        Map<String, Object> diag = map(spec.get("diagnostico_validation"));
        Set<Object> knownCodes = new TreeSet<>();
        knownCodes.addAll(list(diag.get("codigos_sev1")));
        knownCodes.addAll(list(diag.get("codigos_sev3")));
        knownCodes.addAll(list(diag.get("codigos_motor")));
        List<Object> niveles = list(diag.get("niveles"));
        for (Object level : list(registro.get("niveles_que_rechazan"))) {
            if (!niveles.contains(level) || ((String) level).length() != 1) {
                errors.add("nivel " + Py.repr(level) + " no emitido por Validation o no es de un carácter (ACC-R01)");
            }
        }
        for (Object codeObj : list(registro.get("codigos_que_rechazan"))) {
            String code = (String) codeObj;
            if (!knownCodes.contains(code)) {
                errors.add("código " + code + " no emitido por Validation (IN-05)");
            }
            List<String> others = new ArrayList<>();
            for (Object k : knownCodes) {
                if (!k.equals(code) && ((String) k).contains(code)) {
                    others.add((String) k);
                }
            }
            if (!others.isEmpty()) {
                errors.add("código " + code + " es subcadena de " + pyList(others) + " (ACC-R01)");
            }
        }
        for (String literal : findAll(LITERAL_RE, record, 1)) {
            if (!knownCodes.contains(literal) && !niveles.contains(literal)) {
                errors.add("literal " + Py.repr(literal) + " de recordAcceptance no existe en IN-05");
            }
        }
        for (Object name : list(diag.get("quality_scores"))) {
            // El motor hace replaceAll(nombreQS, score) sobre TODO el texto: basta una subcadena.
            if (record.contains((String) name)) {
                errors.add("el quality score " + name + " aparece dentro de recordAcceptance (ACC-R02)");
            }
        }

        Map<String, Object> co = map(spec.get("contextual_override"));
        if (Py.truthy(co.get("decision_humana")) && !Py.truthy(co.get("decision_ref"))) {
            errors.add("contextualOverride habilitado sin referencia a la decisión humana (ACC-TPL-04)");
        }
        Map<String, Object> manual = map(spec.get("intervencion_manual"));
        if (Py.truthy(manual.get("habilitada"))) {
            for (String key : List.of("manualReject", "manualAccept")) {
                Object expr = manual.get(key);
                if (!(expr instanceof String s) || !s.contains("externalAction(")) {
                    errors.add(key + " solo admite expresiones con externalAction(...) (IN-07)");
                }
            }
        }
        errors.addAll(qsNoBinarios(spec));
        return errors;
    }

    /**
     * Regla "umbral en [0, 1]" (AC-04, P-3): {@code calidad.umbral} debe ser un número entre 0 y 1.
     * Los literales de {@code accept} y de las condiciones no necesitan su propia comprobación de rango:
     * la regla de escala ya exige que cada uno sea igual a {@code calidad.umbral}.
     * Devuelve el mensaje de error, o {@code null} si el umbral está en rango.
     */
    static String umbralFueraDeRango(Object umbral) {
        BigDecimal valor;
        try {
            valor = new BigDecimal(String.valueOf(umbral).trim());
        } catch (NumberFormatException exc) {
            return "umbral en [0, 1]: calidad.umbral " + Py.repr(umbral) + " no es un número (AC-04)";
        }
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0) {
            return "umbral en [0, 1]: calidad.umbral " + Py.repr(umbral) + " está fuera de [0, 1] (AC-04)";
        }
        return null;
    }

    /**
     * Regla "QS por registro binario" (AC-ACC-05, plan #2): en el fixture, cada quality score declarado
     * ({@code diagnostico_validation.quality_scores} y {@code calidad.quality_score}) vale 0 o 1 en cada
     * evento ({@code fixture.archivos[].eventos[].qualityScores}). El promedio del archivo es el que
     * puede ser fraccionario. Tolera una spec con otra forma (eso lo informa el schema).
     */
    static List<String> qsNoBinarios(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Set<Object> declarados = new HashSet<>();
        if (spec.get("diagnostico_validation") instanceof Map<?, ?> diag && diag.get("quality_scores") instanceof List<?> qs) {
            qs.stream().filter(String.class::isInstance).forEach(declarados::add);
        }
        if (spec.get("calidad") instanceof Map<?, ?> cal && cal.get("quality_score") instanceof String q) {
            declarados.add(q);
        }
        if (!(spec.get("fixture") instanceof Map<?, ?> fixture) || !(fixture.get("archivos") instanceof List<?> archivos)) {
            return errors;
        }
        for (int i = 0; i < archivos.size(); i++) {
            if (!(archivos.get(i) instanceof Map<?, ?> archivo) || !(archivo.get("eventos") instanceof List<?> eventos)) {
                continue;
            }
            for (int j = 0; j < eventos.size(); j++) {
                if (!(eventos.get(j) instanceof Map<?, ?> evento) || !(evento.get("qualityScores") instanceof List<?> scores)) {
                    continue;
                }
                for (Object s : scores) {
                    if (!(s instanceof Map<?, ?> score) || !declarados.contains(score.get("name"))) {
                        continue;
                    }
                    Object v = score.get("score");
                    boolean binario = v instanceof Number n && (n.doubleValue() == 0.0 || n.doubleValue() == 1.0);
                    if (!binario) {
                        errors.add("QS por registro binario: " + score.get("name") + " = " + Py.str(v)
                                + " en fixture/archivos/" + i + "/eventos/" + j + " (" + evento.get("eventId")
                                + "); debe ser 0 o 1 (AC-ACC-05)");
                    }
                }
            }
        }
        return errors;
    }

    /** Errores de la spec contra el JSON Schema (draft 2020-12), con la ruta del valor. */
    public static List<String> validateSchema(Map<String, Object> spec, Map<String, Object> schema) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.valueToTree(schema);
        JsonNode specNode = mapper.valueToTree(spec);
        JsonSchema validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        List<String> out = new ArrayList<>();
        for (ValidationMessage msg : validator.validate(specNode)) {
            // "$.fixture.archivos[7].marcador" -> "fixture/archivos/7/marcador"
            String path = msg.getInstanceLocation().toString().replaceFirst("^\\$\\.?", "")
                    .replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
            out.add((path.isEmpty() ? "(raíz)" : path) + ": " + msg.getError());
        }
        return out;
    }
}
