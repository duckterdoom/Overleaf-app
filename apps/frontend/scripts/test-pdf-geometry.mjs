import fs from "node:fs";
import ts from "typescript";
import assert from "node:assert/strict";
import { getDocument } from "pdfjs-dist/legacy/build/pdf.mjs";
const code = ts.transpileModule(fs.readFileSync("lib/pdfGeometry.ts", "utf8"), {
    compilerOptions: { module: ts.ModuleKind.ESNext },
}).outputText;
const { toViewport, fromViewport } = await import(
    "data:text/javascript;base64," + Buffer.from(code).toString("base64")
);
const loading = getDocument({
        data: new Uint8Array(fs.readFileSync(process.argv[2])),
    }),
    document = await loading.promise,
    golden = [
        [110, 140],
        [560, 110],
        [410, 560],
        [140, 410],
    ];
for (let i = 0; i < 4; i++) {
    const page = await document.getPage(i + 1);
    for (const scale of [0.5, 1, 1.7]) {
        const viewport = page.getViewport({ scale }),
            box = {
                media: [20, 40, 620, 840],
                crop: [60, 80, 580, 780],
                rotation: i * 90,
            },
            point = { page: i, x: 150, y: 200 };
        const pixel = toViewport(viewport, box, point);
        assert.ok(
            Math.abs(pixel[0] - golden[i][0] * scale) < 1e-8 &&
                Math.abs(pixel[1] - golden[i][1] * scale) < 1e-8,
        );
        const inverse = fromViewport(viewport, box, i, ...pixel);
        assert.ok(
            Math.abs(inverse.x - 150) < 1e-8 &&
                Math.abs(inverse.y - 200) < 1e-8,
        );
    }
}
await loading.destroy();
console.log(
    "PDF_GEOMETRY_OK 12 actual PDF.js viewports: crop offset, MediaBox origin, 0/90/180/270 rotation, 3 zoom levels",
);
