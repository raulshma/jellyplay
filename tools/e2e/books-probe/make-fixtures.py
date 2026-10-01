"""Generate book-library fixtures for the books API probe.

Creates under <state>/media/books:
  Flat Book.epub                  - minimal valid EPUB (zip: mimetype first, stored)
  Flat Comic.cbz                  - zip of two JPEG pages
  Foldered Series/Volume 1.cbz    - nested folder case (folder container item)
"""
import pathlib
import sys
import zipfile

state = pathlib.Path(sys.argv[1])
media = state / "media" / "books"
series = media / "Foldered Series"
series.mkdir(parents=True, exist_ok=True)

# --- minimal JPEG (1x1 black) for CBZ pages ---------------------------------
# Baseline JPEG, 1x1 pixel, grayscale — 119 bytes, verified decodable header.
JPEG_1PX = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000"
    "ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432"
    "ffc0000b080001000101011100"
    "ffc4001f0000010501010101010100000000000000000102030405060708090a0b"
    "ffc400b5100002010303020403050504040000017d01020300041105122131410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9fa"
    "ffda0008010100003f00fbfa"
    "ffd9"
)

# --- CBZ ----------------------------------------------------------------------
for target in (media / "Flat Comic.cbz", series / "Volume 1.cbz"):
    with zipfile.ZipFile(target, "w") as zf:
        zf.writestr("page-001.jpg", JPEG_1PX)
        zf.writestr("page-002.jpg", JPEG_1PX)

# --- EPUB ---------------------------------------------------------------------
container = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
opf = """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:7b0a8b0e-book-probe-0001</dc:identifier>
    <dc:title>Flat Book</dc:title>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
  </manifest>
  <spine><itemref idref="nav"/></spine>
</package>"""
nav = """<?xml version="1.0"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <body><p>Probe fixture.</p></body>
</html>"""
epub = media / "Flat Book.epub"
with zipfile.ZipFile(epub, "w") as zf:
    zf.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip")
    zf.writestr("META-INF/container.xml", container)
    zf.writestr("OEBPS/content.opf", opf)
    zf.writestr("OEBPS/nav.xhtml", nav)

for p in (epub, media / "Flat Comic.cbz", series / "Volume 1.cbz"):
    print(p.relative_to(state), p.stat().st_size)
