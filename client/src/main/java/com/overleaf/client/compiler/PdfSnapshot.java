package com.overleaf.client.compiler;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Immutable successful PDF: rendering and export always read exactly the same bytes. */
public final class PdfSnapshot {
    private final byte[] bytes;
    private final int pages;
    private final String inputFingerprint;
    private final List<PageInfo> pageInfo;

    /** All coordinates are fractions of the rotated crop box, measured from the top left. */
    public record Destination(int page, double x, double y) { }
    public record Link(double x, double y, double width, double height, Destination destination) { }
    public record PageInfo(double width, double height, List<Link> links) { }

    private PdfSnapshot(byte[] bytes, List<PageInfo> pageInfo, String fingerprint) {
        this.bytes = bytes;
        this.pages = pageInfo.size();
        this.pageInfo = List.copyOf(pageInfo);
        this.inputFingerprint = fingerprint;
    }

    public static PdfSnapshot read(Path pdf, String fingerprint) throws IOException {
        byte[] bytes;
        try (var in = Files.newInputStream(pdf)) {
            bytes = in.readNBytes(128 * 1024 * 1024 + 1);
        }
        if (bytes.length > 128 * 1024 * 1024) throw new IOException("PDF exceeds the preview limit of 128 MiB.");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() == 0) throw new IOException("PDF contains no pages.");
            List<PageInfo> info = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getCropBox();
                boolean sideways = rotation(page) == 90 || rotation(page) == 270;
                double width = sideways ? box.getHeight() : box.getWidth();
                double height = sideways ? box.getWidth() : box.getHeight();
                if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
                    throw new IOException("Invalid PDF page dimensions.");
                List<Link> links = new ArrayList<>();
                for (var annotation : page.getAnnotations()) {
                    if (!(annotation instanceof PDAnnotationLink link) || link.isHidden() || link.isInvisible()) continue;
                    try {
                        PDDestination destination = link.getAction() instanceof PDActionGoTo go ? go.getDestination() : link.getDestination();
                        Destination target = destination(document, destination);
                        PDRectangle rect = link.getRectangle();
                        if (target == null || rect == null) continue;
                        double[] a = point(page, rect.getLowerLeftX(), rect.getLowerLeftY());
                        double[] b = point(page, rect.getUpperRightX(), rect.getUpperRightY());
                        double x = Math.min(a[0], b[0]), y = Math.min(a[1], b[1]);
                        double w = Math.abs(a[0] - b[0]), h = Math.abs(a[1] - b[1]);
                        if (w > 0 && h > 0) links.add(new Link(x, y, w, h, target));
                    } catch (IOException | IllegalArgumentException malformed) {
                        // A broken annotation must not prevent viewing/exporting a valid page.
                    }
                }
                info.add(new PageInfo(width, height, List.copyOf(links)));
            }
            return new PdfSnapshot(bytes, info, fingerprint);
        }
    }

    public int pages() { return pages; }
    public PageInfo pageInfo(int page) { return pageInfo.get(page); }
    public String inputFingerprint() { return inputFingerprint; }
    public record PageImage(BufferedImage image, float scale) { }

    private static Destination destination(PDDocument doc, PDDestination destination) throws IOException {
        if (destination instanceof PDNamedDestination named) destination = doc.getDocumentCatalog().findNamedDestinationPage(named);
        if (!(destination instanceof PDPageDestination pageDestination)) return null;
        int index = pageDestination.getPage() == null ? pageDestination.getPageNumber() : doc.getPages().indexOf(pageDestination.getPage());
        if (index < 0 || index >= doc.getNumberOfPages()) return null;
        PDPage page = doc.getPage(index);
        PDRectangle box = page.getCropBox();
        double x = box.getLowerLeftX(), y = box.getUpperRightY();
        if (pageDestination instanceof PDPageXYZDestination xyz) {
            if (xyz.getLeft() >= 0) x = xyz.getLeft();
            if (xyz.getTop() >= 0) y = xyz.getTop();
        } else if (pageDestination instanceof PDPageFitWidthDestination fitWidth && fitWidth.getTop() >= 0) y = fitWidth.getTop();
        // Fit/FitB destinations have no heading coordinate: navigate to the page top.
        if (!(pageDestination instanceof PDPageXYZDestination) && !(pageDestination instanceof PDPageFitWidthDestination)) return new Destination(index, 0, 0);
        double[] p = point(page, x, y);
        return new Destination(index, p[0], p[1]);
    }

    private static int rotation(PDPage page) { return Math.floorMod(page.getRotation(), 360); }
    private static double[] point(PDPage page, double x, double y) {
        PDRectangle box = page.getCropBox();
        double u = (x - box.getLowerLeftX()) / box.getWidth(), v = (box.getUpperRightY() - y) / box.getHeight();
        double[] p = switch (rotation(page)) {
            case 90 -> new double[]{1 - v, u};
            case 180 -> new double[]{1 - u, 1 - v};
            case 270 -> new double[]{v, 1 - u};
            default -> new double[]{u, v};
        };
        return new double[]{Math.clamp(p[0], 0, 1), Math.clamp(p[1], 0, 1)};
    }

    /** Renders one real PDF page, preserving its own crop box, size, rotation and embedded fonts. */
    public BufferedImage render(int page, float requestedScale) throws IOException {
        return renderPage(page, requestedScale).image();
    }

    public PageImage renderPage(int page, float requestedScale) throws IOException {
        if (page < 0 || page >= pages) throw new IllegalArgumentException("Page is out of range.");
        if (!Float.isFinite(requestedScale) || requestedScale <= 0) throw new IllegalArgumentException("Invalid zoom.");
        try (var document = Loader.loadPDF(bytes)) {
            var box = document.getPage(page).getCropBox();
            double area = Math.max(1, box.getWidth() * (double) box.getHeight());
            float scale = (float) Math.min(requestedScale, Math.min(3, Math.sqrt(12_000_000 / area)));
            return new PageImage(new PDFRenderer(document).renderImage(page, scale, ImageType.RGB), scale);
        }
    }

    /** Copies the PDF, never an image of the preview. Atomic replacement when supported. */
    public void exportTo(Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path temp = Files.createTempFile(target.getParent(), ".overleaf-export-", ".pdf");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
