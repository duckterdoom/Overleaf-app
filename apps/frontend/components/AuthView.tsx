"use client";
import { useState } from "react";
import { Leaf, ArrowRight, Eye, EyeOff, LoaderCircle } from "lucide-react";
import { action, errorText, translator, type Session } from "../lib/parity";

export default function AuthView({
    initialEmail,
    language,
    onDone,
}: {
    initialEmail: string;
    language: string;
    onDone: (session: Session) => void;
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
    async function submit(event?: React.FormEvent, offline = false) {
        event?.preventDefault();
        setError("");
        setBusy(true);
        try {
            onDone(
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
                        "Soạn thảo LaTeX, biên dịch trên máy và cộng tác với nhóm của bạn.",
                        "Write LaTeX, compile locally and collaborate with your team.",
                    )}
                </p>
                <small>
                    {t(
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
                                minLength={6}
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
                <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => void submit(undefined, true)}
                >
                    {t("Tiếp tục ngoại tuyến", "Continue offline")}
                </button>
            </div>
        </section>
    );
}
