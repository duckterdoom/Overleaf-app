"use client";
import { useState } from "react";
import { GraduationCap, Presentation, ArrowRight, Leaf, Check, LoaderCircle } from "lucide-react";
import { json } from "../lib/api";
import { errorText, translator, type Session } from "../lib/parity";

export default function RoleSelection({ session, language, onDone, onLogout }: {
    session: Session; language: string; onDone: (value: Session) => void | Promise<void>; onLogout: () => void | Promise<void>;
}) {
    const t = translator(language);
    const [role, setRole] = useState<"student" | "tutor" | null>(null);
    const [name, setName] = useState(session.name);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    async function complete(event: React.FormEvent) {
        event.preventDefault();
        if (!role || busy) return;
        setBusy(true); setError("");
        try {
            const value = await json<Session>("/auth/onboarding", { method: "POST", body: JSON.stringify({ role, name }) });
            await onDone(value);
        } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
    }
    return <section className="auth-screen role-screen">
        <div className="auth-intro"><div className="brand"><span className="brand-mark"><Leaf size={26} /></span><strong>Latex</strong></div>
            <p className="eyebrow">{t("THIẾT LẬP TÀI KHOẢN", "ACCOUNT SETUP")}</p>
            <h1>{t("Không gian phù hợp với bạn.", "A workspace that fits you.")}</h1>
            <p>{t("Chọn cách bạn sử dụng Latex để bắt đầu viết, học tập hoặc hướng dẫn học viên.", "Choose how you use Latex to start writing, learning or tutoring.")}</p>
            <small>{t("Lựa chọn được lưu vào tài khoản. Đóng trang lúc này, bạn có thể tiếp tục khi đăng nhập lại.", "Your choice is saved to your account. You can return to this step after signing in again.")}</small>
        </div>
        <div className="auth-card role-card"><p className="eyebrow">{t("BƯỚC CUỐI CÙNG", "ONE LAST STEP")}</p>
            <h2>{t("Bạn sẽ sử dụng Latex với vai trò nào?", "How will you use Latex?")}</h2>
            <p className="role-account">{session.email}</p>
            <form onSubmit={complete}>
                <label>{t("Tên hiển thị", "Display name")}<input value={name} required maxLength={40} autoComplete="name" onChange={e => setName(e.target.value)} disabled={busy} /></label>
                <fieldset className="role-options" disabled={busy}><legend>{t("Chọn vai trò chính", "Choose your primary role")}</legend>
                    {(["student", "tutor"] as const).map(value => <label key={value} className={`role-option ${role === value ? "selected" : ""}`}>
                        <input type="radio" name="primary-role" value={value} checked={role === value} onChange={() => setRole(value)} required />
                        <span className="role-icon">{value === "student" ? <GraduationCap size={27} /> : <Presentation size={27} />}</span>
                        <span><strong>{value === "student" ? t("Student — Học viên", "Student") : t("Tutor — Giảng viên", "Tutor")}</strong>
                        <small>{value === "student" ? t("Viết và luyện tập IELTS, chuẩn bị bài nộp và nhận phản hồi.", "Write and practise IELTS, prepare submissions and receive feedback.") : t("Soạn tài liệu giảng dạy và làm việc với bài viết của học viên được chia sẻ với bạn.", "Prepare teaching materials and work with student writing shared with you.")}</small></span>
                        {role === value && <Check size={20} />}
                    </label>)}
                </fieldset>
                <p className="role-privacy">{t("Vai trò Tutor không cấp quyền quản trị hoặc quyền truy cập tài liệu của người khác.", "The Tutor role does not grant administrator privileges or access to other users’ documents.")}</p>
                {error && <p className="form-error" role="alert">{error}</p>}
                <button className="primary" disabled={!role || !name.trim() || busy}>{busy ? <LoaderCircle size={17} className="spin" /> : <ArrowRight size={17} />}{t("Vào không gian của tôi", "Enter my workspace")}</button>
                <button type="button" className="secondary" disabled={busy} onClick={() => void onLogout()}>{t("Đăng xuất", "Sign out")}</button>
            </form>
        </div>
    </section>;
}
