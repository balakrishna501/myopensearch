import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldMappingConfig {
    public static Map<String, List<String>> getFieldMapping() {
        Map<String, List<String>> fieldMapping = new HashMap<>();

        fieldMapping.put("name", List.of("name", " Names", " Named"));
        fieldMapping.put("address.city", List.of("city", " City", " located"));
        fieldMapping.put("elasticContact.mobileNo", List.of("mobile", "phone", "number"));

        return fieldMapping;
    }
}
