package edu.ufl.bmi.ontology;

import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Properties;
import java.io.FileReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChebiClassSearcher {

    static String REST_URL;
    static String ONTOLOGY_ID;
    static String API_KEY;
    static String inputFileName;
    static String apiKeyFileName;
    static final String REQUIRE_EXACT_MATCH = "true";
    
    static final ObjectMapper mapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: this main function requires one argument that is the path and filename to a configuration properties file.");
        }
        loadProperties(args[0]);

        ArrayList<SearchParams> terms = new ArrayList<SearchParams>();

        String currentDir = System.getProperty("user.dir");
        Scanner in = new Scanner(new FileReader(inputFileName));

        while (in.hasNextLine()) {
            String line = in.nextLine();
            String[] flds = line.split("\t");
            if (flds.length >= 3) {
                SearchParams p = new SearchParams();
                p.label = flds[0];
                p.dronIriTxt = flds[1];
                p.rxCuiTxt = flds[2];
                terms.add(p);
            } else {
                System.err.println("Too few fields: " + line + "\t" + flds.length);
            }
        }

        HashMap<SearchParams, JsonNode> searchResults = conductSearch(terms);
        Map<SearchParams, ArrayList<SearchMatch>> paramsToMatches = processSearchResults(searchResults);
        processSearchMatches(paramsToMatches);
    }


    protected static void loadProperties(String fileName) {
        File f = new File(fileName);
        if (!f.exists()) {
            System.err.println("configuration property file " + fileName + " does not exist.");
            System.exit(1);
        }
        Properties p = new Properties();
        try {
            p.load(new FileReader(f));
            REST_URL = p.getProperty("apiRestUrl");
            ONTOLOGY_ID = p.getProperty("ontologyId");
            inputFileName = p.getProperty("dronUnmappedIngredientFile");
            apiKeyFileName = p.getProperty("apiKeyFile");

            Scanner in = new Scanner(new FileReader(apiKeyFileName));
            if (!in.hasNextLine()) {
                System.err.println("api key file must have api key on first line.");
                System.exit(2);
            }
            API_KEY = in.nextLine();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(3);
        }
    }

    protected static HashMap<SearchParams, JsonNode> conductSearch(List<SearchParams> terms) {
        HashMap<SearchParams, JsonNode> searchResults = new HashMap<SearchParams, JsonNode>();
        for (SearchParams term : terms) {
            String encodedTerm = encodeValueForUrl(term.label);
            JsonNode searchResult = jsonToNode(get(REST_URL + "/search?q=" + encodedTerm +"&ontologies=" + ONTOLOGY_ID)
                + "&require_exact_match=" + REQUIRE_EXACT_MATCH + "&include=prefLabel,synonym").get("collection");
              searchResults.put(term, searchResult);
        }
        return searchResults;
    }

    protected static Map<SearchParams, ArrayList<SearchMatch>>
                    processSearchResults(HashMap<SearchParams, JsonNode> searchResults) {
        HashMap<SearchParams, ArrayList<SearchMatch>> paramsToMatches = 
                    new HashMap<SearchParams, ArrayList<SearchMatch>>();
        
        Set<SearchParams> keys = searchResults.keySet();
        for (SearchParams key : keys) {
            JsonNode result = searchResults.get(key);
            if (result.isArray()) {
                Iterator<JsonNode> i = result.elements();
                while (i.hasNext()) {
                    JsonNode node = i.next();
                    if (node.isObject()) {
                        JsonNode prefLabelNode = node.get("prefLabel");
                        if (prefLabelNode.isTextual()) {
                            String prefLabel = prefLabelNode.asText();
                            if (key.label.equals(prefLabel)) {
                                SearchMatch sm = new SearchMatch();
                                sm.type = MatchType.EXACT;
                                sm.field = MatchedField.PREF_LABEL;
                                sm.searchString = key.label;
                                sm.matchedString = prefLabel;
                                System.out.println("EXACT MATCH PREFLABEL: " + key.label + "\t" + prefLabel);
                                sm.classIriTxt = getChebiIriTxtFromNode(node);
                                if (paramsToMatches.containsKey(key)) {
                                    ArrayList<SearchMatch> matches = paramsToMatches.get(key);
                                    matches.add(sm);
                                } else {
                                     ArrayList<SearchMatch> matches = new ArrayList<SearchMatch>();
                                     matches.add(sm);
                                     paramsToMatches.put(key, matches);
                                }
                            } else if (key.label.toLowerCase().equals(prefLabel.toLowerCase())) {
                                SearchMatch sm = new SearchMatch();
                                sm.type = MatchType.EXACT;
                                sm.field = MatchedField.PREF_LABEL;
                                sm.searchString = key.label;
                                sm.matchedString = prefLabel;
                                System.out.println("CASE INSENSITIVE MATCH PREFLABEL: "+ key.label + "\t" + prefLabel);
                                sm.classIriTxt = getChebiIriTxtFromNode(node);
                                if (paramsToMatches.containsKey(key)) {
                                    ArrayList<SearchMatch> matches = paramsToMatches.get(key);
                                    matches.add(sm);
                                } else {
                                     ArrayList<SearchMatch> matches = new ArrayList<SearchMatch>();
                                     matches.add(sm);
                                     paramsToMatches.put(key, matches);
                                }
                            }
                        }
                        JsonNode synonymNode = node.get("synonym");
                        if (synonymNode != null) {
                            Iterator<JsonNode> synonyms = synonymNode.elements();
                            while (synonyms.hasNext()) {
                                JsonNode synNode = synonyms.next();
                                String synonym = synNode.asText();
                                if (synonym.equals(key.label)) {
                                    SearchMatch sm = new SearchMatch();
                                    sm.type = MatchType.EXACT;
                                    sm.field = MatchedField.SYNONYM;
                                    sm.searchString = key.label;
                                    sm.matchedString = synonym;
                                    System.out.println("EXACT MATCH SYNONYM: " + key.label + "\t" + synonym);
                                    sm.classIriTxt = getChebiIriTxtFromNode(node);
                                    if (paramsToMatches.containsKey(key)) {
                                        ArrayList<SearchMatch> matches = paramsToMatches.get(key);
                                        matches.add(sm);
                                    } else {
                                        ArrayList<SearchMatch> matches = new ArrayList<SearchMatch>();
                                        matches.add(sm);
                                        paramsToMatches.put(key, matches);
                                    }
                                } else if (key.label.toLowerCase().equals(synonym.toLowerCase())) {
                                    SearchMatch sm = new SearchMatch();
                                    sm.type = MatchType.CASE_INSENSITIVE;
                                    sm.field = MatchedField.SYNONYM;
                                    sm.searchString = key.label;
                                    sm.matchedString = synonym;
                                    System.out.println("CASE INSENSITIVE MATCH SYNONYM: "+ key.label + "\t" + synonym);
                                    sm.classIriTxt = getChebiIriTxtFromNode(node);
                                    if (paramsToMatches.containsKey(key)) {
                                        ArrayList<SearchMatch> matches = paramsToMatches.get(key);
                                        matches.add(sm);
                                    } else {
                                        ArrayList<SearchMatch> matches = new ArrayList<SearchMatch>();
                                        matches.add(sm);
                                        paramsToMatches.put(key, matches);
                                    }
                            }
                            }
                        } else {
                            System.out.println("RESULT HAS NO SYNONYMS");
                        }
                    }
                }
            }
        }
        return paramsToMatches;
    }

    protected static String getChebiIriTxtFromNode(JsonNode node) {
        JsonNode value = node.get("@id");
        String iriTxt = null;
        if (value != null) {
            iriTxt = value.asText();
        }
        System.out.println("CHEBI IRI text: " + iriTxt);
        return iriTxt;
    }

    private static JsonNode jsonToNode(String json) {
        JsonNode root = null;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return root;
    }

    private static String get(String urlToGet) {
        URL url;
        HttpURLConnection conn;
        BufferedReader rd;
        String line;
        String result = "";
        try {
            url = new URL(urlToGet);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "apikey token=" + API_KEY);
            conn.setRequestProperty("Accept", "application/json");
            rd = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            while ((line = rd.readLine()) != null) {
                result += line;
            }
            rd.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

     private static String encodeValueForUrl(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    protected static void processSearchMatches(Map<SearchParams, ArrayList<SearchMatch>> paramsToMatches) {
        Set<SearchParams> keySet = paramsToMatches.keySet();
        for (SearchParams p : keySet) {
            ArrayList<SearchMatch> matches = paramsToMatches.get(p);
            for (SearchMatch m : matches) {
                System.out.println(p.dronIriTxt + "\t" + m.classIriTxt + "\t" + p.label + "\t" + m.searchString + 
                        "\t" + m.matchedString + "\t" + m.type + "\t" + m.field + "\t" + p.rxCuiTxt);
            }
        }
    }
}

enum MatchType {
    EXACT,
    CASE_INSENSITIVE
}

enum MatchedField {
    PREF_LABEL,
    SYNONYM
}

class SearchMatch {
    MatchType type;
    MatchedField field;
    String searchString;
    String matchedString;
    String classIriTxt;
}

class SearchParams {
    String label;
    String dronIriTxt;
    String rxCuiTxt;
}
