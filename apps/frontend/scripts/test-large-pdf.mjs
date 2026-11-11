import { chromium, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
const repo = path.resolve("../.."),
    home = path.resolve(process.argv[3]),
    python = path.resolve(process.argv[2]);
fs.rmSync(path.join(home, "ready.json"), { force: true });
const server = spawn(
    python,
    [
        "-X",
        "utf8",
        path.join(repo, "apps/backend/tests/serve_ui.py"),
        home,
        "--existing",
    ],
    { cwd: repo, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] },
);
let stderr = "";
server.stderr.on("data", (data) => (stderr += data));
let browser;
try {
    for (
        let i = 0;
        i < 200 && !fs.existsSync(path.join(home, "ready.json"));
        i++
    ) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise((r) => setTimeout(r, 100));
    }
    browser = await chromium.launch({ channel: "msedge", headless: true });
    const page = await browser.newPage({
            viewport: { width: 1440, height: 1000 },
        }),
        errors = [];
    page.on("pageerror", (error) => errors.push(String(error)));
    await page.goto(
        JSON.parse(fs.readFileSync(path.join(home, "ready.json"), "utf8")).url,
    );
    await page
        .getByRole("button", { name: "Tiếp tục ngoại tuyến", exact: true })
        .click();
    await page.getByRole("button", { name: /248-page benchmark/ }).click();
    const start = Date.now();
    await page.getByRole("button", { name: "Biên dịch", exact: true }).click();
    await expect(page.locator(".pdf-sheet")).toHaveCount(248, {
        timeout: 90000,
    });
    await expect
        .poll(() =>
            page
                .locator(".pdf-sheet canvas")
                .first()
                .evaluate((canvas) => canvas.width),
        )
        .toBeGreaterThan(0);
    const firstPageMs = Date.now() - start;
    const metrics = [];
    for (const fraction of [0, 0.1, 0.25, 0.5, 0.75, 1, 0]) {
        await page
            .locator(".pdf-scroll")
            .evaluate(
                (node, f) =>
                    (node.scrollTop =
                        (node.scrollHeight - node.clientHeight) * f),
                fraction,
            );
        await page.waitForTimeout(350);
        metrics.push(
            await page.locator(".pdf-scroll").evaluate((node) => {
                const canvases = [...node.querySelectorAll("canvas")];
                return {
                    scroll: node.scrollTop,
                    canvases: canvases.filter((c) => c.width > 0).length,
                    bytes: canvases.reduce(
                        (sum, c) => sum + c.width * c.height * 4,
                        0,
                    ),
                    heap: performance.memory?.usedJSHeapSize,
                };
            }),
        );
    }
    expect(Math.max(...metrics.map((x) => x.canvases))).toBeLessThanOrEqual(6);
    expect(Math.max(...metrics.map((x) => x.bytes))).toBeLessThan(
        80 * 1024 * 1024,
    );
    expect(errors).toEqual([]);
    fs.writeFileSync(
        path.join(home, "viewer-benchmark.json"),
        JSON.stringify({ firstPageMs, metrics }, null, 2),
    );
    await page.screenshot({ path: path.join(home, "large-pdf.png") });
    console.log(
        "PDFJS_248_PAGE_OK",
        JSON.stringify({
            firstPageMs,
            maxCanvases: Math.max(...metrics.map((x) => x.canvases)),
            maxCanvasBytes: Math.max(...metrics.map((x) => x.bytes)),
        }),
    );
} finally {
    await browser?.close();
    if (process.platform === "win32")
        spawnSync("taskkill", ["/PID", String(server.pid), "/T", "/F"], {
            windowsHide: true,
            stdio: "ignore",
        });
    else server.kill("SIGTERM");
}
