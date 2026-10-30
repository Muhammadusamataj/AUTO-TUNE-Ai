package org.json;
import java.util.HashMap;
public class JSONObject {
    private final HashMap<String,Object> values = new HashMap<>();
    public Object get(String key) { return values.get(key); }
    public JSONObject put(String key, Object v) { values.put(key,v); return this; }
    public JSONArray getJSONArray(String key) { return (JSONArray)values.get(key); }
    public String optString(String key,String fallback) { return values.containsKey(key)?values.get(key).toString():fallback; }
}
