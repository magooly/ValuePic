from pathlib import Path
import textwrap

try:
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas
except Exception:
    import subprocess
    import sys

    subprocess.check_call([sys.executable, "-m", "pip", "install", "reportlab", "--quiet"])
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas

src = Path(r"C:\wrhor\DataBase\MONETIZATION_CONVERSATION.md")
dst = Path(r"C:\wrhor\DataBase\MONETIZATION_CONVERSATION.pdf")

PAGE_W, PAGE_H = A4
LEFT, RIGHT, TOP, BOTTOM = 48, 48, 56, 48
MAX_W = PAGE_W - LEFT - RIGHT

lines = src.read_text(encoding="utf-8", errors="ignore").splitlines()
c = canvas.Canvas(str(dst), pagesize=A4)
y = PAGE_H - TOP


def line_height(size: int) -> int:
    return max(15, int(size * 1.35))


for raw in lines:
    line = raw.rstrip()
    if not line:
        y -= 15
        if y < BOTTOM:
            c.showPage()
            y = PAGE_H - TOP
        continue

    text = line
    font = "Helvetica"
    size = 11

    if text.startswith("# "):
        text = text[2:].strip()
        font = "Helvetica-Bold"
        size = 16
    elif text.startswith("## "):
        text = text[3:].strip()
        font = "Helvetica-Bold"
        size = 13
    elif text.lstrip().startswith("- "):
        text = "- " + text.lstrip()[2:].strip()

    c.setFont(font, size)
    avg = size * 0.52
    width_chars = max(25, int(MAX_W / avg))
    wrapped = textwrap.wrap(text, width=width_chars) or [""]

    for part in wrapped:
        if y < BOTTOM:
            c.showPage()
            y = PAGE_H - TOP
            c.setFont(font, size)
        c.drawString(LEFT, y, part)
        y -= line_height(size)

c.save()

print(f"Created: {dst}")
print(f"Size: {dst.stat().st_size} bytes")

