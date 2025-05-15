package lars.ba.own.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DataFrames {
    Map<String, List<Map<String, Object>>> data;

    public DataFrames() {
        this.data = new HashMap<>();
        initializeDataFrames();
    }

    private void initializeDataFrames() {
        data.put("auftraege", new ArrayList<>());
        data.put("dokumente", new ArrayList<>());
        data.put("patienten", new ArrayList<>());
        data.put("autoren", new ArrayList<>());
        data.put("inhalte", new ArrayList<>());
        data.put("werte", new ArrayList<>());
    }

    public void addRow(String tableName, Map<String, Object> row) {
        data.get(tableName).add(row);
    }

    public List<Map<String, Object>> getTable(String tableName) {
        return data.get(tableName);
    }
}