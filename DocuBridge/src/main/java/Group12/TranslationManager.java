package Group12;

import javafx.application.Platform;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

//sits between the UI and TranslationService - handles enable/disable state and
//makes sure callbacks always come back on the JavaFX thread
public class TranslationManager {
    private TranslationService translationService;
    private String targetLanguage = null;
    private String sourceLanguage = "";  // empty = auto-detect source language

    public TranslationManager(String subscriptionKey, String region, String endpoint) {
        //System.out.println("DEBUG: TranslationManager initialized");
        this.translationService = new TranslationService(subscriptionKey, region, endpoint);
    }

    //setting targetLanguage to non-null is what "turns on" translation throughout this class
    public void enableTranslation(String targetLangCode) {
        this.targetLanguage = targetLangCode;
        //System.out.println("DEBUG: ✓ Translation ENABLED for: " + targetLangCode);
    }

    //null targetLanguage is the off switch - every method checks this before doing any work
    public void disableTranslation() {
        this.targetLanguage = null;
        //System.out.println("DEBUG: ✓ Translation DISABLED");
    }

    //hardcoded list of supported languages - lang code is what gets sent to Azure
    public static String[][] getSupportedLanguages() {
        return new String[][] {
                {"English", "en"},
                {"French", "fr"},
                {"Spanish", "es"},
                {"German", "de"},
                {"Greek", "el"}
        };
    }

    //lightweight async translate for short text (e.g. a single typed word)
    //if translation is off, just returns the original text immediately
    public void translateTextAsync(String text, Consumer<String> callback) {
        if (targetLanguage == null) {
            //System.out.println("DEBUG: translateTextAsync - no target language, returning original");
            callback.accept(text);
            return;
        }

        new Thread(() -> {
            try {
                //System.out.println("DEBUG: translateTextAsync - translating: " + text.substring(0, Math.min(50, text.length())));
                String translated = translationService.translate(text, sourceLanguage, targetLanguage);
                //always marshal back to the FX thread before touching UI state
                Platform.runLater(() -> {
                    //System.out.println("DEBUG: translateTextAsync callback called");
                    //fall back to original if the API returned nothing
                    callback.accept(translated != null ? translated : text);
                });
            } catch (Exception e) {
                System.err.println("Async translation error: " + e.getMessage());
                Platform.runLater(() -> callback.accept(text));
            }
        }, "translator").start();
    }

    //same idea as translateTextAsync but with more guard checks and logging
    //used when translating the full document text (plain string, no delta)
    public void translatePlainText(String text, Consumer<String> callback) {
        //System.out.println("DEBUG: translatePlainText called");
        //System.out.println("DEBUG: targetLanguage=" + targetLanguage);
        //System.out.println("DEBUG: text length=" + (text != null ? text.length() : 0));

        if (targetLanguage == null) {
            //System.out.println("DEBUG: ✗ No target language, returning original text");
            callback.accept(text);
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            //System.out.println("DEBUG: ✗ Text is empty");
            callback.accept(text);
            return;
        }

        new Thread(() -> {
            try {
                //System.out.println("DEBUG: Calling translation service with from=" + sourceLanguage + ", to=" + targetLanguage);
                String translated = translationService.translate(text, sourceLanguage, targetLanguage);
                //System.out.println("DEBUG: Translation service returned: " + (translated != null ? translated.substring(0, Math.min(50, translated.length())) : "null"));
                Platform.runLater(() -> {
                    //System.out.println("DEBUG: Calling callback with translated text");
                    callback.accept(translated != null ? translated : text);
                });
            } catch (Exception e) {
                System.err.println("Translation error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    //System.out.println("DEBUG: Exception in translation, returning original");
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

                // Split each text op on embedded \n before sending to Azure so that
                // paragraph breaks are never included in a translated segment. Each \n
                // is recorded as a sentinel and stitched back in after translation.
                // opSegments[i] is null for non-text ops; otherwise a list where each
                // entry is either {"n"} (a preserved newline) or {"t", originalText, batchIndex}.
                List<List<String[]>> opSegments = new ArrayList<>();
                List<String> textsToTranslate = new ArrayList<>();

                for (int i = 0; i < ops.length(); i++) {
                    JSONObject op = ops.getJSONObject(i);
                    //skip ops that aren't inserts (e.g. retain/delete) and non-string inserts (e.g. embedded images)
                    if (!op.has("insert")) { opSegments.add(null); continue; }
                    Object insert = op.get("insert");
                    if (!(insert instanceof String)) { opSegments.add(null); continue; }
                    String text = (String) insert;
                    if (text.isEmpty()) { opSegments.add(null); continue; }

                    //walk char-by-char so we can split on \n without losing where each piece maps to in the batch
                    List<String[]> segments = new ArrayList<>();
                    StringBuilder buf = new StringBuilder();
                    for (char c : text.toCharArray()) {
                        if (c == '\n') {
                            //flush whatever's buffered before the newline as a translate segment
                            if (buf.length() > 0) {
                                int idx = textsToTranslate.size();
                                textsToTranslate.add(buf.toString());
                                segments.add(new String[]{"t", buf.toString(), String.valueOf(idx)});
                                buf.setLength(0);
                            }
                            //store the newline as a sentinel so we can put it back later
                            segments.add(new String[]{"n"});
                        } else {
                            buf.append(c);
                        }
                    }
                    //flush any remaining text after the last \n (or the whole string if no \n)
                    if (buf.length() > 0) {
                        int idx = textsToTranslate.size();
                        textsToTranslate.add(buf.toString());
                        segments.add(new String[]{"t", buf.toString(), String.valueOf(idx)});
                    }
                    opSegments.add(segments.isEmpty() ? null : segments);
                }

                //nothing translatable in the whole delta - return as-is
                if (textsToTranslate.isEmpty()) {
                    Platform.runLater(() -> callback.accept(deltaJson));
                    return;
                }

                //send all text segments in one batch request to avoid multiple round trips
                List<String> translated = translationService.translateBatch(textsToTranslate, sourceLanguage, targetLanguage);
                //if the API failed or returned a mismatched count, bail out with original
                if (translated == null || translated.size() != textsToTranslate.size()) {
                    Platform.runLater(() -> callback.accept(deltaJson));
                    return;
                }

                // Sanitise: fall back to original if Azure returns empty; strip any \r.
                // Each segment is already guaranteed \n-free (we split on them above),
                // so any \n Azure injects into a segment is spurious and gets stripped.
                for (int i = 0; i < translated.size(); i++) {
                    String t = translated.get(i);
                    if (t == null || t.isEmpty()) {
                        translated.set(i, textsToTranslate.get(i));
                    } else {
                        translated.set(i, t.replace("\n", " ").replace("\r", ""));
                    }
                }

                // Rebuild ops: reassemble each op's text from its segments, with \n restored.
                JSONArray newOps = new JSONArray();
                for (int i = 0; i < ops.length(); i++) {
                    JSONObject op = new JSONObject(ops.getJSONObject(i).toString()); // clone
                    List<String[]> segments = opSegments.get(i);
                    if (segments != null) {
                        StringBuilder sb = new StringBuilder();
                        for (String[] seg : segments) {
                            if ("n".equals(seg[0])) {
                                //put the newline back exactly where it was
                                sb.append('\n');
                            } else {
                                //look up the translated text by the batch index we stored earlier
                                sb.append(translated.get(Integer.parseInt(seg[2])));
                            }
                        }
                        op.put("insert", sb.toString());
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
        //System.out.println("DEBUG: isTranslationEnabled() = " + result);
        return result;
    }

    public String getTargetLanguage() {
        //System.out.println("DEBUG: getTargetLanguage() = " + targetLanguage);
        return targetLanguage;
    }

    //empty string means "let Azure auto-detect" - that's the default
    public void setSourceLanguage(String langCode) {
        this.sourceLanguage = langCode;
        //System.out.println("DEBUG: Source language set to: " + langCode);
    }
}
