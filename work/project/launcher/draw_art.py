from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "art"
OUT.mkdir(exist_ok=True)
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_B = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"

def gradient(w, h, a, b):
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            t = min(1, max(0, (x / w * .65 + y / h * .35)))
            px[x, y] = tuple(round(a[i] * (1-t) + b[i] * t) for i in range(3))
    return im

def home_mark(d, cx, cy, s, color):
    lw = max(6, int(s * .055))
    d.line([(cx-s*.38,cy-s*.03),(cx,cy-s*.38),(cx+s*.38,cy-s*.03)], fill=color, width=lw, joint="curve")
    d.line([(cx-s*.27,cy-s*.08),(cx-s*.27,cy+s*.31),(cx+s*.27,cy+s*.31),(cx+s*.27,cy-s*.08)], fill=color, width=lw, joint="curve")
    d.rounded_rectangle((cx-s*.06,cy+s*.08,cx+s*.08,cy+s*.31), radius=lw//2, fill=color)

def switch_mark(d, cx, cy, s, color):
    d.rounded_rectangle((cx-s*.34,cy-s*.34,cx+s*.30,cy+s*.34), radius=s*.10, outline=color, width=max(5,int(s*.045)))
    for oy in (-.16, 0, .16):
        d.ellipse((cx-s*.22,cy+s*oy-s*.025,cx-s*.17,cy+s*oy+s*.025), fill=color)
        d.line((cx-s*.10,cy+s*oy,cx+s*.16,cy+s*oy), fill=color, width=max(4,int(s*.026)))
    d.arc((cx-s*.49,cy-s*.49,cx+s*.49,cy+s*.49), 218, 320, fill=(147,198,255), width=max(6,int(s*.05)))
    d.polygon([(cx-s*.27,cy-s*.45),(cx-s*.48,cy-s*.42),(cx-s*.40,cy-s*.25)], fill=(147,198,255))
    d.arc((cx-s*.49,cy-s*.49,cx+s*.49,cy+s*.49), 38, 140, fill=(147,198,255), width=max(6,int(s*.05)))
    d.polygon([(cx+s*.27,cy+s*.45),(cx+s*.48,cy+s*.42),(cx+s*.40,cy+s*.25)], fill=(147,198,255))

def make(kind, title, subtitle, a, b, accent, mark):
    icon = gradient(512,512,a,b)
    d = ImageDraw.Draw(icon)
    d.ellipse((42,34,470,462), fill=(35,47,68) if kind == "amazon-home" else (29,43,82))
    d.ellipse((58,48,454,444), outline=(89,104,132) if kind == "amazon-home" else (75,99,151), width=3)
    mark(d,256,248,266,accent)
    # a small luminous indicator gives the pair a shared product family signature
    d.ellipse((404,90,431,117), fill=(244,249,255))
    d.ellipse((410,96,425,111), fill=accent)
    icon.save(OUT/f"{kind}-icon.png", optimize=True)

    w,h=1280,720
    banner=gradient(w,h,a,b)
    bd=ImageDraw.Draw(banner)
    # soft geometric rings and a fine dotted field add depth without TV-side text clutter
    ring = (74, 82, 105) if kind == "amazon-home" else (66, 82, 125)
    bd.ellipse((795,-270,1500,435), outline=ring, width=3)
    bd.ellipse((850,-205,1435,380), outline=ring, width=2)
    for y in range(54,680,46):
        for x in range(34,610,46):
            if (x//46+y//46)%3==0: bd.ellipse((x,y,x+3,y+3), fill=(80,91,115) if kind == "amazon-home" else (68,86,133))
    bd.rounded_rectangle((68,66,1118,654), radius=38, outline=(88,96,117) if kind == "amazon-home" else (81,98,142), width=2)
    bd.rounded_rectangle((116,130,540,590), radius=48,
        fill=(31,43,64) if kind == "amazon-home" else (28,41,78),
        outline=(88,99,124) if kind == "amazon-home" else (81,101,151), width=2)
    mark(bd,328,354,286,accent)
    fsmall=ImageFont.truetype(FONT_B,24)
    ftitle=ImageFont.truetype(FONT_B,64)
    fsub=ImageFont.truetype(FONT,27)
    bd.text((614,228),"FIRE TV  /  HOME CONTROL",font=fsmall,fill=accent)
    bd.text((610,282),title,font=ftitle,fill=(248,250,255))
    bd.rounded_rectangle((614,382,720,390),radius=4,fill=accent)
    bd.text((614,423),subtitle,font=fsub,fill=(194,207,226))
    banner.save(OUT/f"{kind}-banner.png", optimize=True)

make("amazon-home","Amazon Home","Restore the original Fire TV home.",(20,31,53),(86,44,37),(255,168,101),home_mark)
make("switch-launcher","Switch Launcher","Choose your preferred Home activity.",(20,29,62),(38,58,113),(126,177,255),switch_mark)

# Switch Launcher: bold, television-readable identity.
def make_switch_identity():
    ink=(20,25,33); white=(245,247,248); amber=(255,174,76)
    def mark(d,x,y,s):
        w=int(s*.095)
        d.line([(x-s*.34,y-s*.17),(x+s*.25,y-s*.17)],fill=white,width=w)
        d.line([(x+s*.08,y-s*.34),(x+s*.27,y-s*.17),(x+s*.08,y)],fill=white,width=w,joint='curve')
        d.line([(x+s*.34,y+s*.18),(x-s*.25,y+s*.18)],fill=amber,width=w)
        d.line([(x-s*.08,y),(x-s*.27,y+s*.18),(x-s*.08,y+s*.35)],fill=amber,width=w,joint='curve')
    im=Image.new('RGB',(512,512),ink);mark(ImageDraw.Draw(im),256,256,300);im.save(OUT/'switch-launcher-icon.png',optimize=True)
    im=Image.new('RGB',(1280,720),ink);d=ImageDraw.Draw(im);mark(d,235,360,295)
    font=ImageFont.truetype(FONT_B,148)
    d.text((460,175),'Switch',font=font,fill=white,stroke_width=0)
    d.text((460,345),'Launcher',font=font,fill=white,stroke_width=0)
    im.save(OUT/'switch-launcher-banner.png',optimize=True)
make_switch_identity()
