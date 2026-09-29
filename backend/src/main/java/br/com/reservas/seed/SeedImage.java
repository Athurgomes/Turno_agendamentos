package br.com.reservas.seed;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Seed local (FD-1, CLAUDE.md regra 2 do briefing): gera uma foto ficticia
 * (PNG com cor solida e um rotulo) em memoria, sem baixar nada da internet e
 * sem gravar nada no disco do container (so os bytes vao para o
 * {@code FileStorage}, como qualquer upload real).
 */
final class SeedImage {

    private SeedImage() {
    }

    static byte[] png(String label, Color color) {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 32));
            g.drawString(label, 24, image.getHeight() / 2);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
