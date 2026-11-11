"""Durable local accounts, preferences and activity, including Java data compatibility."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import re
import secrets
import threading
import copy
from datetime import date
from pathlib import Path

from latex_backend.storage.store import Problem, atomic_write

DEFAULTS = {
    "mode": "light",
    "theme": "forest",
    "fontStyle": "sans",
    "contentWidth": "standard",
    "editorFontSize": 13,
    "editorFont": "mono",
    "wordWrap": False,
    "autoClose": True,
    "autoSave": False,
    "autoCompile": False,
    "workspaceLayout": "split",
    "compiler": "pdflatex",
    "language": "vi",
    "latexmkPath": "",
    "compileTimeoutSeconds": 120,
    "outlineSplit": 0.60,
    "sidebarWidth": 270,
    "projectsDirectory": "",
    "affiliation": "",
    "lastProject": "",
    "editorSplit": 0.5,
}


def properties(path: Path, encoding="utf-8") -> dict[str, str]:
    """Read java.util.Properties without interpreting text as Python escapes/code."""

    def unescape(value):
        value = re.sub(
            r"\\(u[0-9a-fA-F]{4}|.)",
            lambda m: (
                chr(int(m[1][1:], 16))
                if m[1].startswith("u") and len(m[1]) == 5
                else {"n": "\n", "t": "\t", "r": "\r", "f": "\f"}.get(m[1], m[1])
            ),
            value,
        )
        return value.encode("utf-16", errors="surrogatepass").decode("utf-16")

    result, continuation = {}, ""
    if not path.is_file() or path.is_symlink():
        return result
    for raw in path.read_text(encoding=encoding).splitlines():
        line = continuation + raw.lstrip()
        if (len(line) - len(line.rstrip("\\"))) % 2:
            continuation = line[:-1]
            continue
        continuation = ""
        if not line or line[0] in "#!":
            continue
        escaped, split = False, len(line)
        for index, char in enumerate(line):
            if not escaped and char in "=: \t":
                split = index
                break
            escaped = not escaped if char == "\\" else False
        remainder = line[split:].lstrip()
        if remainder.startswith(("=", ":")):
            remainder = remainder[1:].lstrip()
        result[unescape(line[:split])] = unescape(remainder)
    return result


class State:
    @property
    def session(self):
        from latex_backend.auth.context import request_user
        return request_user.get() or self._local_session

    @session.setter
    def session(self, value):
        self._local_session = value

    def __init__(self, home: Path):
        self.folder = home / ".app"
        self.folder.mkdir(parents=True, exist_ok=True)
        self.file = self.folder / "state.json"
        self.lock = threading.RLock()
        self.data = (
            json.loads(self.file.read_text(encoding="utf-8"))
            if self.file.exists()
            else {"accounts": {}, "settings": {}, "activity": {}, "compiles": {}}
        )
        remembered = self.data.get("remembered", "")
        self.session = (
            self._session(remembered) if remembered in self.data["accounts"] else None
        )

    def write(self):
        atomic_write(
            self.file, json.dumps(self.data, ensure_ascii=False).encode("utf-8")
        )

    def settings(self):
        result = {**DEFAULTS, **self.data["settings"]}
        if getattr(self, "managed", False):
            result.update(projectsDirectory="", latexmkPath="")
        return result

    def preferences(self, values):
        if getattr(self, "managed", False):
            if any(values.get(key) for key in ("projectsDirectory", "latexmkPath")):
                raise Problem("Storage and executable paths are configured by the server operator.", 403)
        with self.lock:
            updated = dict(self.data["settings"])
            for key, value in values.items():
                if key not in DEFAULTS:
                    raise Problem(f"Cài đặt không hợp lệ: {key}")
                default = DEFAULTS[key]
                if isinstance(default, bool) and not isinstance(value, bool):
                    raise Problem("Giá trị bật/tắt không hợp lệ.")
                if isinstance(default, (float, int)) and not isinstance(default, bool):
                    if not isinstance(value, (float, int)) or not 0 <= value <= 86400:
                        raise Problem("Giá trị số không hợp lệ.")
                if isinstance(default, str) and (
                    not isinstance(value, str) or len(value) > 2048
                ):
                    raise Problem("Giá trị cài đặt quá dài.")
                options = {
                    "mode": {"light", "dark", "system"},
                    "theme": {"forest", "mint", "bee", "parchment"},
                    "compiler": {"pdflatex", "xelatex", "lualatex"},
                    "language": {"vi", "en"},
                    "workspaceLayout": {"split", "editor", "pdf"},
                    "fontStyle": {"sans", "serif"},
                    "contentWidth": {"compact", "standard", "wide"},
                    "editorFont": {"mono", "menlo", "courier"},
                }
                if key in options and value not in options[key]:
                    raise Problem("Lựa chọn cài đặt không hợp lệ.")
                if key == "editorFontSize" and not 10 <= value <= 32:
                    raise Problem("Cỡ chữ cần từ 10 đến 32.")
                if key == "sidebarWidth" and not 200 <= value <= 600:
                    raise Problem("Chiều rộng thanh thư mục cần từ 200 đến 600.")
                if key in {"outlineSplit", "editorSplit"} and not 0.1 <= value <= 0.9:
                    raise Problem("Tỉ lệ bố cục không hợp lệ.")
                updated[key] = value
            self.data["settings"] = updated
            self.write()
        return self.settings()

    def reset_preferences(self):
        return self.preferences(
            {
                key: value
                for key, value in DEFAULTS.items()
                if key not in {"projectsDirectory", "affiliation", "lastProject"}
            }
        )

    @staticmethod
    def email(value):
        value = value.strip().lower()
        if "@" not in value or len(value) > 254:
            raise Problem("Nhập địa chỉ email hợp lệ.")
        return value

    @staticmethod
    def password(value):
        if not 6 <= len(value) <= 1024:
            raise Problem("Mật khẩu cần từ 6 đến 1.024 ký tự.")
        salt = secrets.token_bytes(16)
        return {
            "salt": base64.b64encode(salt).decode(),
            "hash": base64.b64encode(
                hashlib.pbkdf2_hmac("sha256", value.encode(), salt, 120000)
            ).decode(),
            "iterations": 120000,
        }

    @staticmethod
    def matches(account, password):
        if not 1000 <= int(account["iterations"]) <= 2_000_000 or len(password) > 1024:
            return False
        value = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode(),
            base64.b64decode(account["salt"]),
            int(account["iterations"]),
        )
        return hmac.compare_digest(value, base64.b64decode(account["hash"]))

    def _session(self, email):
        return {
            "name": self.data["accounts"][email]["name"],
            "email": email,
            "offline": False,
        }

    def authenticate(self, kind, name="", email="", password="", remember=False):
        with self.lock:
            if kind == "offline":
                self.session = {
                    "name": name.strip() or "Local User",
                    "email": "",
                    "offline": True,
                }
                self.data.pop("remembered", None)
                self.write()
                return self.session
            email = self.email(email)
            accounts = self.data["accounts"]
            if kind == "register":
                if email in accounts:
                    raise Problem("Email đã được đăng ký.", 409)
                accounts[email] = {
                    "name": name.strip()[:40] or email.split("@")[0],
                    **self.password(password),
                }
            elif (
                kind != "login"
                or email not in accounts
                or not self.matches(accounts[email], password)
            ):
                raise Problem("Email hoặc mật khẩu không đúng.", 401)
            self.session = self._session(email)
            self.data["lastEmail"] = email
            self.data["remembered"] = email if remember else ""
            self.write()
            return self.session

    def logout(self):
        with self.lock:
            self.session = None
            self.data.pop("remembered", None)
            self.write()

    def update_profile(self, name=None, email=None, current_password="", password=None):
        with self.lock:
            if not self.session:
                raise Problem("Cần đăng nhập hoặc tiếp tục ngoại tuyến.", 401)
            # Validate the entire change against a copy; failed authentication or a
            # duplicate email must not mutate the live session/account in memory.
            original_data, original_session = self.data, self.session
            self.data, self.session = copy.deepcopy(self.data), dict(self.session)
            try:
                return self._update_profile(name, email, current_password, password)
            except BaseException:
                self.data, self.session = original_data, original_session
                raise

    def _update_profile(self, name, email, current_password, password):
        with self.lock:
            old_key = self.avatar_key()
            if name is not None:
                if not name.strip() or len(name.strip()) > 40:
                    raise Problem("Tên cần 1–40 ký tự.")
                self.session["name"] = name.strip()
            if self.session["offline"]:
                if email is not None or password is not None:
                    raise Problem("Cần tài khoản để đổi email/mật khẩu.")
            else:
                account = self.data["accounts"][self.session["email"]]
                if email is not None or password is not None:
                    if not self.matches(account, current_password):
                        raise Problem("Mật khẩu hiện tại không đúng.", 401)
                if password is not None:
                    if self.matches(account, password):
                        raise Problem("Mật khẩu mới cần khác mật khẩu hiện tại.")
                    account.update(self.password(password))
                account["name"] = self.session["name"]
                if email is not None:
                    email = self.email(email)
                    if email in self.data["accounts"]:
                        raise Problem("Email đã được dùng.", 409)
                    previous = self.session["email"]
                    self.data["accounts"][email] = self.data["accounts"].pop(previous)
                    if self.data.get("remembered") == previous:
                        self.data["remembered"] = email
                    self.session["email"] = email
            new_key = self.avatar_key()
            if old_key != new_key and (self.folder / f"{old_key}.png").exists():
                (self.folder / f"{old_key}.png").rename(self.folder / f"{new_key}.png")
            self.write()
            return self.session

    def avatar_key(self):
        if not self.session:
            raise Problem("Chưa có phiên người dùng.", 401)
        return hashlib.sha256(
            (self.session["email"] or "offline:" + self.session["name"]).encode()
        ).hexdigest()

    def avatar(self, data=None, remove=False):
        path = self.folder / f"{self.avatar_key()}.png"
        if remove:
            path.unlink(missing_ok=True)
            for old in (self.folder / "avatars").glob(self.avatar_key()[:20] + ".*"):
                if old.is_file() and not old.is_symlink():
                    old.unlink()
        if not path.exists() and data is None and not remove:
            candidates = list(
                (self.folder / "avatars").glob(self.avatar_key()[:20] + ".*")
            )
            if (
                candidates
                and candidates[0].is_file()
                and not candidates[0].is_symlink()
            ):
                data = candidates[0].read_bytes()
        if data is not None:
            from io import BytesIO
            from PIL import Image, ImageOps

            if len(data) > 5 * 1024 * 1024:
                raise Problem("Ảnh đại diện vượt 5 MiB.", 413)
            try:
                with Image.open(BytesIO(data)) as image:
                    if (
                        image.format not in {"PNG", "JPEG", "GIF"}
                        or image.width * image.height > 20_000_000
                    ):
                        raise ValueError("Unsupported image")
                    image = ImageOps.fit(
                        ImageOps.exif_transpose(image).convert("RGBA"), (256, 256)
                    )
                    output = BytesIO()
                    image.save(output, "PNG")
                    atomic_write(path, output.getvalue())
            except (OSError, ValueError, Image.DecompressionBombError) as error:
                raise Problem("Chọn ảnh PNG/JPG/GIF hợp lệ.") from error
        return path.read_bytes() if path.is_file() else b""

    def activity(self, kind=None):
        with self.lock:
            today = date.today()
            if kind == "edit":
                self.data["activity"][today.isoformat()] = (
                    self.data["activity"].get(today.isoformat(), 0) + 1
                )
            if kind == "compile":
                year = str(today.year)
                self.data["compiles"][year] = self.data["compiles"].get(year, 0) + 1
            if kind:
                self.write()
            return {
                "edits": self.data["activity"],
                "compiles": self.data["compiles"].get(str(today.year), 0),
            }

    def import_java_accounts(self, folder: Path):
        imported = 0
        with self.lock:
            for email, record in properties(
                folder / ".app/accounts.properties"
            ).items():
                if email in self.data["accounts"]:
                    continue
                try:
                    iterations, salt, hashed, name = record.split("|", 3)
                    if (
                        not 1000 <= int(iterations) <= 2_000_000
                        or len(base64.b64decode(salt, validate=True)) != 16
                        or len(base64.b64decode(hashed, validate=True)) != 32
                    ):
                        continue
                    self.data["accounts"][email] = {
                        "name": name,
                        "iterations": int(iterations),
                        "salt": salt,
                        "hash": hashed,
                    }
                    imported += 1
                except (ValueError, UnicodeError):
                    continue
            old = properties(folder / ".app/settings.properties")
            for key, default in DEFAULTS.items():
                if key not in old:
                    continue
                value = old[key]
                if key == "compiler":
                    value = value.lower()
                if isinstance(default, bool):
                    value = value.lower() == "true"
                elif isinstance(default, int):
                    try:
                        value = int(value)
                    except ValueError:
                        continue
                elif isinstance(default, float):
                    try:
                        value = float(value)
                    except ValueError:
                        continue
                self.data["settings"].setdefault(key, value)
            self.write()
        import shutil

        avatars = folder / ".app/avatars"
        if avatars.is_dir() and not avatars.is_symlink():
            target = self.folder / "avatars"
            target.mkdir(exist_ok=True)
            for source in avatars.iterdir():
                if (
                    re.fullmatch(r"[a-f0-9]{20}\.(png|jpg|jpeg|gif)", source.name)
                    and source.is_file()
                    and not source.is_symlink()
                    and source.stat().st_size <= 5 * 1024 * 1024
                    and not (target / source.name).exists()
                ):
                    shutil.copy2(source, target / source.name)
        return imported
