package org.json;
import java.util.ArrayList;
public class JSONArray {
    private final ArrayList<Object> values = new ArrayList<>();
    public Object get(int index) { return values.get(index); }
    public JSONArray put(double value) { values.add(value); return this; }
    public int length() { return values.size(); }
    public double getDouble(int index) { return ((Number) values.get(index)).doubleValue(); }
}
