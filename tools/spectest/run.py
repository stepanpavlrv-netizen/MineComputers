"""Тест спецификаций деталей без запуска Minecraft.

Компилирует пакет hardware.spec вместе с крошечными заглушками классов Minecraft
(папка net/), проверяет все заводские пресеты и 300 000 случайных конфигураций
каждой детали, а результат пишет в tools/assetgen/spec_dump.json — по нему
генератор моделей узнаёт, какие варианты геометрии бывают.

Запуск из корня репозитория (нужен JDK 21 в PATH):
    python tools/spectest/run.py

Если добавляешь в Items.* новый предмет-материал, допиши его в
tools/spectest/net/minecraft/world/item/Items.java.
"""
import json, os, re, shutil, subprocess, sys, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HW = os.path.join(ROOT, "src/main/java/com/strikerr212/minepc/hardware")
STUBS = os.path.join(os.path.dirname(__file__), "net")
OUT_JSON = os.path.join(ROOT, "tools/assetgen/spec_dump.json")


def make_test(presets_src):
    tables = {}
    for name, body in re.findall(r"private static final int\[\]\[\] (\w+) = \{(.*?)\n    \};", presets_src, re.S):
        tables[name] = [[int(x) for x in r.split(",")] for r in re.findall(r"\{([\d, ]+)\}", body)]
    customs = re.findall(r'build\(HardwareType\.(\w+), "([^"]*)", ([\d, ]+)\)', presets_src)
    j = ["package com.strikerr212.minepc.hardware.spec;", "import java.util.*;", "import com.strikerr212.minepc.hardware.*;",
         "import net.minecraft.network.chat.Component;", "public class SpecTest {",
         " static List<Integer> L(int... a){List<Integer> l=new ArrayList<>();for(int x:a)l.add(x);return l;}",
         " public static void main(String[] args) {", '  StringBuilder o = new StringBuilder("{\\"specs\\":[");', "  boolean first = true;",
         "  for (PartSpec s : Specs.ALL) {",
         '    if (!first) o.append(","); first = false;',
         '    o.append("{\\"type\\":\\"").append(s.type()).append("\\",\\"params\\":[");',
         "    for (int i = 0; i < s.params().size(); i++) { Param p = s.params().get(i);",
         '      if (i > 0) o.append(",");',
         '      o.append("{\\"key\\":\\"").append(p.key()).append("\\",\\"kind\\":\\"").append(p.kind()).append("\\",\\"count\\":").append(p.count()).append(",\\"page\\":").append(p.page()).append("}"); }',
         '    o.append("],\\"pages\\":").append(s.pages()).append(",\\"visuals\\":[");',
         "    Set<String> vis = new TreeSet<>(); Random r = new Random(1);",
         "    for (int n = 0; n < 300000; n++) { List<Integer> v = new ArrayList<>();",
         "      for (Param p : s.params()) v.add(r.nextInt(p.count()));",
         '      vis.add("[" + s.visual(v,0) + "," + s.visual(v,1) + "," + s.visual(v,2) + "]");',
         '      for (int pg = 0; pg < s.pages(); pg++) { List<Integer> pv = s.previewValues(v, pg); vis.add("[" + s.visual(pv,0) + "," + s.visual(pv,1) + "," + s.visual(pv,2) + "]"); }',
         "      s.tier(v); s.cost(v); s.powerDraw(v); s.describe(v, new ArrayList<>()); for (int t = 1; t <= 3; t++) s.tint(v, t); }",
         '    o.append(String.join(",", vis)).append("]}"); }',
         '  o.append("],\\"presets\\":[");']
    first = True
    for name, rows in tables.items():
        for t, row in enumerate(rows, 1):
            j.append(f'  report(o, Specs.{name}, "{name}", {t}, L({", ".join(map(str, row))}), {"false" if first else "true"});')
            first = False
    for typ, label, vals in customs:
        j.append(f'  report(o, Specs.{typ}, "{typ}*", 0, L({vals}), true);')
    j.append('  o.append("]}"); System.out.println(o); PerfReport.run(); }')
    j.append('''  static void report(StringBuilder o, PartSpec s, String name, int want, List<Integer> v, boolean comma) {
    if (comma) o.append(",");
    if (v.size() != s.params().size()) throw new RuntimeException(name + " T" + want + ": " + v.size() + " values, spec has " + s.params().size());
    for (int i = 0; i < v.size(); i++) if (v.get(i) < 0 || v.get(i) >= s.params().get(i).count()) throw new RuntimeException(name + " T" + want + ": value " + i + " out of range");
    o.append("{\\"name\\":\\"").append(name).append("\\",\\"type\\":\\"").append(s.type()).append("\\",\\"want\\":").append(want)
     .append(",\\"tier\\":").append(s.tier(v)).append(",\\"power\\":").append(s.powerDraw(v)).append(",\\"heat\\":").append(s.heat(v))
     .append(",\\"vis\\":[").append(s.visual(v,0)).append(",").append(s.visual(v,1)).append(",").append(s.visual(v,2)).append("]")
     .append(",\\"tints\\":[").append(s.tint(v,1)).append(",").append(s.tint(v,2)).append(",").append(s.tint(v,3)).append("]}");
  }
}''')
    return "\n".join(j)


def make_tables(presets_src):
    """Таблицы заводских деталей для PerfReport (в HardwarePresets они приватные)."""
    j = ["package com.strikerr212.minepc.hardware.spec;", "public class StockTables {"]
    for name, body in re.findall(r"private static final int\[\]\[\] (\w+) = \{(.*?)\n    \};", presets_src, re.S):
        rows = re.findall(r"\{([\d, ]+)\}", body)
        j.append(f"  public static final int[][] {name} = {{" + ", ".join("{" + r + "}" for r in rows) + "};")
    j.append("}")
    return "\n".join(j)


def main():
    work = tempfile.mkdtemp(prefix="spectest_")
    try:
        src = os.path.join(work, "src")
        shutil.copytree(os.path.join(HW, "spec"), os.path.join(src, "spec"))
        os.remove(os.path.join(src, "spec", "PartDesign.java"))      # зависит от кодеков Minecraft
        for f in os.listdir(HW):
            if f.endswith("Data.java") or f in ("HardwareType.java", "Units.java", "Perf.java", "SystemModel.java"):
                shutil.copy(os.path.join(HW, f), src)
        # частота процессора от температуры — общая с живым компьютером
        os.makedirs(os.path.join(work, "computer"), exist_ok=True)
        shutil.copy(os.path.join(HW, "..", "computer", "CpuClock.java"), os.path.join(work, "computer"))
        presets = open(os.path.join(HW, "HardwarePresets.java"), encoding="utf-8").read()
        with open(os.path.join(src, "spec", "SpecTest.java"), "w", encoding="utf-8") as f:
            f.write(make_test(presets))
        with open(os.path.join(src, "spec", "StockTables.java"), "w", encoding="utf-8") as f:
            f.write(make_tables(presets))
        shutil.copy(os.path.join(os.path.dirname(__file__), "PerfReport.java"), os.path.join(src, "spec"))
        files = [os.path.join(dp, n) for base in (src, STUBS, os.path.join(work, "computer")) for dp, _, fs in os.walk(base) for n in fs if n.endswith(".java")]
        out = os.path.join(work, "out")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", out] + files, check=True)
        res = subprocess.run(["java", "-cp", out, "com.strikerr212.minepc.hardware.spec.SpecTest"],
                             capture_output=True, text=True, encoding="utf-8")
        print(res.stderr)
        if res.returncode != 0:
            sys.exit("Тест упал (см. выше)")
        dump = json.loads(res.stdout)
        with open(OUT_JSON, "w", encoding="utf-8") as f:
            json.dump(dump, f)
        bad = 0
        for p in dump["presets"]:
            mark = ""
            if p["want"] and p["want"] != p["tier"]:
                mark, bad = "  <-- ожидался уровень %d" % p["want"], bad + 1
            print(f'{p["name"]:12} ур.{p["tier"]}  потребл.={p["power"]:4}  вид={p["vis"]}{mark}')
        for s in dump["specs"]:
            print(f'{s["type"]:12} параметров {len(s["params"])}, вариантов модели {len(s["visuals"])}')
        print("ОК" if bad == 0 else f"Пресетов с неверным уровнем: {bad}")
        sys.exit(1 if bad else 0)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
