// NLPService.java

import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentCategorizerModel;
import opennlp.tools.namefinder.NameFinderME;
import opennlp.tools.namefinder.NameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceDetectorModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class NLPService {

    private static final String SENTENCE_MODEL_FILE = "en-sent.bin";
    private static final String TOKENIZER_MODEL_FILE = "en-token.bin";
    private static final String POS_MODEL_FILE = "en-pos-maxent.bin";
    private static final String NAME_FINDER_MODEL_FILE = "en-ner-person.bin";
    private static final String DOCUMENT_CATEGORIZER_MODEL_FILE = "en-doccat.bin";

    private SentenceDetectorME sentenceDetector;
    private TokenizerME tokenizer;
    private POSTaggerME posTagger;
    private NameFinderME nameFinder;
    private DocumentCategorizerME documentCategorizer;

    public NLPService() throws IOException {
        loadModels();
    }

    private void loadModels() throws IOException {
        try (InputStream sentenceModelStream = getClass().getClassLoader().getResourceAsStream(SENTENCE_MODEL_FILE)) {
            SentenceDetectorModel sentenceModel = new SentenceDetectorModel(sentenceModelStream);
            sentenceDetector = new SentenceDetectorME(sentenceModel);
        }

        try (InputStream tokenizerModelStream = getClass().getClassLoader().getResourceAsStream(TOKENIZER_MODEL_FILE)) {
            TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelStream);
            tokenizer = new TokenizerME(tokenizerModel);
        }

        try (InputStream posModelStream = getClass().getClassLoader().getResourceAsStream(POS_MODEL_FILE)) {
            POSModel posModel = new POSModel(posModelStream);
            posTagger = new POSTaggerME(posModel);
        }

        try (InputStream nameFinderModelStream = getClass().getClassLoader().getResourceAsStream(NAME_FINDER_MODEL_FILE)) {
            NameFinderModel nameFinderModel = new NameFinderModel(nameFinderModelStream);
            nameFinder = new NameFinderME(nameFinderModel);
        }

        try (InputStream documentCategorizerModelStream = getClass().getClassLoader().getResourceAsStream(DOCUMENT_CATEGORIZER_MODEL_FILE)) {
            DocumentCategorizerModel documentCategorizerModel = new DocumentCategorizerModel(documentCategorizerModelStream);
            documentCategorizer = new DocumentCategorizerME(documentCategorizerModel);
        }
    }

    public String[] tokenizeQuery(String query) {
        return tokenizer.tokenize(query);
    }

    public String[] posTagQuery(String[] tokens) {
        return posTagger.tag(tokens);
    }

    public Span[] nameFindQuery(String[] tokens) {
        return nameFinder.find(tokens);
    }

    public String extractIntent(String query, String[] tokens) {
        // Generic implementation to extract intent
        for (String token : tokens) {
            if (token.equalsIgnoreCase("find") || token.equalsIgnoreCase("get")) {
                return "RETRIEVE";
            } else if (token.equalsIgnoreCase("create") || token.equalsIgnoreCase("add")) {
                return "CREATE";
            } else if (token.equalsIgnoreCase("update") || token.equalsIgnoreCase("modify")) {
                return "UPDATE";
            } else if (token.equalsIgnoreCase("delete") || token.equalsIgnoreCase("remove")) {
                return "DELETE";
            }
        }
        return "UNKNOWN";
    }

    public String extractEntity(String query, Span[] nameSpans) {
        // Generic implementation to extract entity
        for (Span span : nameSpans) {
            if (span.getType().equals("PERSON") || span.getType().equals("ORGANIZATION") || span.getType().equals("LOCATION")) {
                return span.getType();
            }
        }
        return null;
    }

    public String extractFieldName(String query, String[] tokens, String token) {
        // Generic implementation to extract field name
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals(token) && i > 0) {
                return tokens[i - 1].toLowerCase();
            }
        }
        return null;
    }
}
