"use client";
import { useEffect, useState } from "react";
import { Leaf, ArrowRight, Eye, EyeOff, LoaderCircle } from "lucide-react";
import { action, errorText, translator, type Session } from "../lib/parity";
import { isServerStorage, json, getLaunchRoute } from "../lib/api";

export default function AuthView({
    initialEmail,
    language,
    onDone,
}: {
    initialEmail: string;
    language: string;
    onDone: (session: Session) => void | Promise<void>;
}) {
    const t = translator(language);
    const [signup, setSignup] = useState(false),
        [email, setEmail] = useState(initialEmail),
        [password, setPassword] = useState(""),
        [name, setName] = useState("");
    const [remember, setRemember] = useState(true),
        [show, setShow] = useState(false),
        [busy, setBusy] = useState(false),
        [error, setError] = useState("");
    const [googleEnabled, setGoogleEnabled] = useState(false);
    useEffect(() => {
        if (!isServerStorage()) return;
        void json<{ googleEnabled: boolean }>("/auth/config").then(value => setGoogleEnabled(value.googleEnabled)).catch(() => {});
        const reason = getLaunchRoute().auth_error;
        if (reason) {
            const messages: Record<string, string> = {
                google_not_configured: t("Đăng nhập Google chưa được quản trị viên cấu hình.", "Google sign-in has not been configured."),
                google_cancelled: t("Bạn đã hủy đăng nhập Google. Hãy thử lại hoặc dùng email.", "Google sign-in was cancelled. Try again or use email."),
                account_exists: t("Email này đã có tài khoản Latex. Hãy đăng nhập bằng mật khẩu.", "This email already has a Latex account. Sign in with your password."),
            };
            setError(messages[reason] || t("Không thể xác thực Google. Vui lòng thử lại.", "Google authentication failed. Please try again."));
            history.replaceState(null, "", location.pathname + location.search);
        }
    }, []);
    async function submit(event?: React.FormEvent, offline = false) {
        event?.preventDefault();
        setError("");
        setBusy(true);
        try {
            await onDone(
                await action<Session>(
                    offline ? "offline" : signup ? "register" : "login",
                    { name, email, password, remember },
                ),
            );
        } catch (error) {
            setError(errorText(error));
        } finally {
            setBusy(false);
        }
    }
    return (
        <section className="auth-screen">
            <div className="auth-intro">
                <div className="brand">
                    <span className="brand-mark">
                        <Leaf size={26} />
                    </span>
                    <strong>Latex</strong>
                </div>
                <h1>
                    {t(
                        "Một không gian cho mọi ý tưởng.",
                        "A space for every idea.",
                    )}
                </h1>
                <p>
                    {t(
                        "Soạn thảo LaTeX, biên dịch và làm việc với tài liệu của bạn.",
                        "Write LaTeX, compile and work with your documents.",
                    )}
                </p>
                <small>
                    {isServerStorage() ? t("Tài khoản và tài liệu được lưu trên server chạy ứng dụng.", "Accounts and documents are saved on the application server.") : t(
                        "Tài khoản và tài liệu được lưu local trên máy này.",
                        "Accounts and documents are stored locally on this computer.",
                    )}
                </small>
            </div>
            <div className="auth-card">
                <p className="eyebrow">Latex</p>
                <h2>
                    {signup
                        ? t("Tạo tài khoản", "Create an account")
                        : t("Chào mừng trở lại", "Welcome back")}
                </h2>
                {isServerStorage() && <div className="google-signin">
                    <button className="secondary google-button" disabled={busy || !googleEnabled} onClick={() => { location.assign("/api/auth/google/start"); }}>
                        <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.02v2.51h3.24c1.9-1.75 2.98-4.33 2.98-7.36ZM12 22c2.7 0 4.96-.9 6.61-2.41l-3.24-2.51c-.9.6-2.05.97-3.37.97-2.61 0-4.83-1.76-5.62-4.13H3.04v2.59A10 10 0 0 0 12 22ZM6.38 13.92a6 6 0 0 1 0-3.84V7.49H3.04a10 10 0 0 0 0 9.02l3.34-2.59ZM12 5.95c1.47 0 2.79.51 3.83 1.51l2.87-2.87A9.62 9.62 0 0 0 12 2a10 10 0 0 0-8.96 5.49l3.34 2.59A5.98 5.98 0 0 1 12 5.95Z"/></svg>
                        {t("Tiếp tục với Google", "Continue with Google")}
                    </button>
                    {!googleEnabled && <small>{t("Đăng nhập Google chưa được cấu hình. Bạn có thể đăng ký bằng email bên dưới.", "Google sign-in is not configured yet. You can register with email below.")}</small>}
                    <div className="auth-divider">{t("hoặc dùng email", "or use email")}</div>
                </div>}
                <form onSubmit={(event) => void submit(event)}>
                    {signup && (
                        <label>
                            {t("Tên hiển thị", "Display name")}
                            <input
                                required
                                maxLength={40}
                                value={name}
                                onChange={(event) =>
                                    setName(event.target.value)
                                }
                            />
                        </label>
                    )}
                    <label>
                        Email
                        <input
                            type="email"
                            autoComplete="username"
                            required
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                        />
                    </label>
                    <label>
                        {t("Mật khẩu", "Password")}
                        <div className="password-field">
                            <input
                                type={show ? "text" : "password"}
                                autoComplete={
                                    signup ? "new-password" : "current-password"
                                }
                                minLength={signup ? 8 : 6}
                                required
                                value={password}
                                onChange={(event) =>
                                    setPassword(event.target.value)
                                }
                            />
                            <button
                                type="button"
                                title={t(
                                    "Hiện/ẩn mật khẩu",
                                    "Show/hide password",
                                )}
                                onClick={() => setShow(!show)}
                            >
                                {show ? (
                                    <EyeOff size={17} />
                                ) : (
                                    <Eye size={17} />
                                )}
                            </button>
                        </div>
                    </label>
                    <label className="check-row">
                        <input
                            type="checkbox"
                            checked={remember}
                            onChange={(event) =>
                                setRemember(event.target.checked)
                            }
                        />
                        {t("Duy trì đăng nhập", "Keep me signed in")}
                    </label>
                    {error && (
                        <p className="form-error" role="alert">
                            {error}
                        </p>
                    )}
                    <button className="primary" disabled={busy}>
                        {busy ? (
                            <LoaderCircle size={16} className="spin" />
                        ) : (
                            <ArrowRight size={16} />
                        )}
                        {signup
                            ? t("Đăng ký", "Register")
                            : t("Đăng nhập", "Sign in")}
                    </button>
                </form>
                <p>
                    {signup
                        ? t("Đã có tài khoản?", "Already registered?")
                        : t("Chưa có tài khoản?", "New here?")}{" "}
                    <button
                        onClick={() => {
                            setSignup(!signup);
                            setError("");
                        }}
                    >
                        {signup
                            ? t("Đăng nhập", "Sign in")
                            : t("Đăng ký", "Register")}
                    </button>
                </p>
                {!isServerStorage() && <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => void submit(undefined, true)}
                >
                    {isServerStorage() ? t("Tiếp tục không đăng nhập", "Continue without signing in") : t("Tiếp tục ngoại tuyến", "Continue offline")}
                </button>}
            </div>
        </section>
    );
}
