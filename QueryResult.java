public class QueryResult {
    private String fieldName;
    private String operator;
    private String fieldValue;

    public QueryResult(String fieldName, String operator, String fieldValue) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.fieldValue = fieldValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public String getFieldValue() {
        return fieldValue;
    }
}
