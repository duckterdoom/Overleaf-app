import { createRequire } from "node:module";
import { copyFileSync, mkdirSync } from "node:fs";
const require = createRequire(import.meta.url);
mkdirSync("public", { recursive: true });
copyFileSync(
    require.resolve("pdfjs-dist/build/pdf.worker.min.mjs"),
    "public/pdf.worker.min.mjs",
);
