// SearchController.java

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private NLPOpenSearchService nlpOpenSearchService;

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String indexName, @RequestParam String query) throws IOException {
        return nlpOpenSearchService.search(indexName, query);
    }
}
