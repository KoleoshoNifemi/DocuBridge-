package Group12;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

//thin wrapper around the Azure Cognitive Services Translator REST API (v3)
//does no state management - just builds requests and parses responses
public class TranslationService {
    private final String subscriptionKey;
    private final String endpoint;
    private final String region;
    //OkHttpClient is thread-safe and reuses connections, so one instance is fine for the whole app
    private final OkHttpClient client;

    public TranslationService(String subscriptionKey, String region, String endpoint) {
        //System.out.println("DEBUG: TranslationService initialized");
        //System.out.println("DEBUG: Key present? " + (subscriptionKey != null && !subscriptionKey.isEmpty()));
        //System.out.println("DEBUG: Region: " + region);
        //System.out.println("DEBUG: Endpoint: " + endpoint);
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.endpoint = endpoint;
        this.client = new OkHttpClient();
    }

    //translates a single string; returns null on API error (caller decides what to do)
    public String translate(String text, String sourceLanguage, String targetLanguage) {
        //System.out.println("DEBUG: TranslationService.translate() called");
        //System.out.println("DEBUG: text: " + text);
        //System.out.println("DEBUG: from " + sourceLanguage + " to " + targetLanguage);

        if (text == null || text.trim().isEmpty()) {
            //System.out.println("DEBUG: ✗ Text is empty, returning null");
            return text;
        }

        try {
            //Azure expects a JSON array even for a single string
            JSONArray body = new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("Text", text);
            body.put(obj);

            //omit &from= entirely when sourceLanguage is empty so Azure auto-detects
            String url = endpoint + "/translate?api-version=3.0"
                    + (sourceLanguage != null && !sourceLanguage.isEmpty() ? "&from=" + sourceLanguage : "")
                    + "&to=" + targetLanguage;
            //System.out.println("DEBUG: URL=" + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    //Azure Translator needs both the key and the region header
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .addHeader("Ocp-Apim-Subscription-Region", region)
                    .addHeader("Content-Type", "application/json")
                    .build();

            //System.out.println("DEBUG: Making HTTP request...");
            Response response = client.newCall(request).execute();
            //System.out.println("DEBUG: Response code: " + response.code());

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                //System.out.println("DEBUG: Response body: " + responseBody);
                //response is an array of results, one per input text; we only sent one so grab index 0
                JSONArray jsonResponse = new JSONArray(responseBody);
                String result = jsonResponse
                        .getJSONObject(0)
                        .getJSONArray("translations")
                        .getJSONObject(0)
                        .getString("text");
                //System.out.println("DEBUG: ✓ Translation successful: " + result);
                return result;
            } else {
                System.err.println("Translation API error: " + response.code());
                if (response.body() != null) {
                    System.err.println("Error body: " + response.body().string());
                }
                return null;
            }
        } catch (Exception e) {
            System.err.println("Translation exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** Translates a list of strings in a single API call, preserving order. Returns null on failure. */
    public List<String> translateBatch(List<String> texts, String from, String to) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();
        try {
            //build a JSON array with one {"Text": ...} object per input string
            JSONArray body = new JSONArray();
            for (String text : texts) body.put(new JSONObject().put("Text", text));

            //same URL pattern as single translate - Azure handles 1 or N strings the same way
            String url = endpoint + "/translate?api-version=3.0"
                    + (from != null && !from.isEmpty() ? "&from=" + from : "")
                    + "&to=" + to;
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .addHeader("Ocp-Apim-Subscription-Region", region)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONArray jsonResponse = new JSONArray(response.body().string());
                //Azure guarantees results are in the same order as the inputs
                List<String> results = new ArrayList<>();
                for (int i = 0; i < jsonResponse.length(); i++) {
                    results.add(jsonResponse.getJSONObject(i)
                            .getJSONArray("translations").getJSONObject(0).getString("text"));
                }
                return results;
            }
            System.err.println("Batch translation API error: " + response.code());
        } catch (Exception e) {
            System.err.println("Batch translation exception: " + e.getMessage());
        }
        //null signals a hard failure to the caller (as opposed to an empty list, which is valid)
        return null;
    }
}
