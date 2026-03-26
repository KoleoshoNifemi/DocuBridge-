package Group12;

import javafx.application.Platform;
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