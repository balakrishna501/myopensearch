package com.gmestri.elasticapi.service;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class QueryParser {

    private final StanfordCoreNLP pipeline;
    private final Map<String, Map<String, String>> knowledgeGraph;
    private final FieldMappingConfig fieldMappingConfig;
    private final DateRangeParser dateRangeParser;
    private static final List<String> OPERATORS = Arrays.asList("is", "equals", "like", "greater than", "less than", "between", "starts with", "ends with", "contains", "sum", "avg", "min", "max", "range", "group", "count", "not", "fuzzy", "multiple", "must", "prefix");
    private static final List<String> KEYWORDS = Arrays.asList("asc", "desc", "by", "inclusive", "exclusive");
    private static final List<String> CONJUNCTIONS = Arrays.asList("and", "or");

    public QueryParser(FieldMappingConfig fieldMappingConfig, DateRangeParser dateRangeParser) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, depparse, coref");
        this.pipeline = new StanfordCoreNLP(props);
        this.fieldMappingConfig = fieldMappingConfig;
        this.dateRangeParser = dateRangeParser;

        knowledgeGraph = new HashMap<>();
    }

    public List<QueryResult> parseQuery(String query, User user) {
        List<QueryResult> results = new ArrayList<>();
        Annotation document = new Annotation(query);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null || sentences.isEmpty()) {
            return results;
        }

        CoreMap sentence = sentences.get(0);

        SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);

        CoreLabel subject = findSubject(dependencies, sentence);

        if (subject != null && isPersonalPronoun(subject.lemma().toLowerCase())) {
            if (user != null) {
                System.out.println("User-specific query detected. User ID: " + user.getId());
            } else {
                System.out.println("User-specific query detected, but no user context provided.");
            }
        }

        String currentField = null;
        String currentOperator = null;
        StringBuilder currentValue = new StringBuilder();
        List<String> betweenValues = new ArrayList<>();

        for (int i = 0; i < sentence.get(CoreAnnotations.TokensAnnotation.class).size(); i++) {
            CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(i);
            String word = token.lemma().toLowerCase();

            if (fieldMappingConfig.isField(word)) {
                if (currentField != null && currentOperator != null) {
                    addQueryResult(results, currentField, currentOperator, currentValue, betweenValues);
                    currentValue.setLength(0);
                    betweenValues.clear();
                }
                currentField = fieldMappingConfig.getMappedFieldName(word);
                currentOperator = null;
            } else if (OPERATORS.contains(word)) {
                currentOperator = word;
                if (currentOperator.equals("range") && dateRangeParser.isDateRangeExpression(sentence, i, currentValue.toString().trim())) {
                    DateRangeParser.DateRange range = dateRangeParser.parseDateRange(sentence, i, currentValue.toString().trim());
                    if (range != null) {
                        betweenValues.add(range.getStartDate().toString());
                        betweenValues.add(range.getEndDate().toString());
                        i = range.getEndIndex() - 1;
                    }
                } else if (currentOperator.equals("multiple")) {
                    List<String> multipleValues = new ArrayList<>();
                    int j = i + 1;
                    while (j < sentence.get(CoreAnnotations.TokensAnnotation.class).size()) {
                        CoreLabel multipleToken = sentence.get(CoreAnnotations.TokensAnnotation.class).get(j);
                        String multipleWord = multipleToken.originalText();
                        if (CONJUNCTIONS.contains(multipleWord.toLowerCase())) {
                            break;
                        }
                        multipleValues.add(multipleToken.originalText());
                        j++;
                    }
                    i = j - 1;
                    addMultipleResult(results, currentField, multipleValues);
                    currentOperator = null;
                }
            } else if (CONJUNCTIONS.contains(word)) {
                if (currentField != null && currentOperator != null) {
                    addQueryResult(results, currentField, currentOperator, currentValue, betweenValues);
                    currentValue.setLength(0);
                    betweenValues.clear();
                }
                currentField = null;
                currentOperator = null;
                results.add(createConjunctionResult(word));
            } else if (KEYWORDS.contains(word)) {
                currentValue.append(word).append(" ");
            } else {
                if (currentOperator != null && !currentOperator.equals("range") && !currentOperator.equals("multiple")) {
                    if (currentOperator.equals("between")) {
                        if (betweenValues.size() < 2) {
                            betweenValues.add(token.originalText());
                        }
                    } else {
                        currentValue.append(token.originalText()).append(" ");
                    }
                }
            }
        }
        if (currentField != null && currentOperator != null) {
            addQueryResult(results, currentField, currentOperator, currentValue, betweenValues);
        }

        return results;
    }

    private CoreLabel findSubject(SemanticGraph dependencies, CoreMap sentence) {
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
            IndexedWord indexedWord = new IndexedWord(token);
            List<SemanticGraphEdge> parents = dependencies.getIncomingEdgesSorted(indexedWord);
            if (parents != null && parents.stream().anyMatch(dep -> dep.getRelation().toString().startsWith("nsubj"))) {
                return token;
            }
        }
        return null;
    }

    private boolean isPersonalPronoun(String word) {
        return word.equals("i") || word.equals("me") || word.equals("my") || word.equals("mine") ||
                word.equals("you") || word.equals("your") || word.equals("yours") ||
                word.equals("he") || word.equals("him") || word.equals("his") ||
                word.equals("she") || word.equals("her") || word.equals("hers") ||
                word.equals("it") || word.equals("its") ||
                word.equals("we") || word.equals("us") || word.equals("our") || word.equals("ours") ||
                word.equals("they") || word.equals("them") || word.equals("their") || word.equals("theirs");
    }

    private void addQueryResult(List<QueryResult> results, String currentField, String currentOperator, StringBuilder currentValue, List<String> betweenValues) {
        if (currentOperator.equals("between")) {
            results.add(createBetweenResult(currentField, betweenValues));
        } else {
            String value = currentValue.toString().trim();
            if (knowledgeGraph.containsKey(value)) {
                Map<String, String> entityInfo = knowledgeGraph.get(value);
                if (entityInfo.containsKey(currentField)) {
                    value = entityInfo.get(currentField);
                }
            }
            results.add(createResult(currentField, currentOperator, value));
        }
    }

    private void addMultipleResult(List<QueryResult> results, String currentField, List<String> values) {
        results.add(createMultipleResult(currentField, values));
    }

    private QueryResult createBetweenResult(String field, List<String> values) {
        QueryResult result = new QueryResult();
        result.setFieldName(field);
        result.setOperator("between");
        if (values.size() == 2) {
            result.setFieldValue(values.get(0) + " and " + values.get(1));
        } else if (values.size() == 1) {
            result.setFieldValue(values.get(0) + " and null");
        } else {
            result.setFieldValue("Invalid between values");
        }
        return result;
    }

    private QueryResult createResult(String field, String operator, String value) {
        QueryResult result= new QueryResult();
        result.setFieldName(field);
        result.setOperator(operator);
        result.setFieldValue(value);
        return result;
    }

    private QueryResult createMultipleResult(String field, List<String> values) {
        QueryResult result = new QueryResult();
        result.setFieldName(field);
        result.setOperator("multiple");
        result.setMultipleValues(values);
        return result;
    }

    private QueryResult createConjunctionResult(String conjunction) {
        QueryResult result = new QueryResult();
        result.setOperator(conjunction);
        return result;
    }

    public static class User {
        private long id;
        public User(long id) {
            this.id = id;
        }
        public long getId() {
            return id;
        }
    }

    public static class QueryResult {
        private String fieldName;
        private String operator;
        private String fieldValue;
        private List<String> multipleValues;

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getFieldValue() { return fieldValue; }
        public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
        public List<String> getMultipleValues() { return multipleValues; }
        public void setMultipleValues(List<String> multipleValues) { this.multipleValues = multipleValues; }

        @Override
        public String toString() {
            return "QueryResult{" +
                    "fieldName='" + fieldName + '\'' +
                    ", operator='" + operator + '\'' +
                    ", fieldValue='" + fieldValue + '\'' +
                    ", multipleValues=" + multipleValues +
                    '}';
        }
    }
}

@Service
class FieldMappingConfig {
    private Map<String, List<String>> fieldMapping = new HashMap<>();

    public FieldMappingConfig() {
        fieldMapping.put("dataDate", Arrays.asList("data", "date"));
        fieldMapping.put("transactionDate", Arrays.asList("transaction", "transactions"));
        fieldMapping.put("age", Arrays.asList("age", "customer age"));
        fieldMapping.put("name", Arrays.asList("name", "customer name", "client name"));
    }

    public boolean isField(String word) {
        for (List<String> aliases : fieldMapping.values()) {
            if (aliases.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public String getMappedFieldName(String word) {
        for (Map.Entry<String, List<String>> entry : fieldMapping.entrySet()) {
            if (entry.getValue().contains(word)) {
                return entry.getKey();
            }
        }
        return null;
    }
}


// Example Usage (main method)
class Main {
    public static void main(String[] args) {
        FieldMappingConfig fieldMappingConfig = new FieldMappingConfig();
        DateRangeParser dateRangeParser = new DateRangeParser();
        QueryParser parser = new QueryParser(fieldMappingConfig, dateRangeParser);
        QueryParser.User user = new QueryParser.User(123);

        List<QueryParser.QueryResult> results1 = parser.parseQuery("give me my last 3 months data", user);
        System.out.println("Query 1 Results:");
        for (QueryParser.QueryResult result : results1) {
            System.out.println(result);
        }

        List<QueryParser.QueryResult> results2 = parser.parseQuery("Find age between 25 and 35 and name multiple John Jane Doe", user);
        System.out.println("\nQuery 2 Results:");
        for (QueryParser.QueryResult result : results2) {
            System.out.println(result);
        }

        List<QueryParser.QueryResult> results3 = parser.parseQuery("Find name starts with Jo and age is 30", user);
        System.out.println("\nQuery 3 Results:");
        for (QueryParser.QueryResult result : results3) {
            System.out.println(result);
        }
    }
}
