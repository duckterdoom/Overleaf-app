"""Editable Word drafts converted with Pandoc, stored separately from LaTeX/PDF."""

import base64
import copy
import html
import json
import os
import re
import shutil
import subprocess
import uuid
from pathlib import Path
import io
import zipfile
from html.parser import HTMLParser
from latex_backend.storage.store import Problem, REPO, atomic_write

LIMIT = 8 * 1024 * 1024
MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
CONVERTER_VERSION = 3


def document_layout(source):
    layout = {
        "columns": 2
        if re.search(r"\\(?:twocolumn|begin\{multicols\}\{2\})|\btwocolumn\b", source)
        else 1,
        "paper": "a4",
        "width": 210,
        "height": 297,
        "left": 20,
        "right": 20,
        "top": 25,
        "bottom": 25,
        "fontFamily": "Palatino Linotype"
        if re.search(r"\b(?:mathpazo|palatino)\b", source)
        else "Times New Roman",
        "fontSize": 12,
    }
    if "letterpaper" in source:
        layout.update(paper="letter", width=215.9, height=279.4)
    options = re.search(r"\\documentclass\s*\[([^]]+)\]", source)
    size = re.search(r"\b(10|11|12)pt\b", options[1]) if options else None
    if size:
        layout["fontSize"] = int(size[1])
    for side in ["left", "right", "top", "bottom"]:
        matches = list(
            re.finditer(r"\b" + side + r"\s*=\s*([0-9.]+)\s*(cm|mm|in|pt)", source)
        )
        if matches:
            match = matches[-1]
            size = (
                float(match[1])
                * {"cm": 10, "mm": 1, "in": 25.4, "pt": 25.4 / 72.27}[match[2]]
            )
            if 5 <= size <= 60:
                layout[side] = round(size, 2)
    return layout


class SafeHTML(HTMLParser):
    tags = set(
        "p div span header section figure figcaption h1 h2 h3 h4 h5 h6 strong b em i u s sub sup br hr ul ol li blockquote pre code a img table thead tbody tfoot tr td th caption colgroup col dl dt dd math semantics mrow mi mn mo mtext ms mspace mfrac msqrt mroot mstyle merror mpadded mphantom mfenced menclose msub msup msubsup munder mover munderover mmultiscripts mprescripts none mtable mtr mtd annotation".split()
    )
    void = {"br", "hr", "img", "col", "mspace", "none", "mprescripts"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.output = []
        self.skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style", "iframe", "object", "svg"}:
            self.skip += 1
        if self.skip or tag not in self.tags:
            return
        clean = []
        for key, value in attrs:
            value = value or ""
            if tag == "img" and key == "style":
                dimensions = re.findall(
                    r"(?:^|;)\s*(width|height)\s*:\s*([0-9]+(?:\.[0-9]+)?(?:px|pt|mm|cm|in|%))\s*(?=;|$)",
                    value,
                    re.I,
                )
                if dimensions:
                    clean.append(
                        (
                            "style",
                            ";".join(
                                name.lower() + ":" + size for name, size in dimensions
                            ),
                        )
                    )
                continue
            if (
                key in {"colspan", "rowspan", "start", "width", "height"}
                and value.isdigit()
                and int(value) < 10000
            ):
                clean.append((key, value))
            elif key in {
                "title",
                "alt",
                "display",
                "mathvariant",
                "encoding",
                "xmlns",
                "stretchy",
                "accent",
                "accentunder",
                "columnalign",
                "rowalign",
                "class",
                "data-word-id",
                "contenteditable",
            }:
                clean.append((key, value[:500]))
            elif (
                tag == "a"
                and key == "href"
                and re.match(r"^(https?://|mailto:|#)", value, re.I)
            ):
                clean.append((key, value))
            elif (
                tag == "img"
                and key == "src"
                and re.fullmatch(
                    r"data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/=\r\n]+", value
                )
            ):
                clean.append((key, value))
        self.output.append(
            "<"
            + tag
            + "".join(
                " " + key + '="' + html.escape(value, quote=True) + '"'
                for key, value in clean
            )
            + ">"
        )

    def handle_endtag(self, tag):
        if tag in {"script", "style", "iframe", "object", "svg"} and self.skip:
            self.skip -= 1
            return
        if not self.skip and tag in self.tags and tag not in self.void:
            self.output.append("</" + tag + ">")

    def handle_data(self, data):
        if not self.skip:
            self.output.append(html.escape(data))


def sanitize(value):
    if not isinstance(value, str) or len(value.encode("utf-8")) > LIMIT:
        raise Problem("Nội dung Word vượt giới hạn 8 MiB.")
    parser = SafeHTML()
    parser.feed(value)
    parser.close()
    return "".join(parser.output)


class SyncHTML(HTMLParser):
    """Map editable text leaves to exact source ranges; opaque LaTeX stays intact."""

    def __init__(self, sources=None):
        super().__init__(convert_charrefs=True)
        self.sources = sources
        self.output = []
        self.mapping = {}
        self.values = {}
        self.active = []
        self.span_protection = []
        self.protected = 0
        self.unmapped = []

    def handle_starttag(self, tag, attrs):
        self.output.append(self.get_starttag_text())
        if tag in {"math", "pre", "code"}:
            self.protected += 1
        if tag == "span":
            protected = bool(
                set(dict(attrs).get("class", "").split())
                & {"citation", "header-section-number", "math"}
            )
            self.span_protection.append(protected)
            if protected:
                self.protected += 1
            identifier = dict(attrs).get("data-word-id")
            if identifier and identifier in self.values:
                raise Problem("Đoạn Word bị trùng ID. Không ghi đè LaTeX.", 409)
            self.active.append(identifier)
            if identifier:
                self.values[identifier] = ""
        if self.sources is None:
            for identifier in self.active:
                if identifier:
                    if tag in {"br", "div", "p"}:
                        self.values[identifier] += "\n" if tag == "br" else "\n\n"

    def handle_endtag(self, tag):
        self.output.append("</" + tag + ">")
        if tag == "span" and self.active:
            self.active.pop()
            if self.span_protection.pop():
                self.protected = max(0, self.protected - 1)
        if tag in {"math", "pre", "code"}:
            self.protected = max(0, self.protected - 1)

    def handle_data(self, value):
        if self.sources is None:
            if not any(self.active):
                self.unmapped.append(value)
            for identifier in self.active:
                if identifier:
                    self.values[identifier] += value
        matches = []
        if (
            self.sources is not None
            and not self.protected
            and len(value.strip()) >= 2
            and any(char.isalpha() for char in value)
        ):
            pattern = r"\s+".join(re.escape(part) for part in value.strip().split())
            for file, source in self.sources.items():
                for match in re.finditer(pattern, source):
                    if any(
                        command.start() < match.end() and command.end() > match.start()
                        for command in re.finditer(r"\\[A-Za-z@]+", source)
                    ):
                        continue
                    matches.append((file, match.start(), match.end(), match.group()))
                    if len(matches) > 1:
                        break
        if len(matches) == 1:
            file, start, end, raw = matches[0]
            # Never map overlapping spans, e.g. a title repeated in a heading.
            if not any(
                v["file"] == file and start < v["end"] and end > v["start"]
                for v in self.mapping.values()
            ):
                identifier = uuid.uuid4().hex
                self.mapping[identifier] = {
                    "file": file,
                    "start": start,
                    "end": end,
                    "raw": raw,
                    "text": value.strip(),
                }
                leading = value[: len(value) - len(value.lstrip())]
                trailing = value[len(value.rstrip()) :]
                self.output.append(
                    html.escape(leading)
                    + '<span data-word-id="'
                    + identifier
                    + '" contenteditable="true">'
                    + html.escape(value.strip())
                    + "</span>"
                    + html.escape(trailing)
                )
                return
        self.output.append(html.escape(value))


def tex_escape(value):
    return "".join(
        {
            "\\": r"\textbackslash{}",
            "{": r"\{",
            "}": r"\}",
            "%": r"\%",
            "$": r"\$",
            "&": r"\&",
            "#": r"\#",
            "_": r"\_",
            "^": r"\textasciicircum{}",
            "~": r"\textasciitilde{}",
        }.get(char, char)
        for char in value
    )


class Word:
    def __init__(self, store):
        self.store = store
        self.lock = store.lock

    def path(self, project):
        path = self.store.root(project) / ".overleaf-word.json"
        if path.is_symlink():
            raise Problem("Word draft không được là symbolic link.")
        return path

    def run(self, content, source, target, *extra):
        executable = os.getenv("OVERLEAF_PANDOC") or shutil.which("pandoc")
        bundled = REPO / "target/pandoc/pandoc.exe"
        if not executable and bundled.is_file():
            executable = str(bundled)
        if not executable:
            raise Problem(
                "Cần cài Pandoc để tạo hoặc export Word. Thêm pandoc vào PATH hoặc đặt OVERLEAF_PANDOC.",
                503,
            )
        try:
            result = subprocess.run(
                [executable, "--sandbox", "--from=" + source, "--to=" + target, *extra],
                input=content,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=60,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            raise Problem(
                "Không chạy được Pandoc (timeout 60 giây): " + str(error)
            ) from error
        if result.returncode:
            raise Problem(
                "Pandoc: " + result.stderr.decode("utf-8", errors="replace")[-4000:]
            )
        if len(result.stdout) > 32 * 1024 * 1024:
            raise Problem("Kết quả Word vượt giới hạn 32 MiB.")
        return result.stdout, result.stderr.decode("utf-8", errors="replace")[-4000:]

    def read(self, project):
        with self.lock:
            path = self.path(project)
            if not path.exists():
                return None
            if path.stat().st_size > LIMIT * 2:
                raise Problem("Word draft quá lớn.")
            value = json.loads(path.read_text(encoding="utf-8"))
            value["html"] = sanitize(value["html"])
            value["stale"] = value["fingerprint"] != self.store.fingerprint(project)
            value["needsRefresh"] = value.get("converterVersion") != CONVERTER_VERSION
            return value

    def write(self, project, value):
        value = copy.deepcopy(value)
        value["html"] = sanitize(value["html"])
        value["revision"] = uuid.uuid4().hex
        value.pop("stale", None)
        atomic_write(
            self.path(project), json.dumps(value, ensure_ascii=False).encode("utf-8")
        )
        return self.read(project)

    def generate(self, project, revision):
        with self.lock:
            existing = self.read(project)
            if (existing or {}).get("revision") != revision:
                raise Problem("Bản Word đã thay đổi. Mở lại trước khi tạo mới.", 409)
            fingerprint = self.store.fingerprint(project)
            total = [0]
            sources = {}

            def expand(name, parents=()):
                if name in parents or len(parents) > 24:
                    raise Problem("LaTeX input/include lặp hoặc quá sâu.")
                path = self.store.path(project, name)
                if path.stat().st_size > 2 * 1024 * 1024:
                    raise Problem("Tệp LaTeX quá lớn để chuyển Word.")
                value = path.read_text(encoding="utf-8-sig")
                sources[name] = value
                total[0] += len(value.encode("utf-8"))
                if total[0] > LIMIT:
                    raise Problem("LaTeX vượt giới hạn chuyển Word.")

                def include(match):
                    child = match[1]
                    if not child.endswith(".tex"):
                        child += ".tex"
                    return expand(child, (*parents, name))

                return re.sub(r"\\(?:input|include)\s*\{([^{}]+)\}", include, value)

            source = expand(self.store.info(project)["main"])
            layout_source = source
            class_match = re.search(
                r"\\documentclass(?:\s*\[[^]]*\])?\s*\{([^{}]+)\}", source
            )
            if class_match:
                class_path = self.store.path(project, class_match[1] + ".cls")
                if class_path.is_file() and class_path.stat().st_size < 512000:
                    layout_source += "\n" + class_path.read_text(
                        encoding="utf-8", errors="replace"
                    )
            layout = document_layout(layout_source)
            graphics_dirs = [""]
            for match in re.finditer(
                r"\\graphicspath\s*\{((?:\s*\{[^{}]*\}\s*)+)\}", layout_source
            ):
                graphics_dirs.extend(re.findall(r"\{([^{}]*)\}", match[1]))
            graphics_dirs.extend(
                str(Path(name).parent).replace("\\", "/") + "/"
                for name in sources
                if "/" in name
            )
            graphics_dirs = list(
                dict.fromkeys(
                    directory.strip().strip('"') for directory in graphics_dirs
                )
            )
            missing_images = []

            def graphics(match):
                options, target = match[1] or "", match[2].strip()
                if len(target) >= 2 and target[0] == target[-1] == '"':
                    target = target[1:-1]
                # Validate the filename itself before trying any graphicspath prefix.
                self.store.path(project, target)
                extensions = (
                    [""]
                    if Path(target).suffix
                    else ["", ".png", ".jpg", ".jpeg", ".gif", ".pdf"]
                )
                found = None
                for directory in graphics_dirs:
                    for extension in extensions:
                        relative = (
                            directory.rstrip("/") + "/" + target + extension
                            if directory not in {"", "./", "."}
                            else target + extension
                        )
                        path = self.store.path(project, relative)
                        if path.is_file():
                            found = path.relative_to(
                                self.store.root(project)
                            ).as_posix()
                            break
                    if found:
                        break
                if found is None:
                    missing_images.append(target)
                    found = target
                text_width = layout["width"] - layout["left"] - layout["right"]
                column_width = (
                    text_width - (7 if layout["columns"] == 2 else 0)
                ) / layout["columns"]

                def dimension(size):
                    factor = float(size[1] or "1")
                    width = text_width if size[2] == "textwidth" else column_width
                    return str(round(factor * width, 3)) + "mm"

                options = re.sub(
                    r"([0-9]*\.?[0-9]*)\s*\\(textwidth|linewidth|columnwidth)\b",
                    dimension,
                    options,
                )
                return "\\includegraphics" + options + "{" + found + "}"

            conversion_source = re.sub(r"(?<!\\)%[^\n]*", "", source)
            conversion_source = re.sub(
                r"\\includegraphics\*?\s*(\[[^]]*\])?\s*\{([^{}]+)\}",
                graphics,
                conversion_source,
            )
            for _ in range(8):
                unwrapped = re.sub(
                    r"\\(?:fbox|framebox|mbox)\s*\{\s*(\\includegraphics(?:\s*\[[^]]*\])?\s*\{[^{}]+\})\s*\}",
                    r"\1",
                    conversion_source,
                )
                if unwrapped == conversion_source:
                    break
                conversion_source = unwrapped
            raw, warning = self.run(conversion_source.encode("utf-8"), "latex", "json")
            ast = json.loads(raw)
            warnings = [warning] if warning else []
            warnings.extend(
                "Không tìm thấy ảnh: " + target for target in missing_images
            )
            image_count = 0
            figure_count = 0

            def images(node):
                nonlocal image_count, figure_count
                if isinstance(node, dict):
                    if node.get("t") == "Figure":
                        figure_count += 1
                        caption = node["c"][1][1]
                        if caption and caption[0].get("t") in {"Plain", "Para"}:
                            caption[0]["c"] = [
                                {
                                    "t": "Strong",
                                    "c": [
                                        {
                                            "t": "Str",
                                            "c": "Figure " + str(figure_count) + ":",
                                        }
                                    ],
                                },
                                {"t": "Space"},
                                *caption[0]["c"],
                            ]
                    if node.get("t") == "Image":
                        target = node["c"][2][0]
                        try:
                            path = self.store.path(project, target)
                            if not path.suffix:
                                path = next(
                                    (
                                        self.store.path(project, target + ext)
                                        for ext in [".png", ".jpg", ".jpeg"]
                                        if self.store.path(
                                            project, target + ext
                                        ).is_file()
                                    ),
                                    path,
                                )
                            mime = {
                                ".png": "png",
                                ".jpg": "jpeg",
                                ".jpeg": "jpeg",
                                ".gif": "gif",
                            }.get(path.suffix.lower())
                            if not mime or path.stat().st_size > 2 * 1024 * 1024:
                                raise ValueError("unsupported image")
                            node["c"][2][0] = (
                                "data:image/"
                                + mime
                                + ";base64,"
                                + base64.b64encode(path.read_bytes()).decode("ascii")
                            )
                            image_count += 1
                            if not node["c"][1]:
                                node["c"][1] = [{"t": "Str", "c": Path(target).name}]
                        except (Problem, OSError, ValueError):
                            warnings.append("Không chuyển được ảnh: " + str(target))
                            node.clear()
                            node.update(
                                {"t": "Str", "c": "[Image: " + str(target) + "]"}
                            )
                    for item in node.values():
                        images(item)
                elif isinstance(node, list):
                    for item in node:
                        images(item)

            images(ast)
            requested_images = len(
                re.findall(r"\\includegraphics\*?\b", conversion_source)
            )
            if image_count < requested_images:
                warnings.append(
                    f"Chỉ chuyển được {image_count}/{requested_images} ảnh. Kiểm tra khối LaTeX bao quanh các ảnh chưa hỗ trợ."
                )
            # Never allow metadata to trigger external bibliography/style reads.
            ast["meta"] = {
                k: v
                for k, v in ast.get("meta", {}).items()
                if k in {"title", "author", "date", "abstract", "lang"}
            }
            raw, warning = self.run(
                json.dumps(ast).encode("utf-8"),
                "json",
                "html5",
                "--standalone",
                "--mathml",
                "--number-sections",
            )
            if warning:
                warnings.append(warning)
            body = re.search(r"<body[^>]*>([\s\S]*)</body>", raw.decode("utf-8"), re.I)
            if not body or not body[1].strip():
                raise Problem("Không tạo được nội dung Word từ tài liệu này.")
            body_html = body[1]
            # Some journal classes place the abstract in a preamble title hook.
            # Pandoc does not execute those hooks; recover that visible content.
            preamble = source.split(r"\begin{document}", 1)[0]
            abstract = re.search(
                r"\\begin\{abstract\}([\s\S]*?)\\end\{abstract\}", preamble
            )
            if abstract:
                fragment, _ = self.run(
                    abstract[1].encode("utf-8"), "latex", "html5", "--mathml"
                )
                abstract_html = (
                    '<div class="word-abstract"><h2>Abstract</h2>'
                    + fragment.decode("utf-8")
                    + "</div>"
                )
                body_html = (
                    body_html.replace(
                        "</header>",
                        "</header>"
                        + abstract_html
                        + '<div class="word-body-start"></div>',
                        1,
                    )
                    if "</header>" in body_html
                    else abstract_html + body_html
                )
            elif "</header>" in body_html:
                body_html = body_html.replace(
                    "</header>", '</header><div class="word-body-start"></div>', 1
                )
            if fingerprint != self.store.fingerprint(project):
                raise Problem(
                    "Nguồn đã đổi trong lúc chuyển Word. Vui lòng thử lại.", 409
                )
            mapped = SyncHTML(sources)
            mapped.feed(sanitize(body_html))
            if existing:
                atomic_write(
                    self.store.root(project) / ".overleaf-word.previous.json",
                    json.dumps(existing, ensure_ascii=False).encode("utf-8"),
                )
            return self.write(
                project,
                {
                    "html": "".join(mapped.output),
                    "fingerprint": fingerprint,
                    "warnings": warnings,
                    "sync": mapped.mapping,
                    "layout": layout,
                    "converterVersion": CONVERTER_VERSION,
                    "imageCount": image_count,
                },
            )

    def save(self, project, data):
        with self.lock:
            current = self.read(project)
            if current is None or data.get("revision") != current["revision"]:
                raise Problem(
                    "Bản Word đã thay đổi ở cửa sổ khác. Mở lại trước khi lưu.", 409
                )
            incoming = sanitize(data.get("html"))
            if current.get("sync"):
                if getattr(self.store, "network", None) and self.store.network.joined:
                    raise Problem(
                        "Rời phòng cộng tác trước khi sửa LaTeX từ Word.", 409
                    )
                if current["stale"]:
                    atomic_write(
                        self.store.root(project) / ".overleaf-word-conflict.html",
                        incoming.encode("utf-8"),
                    )
                    raise Problem(
                        "LaTeX đã đổi trong lúc sửa Word. Bản Word đang sửa được giữ; cần đối chiếu trước khi đồng bộ.",
                        409,
                    )
                parsed, original = SyncHTML(), SyncHTML()
                parsed.feed(incoming)
                original.feed(current["html"])
                if (
                    set(parsed.values) != set(current["sync"])
                    or "".join(parsed.unmapped).split()
                    != "".join(original.unmapped).split()
                ):
                    raise Problem(
                        "Chỉ sửa trong các đoạn Word có ánh xạ nguồn. Không thay toàn bộ trang hoặc công thức/khối được bảo vệ.",
                        409,
                    )
                edits = {}
                mapping = copy.deepcopy(current["sync"])
                for identifier, anchor in mapping.items():
                    value = parsed.values[identifier]
                    if value != anchor["text"]:
                        replacement = tex_escape(value)
                        edits.setdefault(anchor["file"], []).append(
                            (
                                identifier,
                                anchor["start"],
                                anchor["end"],
                                replacement,
                                value,
                            )
                        )
                originals, updates = {}, {}
                for file, changes in edits.items():
                    raw = self.store.path(project, file).read_text(encoding="utf-8-sig")
                    originals[file] = raw
                    for identifier, start, end, replacement, value in sorted(
                        changes, key=lambda item: item[1], reverse=True
                    ):
                        if raw[start:end] != mapping[identifier]["raw"]:
                            raise Problem(
                                "Vị trí LaTeX đã đổi. Không ghi đè nguồn.", 409
                            )
                        raw = raw[:start] + replacement + raw[end:]
                    if len(raw.encode("utf-8")) > 2 * 1024 * 1024:
                        raise Problem("Tệp LaTeX sau đồng bộ quá lớn.")
                    updates[file] = raw
                    for identifier, anchor in mapping.items():
                        if anchor["file"] != file:
                            continue
                        start = anchor["start"]
                        offset = sum(
                            len(new) - (end - begin)
                            for changed_id, begin, end, new, _ in changes
                            if end <= start and changed_id != identifier
                        )
                        anchor["start"] += offset
                        anchor["end"] += offset
                        for changed_id, _, _, replacement, value in changes:
                            if identifier == changed_id:
                                anchor["raw"], anchor["text"] = replacement, value
                                anchor["end"] = anchor["start"] + len(replacement)
                try:
                    for file, raw in updates.items():
                        self.store.save(project, file, raw)
                    return self.write(
                        project,
                        {
                            **current,
                            "html": incoming,
                            "sync": mapping,
                            "fingerprint": self.store.fingerprint(project),
                        },
                    )
                except Exception:
                    for file, raw in originals.items():
                        atomic_write(
                            self.store.path(project, file), raw.encode("utf-8")
                        )
                    raise
            return self.write(project, {**current, "html": incoming})

    def export(self, project, revision):
        with self.lock:
            current = self.read(project)
            if current is None or current["revision"] != revision:
                raise Problem("Bản Word đã thay đổi. Lưu lại trước khi export.", 409)
            export_html = current["html"]
            section_marker = "OVERLEAFWORDSECTION" + uuid.uuid4().hex
            has_front = (
                current.get("layout", {}).get("columns") == 2
                and '<div class="word-body-start"></div>' in export_html
            )
            if has_front:
                export_html = export_html.replace(
                    '<div class="word-body-start"></div>',
                    "<p>" + section_marker + "</p>",
                    1,
                )
            raw, _ = self.run(
                export_html.encode("utf-8"),
                "html",
                "docx",
                "--standalone",
                "--output=-",
            )
            if not raw.startswith(b"PK"):
                raise Problem("Pandoc không tạo được tệp DOCX hợp lệ.")
            if current.get("layout"):
                layout = current["layout"]
                output = io.BytesIO()
                with (
                    zipfile.ZipFile(io.BytesIO(raw)) as archive,
                    zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as result,
                ):
                    for item in archive.infolist():
                        content = archive.read(item.filename)
                        if item.filename == "word/styles.xml":
                            xml = content.decode("utf-8")
                            font = html.escape(
                                layout.get("fontFamily", "Times New Roman"), quote=True
                            )
                            font_tag = (
                                '<w:rFonts w:ascii="'
                                + font
                                + '" w:hAnsi="'
                                + font
                                + '" w:eastAsia="'
                                + font
                                + '" w:cs="'
                                + font
                                + '"/>'
                            )

                            def font_properties(value, size):
                                value = re.sub(
                                    r"<w:rFonts\b[^>]*/>|<w:sz(?:Cs)?\b[^>]*/>",
                                    "",
                                    value,
                                )
                                properties = (
                                    font_tag
                                    + '<w:sz w:val="'
                                    + str(size)
                                    + '"/><w:szCs w:val="'
                                    + str(size)
                                    + '"/>'
                                )
                                if "<w:rPr>" in value:
                                    return value.replace(
                                        "<w:rPr>", "<w:rPr>" + properties, 1
                                    )
                                return value.replace(
                                    "</w:style>",
                                    "<w:rPr>" + properties + "</w:rPr></w:style>",
                                )

                            body_size = int(layout.get("fontSize", 12)) * 2
                            xml = re.sub(
                                r"<w:rPrDefault>.*?</w:rPrDefault>",
                                lambda match: font_properties(match[0], body_size),
                                xml,
                                flags=re.S,
                            )

                            def styled(match):
                                style_id = re.search(r'w:styleId="([^"]+)"', match[0])
                                if not style_id:
                                    return match[0]
                                sizes = {
                                    "Normal": body_size,
                                    "BodyText": body_size,
                                    "FirstParagraph": body_size,
                                    "Title": 36,
                                    "Author": 22,
                                    "Date": 18,
                                    "Abstract": 20,
                                    "Caption": 20,
                                    "Heading1": 28,
                                    "Heading2": 26,
                                    "Heading3": body_size,
                                }
                                return (
                                    font_properties(match[0], sizes[style_id[1]])
                                    if style_id[1] in sizes
                                    else match[0]
                                )

                            xml = re.sub(
                                r"<w:style\b[^>]*>.*?</w:style>",
                                styled,
                                xml,
                                flags=re.S,
                            )
                            content = xml.encode("utf-8")
                        if item.filename == "word/document.xml":
                            # Keep Pandoc's namespace declarations, including mc:Ignorable prefixes.
                            xml = content.decode("utf-8")
                            xml = re.sub(
                                r'\bdescr="data:image/[^\"]*"',
                                'descr="Project figure"',
                                xml,
                            )

                            def section(match):
                                value = match[0]
                                twips = lambda key: str(
                                    round(layout[key] * 1440 / 25.4)
                                )
                                geometry = {
                                    "pgSz": '<w:pgSz w:w="'
                                    + twips("width")
                                    + '" w:h="'
                                    + twips("height")
                                    + '"/>',
                                    "pgMar": "<w:pgMar "
                                    + " ".join(
                                        "w:" + side + '="' + twips(side) + '"'
                                        for side in ["top", "right", "bottom", "left"]
                                    )
                                    + ' w:header="360" w:footer="360" w:gutter="0"/>',
                                    "cols": '<w:cols w:num="'
                                    + str(layout.get("columns", 1))
                                    + '" w:space="400"/>',
                                }
                                for tag, replacement in geometry.items():
                                    pattern = r"<w:" + tag + r"\b[^>]*/>"
                                    if re.search(pattern, value):
                                        value = re.sub(
                                            pattern, lambda _: replacement, value
                                        )
                                    else:
                                        value = value.replace(
                                            "</w:sectPr>", replacement + "</w:sectPr>"
                                        )
                                return value

                            xml = re.sub(
                                r"<w:sectPr\b[^>]*>.*?</w:sectPr>",
                                section,
                                xml,
                                flags=re.S,
                            )
                            if has_front:
                                final_section = re.search(
                                    r"<w:sectPr\b[^>]*>.*?</w:sectPr>", xml, re.S
                                )
                                geometry = (
                                    "".join(
                                        re.findall(
                                            r"<w:(?:pgSz|pgMar)\b[^>]*/>",
                                            final_section[0],
                                        )
                                    )
                                    if final_section
                                    else ""
                                )
                                break_paragraph = (
                                    '<w:p><w:pPr><w:sectPr><w:type w:val="continuous"/>'
                                    + geometry
                                    + '<w:cols w:num="1"/></w:sectPr></w:pPr></w:p>'
                                )
                                xml = re.sub(
                                    r"<w:p\b[^>]*>(?:(?!</w:p>)[\s\S])*?"
                                    + section_marker
                                    + r"(?:(?!</w:p>)[\s\S])*?</w:p>",
                                    lambda _: break_paragraph,
                                    xml,
                                    count=1,
                                )
                            content = xml.encode("utf-8")
                        result.writestr(item, content)
                raw = output.getvalue()
            return raw
