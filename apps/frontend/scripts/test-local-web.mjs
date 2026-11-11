import { chromium, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
const repo = path.resolve("../.."),
    python = path.resolve(process.argv[2]),
    home = path.resolve(process.argv[3]);
if (fs.existsSync(home)) throw Error("Use a new test home");
fs.mkdirSync(home, { recursive: true });
const seed = spawnSync(
    python,
    [
        "-X",
        "utf8",
        "-c",
        `
import sys,zipfile
from pathlib import Path
from PIL import Image
home=Path(sys.argv[1])
Image.new('RGB',(20,20),'green').save(home/'fixture.png')
with zipfile.ZipFile(home/'browser-import.zip','w') as z:
 z.writestr('article.tex',r'\\documentclass{article}\\begin{document}ZIP WEB IMPORT\\end{document}')
`,
        home,
    ],
    { cwd: repo, windowsHide: true, encoding: "utf8" },
);
if (seed.status) throw Error(seed.stderr);
const server = spawn(
    python,
    [
        "-X",
        "utf8",
        ...(process.env.LATEX_TEST_WEB_FACTORY ? [path.resolve(process.env.LATEX_TEST_WEB_FACTORY)] : ["-m", "latex_backend.entrypoints.web"]),
        "--home",
        home,
        "--port",
        "0",
        "--no-browser",
    ],
    { cwd: repo, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] },
);
let stdout = "",
    stderr = "",
    browser,
    page;
server.stdout.on("data", (chunk) => (stdout += chunk));
server.stderr.on("data", (chunk) => (stderr += chunk));
try {
    for (let i = 0; i < 300 && !stdout.includes("OVERLEAF_WEB_READY "); i++) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise((r) => setTimeout(r, 100));
    }
    const line = stdout
        .split(/\r?\n/)
        .find((line) => line.startsWith("OVERLEAF_WEB_READY "));
    if (!line) throw Error("Server readiness timeout: " + stderr);
    const { url } = JSON.parse(line.slice("OVERLEAF_WEB_READY ".length));
    browser = await chromium.launch({
        channel: process.env.OVERLEAF_TEST_BROWSER || "msedge",
        headless: true,
    });
    const context = await browser.newContext({
            viewport: { width: 1700, height: 1050 },
        }),
        errors = [],
        auth = [];
    page = await context.newPage();
    page.on("pageerror", (e) => errors.push(String(e)));
    page.on("request", (r) => {
        if (r.url().startsWith(url + "api/") && r.headers().authorization)
            auth.push(r.url());
    });
    await page.goto(url);
    await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
    await page.getByLabel("Tên hiển thị", { exact: true }).fill("Browser Student");
    await page.getByLabel("Email", { exact: true }).fill("browser-student@example.com");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("browser-password");
    await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
    await page.getByRole("radio", { name: /Student/ }).check();
    await page.getByRole("button", { name: "Vào không gian của tôi", exact: true }).click();
    await expect(page.getByRole("heading", { name: /Không gian học viên/ })).toBeVisible();
    const identity = await page.evaluate(async () => (await (await fetch("/api/session")).json()).session);
    const workspaceHome = path.join(home, "users", identity.id);
    await expect(page).toHaveTitle("Latex");
    expect(await page.evaluate(() => Boolean(window.pywebview))).toBe(false);
    expect(
        await page.evaluate(() => sessionStorage.getItem("overleaf_session")),
    ).toBeNull();
    expect(
        await page.evaluate(() =>
            document.cookie.includes("overleaf_local_session"),
        ),
    ).toBe(false);
    const cookie = (await context.cookies()).find(
        (c) => c.name === "overleaf_local_session",
    );
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.sameSite).toBe("Strict");
    await page.getByRole("button", { name: "Cài đặt", exact: true }).click();
    const settings = page.getByRole("dialog");
    await settings.getByRole("button", { name: "Dự án", exact: true }).click();
    await expect(settings.getByText("Thư mục dự án mặc định", { exact: true })).toHaveCount(0);
    await expect(settings.getByText(/Dữ liệu được lưu trên server/)).toBeVisible();
    await settings.getByRole("button", { name: "Đóng", exact: true }).click();
    await page.getByRole("button", { name: "Dự án mới", exact: true }).click();
    await expect(page.getByText("Thư mục cha lưu dự án", { exact: true })).toHaveCount(0);
    await expect(page.getByText(/Dự án mới và tệp nhập được lưu trên server/)).toBeVisible();
    await page
        .getByRole("textbox", { name: "Tên dự án", exact: true })
        .fill("Local web browser project");
    await page.getByRole("button", { name: "Tạo dự án", exact: true }).click();
    await expect(page.locator(".cm-content")).toBeVisible();
    await page
        .locator(".cm-content")
        .fill(
            "\\documentclass{article}\n\\begin{document}\nLOCAL WEB ARTICLE\n\\newpage\nSECOND WEB PAGE\n\\end{document}\n",
        );
    // Live OT may acknowledge the edit before Playwright clicks Save, making
    // that button correctly disabled. Ctrl+S exercises flush in either state.
    await page.keyboard.press('Control+s');
    await expect(page.locator('.save-source')).toBeDisabled();
    await page.locator(".compile-button").click();
    await expect(page.locator(".pdf-sheet")).toHaveCount(2, { timeout: 30000 });
    await expect(page.locator(".compile-button")).toBeEnabled();
    const pdfDownload = page.waitForEvent("download");
    await page.locator(".download-options > button").click();
    await page
        .getByRole("menuitem", { name: "Export PDF (.pdf)", exact: true })
        .click();
    const pdf = await pdfDownload;
    const project = fs.readdirSync(path.join(workspaceHome, "projects"))[0];
    expect(
        fs
            .readFileSync(await pdf.path())
            .equals(
                fs.readFileSync(
                    path.join(workspaceHome, "projects", project, "build/main.pdf"),
                ),
            ),
    ).toBe(true);
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    const popup = context.waitForEvent("page");
    await page
        .getByRole("button", { name: "Mở PDF ở cửa sổ riêng", exact: true })
        .click();
    const separate = await popup;
    await expect(separate.locator(".pdf-sheet")).toHaveCount(2, {
        timeout: 30000,
    });
    expect(new URL(separate.url()).origin).toBe(new URL(url).origin);
    await separate.close();
    await page.getByRole("button", { name: "Bố cục", exact: true }).click();
    await page
        .getByRole("button", { name: "Chia đôi màn hình", exact: true })
        .click();
    await page.getByRole("tab", { name: "Word preview", exact: true }).click();
    await expect(page.locator('[data-docx-ready="true"]')).toBeVisible({
        timeout: 60000,
    });
    await page
        .locator(".superdoc-line")
        .filter({ hasText: "LOCAL" })
        .first()
        .click();
    await page.keyboard.press("Home");
    await page.keyboard.type("WEB_EDIT ");
    await expect(page.locator(".native-word-host")).toContainText("WEB_EDIT");
    await page.locator(".download-options > button").click();
    const wordDownload = page.waitForEvent("download");
    await page
        .getByRole("menuitem", { name: "Export Word (.docx)", exact: true })
        .click();
    const word = await wordDownload;
    const draftRoot = path.join(workspaceHome, "projects", project, ".overleaf-docx");
    const metadata = JSON.parse(
        fs.readFileSync(draftRoot + "/current.json", "utf8"),
    );
    expect(metadata.edited).toBe(true);
    expect(
        fs
            .readFileSync(await word.path())
            .equals(
                fs.readFileSync(draftRoot + "/" + metadata.revision + ".docx"),
            ),
    ).toBe(true);
    await page.screenshot({ path: home + "/local-web-word.png" });
    await page
        .locator(".file-sidebar")
        .getByRole("button", { name: "Tải lên", exact: true })
        .click();
    const upload = page.getByRole("dialog");
    await upload
        .locator("input[type=file]")
        .first()
        .setInputFiles(home + "/fixture.png");
    await upload.locator("footer .primary").click();
    await expect(upload).toHaveCount(0);
    const imagePopup = context.waitForEvent("page");
    await page
        .locator(".file-sidebar")
        .getByRole("button", { name: "fixture.png", exact: true })
        .click();
    const image = await imagePopup;
    await expect(image.locator("img")).toBeVisible();
    await image.close();
    await page.getByRole("button", { name: "Hồ sơ", exact: true }).click();
    await page
        .getByRole("dialog")
        .getByLabel("Tải ảnh đại diện", { exact: true })
        .setInputFiles(home + "/fixture.png");
    await expect(page.locator(".profile-avatar img")).toBeVisible();
    await page
        .getByRole("dialog")
        .getByRole("button", { name: "Đóng", exact: true })
        .click();
    await page.reload();
    await expect(page.locator(".project-card-open").first()).toBeVisible();
    await page.locator(".project-manage").first().click();
    const manage = page.getByRole("dialog");
    await expect(manage.getByText("Thư mục cha mới", { exact: true })).toHaveCount(0);
    await expect(manage.getByText(/Dự án được lưu trên server/)).toBeVisible();
    await manage.getByRole("button", { name: "Đóng", exact: true }).click();
    await page.getByRole("button", { name: "Dự án mới", exact: true }).click();
    await page
        .getByRole("dialog")
        .locator('input[accept=".zip"]')
        .setInputFiles(home + "/browser-import.zip");
    await expect(page.locator(".cm-content")).toContainText("ZIP WEB IMPORT", {
        timeout: 30000,
    });
    await page.locator(".compile-button").click();
    await expect(page.locator(".pdf-sheet")).toHaveCount(1, { timeout: 30000 });
    expect(auth).toEqual([]);
    expect(errors).toEqual([]);
    await page.screenshot({ path: home + "/local-web-home.png" });
    console.log(
        "LOCAL_WEB_OK cookie bootstrap/reload; no native bridge; project/save; 2-page PDF and exact export; PDF tab; Word edit/export; upload/binary preview/avatar; ZIP import/compile",
    );
} catch (e) {
    console.error(stderr.slice(-4000));
    if (page) {
        console.error((await page.locator("body").innerText()).slice(-3000));
        await page.screenshot({ path: home + "/error.png" }).catch(() => {});
    }
    throw e;
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
