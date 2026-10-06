import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * Writes real vector PDFs (text stays text) from the layout dumps that
 * StandardDocumentTemplateTest / InvoiceLayoutRenderTest produce. Uses the
 * PDF base-14 Helvetica fonts so it runs on any JDK with no extra libraries.
 * The app itself paints the same ops with android.graphics.pdf.PdfDocument.
 *
 * usage: java render-standard-document-pdf.java layout.txt logo.png.b64 out-dir
 * One PDF per document: pages named "invoice", "invoice-p2" go into invoice.pdf.
 */
class render_standard_document_pdf {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("usage: dump.txt logo.b64 out-dir");
        File outDir = new File(args[2]);
        outDir.mkdirs();
        BufferedImage logo = null;
        File logoFile = new File(args[1]);
        if (logoFile.exists()) {
            byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(logoFile.toPath()).trim());
            logo = ImageIO.read(new ByteArrayInputStream(bytes));
        }
        Map<String, List<Page>> docs = new LinkedHashMap<>();
        Page current = null;
        for (String line : Files.readAllLines(new File(args[0]).toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("PAGE\t")) {
                String[] p = line.split("\t");
                current = new Page(p[1], Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                String key = p[1].replaceAll("-p\\d+$", "");
                docs.computeIfAbsent(key, k -> new ArrayList<>()).add(current);
            } else if (current != null && !line.isBlank()) {
                current.ops.add(line);
            }
        }
        for (Map.Entry<String, List<Page>> entry : docs.entrySet()) {
            File out = new File(outDir, entry.getKey() + ".pdf");
            try (FileOutputStream stream = new FileOutputStream(out)) {
                stream.write(write(entry.getValue(), logo));
            }
        }
    }

    static byte[] write(List<Page> pages, BufferedImage logo) throws Exception {
        List<byte[]> objects = new ArrayList<>();
        // 1 catalog, 2 pages, 3-5 fonts, 6 logo (optional), then page/content pairs
        objects.add(null);
        objects.add(null);
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Italic /Encoding /WinAnsiEncoding >>"));
        int logoObj = 0;
        if (logo != null) {
            int w = logo.getWidth();
            int h = logo.getHeight();
            BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = flat.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(logo, 0, 0, null);
            g.dispose();
            byte[] rgb = new byte[w * h * 3];
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = flat.getRGB(x, y);
                    rgb[i++] = (byte) ((px >> 16) & 0xff);
                    rgb[i++] = (byte) ((px >> 8) & 0xff);
                    rgb[i++] = (byte) (px & 0xff);
                }
            }
            byte[] data = deflate(rgb);
            ByteArrayOutputStream obj = new ByteArrayOutputStream();
            obj.write(ascii("<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + data.length + " >>\nstream\n"));
            obj.write(data);
            obj.write(ascii("\nendstream"));
            objects.add(obj.toByteArray());
            logoObj = objects.size();
        }
        List<Integer> pageIds = new ArrayList<>();
        for (Page page : pages) {
            byte[] content = deflate(content(page).getBytes(StandardCharsets.ISO_8859_1));
            ByteArrayOutputStream obj = new ByteArrayOutputStream();
            obj.write(ascii("<< /Length " + content.length + " /Filter /FlateDecode >>\nstream\n"));
            obj.write(content);
            obj.write(ascii("\nendstream"));
            objects.add(obj.toByteArray());
            int contentId = objects.size();
            String xobj = logoObj > 0 ? " /XObject << /Im1 " + logoObj + " 0 R >>" : "";
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + page.width + " " + page.height + "]"
                + " /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >>" + xobj + " >> /Contents " + contentId + " 0 R >>"));
            pageIds.add(objects.size());
        }
        StringBuilder kids = new StringBuilder();
        for (int id : pageIds) kids.append(id).append(" 0 R ");
        objects.set(0, ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.set(1, ascii("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageIds.size() + " >>"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ascii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n"));
        long[] offsets = new long[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            out.write(ascii((i + 1) + " 0 obj\n"));
            out.write(objects.get(i));
            out.write(ascii("\nendobj\n"));
        }
        long xref = out.size();
        StringBuilder table = new StringBuilder();
        table.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (long offset : offsets) table.append(String.format(Locale.US, "%010d 00000 n \n", offset));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF\n");
        out.write(ascii(table.toString()));
        return out.toByteArray();
    }

    static String content(Page page) {
        StringBuilder s = new StringBuilder();
        float h = page.height;
        for (String raw : page.ops) {
            String[] p = raw.split("\t", -1);
            switch (p[0]) {
                case "text" -> {
                    String text = unescape(p[1]);
                    if (text.isEmpty()) break;
                    float size = Float.parseFloat(p[4]);
                    boolean bold = "1".equals(p[5]);
                    boolean italic = "1".equals(p[6]);
                    String font = italic ? "F3" : bold ? "F2" : "F1";
                    float x = Float.parseFloat(p[2]);
                    float width = italic ? text.length() * size * 0.45f : Metrics.width(text, bold) * size / 1000f;
                    if ("center".equals(p[7])) x -= width / 2f;
                    else if ("right".equals(p[7])) x -= width;
                    s.append(fill(p[8])).append(" BT /").append(font).append(' ').append(num(size)).append(" Tf ")
                        .append(num(x)).append(' ').append(num(h - Float.parseFloat(p[3]))).append(" Td ")
                        .append(literal(text)).append(" Tj ET\n");
                }
                case "line" -> s.append(stroke(p[6])).append(' ').append(num(Math.max(0.6f, Float.parseFloat(p[5])))).append(" w ")
                    .append(num(Float.parseFloat(p[1]))).append(' ').append(num(h - Float.parseFloat(p[2]))).append(" m ")
                    .append(num(Float.parseFloat(p[3]))).append(' ').append(num(h - Float.parseFloat(p[4]))).append(" l S\n");
                case "rect", "fillrect" -> {
                    float x1 = Float.parseFloat(p[1]);
                    float y1 = Float.parseFloat(p[2]);
                    float x2 = Float.parseFloat(p[3]);
                    float y2 = Float.parseFloat(p[4]);
                    String rect = num(x1) + " " + num(h - y2) + " " + num(x2 - x1) + " " + num(y2 - y1) + " re";
                    if (p[0].equals("rect")) s.append(stroke(p[5])).append(" 1.2 w ").append(rect).append(" S\n");
                    else s.append(fill(p[5])).append(' ').append(rect).append(" f\n");
                }
                case "logo" -> {
                    float x = Float.parseFloat(p[1]);
                    float y = Float.parseFloat(p[2]);
                    float size = Float.parseFloat(p[3]);
                    s.append("q ").append(num(size)).append(" 0 0 ").append(num(size)).append(' ')
                        .append(num(x)).append(' ').append(num(h - y - size)).append(" cm /Im1 Do Q\n");
                }
                default -> {
                }
            }
        }
        return s.toString();
    }

    static float[] rgb(String token) {
        int value;
        if (token.startsWith("#") && token.length() == 7) value = Integer.parseInt(token.substring(1), 16);
        else value = switch (token) {
            case "muted", "rule" -> 0x3A3A3A;
            case "light" -> 0xBEBEBE;
            case "white" -> 0xFFFFFF;
            case "paid" -> 0x0E6B38;
            default -> 0x141416;
        };
        return new float[] { ((value >> 16) & 0xff) / 255f, ((value >> 8) & 0xff) / 255f, (value & 0xff) / 255f };
    }

    static String fill(String token) {
        float[] c = rgb(token);
        return num(c[0]) + " " + num(c[1]) + " " + num(c[2]) + " rg";
    }

    static String stroke(String token) {
        float[] c = rgb(token);
        return num(c[0]) + " " + num(c[1]) + " " + num(c[2]) + " RG";
    }

    static String num(float v) {
        String s = String.format(Locale.US, "%.3f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    static int winAnsi(char c) {
        if (c >= 32 && c < 127) return c;
        return switch (c) {
            case '\u2014' -> 0x97;
            case '\u2013' -> 0x96;
            case '\u2022' -> 0x95;
            case '\u2019' -> 0x92;
            case '\u2018' -> 0x91;
            case '\u201C' -> 0x93;
            case '\u201D' -> 0x94;
            case '\u2026' -> 0x85;
            case '\u20AC' -> 0x80;
            default -> (c >= 0xA0 && c <= 0xFF) ? c : '?';
        };
    }

    static String literal(String text) {
        StringBuilder s = new StringBuilder("(");
        for (char c : text.toCharArray()) {
            int b = winAnsi(c);
            if (b == '(' || b == ')' || b == '\\') s.append('\\').append((char) b);
            else if (b < 32 || b > 126) s.append(String.format("\\%03o", b));
            else s.append((char) b);
        }
        return s.append(')').toString();
    }

    static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    static class Page {
        final String name;
        final int width;
        final int height;
        final List<String> ops = new ArrayList<>();
        Page(String name, int width, int height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }
    }

    /** Adobe AFM advance widths (1/1000 em) for printable ASCII 32..126. */
    static class Metrics {
        static final int[] REGULAR = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
        };
        static final int[] BOLD = {
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
            975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
            333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
            611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584
        };

        static float width(String text, boolean bold) {
            int[] table = bold ? BOLD : REGULAR;
            float total = 0f;
            for (char c : text.toCharArray()) {
                if (c >= 32 && c <= 126) total += table[c - 32];
                else if (c == '\u2014') total += 1000;
                else if (c == '\u00B7' || c == '\u2022') total += bold ? 278 : 278;
                else total += 556;
            }
            return total;
        }
    }
}
