"""Flatten Lucide's icon nodes into one line per icon: name, tags, and every shape as SVG path data.

Usage: python make_icons.py <lucide-static package folder> <out icons.txt>
Each line: name TAB comma-separated tags TAB path data (all shapes of the icon, joined by spaces).
Circles, ellipses and rounded rectangles become arcs; lines, polylines and polygons become straight runs.
"""
import json, re, sys


def num(v):
    f = float(v)
    s = ("%.4f" % f).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"
LEADING_M = re.compile(r"^m\s*(" + NUMBER + r")\s*,?\s*(" + NUMBER + r")(.*)$", re.S)


def absolute_start(d):
    """A path's first relative move is absolute - but only while the path stands alone. Joined after another shape it
    would move from where that one ended, so it is written as the absolute move it is, and any pairs after it as the
    relative lines they are."""
    m = LEADING_M.match(d)
    if not m:
        return d
    rest = m.group(3).lstrip(" ,")
    if rest and (rest[0].isdigit() or rest[0] in "+-."):
        rest = "l" + rest
    return "M" + m.group(1) + " " + m.group(2) + rest


def shape(kind, a):
    if kind == "path":
        return absolute_start(a["d"].strip())
    if kind == "line":
        return "M%s %sL%s %s" % (num(a["x1"]), num(a["y1"]), num(a["x2"]), num(a["y2"]))
    if kind in ("polyline", "polygon"):
        pts = a["points"].replace(",", " ").split()
        pairs = [(pts[i], pts[i + 1]) for i in range(0, len(pts) - 1, 2)]
        d = "M%s %s" % (num(pairs[0][0]), num(pairs[0][1])) + "".join("L%s %s" % (num(x), num(y)) for x, y in pairs[1:])
        return d + ("Z" if kind == "polygon" else "")
    if kind in ("circle", "ellipse"):
        cx, cy = float(a["cx"]), float(a["cy"])
        rx = float(a.get("r", a.get("rx", 0)))
        ry = float(a.get("r", a.get("ry", rx)))
        return "M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ" % (
            num(cx - rx), num(cy), num(rx), num(ry), num(cx + rx), num(cy), num(rx), num(ry), num(cx - rx), num(cy))
    if kind == "rect":
        x, y, w, h = (float(a[k]) for k in ("x", "y", "width", "height"))
        rx = float(a.get("rx", a.get("ry", 0)))
        ry = float(a.get("ry", rx))
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return "M%s %sH%sV%sH%sZ" % (num(x), num(y), num(x + w), num(y + h), num(x))
        return ("M%s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sZ" % (
            num(x + rx), num(y), num(x + w - rx), num(rx), num(ry), num(x + w), num(y + ry), num(y + h - ry),
            num(rx), num(ry), num(x + w - rx), num(y + h), num(x + rx), num(rx), num(ry), num(x), num(y + h - ry),
            num(y + ry), num(rx), num(ry), num(x + rx), num(y)))
    raise ValueError("a shape of kind " + kind)


def main(package, out):
    nodes = json.load(open(package + "/icon-nodes.json", encoding="utf-8"))
    tags = json.load(open(package + "/tags.json", encoding="utf-8"))
    lines = []
    for name in sorted(nodes):
        d = " ".join(shape(kind, attrs) for kind, attrs in nodes[name])
        said = ",".join(t.strip().replace("\t", " ").replace(",", " ") for t in tags.get(name, []) if t.strip())
        lines.append(name + "\t" + said + "\t" + d)
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# Lucide " + json.load(open(package + "/package.json", encoding="utf-8"))["version"]
                 + " (ISC; see NOTICE). One icon a line: name, tags, path data on a 24-unit grid, stroked 2 wide, round ends.\n")
        fh.write("\n".join(lines) + "\n")
    print(len(lines), "icons")


main(sys.argv[1], sys.argv[2])
