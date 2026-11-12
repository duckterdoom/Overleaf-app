import type { Peer } from "../lib/usePresence";

export default function PresenceAvatars({ project, peers, connected, language, selfId, file, digest }: {
    project: string; peers: Peer[]; connected: boolean; language: string;
    selfId?: string; file: string; digest: string;
}) {
    // The signed-in user's avatar already appears at the far right of the header.
    const people = [...new Map(peers.filter(peer => peer.userId !== selfId).map(peer => [peer.userId, peer])).values()];
    const vi = language === "vi";
    return <div className="presence-avatars" aria-label={vi ? "Người đang chỉnh sửa" : "Editors online"}>
        {!connected && <span className="presence-connecting" role="status">{vi ? "Đang kết nối cộng tác…" : "Connecting presence…"}</span>}
        {connected && people.length === 0 && <span className="presence-connecting" role="status"
            title={vi ? "WebSocket đã kết nối. Chưa có người chỉnh sửa khác trong dự án này." : "WebSocket connected. No other editor in this project."}>
            {vi ? "Chỉ bạn đang mở dự án" : "Only you are here"}</span>}
        {people.map(peer => {
            const sessions = peers.filter(p => p.userId === peer.userId);
            const active = sessions.find(p => p.active) || peer;
            const different = active.context !== 'writing' && active.file === file && active.digest && digest && active.digest !== digest;
            const position = active.context === 'writing' ? (active.location || 'IELTS Writing') : active.file;
            const role = active.workspaceRole === 'tutor' ? 'Tutor' : active.workspaceRole === 'student' ? (vi ? 'Học viên' : 'Student') : '';
            const title = `${peer.name}${role ? ' · ' + role : ''} · ${active.active ? (vi ? "Đang hoạt động" : "Active") : (vi ? "Đang mở" : "Viewing")} ${position}${different ? (vi ? " · Nội dung chưa đồng bộ; con trỏ sẽ hiện lại khi hai bản khớp." : " · Unsynchronized text; cursor resumes when documents match.") : ""}`;
            return <span key={peer.userId} className="presence-person" style={{ borderColor: peer.color }} title={title} aria-label={title}>
                <span>{peer.name.trim().split(/\s+/).slice(-2).map(part => part[0]).join("").toUpperCase()}</span>
                {/* Only authorized project members can request another editor's avatar. */}
                <img src={`/api/projects/${project}/sharing/avatar/${peer.userId}`} alt="" onError={event => { event.currentTarget.style.display = "none"; }} />
                <i style={{ background: active.active ? peer.color : "#9ba49f" }} />
            </span>;
        })}
    </div>;
}
