"""Кадры «Разлома» без Minecraft: та же видеокарта мода (Java), что в игре, дискета из src/main/resources.

    python tools/razview/shot.py [--size 200x62] [--vram клеток] [--out папка] "аргументы" ["аргументы" ...]

Аргументы кадра — как у game.lua (через пробел):
    lvl=N              уровень (1–5)
    at=x,z[,угол]      встать игроком здесь (вид из глаз, с оружием в руках)
    cam=x,y,z,yaw,pitch свободная камера (без оружия)
    give / portal / t=секунды (сколько прожить мир до кадра, враги ходят) / pitch=угол
Пример:  python tools/razview/shot.py "lvl=1 at=4.5,4.5,0 give"
Нужен JDK 21 и jar Cobalt (ищется в GRADLE_USER_HOME или ~/.gradle). Вывод — PNG в tools/razview/shots/.
"""
import glob, os, shutil, subprocess, sys, tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/strikerr212/minepc/computer")
RES = os.path.join(ROOT, "src/main/resources")


def cobalt():
    homes = [os.environ.get("GRADLE_USER_HOME"), "D:/gradle-home", os.path.expanduser("~/.gradle")]
    for h in homes:
        if h:
            m = glob.glob(os.path.join(h, "caches/modules-2/files-2.1/cc.tweaked/cobalt/*/*/cobalt-*.jar"))
            m = [x for x in m if "sources" not in x]
            if m:
                return sorted(m)[-1]
    sys.exit("Не найден cobalt-*.jar — соберите мод (gradlew build)")


def main():
    args = sys.argv[1:]
    size, out = "200x62", os.path.join(HERE, "shots")
    if "--size" in args:
        i = args.index("--size"); size = args[i + 1]; del args[i:i + 2]
    vram = "400000"
    if "--vram" in args:
        i = args.index("--vram"); vram = args[i + 1]; del args[i:i + 2]
    if "--out" in args:
        i = args.index("--out"); out = args[i + 1]; del args[i:i + 2]
    if not args:
        sys.exit(__doc__)
    os.makedirs(out, exist_ok=True)
    w, h = size.split("x")
    jar = cobalt()
    work = tempfile.mkdtemp(prefix="razshot_")
    files = [os.path.join(dp, f) for dp, _, fs in os.walk(SRC) for f in fs if f.endswith(".java")]
    files.append(os.path.join(HERE, "RazlomShot.java"))
    cls = os.path.join(work, "out")
    sep = ";" if os.name == "nt" else ":"
    subprocess.run([shutil.which("javac") or "javac", "-encoding", "UTF-8", "-nowarn", "-cp", jar, "-d", cls] + files, check=True)
    raw = os.path.join(work, "raw")
    os.makedirs(raw)
    subprocess.run([shutil.which("java") or "java", "-Dstdout.encoding=UTF-8", "-Dvram=" + vram, "-cp", sep.join([cls, jar, RES]), "com.strikerr212.minepc.computer.RazlomShot", w, h, raw] + args)
    for k, a in enumerate(args):
        p = os.path.join(raw, "f%d.txt" % k)
        if not os.path.exists(p):
            continue
        lines = open(p, encoding="utf-8").read().split("\n")
        cw, ch = map(int, lines[0].split())
        im = Image.new("RGB", (cw, ch))
        px = []
        for y in range(ch):
            row = lines[1 + y]
            for x in range(cw):
                v = int(row[x * 6:x * 6 + 6], 16)
                px.append((v >> 16 & 255, v >> 8 & 255, v & 255))
        im.putdata(px)
        im = im.resize((cw * 4, ch * 4), Image.NEAREST)
        name = "".join(c if c.isalnum() or c in "=.,-" else "_" for c in a)[:80] or "shot"
        dst = os.path.join(out, name + ".png")
        im.save(dst)
        print("сохранено", dst)


if __name__ == "__main__":
    main()
