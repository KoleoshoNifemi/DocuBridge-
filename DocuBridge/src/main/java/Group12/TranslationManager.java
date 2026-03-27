package Group12;

import javafx.application.Platform;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TranslationManager {
    private TranslationService translationService;
    private String targetLanguage = null;
    private String sourceLanguage = "en";

    public TranslationManager(String subscriptionKey, String region, String endpoint) {
        System.out.println("DEBUG: TranslationManager initialized");
        this.translationService = new TranslationService(subscriptionKey, region, endpoint);
    }

    public void enableTranslation(String targetLangCode) {
        this.targetLanguage = targetLangCode;
        System.out.println("DEBUG: ✓ Translation ENABLED for: " + targetLangCode);
    }

    public void disableTranslation() {
        this.targetLanguage = null;
        System.out.println("DEBUG: ✓ Translation DISABLED");
    }

    public static String[][] getSupportedLanguages() {
        return new String[][] {
                {"English", "en"},
                {"French", "fr"},
                {"Spanish", "es"},
                {"German", "de"},
                {"Greek", "el"}
        };
    }

    public void translateTextAsync(String text, Consumer<String> callback) {
        if (targetLanguage == null) {
            System.out.println("DEBUG: translateTextAsync - no target language, returning original");
            callback.accept(text);
            return;
        }

        new Thread(() -> {
            try {
                System.out.println("DEBUG: translateTextAsync - translating: " + text.substring(0, Math.min(50, text.length())));
                String translated = translationService.translate(text, sourceLanguage, targetLanguage);
                Platform.runLater(() -> {
                    System.out.println("DEBUG: translateTextAsync callback called");
                    callback.accept(translated != null ? translated : text);
                });
            } catch (Exception e) {
                System.err.println("Async translation error: " + e.getMessage());
                Platform.runLater(() -> callback.accept(text));
            }
        }, "translator").start();
    }

    public void translatePlainText(String text, Consumer<String> callback) {
        System.out.println("DEBUG: translatePlainText called");
        System.out.println("DEBUG: targetLanguage=" + targetLanguage);
        System.out.println("DEBUG: text length=" + (text != null ? text.length() : 0));

        if (targetLanguage == null) {
            System.out.println("DEBUG: ✗ No target language, returning original text");
            callback.accept(text);
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            System.out.println("DEBUG: ✗ Text is empty");
            callback.accept(text);
            return;
        }

        new Thread(() -> {
            try {
                System.out.println("DEBUG: Calling translation service with from=" + sourceLanguage + ", to=" + targetLanguage);
                String translated = translationService.translate(text, sourceLanguage, targetLanguage);
                System.out.println("DEBUG: Translation service returned: " + (translated != null ? translated.substring(0, Math.min(50, translated.length())) : "null"));
                Platform.runLater(() -> {
                    System.out.println("DEBUG: Calling callback with translated text");
                    callback.accept(translated != null ? translated : text);
                });
            } catch (Exception e) {
                System.err.println("Translation error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    System.out.println("DEBUG: Exception in translation, returning original");
                    callback.accept(text);
                });
            }
        }, "translator").start();
    }

    /**
     * Translates a Quill delta JSON string, preserving all formatting attributes.
     * Only the text content of each run is translated; bold/italic/color/highlight/
     * header/list/image ops etc. are left untouched.
     * Callback is called on the JavaFX Application Thread with the new delta JSON.
     */
    public void translateDeltaAsync(String deltaJson, Consumer<String> callback) {
        if (targetLanguage == null || deltaJson == null || deltaJson.trim().isEmpty()) {
            Platform.runLater(() -> callback.accept(deltaJson));
            return;
        }
        new Thread(() -> {
            try {
                JSONObject delta = new JSONObject(deltaJson);
                JSONArray ops = delta.getJSONArray("ops");

                // Collect indices and text of ops that need translation (skip \n and image ops)
                List<Integer> textOpIndices = new ArrayList<>();
                List<String>  textsToTranslate = new ArrayList<>();
                for (int i = 0; i < ops.length(); i++) {
                    JSONObject op = ops.getJSONObject(i);
                    if (!op.has("insert")) continue;
                    Object insert = op.get("insert");
                    if (!(insert instanceof String)) continue;       // skip image/embed ops
                    String text = (String) insert;
                    if (text.isEmpty() || text.equals("\n")) continue; // skip bare newlines
                    textOpIndices.add(i);
                    textsToTranslate.add(text);
                }

                if (textsToTranslate.isEmpty()) {
                    Platform.runLater(() -> callback.accept(deltaJson));
                    return;
                }

                List<String> translated = translationService.translateBatch(textsToTranslate, sourceLanguage, targetLanguage);
                if (translated == null || translated.size() != textsToTranslate.size()) {
                    Platform.runLater(() -> callback.accept(deltaJson));
                    return;
                }

                // Sanitise: Azure occasionally inserts \n into a single translated run,
                // which creates unwanted paragraph breaks when applied to Quill.
                // Replace embedded newlines with a space and fall back to original text
                // if the translation came back empty.
                for (int i = 0; i < translated.size(); i++) {
                    String t = translated.get(i);
                    if (t == null || t.isEmpty()) {
                        translated.set(i, textsToTranslate.get(i)); // keep original
                    } else {
                        translated.set(i, t.replace("\n", " ").replace("\r", ""));
                    }
                }

                // Rebuild ops with translated text, all attributes unchanged
                JSONArray newOps = new JSONArray();
                int ptr = 0;
                for (int i = 0; i < ops.length(); i++) {
                    JSONObject op = new JSONObject(ops.getJSONObject(i).toString()); // clone
                    if (ptr < textOpIndices.size() && textOpIndices.get(ptr) == i) {
                        op.put("insert", translated.get(ptr++));
                    }
                    newOps.put(op);
                }

                JSONObject newDelta = new JSONObject();
                newDelta.put("ops", newOps);
                final String result = newDelta.toString();
                Platform.runLater(() -> callback.accept(result));

            } catch (Exception e) {
                System.err.println("Delta translation error: " + e.getMessage());
                Platform.runLater(() -> callback.accept(deltaJson));
            }
        }, "translator").start();
    }

    public boolean isTranslationEnabled() {
        boolean result = targetLanguage != null;
        System.out.println("DEBUG: isTranslationEnabled() = " + result);
        return result;
    }

    public String getTargetLanguage() {
        System.out.println("DEBUG: getTargetLanguage() = " + targetLanguage);
        return targetLanguage;
    }

    public void setSourceLanguage(String langCode) {
        this.sourceLanguage = langCode;
        System.out.println("DEBUG: Source language set to: " + langCode);
    }
}