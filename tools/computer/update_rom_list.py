"""Обновить список файлов ПЗУ (src/main/resources/data/minepc/computer/rom.list).
Запускать после добавления или удаления программ в папке rom/:
    python tools/computer/update_rom_list.py
"""
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ROM = os.path.join(ROOT, "src/main/resources/data/minepc/computer/rom")
files = []
for dp, _, fs in os.walk(ROM):
    for f in fs:
        files.append(os.path.relpath(os.path.join(dp, f), ROM).replace(os.sep, "/"))
files.sort()
with open(os.path.join(os.path.dirname(ROM), "rom.list"), "w", encoding="utf-8") as out:
    out.write("# Файлы ПЗУ компьютера. Обновляется tools/computer/update_rom_list.py\n")
    out.write("\n".join(files) + "\n")
print(len(files), "файлов")

# Дискеты с программами (data/minepc/computer/disks/<программа>/...) — список для ProgramDisks
DISKS = os.path.join(os.path.dirname(ROM), "disks")
dfiles = []
for dp, _, fs in os.walk(DISKS):
    for f in fs:
        dfiles.append(os.path.relpath(os.path.join(dp, f), DISKS).replace(os.sep, "/"))
dfiles.sort()
with open(os.path.join(os.path.dirname(ROM), "disks.list"), "w", encoding="utf-8") as out:
    out.write("# Файлы дискет с программами. Обновляется tools/computer/update_rom_list.py\n")
    out.write("\n".join(dfiles) + "\n")
print(len(dfiles), "файлов на дискетах")
