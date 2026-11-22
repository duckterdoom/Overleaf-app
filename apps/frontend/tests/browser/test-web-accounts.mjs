import { chromium, expect } from '@playwright/test';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const repo = path.resolve('../..');
const python = path.resolve(process.argv[2]);
const home = path.resolve(process.argv[3]);
if (fs.existsSync(home)) throw Error('Use a fresh test home');
fs.mkdirSync(home, { recursive: true });
const factory = process.env.LATEX_TEST_WEB_FACTORY ? [path.resolve(process.env.LATEX_TEST_WEB_FACTORY)] : ['-m', 'latex_backend.entrypoints.web'];
const server = spawn(python, [...factory, '--home', home, '--port', '0', '--no-browser'], { cwd: repo, windowsHide: true });
let output = '', stderr = '', browser;
server.stdout.on('data', value => output += value);
server.stderr.on('data', value => stderr += value);
try {
    for (let i = 0; i < 300 && !output.includes('OVERLEAF_WEB_READY '); i++) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    const line = output.split(/\r?\n/).find(value => value.startsWith('OVERLEAF_WEB_READY '));
    if (!line) throw Error('Server failed to start: ' + stderr);
    const { url } = JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const studentContext = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
    const tutorContext = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
    const student = await studentContext.newPage(), tutor = await tutorContext.newPage();
    const errors = [];
    student.on('pageerror', e => errors.push(String(e)));
    tutor.on('pageerror', e => errors.push(String(e)));
    async function signup(page, email, name) {
        await page.goto(url);
        await expect(page.getByRole('button', { name: 'Tiếp tục với Google', exact: true })).toBeDisabled();
        await expect(page.getByRole('button', { name: 'Tiếp tục không đăng nhập', exact: true })).toHaveCount(0);
        await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
        await page.getByLabel('Tên hiển thị', { exact: true }).fill(name);
        await page.getByLabel('Email', { exact: true }).fill(email);
        await page.getByLabel('Mật khẩu', { exact: true }).fill('browser-password');
        await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
        await expect(page.getByRole('heading', { name: 'Bạn sẽ sử dụng Latex với vai trò nào?' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Vào không gian của tôi', exact: true })).toBeDisabled();
    }
    await signup(student, 'student@example.com', 'Browser Student');
    await student.screenshot({ path: path.join(home, 'role-selection.png') });
    await student.reload();
    await expect(student.getByRole('heading', { name: 'Bạn sẽ sử dụng Latex với vai trò nào?' })).toBeVisible();
    await student.getByRole('radio', { name: /Student/ }).check();
    await student.getByRole('button', { name: 'Vào không gian của tôi', exact: true }).click();
    await expect(student.getByRole('heading', { name: /Không gian học viên/ })).toBeVisible();
    const privateProject = await student.evaluate(async () => {
        const response = await fetch('/api/projects', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: 'PRIVATE STUDENT DOCUMENT' }) });
        if (!response.ok) throw Error(await response.text());
        return response.json();
    });
    await signup(tutor, 'tutor@example.com', 'Browser Tutor');
    await tutor.setViewportSize({ width: 390, height: 844 });
    await tutor.screenshot({ path: path.join(home, 'role-selection-mobile.png') });
    expect(await tutor.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect((await tutor.locator('.role-card').boundingBox()).width).toBeGreaterThan(340);
    await tutor.getByRole('radio', { name: /Tutor/ }).check();
    await tutor.getByRole('button', { name: 'Vào không gian của tôi', exact: true }).click();
    await expect(tutor.getByRole('heading', { name: /Không gian Tutor/ })).toBeVisible();
    const denied = await tutor.evaluate(async id => (await fetch(`/api/projects/${id}/files`)).status, privateProject.id);
    expect(denied).toBe(404);
    await student.reload();
    await expect(student.getByRole('heading', { name: /Không gian học viên/ })).toBeVisible();
    await expect(student.getByText('PRIVATE STUDENT DOCUMENT', { exact: true })).toBeVisible();
    await tutor.reload();
    await expect(tutor.getByText('PRIVATE STUDENT DOCUMENT', { exact: true })).toHaveCount(0);
    const cookies = await studentContext.cookies();
    expect(cookies.find(c => c.name === 'latex_account')?.httpOnly).toBe(true);
    await tutor.evaluate(async () => fetch('/api/actions/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }));
    await tutor.reload();
    await expect(tutor.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await tutor.getByLabel('Email', { exact: true }).fill('tutor@example.com');
    await tutor.getByLabel('Mật khẩu', { exact: true }).fill('browser-password');
    await tutor.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(tutor.getByRole('heading', { name: /Không gian Tutor/ })).toBeVisible();
    await tutor.setViewportSize({ width: 1500, height: 1000 });
    await tutor.screenshot({ path: path.join(home, 'tutor-workspace.png') });
    await student.screenshot({ path: path.join(home, 'student-workspace.png') });
    await tutor.evaluate(async () => fetch('/api/actions/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }));
    await tutor.goto(url + '#auth_error=google_cancelled');
    await tutor.reload();
    await expect(tutor.locator('.auth-card .form-error')).toContainText('hủy đăng nhập Google');
    expect(errors).toEqual([]);
    console.log('WEB_ACCOUNTS_OK signup; onboarding/reload; student/tutor; private workspaces; logout/login; Google config/error; mobile');
} finally {
    await browser?.close();
    server.kill('SIGINT');
}
