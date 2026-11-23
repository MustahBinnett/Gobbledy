"""Writes the goblin vector drawables (button glyph + launcher foreground)."""
import sys, os

INK = "#FF2C2C2A"

def p(d, fill=None, stroke=None, width=None, cap=None, join=None):
    a = [f'android:pathData="{d}"']
    if fill: a.append(f'android:fillColor="{fill}"')
    if stroke:
        a.append(f'android:strokeColor="{stroke}"')
        a.append(f'android:strokeWidth="{width}"')
    if cap: a.append(f'android:strokeLineCap="{cap}"')
    if join: a.append(f'android:strokeLineJoin="{join}"')
    return "<path " + " ".join(a) + " />"

def ellipse(cx, cy, rx, ry):
    return f"M{cx-rx},{cy} a{rx},{ry} 0 1,0 {2*rx},0 a{rx},{ry} 0 1,0 {-2*rx},0 Z"

face = [
    p("M30,46 L4,30 L28,58 Z", "#FFD9B48F", INK, 2.4, join="round"),
    p("M26,47 L12,37 L27,53 Z", "#FFF0997B"),
    p("M70,46 L96,26 L72,58 Z", "#FFD9B48F", INK, 2.4, join="round"),
    p("M74,47 L88,34 L73,53 Z", "#FFF0997B"),
    p("M34,30 L36,16 L42,27 L46,12 L50,26 L56,13 L58,27 L65,17 L65,31", "#FF8A7A55", INK, 2, join="round"),
    p(ellipse(50, 52, 23, 25), "#FFC9C07A", INK, 2.4),
    p("M34,40 L46,44 M66,40 L54,44", None, INK, 3, cap="round"),
    p("M37,46 q4,3 8,0 M55,46 q4,3 8,0", None, INK, 2.6, cap="round"),
    p(ellipse(50, 52, 5, 4), "#FFE0937A", INK, 1.8),
    p("M36,60 q14,-6 28,0 q-2,14 -14,15 q-12,-1 -14,-15 z", "#FF712B13", INK, 2.4, join="round"),
    p("M41,59 l2,5 l2,-6 M55,58 l2,6 l2,-5", "#FFFFFFFF", "#FFFFFFFF", 1.5, join="round"),
    p("M44,72 l2,-4 l2,4 M52,72 l2,-4 l2,4", "#FFFFFFFF", "#FFFFFFFF", 1.2),
    p(ellipse(50, 69, 7, 3), "#FFD4537E"),
]
bubble = [
    p("M70,5 h18 q6,0 6,6 v8 q0,6 -6,6 h-10 l-7,6 l1.5,-6 h-2.5 q-6,0 -6,-6 v-8 q0,-6 6,-6 z",
      "#FFFFFFFF", INK, 2, join="round"),
    p("M73,15 v2 M77,12 v8 M81,9 v14 M85,12 v8 M89,15 v2", None, "#FF0B6E69", 2.6, cap="round"),
]

def body(indent="        "):
    out = [f'{indent}<group>',
           f'{indent}    <clip-path android:pathData="{ellipse(50, 50, 48, 48)}" />',
           f'{indent}    <group android:translateX="-13" android:translateY="-6" android:scaleX="1.22" android:scaleY="1.22">']
    out += [f"{indent}        {x}" for x in face]
    out += [f"{indent}    </group>", f"{indent}</group>"]
    out += [f"{indent}{x}" for x in bubble]
    return "\n".join(out)

HDR = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Original goblin artwork for Pocket Dictate. Generated; edit tools/gen_goblin.py rather than by hand. -->\n'
res = sys.argv[1]

with open(os.path.join(res, "drawable", "ic_goblin.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write(HDR + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="100dp" android:height="100dp"\n'
            '    android:viewportWidth="100" android:viewportHeight="100">\n'
            + body("    ") + "\n</vector>\n")

# Launcher: 108dp canvas; the visible mask is roughly the central 72dp, so the
# 96-unit goblin circle is scaled by 0.75 and centred.
with open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write(HDR + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <group android:translateX="16.5" android:translateY="16.5" android:scaleX="0.75" android:scaleY="0.75">\n'
            + body("        ") + "\n    </group>\n</vector>\n")
print("ok")
