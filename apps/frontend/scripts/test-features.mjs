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
    // The themed UIW wrapper must constrain CodeMirror, otherwise its content
    // grows under overflow:hidden and neither wheel nor scrollbars can reach it.
    const longSource = Array.from(
        { length: 240 },
        (_, i) => `% line ${i + 1} ${"long content ".repeat(35)}`,
    ).join("\n");
    await page.locator(".cm-content").fill(longSource);
    const scroller = page.locator(".source-editor .cm-scroller");
    await scroller.evaluate((el) => {
        el.scrollTop = 0;
        el.scrollLeft = 0;
    });
    await scroller.hover();
    await page.mouse.wheel(0, 700);
    await expect
        .poll(() => scroller.evaluate((el) => el.scrollTop))
        .toBeGreaterThan(100);
    await page.mouse.wheel(500, 0);
    await expect
        .poll(() => scroller.evaluate((el) => el.scrollLeft))
        .toBeGreaterThan(50);
    const sizes = await scroller.evaluate((el) => ({
        height: el.clientHeight,
        parent: el.closest(".editor-area").clientHeight,
        scroll: el.scrollHeight,
    }));
    expect(sizes.height).toBeLessThanOrEqual(sizes.parent);
    expect(sizes.scroll).toBeGreaterThan(sizes.height);
    const source =
        "\\documentclass{article}\n\\usepackage{hyperref,graphicx}\n\\begin{document}\n\\section{First}\\label{sec:first}\nHello history.\n\\end{document}\n";
    await page.locator(".cm-content").fill(source);
    await page.locator(".save-source").click();
    // Edit Word directly in the workspace, then download the edited DOCX.
    await page.getByRole("tab", { name: "Word preview", exact: true }).click();
    await page.getByRole("button", { name: "Đồng bộ chữ LaTeX", exact: true }).click();
    const wordArea = page.getByRole("textbox", {
        name: "Nội dung Word",
        exact: true,
    });
    await expect(wordArea).toContainText("Hello history.");
    await wordArea
        .locator("[data-word-id]")
        .filter({ hasText: "Hello history." })
        .fill("Word direct editing synced");
    await page.getByRole("button", { name: "Lưu Word", exact: true }).click();
    await expect(page.locator(".word-actions")).toContainText("Đã lưu Word");
    await expect(page.locator(".cm-content")).toContainText(
        "Word direct editing synced",
    );
    await page.getByRole("button", { name: "Biên dịch", exact: true }).click();
    await expect(page.locator(".statusbar")).toContainText(
        "Biên dịch thành công",
        { timeout: 90000 },
    );
    const wordDownloadEvent = page.waitForEvent("download");
    await page.getByRole("button", { name: "Tải xuống", exact: true }).click();
    await page
        .getByRole("menuitem", { name: "Export Word (.docx)", exact: true })
        .click();
    const wordDownload = await wordDownloadEvent;
    expect(wordDownload.suggestedFilename()).toBe("main.docx");
    const docxBytes = fs.readFileSync(await wordDownload.path());
    expect(docxBytes.subarray(0, 2).toString()).toBe("PK");
    fs.writeFileSync(path.join(home, "edited-word.docx"), docxBytes);
    await page.screenshot({ path: path.join(home, "word-preview.png") });
    await page
        .locator(".cm-content")
        .fill(
            source.replace(
                "Hello history.",
                "LaTeX automatically updates Word.",
            ),
        );
    await expect(wordArea).toContainText("LaTeX automatically updates Word.", {
        timeout: 15000,
    });
    await page.locator(".cm-content").fill(source);
    await page.locator(".save-source").click();
    await expect(wordArea).toContainText("Hello history.", { timeout: 15000 });
    await page.getByRole("tab", { name: "PDF preview", exact: true }).click();
    // Review is metadata, never a percent-comment edit of the LaTeX source.
    const beforeReviewSource = await page.locator(".cm-content").innerText();
    await page.locator(".cm-content").click();
    await page.keyboard.press("Control+Home");
    await page.keyboard.press("Control+Shift+ArrowRight");
    await page.locator("#editor-add-comment").click();
    const review = page.getByRole("complementary", {
        name: "Nhận xét",
        exact: true,
    });
    await review
        .getByLabel("Nội dung nhận xét", { exact: true })
        .fill("Cần kiểm tra đoạn này");
    await review
        .getByRole("button", { name: "Đăng nhận xét", exact: true })
        .click();
    await expect(review).toContainText("Cần kiểm tra đoạn này");
    await review.getByLabel("Trả lời", { exact: true }).fill("Đã kiểm tra");
    await review
        .getByRole("button", { name: "Gửi trả lời", exact: true })
        .click();
    await expect(review).toContainText("Đã kiểm tra");
    await expect(page.locator(".review-highlight").first()).toBeVisible();
    await page.screenshot({ path: path.join(home, "review.png") });
    await review
        .getByRole("button", { name: "Đánh dấu đã giải quyết", exact: true })
        .click();
    await expect(review.locator("article")).toHaveCount(0);
    await review.getByLabel("Hiện đã giải quyết", { exact: true }).check();
    await expect(review.locator("article")).toHaveCount(1);
    await review.getByRole("button", { name: "Mở lại", exact: true }).click();
    await review
        .getByRole("button", { name: "Đóng nhận xét", exact: true })
        .click();
    await page.locator("#editor-add-comment").click();
    await expect(review).toContainText("Cần kiểm tra đoạn này");
    await review.getByRole("button", { name: "Hủy", exact: true }).click();
    await review
        .getByRole("button", { name: "Đóng nhận xét", exact: true })
        .click();
    expect(await page.locator(".cm-content").innerText()).toBe(
        beforeReviewSource,
    );
    const rail = page.getByRole("navigation", {
        name: "Thanh công cụ workspace",
    });
    await expect(rail.getByRole("button", { name: "Tệp và outline", exact: true })).toBeVisible();
    await rail.getByRole("button", { name: "Nhận xét", exact: true }).click();
    await expect(review).toBeVisible();
    await expect(page.locator(".file-sidebar")).toBeHidden();
    expect(await review.evaluate((el) => !!el.closest(".editor-column"))).toBe(
        false,
    );
    await rail
        .getByRole("button", { name: "Chat cộng tác", exact: true })
        .click();
    await expect(review).toBeHidden();
    const chat = page.getByRole("complementary", {
        name: "Kênh chat cộng tác",
    });
    await expect(chat).toBeVisible();
    await expect(chat.getByRole("textbox")).toBeDisabled();
    await page.screenshot({ path: path.join(home, "chat-panel.png") });
    await rail
        .getByRole("button", { name: "Tệp và outline", exact: true })
        .click();
    await expect(page.locator(".file-sidebar")).toBeVisible();
    await expect(chat).toBeHidden();
    await rail
        .getByRole("button", { name: "Tệp và outline", exact: true })
        .click();
    await expect(page.locator(".file-sidebar")).toBeHidden();
    await expect(page.locator(".sidebar-divider")).toBeHidden();
    await rail
        .getByRole("button", { name: "Tệp và outline", exact: true })
        .click();
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
    const treePaths = await page
        .locator(".file-tree button")
        .evaluateAll((nodes) => nodes.map((n) => n.title));
    expect(treePaths.indexOf("chapters/renamed.tex")).toBe(
        treePaths.indexOf("chapters") + 1,
    );
    expect(treePaths.indexOf("chapters/renamed.tex")).toBeLessThan(
        treePaths.indexOf("main.tex"),
    );
    await page.getByTitle("chapters", { exact: true }).click();
    await expect(
        page.getByTitle("chapters/renamed.tex", { exact: true }),
    ).toHaveCount(0);
    await page.getByTitle("chapters", { exact: true }).click();
    await expect(
        page.getByTitle("chapters/renamed.tex", { exact: true }),
    ).toBeVisible();
    const sidebarDivider = page.getByRole("separator", {
        name: "Điều chỉnh thanh thư mục",
    });
    const sidebarBox = await sidebarDivider.boundingBox();
    const beforeWidth = await page
        .locator(".file-sidebar")
        .evaluate((el) => el.clientWidth);
    await page.mouse.move(sidebarBox.x + 3, sidebarBox.y + 100);
    await page.mouse.down();
    await page.mouse.move(sidebarBox.x + 103, sidebarBox.y + 100);
    await page.mouse.up();
    await expect
        .poll(() =>
            page.locator(".file-sidebar").evaluate((el) => el.clientWidth),
        )
        .toBeGreaterThan(beforeWidth + 50);
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
    // Focus mode must always expose a route back, even with the editor focused.
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    await page
        .locator(".layout-menu")
        .getByRole("button", { name: "Chế độ tập trung", exact: false })
        .click();
    await expect(page.locator(".desktop-shell")).toHaveClass(/focus-mode/);
    await expect(
        page.getByRole("button", { name: "Bố cục", exact: true }),
    ).toBeVisible();
    await expect(page.locator(".focus-exit-button")).toBeVisible();
    await page.screenshot({ path: path.join(home, "focus-mode.png") });
    await page.locator(".cm-content").click();
    await page.keyboard.press("Escape");
    await expect(page.locator(".desktop-shell")).not.toHaveClass(/focus-mode/);
    await expect(page.locator(".file-sidebar")).toBeVisible();
    await page.keyboard.press("Control+Shift+M");
    await expect(page.locator(".desktop-shell")).toHaveClass(/focus-mode/);
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    await page
        .locator(".layout-menu")
        .getByRole("button", { name: "Thoát chế độ tập trung", exact: false })
        .click();
    await expect(page.locator(".desktop-shell")).not.toHaveClass(/focus-mode/);
    await page.keyboard.press("Control+Shift+M");
    await page.locator(".focus-exit-button").click();
    await expect(page.locator(".desktop-shell")).not.toHaveClass(/focus-mode/);
    await expect(page.locator(".workspace-content")).toHaveClass(
        /layout-editor/,
    );
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
    await page.getByRole("button", { name: "Tải xuống", exact: true }).click();
    await expect(
        page.getByRole("menuitem", { name: "Export PDF (.pdf)", exact: true }),
    ).toBeEnabled({ timeout: 90000 });
    await page.getByRole("button", { name: "Tải xuống", exact: true }).click();
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
        page.getByRole("button", { name: "Download", exact: true }),
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
        "PARITY_UI_OK editor vertical/horizontal wheel scrolling, accounts, toolbar, labels/diff/history, upload, nested file, outline drag, layout, F5, auto compile, preferences, English, profile, remember/activity, system dark, true example template",
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
