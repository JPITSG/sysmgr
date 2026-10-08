#!/usr/bin/env python3
"""The System Manager launcher icon ("Hub"), from a single source.

An Android adaptive icon: a 108x108 background layer, a 108x108 foreground layer
and a one-colour monochrome layer (Android 13+ themed icons), plus the white
status-bar glyph derived from that monochrome layer.

Launchers mask the 108-unit canvas down to a 72-unit window (circle, squircle,
rounded square) and only promise that a 66-unit circle in the middle survives
every mask. The artwork is kept well inside that circle: --check measures how
close it really comes (by rendering it, not by trusting the numbers below) and
fails if it gets within MIN_SAFE_MARGIN units, so the icon cannot drift into
hugging the edge. The status-bar glyph is checked for at least 2.4dp of padding
in its 24dp box (the platform convention is 2dp).

    python3 design/icons/launcher_icons.py --check
    python3 design/icons/launcher_icons.py --apply             # write app/src/main/res
    python3 design/icons/launcher_icons.py --preview sheet.html

Every shape is described once and emitted both as SVG (the preview) and as an
Android vector drawable (the app), so what you preview is what ships. Only
Python 3 is needed to --apply; --check also needs Pillow and Inkscape.
"""
import math
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")

CANVAS = 108.0
CENTRE = CANVAS / 2
SAFE_RADIUS = 33.0         # the circle every launcher mask preserves
MASK_RADIUS = 36.0         # the visible window of a circular mask
MIN_SAFE_MARGIN = 6.0      # artwork must stay at least this far inside the safe circle
LIVE_AREA = 19.0 / 24.0    # the glyph fills 19dp of its 24dp box: 2.5dp of padding all round
MIN_STATUS_PADDING_DP = 2.4


def f(n):
    s = ("%.2f" % n).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---- shape primitives: each is a dict -> SVG + VectorDrawable -----------------------------
def path(d, fill=None, stroke=None, sw=0, cap="round", join="round", opacity=1.0, even_odd=False):
    return dict(d=d, fill=fill, stroke=stroke, sw=sw, cap=cap, join=join, opacity=opacity, even_odd=even_odd)


def circle_d(cx, cy, r):
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0" % (
        f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))


def circle(cx, cy, r, **kw):
    return path(circle_d(cx, cy, r), **kw)


def ring_disc(cx, cy, r, hole, **kw):
    """A disc with a hole punched through it (even-odd fill)."""
    return path(circle_d(cx, cy, r) + " " + circle_d(cx, cy, hole), even_odd=True, **kw)


def line(x0, y0, x1, y1, **kw):
    return path("M%s,%s L%s,%s" % (f(x0), f(y0), f(x1), f(y1)), **kw)


class Grad:
    def __init__(self, x0, y0, x1, y1, stops):
        self.x0, self.y0, self.x1, self.y1, self.stops = x0, y0, x1, y1, stops


class Radial:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r, self.stops = cx, cy, r, stops


# ---- the icon ----------------------------------------------------------------------------------
RING_R = 19.5          # the orbit the devices sit on
NODE_R = 6.2           # a device
CORE_R = 10.0          # the manager at the centre
RING_SW = 3.0
SPOKE_SW = 3.4
MONO_RING_SW = 2.4     # the single-colour layers lighten the orbit so the devices lead
MONO_SPOKE_SW = 3.0
NODE_ANGLES = (-90, 30, 150)


def hub():
    """One manager at the centre, three devices around it: the network you look after.

    The hub is centred on the canvas, so the three devices are equally far from the
    middle and no mask can favour one of them. The farthest point of the artwork is
    RING_R + NODE_R from the centre.
    """
    bg = [path("M0,0h108v108h-108z", fill=Grad(0, 0, 108, 108, [(0, "#13242E"), (1, "#070C11")])),
          path("M0,0h108v108h-108z", fill=Radial(28, 26, 86, [(0, "#3A3FD6A0"), (1, "#003FD6A0")]))]
    directions = [(math.cos(math.radians(a)), math.sin(math.radians(a))) for a in NODE_ANGLES]
    nodes = [(CENTRE + RING_R * dx, CENTRE + RING_R * dy) for dx, dy in directions]

    def spoke(sw, colour, opacity=1.0):
        # Rim to rim, so a spoke never crosses the hole of the hub or fills a device.
        return [line(CENTRE + (CORE_R - 1.0) * dx, CENTRE + (CORE_R - 1.0) * dy,
                     CENTRE + (RING_R - NODE_R + 1.0) * dx, CENTRE + (RING_R - NODE_R + 1.0) * dy,
                     stroke=colour, sw=sw, opacity=opacity) for dx, dy in directions]

    fg = [circle(CENTRE, CENTRE, RING_R, stroke="#FFFFFF", sw=RING_SW, opacity=0.24)]
    fg += spoke(SPOKE_SW, "#FFFFFF", 0.5)
    fg.append(circle(CENTRE, CENTRE, CORE_R, fill=Grad(CENTRE - CORE_R, CENTRE - CORE_R, CENTRE + CORE_R,
                                                       CENTRE + CORE_R, [(0, "#8CF7D4"), (1, "#2FCB9A")])))
    fg.append(circle(CENTRE, CENTRE, 3.6, fill="#0A3A31"))
    for x, y in nodes:
        fg.append(circle(x, y, NODE_R, fill="#FFFFFF"))
        fg.append(circle(x, y, 2.5, fill="#13242E"))

    # Single colour (themed icons and the status bar): an orbit, three solid devices, a donut core.
    mono = [circle(CENTRE, CENTRE, RING_R, stroke="#000000", sw=MONO_RING_SW)]
    mono += spoke(MONO_SPOKE_SW, "#000000")
    mono.append(ring_disc(CENTRE, CENTRE, CORE_R, 4.0, fill="#000000"))
    for x, y in nodes:
        mono.append(circle(x, y, NODE_R, fill="#000000"))

    # Bounding box of the monochrome artwork, for sizing the status-bar glyph.
    half = max(RING_R * math.cos(math.radians(30)) + NODE_R, RING_R + MONO_RING_SW / 2)
    top = CENTRE - (RING_R + NODE_R)
    bottom = CENTRE + max(RING_R + MONO_RING_SW / 2, RING_R * math.sin(math.radians(30)) + NODE_R)
    return dict(name="Hub", bg=bg, fg=fg, mono=mono, bbox=(CENTRE - half, top, CENTRE + half, bottom))


# ---- SVG emission ----------------------------------------------------------------------------------
def svg_color(c):
    c = c.lstrip("#")
    if len(c) == 8:
        return "#" + c[2:], "%.3f" % (int(c[0:2], 16) / 255.0)
    return "#" + c, "1"


def svg_defs(shapes, prefix):
    defs, n = [], 0
    for s in shapes:
        fill = s.get("fill")
        if isinstance(fill, Grad):
            n += 1
            gid = "%s%d" % (prefix, n)
            stops = "".join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % ((o,) + svg_color(c))
                            for o, c in fill.stops)
            defs.append('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s'
                        '</linearGradient>' % (gid, f(fill.x0), f(fill.y0), f(fill.x1), f(fill.y1), stops))
            s["_gid"] = gid
        elif isinstance(fill, Radial):
            n += 1
            gid = "%s%d" % (prefix, n)
            stops = "".join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % ((o,) + svg_color(c))
                            for o, c in fill.stops)
            defs.append('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s'
                        '</radialGradient>' % (gid, f(fill.cx), f(fill.cy), f(fill.r), stops))
            s["_gid"] = gid
    return "".join(defs)


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
        if s.get("even_odd"):
            fa += ' fill-rule="evenodd"'
        sa = ""
        if s.get("stroke"):
            col, op = svg_color(s["stroke"])
            sa = ' stroke="%s" stroke-opacity="%s" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="%s"' % (
                col, op, f(s["sw"]), s["cap"], s["join"])
        oa = ' opacity="%s"' % f(s["opacity"]) if s.get("opacity", 1.0) != 1.0 else ""
        out.append('<path d="%s" %s%s%s/>' % (s["d"], fa, sa, oa))
    return "".join(out)


MASKS = {
    "circle": '<circle cx="54" cy="54" r="36"/>',
    "squircle": '<path d="M54,18 C82,18 90,26 90,54 C90,82 82,90 54,90 C26,90 18,82 18,54 C18,26 26,18 54,18 Z"/>',
    "round": '<rect x="18" y="18" width="72" height="72" rx="20"/>',
}


def svg_icon(c, prefix, size, mask, guides=False):
    """The icon as a launcher shows it: the 72-unit window of the canvas through a mask."""
    shapes = c["bg"] + c["fg"]
    defs = svg_defs(shapes, prefix + "g")
    guide = ""
    if guides:
        guide = ('<circle cx="54" cy="54" r="%s" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" '
                 'stroke-width="0.35" stroke-dasharray="1.6 1.2"/>'
                 '<circle cx="54" cy="54" r="%s" fill="none" stroke="#FFD166" stroke-opacity="0.9" stroke-width="0.35" '
                 'stroke-dasharray="0.8 1.2"/>' % (f(SAFE_RADIUS), f(SAFE_RADIUS - MIN_SAFE_MARGIN)))
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="%d" height="%d">'
            '<defs>%s<clipPath id="%sm">%s</clipPath></defs><g clip-path="url(#%sm)">%s</g>%s</svg>'
            % (size, size, defs, prefix, MASKS[mask], prefix, svg_shapes(c["bg"]) + svg_shapes(c["fg"]), guide))


# ---- Android emission -------------------------------------------------------------------------------
def a_color(c):
    c = c.lstrip("#")
    return "#" + (c.upper() if len(c) == 8 else "FF" + c.upper())


def a_fill(fill, indent):
    if isinstance(fill, Grad):
        stops = "".join('\n%s    <item android:color="%s" android:offset="%s" />' % (indent, a_color(c), o)
                        for o, c in fill.stops)
        return ('\n%s<aapt:attr name="android:fillColor">\n%s    <gradient android:startX="%s" android:startY="%s" '
                'android:endX="%s" android:endY="%s" android:type="linear">%s\n%s    </gradient>\n%s</aapt:attr>'
                % (indent, indent, f(fill.x0), f(fill.y0), f(fill.x1), f(fill.y1), stops, indent, indent)), True
    if isinstance(fill, Radial):
        stops = "".join('\n%s    <item android:color="%s" android:offset="%s" />' % (indent, a_color(c), o)
                        for o, c in fill.stops)
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
        if s.get("even_odd"):
            attrs.append('android:fillType="evenOdd"')
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


def status_geometry(c):
    """Viewport size and translation that centre the glyph in 24dp with 2dp of padding all round."""
    x0, y0, x1, y1 = c["bbox"]
    size = max(x1 - x0, y1 - y0) / LIVE_AREA
    return size, size / 2 - (x0 + x1) / 2, size / 2 - (y0 + y1) / 2


def white(shapes):
    out = []
    for shape in shapes:
        d = dict(shape)
        if d.get("stroke"):
            d["stroke"] = "#FFFFFF"
        if d.get("fill"):
            d["fill"] = "#FFFFFF"
        out.append(d)
    return out


def stat_icon_xml(c):
    size, tx, ty = status_geometry(c)
    body = "\n".join("    " + line for line in a_shapes(white(c["mono"])).split("\n"))
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="%s"\n    android:viewportHeight="%s">\n'
            '    <group\n        android:translateX="%s"\n        android:translateY="%s">\n%s\n    </group>\n</vector>\n'
            % (f(size), f(size), f(tx), f(ty), body))


def apply():
    c = hub()
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    for name, shapes in (("ic_launcher_background", c["bg"]), ("ic_launcher_foreground", c["fg"]),
                         ("ic_launcher_monochrome", c["mono"])):
        with open(os.path.join(RES, "drawable", name + ".xml"), "w") as fh:
            fh.write(vector_xml(shapes))
    with open(os.path.join(RES, "drawable", "ic_stat_system_manager.xml"), "w") as fh:
        fh.write(stat_icon_xml(c))
    with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as fh:
        fh.write('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                 '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                 '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                 '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                 '</adaptive-icon>\n')
    print("wrote the %s icon to %s" % (c["name"], RES))


# ---- measuring --------------------------------------------------------------------------------------------
def render_alpha(svg_body, view_box, pixels):
    """Rasterises shapes on a transparent background and returns the alpha channel, or None."""
    if not shutil.which("inkscape"):
        return None
    try:
        from PIL import Image
    except ImportError:
        return None
    folder = tempfile.mkdtemp()
    src, out = os.path.join(folder, "a.svg"), os.path.join(folder, "a.png")
    with open(src, "w") as fh:
        fh.write('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s" width="%d" height="%d">%s</svg>'
                 % (view_box, pixels, pixels, svg_body))
    subprocess.run(["inkscape", src, "-e", out, "-w", str(pixels), "-h", str(pixels)], capture_output=True)
    try:
        return Image.open(out).convert("RGBA").split()[3]
    finally:
        shutil.rmtree(folder, ignore_errors=True)


def measure(c):
    """Returns (artwork radius in units, status padding in dp), or None without Inkscape and Pillow."""
    shapes = c["fg"]
    alpha = render_alpha("<defs>%s</defs>%s" % (svg_defs(shapes, "m"), svg_shapes(shapes)), "0 0 108 108", 1080)
    if alpha is None:
        return None
    px, far = alpha.load(), 0.0
    for y in range(0, 1080, 2):
        for x in range(0, 1080, 2):
            if px[x, y] > 12:
                far = max(far, math.hypot(x - 540, y - 540))
    size, tx, ty = status_geometry(c)
    glyph = white(c["mono"])
    body = '<g transform="translate(%s %s)">%s</g>' % (f(tx), f(ty), svg_shapes(glyph))
    alpha = render_alpha(body, "0 0 %s %s" % (f(size), f(size)), 960)
    px, lo, hi = alpha.load(), 960, 0
    for y in range(0, 960, 2):
        for x in range(0, 960, 2):
            if px[x, y] > 12:
                lo, hi = min(lo, x, y), max(hi, x, y)
    padding = min(lo, 960 - hi) / 960.0 * 24.0
    return far / 10.0, padding


def check():
    result = measure(hub())
    if result is None:
        print("skipped: --check needs Pillow and Inkscape")
        return 0
    radius, padding = result
    safe_margin = SAFE_RADIUS - radius
    print("launcher foreground: farthest artwork %.1f units from the centre" % radius)
    print("  margin to the %.0f-unit safe circle: %.1f (minimum %.1f)" % (SAFE_RADIUS, safe_margin, MIN_SAFE_MARGIN))
    print("  margin to the edge of a circular mask: %.1f" % (MASK_RADIUS - radius))
    print("status-bar glyph: %.1f dp of padding on its tightest side (minimum %.1f)" % (padding, MIN_STATUS_PADDING_DP))
    ok = safe_margin >= MIN_SAFE_MARGIN and padding >= MIN_STATUS_PADDING_DP
    print("OK" if ok else "FAIL: the artwork is hugging its margins")
    return 0 if ok else 1


# ---- preview ---------------------------------------------------------------------------------------------------
def preview(path_out):
    c = hub()
    big = svg_icon(c, "big", 280, "squircle", guides=True)
    circle_big = svg_icon(c, "cb", 280, "circle", guides=True)
    masks = "".join('<span>%s</span>' % svg_icon(c, "m" + m, 88, m) for m in MASKS)
    sizes = "".join('<span>%s</span>' % svg_icon(c, "s%d" % k, k, "circle") for k in (72, 56, 44, 34))
    size, tx, ty = status_geometry(c)
    glyph = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="%%d" height="%%d">'
             '<g transform="translate(%s %s)" fill="#fff">%s</g></svg>'
             % (f(size), f(size), f(tx), f(ty), svg_shapes(white(c["mono"]))))
    bar = ('<div class="bar"><span class="clock">12:30</span><span class="icons">%s%s</span></div>'
           % (glyph % (24, 24), '<span class="dot"></span><span class="dot"></span>'))
    zoom = '<div class="zoom">%s<small>status-bar glyph, enlarged; the box is its 24dp frame</small></div>' % (glyph % (120, 120))

    def themed(bg, fg):
        shapes = "".join('<path d="%s" fill="%s" fill-rule="evenodd" stroke="%s" stroke-width="%s" stroke-linecap="round"/>'
                         % (s["d"], fg if s.get("fill") else "none", fg if s.get("stroke") else "none", f(s.get("sw", 0)))
                         for s in c["mono"])
        return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="88" height="88">'
                '<circle cx="54" cy="54" r="36" fill="%s"/>%s</svg>' % (bg, shapes))

    html = """<!doctype html><meta charset=utf-8><title>System Manager icon</title><style>
body{margin:0;background:#101614;color:#e8f1ee;font:14px/1.45 Roboto,Helvetica,Arial,sans-serif;padding:32px}
h1{font-size:22px;margin:0 0 4px}.sub{color:#8fa59e;margin:0 0 26px;max-width:760px}
.card{background:#172120;border:1px solid #243430;border-radius:22px;padding:26px;display:flex;gap:34px;flex-wrap:wrap;max-width:1060px}
.col{display:flex;flex-direction:column;gap:12px}.row{display:flex;gap:16px;align-items:center}
h3{margin:0;font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:#8fa59e}
.shadow{filter:drop-shadow(0 10px 18px rgba(0,0,0,.45))}
.bar{background:#000;border-radius:10px;padding:6px 14px;display:flex;justify-content:space-between;align-items:center;width:260px}
.bar .icons{display:flex;gap:10px;align-items:center}.dot{width:12px;height:12px;border-radius:6px;background:#fff;opacity:.85}
.zoom{display:flex;align-items:center;gap:12px}.zoom svg{background:#000;outline:1px dashed #3fd6a0}small{color:#8fa59e;max-width:150px}
.key{display:flex;gap:18px;color:#9ab0a9;font-size:12px}.key i{display:inline-block;width:18px;border-top:2px dashed;vertical-align:middle;margin-right:6px}
</style><h1>System Manager launcher icon</h1>
<p class=sub>The guides on the large icons show the 66-unit safe circle (dashed white) that every launcher mask keeps, and the
6-unit margin the artwork must stay inside (dotted amber). The hub sits well within both.</p>
<div class=card>
<div class=col><h3>Squircle, with guides</h3><div class=shadow>%s</div>
<div class=key><span><i style="border-color:#fff"></i>safe zone</span><span><i style="border-color:#ffd166"></i>required margin</span></div></div>
<div class=col><h3>Circle, with guides</h3><div class=shadow>%s</div></div>
<div class=col><h3>Masks</h3><div class="row shadow">%s</div>
<h3>Launcher sizes</h3><div class="row shadow">%s</div>
<h3>Themed icon</h3><div class="row">%s%s</div>
<h3>Status bar</h3>%s%s</div>
</div>""" % (big, circle_big, masks, sizes, themed("#D7E3DF", "#1B332B"), themed("#2B3B35", "#C9E8DB"), bar, zoom)
    with open(path_out, "w") as fh:
        fh.write(html)
    print("preview written to", path_out)


if __name__ == "__main__":
    status = 0
    if "--apply" in sys.argv:
        apply()
    if "--check" in sys.argv or "--apply" in sys.argv:
        status = check()
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    if len(sys.argv) == 1:
        print(__doc__)
    sys.exit(status)
