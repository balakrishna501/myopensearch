import java.util.List;
import java.util.Map;

public class FieldMapper {
    private Map<String, List<String>> fieldMapping;

    public FieldMapper(Map<String, List<String>> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public String getFieldName(String token) {
        for (Map.Entry<String, List<String>> entry : fieldMapping.entrySet()) {
            if (entry.getValue().contains(token)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
