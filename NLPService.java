// NLPOpenSearchService.java

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.OpenSearchClient;
import org.opensearch.client.OpenSearchClients;
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

    private final OpenSearchClient openSearchClient;

    public NLPOpenSearchService() {
        openSearchClient = OpenSearchClients.create("http://localhost:9200");
    }

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
        SearchSourceBuilder searchSourceBuilder = constructESQuery(intent, entity, query, tokens, posTags, nameSpans);

        // Execute the search
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = openSearchClient.search(searchRequest);

        // Extract the results
        List<Map<String, Object>> results = new ArrayList<>();
        SearchHits hits = searchResponse.getHits();
        for (SearchHit hit : hits) {
            results.add(hit.getSourceAsMap());
        }

        return results;
    }

    private SearchSourceBuilder constructESQuery(String intent, String entity, String query, String[] tokens, String[] posTags, Span[] nameSpans) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        if (intent.equals("RETRIEVE")) {
            for (String token : tokens) {
                if (posTags[tokens.indexOf(token)].startsWith("CD")) { // CD: Cardinal number
                    searchSourceBuilder.query(QueryBuilders.rangeQuery(nlpService.extractFieldName(query, tokens, token)).gt(token));
                } else if (posTags[tokens.indexOf(token)].startsWith("NN")) { // NN: Noun
                    searchSourceBuilder.query(QueryBuilders.termQuery(nlpService.extractFieldName(query, tokens, token), token));
                }
            }
        }

        return searchSourceBuilder;
    }
}
