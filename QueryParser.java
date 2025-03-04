import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.namefinder.NameFinderME;
import opennlp.tools.namefinder.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueryParser {
    private TokenizerModel tokenizerModel;
    private TokenNameFinderModel nameFinderModel;
    private POSModel posModel;

    public QueryParser() throws IOException {
        tokenizerModel = new TokenizerModel(new FileInputStream("src/main/resources/en-token.bin"));
        nameFinderModel = new TokenNameFinderModel(new FileInputStream("src/main/resources/en-ner-person.bin"));
        posModel = new POSModel(new FileInputStream("src/main/resources/en-pos-maxent.bin"));
    }

    public List<QueryResult> parseQuery(String query) throws IOException {
        Tokenizer tokenizer = new TokenizerME(tokenizerModel);
        String[] tokens = tokenizer.tokenize(query);

        NameFinderME nameFinder = new NameFinderME(nameFinderModel);
        String[] names = nameFinder.find(tokens);

        POSTaggerME posTagger = new POSTaggerME(posModel);
        String[] posTags = posTagger.tag(tokens);

        List<QueryResult> results = new ArrayList<>();

        String columnName = null;
        String operator = null;
        String columnValue = null;

        for (int i = 0; i < tokens.length; i++) {
            if (posTags[i].startsWith("NN")) {
                // Noun, potential column name
                columnName = getActualFieldName(tokens[i]);
            } else if (isOperator(tokens[i])) {
                // Operator
                operator = tokens[i];
            } else if (operator != null && (posTags[i].startsWith("CD") || posTags[i].startsWith("NN"))) {
                // Value
                columnValue = tokens[i];

                // Create a QueryResult object
                QueryResult result = new QueryResult(columnName, operator, columnValue);
                results.add(result);

                // Reset for next condition
                columnName = null;
                operator = null;
                columnValue = null;
            }
        }

        // If no conditions are found, use matchAll query
        if (results.isEmpty()) {
            QueryResult result = new QueryResult("*", "*", "*");
            results.add(result);
        }

        return results;
    }

    private String getActualFieldName(String columnName) {
        Map<String, List<String>> fieldMapping = FieldMappingConfig.getFieldMapping();
        return fieldMapping.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(s -> s.equalsIgnoreCase(columnName) 
                                || s.toLowerCase().contains(columnName.toLowerCase())
                                || columnName.toLowerCase().contains(s.toLowerCase())))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private boolean isOperator(String token) {
        String[] operators = {"is", "=", "contains", "starts", "ends", ">", "<", ">=", "<=", "between", "and", "or", "fuzzy"};
        for (String operator : operators) {
            if (Pattern.matches(operator, token, Pattern.CASE_INSENSITIVE)) {
                return true;
            }
        }
        return false;
    }
}
