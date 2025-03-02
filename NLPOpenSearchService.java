// NLPOpenSearchService.java

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.core.OpenSearchClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NLPOpenSearchService {

    @Autowired
    private NLPService nlpService;

    @Autowired
    private OpenSearchClient openSearchClient;

    public List<Map<String, Object>> search(String indexName, String query) throws IOException {
        // Tokenize the query
        String[] tokens = nlpService.tokenizeQuery(query);

        // Perform POS tagging
        String[] posTags = nlpService.posTagQuery(tokens);

        // Perform named entity recognition
        Span[] nameSpans = nlpService.nameFindQuery(tokens);

        // Extract intent and entity
        String intent = nlpService.extractIntent(query, tokens);
        String entity = nlpService.extractEntity(query, nameSpans);

        // Construct a search query
        String esQuery = constructESQuery(intent, entity, query, tokens, posTags, nameSpans);

        // Execute the search
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.wrapperQuery(esQuery));
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

        // Extract the results
        List<Map<String, Object>> results = new ArrayList<>();
        SearchHits hits = searchResponse.getHits();
        for (SearchHit hit : hits) {
            results.add(hit.getSourceAsMap());
        }

        return results;
    }

    private String constructESQuery(String intent, String entity, String query, String[] tokens, String[] posTags, Span[] nameSpans) {
        StringBuilder esQuery = new StringBuilder("{\"query\":{\"bool\":{\"must\":[");
        if (intent.equals("RETRIEVE")) {
            for (String token : tokens) {
                if (posTags[tokens.indexOf(token)].startsWith("CD")) { // CD: Cardinal number
                    esQuery.append("{\"range\":{\"").append(nlpService.extractFieldName(query, tokens, token)).append("\":{\"gt\":").append(token).append("}}}");
                } else if (posTags[tokens.indexOf(token)].startsWith("NN")) { // NN: Noun
                    esQuery.append("{\"term\":{\"").append(nlpService.extractFieldName(query, tokens, token)).append("\":\"").append(token).append("\"}}");
                }
            }
        }
        esQuery.append("]}}}");
        return esQuery.toString();
    }
}
