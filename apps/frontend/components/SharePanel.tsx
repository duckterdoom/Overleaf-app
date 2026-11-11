"use client";
import { useEffect, useRef, useState } from "react";
import { Check, Copy, Globe, Link2, LoaderCircle, LockKeyhole, MailPlus, ShieldCheck, UsersRound, X } from "lucide-react";
import { json, type Project } from "../lib/api";
import { errorText, translator } from "../lib/parity";

type Permission = "viewer" | "editor";
type Sharing = {
    permission: "owner" | Permission;
    owner: { name: string; email: string };
    mode: "private" | "link";
    linkPermission: Permission;
    linkToken: string | null;
    members: { id: string; email: string; permission: Permission; status: "pending" | "accepted"; inviteToken: string }[];
};

export default function SharePanel({ project, language, onClose }: { project: Project; language: string; onClose: () => void }) {
    const t = translator(language), route = `/projects/${project.id}/sharing`;
    const [data, setData] = useState<Sharing | null>(null), [emails, setEmails] = useState("");
    const [permission, setPermission] = useState<Permission>("viewer"), [busy, setBusy] = useState(false);
    const [error, setError] = useState(""), [status, setStatus] = useState("");
    const dialog = useRef<HTMLElement>(null), members = useRef<HTMLDivElement>(null);
    useEffect(() => {
        let active = true;
        const opener = document.activeElement as HTMLElement | null;
        dialog.current?.focus();
        void json<Sharing>(route).then(value => { if (active) setData(value); }).catch(e => { if (active) setError(errorText(e)); });
        return () => { active = false; opener?.focus(); };
    }, [route]);
    useEffect(() => {
        // Disabling a focused select during a save can move focus to the body.
        // Escape must still close the dialog instead of reaching editor shortcuts.
        if (!busy && !dialog.current?.contains(document.activeElement)) dialog.current?.focus();
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key !== "Escape" || busy) return;
            event.preventDefault(); event.stopImmediatePropagation(); onClose();
        };
        window.addEventListener("keydown", closeOnEscape, true);
        return () => window.removeEventListener("keydown", closeOnEscape, true);
    }, [busy, onClose]);
    const owner = data?.permission === "owner";
    const sharingLink = (token: string) => `${window.location.origin}/#share=${encodeURIComponent(token)}`;
    async function copy(token: string) {
        setError("");
        try { await navigator.clipboard.writeText(sharingLink(token)); setStatus(t("Đã sao chép liên kết.", "Link copied.")); }
        catch { setError(t("Không thể sao chép tự động. Hãy sao chép liên kết trong ô bên dưới.", "Could not copy automatically. Copy the link from the field below.")); }
    }
    async function update(path: string, method: string, body?: object) {
        setBusy(true); setError(""); setStatus("");
        try {
            const value = await json<Sharing>(path, { method, ...(body ? { body: JSON.stringify(body) } : {}) });
            setData(value); setStatus(t("Đã cập nhật quyền truy cập.", "Access updated."));
            return true;
        } catch (e) { setError(errorText(e)); return false; } finally { setBusy(false); }
    }
    return <div className="modal-backdrop" onKeyDown={event => {
        if (event.key === "Escape" && !busy) onClose();
        if (event.key === "Tab") {
            const controls = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)');
            if (!controls?.length) return;
            const first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    }}>
        <section ref={dialog} tabIndex={-1} className="modal share-modal" role="dialog" aria-modal="true" aria-labelledby="share-title">
            <header><div className="share-heading"><span className="share-heading-icon"><UsersRound size={23} /></span><div><p>{t("CHIA SẺ DỰ ÁN", "SHARE PROJECT")}</p><h2 id="share-title">{project.title}</h2></div></div>
                <button disabled={busy} aria-label={t("Đóng chia sẻ", "Close sharing")} onClick={onClose}><X size={20} /></button></header>
            <div className="share-body">
                {!data && !error && <p className="share-loading"><LoaderCircle size={18} className="spin" />{t("Đang tải quyền truy cập…", "Loading access…")}</p>}
                {data && <>
                    {owner && <form className="share-invite" onSubmit={async event => {
                        event.preventDefault();
                        const list = emails.split(/[,;\n]+/).map(value => value.trim()).filter(Boolean);
                        if (await update(route + "/invitations", "POST", { emails: list, permission })) { setEmails(""); setStatus(t("Đã tạo lời mời. Sao chép liên kết lời mời bên cạnh email để gửi cho người nhận.", "Invitation created. Copy the invitation link beside the email and send it to the recipient.")); }
                    }}>
                        <label htmlFor="share-emails">{t("Mời người khác qua email", "Invite people by email")}</label>
                        <div className="share-invite-controls"><input id="share-emails" value={emails} onChange={e => setEmails(e.target.value)} placeholder={t("Nhập email, phân cách bằng dấu phẩy", "Enter emails, separated by commas")} disabled={busy} required maxLength={5000} />
                            <select aria-label={t("Quyền của lời mời", "Invitation permission")} value={permission} onChange={e => setPermission(e.target.value as Permission)} disabled={busy}><option value="viewer">{t("Chỉ xem", "Viewer")}</option><option value="editor">{t("Chỉnh sửa", "Editor")}</option></select>
                            <button className="primary" disabled={busy || !emails.trim()}><MailPlus size={16} />{t("Mời", "Invite")}</button></div>
                        <small>{t("Ứng dụng chưa gửi email tự động. Bạn sao chép và gửi liên kết lời mời; người nhận cần đăng nhập đúng email được mời.", "Email delivery is not enabled. Copy and send the invitation link; the recipient must sign in with the invited email.")}</small>
                    </form>}
                    <div className="share-section-heading"><h3>{t("Người có quyền truy cập", "People with access")}</h3>{owner && <button onClick={() => members.current?.scrollIntoView({ block: "nearest" })}>{t("Quản lý quyền", "Manage access")}</button>}</div>
                    <div className="share-members" ref={members}>
                        <div className="share-person"><span className="share-person-avatar">{data.owner.name.slice(0, 1).toUpperCase()}</span><div className="share-person-text"><strong>{data.owner.name}</strong><small>{data.owner.email}</small></div><span className="share-owner-label">{t("Chủ sở hữu", "Owner")}</span></div>
                        {data.members.map(member => <div className="share-person" key={member.id}><span className="share-person-avatar"><UsersRound size={17} /></span><div className="share-person-text"><strong>{member.email}</strong><small>{member.status === "pending" ? t("Đang chờ chấp nhận lời mời", "Invitation pending") : t("Đã tham gia", "Joined")}</small></div>
                            <select disabled={busy} aria-label={`${t("Quyền của", "Permission for")} ${member.email}`} value={member.permission} onChange={e => void update(route + "/members/" + member.id, "PATCH", { permission: e.target.value })}><option value="viewer">{t("Chỉ xem", "Viewer")}</option><option value="editor">{t("Chỉnh sửa", "Editor")}</option></select>
                            <button disabled={busy} title={t("Sao chép liên kết lời mời", "Copy invitation link")} aria-label={`${t("Sao chép lời mời cho", "Copy invitation for")} ${member.email}`} onClick={() => void copy(member.inviteToken)}><Copy size={16} /></button>
                            <button disabled={busy} title={t("Thu hồi quyền", "Revoke access")} aria-label={`${t("Thu hồi quyền của", "Revoke access for")} ${member.email}`} onClick={() => void update(route + "/members/" + member.id, "DELETE")}><X size={16} /></button>
                        </div>)}
                        {owner && !data.members.length && <p className="share-no-members">{t("Bạn chưa mời ai vào dự án này.", "No one has been invited yet.")}</p>}
                    </div>
                    <h3>{t("Quyền truy cập bằng liên kết", "General access")}</h3>
                    <div className={`share-general ${data.mode === "link" ? "link-enabled" : ""}`}>
                        <span className="share-access-icon">{data.mode === "private" ? <LockKeyhole size={23} /> : <Globe size={23} />}</span>
                        <div className="share-general-setting"><select disabled={!owner || busy} aria-label={t("Chế độ chia sẻ", "Sharing mode")} value={data.mode} onChange={e => void update(route, "PATCH", { mode: e.target.value, permission: data.linkPermission })}><option value="private">{t("Riêng tư — Chỉ người được mời", "Private — Only invited people")}</option><option value="link">{t("Bất kỳ ai có liên kết", "Anyone with the link")}</option></select>
                            <small>{data.mode === "private" ? t("Liên kết công khai đã tắt. Chỉ người được mời và chấp nhận lời mời mới mở được tài liệu.", "Link sharing is off. Only people who accept a private invitation can open this document.") : t("Người có liên kết cần đăng nhập Latex để truy cập với quyền bên cạnh.", "Anyone with this link can sign in to Latex and access the project with the selected permission.")}</small></div>
                        {data.mode === "link" && <select disabled={!owner || busy} aria-label={t("Quyền của liên kết", "Link permission")} value={data.linkPermission} onChange={e => void update(route, "PATCH", { mode: data.mode, permission: e.target.value })}><option value="viewer">{t("Chỉ xem", "Viewer")}</option><option value="editor">{t("Chỉnh sửa", "Editor")}</option></select>}
                    </div>
                    {owner && data.mode === "link" && data.linkToken && <div className="share-link-field"><input aria-label={t("Liên kết chia sẻ", "Sharing link")} value={sharingLink(data.linkToken)} readOnly onFocus={e => e.currentTarget.select()} /><button disabled={busy} onClick={() => void update(route, "PATCH", { mode: data.mode, permission: data.linkPermission, rotate: true })}>{t("Tạo liên kết mới", "Reset link")}</button></div>}
                    {!owner && <p className="share-notice">{t("Chỉ chủ sở hữu được mời thành viên và thay đổi chế độ chia sẻ.", "Only the owner can invite people and manage sharing.")}</p>}
                    <p className="share-notice"><ShieldCheck size={15} />{t("Quyền xem/chỉnh sửa được kiểm tra trên server. Đồng bộ thao tác gõ đồng thời sẽ được bổ sung sau.", "View/edit permissions are enforced on the server. Live simultaneous editing will be added later.")}</p>
                </>}
                {error && <p className="form-error" role="alert">{error}</p>}
                {status && <p className="share-status" role="status"><Check size={15} />{status}</p>}
            </div>
            <footer><button className="secondary" disabled={!owner || !data?.linkToken || busy} onClick={() => data?.linkToken && void copy(data.linkToken)}><Link2 size={16} />{t("Sao chép liên kết chia sẻ", "Copy sharing link")}</button><button className="primary" disabled={busy} onClick={onClose}>{t("Xong", "Done")}</button></footer>
        </section>
    </div>;
}
