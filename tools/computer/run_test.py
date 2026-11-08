"""Проверка компьютера (Lua, файлы, оболочка, редактор) без запуска Minecraft.

Нужен JDK 21 и jar движка Lua Cobalt (cc.tweaked:cobalt). Gradle сам скачивает его при сборке мода,
путь можно найти так:  .\\gradlew dependencies  или в папке ~/.gradle/caches/modules-2/files-2.1/cc.tweaked/cobalt
Запуск из корня репозитория:
    python tools/computer/run_test.py путь/к/cobalt.jar [команд_за_тик]
"""
import os, shutil, subprocess, sys, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/strikerr212/minepc/computer")
RES = os.path.join(ROOT, "src/main/resources")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cobalt = sys.argv[1]
    work = tempfile.mkdtemp(prefix="pctest_")
    try:
        files = [os.path.join(dp, f) for dp, _, fs in os.walk(SRC) for f in fs if f.endswith(".java")]
        files.append(os.path.join(os.path.dirname(__file__), "HeadlessTest.java"))
        out = os.path.join(work, "out")
        sep = ";" if os.name == "nt" else ":"
        subprocess.run(["javac", "-encoding", "UTF-8", "-nowarn", "-cp", cobalt, "-d", out] + files, check=True)
        res = subprocess.run(["java", "-Dstdout.encoding=UTF-8", "-cp", sep.join([out, cobalt, RES]), "HeadlessTest"]
                             + sys.argv[2:])
        sys.exit(res.returncode)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
