package com.jeshwin.pulsegrid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

final class PulseGridPanel extends JPanel {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 720;
    private static final int HUD_HEIGHT = 82;
    private static final int GRID_COLS = 10;
    private static final int GRID_ROWS = 7;
    private static final int CELL_SIZE = 82;
    private static final int GRID_X = 70;
    private static final int GRID_Y = 120;
    private static final int NODE_RADIUS = 22;
    private static final int PLAYER_RADIUS = 16;
    private static final int MAX_HEALTH = 100;
    private static final int TARGET_SCORE = 4000;
    private static final int PULSE_COOLDOWN = 22;

    private final Random random = new Random();
    private final Timer timer;
    private final Node[][] nodes = new Node[GRID_ROWS][GRID_COLS];
    private final List<PulseRing> rings = new ArrayList<>();
    private final List<SignalShard> shards = new ArrayList<>();
    private final Player player = new Player();

    private boolean upPressed;
    private boolean downPressed;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean pulseRequested;
    private boolean playing = true;

    private int score;
    private int bestCombo;
    private int combo;
    private int health = MAX_HEALTH;
    private int level = 1;
    private int unstableCount;
    private int frameCount;
    private int pulseCooldown;
    private String banner = "Stabilize the grid before the cascade wins.";
    private int bannerTicks = 240;

    PulseGridPanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        setBackground(new Color(5, 10, 24));
        setupKeyBindings();
        initializeGrid();
        timer = new Timer(16, this::tick);
        timer.start();
    }

    private void initializeGrid() {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int x = GRID_X + col * CELL_SIZE;
                int y = GRID_Y + row * CELL_SIZE;
                nodes[row][col] = new Node(x, y);
            }
        }
        for (int i = 0; i < 6; i++) {
            destabilizeRandomNode();
        }
        player.x = GRID_X + (GRID_COLS / 2) * CELL_SIZE;
        player.y = GRID_Y + (GRID_ROWS / 2) * CELL_SIZE;
    }

    private void setupKeyBindings() {
        bindKey("pressed W", () -> upPressed = true);
        bindKey("released W", () -> upPressed = false);
        bindKey("pressed S", () -> downPressed = true);
        bindKey("released S", () -> downPressed = false);
        bindKey("pressed A", () -> leftPressed = true);
        bindKey("released A", () -> leftPressed = false);
        bindKey("pressed D", () -> rightPressed = true);
        bindKey("released D", () -> rightPressed = false);
        bindKey("pressed UP", () -> upPressed = true);
        bindKey("released UP", () -> upPressed = false);
        bindKey("pressed DOWN", () -> downPressed = true);
        bindKey("released DOWN", () -> downPressed = false);
        bindKey("pressed LEFT", () -> leftPressed = true);
        bindKey("released LEFT", () -> leftPressed = false);
        bindKey("pressed RIGHT", () -> rightPressed = true);
        bindKey("released RIGHT", () -> rightPressed = false);
        bindKey("pressed SPACE", () -> pulseRequested = true);
        bindKey("pressed ENTER", () -> {
            if (!playing) {
                restartGame();
            }
        });
    }

    private void bindKey(String keyStroke, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void tick(ActionEvent event) {
        if (playing) {
            frameCount++;
            updatePlayer();
            updateNodes();
            updateRings();
            updateShards();
            if (pulseRequested && pulseCooldown == 0) {
                emitPulse();
            }
            pulseRequested = false;
            if (pulseCooldown > 0) {
                pulseCooldown--;
            }
            maybeEscalateThreat();
            updateBanner();
            if (health <= 0) {
                playing = false;
                banner = "Grid collapse detected. Press Enter to reboot.";
                bannerTicks = Integer.MAX_VALUE;
            }
            if (score >= TARGET_SCORE) {
                playing = false;
                banner = "Protocol secured. Press Enter to run again.";
                bannerTicks = Integer.MAX_VALUE;
            }
        }
        repaint();
    }

    private void updatePlayer() {
        double speed = 4.3;
        if (upPressed) {
            player.y -= speed;
        }
        if (downPressed) {
            player.y += speed;
        }
        if (leftPressed) {
            player.x -= speed;
        }
        if (rightPressed) {
            player.x += speed;
        }
        double minX = GRID_X - 20;
        double maxX = GRID_X + (GRID_COLS - 1) * CELL_SIZE + 20;
        double minY = GRID_Y - 20;
        double maxY = GRID_Y + (GRID_ROWS - 1) * CELL_SIZE + 20;
        player.x = clamp(player.x, minX, maxX);
        player.y = clamp(player.y, minY, maxY);
    }

    private void updateNodes() {
        unstableCount = 0;
        for (Node[] row : nodes) {
            for (Node node : row) {
                if (node.stability < 100) {
                    node.stability = Math.max(0, node.stability - node.decayRate);
                    unstableCount++;
                    if (distance(player.x, player.y, node.x, node.y) < 32) {
                        node.stability = Math.min(100, node.stability + 1.6);
                        score += 1;
                    }
                }
                if (node.stability <= 0) {
                    health -= 1;
                    node.stability = 18 + random.nextInt(18);
                    node.decayRate = 0.32 + random.nextDouble() * 0.45 + (level * 0.03);
                    combo = 0;
                    banner = "A breach slipped through. Recover the chain.";
                    bannerTicks = 90;
                }
            }
        }
    }

    private void updateRings() {
        Iterator<PulseRing> iterator = rings.iterator();
        while (iterator.hasNext()) {
            PulseRing ring = iterator.next();
            ring.radius += 11;
            ring.life--;
            if (ring.life <= 0) {
                iterator.remove();
            }
        }
    }

    private void updateShards() {
        Iterator<SignalShard> iterator = shards.iterator();
        while (iterator.hasNext()) {
            SignalShard shard = iterator.next();
            shard.x += shard.vx;
            shard.y += shard.vy;
            shard.life--;
            if (shard.life <= 0) {
                iterator.remove();
            }
        }
    }

    private void emitPulse() {
        pulseCooldown = PULSE_COOLDOWN;
        rings.add(new PulseRing(player.x, player.y));
        int stabilizedThisPulse = 0;
        for (Node[] row : nodes) {
            for (Node node : row) {
                double distance = distance(player.x, player.y, node.x, node.y);
                if (distance < 145) {
                    if (node.stability < 100) {
                        node.stability = Math.min(100, node.stability + 45);
                        stabilizedThisPulse++;
                        spawnShards(node.x, node.y, new Color(86, 223, 209));
                    } else {
                        spawnShards(node.x, node.y, new Color(105, 121, 255));
                    }
                }
            }
        }
        if (stabilizedThisPulse > 0) {
            combo += stabilizedThisPulse;
            bestCombo = Math.max(bestCombo, combo);
            score += stabilizedThisPulse * (25 + combo * 3);
            banner = "Chain stabilized x" + combo;
            bannerTicks = 55;
        } else {
            combo = 0;
            banner = "Pulse wasted. Move closer to the failing nodes.";
            bannerTicks = 45;
        }
    }

    private void maybeEscalateThreat() {
        if (frameCount % Math.max(70, 165 - level * 10) == 0) {
            destabilizeRandomNode();
        }
        if (frameCount % 900 == 0) {
            level++;
            banner = "Threat level increased to " + level + ".";
            bannerTicks = 110;
        }
    }

    private void destabilizeRandomNode() {
        Node node = nodes[random.nextInt(GRID_ROWS)][random.nextInt(GRID_COLS)];
        node.stability = Math.min(node.stability, 35 + random.nextInt(28));
        node.decayRate = 0.34 + random.nextDouble() * 0.45 + (level * 0.03);
    }

    private void updateBanner() {
        if (bannerTicks > 0 && bannerTicks != Integer.MAX_VALUE) {
            bannerTicks--;
        } else if (bannerTicks == 0) {
            banner = "Stay mobile, keep the combo alive, and secure 4000 points.";
        }
    }

    private void restartGame() {
        score = 0;
        bestCombo = 0;
        combo = 0;
        health = MAX_HEALTH;
        level = 1;
        unstableCount = 0;
        frameCount = 0;
        pulseCooldown = 0;
        banner = "Stabilize the grid before the cascade wins.";
        bannerTicks = 240;
        rings.clear();
        shards.clear();
        playing = true;
        for (Node[] row : nodes) {
            for (Node node : row) {
                node.stability = 100;
                node.decayRate = 0.35;
            }
        }
        for (int i = 0; i < 6; i++) {
            destabilizeRandomNode();
        }
        player.x = GRID_X + (GRID_COLS / 2) * CELL_SIZE;
        player.y = GRID_Y + (GRID_ROWS / 2) * CELL_SIZE;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(6, 10, 24), WIDTH, HEIGHT, new Color(17, 24, 50)));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        drawGrid(g2);
        drawRings(g2);
        drawShards(g2);
        drawPlayer(g2);
        drawHud(g2);
        drawBanner(g2);
        if (!playing) {
            drawEndOverlay(g2);
        }

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(45, 64, 114, 120));
        for (int col = 0; col < GRID_COLS; col++) {
            int x = GRID_X + col * CELL_SIZE;
            g2.drawLine(x, GRID_Y, x, GRID_Y + (GRID_ROWS - 1) * CELL_SIZE);
        }
        for (int row = 0; row < GRID_ROWS; row++) {
            int y = GRID_Y + row * CELL_SIZE;
            g2.drawLine(GRID_X, y, GRID_X + (GRID_COLS - 1) * CELL_SIZE, y);
        }
        for (Node[] row : nodes) {
            for (Node node : row) {
                float ratio = (float) node.stability / 100.0f;
                Color glow = blend(new Color(255, 84, 84), new Color(72, 231, 192), ratio);
                g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 55));
                g2.fillOval((int) node.x - NODE_RADIUS - 10, (int) node.y - NODE_RADIUS - 10, 64, 64);
                g2.setColor(glow);
                g2.fillOval((int) node.x - NODE_RADIUS, (int) node.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
                g2.setColor(new Color(5, 10, 24));
                g2.fillOval((int) node.x - 9, (int) node.y - 9, 18, 18);
            }
        }
    }

    private void drawRings(Graphics2D g2) {
        g2.setStroke(new BasicStroke(3f));
        for (PulseRing ring : rings) {
            int alpha = Math.max(0, ring.life * 9);
            g2.setColor(new Color(86, 223, 209, alpha));
            int diameter = ring.radius * 2;
            g2.drawOval((int) ring.x - ring.radius, (int) ring.y - ring.radius, diameter, diameter);
        }
    }

    private void drawShards(Graphics2D g2) {
        for (SignalShard shard : shards) {
            int alpha = Math.max(0, shard.life * 10);
            g2.setColor(new Color(shard.color.getRed(), shard.color.getGreen(), shard.color.getBlue(), alpha));
            g2.fillOval((int) shard.x, (int) shard.y, 6, 6);
        }
    }

    private void drawPlayer(Graphics2D g2) {
        g2.setColor(new Color(96, 179, 255, 45));
        g2.fillOval((int) player.x - 30, (int) player.y - 30, 60, 60);
        g2.setColor(new Color(217, 248, 255));
        g2.fillOval((int) player.x - PLAYER_RADIUS, (int) player.y - PLAYER_RADIUS, PLAYER_RADIUS * 2, PLAYER_RADIUS * 2);
        g2.setColor(new Color(5, 10, 24));
        g2.fillOval((int) player.x - 5, (int) player.y - 5, 10, 10);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(7, 14, 31, 215));
        g2.fillRoundRect(24, 18, WIDTH - 48, HUD_HEIGHT, 24, 24);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 24));
        g2.drawString("Pulse Grid Protocol", 42, 50);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g2.drawString("Score: " + score, 44, 80);
        g2.drawString("Combo: " + combo, 220, 80);
        g2.drawString("Best Chain: " + bestCombo, 360, 80);
        g2.drawString("Threat: " + level, 575, 80);
        g2.drawString("Unstable Nodes: " + unstableCount, 700, 80);

        g2.setColor(new Color(37, 54, 92));
        g2.fillRoundRect(44, 92, 250, 18, 12, 12);
        g2.setColor(new Color(72, 231, 192));
        g2.fillRoundRect(44, 92, (int) (250 * (health / (double) MAX_HEALTH)), 18, 12, 12);
        g2.setColor(Color.WHITE);
        g2.drawString("Grid Integrity", 304, 107);

        g2.setColor(new Color(37, 54, 92));
        g2.fillRoundRect(484, 92, 180, 18, 12, 12);
        int cooldownWidth = 180 - (int) ((pulseCooldown / (double) PULSE_COOLDOWN) * 180);
        g2.setColor(new Color(105, 121, 255));
        g2.fillRoundRect(484, 92, cooldownWidth, 18, 12, 12);
        g2.setColor(Color.WHITE);
        g2.drawString("Pulse Charge", 674, 107);
    }

    private void drawBanner(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 215));
        g2.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics metrics = g2.getFontMetrics();
        int textWidth = metrics.stringWidth(banner);
        g2.drawString(banner, (WIDTH - textWidth) / 2, HEIGHT - 34);
    }

    private void drawEndOverlay(Graphics2D g2) {
        g2.setColor(new Color(4, 8, 18, 180));
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 40));
        String title = score >= TARGET_SCORE ? "Protocol Secured" : "Cascade Failure";
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.drawString(title, (WIDTH - titleMetrics.stringWidth(title)) / 2, HEIGHT / 2 - 40);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 22));
        String stats = "Final Score " + score + "   |   Best Chain " + bestCombo;
        FontMetrics bodyMetrics = g2.getFontMetrics();
        g2.drawString(stats, (WIDTH - bodyMetrics.stringWidth(stats)) / 2, HEIGHT / 2 + 8);
        String prompt = "Press Enter to restart";
        g2.drawString(prompt, (WIDTH - bodyMetrics.stringWidth(prompt)) / 2, HEIGHT / 2 + 48);
    }

    private void spawnShards(double x, double y, Color color) {
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.8 + random.nextDouble() * 2.6;
            shards.add(new SignalShard(
                x,
                y,
                Math.cos(angle) * speed,
                Math.sin(angle) * speed,
                18 + random.nextInt(12),
                color
            ));
        }
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Color blend(Color low, Color high, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int red = (int) (low.getRed() + (high.getRed() - low.getRed()) * ratio);
        int green = (int) (low.getGreen() + (high.getGreen() - low.getGreen()) * ratio);
        int blue = (int) (low.getBlue() + (high.getBlue() - low.getBlue()) * ratio);
        return new Color(red, green, blue);
    }

    private static final class Node {
        private final double x;
        private final double y;
        private double stability = 100;
        private double decayRate = 0.35;

        private Node(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Player {
        private double x;
        private double y;
    }

    private static final class PulseRing {
        private final double x;
        private final double y;
        private int radius = 8;
        private int life = 15;

        private PulseRing(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class SignalShard {
        private double x;
        private double y;
        private final double vx;
        private final double vy;
        private int life;
        private final Color color;

        private SignalShard(double x, double y, double vx, double vy, int life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
        }
    }
}
