import java.awt.AlphaComposite;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 * Nap anh logo truong VKU.
 *
 * <p>Anh nam o {@code assets/vku-logo.png}. De {@code javac -d build src/*.java} + {@code java -cp build}
 * chay duoc ngay, {@code test/run.sh} copy thu muc {@code assets/} vao {@code build/} de anh co
 * trong classpath. Neu chay tay khong co buoc copy, loader doi quet file he thong.
 *
 * <p>Khong tim thay anh thi {@link #master()} tra {@code null} - khong nem loi, de giao dien van
 * khoi dong. Chu {@link #warning()} dung noi canh bao cho nguoi dung biet thieu buoc copy.
 */
public final class LogoAssets {

    /** Duong dan trong classpath. */
    public static final String RESOURCE = "/assets/vku-logo.png";

    /** Duong dan tuong doi, dung cho README va thong bao. */
    public static final String FILE = "assets/vku-logo.png";

    private static final String WARNING =
            "⚠ thiếu " + FILE + " — chạy ./test/run.sh hoặc cp -r assets build/";

    private static BufferedImage master;
    private static boolean loaded;

    private LogoAssets() {
    }

    /**
     * Anh goc, doc mot lan roi giu lai. Tra {@code null} neu khong tim thay.
     */
    public static synchronized BufferedImage master() {
        if (loaded) {
            return master;
        }
        loaded = true;
        master = readFromClasspath();
        if (master == null) {
            master = readFromDisk();
        }
        if (master != null && master.getColorModel().hasAlpha() == false) {
            BufferedImage argb = new BufferedImage(master.getWidth(), master.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            var g = argb.createGraphics();
            g.drawImage(master, 0, 0, null);
            g.dispose();
            master = argb;
        }
        return master;
    }

    /**
     * Thanh bao canh bao, hoac {@code null} khi anh da co.
     */
    public static String warning() {
        return master() == null ? WARNING : null;
    }

    /**
     * Ban logo co chieu cao {@code height}, giu nguyen ti le.
     */
    public static Image scaledToHeight(int height) {
        BufferedImage src = master();
        if (src == null || height <= 0) {
            return null;
        }
        double ratio = src.getWidth() / (double) src.getHeight();
        int w = Math.max(1, (int) Math.round(height * ratio));
        return src.getScaledInstance(w, height, Image.SCALE_SMOOTH);
    }

    /**
     * Icon vuong {@code size}, logo chua giua tren nen trong suot.
     *
     * <p>Logo rong hon cao, nen dung chieu rong lam chuan; canh con lai de trong suot
     * de taskbar khong nen dung o goc.
     */
    public static Image windowIcon(int size) {
        BufferedImage src = master();
        if (src == null || size <= 0) {
            return null;
        }
        double ratio = src.getWidth() / (double) src.getHeight();
        int w = size;
        int h = Math.max(1, (int) Math.round(size / ratio));
        if (h > size) {
            h = size;
            w = Math.max(1, (int) Math.round(size * ratio));
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, (size - w) / 2, (size - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage readFromClasspath() {
        try (InputStream in = LogoAssets.class.getResourceAsStream(RESOURCE)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage readFromDisk() {
        for (File dir : candidateDirs()) {
            if (dir == null) {
                continue;
            }
            File f = new File(dir, FILE);
            if (f.isFile()) {
                try {
                    return ImageIO.read(f);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Thu muc chay chuong trinh, thu muc cha cua no, va thu muc hien tai.
     */
    private static File[] candidateDirs() {
        return new File[] {
                codeSourceDir(),
                parentOf(codeSourceDir()),
                new File(System.getProperty("user.dir", ".")),
                new File(System.getProperty("user.dir", ".")).getParentFile(),
        };
    }

    private static File codeSourceDir() {
        try {
            URL url = LogoAssets.class.getProtectionDomain().getCodeSource().getLocation();
            return url == null ? null : new File(url.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static File parentOf(File dir) {
        return dir == null ? null : dir.getParentFile();
    }
}