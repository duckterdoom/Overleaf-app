"""Run the user's original converter without rewriting its engines or formatting."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import zipfile
import pymupdf

BUNDLE = Path(__file__).with_name("original")


def pdf_signature(source):
    """Ignore PDF timestamps; require the same text, geometry and raster content."""
    pages = []
    with pymupdf.open(source) as document:
        for page in document:
            blocks = []
            for block in page.get_text("blocks"):
                if block[6] == 0:
                    blocks.append(
                        [
                            *[round(v, 2) for v in block[:4]],
                            re.sub(r"\s+", "", block[4].replace("\u00ad", "")),
                        ]
                    )
            images = [
                [
                    *[round(v, 2) for v in image["bbox"]],
                    image["width"],
                    image["height"],
                    image["digest"].hex(),
                ]
                for image in page.get_image_info(hashes=True)
            ]
            pages.append(
                {
                    "rect": [round(v, 2) for v in page.rect],
                    "blocks": blocks,
                    "images": images,
                }
            )
    return hashlib.sha256(
        json.dumps(pages, sort_keys=True, ensure_ascii=False).encode("utf-8")
    ).hexdigest()


def convert(source: str, destination: str):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    reference = json.loads((BUNDLE / "reference.json").read_text(encoding="utf-8"))
    selected = source
    # The app's PDF has a generated filename. Restore the original sample dispatch
    # only when every page's text, bounds and image digest matches that sample.
    if pdf_signature(source) == reference["signature"]:
        selected = destination.parent / "paper_input.pdf"
        if selected != source:
            shutil.copyfile(source, selected)
    elif "paper_input.pdf" in source.name:
        selected = destination.parent / "document.pdf"
        shutil.copyfile(source, selected)
    previous = Path.cwd()
    sys.path.insert(0, str(BUNDLE))
    try:
        os.chdir(BUNDLE)
        from convert_pdf2docx import convert_pdf_to_word

        # No engine override: original universal -> original patched pdf2docx,
        # plus its original specialized paper builder when matched above.
        if not convert_pdf_to_word(str(selected), str(destination)):
            raise ValueError("The original PDF-to-Word converter reported failure")
    finally:
        os.chdir(previous)
    if not destination.is_file() or destination.stat().st_size > 128 * 1024 * 1024:
        raise ValueError("DOCX is missing or exceeds 128 MiB")
    with zipfile.ZipFile(destination) as archive:
        if "word/document.xml" not in archive.namelist():
            raise ValueError("Invalid DOCX output")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Run the original ilovepdf-python converter"
    )
    parser.add_argument("source")
    parser.add_argument("destination")
    args = parser.parse_args()
    convert(args.source, args.destination)
