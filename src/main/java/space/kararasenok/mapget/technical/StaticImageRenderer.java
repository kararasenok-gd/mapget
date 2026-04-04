package space.kararasenok.mapget.technical;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.*;
import java.awt.image.BufferedImage;

public class StaticImageRenderer extends MapRenderer {
    private final byte[] buffer;

    public StaticImageRenderer(BufferedImage image) {
        super(false);
        BufferedImage resized = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image, 0, 0, 128, 128, null);
        g.dispose();

        this.buffer = mapImageToBytes(resized);
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, buffer[y * 128 + x]);
            }
        }
    }

    private byte[] mapImageToBytes(BufferedImage image) {
        byte[] result = new byte[128 * 128];

        RGBColor[][] pixels = new RGBColor[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                pixels[y][x] = new RGBColor(new Color(image.getRGB(x, y), true));
            }
        }

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                RGBColor oldColor = pixels[y][x];
                byte mcByte = MapPalette.matchColor(oldColor.toColor());
                result[y * 128 + x] = mcByte;

                Color mcColor = MapPalette.getColor(mcByte);
                int errR = oldColor.r - mcColor.getRed();
                int errG = oldColor.g - mcColor.getGreen();
                int errB = oldColor.b - mcColor.getBlue();

                distributeError(pixels, x + 1, y, errR, errG, errB, 7.0 / 16.0);
                distributeError(pixels, x - 1, y + 1, errR, errG, errB, 3.0 / 16.0);
                distributeError(pixels, x, y + 1, errR, errG, errB, 5.0 / 16.0);
                distributeError(pixels, x + 1, y + 1, errR, errG, errB, 1.0 / 16.0);
            }
        }
        return result;
    }

    private void distributeError(RGBColor[][] pixels, int x, int y, int errR, int errG, int errB, double factor) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            pixels[y][x].r += (int) (errR * factor);
            pixels[y][x].g += (int) (errG * factor);
            pixels[y][x].b += (int) (errB * factor);
        }
    }

    private static class RGBColor {
        int r, g, b;
        RGBColor(Color c) { this.r = c.getRed(); this.g = c.getGreen(); this.b = c.getBlue(); }
        Color toColor() { return new Color(clamp(r), clamp(g), clamp(b)); }
        private int clamp(int val) { return Math.max(0, Math.min(255, val)); }
    }
}