#!/usr/bin/env python3
"""Generate the MesOS Aurora app icons as Android adaptive icons.

Each icon is drawn on the 100-unit grid of the Aurora icon set (see the "MesOS
Aurora Concept" design) and written as vector drawables:

  <module>/src/main/res/drawable/ic_<name>_background.xml   gradient
  <module>/src/main/res/drawable/ic_<name>_foreground.xml   white glyph
  <module>/src/main/res/drawable/ic_<name>_monochrome.xml   themed-icon layer
  <module>/src/main/res/mipmap-anydpi/ic_<name>.xml         adaptive icon

The 100-unit design square maps onto the 72 dp visible area of the 108 dp
adaptive-icon canvas. Run from the repository root: python3 scripts/icons/generate_icons.py
"""

import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
WHITE = "#FFFFFF"


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0 Z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g} a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0 Z"


def rrect(x, y, w, h, r):
    return (
        f"M{x + r:g},{y:g} H{x + w - r:g} A{r:g},{r:g} 0 0 1 {x + w:g},{y + r:g} V{y + h - r:g} "
        f"A{r:g},{r:g} 0 0 1 {x + w - r:g},{y + h:g} H{x + r:g} A{r:g},{r:g} 0 0 1 {x:g},{y + h - r:g} "
        f"V{y + r:g} A{r:g},{r:g} 0 0 1 {x + r:g},{y:g} Z"
    )


def gear(cx, cy, outer, inner, teeth=8):
    step = 2 * math.pi / teeth
    points = []
    for i in range(teeth):
        a = i * step - math.pi / 2
        for r, da in ((inner, -0.36), (outer, -0.2), (outer, 0.2), (inner, 0.36)):
            points.append((cx + r * math.cos(a + da * step), cy + r * math.sin(a + da * step)))
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def fill(d, color=WHITE, alpha=None, even_odd=False):
    return {"d": d, "fill": color, "alpha": alpha, "evenOdd": even_odd}


def stroke(d, width, color=WHITE, alpha=None):
    return {"d": d, "stroke": color, "width": width, "alpha": alpha}


# name -> (module, gradient start, gradient end, diagonal?, foreground elements, monochrome elements or None)
ICONS = {
    "camera": ("apps/camera", "#4B5263", "#15171D", False, [
        stroke("M28,38 H36 L41,31 H59 L64,38 H72 A6,6 0 0 1 78,44 V68 A6,6 0 0 1 72,74 H28 A6,6 0 0 1 22,68 V44 A6,6 0 0 1 28,38 Z", 5.5),
        stroke(circle(50, 56, 11), 5.5),
        fill(circle(50, 56, 4), "#7DD3FC"),
        fill(circle(70, 45, 2.8)),
    ], None),
    "photos": ("apps/photos", "#FFB347", "#FF4D6D", True, [
        fill(circle(36, 36, 8)),
        fill("M34,76 L60,46 L84,72 L84,76 Z", alpha=0.55),
        fill("M16,76 L38,52 L52,66 L60,58 L78,76 Z"),
    ], None),
    "files": ("apps/files", "#5AB0FF", "#2563EB", False, [
        fill("M20,36 A6,6 0 0 1 26,30 H40 L46,36 H74 A6,6 0 0 1 80,42 V48 H20 Z", alpha=0.55),
        fill("M18,48 H82 A4,4 0 0 1 86,52 L83,70 A8,8 0 0 1 75,77 H25 A8,8 0 0 1 17,70 L14,52 A4,4 0 0 1 18,48 Z"),
    ], None),
    "downloads": ("apps/files", "#4ADE9E", "#059669", False, [
        stroke("M50,22 V58 M35,45 L50,60 L65,45", 7),
        stroke("M24,62 V70 A8,8 0 0 0 32,78 H68 A8,8 0 0 0 76,70 V62", 7),
    ], None),
    "calculator": ("apps/calculator", "#B79CFF", "#6D28D9", True, [
        stroke("M34,25 V43 M25,34 H43 M57,34 H75 M27.5,59.5 L40.5,72.5 M40.5,59.5 L27.5,72.5", 6.5),
        fill(rrect(54, 54, 24, 24, 8)),
        stroke("M60,62.5 H72 M60,69.5 H72", 4.5, "#6D28D9"),
    ], [
        stroke("M34,25 V43 M25,34 H43 M57,34 H75 M27.5,59.5 L40.5,72.5 M40.5,59.5 L27.5,72.5 M57,62.5 H75 M57,70 H75", 6.5),
    ]),
    "notes": ("apps/notes", "#FFD76A", "#F59E0B", False, [
        fill("M30,20 H60 L76,36 V72 A8,8 0 0 1 68,80 H30 A8,8 0 0 1 22,72 V28 A8,8 0 0 1 30,20 Z"),
        fill("M60,20 V30 A6,6 0 0 0 66,36 H76 Z", "#000000", 0.12),
        stroke("M32,50 H64 M32,60 H64 M32,70 H52", 5, "#F59E0B"),
    ], [
        stroke("M31,22 H59 L74,37 V71 A7,7 0 0 1 67,78 H31 A7,7 0 0 1 24,71 V29 A7,7 0 0 1 31,22 Z", 5.5),
        stroke("M34,50 H62 M34,60 H62 M34,69 H50", 5),
    ]),
    "settings": ("settings", "#9AA6B8", "#475569", False, [
        fill(gear(50, 50, 30, 22.5)),
        fill(circle(50, 50, 8.5), "#64748B"),
    ], [
        fill(gear(50, 50, 30, 22.5) + " " + circle(50, 50, 8.5), even_odd=True),
    ]),
    "launcher": ("shell", "#6D7CFF", "#2DD4BF", True, [
        stroke("M28,70 V32 L50,56 L72,32 V70", 9),
    ], None),
    "clock": ("apps/clock", "#2A3548", "#0B1220", False, [
        fill(circle(50, 50, 29)),
        stroke("M50,25 V29 M75,50 H71 M50,75 V71 M25,50 H29", 3, "#94A3B8"),
        stroke("M50,50 V33 M50,50 L62,58", 5, "#0F172A"),
        fill(circle(50, 50, 4), "#F97316"),
    ], [
        stroke(circle(50, 50, 27), 5),
        stroke("M50,50 V33 M50,50 L62,58", 5),
    ]),
    "calendar": ("apps/calendar", "#FFFFFF", "#E3E8F0", False, [
        fill("M28,24 H72 A6,6 0 0 1 78,30 V40 H22 V30 A6,6 0 0 1 28,24 Z", "#EF4444"),
        stroke("M22,40 V70 A6,6 0 0 0 28,76 H72 A6,6 0 0 0 78,70 V40", 3.5, "#CBD5E1"),
        fill(rrect(28, 47, 8, 7, 2) + " " + rrect(40, 47, 8, 7, 2) + " " + rrect(52, 47, 8, 7, 2) + " " + rrect(64, 47, 8, 7, 2), "#334155"),
        fill(rrect(28, 58, 8, 7, 2) + " " + rrect(40, 58, 8, 7, 2) + " " + rrect(52, 58, 8, 7, 2), "#334155"),
        fill(rrect(64, 58, 8, 7, 2), "#EF4444"),
        fill(rrect(28, 68, 8, 3.5, 1.5) + " " + rrect(40, 68, 8, 3.5, 1.5), "#334155", 0.5),
    ], [
        stroke(rrect(22, 26, 56, 50, 7), 5),
        stroke("M22,40 H78", 5),
        fill(rrect(30, 49, 8, 7, 2) + " " + rrect(46, 49, 8, 7, 2) + " " + rrect(62, 49, 8, 7, 2) + " " + rrect(30, 61, 8, 7, 2) + " " + rrect(46, 61, 8, 7, 2)),
    ]),
    "weather": ("apps/weather", "#7BB8FF", "#1D4ED8", False, [
        fill(circle(40, 40, 13), "#FDE68A"),
        fill("M34,76 H70 A13,13 0 0 0 71,50 A18,18 0 0 0 37,55 A11,11 0 0 0 34,76 Z"),
    ], [
        fill(circle(40, 40, 13), alpha=0.6),
        fill("M34,76 H70 A13,13 0 0 0 71,50 A18,18 0 0 0 37,55 A11,11 0 0 0 34,76 Z"),
    ]),
    "music": ("apps/music", "#FF8AC6", "#DB2777", True, [
        stroke("M40,66 V32 L70,26 V60", 6),
        fill(ellipse(33, 67, 8.5, 7.5)),
        fill(ellipse(63, 61, 8.5, 7.5)),
    ], None),
    "recorder": ("apps/recorder", "#FF8A9A", "#BE123C", False, [
        stroke("M28,44 V56 M39,34 V66 M50,24 V76 M61,34 V66 M72,44 V56", 7),
    ], None),
    "scanner": ("apps/scanner", "#3FE0C5", "#0F766E", False, [
        stroke("M24,38 V31 A7,7 0 0 1 31,24 H38 M62,24 H69 A7,7 0 0 1 76,31 V38 M76,62 V69 A7,7 0 0 1 69,76 H62 M38,76 H31 A7,7 0 0 1 24,69 V62", 6),
        fill(rrect(37, 37, 11, 11, 2.5)),
        fill(rrect(52, 37, 11, 11, 2.5), alpha=0.6),
        fill(rrect(37, 52, 11, 11, 2.5), alpha=0.6),
        fill(rrect(52, 52, 11, 11, 2.5)),
    ], None),
    "contacts": ("apps/contacts", "#FFB0BE", "#E11D48", True, [
        fill(circle(50, 39, 13)),
        fill("M25,78 C25,63 36,56 50,56 C64,56 75,63 75,78 Z"),
    ], None),
    "phone": ("apps/phone", "#5BE38D", "#16A34A", False, [
        fill("M36,22 C39,21 42,23 43,26 L47,36 C48,39 47,42 45,44 L41,47 C45,55 51,61 59,65 L62,61 C64,59 67,58 70,59 L79,63 C82,64 84,67 83,70 L81,77 C80,80 77,82 74,82 C47,81 24,58 23,31 C23,28 25,25 28,24 Z"),
    ], None),
    "messages": ("apps/messages", "#4CC9FF", "#0284C7", False, [
        fill("M50,22 C67,22 80,33 80,47 C80,61 67,72 50,72 C46,72 42,71 38,70 L25,77 L28,64 C23,59 20,53 20,47 C20,33 33,22 50,22 Z"),
        fill(circle(38, 47, 4) + " " + circle(50, 47, 4) + " " + circle(62, 47, 4), "#0EA5E9"),
    ], [
        fill("M50,22 C67,22 80,33 80,47 C80,61 67,72 50,72 C46,72 42,71 38,70 L25,77 L28,64 C23,59 20,53 20,47 C20,33 33,22 50,22 Z "
             + circle(38, 47, 4) + " " + circle(50, 47, 4) + " " + circle(62, 47, 4), even_odd=True),
    ]),
    "browser": ("apps/browser", "#3EE0F5", "#0E7490", True, [
        stroke(circle(50, 50, 27), 5),
        stroke(ellipse(50, 50, 11, 27), 4.5),
        stroke("M23,50 H77 M28,37 C42,42 58,42 72,37 M28,63 C42,58 58,58 72,63", 4),
    ], None),
    "care": ("apps/care", "#FDBA74", "#EA580C", True, [
        fill("M50,20 L74,29 V48 C74,64 63,75 50,80 C37,75 26,64 26,48 V29 Z"),
        stroke("M35,51 H43 L47,42 L53,59 L57,51 H65", 5, "#EA580C"),
    ], [
        stroke("M50,22 L72,30.5 V48 C72,62.5 62,72.5 50,77.5 C38,72.5 28,62.5 28,48 V30.5 Z", 5),
        stroke("M36,51 H43 L47,43 L53,59 L57,51 H64", 5),
    ]),
    "tips": ("apps/tips", "#6D7CFF", "#2DD4BF", True, [
        fill("M50,20 A19,19 0 0 1 61,55 V62 H39 V55 A19,19 0 0 1 50,20 Z"),
        stroke("M41,70 H59 M44,78 H56", 5),
    ], None),
}

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
GENERATED = "<!-- Generated by scripts/icons/generate_icons.py; edit the script, not this file. -->\n"


def path_xml(element, mono):
    attrs = [f'android:pathData="{element["d"]}"']
    if "fill" in element:
        attrs.append(f'android:fillColor="{WHITE if mono else element["fill"]}"')
        if element.get("alpha") is not None:
            attrs.append(f'android:fillAlpha="{element["alpha"]}"')
        if element.get("evenOdd"):
            attrs.append('android:fillType="evenOdd"')
    else:
        attrs.append(f'android:strokeColor="{WHITE if mono else element["stroke"]}"')
        attrs.append(f'android:strokeWidth="{element["width"]}"')
        attrs.append('android:strokeLineCap="round"')
        attrs.append('android:strokeLineJoin="round"')
        if element.get("alpha") is not None:
            attrs.append(f'android:strokeAlpha="{element["alpha"]}"')
    inner = "\n            ".join(attrs)
    return f"        <path\n            {inner} />\n"


def glyph_xml(elements, mono):
    body = "".join(path_xml(e, mono) for e in elements)
    return (
        HEADER + GENERATED
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    android:width="108dp"\n    android:height="108dp"\n'
        + '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + '    <group\n        android:scaleX="0.72"\n        android:scaleY="0.72"\n'
        + '        android:translateX="18"\n        android:translateY="18">\n'
        + body
        + "    </group>\n</vector>\n"
    )


def background_xml(start, end, diagonal):
    sx, sy, ex, ey = (18, 18, 90, 90) if diagonal else (54, 18, 54, 90)
    return (
        HEADER + GENERATED
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        + '    android:width="108dp"\n    android:height="108dp"\n'
        + '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + '    <path android:pathData="M0,0 H108 V108 H0 Z">\n'
        + '        <aapt:attr name="android:fillColor">\n'
        + f'            <gradient\n                android:type="linear"\n'
        + f'                android:startX="{sx}"\n                android:startY="{sy}"\n'
        + f'                android:endX="{ex}"\n                android:endY="{ey}">\n'
        + f'                <item android:offset="0" android:color="{start}" />\n'
        + f'                <item android:offset="1" android:color="{end}" />\n'
        + "            </gradient>\n        </aapt:attr>\n    </path>\n</vector>\n"
    )


def adaptive_xml(name):
    return (
        HEADER + GENERATED
        + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        + f'    <background android:drawable="@drawable/ic_{name}_background" />\n'
        + f'    <foreground android:drawable="@drawable/ic_{name}_foreground" />\n'
        + f'    <monochrome android:drawable="@drawable/ic_{name}_monochrome" />\n'
        + "</adaptive-icon>\n"
    )


def main():
    for name, (module, start, end, diagonal, fg, mono) in ICONS.items():
        res = ROOT / module / "src" / "main" / "res"
        (res / "drawable").mkdir(parents=True, exist_ok=True)
        (res / "mipmap-anydpi").mkdir(parents=True, exist_ok=True)
        (res / "drawable" / f"ic_{name}_background.xml").write_text(background_xml(start, end, diagonal))
        (res / "drawable" / f"ic_{name}_foreground.xml").write_text(glyph_xml(fg, mono=False))
        (res / "drawable" / f"ic_{name}_monochrome.xml").write_text(glyph_xml(mono or fg, mono=True))
        (res / "mipmap-anydpi" / f"ic_{name}.xml").write_text(adaptive_xml(name))
        print(f"{module}: ic_{name}")


if __name__ == "__main__":
    main()
