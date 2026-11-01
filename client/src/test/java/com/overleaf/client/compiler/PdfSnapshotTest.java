package com.overleaf.client.compiler;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PdfSnapshotTest {
    @TempDir Path root;

    @Test void linkCoordinatesRespectCropOffsetsAndRotatedDestination() throws Exception {
        Path file = root.resolve("links.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage first = new PDPage(new PDRectangle(20,30,200,100)); doc.addPage(first);
            PDPage target = new PDPage(new PDRectangle(10,20,300,400)); target.setRotation(90); doc.addPage(target);
            PDPageXYZDestination destination = new PDPageXYZDestination(); destination.setPage(target); destination.setLeft(70); destination.setTop(320);
            PDAnnotationLink link = new PDAnnotationLink(); link.setRectangle(new PDRectangle(40,50,40,20)); link.setDestination(destination);
            first.getAnnotations().add(link); doc.save(file.toFile());
        }
        var pdf = PdfSnapshot.read(file, ""); var link = pdf.pageInfo(0).links().getFirst();
        assertEquals(.1, link.x(), .001); assertEquals(.6, link.y(), .001);
        assertEquals(.2, link.width(), .001); assertEquals(.2, link.height(), .001);
        assertEquals(1, link.destination().page()); assertEquals(.75, link.destination().x(), .001); assertEquals(.2, link.destination().y(), .001);
        assertEquals(400, pdf.pageInfo(1).width()); assertEquals(300, pdf.pageInfo(1).height());
    }

    @Test void namedHyperrefStyleLinksResolveAndExternalActionsAreNotNavigationTargets() throws Exception {
        Path file = root.resolve("named.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4), target = new PDPage(PDRectangle.A4); doc.addPage(first); doc.addPage(target);
            PDPageXYZDestination destination = new PDPageXYZDestination(); destination.setPage(target); destination.setTop(700);
            var names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            var tree = new PDDestinationNameTreeNode(); tree.setNames(Map.of("section.1", destination)); names.setDests(tree); doc.getDocumentCatalog().setNames(names);
            PDAnnotationLink link = new PDAnnotationLink(); link.setRectangle(new PDRectangle(30,700,100,20));
            PDActionGoTo go = new PDActionGoTo(); go.setDestination(new PDNamedDestination("section.1")); link.setAction(go); first.getAnnotations().add(link);
            PDAnnotationLink external = new PDAnnotationLink(); external.setRectangle(new PDRectangle(30,650,100,20));
            PDActionURI uri = new PDActionURI(); uri.setURI("https://example.test"); external.setAction(uri); first.getAnnotations().add(external);
            doc.save(file.toFile());
        }
        var pdf = PdfSnapshot.read(file, "");
        assertEquals(1, pdf.pageInfo(0).links().size()); assertEquals(1, pdf.pageInfo(0).links().getFirst().destination().page());
        assertThrows(UnsupportedOperationException.class, () -> pdf.pageInfo(0).links().clear());
    }

    @Test void invalidAndMissingDestinationsDoNotBlockPreviewOrExport() throws Exception {
        Path file = root.resolve("broken-link.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4); doc.addPage(first);
            PDAnnotationLink link = new PDAnnotationLink(); link.setRectangle(new PDRectangle(10,10,50,20));
            PDPageXYZDestination missing = new PDPageXYZDestination(); missing.setPageNumber(999); link.setDestination(missing); first.getAnnotations().add(link);
            doc.save(file.toFile());
        }
        var pdf = PdfSnapshot.read(file, ""); assertTrue(pdf.pageInfo(0).links().isEmpty());
        pdf.exportTo(root.resolve("copy.pdf")); assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(root.resolve("copy.pdf")));
    }

    private Path pdf() throws Exception {
        Path file = root.resolve("result.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage first = new PDPage(new PDRectangle(200, 100));
            doc.addPage(first);
            try (PDPageContentStream content = new PDPageContentStream(doc, first)) {
                content.setNonStrokingColor(Color.RED);
                content.addRect(0, 0, 200, 100);
                content.fill();
            }
            PDPage second = new PDPage(new PDRectangle(300, 400));
            second.setRotation(90);
            doc.addPage(second);
            doc.save(file.toFile());
        }
        return file;
    }

    @Test void rendersActualPageDimensionsRotationAndContent() throws Exception {
        PdfSnapshot snapshot = PdfSnapshot.read(pdf(), "fingerprint");
        assertEquals(2, snapshot.pages());
        var first = snapshot.render(0, 1);
        assertEquals(200, first.getWidth());
        assertEquals(100, first.getHeight());
        assertEquals(Color.RED.getRGB(), first.getRGB(100, 50));
        var second = snapshot.render(1, 1);
        assertEquals(400, second.getWidth());
        assertEquals(300, second.getHeight());
    }

    @Test void exportCopiesExactPreviewBytesEvenWhenCompilerOverwritesOutput() throws Exception {
        Path original = pdf();
        byte[] expected = Files.readAllBytes(original);
        PdfSnapshot snapshot = PdfSnapshot.read(original, "input");
        Files.writeString(original, "partial output from a failed compile");
        Path destination = root.resolve("export.pdf");
        snapshot.exportTo(destination);
        assertArrayEquals(expected, Files.readAllBytes(destination));
        assertEquals(Color.RED.getRGB(), snapshot.render(0, 1).getRGB(100, 50));
    }

    @Test void invalidPdfPageOrZoomIsRejected() throws Exception {
        Path invalid = root.resolve("invalid.pdf");
        Files.writeString(invalid, "not a PDF");
        assertThrows(IOException.class, () -> PdfSnapshot.read(invalid, ""));
        PdfSnapshot snapshot = PdfSnapshot.read(pdf(), "");
        assertThrows(IllegalArgumentException.class, () -> snapshot.render(2, 1));
        assertThrows(IllegalArgumentException.class, () -> snapshot.render(0, Float.NaN));
    }

    @Test void fingerprintIgnoresBuildButDetectsSameSizeDependencyEdit() throws Exception {
        Files.writeString(root.resolve("main.tex"), "main");
        Path included = root.resolve("chapter.tex");
        Files.writeString(included, "old");
        String first = ProjectInputs.fingerprint(root);
        Files.createDirectory(root.resolve("build"));
        Files.writeString(root.resolve("build/main.pdf"), "output");
        assertEquals(first, ProjectInputs.fingerprint(root));
        var time = Files.getLastModifiedTime(included);
        Files.writeString(included, "new");
        Files.setLastModifiedTime(included, time);
        assertNotEquals(first, ProjectInputs.fingerprint(root));
    }

    @Test void fingerprintTracksBinaryAssetsRenameAndDeletion() throws Exception {
        Path image = root.resolve("plot.png");
        Files.write(image, new byte[]{1, 2, 3});
        String first = ProjectInputs.fingerprint(root);
        Files.move(image, root.resolve("other.png"));
        String renamed = ProjectInputs.fingerprint(root);
        assertNotEquals(first, renamed);
        Files.delete(root.resolve("other.png"));
        assertNotEquals(renamed, ProjectInputs.fingerprint(root));
    }
}
