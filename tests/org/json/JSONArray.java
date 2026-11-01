package org.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Minimal JSON array used only by the host regression harnesses. */
public class JSONArray {
    final List<Object> values = new ArrayList<>();

    public JSONArray() {}

    public JSONArray(Collection<?> input) {
        if (input != null) {
            for (Object value : input) values.add(JSONObject.wrap(value));
        }
    }

    JSONArray(List<?> input) {
        if (input != null) {
            for (Object value : input) values.add(JSONObject.wrap(value));
        }
    }

    public JSONArray(String source) throws JSONException {
        Object parsed = JsonHostParser.parse(source);
        if (!(parsed instanceof JSONArray)) {
            throw new JSONException("Input is not a JSON array");
        }
        values.addAll(((JSONArray) parsed).values);
    }

    public JSONArray put(Object value) {
        values.add(JSONObject.wrap(value));
        return this;
    }

    public int length() {
        return values.size();
    }

    public Object get(int index) throws JSONException {
        if (index < 0 || index >= values.size()) {
            throw new JSONException("JSONArray index out of range: " + index);
        }
        return values.get(index);
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object value = get(index);
        if (!(value instanceof JSONObject)) {
            throw new JSONException("JSONArray value is not an object at " + index);
        }
        return (JSONObject) value;
    }
    public JSONObject optJSONObject(int index) {
        if(index<0||index>=values.size())return null;
        Object value=values.get(index);return value instanceof JSONObject?(JSONObject)value:null;
    }

    public JSONArray getJSONArray(int index) throws JSONException {
        Object value = get(index);
        if (!(value instanceof JSONArray)) {
            throw new JSONException("JSONArray value is not an array at " + index);
        }
        return (JSONArray) value;
    }

    public String getString(int index) throws JSONException {
        Object value = get(index);
        if (value == JSONObject.NULL) {
            throw new JSONException("JSONArray value is null at " + index);
        }
        return String.valueOf(value);
    }

    public String optString(int index, String fallback) {
        if (index < 0 || index >= values.size()) return fallback;
        Object value = values.get(index);
        return value == null || value == JSONObject.NULL
                ? fallback : String.valueOf(value);
    }

    @Override public String toString() {
        return JSONObject.render(this, 0, 0);
    }

    public String toString(int indent) {
        return JSONObject.render(this, Math.max(0, indent), 0);
    }
}
