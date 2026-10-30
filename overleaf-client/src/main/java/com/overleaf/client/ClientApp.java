package com.overleaf.client;

import com.overleaf.client.gui.OverleafDesktopApp;
import com.overleaf.client.gui.OverleafFxApp;
import javafx.application.Application;

/**
 * =========================================================================
 * M1: ĐIỂM KHỞI CHẠY CHÍNH CỦA OVERLEAF DESKTOP CLIENT
 * =========================================================================
 * 
 * - Mặc định: Khởi chạy giao diện JavaFX tối ưu hiện đại (OverleafFxApp).
 * - Tương thích ngược: Truyền tham số "--swing" nếu muốn mở bản Swing cũ.
 */
public class ClientApp {
    public static void main(String[] args) {
        if (args != null && args.length > 0 && "--swing".equalsIgnoreCase(args[0])) {
            System.out.println("[INFO] Khoi chay che do giao dien Java Swing cu...");
            OverleafDesktopApp.main(args);
        } else {
            System.out.println("[INFO] Khoi chay giao dien Overleaf JavaFX hien dai...");
            Application.launch(OverleafFxApp.class, args);
        }
    }
}
