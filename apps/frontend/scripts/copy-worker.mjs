import { createRequire } from "node:module";
import { copyFileSync, mkdirSync, readdirSync, cpSync } from "node:fs";
const require = createRequire(import.meta.url);
mkdirSync("public", { recursive: true });
copyFileSync(
    require.resolve("pdfjs-dist/build/pdf.worker.min.mjs"),
    "public/pdf.worker.min.mjs",
);
const editorAssets = "node_modules/@superdoc/docx-engine/dist/assets";
mkdirSync("public/superdoc-workers", { recursive: true });
cpSync(editorAssets, "public/superdoc-workers", { recursive: true });
const documentWorker = readdirSync(editorAssets).find(name => name.startsWith("dom-worker-entry-") && name.endsWith(".js"));
if (!documentWorker) throw Error("SuperDoc document worker asset missing");
copyFileSync(editorAssets + "/" + documentWorker, "public/superdoc-workers/document-worker.js");
