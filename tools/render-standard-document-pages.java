import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Paints layout dumps from StandardDocumentTemplateTest into PNGs.
 * Android unit tests cannot see java.awt, so this runs on the full JDK.
 */
class render_standard_document_pages {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("usage: dump.txt logo.b64 out-dir");
        }
        File dump = new File(args[0]);
        File logoFile = new File(args[1]);
        File outDir = new File(args[2]);
        outDir.mkdirs();
        BufferedImage logo = null;
        if (logoFile.exists()) {
            byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(logoFile.toPath()).trim());
            logo = ImageIO.read(new ByteArrayInputStream(bytes));
        }
        List<Page> pages = parse(dump);
        List<BufferedImage> images = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (Page page : pages) {
            BufferedImage image = render(page, logo);
            ImageIO.write(image, "png", new File(outDir, page.name + ".png"));
            images.add(image);
            titles.add(page.title);
        }
        ImageIO.write(contact(titles, images), "png", new File(outDir, "all-documents.png"));
    }

    static List<Page> parse(File dump) throws Exception {
        List<Page> pages = new ArrayList<>();
        Page current = null;
        try (BufferedReader reader = Files.newBufferedReader(dump.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PAGE\t")) {
                    String[] parts = line.split("\t");
                    current = new Page(parts[1], unescape(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                    pages.add(current);
                } else if (current != null && !line.isBlank()) {
                    current.ops.add(line);
                }
            }
        }
        return pages;
    }

    static BufferedImage render(Page page, BufferedImage logo) {
        BufferedImage image = new BufferedImage(page.width, page.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, page.width, page.height);
        for (String raw : page.ops) {
            String[] p = raw.split("\t", -1);
            switch (p[0]) {
                case "text" -> {
                    boolean bold = "1".equals(p[5]);
                    boolean italic = "1".equals(p[6]);
                    int style = italic ? Font.ITALIC : bold ? Font.BOLD : Font.PLAIN;
                    g.setFont(new Font(Font.SANS_SERIF, style, Math.max(6, (int) Float.parseFloat(p[4]))));
                    g.setColor(color(p[8]));
                    String text = unescape(p[1]);
                    int width = g.getFontMetrics().stringWidth(text);
                    float x = Float.parseFloat(p[2]);
                    if ("center".equals(p[7])) x -= width / 2f;
                    else if ("right".equals(p[7])) x -= width;
                    g.drawString(text, x, Float.parseFloat(p[3]));
                }
                case "line" -> {
                    g.setColor(color(p[6]));
                    g.setStroke(new BasicStroke(Math.max(0.6f, Float.parseFloat(p[5]))));
                    g.drawLine((int) Float.parseFloat(p[1]), (int) Float.parseFloat(p[2]), (int) Float.parseFloat(p[3]), (int) Float.parseFloat(p[4]));
                }
                case "rect" -> {
                    g.setColor(color(p[5]));
                    int x = (int) Float.parseFloat(p[1]);
                    int y = (int) Float.parseFloat(p[2]);
                    g.drawRect(x, y, (int) Float.parseFloat(p[3]) - x, (int) Float.parseFloat(p[4]) - y);
                }
                case "fillrect" -> {
                    g.setColor(color(p[5]));
                    int x = (int) Float.parseFloat(p[1]);
                    int y = (int) Float.parseFloat(p[2]);
                    g.fillRect(x, y, (int) Float.parseFloat(p[3]) - x, (int) Float.parseFloat(p[4]) - y);
                }
                case "logo" -> {
                    int x = (int) Float.parseFloat(p[1]);
                    int y = (int) Float.parseFloat(p[2]);
                    int size = Math.max(1, (int) Float.parseFloat(p[3]));
                    if (logo != null) g.drawImage(logo, x, y, size, size, null);
                    else {
                        g.setColor(new Color(0x3A, 0x3A, 0x3A));
                        g.drawOval(x, y, size, size);
                    }
                }
                default -> {
                }
            }
        }
        g.dispose();
        return image;
    }

    static BufferedImage contact(List<String> titles, List<BufferedImage> images) {
        int columns = 3;
        int cellW = 320;
        int cellH = 440;
        int rows = (images.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0xF4, 0xF4, 0xF4));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int i = 0; i < images.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int x = col * cellW;
            int y = row * cellH;
            g.setColor(new Color(0x14, 0x14, 0x16));
            g.drawString(titles.get(i), x + 12, y + 22);
            g.drawImage(images.get(i), x + 12, y + 30, cellW - 24, cellH - 40, null);
        }
        g.dispose();
        return sheet;
    }

    static Color color(String token) {
        return switch (token) {
            case "muted", "rule" -> new Color(0x3A, 0x3A, 0x3A);
            case "light" -> new Color(0xBE, 0xBE, 0xBE);
            default -> new Color(0x14, 0x14, 0x16);
        };
    }

    static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    static class Page {
        final String name;
        final String title;
        final int width;
        final int height;
        final List<String> ops = new ArrayList<>();
        Page(String name, String title, int width, int height) {
            this.name = name;
            this.title = title;
            this.width = width;
            this.height = height;
        }
    }
}
