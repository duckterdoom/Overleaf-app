import type { PageGeometry, PdfPoint } from "./api";
type Viewport = {
    convertToViewportPoint: (x: number, y: number) => number[];
    convertToPdfPoint: (x: number, y: number) => number[];
};
// SyncTeX uses the top-left of MediaBox; PDF.js handles CropBox, rotation and zoom.
export function toViewport(
    viewport: Viewport,
    box: PageGeometry,
    point: PdfPoint,
) {
    return viewport.convertToViewportPoint(
        point.x + box.media[0],
        box.media[3] - point.y,
    );
}
export function fromViewport(
    viewport: Viewport,
    box: PageGeometry,
    page: number,
    x: number,
    y: number,
): PdfPoint {
    const [rawX, rawY] = viewport.convertToPdfPoint(x, y);
    return { page, x: rawX - box.media[0], y: box.media[3] - rawY };
}
