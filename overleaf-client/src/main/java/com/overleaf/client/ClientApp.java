package com.overleaf.client;

import com.overleaf.client.gui.OverleafDesktopApp;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * M1: Điểm khởi chạy chính của ứng dụng Overleaf Desktop Client.
 */
public class ClientApp {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            OverleafDesktopApp app = new OverleafDesktopApp();
            app.setVisible(true);
        });
    }
}
