import { chromium, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
const repo = path.resolve("../..");
const python = path.resolve(process.argv[2]);
const home = path.resolve(process.argv[3]);
const server = spawn(
    python,
    ["-X", "utf8", path.join(repo, "apps/desktop/tests/serve_ui.py"), home],
    { cwd: repo, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] },
);
let serverErrors = "";
server.stderr.on("data", (data) => {
    serverErrors += data;
});
let browser;
try {
    for (
        let i = 0;
        i < 200 && !existsSync(path.join(home, "ready.json"));
        i++
    ) {
        if (server.exitCode !== null) throw new Error(serverErrors);
        await new Promise((resolve) => setTimeout(resolve, 100));
    }
    const { url } = JSON.parse(
        readFileSync(path.join(home, "ready.json"), "utf8"),
    );
    browser = await chromium.launch({ channel: "msedge", headless: true });
    const page = await browser.newPage({
        viewport: { width: 1440, height: 1000 },
    });
    const errors = [];
    page.on("pageerror", (error) => errors.push(String(error)));
    await page.goto(url);
    await expect(page.locator("[data-desktop-ready=true]")).toBeVisible();
    await page
        .getByRole("button", { name: "Tiếp tục ngoại tuyến", exact: true })
        .click();
    await page.screenshot({ path: path.join(home, "dashboard.png") });
    await page.getByRole("button", { name: "Dự án mới", exact: true }).click();
    await page
        .getByRole("textbox", { name: "Tên dự án", exact: true })
        .fill("Migration smoke");
    await page.getByRole("button", { name: "Tạo dự án", exact: true }).click();
    await expect(page.locator(".cm-content")).toBeVisible();
    const source =
        "\\documentclass{article}\n\\usepackage{hyperref}\n\\begin{document}\n\\tableofcontents\n\\newpage\n\\section{First chapter}\nA real local PDF.\n\\newpage\n\\section{Second chapter}\nThe final page.\n\\end{document}\n";
    await page.locator(".cm-content").fill(source);
    await page.locator(".save-source").click();
    await expect(page.locator(".save-source")).toHaveText("Đã lưu");
    const project = readdirSync(path.join(home, "projects")).find((name) =>
        /^[a-f0-9]{32}$/.test(name),
    );
    expect(
        readFileSync(path.join(home, "projects", project, "main.tex"), "utf8"),
    ).toBe(source);
    await page
        .getByRole("button", { name: "Thêm thư mục", exact: true })
        .click();
    await page
        .getByLabel("Tên tệp / thư mục", { exact: true })
        .fill("chapters");
    await page.getByRole("button", { name: "Tạo", exact: true }).click();
    await expect(page.locator(".file-tree")).toContainText("chapters");
    await page.getByRole("button", { name: "Biên dịch", exact: true }).click();
    await expect(page.locator(".statusbar")).toContainText(
        "Biên dịch thành công",
        { timeout: 90000 },
    );
    await expect(page.locator(".pdf-sheet")).toHaveCount(3);
    await expect
        .poll(() =>
            page
                .locator(".pdf-sheet canvas")
                .first()
                .evaluate((canvas) => canvas.width),
        )
        .toBeGreaterThan(0);
    await expect(page.locator(".pdf-link").first()).toBeVisible();
    await page.locator(".pdf-link").first().click();
    await expect
        .poll(() =>
            page.locator(".pdf-scroll").evaluate((node) => node.scrollTop),
        )
        .toBeGreaterThan(100);
    const downloadEvent = page.waitForEvent("download");
    await page.getByRole("button", { name: "Xuất PDF", exact: true }).click();
    const download = await downloadEvent;
    expect(readFileSync(await download.path())).toEqual(
        readFileSync(path.join(home, "projects", project, "build/main.pdf")),
    );
    await page.screenshot({ path: path.join(home, "workspace.png") });
    await page.locator(".cm-content").fill(source + "\n% unsaved edit\n");
    await expect(
        page.getByRole("button", { name: "Xuất PDF", exact: true }),
    ).toBeDisabled();
    await page
        .locator(".cm-content")
        .fill(
            source.replace(
                "\\end{document}",
                "\\deliberatelyUnknownCommand\n\\end{document}",
            ),
        );
    await page.getByRole("button", { name: "Biên dịch", exact: true }).click();
    await expect(page.locator(".statusbar")).toContainText(
        "Biên dịch chưa thành công",
        { timeout: 90000 },
    );
    await expect(
        page.getByRole("button", { name: "Xuất PDF", exact: true }),
    ).toBeDisabled();
    await expect(page.locator(".pdf-sheet")).toHaveCount(3);
    expect(errors).toEqual([]);
    console.log(
        "PYTHON_NEXT_UI_SMOKE_OK create, edit/save, folder, native compile, 3-page PDF/link, exact export, stale/error guards",
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
