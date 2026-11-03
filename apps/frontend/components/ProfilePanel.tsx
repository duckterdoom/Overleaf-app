"use client";
import { useEffect, useState } from "react";
import { X, User, Camera } from "lucide-react";
import { request } from "../lib/api";
import {
    action,
    native,
    errorText,
    translator,
    type Session,
} from "../lib/parity";

export default function ProfilePanel({
    session,
    language,
    onDone,
    onClose,
    onLogout,
}: {
    session: Session;
    language: string;
    onDone: (session: Session) => void;
    onClose: () => void;
    onLogout: () => void;
}) {
    const t = translator(language),
        [name, setName] = useState(session.name),
        [email, setEmail] = useState(session.email),
        [current, setCurrent] = useState(""),
        [password, setPassword] = useState("");
    const [image, setImage] = useState(""),
        [error, setError] = useState(""),
        [busy, setBusy] = useState(false),
        [refresh, setRefresh] = useState(0);
    useEffect(() => {
        let object = "",
            disposed = false;
        void request("/avatar")
            .then((response) => response.blob())
            .then((blob) => {
                if (blob.size && !disposed) {
                    object = URL.createObjectURL(blob);
                    setImage(object);
                } else setImage("");
            })
            .catch((error) => setError(errorText(error)));
        return () => {
            disposed = true;
            if (object) URL.revokeObjectURL(object);
        };
    }, [refresh]);
    async function save(event: React.FormEvent) {
        event.preventDefault();
        setBusy(true);
        setError("");
        try {
            const data: Record<string, string> = { name };
            if (!session.offline && email !== session.email) data.email = email;
            if (password) data.password = password;
            if (data.email || password) data.currentPassword = current;
            onDone(await action<Session>("profile", data));
            setPassword("");
            setCurrent("");
            onClose();
        } catch (error) {
            setError(errorText(error));
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="modal-backdrop">
            <section
                className="modal profile-modal"
                role="dialog"
                aria-modal="true"
                aria-label={t("Hồ sơ", "Profile")}
            >
                <header>
                    <h2>{t("Hồ sơ của bạn", "Your profile")}</h2>
                    <button title={t("Đóng", "Close")} onClick={onClose}>
                        <X size={20} />
                    </button>
                </header>
                <div className="profile-avatar">
                    {image ? (
                        <img src={image} alt={session.name} />
                    ) : (
                        <User size={42} />
                    )}
                    <button
                        title={t("Chọn ảnh", "Select picture")}
                        onClick={async () => {
                            try {
                                await native("profile_picture");
                                setRefresh((value) => value + 1);
                            } catch (error) {
                                setError(errorText(error));
                            }
                        }}
                    >
                        <Camera size={18} />
                    </button>
                </div>
                <div className="profile-photo-actions">
                    <label className="secondary">
                        {t("Tải ảnh", "Upload picture")}
                        <input
                            hidden
                            type="file"
                            accept="image/png,image/jpeg,image/gif"
                            onChange={async (event) => {
                                const file = event.target.files?.[0];
                                if (file)
                                    try {
                                        await request("/avatar", {
                                            method: "POST",
                                            headers: {
                                                "Content-Type": file.type,
                                            },
                                            body: file,
                                        });
                                        setRefresh((value) => value + 1);
                                    } catch (error) {
                                        setError(errorText(error));
                                    }
                            }}
                        />
                    </label>
                    <button
                        onClick={async () => {
                            await action("remove-avatar");
                            setRefresh((value) => value + 1);
                        }}
                    >
                        {t("Xóa ảnh", "Remove picture")}
                    </button>
                </div>
                <form onSubmit={(event) => void save(event)}>
                    <label>
                        {t("Tên hiển thị", "Display name")}
                        <input
                            required
                            maxLength={40}
                            value={name}
                            onChange={(event) => setName(event.target.value)}
                        />
                    </label>
                    {!session.offline && (
                        <>
                            <label>
                                Email
                                <input
                                    type="email"
                                    value={email}
                                    onChange={(event) =>
                                        setEmail(event.target.value)
                                    }
                                />
                            </label>
                            <label>
                                {t(
                                    "Mật khẩu hiện tại (khi đổi email/mật khẩu)",
                                    "Current password (to change email/password)",
                                )}
                                <input
                                    type="password"
                                    autoComplete="current-password"
                                    value={current}
                                    onChange={(event) =>
                                        setCurrent(event.target.value)
                                    }
                                />
                            </label>
                            <label>
                                {t(
                                    "Mật khẩu mới (để trống nếu giữ nguyên)",
                                    "New password (leave blank to keep it)",
                                )}
                                <input
                                    type="password"
                                    autoComplete="new-password"
                                    value={password}
                                    onChange={(event) =>
                                        setPassword(event.target.value)
                                    }
                                />
                            </label>
                        </>
                    )}
                    {error && (
                        <p className="form-error" role="alert">
                            {error}
                        </p>
                    )}
                    <footer>
                        <button
                            className="secondary"
                            type="button"
                            onClick={async () => {
                                onLogout();
                            }}
                        >
                            {t("Đăng xuất", "Sign out")}
                        </button>
                        <button className="primary" disabled={busy}>
                            {t("Lưu hồ sơ", "Save profile")}
                        </button>
                    </footer>
                </form>
            </section>
        </div>
    );
}
