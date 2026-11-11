"""All 28 offline Java explanation rules, packaged as data and evaluated in Python."""

import json
import re
from latex_backend.core.config import RESOURCES
from functools import lru_cache


@lru_cache
def rules():
    data = json.loads(
        (RESOURCES / "error-rules.json").read_text(
            encoding="utf8"
        )
    )
    return [(re.compile(item["pattern"], re.I), item) for item in data]


def explain_message(message, context="", language="vi"):
    raw = message.strip()
    lang = "en" if language == "en" else "vi"
    metric = re.search(
        r"Font\s+.+?=([A-Za-z0-9_-]+).*Metric\s*\(TFM\)\s*file not found", raw, re.I
    )
    if metric:
        font = metric[1]
        package = (
            "palatino"
            if font.startswith("ppl")
            else "mathpazo"
            if font.startswith("zpl")
            else ""
        )
        return {
            "title": "Missing TeX font metrics"
            if lang == "en"
            else "Thiếu dữ liệu font TeX",
            "why": f"TeX cannot load the font metrics {font}.tfm."
            if lang == "en"
            else f"TeX không nạp được dữ liệu font {font}.tfm.",
            "fix": (
                f"Install the {package} font package in the selected TeX distribution, then recompile."
                if package
                else "Install the font package containing this TFM file, then recompile."
            )
            if lang == "en"
            else (
                f"Cài gói font {package} vào bộ TeX đang chọn, rồi biên dịch lại."
                if package
                else "Cài gói font có tệp TFM này vào bộ TeX đang chọn rồi biên dịch lại."
            ),
            "known": True,
        }
    for pattern, item in rules():
        match = pattern.search(raw)
        if not match:
            continue
        commands = re.findall(r"\\[A-Za-z@]+", raw)
        context_commands = re.findall(r"\\[A-Za-z@]+", context.strip().split("\n")[0])
        command = (
            commands[0]
            if commands
            else context_commands[-1]
            if context_commands
            else ("it found on this line" if lang == "en" else "được dùng ở dòng này")
        )

        def fill(value):
            value = value.replace("{cmd}", command)
            if "{file}" in value:
                value = value.replace(
                    "{file}", next((group for group in match.groups() if group), "?")
                )
            for i, group in enumerate(match.groups(), 1):
                value = value.replace("{" + str(i) + "}", group or "?")
            return value

        return {key: fill(value) for key, value in item[lang].items()} | {"known": True}
    if re.search(r"fontspec|font.*not found", raw, re.I):
        return {
            "title": "Font/engine mismatch"
            if lang == "en"
            else "Font hoặc engine chưa phù hợp",
            "why": raw,
            "fix": "Use fontspec with XeLaTeX/LuaLaTeX and an available Unicode font."
            if lang == "en"
            else "Dùng fontspec với XeLaTeX/LuaLaTeX và font Unicode có sẵn trên máy.",
            "known": True,
        }
    return {
        "title": "LaTeX reported a problem" if lang == "en" else "LaTeX báo có lỗi",
        "why": raw[:140],
        "fix": "Read this error and the preceding log lines."
        if lang == "en"
        else "Đọc lỗi này và các dòng ngay phía trên trong Build output.",
        "known": False,
    }


def explain(log, language="vi"):
    parsed = []
    for row in log.splitlines():
        location = re.match(r"(.+?\.(?:tex|sty|cls)):(\d+):\s*(.+)", row)
        context = re.match(r"l\.(\d+)\s*(.*)", row)
        if context and parsed:
            parsed[-1]["line"] = parsed[-1]["line"] or int(context[1])
            parsed[-1]["context"] = context[2]
        elif row.startswith("!") or location:
            parsed.append(
                {
                    "file": location[1] if location else "",
                    "line": int(location[2]) if location else 0,
                    "message": location[3] if location else row.lstrip("! "),
                    "context": "",
                }
            )
        elif "command failed with exit code" in row and not parsed:
            parsed.append({"file": "", "line": 0, "message": row, "context": ""})
    return [
        issue | explain_message(issue["message"], issue["context"], language)
        for issue in parsed[:50]
    ]


def explain_exit(kind, message, language="vi"):
    items = {
        "timeout": (
            "Biên dịch quá thời gian",
            "Điều chỉnh timeout hoặc đơn giản hóa hình nặng; thử biên dịch riêng các hình TikZ.",
            "Build timed out",
            "Adjust the timeout or simplify/precompile heavy figures.",
        ),
        "start": (
            "Không chạy được LaTeX",
            "Kiểm tra latexmk -v, PATH và đường dẫn bộ TeX trong Cài đặt.",
            "LaTeX is not available",
            "Check latexmk -v, PATH and the configured TeX installation.",
        ),
        "cancel": (
            "Biên dịch bị ngắt",
            "Bấm Biên dịch lại để chạy tiếp.",
            "Build was interrupted",
            "Press Recompile to try again.",
        ),
        "glyph": (
            "PDF bị thiếu ký tự",
            "Dùng iftex: pdfLaTeX dùng inputenc/T5; XeLaTeX/LuaLaTeX dùng fontspec và font Unicode hỗ trợ tiếng Việt.",
            "PDF contains missing characters",
            "Use iftex: inputenc/T5 with pdfLaTeX; fontspec and a suitable Unicode font with XeLaTeX/LuaLaTeX.",
        ),
        "failure": (
            "Biên dịch thất bại",
            "Đọc lỗi LaTeX đầu tiên trong Build output trước thông báo tổng kết mã thoát.",
            "Build failed",
            "Read the first LaTeX error before the exit-code summary.",
        ),
    }
    item = items.get(kind, items["failure"])
    index = 2 if language == "en" else 0
    return {
        "file": "",
        "line": 0,
        "message": message,
        "title": item[index],
        "why": message,
        "fix": item[index + 1],
        "known": True,
    }
