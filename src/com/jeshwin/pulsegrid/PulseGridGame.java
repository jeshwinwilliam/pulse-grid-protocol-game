package com.jeshwin.pulsegrid;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class PulseGridGame {
    private PulseGridGame() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pulse Grid Protocol");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new PulseGridPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
