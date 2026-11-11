"""Conservative text edits from converted DOCX back to the original TeX.

DOCX layout is not a TeX AST. Only uniquely anchored text can round-trip;
commands, math and ambiguous/restructured text must never be guessed.
"""
import difflib
import io
import re
import unicodedata
import zipfile
from pathlib import PurePosixPath
from xml.etree import ElementTree as ET

from latex_backend.storage.store import Problem
from latex_backend.documents.word import tex_escape

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def document_text(data):
    if len(data) > 32 * 1024 * 1024:
        raise Problem('DOCX vượt giới hạn 32 MiB.')
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if len(archive.infolist()) > 2048 or sum(i.file_size for i in archive.infolist()) > 128 * 1024 * 1024:
                raise Problem('DOCX vượt giới hạn giải nén.')
            if any('vbaProject' in name for name in archive.namelist()):
                raise Problem('DOCX không được chứa macro.')
            xml = archive.read('word/document.xml')
    except (zipfile.BadZipFile, KeyError, RuntimeError) as error:
        raise Problem('Không phải tệp DOCX hợp lệ.') from error
    if b'<!DOCTYPE' in xml or b'<!ENTITY' in xml:
        raise Problem('XML DOCX không hợp lệ.')
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as error:
        raise Problem('XML DOCX không hợp lệ.') from error
    paragraphs = []
    for paragraph in root.iter(W + 'p'):
        paragraphs.append(''.join(node.text or '' for node in paragraph.iter(W + 't')))
    return re.sub(r'\s+', ' ', unicodedata.normalize('NFKC', ' '.join(paragraphs))).strip()


def projection(source):
    """Visible text and source offsets, without interpreting executable TeX."""
    chars, positions = [], []
    protected = {'input', 'include', 'includegraphics', 'label', 'ref', 'pageref',
                 'cite', 'citep', 'citet', 'autocite', 'parencite', 'bibliography',
                 'addbibresource', 'documentclass', 'usepackage', 'begin', 'end',
                 'newcommand', 'renewcommand', 'def', 'setlength', 'url'}

    def append(value, start, end):
        for char in unicodedata.normalize('NFKC', value):
            char = ' ' if char.isspace() else char
            if char == ' ' and chars and chars[-1] == ' ':
                positions[-1] = (positions[-1][0], end)
            else:
                chars.append(char)
                positions.append((start, end))

    index = 0
    while index < len(source):
        char = source[index]
        if char == '%':
            end = source.find('\n', index)
            index = len(source) if end < 0 else end
            continue
        if char == '$':
            delimiter = '$$' if source.startswith('$$', index) else '$'
            end = source.find(delimiter, index + len(delimiter))
            index = len(source) if end < 0 else end + len(delimiter)
            append(' ', index, index)
            continue
        if char == '\\':
            environment = re.match(r'\\begin\s*\{((?:equation|align|alignat|gather|multline|eqnarray|math|displaymath|verbatim|lstlisting|minted)\*?)\}', source[index:])
            if environment:
                closing = re.search(r'\\end\s*\{' + re.escape(environment[1]) + r'\}', source[index + len(environment[0]):])
                index = len(source) if closing is None else index + len(environment[0]) + closing.end()
                append(' ', index, index)
                continue
            escaped = re.match(r'\\([&%#_{}$])', source[index:])
            if escaped:
                append(escaped[1], index, index + 2)
                index += 2
                continue
            math = {'\\(': '\\)', '\\[': '\\]'}.get(source[index:index + 2])
            if math:
                end = source.find(math, index + 2)
                index = len(source) if end < 0 else end + 2
                append(' ', index, index)
                continue
            command = re.match(r'\\([A-Za-z@]+)\*?', source[index:])
            if command:
                index += len(command[0])
                if command[1] in protected:
                    while index < len(source):
                        space = re.match(r'\s*', source[index:])[0]
                        index += len(space)
                        if index >= len(source) or source[index] not in '{[':
                            break
                        opening = source[index]
                        closing = '}' if opening == '{' else ']'
                        depth = 1
                        index += 1
                        while index < len(source) and depth:
                            if source[index] == '\\':
                                index += 2
                                continue
                            depth += (source[index] == opening) - (source[index] == closing)
                            index += 1
                    append(' ', index, index)
                continue
            index += 2
            append(' ', index, index)
            continue
        if char not in '{}':
            append(' ' if char == '~' else char, index, index + 1)
        index += 1
    return ''.join(chars), positions


def sources_for(store, project, main):
    sources = {}

    def visit(name):
        if name in sources:
            return
        if len(sources) >= 128:
            raise Problem('Quá nhiều tệp nguồn để đồng bộ Word.')
        value = store.read(project, name)
        if sum(len(s) for s in sources.values()) + len(value) > 4 * 1024 * 1024:
            raise Problem('Nguồn quá lớn để đồng bộ Word.')
        sources[name] = value
        for child in re.findall(r'\\(?:input|include)\s*\{([^{}]+)\}', value):
            if not child.endswith('.tex'):
                child += '.tex'
            # TeX resolves input from the project working directory first.
            if not store.path(project, child).is_file():
                child = str(PurePosixPath(name).parent / child)
            visit(child)

    visit(main)
    return sources


def plan(store, project, main, before, after):
    old, new = document_text(before), document_text(after)
    if old == new:
        return {}
    if not old:
        raise Problem('Word không có ánh xạ văn bản gốc. Tạo lại từ PDF trước khi sửa.', 409)
    sources = sources_for(store, project, main)
    projected = {name: projection(value) for name, value in sources.items()}
    edits = {}
    # Word-sized tokens keep long articles linear in the common case and avoid
    # interpreting a one-letter edit as a match in an unrelated command.
    a, b = list(re.finditer(r'\S+|\s+', old)), list(re.finditer(r'\S+|\s+', new))
    matcher = difflib.SequenceMatcher(None, [m[0] for m in a], [m[0] for m in b])
    for tag, ai, aj, bi, bj in matcher.get_opcodes():
        if tag == 'equal':
            continue
        start = a[ai].start() if ai < len(a) else len(old)
        end = a[aj - 1].end() if aj > ai else start
        replacement = ''.join(m[0] for m in b[bi:bj])
        matches = set()
        for context in (80, 40, 20, 8, 0):
            left, right = max(0, start - context), min(len(old), end + context)
            needle = old[left:right]
            if not needle.strip() or (context == 0 and end - start < 3):
                continue
            for name, (text, positions) in projected.items():
                offset = text.find(needle)
                while offset >= 0:
                    begin, finish = offset + start - left, offset + end - left
                    raw_start = positions[begin][0] if begin < len(positions) else len(sources[name])
                    raw_end = positions[finish - 1][1] if finish > begin else raw_start
                    raw = sources[name][raw_start:raw_end]
                    # Crossing formatting, commands or a formula is unsafe.
                    if not re.search(r'[\\{}$%]', re.sub(r'\\[&%#_{}$]', '', raw)):
                        matches.add((name, raw_start, raw_end))
                    offset = text.find(needle, offset + 1)
            if len(matches) == 1:
                break
            if len(matches) > 1:
                matches.clear()
        if len(matches) != 1:
            raise Problem('Không xác định duy nhất đoạn LaTeX cho thay đổi Word. Bản Word được giữ để đối chiếu; sửa đoạn này trong editor LaTeX hoặc tạo lại Word từ PDF. Không ghi đè nguồn.', 409)
        name, begin, finish = matches.pop()
        edits.setdefault(name, []).append((begin, finish, tex_escape(replacement)))
    updates = {}
    for name, changes in edits.items():
        source = sources[name]
        boundary = len(source) + 1
        for begin, finish, value in sorted(changes, reverse=True):
            if finish > boundary:
                raise Problem('Các đoạn Word ánh xạ chồng nhau. Không ghi đè LaTeX.', 409)
            source = source[:begin] + value + source[finish:]
            boundary = begin
        updates[name] = source
    return updates
