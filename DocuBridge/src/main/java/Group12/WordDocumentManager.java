package Group12;

import org.apache.poi.xwpf.usermodel.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STVerticalAlignRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;

public class WordDocumentManager {

    public static void createWordFile(String fileName, String content) {
        try (XWPFDocument document = new XWPFDocument()) {
            //Quill exports content as a Delta JSON object; check for that before treating it as plain text
            if (content != null && content.trim().startsWith("{")) {
                buildDocumentFromDelta(document, content);
            } else {
                //Plain text fallback - just dump everything into one run
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(content != null ? content : "");
            }

            //Save to ~/Documents/DocuBridge/, creating the folder if it doesn't exist
            String documentsFolder = System.getProperty("user.home") + java.io.File.separator + "Documents";
            String docuBridgeFolder = documentsFolder + java.io.File.separator + "DocuBridge";
            new java.io.File(docuBridgeFolder).mkdirs();

            //Ensure the file always gets the .docx extension
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

            //Start with one blank paragraph; we'll keep reusing/extending it until we hit a \n
            XWPFParagraph currentParagraph = document.createParagraph();
            //These are lazily created the first time we encounter each list type in the document
            BigInteger bulletNumId  = null;
            BigInteger orderedNumId = null;

            for (int i = 0; i < ops.length(); i++) {
                JSONObject op = ops.getJSONObject(i);
                //Only "insert" ops matter here; skip retain/delete
                if (!op.has("insert")) continue;

                Object insertVal = op.get("insert");
                JSONObject attrs = op.has("attributes") ? op.getJSONObject("attributes") : null;

                // Image embed: {"insert": {"image": "data:image/...;base64,..."}}
                if (insertVal instanceof JSONObject) {
                    String imgUri = ((JSONObject) insertVal).optString("image", null);
                    if (imgUri != null) insertImage(document, currentParagraph, imgUri);
                    continue;
                }

                if (!(insertVal instanceof String)) continue;

                //Split on \n because in Quill's Delta format, a newline carries the paragraph-level
                //attributes (alignment, list type, header) for the paragraph that just ended
                String text = (String) insertVal;
                String[] parts = text.split("\n", -1);

                for (int p = 0; p < parts.length; p++) {
                    if (!parts[p].isEmpty()) {
                        XWPFRun run = currentParagraph.createRun();
                        run.setText(parts[p]);
                        applyRunFormatting(run, attrs);
                    }

                    if (p < parts.length - 1) {
                        //We just crossed a \n - apply paragraph-level formatting to the paragraph we finished
                        applyParagraphFormatting(currentParagraph, attrs);

                        if (attrs != null) {
                            // Lists
                            String listType = attrs.optString("list", null);
                            if ("bullet".equals(listType)) {
                                //Reuse the same numId for all bullets so they belong to one list
                                if (bulletNumId == null) bulletNumId = createBulletList(document);
                                currentParagraph.setNumID(bulletNumId);
                                currentParagraph.setNumILvl(BigInteger.ZERO);
                            } else if ("ordered".equals(listType)) {
                                if (orderedNumId == null) orderedNumId = createOrderedList(document);
                                currentParagraph.setNumID(orderedNumId);
                                currentParagraph.setNumILvl(BigInteger.ZERO);
                            }

                            // Headers (h1–h6)
                            Object headerVal = attrs.opt("header");
                            if (headerVal != null) {
                                String hStr = headerVal.toString();
                                //"false" and "null" show up when the attribute is explicitly cleared in Quill
                                if (!hStr.isEmpty() && !hStr.equals("null") && !hStr.equals("false")) {
                                    try {
                                        int level = Integer.parseInt(hStr);
                                        if (level >= 1 && level <= 6)
                                            currentParagraph.setStyle("Heading" + level);
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }

                        //Move on to the next paragraph for the content that follows
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

        //standard character formatting
        if (attrs.optBoolean("bold",      false)) run.setBold(true);
        if (attrs.optBoolean("italic",    false)) run.setItalic(true);
        if (attrs.optBoolean("underline", false)) run.setUnderline(UnderlinePatterns.SINGLE);
        if (attrs.optBoolean("strike",    false)) run.setStrikeThrough(true);

        //Quill colors come in as "#rrggbb"; POI wants them without the leading #
        String color = attrs.optString("color", null);
        if (color != null && color.startsWith("#"))
            run.setColor(color.substring(1));

        //Quill stores font size as CSS pixels (e.g. "16px"); convert to points for Word
        String size = attrs.optString("size", null);
        if (size != null && size.endsWith("px")) {
            try {
                int pt = (int) Math.round(Integer.parseInt(size.replace("px", "").trim()) * 0.75);
                run.setFontSize(pt);
            } catch (NumberFormatException ignored) {}
        }

        String font = attrs.optString("font", null);
        if (font != null && !font.isEmpty() && !font.equals("null"))
            run.setFontFamily(getFontName(font));

        //subscript / superscript - must use raw CTRPr, XWPFRun has no public API for this
        String script = attrs.optString("script", null);
        if ("sub".equals(script) || "super".equals(script)) {
            CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
            rPr.addNewVertAlign().setVal(
                "sub".equals(script) ? STVerticalAlignRun.SUBSCRIPT : STVerticalAlignRun.SUPERSCRIPT
            );
        }

        //Word uses shading (CTShd) to represent background highlight, not a dedicated highlight element
        String bg = attrs.optString("background", null);
        if (bg != null && !bg.isEmpty() && !bg.equals("false")) {
            String hex = colorToHex(bg);
            if (hex != null) {
                CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
                CTShd shd = rPr.addNewShd();
                shd.setVal(STShd.CLEAR);
                shd.setColor("auto");
                shd.setFill(hex);
            }
        }
    }

    private static void applyParagraphFormatting(XWPFParagraph paragraph, JSONObject attrs) {
        if (attrs == null) return;
        String align = attrs.optString("align", null);
        if (align != null) {
            //"justify" maps to BOTH in OOXML terminology
            switch (align) {
                case "center":  paragraph.setAlignment(ParagraphAlignment.CENTER); break;
                case "right":   paragraph.setAlignment(ParagraphAlignment.RIGHT);  break;
                case "justify": paragraph.setAlignment(ParagraphAlignment.BOTH);   break;
                default:        paragraph.setAlignment(ParagraphAlignment.LEFT);
            }
        }
    }

    //list numbering helpers

    private static BigInteger createBulletList(XWPFDocument document) {
        //Build an abstract numbering definition for a single-level bullet list, then register it
        CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
        CTLvl lvl = ctAbstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
        lvl.addNewLvlText().setVal("\u2022"); // •
        CTInd ind = lvl.addNewPPr().addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));
        XWPFNumbering numbering = document.createNumbering();
        return numbering.addNum(numbering.addAbstractNum(new XWPFAbstractNum(ctAbstractNum)));
    }

    private static BigInteger createOrderedList(XWPFDocument document) {
        //Same structure as bullet but uses DECIMAL format and "%1." as the level text pattern
        CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
        CTLvl lvl = ctAbstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl.addNewLvlText().setVal("%1.");
        CTInd ind = lvl.addNewPPr().addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));
        XWPFNumbering numbering = document.createNumbering();
        return numbering.addNum(numbering.addAbstractNum(new XWPFAbstractNum(ctAbstractNum)));
    }

    //image helper

    private static void insertImage(XWPFDocument document, XWPFParagraph paragraph, String dataUri) {
        try {
            //Data URIs are "data:<mime>;base64,<data>" - split at the comma to separate header from payload
            int comma = dataUri.indexOf(',');
            if (comma < 0) return;
            String header  = dataUri.substring(0, comma);
            byte[] imgData = java.util.Base64.getDecoder().decode(dataUri.substring(comma + 1).trim());

            //Default to PNG; switch based on what the MIME header says
            int picType = XWPFDocument.PICTURE_TYPE_PNG;
            if (header.contains("jpeg") || header.contains("jpg")) picType = XWPFDocument.PICTURE_TYPE_JPEG;
            else if (header.contains("gif"))                        picType = XWPFDocument.PICTURE_TYPE_GIF;
            else if (header.contains("bmp"))                        picType = XWPFDocument.PICTURE_TYPE_BMP;

            // Read actual pixel dimensions; fall back to a sensible default
            int widthPx = 400, heightPx = 300;
            try {
                java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new ByteArrayInputStream(imgData));
                if (img != null) { widthPx = img.getWidth(); heightPx = img.getHeight(); }
            } catch (Exception ignored) {}

            // Cap at ~500 px wide so image fits within normal page margins
            if (widthPx > 500) {
                heightPx = (int)(heightPx * 500.0 / widthPx);
                widthPx  = 500;
            }

            XWPFRun run = paragraph.createRun();
            run.addPicture(
                new ByteArrayInputStream(imgData),
                picType, "image",
                widthPx  * 9525,   // pixels → EMU at 96 dpi (1 px = 9525 EMU)
                heightPx * 9525
            );
        } catch (Exception e) {
            System.err.println("Error inserting image: " + e.getMessage());
        }
    }

    //colour helpers

    //returns uppercase hex (no #) for a CSS hex or named colour, or null if unknown
    private static String colorToHex(String color) {
        if (color == null) return null;
        //Already a hex value, just strip the # and normalise case
        if (color.startsWith("#")) return color.substring(1).toUpperCase();
        //Map the CSS named colors that Quill commonly uses; anything else we can't handle
        switch (color.toLowerCase()) {
            case "yellow":  return "FFFF00";
            case "lime":    return "00FF00";
            case "cyan":    return "00FFFF";
            case "magenta": return "FF00FF";
            case "blue":    return "0000FF";
            case "red":     return "FF0000";
            case "navy":    return "000080";
            case "teal":    return "008080";
            case "green":   return "008000";
            case "purple":  return "800080";
            case "maroon":  return "800000";
            case "gray":    return "808080";
            case "silver":  return "C0C0C0";
            case "black":   return "000000";
            case "white":   return "FFFFFF";
            default:        return null;
        }
    }

    //Quill stores font names in a normalised form (e.g. "courier-new"); map them back to proper names
    private static String getFontName(String quillFont) {
        switch (quillFont.toLowerCase().replace("-", " ")) {
            case "arial":           return "Arial";
            case "courier new":     return "Courier New";
            case "georgia":         return "Georgia";
            case "times new roman": return "Times New Roman";
            default:                return quillFont;
        }
    }

    //read

    public static String readWordFile(String fileName) {
        try (FileInputStream fis = new FileInputStream(fileName);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder content = new StringBuilder();
            //Iterate paragraphs and join with newlines - formatting is discarded, plain text only
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
