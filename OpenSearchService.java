import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.OpenSearchClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.FuzzyQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenSearchService {
    private final OpenSearchClient openSearchClient;
    private final QueryParser queryParser;

    @Autowired
    public OpenSearchService(OpenSearchClient openSearchClient, QueryParser queryParser) {
        this.openSearchClient = openSearchClient;
        this.queryParser = queryParser;
    }

    public SearchResponse search(String indexName, String query) throws IOException {
        List<QueryResult> queryResults = queryParser.parseQuery(query);
        SearchRequest searchRequest = buildSearchRequest(indexName, queryResults);
        return openSearchClient.search(searchRequest);
    }

    private SearchRequest buildSearchRequest(String indexName, List<QueryResult> queryResults) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .index(indexName);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        for (QueryResult queryResult : queryResults) {
            QueryBuilder queryBuilder = buildQueryBuilder(queryResult);
            boolQueryBuilder.must(queryBuilder);
        }

        requestBuilder.query(boolQueryBuilder);

        return requestBuilder.build();
    }

    private QueryBuilder buildQueryBuilder(QueryResult queryResult) {
        switch (queryResult.getOperator()) {
            case "*":
                return QueryBuilders.multiMatchQuery(queryResult.getFieldValue(), "*");
            case "is":
            case "=":
                return QueryBuilders.matchQuery(queryResult.getFieldName(), queryResult.getFieldValue());
            case "contains":
                return QueryBuilders.matchQuery(queryResult.getFieldName(), "*" + queryResult.getFieldValue() + "*");
            case "starts":
                return QueryBuilders.prefixQuery(queryResult.getFieldName(), queryResult.getFieldValue());
            case "ends":
                return QueryBuilders.suffixQuery(queryResult.getFieldName(), queryResult.getFieldValue());
            case ">":
                return QueryBuilders.rangeQuery(queryResult.getFieldName()).gt(queryResult.getFieldValue());
            case "<":
                return QueryBuilders.rangeQuery(queryResult.getFieldName()).lt(queryResult.getFieldValue());
            case ">=":
                return QueryBuilders.rangeQuery(queryResult.getFieldName()).gte(queryResult.getFieldValue());
            case "<=":
                return QueryBuilders.rangeQuery(queryResult.getFieldName()).lte(queryResult.getFieldValue());
            case "between":
                String[] values;
                if (queryResult.getFieldValue().contains(" and ")) {
                    values = queryResult.getFieldValue().split(" and ");
                } else {
                    values = queryResult.getFieldValue().split("\\s+");
                }
                return QueryBuilders.rangeQuery(queryResult.getFieldName()).gte(values[0]).lte(values[values.length - 1]);
            case "and":
                // Handle AND operator
                return handleAndOperator(queryResult);
            case "or":
                // Handle OR operator
                return handleOrOperator(queryResult);
            case "fuzzy":
                // Handle fuzzy search
                return handleFuzzySearch(queryResult);
            default:
                throw new UnsupportedOperationException("Unsupported operator: " + queryResult.getOperator());
        }
    }

    private QueryBuilder handleFuzzySearch(QueryResult queryResult) {
        FuzzyQueryBuilder fuzzyQueryBuilder = QueryBuilders.fuzzyQuery(queryResult.getFieldName(), queryResult.getFieldValue());
        fuzzyQueryBuilder.fuzziness(Fuzziness.AUTO);
        return fuzzyQueryBuilder;
    }

    private QueryBuilder handleAndOperator(QueryResult queryResult) {
        String[] values = queryResult.getFieldValue().split(",");
        QueryBuilder leftQueryBuilder = buildQueryBuilder(new QueryResult(queryResult.getFieldName(), "contains", values[0]));
        QueryBuilder rightQueryBuilder = buildQueryBuilder(new QueryResult(queryResult.getFieldName(), "contains", values[1]));
        return QueryBuilders.boolQuery().must(leftQueryBuilder).must(rightQueryBuilder);
    }

    private QueryBuilder handleOrOperator(QueryResult queryResult) {
        String[] values = queryResult.getFieldValue().split(",");
        QueryBuilder leftQueryBuilder = buildQueryBuilder(new QueryResult(queryResult.getFieldName(), "contains", values[0]));
        QueryBuilder rightQueryBuilder = buildQueryBuilder(new QueryResult(queryResult.getFieldName(), "contains", values[1]));
        return QueryBuilders.boolQuery().should(leftQueryBuilder).should(rightQueryBuilder);
    }
}
