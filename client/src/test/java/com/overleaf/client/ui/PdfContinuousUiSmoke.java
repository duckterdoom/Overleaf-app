package com.overleaf.client.ui;

import com.overleaf.client.compiler.PdfSnapshot;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.concurrent.*;

/** Explicit control/layout smoke using a real multi-page hyperref PDF. */
public final class PdfContinuousUiSmoke {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), home = Path.of(args[1]);
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new output directory"); Files.createDirectories(home);
        PdfSnapshot pdf = PdfSnapshot.read(source, "verified-input");
        if (pdf.pages() < 4) throw new AssertionError("Use a multi-page PDF");
        int sourcePage = -1, linkIndex = -1;
        outer: for (int p = 0; p < pdf.pages(); p++) for (int i = 0; i < pdf.pageInfo(p).links().size(); i++) {
            var target = pdf.pageInfo(p).links().get(i).destination();
            if (target.page() > p + 1 && target.page() < pdf.pages() - 1 && target.y() > .03 && target.y() < .3) { sourcePage = p; linkIndex = i; break outer; }
        }
        if (sourcePage < 0) throw new AssertionError("No suitable internal PDF heading link");
        int from = sourcePage, link = linkIndex; var target = pdf.pageInfo(from).links().get(link).destination();
        CompletableFuture<Void> ready = new CompletableFuture<>(); Platform.startup(() -> ready.complete(null)); ready.get(10,TimeUnit.SECONDS); Platform.setImplicitExit(false);
        PdfPreviewPane pane = UiSmoke.fx(() -> new PdfPreviewPane(() -> {}));
        Stage stage = UiSmoke.fx(() -> {
            Stage s = new Stage(); s.setOpacity(0); Scene scene = new Scene(pane,850,850);
            scene.getStylesheets().add(PdfContinuousUiSmoke.class.getResource("/styles/overleaf-theme.css").toExternalForm());
            s.setScene(scene); s.show(); pane.show(pdf,true); return s;
        });
        try {
            waitFor(() -> UiSmoke.fx(pane::hasRenderedPage));
            ScrollPane scroll = UiSmoke.fx(() -> (ScrollPane)stage.getScene().lookup("#pdf-continuous-scroll"));
            UiSmoke.fx(() -> { scroll.setVvalue(.45); return null; });
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex() > 0 && pane.hasRenderedPage()));
            checkPixels(stage, pdf, pane); System.out.println("PASS scroll changes current page and real PDF pixels");
            UiSmoke.fx(() -> { scroll.setVvalue(1); return null; });
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex() == pdf.pages()-1 && pane.hasRenderedPage()));
            UiSmoke.fx(() -> {
                long count = stage.getScene().getRoot().lookupAll("ImageView").stream().filter(n -> ((ImageView)n).getImage() != null).count();
                if (count >= pdf.pages()) throw new AssertionError("All PDF pages retained in memory");
                UiSmoke.invoke(pane,"showPage",from); return null;
            });
            waitFor(() -> UiSmoke.fx(() -> stage.getScene().lookup("#pdf-link-"+from+"-"+link) != null));
            UiSmoke.fx(() -> {
                var button=(Hyperlink)stage.getScene().lookup("#pdf-link-"+from+"-"+link);
                var sheet=stage.getScene().lookup("#pdf-sheet-"+from); var metadata=pdf.pageInfo(from).links().get(link);
                if(Math.abs(button.getLayoutX()-metadata.x()*sheet.getLayoutBounds().getWidth())>1
                        ||Math.abs(button.getLayoutY()-metadata.y()*sheet.getLayoutBounds().getHeight())>1)throw new AssertionError("Link overlay not aligned with PDF");
                button.fire(); return null;
            });
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex() == target.page() && pane.hasRenderedPage()));
            UiSmoke.fx(() -> {
                var sheet = stage.getScene().lookup("#pdf-sheet-"+target.page());
                var inViewport = scroll.lookup(".viewport");
                double heading = sheet.localToScene(0, target.y()*sheet.getBoundsInLocal().getHeight()).getY();
                if (Math.abs(heading - inViewport.localToScene(0,0).getY()) > 8) throw new AssertionError("Link did not align with heading: " + heading);
                ((Button)stage.getScene().lookup("#pdf-back")).fire(); return null;
            });
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex() == from && pane.hasRenderedPage()));
            System.out.println("PASS PDF named link jumps to heading and Back returns");
            UiSmoke.fx(() -> { ((Button)stage.getScene().lookup("#pdf-zoom-in")).fire(); return null; });
            waitFor(() -> UiSmoke.fx(pane::hasRenderedPage));
            UiSmoke.fx(() -> {
                ((Button)stage.getScene().lookup("#pdf-fit")).fire();
                var sheet=stage.getScene().lookup("#pdf-sheet-2");
                double total=((Number)UiSmoke.field(pane,"contentHeight")).doubleValue();
                double h=scroll.getViewportBounds().getHeight();
                scroll.setVvalue((sheet.getLayoutY()+sheet.getLayoutBounds().getHeight()-h*.55)/(total-h)); return null;
            });
            waitFor(() -> UiSmoke.fx(pane::hasRenderedPage));
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex()==3
                    || ((ImageView)stage.getScene().lookup("#pdf-page-image-3")).getImage()!=null));
            UiSmoke.fx(() -> { screenshot(stage,home.resolve("continuous-preview.png")); pane.markStale("Source changed"); if (pane.canExport()) throw new AssertionError("Stale export enabled"); return null; });
            Path exported = home.resolve("export.pdf"); pdf.exportTo(exported);
            if (Files.mismatch(source,exported) != -1) throw new AssertionError("Export bytes changed");
            UiSmoke.fx(() -> { pane.reset(); pane.show(pdf,true); pane.reset(); return null; });
            Thread.sleep(300);
            UiSmoke.fx(() -> { if (pane.snapshot()!=null || pane.hasRenderedPage() || pane.canExport()) throw new AssertionError("Stale render returned after reset"); return null; });
            System.out.println("PDF_CONTINUOUS_UI_SMOKE_OK pages="+pdf.pages()+" link="+from+" -> "+target.page());
        } finally { UiSmoke.fx(() -> { pane.close(); stage.hide(); return null; }); Platform.exit(); }
    }
    private static void checkPixels(Stage stage,PdfSnapshot pdf,PdfPreviewPane pane) throws Exception {
        int page=UiSmoke.fx(pane::pageIndex); var expected=pdf.render(page,1.5f);
        UiSmoke.fx(() -> { var actual=((ImageView)stage.getScene().lookup("#pdf-page-image")).getImage();
            if (actual.getWidth()!=expected.getWidth() || actual.getHeight()!=expected.getHeight()) throw new AssertionError("Image dimensions changed");
            for(int y=0;y<expected.getHeight();y+=31) for(int x=0;x<expected.getWidth();x+=31) if(actual.getPixelReader().getArgb(x,y)!=expected.getRGB(x,y)) throw new AssertionError("PDF pixels differ"); return null; });
    }
    private static void waitFor(Callable<Boolean> check) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(40); while(!check.call()){ if(System.nanoTime()>end)throw new AssertionError("PDF operation timed out");Thread.sleep(70); } }
    private static void screenshot(Stage stage,Path path) throws Exception {
        var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var buffered=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(buffered,"png",path.toFile());
    }
}
