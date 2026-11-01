package com.overleaf.client.ui;

import com.overleaf.client.compiler.PdfSnapshot;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.*;

/** Continuous real-PDF preview with bounded, lazy page rendering and internal PDF links. */
public final class PdfPreviewPane extends VBox implements AutoCloseable {
    private static final double GAP = 16, PADDING = 16, PIXEL_BUDGET = 24_000_000;
    private final Label status = new Label("Chưa có PDF — bấm Recompile để biên dịch.");
    private final Label pageLabel = new Label("0 / 0"), zoomLabel = new Label("100%");
    private final Button previous = new Button("‹"), next = new Button("›"), back = new Button("↶"), export = new Button("Export PDF…");
    private final VBox sheets = new VBox(GAP);
    private final List<PageView> views = new ArrayList<>();
    private final Set<Integer> wanted = new LinkedHashSet<>(), failedPages = new HashSet<>();
    private final Deque<Location> history = new ArrayDeque<>();
    private final ScrollPane scroll;
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "pdf-render"); thread.setDaemon(true); return thread;
    });
    private PdfSnapshot snapshot;
    private int page;
    private double zoom = 1, contentHeight;
    private boolean fit = true, current, disposed, busy, layingOut, resizePending;
    private long generation;
    private record Location(int page, double y) { }

    private final class PageView extends StackPane {
        final int index;
        final ImageView image = new ImageView();
        final Pane links = new Pane();
        final Label placeholder;
        double top, width, height;
        PageView(int index) {
            this.index = index; setId("pdf-sheet-" + index);
            getStyleClass().add("pdf-continuous-sheet"); setAlignment(Pos.TOP_LEFT);
            image.setSmooth(true); image.setPreserveRatio(false); image.setMouseTransparent(true);
            placeholder = new Label("PDF · " + (index + 1)); placeholder.setMouseTransparent(true);
            StackPane.setAlignment(placeholder, Pos.CENTER); links.setPickOnBounds(false);
            getChildren().addAll(placeholder, image, links);
        }
        void resizePage(double w, double h, double y) {
            width = w; height = h; top = y;
            setMinSize(w, h); setPrefSize(w, h); setMaxSize(w, h);
            image.setFitWidth(w); image.setFitHeight(h);
            links.setMinSize(w, h); links.setPrefSize(w, h); links.setMaxSize(w, h); layoutLinks();
        }
        void layoutLinks() {
            if (links.getChildren().isEmpty()) return;
            var metadata = snapshot.pageInfo(index).links();
            for (int i = 0; i < metadata.size(); i++) {
                var link = metadata.get(i); var button = (Hyperlink)links.getChildren().get(i);
                button.setLayoutX(link.x() * width); button.setLayoutY(link.y() * height);
                button.setMinSize(link.width() * width, link.height() * height);
                button.setPrefSize(link.width() * width, link.height() * height);
                button.setMaxSize(link.width() * width, link.height() * height);
            }
        }
        void addLinks() {
            if (!links.getChildren().isEmpty()) return;
            int id = 0;
            for (var link : snapshot.pageInfo(index).links()) {
                Hyperlink button = new Hyperlink(); button.setId("pdf-link-" + index + "-" + id++);
                button.getStyleClass().add("pdf-internal-link"); button.setCursor(Cursor.HAND);
                button.setAccessibleText("Đến trang / Go to page " + (link.destination().page() + 1));
                button.setTooltip(new Tooltip("Đến trang / Go to page " + (link.destination().page() + 1)));
                button.setOnAction(e -> {
                    history.push(position()); if (history.size() > 100) history.removeLast();
                    navigate(link.destination().page(), link.destination().y());
                });
                links.getChildren().add(button);
            }
            layoutLinks();
        }
        void clearImage() { image.setImage(null); links.getChildren().clear(); placeholder.setVisible(true); }
    }

    public PdfPreviewPane(Runnable exportAction) {
        setMinWidth(260); getStyleClass().add("preview-pane");
        sheets.setAlignment(Pos.TOP_CENTER); sheets.setPadding(new Insets(PADDING));
        scroll = new ScrollPane(sheets); scroll.setPannable(true);
        scroll.setId("pdf-continuous-scroll"); scroll.getStyleClass().add("preview-scroll"); VBox.setVgrow(scroll, Priority.ALWAYS);
        status.setId("pdf-preview-status"); status.setWrapText(true); status.setMaxWidth(Double.MAX_VALUE); statusTone(false, false);
        Button smaller = new Button("−"), larger = new Button("+"), fitButton = new Button("Fit");
        smaller.setId("pdf-zoom-out"); larger.setId("pdf-zoom-in"); fitButton.setId("pdf-fit");
        for (Button button : new Button[]{previous, next, back, smaller, larger, fitButton}) button.getStyleClass().add("btn-ghost");
        export.getStyleClass().add("btn-secondary");
        smaller.setOnAction(e -> setZoom(zoom / 1.2)); larger.setOnAction(e -> setZoom(zoom * 1.2));
        fitButton.setOnAction(e -> { fit = true; reflow(true); });
        previous.setOnAction(e -> showPage(page - 1)); next.setOnAction(e -> showPage(page + 1));
        back.setOnAction(e -> { if (!history.isEmpty()) { Location at = history.pop(); navigate(at.page(), at.y()); } });
        back.setId("pdf-back"); back.setTooltip(new Tooltip("Quay lại vị trí trước / Back to previous location"));
        previous.setId("pdf-previous"); next.setId("pdf-next"); export.setId("export-pdf"); export.setOnAction(e -> exportAction.run());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(6, new Label("PDF"), back, previous, pageLabel, next, spacer, smaller, zoomLabel, larger, fitButton);
        toolbar.setAlignment(Pos.CENTER_LEFT); toolbar.setStyle("-fx-padding: 8;");
        HBox actions = new HBox(export); actions.setStyle("-fx-padding: 0 8 8 8;"); getChildren().addAll(toolbar, actions, status, scroll);
        scroll.vvalueProperty().addListener((o, a, b) -> { if (!layingOut) updateVisible(); });
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (Math.abs(a.getWidth() - b.getWidth()) < 0.5) { if (!layingOut) updateVisible(); return; }
            if (resizePending) return; resizePending = true;
            Platform.runLater(() -> { resizePending = false; if (!disposed) reflow(true); });
        });
        updateControls();
    }
    public void reset() {
        generation++; wanted.clear(); failedPages.clear(); history.clear(); snapshot = null; current = false; page = 0;
        views.clear(); sheets.getChildren().clear(); status.setText("Chưa có PDF đã xác nhận cho dự án này — bấm Recompile."); statusTone(false, false); updateControls();
    }
    public void markStale(String reason) {
        current = false; status.setText(reason + (snapshot == null ? "" : " Đang hiển thị PDF của lần biên dịch thành công trước.")); statusTone(false, false); updateControls();
    }
    public void compiling() { markStale("Đang biên dịch. Chưa thể xuất PDF của nội dung hiện tại."); }
    public void failed() { markStale("Biên dịch thất bại hoặc bị dừng. Xem Build output; chưa thể xuất PDF."); statusTone(false, true); }
    private void statusTone(boolean success, boolean error) {
        status.setStyle("-fx-padding: 10; -fx-font-size: 13px; -fx-text-fill: " + (success ? "#17633b" : error ? "#9b2323" : "#8a5200")
                + "; -fx-background-color: " + (success ? "#e7f5ec" : error ? "#fdeaea" : "#fff3d6") + ";");
    }
    public void show(PdfSnapshot pdf, boolean matchesInputs) {
        generation++; wanted.clear(); failedPages.clear(); history.clear(); snapshot = pdf; current = matchesInputs; page = Math.min(page, pdf.pages() - 1);
        views.clear(); sheets.getChildren().clear();
        for (int i = 0; i < pdf.pages(); i++) { PageView view = new PageView(i); views.add(view); sheets.getChildren().add(view); }
        status.setText(matchesInputs ? "PDF đã biên dịch thành công — cuộn để xem các trang, bấm liên kết để đến mục."
                : "Nội dung đã thay đổi trong lúc biên dịch. Bấm Recompile trước khi xuất.");
        statusTone(matchesInputs, false); reflow(false); navigate(page, 0);
    }
    public boolean canExport() { return snapshot != null && current; }
    public PdfSnapshot snapshot() { return snapshot; }
    public int pageIndex() { return page; }
    public boolean hasRenderedPage() { return !views.isEmpty() && views.get(page).image.getImage() != null; }
    private void setZoom(double value) { fit = false; zoom = Math.clamp(value, 0.25, 3); reflow(true); }
    private void showPage(int index) { if (snapshot != null && index >= 0 && index < snapshot.pages()) navigate(index, 0); }
    private double viewportHeight() { return Math.max(1, scroll.getViewportBounds().getHeight()); }
    private double scrollTop() { return scroll.getVvalue() * Math.max(0, contentHeight - viewportHeight()); }
    private Location position() {
        if (views.isEmpty()) return new Location(0, 0);
        double top = scrollTop();
        PageView view = views.get(page);
        for (PageView candidate : views) if (candidate.top + candidate.height >= top) { view = candidate; break; }
        return new Location(view.index, Math.clamp((top - view.top) / Math.max(1, view.height), 0, 1));
    }
    private void navigate(int index, double y) {
        if (snapshot == null || index < 0 || index >= views.size()) return;
        PageView view = views.get(index); double top = view.top + Math.clamp(y, 0, 1) * view.height;
        scroll.setVvalue(Math.clamp(top / Math.max(1, contentHeight - viewportHeight()), 0, 1)); updateVisible(); updateControls();
    }
    private void updateControls() {
        previous.setDisable(snapshot == null || page == 0); next.setDisable(snapshot == null || page >= snapshot.pages() - 1);
        back.setDisable(snapshot == null || history.isEmpty()); pageLabel.setText(snapshot == null ? "0 / 0" : (page + 1) + " / " + snapshot.pages()); export.setDisable(!canExport());
        for (PageView view : views) view.image.setId(view.index == page ? "pdf-page-image" : "pdf-page-image-" + view.index);
    }
    private void reflow(boolean preservePosition) {
        if (snapshot == null || disposed || views.isEmpty() || layingOut) return;
        Location anchor = preservePosition ? position() : new Location(page, 0); layingOut = true;
        try {
            double widest = 1; for (int i = 0; i < snapshot.pages(); i++) widest = Math.max(widest, snapshot.pageInfo(i).width());
            if (fit) zoom = Math.clamp((Math.max(100, scroll.getViewportBounds().getWidth()) - PADDING * 2 - 4) / widest, 0.1, 3);
            double y = PADDING;
            for (PageView view : views) { var info = snapshot.pageInfo(view.index); view.resizePage(info.width() * zoom, info.height() * zoom, y); y += view.height + GAP; }
            contentHeight = y - GAP + PADDING;
            sheets.setMinSize(Math.max(scroll.getViewportBounds().getWidth(), widest * zoom + PADDING * 2), contentHeight);
            sheets.setPrefSize(sheets.getMinWidth(), contentHeight); sheets.setMaxHeight(Region.USE_PREF_SIZE);
            sheets.resize(sheets.getPrefWidth(), contentHeight); sheets.layout(); scroll.layout(); generation++; failedPages.clear();
            for (PageView view : views) view.clearImage(); zoomLabel.setText(Math.round(zoom * 100) + "%");
            PageView target = views.get(anchor.page()); scroll.setVvalue(Math.clamp((target.top + anchor.y() * target.height) / Math.max(1, contentHeight - viewportHeight()), 0, 1));
        } finally { layingOut = false; }
        updateVisible();
    }
    private float renderScale() { return (float)Math.max(1.5, Math.min(3, zoom)); }
    private void updateVisible() {
        if (snapshot == null || disposed || views.isEmpty() || layingOut) return;
        double top = scrollTop(), bottom = top + viewportHeight(), largest = -1; List<Integer> visible = new ArrayList<>();
        for (PageView view : views) {
            double area = Math.max(0, Math.min(bottom, view.top + view.height) - Math.max(top, view.top));
            if (area > 0) { visible.add(view.index); if (area > largest) { largest = area; page = view.index; } }
        }
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>(); candidates.add(page); candidates.addAll(visible);
        if (!visible.isEmpty()) { if (visible.getFirst() > 0) candidates.add(visible.getFirst() - 1); if (visible.getLast() + 1 < views.size()) candidates.add(visible.getLast() + 1); }
        wanted.clear(); double pixels = 0;
        for (int index : candidates) {
            var info = snapshot.pageInfo(index); double cost = Math.min(12_000_000, info.width() * info.height() * renderScale() * renderScale());
            if (wanted.isEmpty() || pixels + cost <= PIXEL_BUDGET) { wanted.add(index); pixels += cost; }
        }
        for (PageView view : views) if (!wanted.contains(view.index)) view.clearImage(); updateControls(); renderNext();
    }
    private void renderNext() {
        if (busy || snapshot == null || disposed) return;
        int index = wanted.stream().filter(i -> views.get(i).image.getImage() == null && !failedPages.contains(i)).findFirst().orElse(-1);
        if (index < 0) return;
        busy = true; long ticket = generation; PdfSnapshot pdf = snapshot; float scale = renderScale();
        Task<PdfSnapshot.PageImage> task = new Task<>() { @Override protected PdfSnapshot.PageImage call() throws Exception { return pdf.renderPage(index, scale); } };
        task.setOnSucceeded(e -> {
            busy = false;
            if (!disposed && ticket == generation && snapshot == pdf && wanted.contains(index)) {
                var pixels = task.getValue().image(); WritableImage result = new WritableImage(pixels.getWidth(), pixels.getHeight());
                int[] rgb = pixels.getRGB(0, 0, pixels.getWidth(), pixels.getHeight(), null, 0, pixels.getWidth());
                result.getPixelWriter().setPixels(0, 0, pixels.getWidth(), pixels.getHeight(), PixelFormat.getIntArgbInstance(), rgb, 0, pixels.getWidth());
                PageView view = views.get(index); view.image.setImage(result); view.placeholder.setVisible(false); view.addLinks();
            }
            renderNext();
        });
        task.setOnFailed(e -> {
            busy = false;
            if (!disposed && ticket == generation && snapshot == pdf) { failedPages.add(index); current = false; status.setText("Không hiển thị được trang " + (index + 1) + ": " + task.getException().getMessage()); statusTone(false, true); updateControls(); }
            renderNext();
        });
        renderer.execute(task);
    }
    @Override public void close() { disposed = true; generation++; wanted.clear(); renderer.shutdownNow(); views.forEach(PageView::clearImage); }
}
