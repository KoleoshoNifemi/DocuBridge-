package Group12;

import org.apache.poi.xwpf.usermodel.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class WordDocumentManager {

    public static void createWordFile(String fileName, String content) {
        try (XWPFDocument document = new XWPFDocument()) {
            if (content != null && content.trim().startsWith("{")) {
                buildDocumentFromDelta(document, content);
            } else {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(content != null ? content : "");
            }

            String documentsFolder = System.getProperty("user.home") + java.io.File.separator + "Documents";
            String docuBridgeFolder = documentsFolder + java.io.File.separator + "DocuBridge";
            new java.io.File(docuBridgeFolder).mkdirs();

            String fileName_final = fileName.endsWith(".docx") ? fileName : fileName + ".docx";
            String filePath = docuBridgeFolder + java.io.File.separator + fileName_final;

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                document.write(out);
                System.out.println("✓ Word file created: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error creating Word file: " + e.getMessage());
        }
    }

    private static void buildDocumentFromDelta(XWPFDocument document, String deltaJson) {
        try {
            JSONObject json = new JSONObject(deltaJson);
            JSONArray ops = json.getJSONArray("ops");

            XWPFParagraph currentParagraph = document.createParagraph();

            for (int i = 0; i < ops.length(); i++) {
                JSONObject op = ops.getJSONObject(i);
                if (!op.has("insert")) continue;

                Object insertVal = op.get("insert");
                if (!(insertVal instanceof String)) continue; // skip image embeds

                String text = (String) insertVal;
                JSONObject attrs = op.has("attributes") ? op.getJSONObject("attributes") : null;

                String[] parts = text.split("\n", -1);

                for (int p = 0; p < parts.length; p++) {
                    String part = parts[p];

                    if (!part.isEmpty()) {
                        XWPFRun run = currentParagraph.createRun();
                        run.setText(part);
                        applyRunFormatting(run, attrs);
                    }

                    if (p < parts.length - 1) {
                        // End of a line — apply paragraph-level formatting then start a new paragraph
                        applyParagraphFormatting(currentParagraph, attrs);
                        currentParagraph = document.createParagraph();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error building document from delta: " + e.getMessage());
        }
    }

    private static void applyRunFormatting(XWPFRun run, JSONObject attrs) {
        if (attrs == null) return;

        if (attrs.optBoolean("bold", false)) run.setBold(true);
        if (attrs.optBoolean("italic", false)) run.setItalic(true);
        if (attrs.optBoolean("underline", false)) run.setUnderline(UnderlinePatterns.SINGLE);
        if (attrs.optBoolean("strike", false)) run.setStrikeThrough(true);

        String color = attrs.optString("color", null);
        if (color != null && !color.isEmpty() && color.startsWith("#")) {
            run.setColor(color.substring(1)); // POI expects hex without #
        }

        String size = attrs.optString("size", null);
        if (size != null && size.endsWith("px")) {
            try {
                int px = Integer.parseInt(size.replace("px", "").trim());
                int pt = (int) Math.round(px * 0.75); // px to pt
                run.setFontSize(pt);
            } catch (NumberFormatException ignored) {}
        }

        String font = attrs.optString("font", null);
        if (font != null && !font.isEmpty() && !font.equals("null")) {
            run.setFontFamily(getFontName(font));
        }
    }

    private static void applyParagraphFormatting(XWPFParagraph paragraph, JSONObject attrs) {
        if (attrs == null) return;

        String align = attrs.optString("align", null);
        if (align != null) {
            switch (align) {
                case "center":  paragraph.setAlignment(ParagraphAlignment.CENTER); break;
                case "right":   paragraph.setAlignment(ParagraphAlignment.RIGHT);  break;
                case "justify": paragraph.setAlignment(ParagraphAlignment.BOTH);   break;
                default:        paragraph.setAlignment(ParagraphAlignment.LEFT);
            }
        }
    }

    private static String getFontName(String quillFont) {
        switch (quillFont.toLowerCase().replace("-", " ")) {
            case "arial":            return "Arial";
            case "courier new":      return "Courier New";
            case "georgia":          return "Georgia";
            case "times new roman":  return "Times New Roman";
            default:                 return quillFont;
        }
    }

    public static String readWordFile(String fileName) {
        try (FileInputStream fis = new FileInputStream(fileName);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder content = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            System.err.println("Error reading Word file: " + e.getMessage());
            return "";
        }
    }
}
