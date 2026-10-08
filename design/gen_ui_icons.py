#!/usr/bin/env python3
"""Generates the in-app line icons (res/drawable/ic_*.xml) from one source.

All icons share a 24x24 grid, a 2-unit round-capped stroke and no fill, so a
single tint colour recolours them for light and dark themes. Run from the
repository root:

    python3 design/gen_ui_icons.py            # rewrite res/drawable/ic_*.xml
    python3 design/gen_ui_icons.py --sheet F  # also write an HTML contact sheet

Requires only Python 3. Path data is validated against the commands Android's
VectorDrawable parser accepts, because a malformed path is a runtime crash, not
a build error.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")


def fmt(n):
    s = ("%.3f" % n).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def P(d):
    return ("stroke", d)


def F(d):
    """A filled (solid) shape."""
    return ("fill", d)


def C(cx, cy, r):
    return ("stroke", "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0" % (
        fmt(cx - r), fmt(cy), fmt(r), fmt(r), fmt(2 * r), fmt(r), fmt(r), fmt(-2 * r)))


def FC(cx, cy, r):
    return ("fill", C(cx, cy, r)[1])


def R(x, y, w, h, rx=0):
    if rx <= 0:
        return ("stroke", "M%s,%s h%s v%s h%s z" % (fmt(x), fmt(y), fmt(w), fmt(h), fmt(-w)))
    return ("stroke",
            "M%s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s h%s "
            "a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s z" % (
                fmt(x + rx), fmt(y), fmt(w - 2 * rx),
                fmt(rx), fmt(rx), fmt(rx), fmt(rx),
                fmt(h - 2 * rx),
                fmt(rx), fmt(rx), fmt(-rx), fmt(rx),
                fmt(-(w - 2 * rx)),
                fmt(rx), fmt(rx), fmt(-rx), fmt(-rx),
                fmt(-(h - 2 * rx)),
                fmt(rx), fmt(rx), fmt(rx), fmt(-rx)))


def L(x1, y1, x2, y2):
    return ("stroke", "M%s,%s L%s,%s" % (fmt(x1), fmt(y1), fmt(x2), fmt(y2)))


def DOT(x, y):
    return ("stroke", "M%s,%s h.01" % (fmt(x), fmt(y)))


def PL(*pts, close=False):
    d = "M" + " L".join("%s,%s" % (fmt(x), fmt(y)) for x, y in pts)
    return ("stroke", d + (" z" if close else ""))


ICONS = {
    # ---- navigation & actions -------------------------------------------
    "arrow_back": [P("M19 12H5"), P("M12 19l-7-7 7-7")],
    "chevron_right": [P("M9 6l6 6-6 6")],
    "close": [P("M6 6l12 12M18 6L6 18")],
    "plus": [P("M12 5v14M5 12h14")],
    "refresh": [P("M20.5 12.5a8.5 8.5 0 1 1-2.6-6.1"), P("M20.5 4v5h-5")],
    "copy": [R(9, 9, 12, 12, 2), P("M5 15H4a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 4 3h9a1.5 1.5 0 0 1 1.5 1.5V5")],
    "trash": [P("M3.5 6h17"), P("M18.5 6l-.9 13.2A2 2 0 0 1 15.6 21H8.4a2 2 0 0 1-2-1.8L5.5 6"),
              P("M10 11v6M14 11v6"), P("M9 6V4.2A1.2 1.2 0 0 1 10.2 3h3.6A1.2 1.2 0 0 1 15 4.2V6")],
    "edit": [P("M12 20h9"), P("M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")],
    "play": [P("M7 4.8v14.4a.6.6 0 0 0 .9.5l11.6-7.2a.6.6 0 0 0 0-1L7.9 4.3a.6.6 0 0 0-.9.5z")],
    "stop": [R(6, 6, 12, 12, 2.5)],
    "send": [P("M21.5 2.5L10.5 13.5"), P("M21.5 2.5l-6.8 19-4.2-8-8-4.2z")],
    "warning": [P("M10.3 3.9L2.4 17.6A2 2 0 0 0 4.1 20.6h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"), P("M12 9.5v4"), DOT(12, 17)],
    "check_circle": [P("M21.5 11.1V12a9.5 9.5 0 1 1-5.6-8.7"), P("M21.5 4L12 13.5l-3-3")],
    "sun": [C(12, 12, 4.2), P("M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")],
    "moon": [P("M20.5 13.2A8.6 8.6 0 1 1 10.8 3.5a6.7 6.7 0 0 0 9.7 9.7z")],
    "contrast": [C(12, 12, 9.5), F("M12 2.5a9.5 9.5 0 0 1 0 19z")],
    "download": [P("M20.5 15v4a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-4"), P("M7 10.5l5 5 5-5"), P("M12 15.5v-12")],
    "upload_cloud": [P("M16 15.5l-4-4-4 4"), P("M12 11.5v9"), P("M20.4 18.4A5 5 0 0 0 18 9h-1.3A8 8 0 1 0 3 16.3")],
    "archive": [P("M21 8v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8"), R(1.5, 3, 21, 5, 1.5), P("M10 12.5h4")],
    "terminal": [P("M4.5 17l6-6-6-6"), P("M12.5 19h7")],
    "sliders": [P("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3"), P("M1.5 14h5M9.5 8h5M17.5 16h5")],
    "search": [C(10.8, 10.8, 7.3), L(21.5, 21.5, 16.2, 16.2)],
    # ---- features -------------------------------------------------------
    "link": [P("M15 7h2.5a5 5 0 0 1 0 10H15"), P("M9 17H6.5a5 5 0 0 1 0-10H9"), P("M8.5 12h7")],
    "gps": [C(12, 12, 3.2), C(12, 12, 7.5), P("M12 1.8v3M12 19.2v3M1.8 12h3M19.2 12h3")],
    "pin": [P("M20.5 10c0 6.8-8.5 12.2-8.5 12.2S3.5 16.8 3.5 10a8.5 8.5 0 0 1 17 0z"), C(12, 10, 3)],
    "bell": [P("M18 8.5A6 6 0 0 0 6 8.5c0 6.8-3 8.8-3 8.8h18s-3-2-3-8.8"), P("M13.7 21a2 2 0 0 1-3.4 0")],
    "bell_ring": [P("M18 8.5A6 6 0 0 0 6 8.5c0 6.8-3 8.8-3 8.8h18s-3-2-3-8.8"), P("M13.7 21a2 2 0 0 1-3.4 0"),
                  P("M2 7.5c.3-1.9 1.1-3.6 2.4-5"), P("M22 7.5c-.3-1.9-1.1-3.6-2.4-5")],
    "inbox": [P("M21.5 12.5h-5.2l-1.8 2.8h-5l-1.8-2.8H2.5"),
              P("M5.5 5.6L2.5 12.5V18a2 2 0 0 0 2 2h15a2 2 0 0 0 2-2v-5.5l-3-6.9A2 2 0 0 0 16.7 4.5H7.3a2 2 0 0 0-1.8 1.1z")],
    "battery": [R(2, 7, 17, 10, 2.5), P("M22.5 10.5v3"), P("M6 11v2")],
    "battery_charging": [P("M5.5 17H4.5A2.5 2.5 0 0 1 2 14.5v-5A2.5 2.5 0 0 1 4.5 7h3.2"),
                         P("M15 7h1.5A2.5 2.5 0 0 1 19 9.5v5a2.5 2.5 0 0 1-2.5 2.5h-3.2"),
                         P("M22.5 10.5v3"), P("M11.5 6.5L8 12h6l-3.5 5.5")],
    "volume": [P("M11 5L6.5 9H3v6h3.5l4.5 4z"), P("M15.5 8.5a5 5 0 0 1 0 7"), P("M19 5a10 10 0 0 1 0 14")],
    "shield_lock": [P("M12 22s8-3.8 8-10V5l-8-3-8 3v7c0 6.2 8 10 8 10z"), R(9, 11, 6, 5, 1.2), P("M10.2 11V9.8a1.8 1.8 0 0 1 3.6 0V11")],
    "shield_check": [P("M12 22s8-3.8 8-10V5l-8-3-8 3v7c0 6.2 8 10 8 10z"), P("M8.5 12l2.5 2.5 4.5-5")],
    "cast": [P("M2 16.1A5 5 0 0 1 5.9 20"), P("M2 12.1A9 9 0 0 1 9.9 20"), P("M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6"), DOT(2.3, 20)],
    "radio": [C(12, 12, 2), P("M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4"), P("M19.1 4.9a10 10 0 0 1 0 14.2M4.9 19.1a10 10 0 0 1 0-14.2")],
    "power": [P("M18.4 6.6a9 9 0 1 1-12.8 0"), P("M12 2.5v9")],
    "wifi": [P("M5 12.6a10.5 10.5 0 0 1 14 0"), P("M1.8 9a15.5 15.5 0 0 1 20.4 0"), P("M8.6 16.2a5.5 5.5 0 0 1 6.8 0"), DOT(12, 20)],
    "wifi_off": [P("M2.5 2.5l19 19"), P("M16.7 11.1A10.6 10.6 0 0 1 19 12.6"), P("M5 12.6a10.5 10.5 0 0 1 5.2-2.8"),
                 P("M10.7 5.1A15.6 15.6 0 0 1 22.2 9"), P("M1.8 9a15.5 15.5 0 0 1 4.4-2.7"), P("M8.6 16.2a5.5 5.5 0 0 1 6.8 0"), DOT(12, 20)],
    "bluetooth": [P("M6.5 7l11 10L12 22V2l5.5 5-11 10")],
    "clock": [C(12, 12, 9.5), P("M12 6.5V12l3.8 2.2")],
    "key": [C(7.5, 15.5, 4.5), P("M10.7 12.3L21 2"), P("M17 6l3 3"), P("M14.5 8.5l2 2")],
    "activity": [P("M22 12h-4l-3 9L9 3l-3 9H2")],
    "globe": [C(12, 12, 9.5), P("M2.5 12h19"), P("M12 2.5a14.5 14.5 0 0 1 0 19a14.5 14.5 0 0 1 0-19z")],
    "user": [C(12, 8, 4), P("M4.5 21a7.5 7.5 0 0 1 15 0")],
}

CMD = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]")
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def validate(name, d):
    """Mimics VectorDrawable's PathParser strictly enough to catch typos."""
    i, cmd, count = 0, None, 0
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)", d)
    if re.sub(r"[\s,]", "", d) != "".join(tokens):
        raise SystemExit("%s: unparseable path data: %r" % (name, d))
    pending = []
    first = True
    for t in tokens:
        if CMD.fullmatch(t):
            if pending and cmd and len(pending) % ARGS[cmd.upper()] != 0:
                raise SystemExit("%s: wrong argument count before %s in %r" % (name, t, d))
            if first and t not in "Mm":
                raise SystemExit("%s: path must start with a moveto: %r" % (name, d))
            first = False
            cmd, pending = t, []
        else:
            if cmd is None:
                raise SystemExit("%s: number before command in %r" % (name, d))
            pending.append(t)
    if cmd and cmd.upper() != "Z" and (not pending or len(pending) % ARGS[cmd.upper()] != 0):
        raise SystemExit("%s: wrong argument count at end of %r" % (name, d))


def to_xml(name, parts):
    strokes = [d for kind, d in parts if kind == "stroke"]
    fills = [d for kind, d in parts if kind == "fill"]
    for d in strokes + fills:
        validate(name, d)
    out = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp"', '    android:height="24dp"',
           '    android:viewportWidth="24"', '    android:viewportHeight="24">']
    if fills:
        out += ['    <path', '        android:fillColor="#FFFFFFFF"',
                '        android:pathData="%s" />' % " ".join(fills)]
    if strokes:
        out += ['    <path', '        android:pathData="%s"' % " ".join(strokes),
                '        android:strokeColor="#FFFFFFFF"', '        android:strokeWidth="2"',
                '        android:strokeLineCap="round"', '        android:strokeLineJoin="round" />']
    out.append('</vector>')
    return "\n".join(out) + "\n"


def sheet(path):
    cells = []
    for name, parts in ICONS.items():
        body = "".join(
            '<path d="%s" %s/>' % (d, 'fill="currentColor" stroke="none"' if kind == "fill"
                                   else 'fill="none" stroke="currentColor" stroke-width="2" '
                                        'stroke-linecap="round" stroke-linejoin="round"')
            for kind, d in parts)
        cells.append('<div class="c"><svg viewBox="0 0 24 24" width="40" height="40">%s</svg>'
                     '<span>%s</span></div>' % (body, name))
    html = """<!doctype html><meta charset=utf-8><style>
body{margin:0;font:11px sans-serif;display:flex}
.p{flex:1;padding:14px;display:flex;flex-wrap:wrap;gap:6px;align-content:flex-start}
.l{background:#f1f5f3;color:#10201a}.d{background:#0b1110;color:#6fe3b4}
.c{width:88px;height:78px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px}
</style><div class="p l">%s</div><div class="p d">%s</div>""" % ("".join(cells), "".join(cells))
    with open(path, "w") as f:
        f.write(html)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, parts in ICONS.items():
        with open(os.path.join(OUT, "ic_%s.xml" % name), "w") as f:
            f.write(to_xml(name, parts))
    print("wrote %d icons to %s" % (len(ICONS), OUT))
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])


if __name__ == "__main__":
    main()
