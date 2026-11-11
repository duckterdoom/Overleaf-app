"""Generate actual offset MediaBox/CropBox PDFs for the PDF.js mapping regression."""

import sys
from pypdf import PdfWriter
from pypdf.generic import RectangleObject

writer = PdfWriter()
for rotation in [0, 90, 180, 270]:
    page = writer.add_blank_page(600, 800)
    page.mediabox = RectangleObject([20, 40, 620, 840])
    page.cropbox = RectangleObject([60, 80, 580, 780])
    page.rotate(rotation)
writer.write(sys.argv[1])
