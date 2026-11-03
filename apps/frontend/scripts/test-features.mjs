import { chromium, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
const repo = path.resolve("../.."),
    python = path.resolve(process.argv[2]),
    home = path.resolve(process.argv[3]);
const server = spawn(
    python,
    ["-X", "utf8", path.join(repo, "apps/desktop/tests/serve_ui.py"), home],
    { cwd: repo, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] },
);
let stderr = "";
server.stderr.on("data", (chunk) => (stderr += chunk));
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
    const { url } = JSON.parse(
        fs.readFileSync(path.join(home, "ready.json"), "utf8"),
    );
    browser = await chromium.launch({
        channel: process.env.OVERLEAF_TEST_BROWSER || "msedge",
        headless: true,
        timeout: 15000,
    });
    const page = await browser.newPage({
            viewport: { width: 1500, height: 1000 },
        }),
        errors = [];
    page.on("pageerror", (e) => errors.push(String(e)));
    await page.goto(url);
    await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
    await page
        .getByLabel("Tên hiển thị", { exact: true })
        .fill("Nguyễn Tester");
    await page.getByLabel("Email", { exact: true }).fill("tester@example.org");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("secret123");
    await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
    await page.getByRole("button", { name: "Dự án mới", exact: true }).click();
    await page
        .getByRole("textbox", { name: "Tên dự án", exact: true })
        .fill("Parity features");
    await page.getByRole("button", { name: "Tạo dự án", exact: true }).click();
    await expect(page.locator(".cm-content")).toBeVisible();
    const source =
        "\\documentclass{article}\n\\usepackage{hyperref,graphicx}\n\\begin{document}\n\\section{First}\\label{sec:first}\nHello history.\n\\end{document}\n";
    await page.locator(".cm-content").fill(source);
    await page.locator(".save-source").click();
    // Toolbar order and operations use the real CodeMirror state, not DOM-only text.
    const ids = await page
        .locator(".full-toolbar button[id]")
        .evaluateAll((nodes) => nodes.map((n) => n.id));
    expect(ids).toEqual([
        "editor-bold",
        "editor-italic",
        "editor-insert-math",
        "editor-insert-link",
        "editor-add-comment",
        "editor-insert-reference",
        "editor-insert-citation",
        "editor-insert-figure",
        "editor-insert-table",
        "editor-insert-list",
        "editor-decrease-indent",
        "editor-increase-indent",
    ]);
    await page.locator("#editor-insert-link").click();
    await page.getByLabel("URL", { exact: true }).fill("https://example.org");
    await page.getByLabel("Chữ hiển thị", { exact: true }).fill("Example");
    await page.locator("#editor-insert-confirm").click();
    await expect(page.locator(".cm-content")).toContainText(
        "\\href{https://example.org}{Example}",
    );
    await page.locator(".cm-content").fill(source);
    await page.keyboard.press("Control+s");
    await page.getByRole("button", { name: "Lịch sử", exact: true }).click();
    await expect(page.locator(".revision-list>button")).not.toHaveCount(0);
    await page.locator(".revision-list>button").first().click();
    await page.getByLabel("Nhãn revision", { exact: true }).fill("Milestone");
    await page.getByRole("button", { name: "Lưu nhãn", exact: true }).click();
    await page.getByLabel("So sánh với hiện tại", { exact: true }).check();
    await expect(page.locator(".history-diff")).toBeVisible();
    await page
        .getByRole("button", { name: "Quay lại editor", exact: true })
        .click();
    await page.getByRole("button", { name: "Tải lên", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Thêm tệp", exact: true });
    await dialog
        .locator("input[type=file]")
        .first()
        .setInputFiles({
            name: "refs.bib",
            mimeType: "application/octet-stream",
            buffer: Buffer.from("@book{key,title={Test}}"),
        });
    await dialog
        .getByRole("button", { name: "Tải lên", exact: true })
        .last()
        .click();
    await expect(page.locator(".file-tree")).toContainText("refs.bib");
    await page.getByRole("button", { name: "Thêm tệp", exact: true }).click();
    await page
        .getByLabel("Tên tệp / thư mục", { exact: true })
        .fill("chapters/one");
    await page
        .getByRole("dialog")
        .getByRole("button", { name: "Tạo", exact: true })
        .click();
    await expect(page.locator(".file-tab")).toContainText("chapters/one.tex");
    await page
        .locator(".cm-content")
        .fill("Unsaved nested content before rename");
    await page
        .getByTitle("chapters/one.tex", { exact: true })
        .click({ button: "right" });
    await page
        .getByLabel("Đường dẫn mới", { exact: true })
        .fill("chapters/renamed.tex");
    await page.getByRole("button", { name: "Đổi tên", exact: true }).click();
    await expect(page.locator(".file-tree")).toContainText("renamed.tex");
    const currentId = fs
        .readdirSync(path.join(home, "projects"))
        .find((id) => /^[a-f0-9]{32}$/.test(id));
    expect(
        fs.readFileSync(
            path.join(home, "projects", currentId, "chapters/renamed.tex"),
            "utf8",
        ),
    ).toBe("Unsaved nested content before rename");
    await page.getByRole("button", { name: "main.tex", exact: true }).click();
    const divider = page.getByRole("separator", { name: "Điều chỉnh outline" });
    const box = await divider.boundingBox();
    await page.mouse.move(box.x + 20, box.y + 3);
    await page.mouse.down();
    await page.mouse.move(box.x + 20, box.y + 90);
    await page.mouse.up();
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    await page
        .getByRole("button", { name: "Chỉ editor", exact: false })
        .click();
    await expect(page.locator(".preview-column")).toHaveCount(0);
    await page.keyboard.press("F5");
    await expect(page.locator(".statusbar")).toContainText(
        "Biên dịch thành công",
        { timeout: 90000 },
    );
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    await page
        .getByRole("button", { name: "Chia đôi màn hình", exact: false })
        .click();
    await expect(page.locator(".pdf-sheet")).toHaveCount(1);
    await page
        .getByRole("checkbox", { name: "Tự biên dịch", exact: true })
        .check();
    await page
        .locator(".cm-content")
        .fill(source.replace("Hello history.", "Automatic compilation."));
    await expect(page.locator(".statusbar")).toContainText(
        "Biên dịch thành công",
        { timeout: 90000 },
    );
    await expect(
        page.getByRole("button", { name: "Xuất PDF", exact: true }),
    ).toBeEnabled({ timeout: 90000 });
    await page
        .getByRole("checkbox", { name: "Tự biên dịch", exact: true })
        .uncheck();
    await page.getByRole("button", { name: "Cài đặt", exact: true }).click();
    await page
        .getByRole("button", { name: "Trình soạn thảo", exact: true })
        .click();
    await page.getByLabel("Cỡ chữ editor", { exact: true }).fill("18");
    await page.getByLabel("Tự xuống dòng", { exact: true }).check();
    await page.getByRole("button", { name: "Giao diện", exact: true }).click();
    await page.getByLabel("Ngôn ngữ", { exact: true }).selectOption("en");
    await page.getByRole("button", { name: "Apply", exact: true }).click();
    await expect(
        page.getByRole("button", { name: "History", exact: true }),
    ).toBeVisible();
    await expect(
        page.getByRole("button", { name: "Export PDF", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Profile", exact: true }).click();
    await page
        .getByLabel("Display name", { exact: true })
        .fill("Renamed Tester");
    await page
        .getByRole("button", { name: "Save profile", exact: true })
        .click();
    await page.reload();
    await expect(
        page.getByRole("button", { name: "New project", exact: true }),
    ).toBeVisible();
    await expect(page.locator(".activity-panel .heatmap span")).toHaveCount(
        364,
    );
    await expect(page.locator("html")).toHaveAttribute("data-mode", "light");
    await page.emulateMedia({ colorScheme: "dark" });
    await page.getByRole("button", { name: "Settings", exact: true }).click();
    await page.getByLabel("Mode", { exact: true }).selectOption("system");
    await page.getByRole("button", { name: "Apply", exact: true }).click();
    await expect(page.locator("html")).toHaveAttribute("data-mode", "dark");
    await page
        .getByRole("button", { name: "New project", exact: true })
        .click();
    await page
        .getByRole("button", { name: "Example project", exact: true })
        .click();
    await page
        .getByRole("textbox", { name: "Project name", exact: true })
        .fill("Example parity project");
    await page
        .getByRole("button", { name: "Create project", exact: true })
        .click();
    await expect(page.locator(".cm-content")).toBeVisible();
    const example = fs
        .readdirSync(path.join(home, "projects"))
        .filter((id) => /^[a-f0-9]{32}$/.test(id))
        .map((id) =>
            JSON.parse(
                fs.readFileSync(
                    path.join(home, "projects", id, ".project.json"),
                    "utf8",
                ),
            ),
        )
        .find((item) => item.title === "Example parity project");
    expect(example.template).toBe("example");
    await page.screenshot({ path: path.join(home, "features.png") });
    expect(errors).toEqual([]);
    console.log(
        "PARITY_UI_OK accounts, toolbar, labels/diff/history, upload, nested file, outline drag, layout, F5, auto compile, preferences, English, profile, remember/activity, system dark, true example template",
    );
} finally {
    await browser?.close();
    if (process.platform === "win32")
        spawnSync("taskkill", ["/PID", String(server.pid), "/T", "/F"], {
            windowsHide: true,
            stdio: "ignore",
            timeout: 5000,
        });
    else server.kill("SIGTERM");
}
