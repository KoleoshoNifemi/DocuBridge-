package Group12;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class WordDocumentManager {

    public static void createWordFile(String fileName, String content) {
        try (XWPFDocument document = new XWPFDocument()) {
            // Extract plain text from Quill JSON
            String plainText = extractPlainText(content);

            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(plainText);

            String documentsFolder = System.getProperty("user.home") + java.io.File.separator + "Documents";

            // Only add .docx if it doesn't already have it
            String fileName_final = fileName.endsWith(".docx") ? fileName : fileName + ".docx";
            String filePath = documentsFolder + java.io.File.separator + fileName_final;

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                document.write(out);
                System.out.println("✓ Word file created: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error creating Word file: " + e.getMessage());
        }
    }

    private static String extractPlainText(String jsonContent) {
        try {
            // If it's Quill delta JSON
            if (jsonContent.startsWith("{")) {
                JSONObject json = new JSONObject(jsonContent);
                JSONArray ops = json.getJSONArray("ops");
                StringBuilder text = new StringBuilder();

                for (int i = 0; i < ops.length(); i++) {
                    JSONObject op = ops.getJSONObject(i);
                    if (op.has("insert")) {
                        text.append(op.getString("insert"));
                    }
                }
                return text.toString();
            }
        } catch (Exception e) {
            System.err.println("Error extracting text: " + e.getMessage());
        }
        return jsonContent; // Return as-is if not JSON
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