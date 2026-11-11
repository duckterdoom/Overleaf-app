"""Wire-v1/v2 TCP/TLS, Java-compatible operations, bounded RESYNC and desktop replica."""

from __future__ import annotations
import asyncio
import ipaddress
import json
import os
import secrets
import socket
import ssl
import struct
import threading
import time
import uuid
from collections import OrderedDict
from dataclasses import dataclass, field
from latex_backend.storage.store import Problem

TYPES = set(
    "AUTH RESYNC_BEGIN RESYNC_PART RESYNC_END JOIN_ROOM LEAVE_ROOM TEXT_EDIT FULL_SYNC ACK ACK_BATCH RESYNC CHECKPOINT CURSOR_UPDATE USER_LIST_UPDATE HEARTBEAT ERROR".split()
)
MAX_FRAME = 1024 * 1024


def ulen(text):
    return len(text.encode("utf-16-le", errors="surrogatepass")) // 2


def splice(text, pos, length=0, value=""):
    raw = text.encode("utf-16-le", errors="surrogatepass")
    pos = max(0, min(pos, len(raw) // 2)) * 2
    return (
        raw[:pos]
        + value.encode("utf-16-le", errors="surrogatepass")
        + raw[pos + max(0, length) * 2 :]
    ).decode("utf-16-le", errors="replace")


@dataclass
class Message:
    type: str
    room: str = ""
    user: str = "SERVER"
    name: str = "Server"
    payload: str = ""
    timestamp: int = field(default_factory=lambda: int(time.time() * 1000))
    clock: int = 0

    def encode(self):
        if self.type not in TYPES:
            raise ValueError("Invalid message type")
        data = struct.pack(">i", 2)
        for value in [self.type, self.room, self.user, self.name, self.payload]:
            encoded = value.encode("utf-8")
            data += struct.pack(">i", len(encoded)) + encoded
        data += struct.pack(">qq", self.timestamp, self.clock)
        if not 1 <= len(data) <= MAX_FRAME:
            raise ValueError("Frame too large")
        return struct.pack(">i", len(data)) + data

    @classmethod
    def decode(cls, raw):
        def integer(offset):
            return struct.unpack_from(">i", raw, offset)[0]

        if len(raw) > MAX_FRAME or len(raw) < 4:
            raise ValueError("Invalid frame")
        version = integer(0)
        if version not in {1, 2}:
            raise ValueError("Unsupported wire version")
        offset, values = 4, []
        for _ in range(5):
            length = integer(offset)
            offset += 4
            if length < 0 or offset + length > len(raw):
                raise ValueError("Invalid field length")
            values.append(
                raw[offset : offset + length].decode("utf-8", errors="strict")
            )
            offset += length
        if len(raw) - offset != (16 if version == 2 else 8) or values[0] not in TYPES:
            raise ValueError("Invalid frame trailer")
        timestamp = struct.unpack_from(">q", raw, offset)[0]
        clock = struct.unpack_from(">q", raw, offset + 8)[0] if version == 2 else 0
        return cls(*values, timestamp, clock)


def recv_exact(sock, count):
    output = bytearray()
    while len(output) < count:
        part = sock.recv(count - len(output))
        if not part:
            raise EOFError("Peer closed")
        output.extend(part)
    return bytes(output)


def receive(sock):
    size = struct.unpack(">i", recv_exact(sock, 4))[0]
    if not 1 <= size <= MAX_FRAME:
        raise ValueError("Invalid frame size")
    return Message.decode(recv_exact(sock, size))


def frames(message):
    if message.type != "RESYNC" or ulen(message.payload) <= 65536:
        if message.type == "RESYNC" and len(message.payload.encode()) > 4 * 1024 * 1024:
            raise ValueError("RESYNC_LIMIT")
        return [message.encode()]
    raw = message.payload.encode()
    if len(raw) > 4 * 1024 * 1024:
        raise ValueError("RESYNC_LIMIT: pending edits retained")
    result = [
        Message(
            "RESYNC_BEGIN", message.room, message.user, message.name, str(len(raw))
        ).encode()
    ]
    for offset in range(0, len(message.payload), 32768):
        result.append(
            Message(
                "RESYNC_PART",
                message.room,
                message.user,
                message.name,
                message.payload[offset : offset + 32768],
            ).encode()
        )
    result.append(
        Message("RESYNC_END", message.room, message.user, message.name).encode()
    )
    return result


class Transfer:
    def __init__(self):
        self.begin = None
        self.parts = []
        self.size = 0

    def accept(self, message):
        if message.type == "RESYNC_BEGIN":
            if (
                self.begin
                or not message.payload.isdigit()
                or not 1 <= int(message.payload) <= 4 * 1024 * 1024
            ):
                raise ValueError("RESYNC_LIMIT")
            self.begin = message
            self.parts = []
            self.size = 0
            return None
        if message.type not in {"RESYNC_PART", "RESYNC_END"}:
            if self.begin:
                raise ValueError("Interleaved upload")
            return message
        if not self.begin or (message.room, message.user, message.name) != (
            self.begin.room,
            self.begin.user,
            self.begin.name,
        ):
            raise ValueError("Invalid RESYNC envelope")
        if message.type == "RESYNC_PART":
            self.parts.append(message.payload)
            self.size += len(message.payload.encode())
            if len(self.parts) > 128 or self.size > int(self.begin.payload):
                raise ValueError("RESYNC_LIMIT")
            return None
        if message.payload or self.size != int(self.begin.payload):
            raise ValueError("Incomplete upload")
        result = Message(
            "RESYNC", message.room, message.user, message.name, "".join(self.parts)
        )
        self.begin = None
        self.parts = []
        self.size = 0
        return result


def valid_id(value):
    return (
        isinstance(value, str)
        and 1 <= ulen(value) <= 128
        and not any(ord(char) < 32 or char == "|" for char in value)
    )


def operation(raw, user=None):
    op = json.loads(raw) if isinstance(raw, str) else dict(raw)
    if not all(
        key in op for key in ["id", "type", "pos", "content", "user", "lamport", "ts"]
    ):
        raise ValueError("Invalid operation")
    if (
        not valid_id(op["id"])
        or op["type"] not in {"INSERT", "DELETE"}
        or (user is not None and op["user"] != user)
    ):
        raise ValueError("Invalid operation identity")
    if (
        not all(
            type(op[key]) is int and 0 <= op[key] < 2**63
            for key in ["pos", "lamport", "ts"]
        )
        or not isinstance(op["content"], str)
        or ulen(op["content"]) > 200000
    ):
        raise ValueError("Invalid operation content")
    return op


def transform(op, concurrent, left=False):
    result = dict(op)
    pos, other, length = op["pos"], concurrent["pos"], ulen(concurrent["content"])
    if concurrent["type"] == "INSERT":
        if other < pos or (other == pos and not left):
            pos += length
    elif other + length <= pos:
        pos -= length
    elif other < pos:
        pos = other
    result["pos"] = pos
    return result


def apply(text, op):
    return (
        splice(text, op["pos"], 0, op["content"])
        if op["type"] == "INSERT"
        else splice(text, op["pos"], ulen(op["content"]))
    )


@dataclass
class Room:
    text: str = ""
    clock: int = 0
    history: list = field(default_factory=list)
    peers: dict = field(default_factory=dict)
    cursors: dict = field(default_factory=dict)
    trimmed: bool = False
    touched: float = field(default_factory=time.monotonic)


class Peer:
    def __init__(self, reader, writer, queue_capacity=32):
        self.reader, self.writer = reader, writer
        self.user = self.name = self.room = ""
        self.queue = asyncio.Queue(maxsize=queue_capacity)
        self.sender = asyncio.create_task(self.flush())

    def send(self, message):
        try:
            self.queue.put_nowait(message.encode())
        except (asyncio.QueueFull, ValueError):
            self.writer.close()

    async def flush(self):
        try:
            while True:
                self.writer.write(await self.queue.get())
                await asyncio.wait_for(self.writer.drain(), 5)
        except (ConnectionError, TimeoutError, asyncio.CancelledError):
            self.writer.close()

    async def close(self):
        self.sender.cancel()
        self.writer.close()
        try:
            await self.writer.wait_closed()
        except (OSError, ssl.SSLError):
            pass


class CollaborationServer:
    def __init__(
        self,
        token="",
        max_clients=64,
        max_document=100000,
        history_limit=10000,
        idle_timeout=60,
        queue_capacity=32,
    ):
        if (
            not 1 <= max_clients <= 1024
            or not 1 <= max_document <= 200000
            or not 1 <= history_limit <= 10000
            or not 0.1 <= idle_timeout <= 86400
            or not 1 <= queue_capacity <= 4096
        ):
            raise ValueError("Invalid server limits")
        self.token, self.max_clients, self.max_document, self.history_limit = (
            token,
            max_clients,
            max_document,
            history_limit,
        )
        self.rooms, self.peers = {}, set()
        self.idle_timeout, self.queue_capacity = idle_timeout, queue_capacity
        self.server = None

    async def start(self, host="127.0.0.1", port=8080, context=None):
        addresses = await asyncio.get_running_loop().getaddrinfo(host, port)
        external = any(
            not ipaddress.ip_address(address[4][0]).is_loopback for address in addresses
        )
        if external and (context is None or len(self.token) < 24):
            raise ValueError(
                "External bind requires TLS and OVERLEAF_TOKEN >=24 characters"
            )
        self.server = await asyncio.start_server(
            self.accept, host, port, ssl=context, limit=MAX_FRAME + 4
        )
        return self.server.sockets[0].getsockname()[1]

    async def close(self):
        if self.server:
            self.server.close()
            await self.server.wait_closed()
        for peer in list(self.peers):
            await peer.close()

    async def read(self, reader):
        size = struct.unpack(">i", await reader.readexactly(4))[0]
        if not 1 <= size <= MAX_FRAME:
            raise ValueError("Invalid frame size")
        return Message.decode(await reader.readexactly(size))

    async def accept(self, reader, writer):
        if len(self.peers) >= self.max_clients:
            writer.close()
            return
        peer = Peer(reader, writer, self.queue_capacity)
        self.peers.add(peer)
        try:
            auth = await asyncio.wait_for(self.read(reader), 5)
            if auth.type != "AUTH" or not secrets.compare_digest(
                auth.payload.encode(), self.token.encode()
            ):
                return
            peer.send(Message("ACK", payload="AUTHENTICATED"))
            transfer = Transfer()
            while True:
                incoming = transfer.accept(
                    await asyncio.wait_for(self.read(reader), self.idle_timeout)
                )
                if incoming:
                    try:
                        self.handle(peer, incoming)
                    except (ValueError, KeyError, TypeError) as error:
                        peer.send(Message("ERROR", incoming.room, payload=str(error)))
        except (
            EOFError,
            OSError,
            ValueError,
            struct.error,
            UnicodeError,
            asyncio.IncompleteReadError,
            TimeoutError,
        ):
            pass
        finally:
            self.leave(peer)
            self.peers.discard(peer)
            await peer.close()

    def broadcast(self, room_id, message, exclude=""):
        for user, peer in list(self.rooms[room_id].peers.items()):
            if user != exclude:
                peer.send(message)

    def members(self, room_id):
        room = self.rooms[room_id]
        payload = "\n".join(
            f"{user}|{peer.name}|{'HOST' if i == 0 else 'EDITOR'}|ONLINE"
            for i, (user, peer) in enumerate(room.peers.items())
        )
        self.broadcast(room_id, Message("USER_LIST_UPDATE", room_id, payload=payload))

    def leave(self, peer):
        if peer.room in self.rooms:
            room = self.rooms[peer.room]
            if room.peers.get(peer.user) is peer:
                room.peers.pop(peer.user, None)
            room.cursors.pop(peer.user, None)
            room.touched = time.monotonic()
            self.members(peer.room)
        peer.room = ""

    def checkpoint(self, peer, room):
        peer.send(
            Message(
                "CHECKPOINT",
                peer.room,
                payload=room.history[-1]["id"] if room.history else "",
                clock=room.clock,
            )
        )

    def commit(self, room, op):
        text = apply(room.text, op)
        if ulen(text) > self.max_document:
            raise ValueError("DOCUMENT_LIMIT")
        room.text = text
        room.clock = max(room.clock, op["lamport"]) + 1
        room.history.append(op)
        if len(room.history) > self.history_limit:
            room.history.pop(0)
            room.trimmed = True

    def handle(self, peer, message):
        if message.type in {"JOIN_ROOM", "RESYNC"}:
            if not all(
                valid_id(value) for value in [message.room, message.user, message.name]
            ):
                raise ValueError("INVALID_ID")
            if peer.user and peer.user != message.user:
                raise ValueError("IDENTITY_CANNOT_CHANGE")
            if message.type == "RESYNC" and message.room not in self.rooms:
                raise ValueError("ROOM_UNKNOWN")
            if message.room not in self.rooms:
                for name, old in list(self.rooms.items()):
                    if not old.peers and time.monotonic() - old.touched > 600:
                        self.rooms.pop(name)
                if len(self.rooms) >= 256:
                    raise ValueError("ROOM_LIMIT")
                self.rooms[message.room] = Room()
            room = self.rooms[message.room]
            if message.user in room.peers and room.peers[message.user] is not peer:
                raise ValueError("USER_ALREADY_CONNECTED")
            pending, last_seen = [], ""
            if message.type == "RESYNC":
                rows = message.payload.split("\n")
                last_seen = rows[0].strip()
                if (
                    len(rows) - 1 > 30000
                    or len(message.payload.encode()) > 4 * 1024 * 1024
                ):
                    raise ValueError("RESYNC_LIMIT")
                pending = [
                    operation(row, message.user) for row in rows[1:] if row.strip()
                ]
            self.leave(peer)
            peer.user, peer.name, peer.room = message.user, message.name, message.room
            room.peers[peer.user] = peer
            if message.type == "RESYNC":
                known = {op["id"] for op in room.history}
                index = next(
                    (
                        i + 1
                        for i, op in enumerate(room.history)
                        if op["id"] == last_seen
                    ),
                    None,
                )
                missed = (
                    (
                        [op for op in room.history[index:] if op["user"] != peer.user]
                        if index is not None
                        else None
                    )
                    if last_seen
                    else (
                        None
                        if room.trimmed
                        else [op for op in room.history if op["user"] != peer.user]
                    )
                )
                shadow = Room(
                    room.text, room.clock, list(room.history), trimmed=room.trimmed
                )
                published, acks = [], []
                for incoming in pending:
                    acks.append(incoming["id"])
                    if incoming["id"] in known:
                        continue
                    rebased = incoming
                    if missed is None:
                        for other in shadow.history:
                            if (
                                other["user"] != peer.user
                                and other["lamport"] >= incoming["lamport"]
                            ):
                                rebased = transform(rebased, other)
                    else:
                        next_missed = []
                        for other in missed:
                            next_missed.append(transform(other, rebased))
                            rebased = transform(rebased, other)
                        missed = next_missed
                    self.commit(shadow, rebased)
                    known.add(incoming["id"])
                    published.append(rebased)
                room.text, room.clock, room.history, room.trimmed = (
                    shadow.text,
                    shadow.clock,
                    shadow.history,
                    shadow.trimmed,
                )
                for op in published:
                    self.broadcast(
                        peer.room,
                        Message(
                            "TEXT_EDIT",
                            peer.room,
                            peer.user,
                            peer.name,
                            json.dumps(op, ensure_ascii=False),
                            clock=room.clock,
                        ),
                        peer.user,
                    )
                for offset in range(0, len(acks), 2048):
                    peer.send(
                        Message(
                            "ACK_BATCH",
                            peer.room,
                            payload="\n".join(acks[offset : offset + 2048]),
                            clock=room.clock,
                        )
                    )
            peer.send(
                Message("FULL_SYNC", peer.room, payload=room.text, clock=room.clock)
            )
            self.checkpoint(peer, room)
            self.members(peer.room)
            return
        if not peer.room or message.room != peer.room or message.user != peer.user:
            raise ValueError("MEMBERSHIP_REQUIRED")
        room = self.rooms[peer.room]
        room.touched = time.monotonic()
        if message.type == "LEAVE_ROOM":
            self.leave(peer)
        elif message.type == "HEARTBEAT":
            peer.send(Message("HEARTBEAT", peer.room, payload="pong", clock=room.clock))
        elif message.type == "CURSOR_UPDATE":
            fields = message.payload.split(",", 2)
            if (
                len(fields) != 3
                or any(not value.isdigit() for value in fields[:2])
                or fields[2] != "main.tex"
            ):
                raise ValueError("INVALID_CURSOR")
            room.cursors[peer.user] = message.payload
            self.broadcast(
                peer.room,
                Message(
                    "CURSOR_UPDATE",
                    peer.room,
                    peer.user,
                    peer.name,
                    message.payload,
                    clock=room.clock,
                ),
                peer.user,
            )
        elif message.type == "TEXT_EDIT":
            incoming = operation(message.payload, peer.user)
            if any(op["id"] == incoming["id"] for op in room.history):
                peer.send(
                    Message("ACK", peer.room, payload=incoming["id"], clock=room.clock)
                )
                return
            transformed = incoming
            for other in room.history:
                if (
                    other["user"] != peer.user
                    and other["lamport"] >= incoming["lamport"]
                ):
                    transformed = transform(transformed, other)
            self.commit(room, transformed)
            peer.send(
                Message("ACK", peer.room, payload=incoming["id"], clock=room.clock)
            )
            self.broadcast(
                peer.room,
                Message(
                    "TEXT_EDIT",
                    peer.room,
                    peer.user,
                    peer.name,
                    json.dumps(transformed, ensure_ascii=False),
                    clock=room.clock,
                ),
                peer.user,
            )
        else:
            raise ValueError("UNSUPPORTED_CLIENT_MESSAGE")


class NetworkSession:
    def __init__(self, store):
        self.store = store
        self.lock = threading.RLock()
        self.send_lock = threading.Lock()
        self.sock = None
        self.joined = self.online = self.resyncing = False
        self.user = uuid.uuid4().hex
        self.project = self.room = self.name = ""
        self.document = ""
        self.pending = OrderedDict()
        self.clock = 0
        self.last_seen = ""
        self.version = 0
        self.members = []
        self.cursors = {}
        self.error = ""
        self.config = {}
        self.generation = 0
        self.seed = None

    def status(self):
        with self.lock:
            return {
                "joined": self.joined,
                "online": self.online,
                "resyncing": self.resyncing,
                "pending": len(self.pending),
                "room": self.room,
                "user": self.user,
                "members": self.members,
                "cursors": self.cursors,
                "document": self.document,
                "version": self.version,
                "project": self.project,
                "error": self.error,
            }

    def connect(
        self,
        project,
        host="127.0.0.1",
        port=8080,
        room="ROOM-101",
        name="Local User",
        token="",
        tls=False,
        caFile="",
        reconnect=False,
    ):
        if not valid_id(room) or not valid_id(name):
            raise Problem("Tên phòng hoặc tên người dùng không hợp lệ.")
        source = None
        if not reconnect:
            with self.lock:
                if self.joined and self.pending:
                    raise Problem(
                        "Còn thao tác chờ ACK. Nối lại phòng hiện tại hoặc lưu/rời phòng trước khi kết nối phòng khác.",
                        409,
                    )
                source = self.store.read(project, "main.tex")
                if ulen(source) > 100000:
                    raise Problem(
                        "main.tex vượt 100.000 ký tự UTF-16 của phòng. Nội dung local được giữ nguyên.",
                        413,
                    )
                self.leave()
        addresses = socket.getaddrinfo(host, int(port), type=socket.SOCK_STREAM)
        if any(
            not ipaddress.ip_address(value[4][0]).is_loopback for value in addresses
        ) and (not tls or len(token) < 24):
            raise Problem("Kết nối ngoài máy cần TLS và token tối thiểu 24 ký tự.")
        sock = socket.create_connection((host, int(port)), timeout=5)
        try:
            if tls:
                context = ssl.create_default_context(cafile=caFile or None)
                context.minimum_version = ssl.TLSVersion.TLSv1_2
                sock = context.wrap_socket(sock, server_hostname=host)
            sock.sendall(Message("AUTH", payload=token).encode())
            auth = receive(sock)
            if auth.type != "ACK" or auth.payload != "AUTHENTICATED":
                raise OSError("Authentication rejected")
            sock.settimeout(45)
        except BaseException:
            sock.close()
            raise
        with self.lock:
            self.generation += 1
            generation = self.generation
            self.sock = sock
            self.project, self.room, self.name = project, room, name
            self.joined = self.online = True
            self.resyncing = True
            self.error = ""
            self.config = dict(
                project=project,
                host=host,
                port=int(port),
                room=room,
                name=name,
                token=token,
                tls=tls,
                caFile=caFile,
            )
            if not reconnect:
                self.document = source
                self.seed = self.document
                self.pending.clear()
                self.clock = 0
                self.last_seen = ""
            payload = self.last_seen + "".join(
                "\n" + json.dumps(op, ensure_ascii=False)
                for op in self.pending.values()
            )
            try:
                self.send(
                    Message(
                        "RESYNC" if reconnect else "JOIN_ROOM",
                        room,
                        self.user,
                        name,
                        payload if reconnect else "",
                    )
                )
            except (OSError, ValueError):
                self.offline()
                raise
        threading.Thread(
            target=self.read_loop, args=(sock, generation), daemon=True
        ).start()
        threading.Thread(
            target=self.heartbeat, args=(sock, generation), daemon=True
        ).start()
        return self.status()

    def send(self, message):
        with self.send_lock:
            if not self.sock:
                raise OSError("Connection offline")
            for frame in frames(message):
                self.sock.sendall(frame)

    def offline(self):
        with self.lock:
            self.generation += 1
            sock, self.sock = self.sock, None
            self.online = self.resyncing = False
            if sock:
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                sock.close()
            return self.status()

    def leave(self):
        with self.lock:
            if self.sock and self.room:
                try:
                    self.send(Message("LEAVE_ROOM", self.room, self.user, self.name))
                except OSError:
                    pass
            self.offline()
            self.joined = False
            self.pending.clear()
            self.members = []
            self.cursors = {}
            self.version += 1
            return self.status()

    def reconnect(self):
        if not self.joined:
            raise Problem("Chưa có phòng để nối lại.")
        self.offline()
        return self.connect(**self.config, reconnect=True)

    def edit(self, text, base=None):
        with self.lock:
            if not self.joined:
                raise Problem("Chưa vào phòng.")
            if self.resyncing:
                raise Problem("Đợi RESYNC hoàn tất trước khi sửa.", 409)
            if ulen(text) > 100000:
                raise Problem("Tài liệu phòng vượt 100.000 ký tự UTF-16.", 413)
            old = self.document if base is None else base
            prefix = 0
            while prefix < min(len(old), len(text)) and old[prefix] == text[prefix]:
                prefix += 1
            suffix = 0
            while (
                suffix < min(len(old) - prefix, len(text) - prefix)
                and old[len(old) - suffix - 1] == text[len(text) - suffix - 1]
            ):
                suffix += 1
            deleted, inserted = (
                old[prefix : len(old) - suffix if suffix else len(old)],
                text[prefix : len(text) - suffix if suffix else len(text)],
            )
            generated = []
            for kind, content in [("DELETE", deleted), ("INSERT", inserted)]:
                if not content:
                    continue
                self.clock += 1
                generated.append(
                    {
                        "id": self.user[:12] + "-" + uuid.uuid4().hex,
                        "type": kind,
                        "pos": ulen(old[:prefix]),
                        "content": content,
                        "user": self.user,
                        "lamport": self.clock,
                        "ts": int(time.time() * 1000),
                    }
                )
            # The web editor may have a remote update in flight; preserve it instead of
            # interpreting an older UI snapshot as a request to delete someone else's edit.
            if old != self.document:
                import difflib

                missed = []
                for tag, a, b, c, d in difflib.SequenceMatcher(
                    None, old, self.document, autojunk=False
                ).get_opcodes():
                    if tag in {"replace", "delete"}:
                        missed.append(
                            {
                                "type": "DELETE",
                                "pos": ulen(old[:a]),
                                "content": old[a:b],
                            }
                        )
                    if tag in {"replace", "insert"}:
                        missed.append(
                            {
                                "type": "INSERT",
                                "pos": ulen(old[:a]),
                                "content": self.document[c:d],
                            }
                        )
                rebased = []
                for incoming in generated:
                    next_missed = []
                    for other in missed:
                        next_missed.append(transform(other, incoming, left=True))
                        incoming = transform(incoming, other)
                    missed = next_missed
                    rebased.append(incoming)
                generated = rebased
            if len(self.pending) + len(generated) > 30000:
                raise Problem("Queue đạt 30.000 thao tác. Lưu bản sao và nối lại.", 413)
            for op in generated:
                self.pending[op["id"]] = op
                if self.online:
                    try:
                        self.send(
                            Message(
                                "TEXT_EDIT",
                                self.room,
                                self.user,
                                self.name,
                                json.dumps(op, ensure_ascii=False),
                                clock=self.clock,
                            )
                        )
                    except (OSError, ValueError):
                        self.offline()
            for op in generated:
                self.document = apply(self.document, op)
            self.version += 1
            return self.status()

    def heartbeat(self, sock, generation):
        while generation == self.generation:
            time.sleep(15)
            if generation != self.generation:
                return
            try:
                self.send(Message("HEARTBEAT", self.room, self.user, self.name))
            except OSError:
                return

    def read_loop(self, sock, generation):
        try:
            while generation == self.generation:
                message = receive(sock)
                with self.lock:
                    if generation != self.generation or message.room != self.room:
                        continue
                    self.clock = max(self.clock, message.clock)
                    if message.type == "FULL_SYNC":
                        if (
                            self.seed is not None
                            and message.payload
                            and message.payload != self.seed
                        ):
                            backup = "main.before-" + uuid.uuid4().hex[:12] + ".tex"
                            self.store.add(self.project, backup, False)
                            self.store.save(self.project, backup, self.seed)
                        self.document = message.payload
                        self.version += 1
                    elif message.type in {"ACK", "ACK_BATCH"}:
                        for identifier in message.payload.split("\n"):
                            self.pending.pop(identifier, None)
                            if identifier:
                                self.last_seen = identifier
                    elif message.type == "TEXT_EDIT" and message.user != self.user:
                        op = operation(message.payload)
                        self.last_seen = op["id"]
                        remote = op
                        for identifier, pending in list(self.pending.items()):
                            self.pending[identifier] = transform(pending, remote)
                            remote = transform(remote, pending, left=True)
                        self.document = apply(self.document, remote)
                        self.version += 1
                    elif message.type == "CHECKPOINT":
                        self.last_seen = message.payload
                        self.resyncing = False
                        if self.seed is not None:
                            seed, self.seed = self.seed, None
                            if not self.document and seed:
                                self.edit(seed)
                    elif message.type == "USER_LIST_UPDATE":
                        self.members = [
                            dict(
                                zip(["id", "name", "role", "status"], row.split("|", 3))
                            )
                            for row in message.payload.splitlines()
                        ]
                    elif message.type == "CURSOR_UPDATE":
                        self.cursors[message.user] = {
                            "name": message.name,
                            "position": message.payload,
                        }
                    elif message.type == "ERROR":
                        self.error = message.payload
                        if self.resyncing:
                            self.offline()
                    if message.type == "CHECKPOINT" and not self.resyncing:
                        self.store.save(self.project, "main.tex", self.document)
        except (OSError, EOFError, ValueError, Problem) as error:
            with self.lock:
                if generation == self.generation:
                    self.error = str(error)
                    self.offline()

    def cursor(self, line, column):
        if self.online:
            self.send(
                Message(
                    "CURSOR_UPDATE",
                    self.room,
                    self.user,
                    self.name,
                    f"{max(0, int(line))},{max(0, int(column))},main.tex",
                )
            )


def server_main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Overleaf Python TCP/TLS server (Java wire v2 compatible)"
    )
    parser.add_argument("--host", default=os.getenv("OVERLEAF_HOST", "127.0.0.1"))
    parser.add_argument(
        "--port", type=int, default=int(os.getenv("OVERLEAF_PORT", "8080"))
    )
    parser.add_argument("--cert", default=os.getenv("OVERLEAF_TLS_CERT", ""))
    parser.add_argument("--key", default=os.getenv("OVERLEAF_TLS_KEY", ""))
    parser.add_argument(
        "--max-clients", type=int, default=int(os.getenv("OVERLEAF_MAX_CLIENTS", "64"))
    )
    parser.add_argument(
        "--max-document-chars",
        type=int,
        default=int(os.getenv("OVERLEAF_MAX_DOCUMENT_CHARS", "100000")),
    )
    parser.add_argument(
        "--idle-timeout-ms",
        type=int,
        default=int(os.getenv("OVERLEAF_IDLE_TIMEOUT_MS", "60000")),
    )
    parser.add_argument(
        "--queue-capacity",
        type=int,
        default=int(os.getenv("OVERLEAF_QUEUE_CAPACITY", "32")),
    )
    args = parser.parse_args()
    context = None
    if args.cert or args.key:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(args.cert, args.key)

    async def run_server():
        token = os.getenv("OVERLEAF_TOKEN", "")
        if token and len(token) < 24:
            raise ValueError("OVERLEAF_TOKEN must have at least 24 characters")
        server = CollaborationServer(
            token,
            args.max_clients,
            args.max_document_chars,
            idle_timeout=args.idle_timeout_ms / 1000,
            queue_capacity=args.queue_capacity,
        )
        port = await server.start(args.host, args.port, context)
        print(
            f"Overleaf Python TCP listening on {args.host}:{port}, TLS={bool(context)}",
            flush=True,
        )
        try:
            await server.server.serve_forever()
        finally:
            await server.close()

    try:
        asyncio.run(run_server())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    server_main()
