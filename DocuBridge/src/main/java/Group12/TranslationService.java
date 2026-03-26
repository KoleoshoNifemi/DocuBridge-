package Group12;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class TranslationService {
    private final String subscriptionKey;
    private final String endpoint;
    private final String region;
    private final OkHttpClient client;

    public TranslationService(String subscriptionKey, String region, String endpoint) {
        System.out.println("DEBUG: TranslationService initialized");
        System.out.println("DEBUG: Key present? " + (subscriptionKey != null && !subscriptionKey.isEmpty()));
        System.out.println("DEBUG: Region: " + region);
        System.out.println("DEBUG: Endpoint: " + endpoint);
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.endpoint = endpoint;
        this.client = new OkHttpClient();
    }

    public String translate(String text, String sourceLanguage, String targetLanguage) {
        System.out.println("DEBUG: TranslationService.translate() called");
        System.out.println("DEBUG: text: " + text);
        System.out.println("DEBUG: from " + sourceLanguage + " to " + targetLanguage);

        if (text == null || text.trim().isEmpty()) {
            System.out.println("DEBUG: ✗ Text is empty, returning null");
            return text;
        }

        try {
            JSONArray body = new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("Text", text);
            body.put(obj);

            String url = endpoint + "/translate?api-version=3.0&from=" + sourceLanguage + "&to=" + targetLanguage;
            System.out.println("DEBUG: URL=" + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .addHeader("Ocp-Apim-Subscription-Region", region)
                    .addHeader("Content-Type", "application/json")
                    .build();

            System.out.println("DEBUG: Making HTTP request...");
            Response response = client.newCall(request).execute();
            System.out.println("DEBUG: Response code: " + response.code());

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                System.out.println("DEBUG: Response body: " + responseBody);
                JSONArray jsonResponse = new JSONArray(responseBody);
                String result = jsonResponse
                        .getJSONObject(0)
                        .getJSONArray("translations")
                        .getJSONObject(0)
                        .getString("text");
                System.out.println("DEBUG: ✓ Translation successful: " + result);
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
}