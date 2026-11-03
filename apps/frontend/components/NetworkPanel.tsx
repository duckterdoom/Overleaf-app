"use client";
import { useState } from "react";
import {
    X,
    Wifi,
    WifiOff,
    RefreshCw,
    LogOut,
    LoaderCircle,
} from "lucide-react";
import { action, translator, errorText, type Network } from "../lib/parity";
export default function NetworkPanel({
    project,
    language,
    name,
    state,
    onDone,
    onClose,
}: {
    project: string;
    language: string;
    name: string;
    state: Network;
    onDone: (state: Network) => void;
    onClose: () => void;
}) {
    const t = translator(language),
        [host, setHost] = useState("127.0.0.1"),
        [port, setPort] = useState(8080),
        [room, setRoom] = useState(state.room || "ROOM-101"),
        [display, setDisplay] = useState(name),
        [token, setToken] = useState(""),
        [tls, setTls] = useState(false),
        [ca, setCa] = useState("");
    const [busy, setBusy] = useState(false),
        [error, setError] = useState("");
    async function run(kind: string) {
        setBusy(true);
        setError("");
        try {
            const result = await action<Network>(
                kind,
                kind === "network-connect"
                    ? {
                          project,
                          host,
                          port,
                          room,
                          name: display,
                          token,
                          tls,
                          caFile: ca,
                      }
                    : {},
            );
            onDone(result);
            if (kind === "network-connect") onClose();
        } catch (error) {
            setError(errorText(error));
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="modal-backdrop">
            <section
                className="modal network-modal"
                role="dialog"
                aria-modal="true"
                aria-label={t("Cộng tác", "Collaboration")}
            >
                <header>
                    <h2>{t("Phòng cộng tác", "Collaboration room")}</h2>
                    <button title={t("Đóng", "Close")} onClick={onClose}>
                        <X size={20} />
                    </button>
                </header>
                <div className="feature-form">
                    <p className="help-text">
                        {t(
                            "Phòng đồng bộ main.tex qua TCP/TLS. Phòng trống nhận nội dung của bạn; bản local khác được giữ thành tệp backup trước khi nhận snapshot.",
                            "Rooms sync main.tex over TCP/TLS. Empty rooms start from your document; differing local content is backed up before receiving a snapshot.",
                        )}
                    </p>
                    <label>
                        {t("Máy chủ", "Server host")}
                        <input
                            value={host}
                            onChange={(event) => setHost(event.target.value)}
                        />
                    </label>
                    <label>
                        Port
                        <input
                            type="number"
                            min={1}
                            max={65535}
                            value={port}
                            onChange={(event) =>
                                setPort(Number(event.target.value))
                            }
                        />
                    </label>
                    <label>
                        {t("Tên phòng", "Room")}
                        <input
                            value={room}
                            onChange={(event) => setRoom(event.target.value)}
                        />
                    </label>
                    <label>
                        {t("Tên hiển thị", "Display name")}
                        <input
                            value={display}
                            onChange={(event) => setDisplay(event.target.value)}
                        />
                    </label>
                    <label>
                        Access token
                        <input
                            type="password"
                            autoComplete="off"
                            value={token}
                            onChange={(event) => setToken(event.target.value)}
                        />
                    </label>
                    <label className="check-row">
                        <input
                            type="checkbox"
                            checked={tls}
                            onChange={(event) => setTls(event.target.checked)}
                        />
                        {t(
                            "Dùng TLS (bắt buộc khi kết nối ngoài localhost)",
                            "Use TLS (required outside localhost)",
                        )}
                    </label>
                    {tls && (
                        <label>
                            {t(
                                "Tệp CA PEM (trống = trust hệ thống)",
                                "CA PEM file (empty = system trust)",
                            )}
                            <input
                                value={ca}
                                onChange={(event) => setCa(event.target.value)}
                            />
                        </label>
                    )}
                    {state.joined && (
                        <div className="room-status">
                            <strong>
                                {state.online
                                    ? t("Đang trực tuyến", "Online")
                                    : t("Đang ngoại tuyến", "Offline")}{" "}
                                · {state.pending}{" "}
                                {t("thao tác chờ ACK", "pending ACK")}
                            </strong>
                            <div className="inline-controls">
                                <button
                                    className="secondary"
                                    disabled={busy}
                                    onClick={() => void run("network-offline")}
                                >
                                    <WifiOff size={15} />
                                    {t("Ngắt mạng", "Go offline")}
                                </button>
                                <button
                                    className="secondary"
                                    disabled={busy}
                                    onClick={() =>
                                        void run("network-reconnect")
                                    }
                                >
                                    <RefreshCw size={15} />
                                    {t(
                                        "Nối lại / RESYNC",
                                        "Reconnect / RESYNC",
                                    )}
                                </button>
                                <button
                                    className="secondary"
                                    disabled={busy}
                                    onClick={() => void run("network-leave")}
                                >
                                    <LogOut size={15} />
                                    {t("Rời phòng", "Leave room")}
                                </button>
                            </div>
                            {state.members.map((member) => (
                                <p key={member.id}>
                                    {member.name} · {member.role} ·{" "}
                                    {member.status}
                                </p>
                            ))}
                        </div>
                    )}
                    {Object.entries(state.cursors).map(([id, cursor]) => (
                        <p className="help-text" key={id}>
                            {cursor.name}: {cursor.position}
                        </p>
                    ))}
                    {state.error && (
                        <p className="form-error" role="alert">
                            {state.error}
                        </p>
                    )}
                    {error && (
                        <p className="form-error" role="alert">
                            {error}
                        </p>
                    )}
                </div>
                <footer>
                    <button className="secondary" onClick={onClose}>
                        {t("Hủy", "Cancel")}
                    </button>
                    <button
                        className="primary"
                        disabled={busy}
                        onClick={() => void run("network-connect")}
                    >
                        {busy ? (
                            <LoaderCircle size={16} className="spin" />
                        ) : (
                            <Wifi size={16} />
                        )}{" "}
                        {t("Kết nối", "Connect")}
                    </button>
                </footer>
            </section>
        </div>
    );
}
