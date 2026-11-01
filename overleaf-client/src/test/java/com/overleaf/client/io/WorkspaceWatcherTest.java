package com.overleaf.client.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceWatcherTest {

    private WorkspaceWatcher watcher;

    @BeforeEach
    void setUp() {
        // Khởi tạo trước mỗi test (sẽ được cấu hình cụ thể trong từng test method)
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stopWatching();
        }
    }

    @Test
    void testStartWatching_InvalidDirectory() {
        watcher = new WorkspaceWatcher("non_existent_directory_123456");
        TestFileListener listener = new TestFileListener();

        // Thư mục không tồn tại phải quăng ra IOException
        assertThrows(IOException.class, () -> watcher.startWatching(listener));
    }

    @Test
    void testStartWatching_NullListener(@TempDir Path tempDir) {
        watcher = new WorkspaceWatcher(tempDir.toString());

        // Truyền listener null phải quăng ra IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> watcher.startWatching(null));
    }

    @Test
    void testFileChanged_Detection(@TempDir Path tempDir) throws IOException, InterruptedException {
        watcher = new WorkspaceWatcher(tempDir.toString());

        CountDownLatch latch = new CountDownLatch(1);
        TestFileListener listener = new TestFileListener(latch);

        watcher.startWatching(listener);

        // Tạo một file đuôi .tex hợp lệ bên trong thư mục project được theo dõi
        Path texFile = tempDir.resolve("document.tex");
        Files.writeString(texFile, "\\section{Test}");

        // Do có debounce 500ms, ta cần chờ tối đa khoảng 1.5 - 2 giây để sự kiện được bắn ra
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Phải nhận được sự kiện thay đổi file trong thời gian cho phép");

        assertEquals("document.tex", listener.getChangedFileName(), "Tên file thay đổi phải khớp");
    }

    @Test
    void testIgnoredExtensions(@TempDir Path tempDir) throws IOException, InterruptedException {
        watcher = new WorkspaceWatcher(tempDir.toString());

        // CountDownLatch để kiểm tra xem file KHÔNG PHẢI đuôi LaTeX có bị gọi nhầm không
        CountDownLatch latch = new CountDownLatch(1);
        TestFileListener listener = new TestFileListener(latch);

        watcher.startWatching(listener);

        // Tạo file có đuôi không nằm trong danh sách theo dõi (.txt thay vì .tex, .bib, .sty, .cls)
        Path txtFile = tempDir.resolve("notes.txt");
        Files.writeString(txtFile, "Some notes");

        // Chờ 800ms (lố hơn thời gian debounce một chút)
        boolean eventFired = latch.await(800, TimeUnit.MILLISECONDS);

        assertFalse(eventFired, "Không được gọi callback cho các định dạng file không được theo dõi (.txt)");
    }

    @Test
    void testFileDeleted_Detection(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Tạo sẵn file .tex trước khi start watching
        Path texFile = tempDir.resolve("report.tex");
        Files.writeString(texFile, "Hello overleaf");

        watcher = new WorkspaceWatcher(tempDir.toString());

        CountDownLatch latch = new CountDownLatch(1);
        TestFileListener listener = new TestFileListener(latch);

        watcher.startWatching(listener);

        // Xóa file đi
        Files.delete(texFile);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Phải nhận được sự kiện xóa file");

        assertEquals("report.tex", listener.getDeletedFileName(), "Tên file bị xóa phải khớp");
    }

    @Test
    void testMultipleRapidChanges_Debounce(@TempDir Path tempDir) throws IOException, InterruptedException {
        watcher = new WorkspaceWatcher(tempDir.toString());

        AtomicInteger changeEventCount = new AtomicInteger(0);
        WorkspaceWatcher.FileChangeListener listener = new WorkspaceWatcher.FileChangeListener() {
            @Override
            public void onFileChanged(String fileName) {
                changeEventCount.incrementAndGet();
            }
        };

        watcher.startWatching(listener);

        Path texFile = tempDir.resolve("rapid.tex");

        // Ghi file liên tục nhiều lần trong khoảng thời gian ngắn (nhanh hơn 500ms debounce)
        for (int i = 0; i < 5; i++) {
            Files.writeString(texFile, "Content version " + i);
            Thread.sleep(50); // Sửa liên tục mỗi 50ms
        }

        // Chờ hết thời gian debounce (500ms) + dư dã thời gian xử lý
        Thread.sleep(900);

        // Nhờ có debounce riêng cho file, sự kiện chỉ nên được gom lại và bắn ra duy nhất 1 LẦN cuối cùng
        assertEquals(1, changeEventCount.get(), "Cơ chế debounce phải gom nhiều lần sửa liên tiếp thành đúng 1 lần gọi callback");
    }


    // =========================================================================
    // HELPER LISTENER CLASS (Thay thế Mockito)
    // =========================================================================

    private static class TestFileListener implements WorkspaceWatcher.FileChangeListener {
        private final CountDownLatch latch;
        private final AtomicReference<String> changedFileName = new AtomicReference<>();
        private final AtomicReference<String> deletedFileName = new AtomicReference<>();

        public TestFileListener() {
            this.latch = null;
        }

        public TestFileListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onFileChanged(String fileName) {
            changedFileName.set(fileName);
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onFileDeleted(String fileName) {
            deletedFileName.set(fileName);
            if (latch != null) {
                latch.countDown();
            }
        }

        public String getChangedFileName() {
            return changedFileName.get();
        }

        public String getDeletedFileName() {
            return deletedFileName.get();
        }
    }
    @Test
    void tc02_testStopWatching_StopsReceivingEvents(@TempDir Path tempDir) throws IOException, InterruptedException {
        watcher = new WorkspaceWatcher(tempDir.toString());

        AtomicInteger eventCount = new AtomicInteger(0);
        WorkspaceWatcher.FileChangeListener listener = new WorkspaceWatcher.FileChangeListener() {
            @Override
            public void onFileChanged(String fileName) {
                eventCount.incrementAndGet();
            }
        };

        watcher.startWatching(listener);

        // Dừng watcher ngay lập tức
        watcher.stopWatching();

        // Tạo file sau khi watcher đã dừng
        Path texFile = tempDir.resolve("ignored_after_stop.tex");
        Files.writeString(texFile, "Should not trigger");

        // Chờ một khoảng thời gian để chắc chắn sự kiện không được bắn
        Thread.sleep(800);

        assertEquals(0, eventCount.get(), "Watcher đã dừng thì không được nhận bất kỳ sự kiện thay đổi nào nữa");
    }
}