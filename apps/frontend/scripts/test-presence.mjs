import { chromium, expect as baseExpect } from '@playwright/test';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect = baseExpect.configure({ timeout: 20000 });
const repo = path.resolve('../..'), python = path.resolve(process.argv[2]), home = path.resolve(process.argv[3]);
if (fs.existsSync(home)) throw Error('Use a fresh test home');
fs.mkdirSync(home, { recursive: true });
// Explicit test double, never use real accounts or modify the user's Atlas collections.
const factory = process.env.LATEX_TEST_WEB_FACTORY || path.join(repo, 'apps/backend/tests/run_mongo_web.py');
const server = spawn(python, [factory, '--home', home, '--port', '0', '--no-browser'], { cwd: repo, windowsHide: true });
let stdout = '', stderr = '', browser, chrome;
const frames = [];
async function sourceText(page) {
    return page.locator('.cm-content').evaluate(node => {
        const copy = node.cloneNode(true);
        copy.querySelectorAll('.remote-cursor').forEach(n => n.remove());
        return [...copy.querySelectorAll('.cm-line')].map(n => n.textContent).join('\n');
    });
}

server.stdout.on('data', v => stdout += v); server.stderr.on('data', v => stderr += v);
try {
    for (let i = 0; i < 300 && !stdout.includes('OVERLEAF_WEB_READY '); i++) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise(r => setTimeout(r, 100));
    }
    const ready = stdout.split(/\r?\n/).find(v => v.startsWith('OVERLEAF_WEB_READY '));
    if (!ready) throw Error(stderr);
    const { url } = JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const owner = await browser.newContext({ viewport: { width: 1700, height: 1000 } });
    chrome = await chromium.launch({ channel: 'chrome', headless: true });
    const member = await chrome.newContext({ viewport: { width: 1700, height: 1000 } });
    const reader = await browser.newContext();
    const errors = [];
    const a = await owner.newPage(), b = await member.newPage(), r = await reader.newPage();
    // Test-only socket registry lets us force a genuine disconnect deterministically.
    await b.addInitScript(() => {
        const Native = window.WebSocket;
        window.__testLiveSockets = [];
        window.WebSocket = class extends Native {
            constructor(...args) { super(...args); window.__testLiveSockets.push(this); }
        };
    });
    for (const [label,page] of [['owner',a],['member',b],['reader',r]]) {
        page.on('pageerror', e => errors.push(String(e)));
        page.on('websocket', ws => {
            for (const direction of ['framesent','framereceived']) ws.on(direction, frame => {
                try { const data = JSON.parse(String(frame.payload)); if (data.type?.startsWith('sync-') && frames.length < 500) frames.push({label,direction,data}); } catch {}
            });
        });
    }
    async function signup(ctx, email, name) {
        expect((await ctx.request.post(url + 'api/actions/register', { data: { name, email, password: 'test-password' } })).ok()).toBe(true);
        expect((await ctx.request.post(url + 'api/auth/onboarding', { data: { name, role: 'student' } })).ok()).toBe(true);
    }
    await signup(owner, 'owner@example.com', 'Real Owner');
    const project = await (await owner.request.post(url + 'api/projects', { data: { title: 'Presence test' } })).json();
    const route = url + `api/projects/${project.id}/sharing`;
    const link = (await (await owner.request.patch(route, { data: { mode: 'link', permission: 'editor' } })).json()).linkToken;
    await signup(member, 'member@example.com', 'Actual Editor');
    const avatar = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aK1sAAAAASUVORK5CYII=', 'base64');
    expect((await member.request.post(url + 'api/avatar', { data: avatar, headers: { 'Content-Type': 'image/png' } })).ok()).toBe(true);
    expect((await member.request.post(url + 'api/shares/accept', { data: { token: link } })).ok()).toBe(true);
    await a.goto(url); await a.locator('.project-card-open').click();
    await b.goto(url); await b.locator('.project-card-open').click();
    await expect(a.locator('.presence-person')).toHaveCount(1);
    await expect(b.locator('.presence-person')).toHaveCount(1);
    await expect.poll(() => a.locator('.presence-person img').evaluateAll(nodes => nodes.filter(n => n.naturalWidth > 0).length)).toBe(1);
    await b.bringToFront();
    await b.locator('.cm-content').click();
    await b.keyboard.press('Control+Home'); await b.keyboard.press('ArrowDown');
    await b.keyboard.press('Home');
    await b.keyboard.down('Shift'); for (let i = 0; i < 8; i++) await b.keyboard.press('ArrowRight'); await b.keyboard.up('Shift');
    await expect(a.locator('.remote-cursor-label')).toHaveText('Actual Editor');
    await expect(a.locator('.remote-selection')).toHaveCount(0);
    await expect(b.locator('.cm-editor')).toHaveAttribute('data-local-selection','true');
    // Switching windows must keep the other editor's last cursor.
    await a.bringToFront(); await a.locator('.cm-content').click();
    await expect(b.locator('.remote-cursor-label')).toHaveText('Real Owner');
    await expect(a.locator('.remote-cursor-label')).toHaveText('Actual Editor');
    await a.screenshot({ path: path.join(home, 'named-cursor-selection.png') });
    // Moving to a different file retains presence, but never draws in the wrong editor.
    await b.getByRole('tab', { name: /main.tex/ }).first().click();
    await b.locator('.file-tree button[title="preamble.tex"]').click();
    await expect(a.locator('.remote-cursor')).toHaveCount(0);
    await expect(a.locator('.presence-person').filter({ has: a.locator('span', { hasText: 'AE' }) })).toHaveAttribute('title', /preamble.tex/);
    await b.getByRole('tab', { name: /main.tex/ }).first().click();
    await b.locator('.cm-content').click();
    await expect(a.locator('.remote-cursor-label')).toHaveText('Actual Editor');
    // Changes are persisted and broadcast immediately without clicking Save.
    await a.bringToFront(); await a.locator('.cm-content').click();
    await a.keyboard.press('Control+End'); await a.keyboard.insertText('\nSYNC_DELETE_n');
    await expect(b.locator('.cm-content')).toContainText('SYNC_DELETE_n');
    await b.bringToFront(); await b.locator('.cm-content').click();
    await b.keyboard.press('Control+End'); await b.keyboard.press('Backspace');
    await expect(a.locator('.cm-content')).toContainText('SYNC_DELETE_');
    await expect(a.locator('.cm-content')).not.toContainText('SYNC_DELETE_n');
    await expect.poll(async () => (await sourceText(a)) === (await sourceText(b))).toBe(true);
    // Sustained individual keystrokes exercise batching and the unconfirmed OT
    // queue, unlike insertText(), which creates a single large transaction.
    const burst = ' BURST_0123456789'.repeat(8);
    await b.locator('.cm-content').click(); await b.keyboard.press('Control+End');
    await b.keyboard.type(burst, { delay: 3 });
    await expect.poll(() => sourceText(a)).toContain(burst);
    await expect.poll(async () => (await sourceText(a)) === (await sourceText(b))).toBe(true);
    // Both processes submit local edits before seeing the other's acknowledgement.
    await a.locator('.cm-content').click(); await a.keyboard.press('Control+End');
    await b.locator('.cm-content').click(); await b.keyboard.press('Control+End');
    await Promise.all([a.keyboard.insertText(' OWNER_SIMULTANEOUS '), b.keyboard.insertText(' MEMBER_SIMULTANEOUS ')]);
    for (const page of [a,b]) {
        await expect(page.locator('.cm-content')).toContainText('OWNER_SIMULTANEOUS');
        await expect(page.locator('.cm-content')).toContainText('MEMBER_SIMULTANEOUS');
    }
    await expect.poll(async () => (await sourceText(a)) === (await sourceText(b))).toBe(true);
    await expect.poll(async () => {
        const response = await owner.request.get(url + `api/projects/${project.id}/file?path=main.tex`);
        return (await response.json()).content;
    }).toContain('MEMBER_SIMULTANEOUS');
    // Undo must affect this user's action and preserve the other user's insert.
    await a.locator('.cm-content').click(); await a.keyboard.press('Control+z');
    await expect(b.locator('.cm-content')).not.toContainText('OWNER_SIMULTANEOUS');
    await expect(b.locator('.cm-content')).toContainText('MEMBER_SIMULTANEOUS');
    await a.keyboard.press('Control+y');
    await expect(b.locator('.cm-content')).toContainText('OWNER_SIMULTANEOUS');
    // Keep local edits across a disconnect while the other editor continues writing.
    await member.setOffline(true);
    await b.evaluate(() => window.__testLiveSockets.forEach(ws => ws.close()));
    await expect(a.locator('.presence-person')).toHaveCount(0);
    await b.locator('.cm-content').click(); await b.keyboard.press('Control+End');
    await b.keyboard.insertText(' OFFLINE_PENDING ');
    await a.locator('.cm-content').click(); await a.keyboard.press('Control+End');
    await a.keyboard.insertText(' ONLINE_DURING_DISCONNECT ');
    await member.setOffline(false);
    for (const page of [a,b]) {
        await expect(page.locator('.cm-content')).toContainText('OFFLINE_PENDING');
        await expect(page.locator('.cm-content')).toContainText('ONLINE_DURING_DISCONNECT');
    }
    await expect.poll(async () => (await sourceText(a)) === (await sourceText(b))).toBe(true);
    await a.screenshot({ path: path.join(home, 'live-edits-synchronized.png') });
    // Viewer never connects or advertises an editing cursor/avatar.
    const invitation = await (await owner.request.post(route + '/invitations', { data: { emails: ['reader@example.com'], permission: 'viewer' } })).json();
    await signup(reader, 'reader@example.com', 'Only Reader');
    const invite = invitation.members.find(v => v.email === 'reader@example.com');
    expect((await reader.request.post(url + 'api/shares/accept', { data: { token: invite.inviteToken } })).ok()).toBe(true);
    await r.goto(url); await r.locator('.project-card-open').click();
    await expect(r.locator('.cm-content')).toHaveAttribute('aria-readonly', 'true');
    await expect(r.locator('.presence-avatars')).toHaveCount(0);
    await expect(r.locator('.cm-content')).toContainText('MEMBER_SIMULTANEOUS');
    await expect(a.locator('.presence-person')).toHaveCount(1);
    await b.reload();
    await b.locator('.project-card-open').click();
    await expect(b.locator('.cm-content')).toContainText('MEMBER_SIMULTANEOUS');
    await expect(a.locator('.presence-person')).toHaveCount(1);
    await b.bringToFront(); await b.locator('.cm-content').click();
    await expect(a.locator('.remote-cursor-label')).toHaveText('Actual Editor');
    await b.close();
    await expect(a.locator('.presence-person')).toHaveCount(0);
    await expect(a.locator('.remote-cursor')).toHaveCount(0);
    expect(errors).toEqual([]);
    fs.writeFileSync(path.join(home, 'report.json'), JSON.stringify({ passed: true, checks: ['separate Edge and Chrome processes', 'two authenticated accounts', 'bidirectional cursor persists across window blur', 'real names', 'avatars', 'selection and cursor', 'deletion syncs without Save', 'sustained individual keystrokes converge', 'simultaneous insertions converge', 'live changes persisted', 'per-user undo and redo', 'pending edits survive disconnect with concurrent remote edit', 'viewer excluded', 'disconnect cleanup'], errors }, null, 2));
    console.log('Presence browser regression passed: ' + home);
} catch (error) {
    fs.writeFileSync(path.join(home, 'server.log'), stderr);
    fs.writeFileSync(path.join(home, 'frames.json'), JSON.stringify(frames,null,2));
    if (browser) for (const [i, page] of browser.contexts().flatMap(c => c.pages()).entries()) await page.screenshot({ path: path.join(home, `failure-${i}.png`) }).catch(() => {});
    throw error;
} finally { if (browser) await browser.close(); if (chrome) await chrome.close(); server.kill(); }
