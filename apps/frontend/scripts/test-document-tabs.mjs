import { chromium, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

const repo = path.resolve("../.."),
    python = path.resolve(process.argv[2]),
    home = path.resolve(process.argv[3]);
if (fs.existsSync(home)) throw Error("Use a new test home");
const seed = spawnSync(
    python,
    [
        "-X",
        "utf8",
        "-c",
        `
import sys
from pathlib import Path
from latex_backend.storage.store import Store
from latex_backend.documents.ielts import IeltsManager
s=Store(Path(sys.argv[1]))
p=s.create('Multiple document tabs',language='en')
s.save(p['id'],'main.tex',r'\\documentclass{article}\\begin{document}MAIN DOCUMENT ORIGINAL\\end{document}')
IeltsManager(s).generate_report_tex(p['id'],{})
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
        path.join(repo, "apps/backend/tests/serve_ui.py"),
        home,
        "--existing",
    ],
    { cwd: repo, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] },
);
let browser,
    page,
    stderr = "";
server.stderr.on("data", (chunk) => (stderr += chunk));
try {
    for (let i = 0; i < 200 && !fs.existsSync(home + "/ready.json"); i++) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise((r) => setTimeout(r, 100));
    }
    const { url } = JSON.parse(fs.readFileSync(home + "/ready.json", "utf8"));
    browser = await chromium.launch({
        channel: process.env.OVERLEAF_TEST_BROWSER || "msedge",
        headless: true,
    });
    page = await browser.newPage({
        viewport: { width: 1700, height: 1050 },
    });
    const errors = [];
    page.on("pageerror", (e) => errors.push(String(e)));
    await page.goto(url);
    await page
        .getByRole("button", { name: "Tiếp tục ngoại tuyến", exact: true })
        .click();
    await page.locator(".project-card-open").first().click();
    const sources = page.locator(".source-tabs"),
        outputs = page.locator(".output-tabs");
    await expect(
        page.getByRole("combobox", { name: "Tệp biên dịch", exact: true }),
    ).toHaveValue("main.tex");
    await page.locator(".compile-button").click();
    await expect(
        outputs.getByRole("tab", { name: "main.pdf", exact: true }),
    ).toBeVisible({ timeout: 30000 });
    await expect(page.locator(".compile-button")).toBeEnabled();
    await page
        .locator(".file-sidebar")
        .getByText("ielts_report.tex", { exact: true })
        .click();
    await expect(
        sources.getByRole("tab", { name: "ielts_report.tex", exact: true }),
    ).toHaveAttribute("aria-selected", "true");
    await expect(
        page.getByRole("combobox", { name: "Tệp biên dịch", exact: true }),
    ).toHaveValue("ielts_report.tex");
    await page.locator(".compile-button").click();
    await expect(
        outputs.getByRole("tab", { name: "ielts_report.pdf", exact: true }),
    ).toBeVisible({ timeout: 30000 });
    await expect(page.locator(".compile-button")).toBeEnabled();
    await page.getByRole("tab", { name: "Word preview", exact: true }).click();
    await expect(page.locator('[data-docx-ready="true"]')).toBeVisible({
        timeout: 60000,
    });
    await expect(page.locator(".native-word-host")).toContainText("IELTS");
    await page
        .locator(".superdoc-text-run")
        .filter({ hasText: "IELTS" })
        .first()
        .click();
    await page.keyboard.press("Home");
    await page.keyboard.type("REPORT_WORD_EDIT ");
    await expect(page.locator(".native-word-host")).toContainText(
        "REPORT_WORD_EDIT",
    );
    await outputs.getByRole("tab", { name: "main.pdf", exact: true }).click();
    await expect(
        sources.getByRole("tab", { name: "main.tex", exact: true }),
    ).toHaveAttribute("aria-selected", "true");
    await expect(page.locator(".cm-content")).toContainText(
        "MAIN DOCUMENT ORIGINAL",
    );
    await page.getByRole("tab", { name: "Word preview", exact: true }).click();
    await expect(page.locator('[data-docx-ready="true"]')).toBeVisible({
        timeout: 60000,
    });
    await expect(page.locator(".native-word-host")).toContainText(
        "MAIN DOCUMENT ORIGINAL",
    );
    await expect(page.locator(".native-word-host")).not.toContainText(
        "REPORT_WORD_EDIT",
    );
    await outputs
        .getByRole("tab", { name: "ielts_report.docx", exact: true })
        .click();
    await expect(page.locator('[data-docx-ready="true"]')).toBeVisible({
        timeout: 30000,
    });
    await expect(page.locator(".native-word-host")).toContainText(
        "REPORT_WORD_EDIT",
    );
    await page.locator(".download-options > button").click();
    const pending = page.waitForEvent("download");
    await page
        .getByRole("menuitem", { name: "Export Word (.docx)", exact: true })
        .click();
    const download = await pending;
    expect(download.suggestedFilename()).toBe("ielts_report.docx");
    await download.saveAs(home + "/edited-report.docx");
    const project = fs.readdirSync(home + "/projects")[0];
    const wordFolder = path.join(
        home,
        "projects",
        project,
        ".overleaf-docx-" +
            crypto
                .createHash("sha256")
                .update("ielts_report.tex")
                .digest("hex")
                .slice(0, 16),
    );
    const metadata = JSON.parse(
        fs.readFileSync(wordFolder + "/current.json", "utf8"),
    );
    expect(
        fs
            .readFileSync(home + "/edited-report.docx")
            .equals(
                fs.readFileSync(wordFolder + "/" + metadata.revision + ".docx"),
            ),
    ).toBe(true);
    await page.screenshot({ path: home + "/document-tabs-word.png" });
    await outputs.getByRole("tab", { name: "main.pdf", exact: true }).click();
    await sources
        .getByRole("tab", { name: "ielts_report.tex", exact: true })
        .click();
    await expect(page.locator(".cm-content")).toContainText(
        "IELTS WRITING EVALUATION REPORT",
    );
    const old = await page.locator(".cm-content").innerText();
    await page.locator(".cm-content").fill(old + "\n% SOURCE_TAB_SAVED");
    await sources.getByRole("tab", { name: "main.tex", exact: true }).click();
    await expect(page.locator(".cm-content")).toContainText(
        "MAIN DOCUMENT ORIGINAL",
    );
    await sources
        .getByRole("tab", { name: "ielts_report.tex", exact: true })
        .click();
    await expect(page.locator(".cm-content")).toContainText("SOURCE_TAB_SAVED");
    await page.screenshot({ path: home + "/document-tabs.png" });
    await sources
        .getByRole("button", { name: "Đóng main.tex", exact: true })
        .click();
    await expect(sources.getByRole("tab")).toHaveCount(1);
    await outputs
        .getByRole("button", {
            name: "Đóng bản xem ielts_report.tex pdf",
            exact: true,
        })
        .click();
    await expect(
        outputs.getByRole("tab", { name: "ielts_report.pdf", exact: true }),
    ).toHaveCount(0);
    await expect(page.locator('[data-docx-ready="true"]')).toBeVisible({
        timeout: 30000,
    });
    await expect(page.locator(".native-word-host")).toContainText(
        "MAIN DOCUMENT ORIGINAL",
    );
    expect(errors).toEqual([]);
    console.log(
        "DOCUMENT_TABS_OK main/report compiled separately; PDF and editable Word tabs retained; export includes correct root edits; source switching saves text",
    );
} catch (e) {
    console.error(stderr.slice(-4000));
    if (page) {
        console.error((await page.locator("body").innerText()).slice(-3500));
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
