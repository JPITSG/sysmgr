#!/usr/bin/env python3
"""Launcher icon candidates for System Manager, from a single source.

Every candidate is an Android adaptive icon: a 108x108 background layer, a
108x108 foreground layer and a one-colour monochrome layer (Android 13+ themed
icons). The live content stays inside the central 66x66 "safe zone" so any
launcher mask (circle, squircle, rounded square) leaves it intact.

    python3 design/icons/launcher_icons.py --preview /tmp/icons.html
    python3 design/icons/launcher_icons.py --apply 3      # write res/ for candidate 3

Each shape is described once and emitted both as SVG (for the preview sheet)
and as an Android vector drawable (for the app), so what you preview is what
ships. Requires only Python 3.
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")


def f(n):
    s = ("%.2f" % n).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---- shape primitives: each is a dict -> SVG + VectorDrawable ---------------------------
def path(d, fill=None, stroke=None, sw=0, cap="round", join="round", opacity=1.0):
    return dict(kind="path", d=d, fill=fill, stroke=stroke, sw=sw, cap=cap, join=join, opacity=opacity)


def circle(cx, cy, r, **kw):
    d = "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0" % (f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))
    return path(d, **kw)


def rrect(x, y, w, h, r, **kw):
    d = ("M%s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s z" % (
        f(x + r), f(y), f(w - 2 * r), f(r), f(r), f(r), f(r), f(h - 2 * r), f(r), f(r), f(-r), f(r),
        f(-(w - 2 * r)), f(r), f(r), f(-r), f(-r), f(-(h - 2 * r)), f(r), f(r), f(r), f(-r)))
    return path(d, **kw)


def arc(cx, cy, r, a0, a1, **kw):
    """Open arc from angle a0 to a1 degrees (0 = east, clockwise on screen)."""
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    large = 1 if (a1 - a0) % 360 > 180 else 0
    d = "M%s,%s A%s,%s 0 %d,1 %s,%s" % (f(x0), f(y0), f(r), f(r), large, f(x1), f(y1))
    return path(d, fill=None, **kw)


class Grad:
    def __init__(self, x0, y0, x1, y1, stops):
        self.x0, self.y0, self.x1, self.y1, self.stops = x0, y0, x1, y1, stops  # stops: [(offset, "#AARRGGBB"|"#RRGGBB")]


class Radial:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r, self.stops = cx, cy, r, stops


# ---- the six candidates ---------------------------------------------------------------------------------
def bg_full(grad_stops, angle=(0, 0, 108, 108), glow=None):
    shapes = [path("M0,0h108v108h-108z", fill=Grad(*angle, grad_stops))]
    if glow:
        shapes.append(path("M0,0h108v108h-108z", fill=glow))
    return shapes


def candidate_1():
    """Pulse Shield: a shield that carries a heartbeat line."""
    bg = bg_full([(0, "#14BE90"), (1, "#0A5A72")], glow=Radial(30, 18, 84, [(0, "#66FFFFFF"), (1, "#00FFFFFF")]))
    shield = "M54,23 L78,31.5 V54 C78,69 67,78 54,85 C41,78 30,69 30,54 V31.5 Z"
    beat = "M37,54 H47.5 L52,43 L58.5,66 L63,54 H71"
    fg = [
        path(shield, fill=Grad(30, 23, 78, 85, [(0, "#66FFFFFF"), (1, "#1AFFFFFF")])),
        path(shield, stroke="#FFFFFF", sw=4.8),
        path(beat, stroke="#FFFFFF", sw=4.8),
    ]
    mono = [path(shield, stroke="#000000", sw=4.8), path(beat, stroke="#000000", sw=4.8)]
    return dict(name="Pulse Shield", bg=bg, fg=fg, mono=mono,
                note="Protection plus live monitoring: a shield carrying a heartbeat.")


def candidate_2():
    """Control Ring: the segmented status dial from the dashboard."""
    bg = bg_full([(0, "#0D3640"), (1, "#06191E")], glow=Radial(54, 54, 62, [(0, "#4014E0A4"), (1, "#0014E0A4")]))
    segs = 10
    lit = [0, 1, 2, 3, 4, 5, 6]
    fg = []
    mono = []
    for i in range(segs):
        a0 = -90 + i * (360 / segs) + 5.5
        a1 = -90 + (i + 1) * (360 / segs) - 5.5
        if i in lit:
            t = lit.index(i) / float(len(lit) - 1)
            r = int(0x3F + (0x35 - 0x3F) * t)
            g = int(0xE8 + (0xC4 - 0xE8) * t)
            b = int(0xA8 + (0xF0 - 0xA8) * t)
            col = "#%02X%02X%02X" % (r, g, b)
            mono.append(arc(54, 54, 25.5, a0, a1, stroke="#000000", sw=7.6))
        else:
            col = "#2B6B66"
        fg.append(arc(54, 54, 25.5, a0, a1, stroke=col, sw=7.6))
    fg.append(circle(54, 54, 8.2, fill="#FFFFFF"))
    fg.append(circle(54, 54, 3.6, fill="#0D3640"))
    mono.append(circle(54, 54, 8.2, fill="#000000"))
    return dict(name="Control Ring", bg=bg, fg=fg, mono=mono,
                note="The segmented status dial from the app's overview, lit segment by segment.")


def candidate_3():
    """Beacon Phone: a phone with a signal radiating from its corner."""
    bg = bg_full([(0, "#7A66FF"), (1, "#2C2FA6")], glow=Radial(82, 22, 84, [(0, "#66FFFFFF"), (1, "#00FFFFFF")]))
    x, y, w, h, r = 38, 36, 28, 50, 7.5
    body = ("M%s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s z" % (
        f(x + r), f(y), f(w - 2 * r), f(r), f(r), f(r), f(r), f(h - 2 * r), f(r), f(r), f(-r), f(r),
        f(-(w - 2 * r)), f(r), f(r), f(-r), f(-r), f(-(h - 2 * r)), f(r), f(r), f(r), f(-r)))
    fg = [
        path(body, fill=Grad(x, y, x + w, y + h, [(0, "#55FFFFFF"), (1, "#14FFFFFF")])),
        path(body, stroke="#FFFFFF", sw=4.6),
        circle(52, 59, 4.6, fill="#9CFFE0"),
        path("M46,78 H58", stroke="#FFFFFF", sw=3.4, opacity=0.9),
        arc(66, 44, 10, -86, -4, stroke="#FFFFFF", sw=4.4),
        arc(66, 44, 18, -86, -4, stroke="#FFFFFF", sw=4.4, opacity=0.72),
        arc(66, 44, 26, -86, -4, stroke="#FFFFFF", sw=4.4, opacity=0.42),
    ]
    mono = [path(body, stroke="#000000", sw=4.6), circle(52, 59, 4.6, fill="#000000"),
            arc(66, 44, 10, -86, -4, stroke="#000000", sw=4.4), arc(66, 44, 18, -86, -4, stroke="#000000", sw=4.4),
            arc(66, 44, 26, -86, -4, stroke="#000000", sw=4.4)]
    return dict(name="Beacon Phone", bg=bg, fg=fg, mono=mono,
                note="A phone with a signal radiating from its corner: the device you manage, reporting in.")


def candidate_4():
    """Radar Pin: a radar scope with a location pin at the centre."""
    bg = bg_full([(0, "#FFA23F"), (1, "#E23D33")], glow=Radial(26, 24, 74, [(0, "#66FFFFFF"), (1, "#00FFFFFF")]))
    pin = "M54,31 C44.6,31 37.5,38.3 37.5,47.4 C37.5,59.8 54,76 54,76 C54,76 70.5,59.8 70.5,47.4 C70.5,38.3 63.4,31 54,31 Z"
    fg = [
        circle(54, 54, 33, stroke="#FFFFFF", sw=2.8, opacity=0.32),
        circle(54, 54, 22, stroke="#FFFFFF", sw=2.8, opacity=0.5),
        path("M54,54 L82,38 A33,33 0 0,0 72,26 Z", fill="#FFFFFF", opacity=0.24),
        path("M54,81 m-11,0 a11,3.6 0 1,0 22,0 a11,3.6 0 1,0 -22,0", fill="#000000", opacity=0.16),
        path(pin, fill="#FFFFFF"),
        circle(54, 47.4, 6.6, fill="#E23D33"),
    ]
    mono = [circle(54, 54, 33, stroke="#000000", sw=2.8), circle(54, 54, 22, stroke="#000000", sw=2.8), path(pin, fill="#000000")]
    return dict(name="Radar Pin", bg=bg, fg=fg, mono=mono,
                note="Find-my-phone energy: a radar sweep around a location pin.")


def candidate_5():
    """Hub: a central node with three satellites, a manager of many devices."""
    bg = bg_full([(0, "#13242E"), (1, "#070C11")], glow=Radial(28, 26, 86, [(0, "#3A3FD6A0"), (1, "#003FD6A0")]))
    cx, cy, ring = 54, 55, 25
    nodes = [(cx + ring * math.cos(math.radians(a)), cy + ring * math.sin(math.radians(a))) for a in (-90, 30, 150)]
    fg = [circle(cx, cy, ring, stroke="#FFFFFF", sw=3.0, opacity=0.24)]
    mono = [circle(cx, cy, ring, stroke="#000000", sw=3.0)]
    for (x, y) in nodes:
        fg.append(path("M%s,%s L%s,%s" % (f(cx), f(cy), f(x), f(y)), stroke="#FFFFFF", sw=3.4, opacity=0.5))
        mono.append(path("M%s,%s L%s,%s" % (f(cx), f(cy), f(x), f(y)), stroke="#000000", sw=3.4))
    fg.append(circle(cx, cy, 11.5, fill=Grad(cx - 11, cy - 11, cx + 11, cy + 11, [(0, "#8CF7D4"), (1, "#2FCB9A")])))
    fg.append(circle(cx, cy, 4.2, fill="#0A3A31"))
    mono.append(circle(cx, cy, 11.5, fill="#000000"))
    for (x, y) in nodes:
        fg.append(circle(x, y, 7.2, fill="#FFFFFF"))
        fg.append(circle(x, y, 2.9, fill="#13242E"))
        mono.append(circle(x, y, 7.2, fill="#000000"))
    return dict(name="Hub", bg=bg, fg=fg, mono=mono,
                note="One manager at the centre, three devices around it: the network you look after.")


def candidate_6():
    """Command Tile: the tile grid with one lit tile and a prompt."""
    bg = bg_full([(0, "#22386A"), (1, "#0C1633")], glow=Radial(80, 24, 84, [(0, "#445B8CFF"), (1, "#005B8CFF")]))
    t = 21
    gap = 6
    x0 = 54 - t - gap / 2
    y0 = 54 - t - gap / 2
    tiles = [(x0, y0), (x0 + t + gap, y0), (x0, y0 + t + gap), (x0 + t + gap, y0 + t + gap)]
    fg = []
    mono = []
    for i, (x, y) in enumerate(tiles):
        lit = i == 0
        if lit:
            fg.append(rrect(x, y, t, t, 6.5, fill=Grad(x, y, x + t, y + t, [(0, "#7CF5CD"), (1, "#2FCB9A")])))
        else:
            fg.append(rrect(x, y, t, t, 6.5, fill="#FFFFFF", opacity=0.22))
        mono.append(rrect(x, y, t, t, 6.5, fill="#000000"))
    px, py = x0 + t + gap + 5.2, y0 + t + gap + 5.6
    fg.append(path("M%s,%s l6.2,4.6 l-6.2,4.6" % (f(px), f(py)), stroke="#FFFFFF", sw=3.6))
    fg.append(path("M%s,%s h5.6" % (f(px + 8.2), f(py + 10.6)), stroke="#FFFFFF", sw=3.6))
    return dict(name="Command Tile", bg=bg, fg=fg, mono=mono,
                note="The overview's tile grid with one tile live, plus a command prompt.")


CANDIDATES = [candidate_1, candidate_2, candidate_3, candidate_4, candidate_5, candidate_6]


# ---- SVG emission --------------------------------------------------------------------------------------------------
def svg_defs(shapes, prefix):
    defs = []
    n = 0
    for s in shapes:
        fill = s.get("fill")
        if isinstance(fill, Grad):
            n += 1
            gid = "%s%d" % (prefix, n)
            stops = "".join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % ((o,) + svg_color(c)) for o, c in fill.stops)
            defs.append('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>'
                        % (gid, f(fill.x0), f(fill.y0), f(fill.x1), f(fill.y1), stops))
            s["_gid"] = gid
        elif isinstance(fill, Radial):
            n += 1
            gid = "%s%d" % (prefix, n)
            stops = "".join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % ((o,) + svg_color(c)) for o, c in fill.stops)
            defs.append('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s</radialGradient>'
                        % (gid, f(fill.cx), f(fill.cy), f(fill.r), stops))
            s["_gid"] = gid
    return "".join(defs)


def svg_color(c):
    c = c.lstrip("#")
    if len(c) == 8:
        a = int(c[0:2], 16) / 255.0
        return "#" + c[2:], "%.3f" % a
    return "#" + c, "1"


def svg_shapes(shapes):
    out = []
    for s in shapes:
        fill = s.get("fill")
        if isinstance(fill, (Grad, Radial)):
            fa = 'fill="url(#%s)"' % s["_gid"]
        elif fill:
            col, op = svg_color(fill)
            fa = 'fill="%s" fill-opacity="%s"' % (col, op)
        else:
            fa = 'fill="none"'
        if s.get("stroke"):
            col, op = svg_color(s["stroke"])
            sa = ' stroke="%s" stroke-opacity="%s" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="%s"' % (
                col, op, f(s["sw"]), s["cap"], s["join"])
        else:
            sa = ""
        oa = ' opacity="%s"' % f(s["opacity"]) if s.get("opacity", 1.0) != 1.0 else ""
        out.append('<path d="%s" %s%s%s/>' % (s["d"], fa, sa, oa))
    return "".join(out)


def svg_icon(c, prefix, size=108, mask="circle"):
    shapes = c["bg"] + c["fg"]
    defs = svg_defs(c["bg"] + c["fg"], prefix + "g")
    body = svg_shapes(c["bg"]) + svg_shapes(c["fg"])
    masks = {
        "circle": '<circle cx="54" cy="54" r="54"/>',
        "squircle": '<path d="M54,0 C94,0 108,14 108,54 C108,94 94,108 54,108 C14,108 0,94 0,54 C0,14 14,0 54,0 Z"/>',
        "round": '<rect width="108" height="108" rx="28"/>',
        "none": '<rect width="108" height="108"/>',
    }
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="%d" height="%d">'
            '<defs>%s<clipPath id="%sm">%s</clipPath></defs><g clip-path="url(#%sm)">%s</g></svg>'
            % (size, size, defs, prefix, masks[mask], prefix, body))


# ---- Android emission ----------------------------------------------------------------------------------------------
def a_color(c):
    c = c.lstrip("#")
    return "#" + (c.upper() if len(c) == 8 else "FF" + c.upper())


def a_fill(fill, indent):
    if isinstance(fill, Grad):
        stops = "".join('\n%s    <item android:color="%s" android:offset="%s" />' % (indent, a_color(c), o) for o, c in fill.stops)
        return ('\n%s<aapt:attr name="android:fillColor">\n%s    <gradient android:startX="%s" android:startY="%s" '
                'android:endX="%s" android:endY="%s" android:type="linear">%s\n%s    </gradient>\n%s</aapt:attr>'
                % (indent, indent, f(fill.x0), f(fill.y0), f(fill.x1), f(fill.y1), stops, indent, indent)), True
    if isinstance(fill, Radial):
        stops = "".join('\n%s    <item android:color="%s" android:offset="%s" />' % (indent, a_color(c), o) for o, c in fill.stops)
        return ('\n%s<aapt:attr name="android:fillColor">\n%s    <gradient android:centerX="%s" android:centerY="%s" '
                'android:gradientRadius="%s" android:type="radial">%s\n%s    </gradient>\n%s</aapt:attr>'
                % (indent, indent, f(fill.cx), f(fill.cy), f(fill.r), stops, indent, indent)), True
    return None, False


def a_shapes(shapes):
    out = []
    for s in shapes:
        attrs = ['android:pathData="%s"' % s["d"]]
        child = ""
        fill = s.get("fill")
        grad, is_grad = a_fill(fill, "            ")
        if is_grad:
            child = grad
        elif fill:
            attrs.append('android:fillColor="%s"' % a_color(fill))
            if s.get("opacity", 1.0) != 1.0:
                attrs.append('android:fillAlpha="%s"' % f(s["opacity"]))
        if s.get("stroke"):
            attrs.append('android:strokeColor="%s"' % a_color(s["stroke"]))
            attrs.append('android:strokeWidth="%s"' % f(s["sw"]))
            attrs.append('android:strokeLineCap="%s"' % s["cap"])
            attrs.append('android:strokeLineJoin="%s"' % s["join"])
            if s.get("opacity", 1.0) != 1.0:
                attrs.append('android:strokeAlpha="%s"' % f(s["opacity"]))
        text = "    <path\n        " + "\n        ".join(attrs)
        text += (">" + child + "\n    </path>") if child else " />"
        out.append(text)
    return "\n".join(out)


def vector_xml(shapes):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n%s\n</vector>\n' % a_shapes(shapes))


def stat_icon_xml(shapes):
    """The status-bar glyph: the monochrome layer cropped to the safe zone, in white."""
    white = []
    for shape in shapes:
        d = dict(shape)
        if d.get("stroke"):
            d["stroke"] = "#FFFFFF"
        if d.get("fill"):
            d["fill"] = "#FFFFFF"
        white.append(d)
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="66"\n    android:viewportHeight="66">\n'
            '    <group\n        android:translateX="-21"\n        android:translateY="-21">\n%s\n    </group>\n</vector>\n'
            % "\n".join("    " + line for line in a_shapes(white).split("\n")))


def apply(index):
    c = CANDIDATES[index - 1]()
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    for name, shapes in (("ic_launcher_background", c["bg"]), ("ic_launcher_foreground", c["fg"]),
                         ("ic_launcher_monochrome", c["mono"])):
        with open(os.path.join(RES, "drawable", name + ".xml"), "w") as fh:
            fh.write(vector_xml(shapes))
    with open(os.path.join(RES, "drawable", "ic_stat_system_manager.xml"), "w") as fh:
        fh.write(stat_icon_xml(c["mono"]))
    with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as fh:
        fh.write('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                 '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                 '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                 '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                 '</adaptive-icon>\n')
    print("applied candidate %d: %s" % (index, c["name"]))


def preview(path_out):
    cells = []
    for i, fn in enumerate(CANDIDATES, 1):
        c = fn()
        big = svg_icon(c, "p%d" % i, 200, "squircle")
        sizes = "".join(svg_icon(c, "s%d_%d" % (i, k), k, "circle") for k in (96, 64, 48, 36))
        shapes = "".join('<span class="m">%s</span>' % svg_icon(c, "m%d_%s" % (i, m), 56, m) for m in ("circle", "squircle", "round"))
        mono_defs = ""
        mono = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="56" height="56"><rect width="108" height="108" rx="54" fill="#d7e3df"/>'
                '<g style="filter:invert(0)" fill="none">%s</g></svg>' % svg_shapes([dict(s, stroke="#233" if s.get("stroke") else None, fill="#233" if s.get("fill") else None) for s in c["mono"]]))
        cells.append('<div class="card"><div class="n">%d</div><div class="big">%s</div><h3>%s</h3><p>%s</p>'
                     '<div class="row">%s</div><div class="row">%s<span class="m">%s</span></div></div>'
                     % (i, big, c["name"], c["note"], sizes, shapes, mono))
    html = """<!doctype html><meta charset=utf-8><title>System Manager icons</title><style>
body{margin:0;background:#101614;color:#e8f1ee;font:14px/1.4 Roboto,Helvetica,Arial,sans-serif;padding:28px}
h1{font-size:22px;margin:0 0 4px}.sub{color:#8fa59e;margin:0 0 22px}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;max-width:1280px}
.card{background:#172120;border:1px solid #243430;border-radius:22px;padding:22px 18px;position:relative;text-align:center}
.n{position:absolute;left:16px;top:14px;width:30px;height:30px;border-radius:15px;background:#3fd6a0;color:#00281b;font-weight:700;line-height:30px}
.big{display:flex;justify-content:center;margin:6px 0 10px;filter:drop-shadow(0 10px 18px rgba(0,0,0,.45))}
h3{margin:2px 0 4px;font-size:18px}p{margin:0 auto 14px;color:#9ab0a9;max-width:300px;font-size:13px}
.row{display:flex;gap:12px;justify-content:center;align-items:center;margin-top:10px}.m{display:inline-flex}
</style><h1>System Manager - launcher icon candidates</h1><p class=sub>Each shown as a squircle, then at launcher sizes, under three masks, and as the single-colour themed icon.</p>
<div class=grid>%s</div>""" % "".join(cells)
    with open(path_out, "w") as fh:
        fh.write(html)
    print("preview written to", path_out)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    if "--apply" in sys.argv:
        apply(int(sys.argv[sys.argv.index("--apply") + 1]))
