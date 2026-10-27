"""Check generated README screenshots before CI publishes them."""

from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
for name in ("apps", "release", "downloads", "appearance", "apps-hero", "books-hero", "games-hero"):
    path = ROOT / "docs" / "images" / f"{name}.png"
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
    width, height = struct.unpack(">II", data[16:24])
    assert width == 1080 and 1800 <= height <= 2400, f"Unexpected size: {name} {width}x{height}"
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        assert kind not in (b"eXIf", b"tEXt", b"iTXt", b"zTXt"), f"Unexpected metadata: {name}"
        offset += length + 12
    assert offset == len(data), f"Truncated PNG: {name}"
    print(f"{name}: {width}x{height}, no text or EXIF metadata")
