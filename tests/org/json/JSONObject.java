package org.json;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small deterministic host-test JSON implementation. Android builds continue
 * to use the platform org.json classes; this file is compiled only in tests.
 */
public class JSONObject {
    public static final Object NULL = new Object() {
        @Override public boolean equals(Object other) {
            return other == null || other == this;
        }

        @Override public String toString() {
            return "null";
        }
    };

    final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String source) throws JSONException {
        Object parsed = JsonHostParser.parse(source);
        if (!(parsed instanceof JSONObject)) {
            throw new JSONException("Input is not a JSON object");
        }
        values.putAll(((JSONObject) parsed).values);
    }

    public JSONObject(Map<?, ?> input) {
        if (input != null) {
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                values.put(String.valueOf(entry.getKey()),
                        wrap(entry.getValue()));
            }
        }
    }

    public JSONObject put(String name, Object value) throws JSONException {
        if (name == null) throw new JSONException("Null JSON object key");
        if (value instanceof Double && !Double.isFinite((Double) value)
                || value instanceof Float && !Float.isFinite((Float) value)) {
            throw new JSONException("Non-finite JSON number for " + name);
        }
        values.put(name, wrap(value));
        return this;
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public boolean isNull(String name) {
        Object value = values.get(name);
        return value == null || value == NULL;
    }

    public int length() {
        return values.size();
    }

    public Object remove(String name) {
        return values.remove(name);
    }

    public Object get(String name) throws JSONException {
        if (!values.containsKey(name)) {
            throw new JSONException("JSONObject key not found: " + name);
        }
        return values.get(name);
    }

    public JSONObject getJSONObject(String name) throws JSONException {
        Object value = get(name);
        if (!(value instanceof JSONObject)) {
            throw new JSONException(name + " is not a JSONObject");
        }
        return (JSONObject) value;
    }

    public JSONArray getJSONArray(String name) throws JSONException {
        Object value = get(name);
        if (!(value instanceof JSONArray)) {
            throw new JSONException(name + " is not a JSONArray");
        }
        return (JSONArray) value;
    }

    public String getString(String name) throws JSONException {
        Object value = get(name);
        if (value == NULL) throw new JSONException(name + " is null");
        return String.valueOf(value);
    }

    public boolean getBoolean(String name) throws JSONException {
        Object value = get(name);
        if (value instanceof Boolean) return (Boolean) value;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new JSONException(name + " is not boolean");
    }

    public int getInt(String name) throws JSONException {
        return (int) getLong(name);
    }

    public long getLong(String name) throws JSONException {
        Object value = get(name);
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new JSONException(name + " is not integer", error);
        }
    }

    public double getDouble(String name) throws JSONException {
        Object value = get(name);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new JSONException(name + " is not numeric", error);
        }
    }

    public String optString(String name, String fallback) {
        Object value = values.get(name);
        return value == null || value == NULL
                ? fallback : String.valueOf(value);
    }

    public String optString(String name) {
        return optString(name, "");
    }

    public int optInt(String name, int fallback) {
        Object value = values.get(name);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null || value == NULL ? fallback
                    : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public double optDouble(String name, double fallback) {
        Object value = values.get(name);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null || value == NULL ? fallback
                    : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public double optDouble(String name) { return optDouble(name, Double.NaN); }
    public long optLong(String name, long fallback) {
        Object value=values.get(name);
        if(value instanceof Number)return ((Number)value).longValue();
        try{return Long.parseLong(String.valueOf(value));}catch(Exception ignored){return fallback;}
    }

    public boolean optBoolean(String name, boolean fallback) {
        Object value = values.get(name);
        if (value instanceof Boolean) return (Boolean) value;
        if (value == null || value == NULL) return fallback;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        return fallback;
    }

    public JSONObject optJSONObject(String name) {
        Object value = values.get(name);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public JSONArray optJSONArray(String name) {
        Object value = values.get(name);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    public JSONArray names() {
        return values.isEmpty() ? null
                : new JSONArray(values.keySet());
    }

    @Override public String toString() {
        return render(this, 0, 0);
    }

    public String toString(int indent) {
        return render(this, Math.max(0, indent), 0);
    }

    static Object wrap(Object value) {
        if (value == null || value == NULL) return NULL;
        if (value instanceof JSONObject || value instanceof JSONArray
                || value instanceof String || value instanceof Number
                || value instanceof Boolean) return value;
        if (value instanceof Map) return new JSONObject((Map<?, ?>) value);
        if (value instanceof Collection) {
            return new JSONArray((Collection<?>) value);
        }
        if (value.getClass().isArray()) {
            JSONArray array = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.put(Array.get(value, i));
            }
            return array;
        }
        return String.valueOf(value);
    }

    static String render(Object value, int indent, int depth) {
        if (value == null || value == NULL) return "null";
        if (value instanceof String) return quote((String) value);
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry
                    : object.values.entrySet()) {
                if (!first) out.append(',');
                newline(out, indent, depth + 1);
                out.append(quote(entry.getKey()));
                out.append(indent > 0 ? ": " : ":");
                out.append(render(entry.getValue(), indent, depth + 1));
                first = false;
            }
            if (!object.values.isEmpty()) newline(out, indent, depth);
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.values.size(); i++) {
                if (i > 0) out.append(',');
                newline(out, indent, depth + 1);
                out.append(render(array.values.get(i), indent, depth + 1));
            }
            if (!array.values.isEmpty()) newline(out, indent, depth);
            return out.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static void newline(StringBuilder out, int indent, int depth) {
        if (indent <= 0) return;
        out.append('\n');
        for (int i = 0; i < indent * depth; i++) out.append(' ');
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.US, "\\u%04x",
                                (int) c));
                    } else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

final class JsonHostParser {
    private final String source;
    private int position;

    private JsonHostParser(String source) {
        this.source = source == null ? "" : source;
    }

    static Object parse(String source) throws JSONException {
        JsonHostParser parser = new JsonHostParser(source);
        Object value = parser.value();
        parser.space();
        if (parser.position != parser.source.length()) {
            throw parser.error("Trailing JSON content");
        }
        return value;
    }

    private Object value() throws JSONException {
        space();
        if (position >= source.length()) throw error("Missing JSON value");
        char c = source.charAt(position);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (source.startsWith("true", position)) {
            position += 4;
            return true;
        }
        if (source.startsWith("false", position)) {
            position += 5;
            return false;
        }
        if (source.startsWith("null", position)) {
            position += 4;
            return JSONObject.NULL;
        }
        return number();
    }

    private JSONObject object() throws JSONException {
        position++;
        JSONObject out = new JSONObject();
        space();
        if (take('}')) return out;
        while (true) {
            space();
            if (position >= source.length()
                    || source.charAt(position) != '"') {
                throw error("Expected object key");
            }
            String name = string();
            space();
            require(':');
            out.put(name, value());
            space();
            if (take('}')) return out;
            require(',');
        }
    }

    private JSONArray array() throws JSONException {
        position++;
        JSONArray out = new JSONArray();
        space();
        if (take(']')) return out;
        while (true) {
            out.put(value());
            space();
            if (take(']')) return out;
            require(',');
        }
    }

    private String string() throws JSONException {
        require('"');
        StringBuilder out = new StringBuilder();
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= source.length()) throw error("Bad escape");
            char escaped = source.charAt(position++);
            switch (escaped) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (position + 4 > source.length()) {
                        throw error("Short unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(
                                source.substring(position, position + 4), 16));
                    } catch (NumberFormatException error) {
                        throw error("Bad unicode escape");
                    }
                    position += 4;
                    break;
                default: throw error("Unknown escape");
            }
        }
        throw error("Unterminated string");
    }

    private Number number() throws JSONException {
        int start = position;
        if (take('-')) {}
        while (position < source.length()
                && Character.isDigit(source.charAt(position))) position++;
        if (take('.')) {
            while (position < source.length()
                    && Character.isDigit(source.charAt(position))) position++;
        }
        if (position < source.length()
                && (source.charAt(position) == 'e'
                || source.charAt(position) == 'E')) {
            position++;
            if (position < source.length()
                    && (source.charAt(position) == '+'
                    || source.charAt(position) == '-')) position++;
            while (position < source.length()
                    && Character.isDigit(source.charAt(position))) position++;
        }
        String token = source.substring(start, position);
        try {
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0
                    || token.indexOf('E') >= 0) {
                return Double.parseDouble(token);
            }
            long value = Long.parseLong(token);
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? (int) value : value;
        } catch (NumberFormatException error) {
            throw error("Bad JSON number");
        }
    }

    private void space() {
        while (position < source.length()
                && Character.isWhitespace(source.charAt(position))) position++;
    }

    private boolean take(char expected) {
        if (position < source.length()
                && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) throws JSONException {
        if (!take(expected)) throw error("Expected '" + expected + "'");
    }

    private JSONException error(String message) {
        return new JSONException(message + " at character " + position);
    }
}
