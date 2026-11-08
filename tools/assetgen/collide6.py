import json, itertools, sys
# запуск: python tools/assetgen/collide6.py <корень проекта> <spec_dump.json>
A = f"{sys.argv[1]}/src/main/resources/assets/minepc/models"
dump = json.load(open(sys.argv[2]))
COMBOS = {s["type"]: [tuple(v) for v in s["visuals"]] for s in dump["specs"]}
NAMES = {"MOTHERBOARD": "motherboard", "CPU": "cpu", "COOLER": "cooler", "RAM": "ram", "GPU": "gpu", "PSU": "psu", "HDD": "hdd", "SSD": "ssd"}
SLOTS = {"MOTHERBOARD": [(0, 0, 0)], "CPU": [(0, 0, 0)], "COOLER": [(0, 0, 0)],
         "RAM": [(0, 0, -0.35 * i) for i in range(4)], "GPU": [(0, 0, 0)], "PSU": [(0, 0, 0)],
         "HDD": [(0, 1.5 * i, 0) for i in range(2)], "SSD_SATA": [(0, -3.0 * i, 0) for i in range(2)],
         "SSD_M2": [(0, -2.8 * i, 0) for i in range(2)]}
cache = {}
def boxes(t, v, off):
    key = (t, v)
    if key not in cache:
        m = json.load(open(f"{A}/item/{NAMES[t]}/part_{v[0]}_{v[1]}_{v[2]}.json"))
        cache[key] = [([e["from"][i] - (4 if i == 0 else 0) for i in range(3)], [e["to"][i] - (4 if i == 0 else 0) for i in range(3)]) for e in m["elements"]]
    return [([a[i] + off[i] for i in range(3)], [b[i] + off[i] for i in range(3)]) for a, b in cache[key]]
def variants(slot):
    if slot == "SSD_SATA": return [("SSD", v) for v in COMBOS["SSD"] if v[0] == 0]
    if slot == "SSD_M2": return [("SSD", v) for v in COMBOS["SSD"] if v[0] == 1]
    return [(slot, v) for v in COMBOS[slot]]
def overlap(a, b, eps=0.011):
    return all(min(a[1][i], b[1][i]) - max(a[0][i], b[0][i]) > eps for i in range(3))
OK = {frozenset(p) for p in [("CPU", "MOTHERBOARD"), ("COOLER", "CPU"), ("RAM", "MOTHERBOARD"), ("GPU", "MOTHERBOARD"),
                             ("SSD_M2", "MOTHERBOARD"), ("COOLER", "MOTHERBOARD")]}
case = json.load(open(f"{A}/block/pc_case.json"))
case_boxes = [(e["from"], e["to"]) for e in case["elements"] if e.get("name") != "glass"]
problems = set()
slots = list(SLOTS)
for s1, s2 in itertools.combinations(slots, 2):
    if frozenset((s1, s2)) in OK: continue
    for t1, v1 in variants(s1):
        for t2, v2 in variants(s2):
            for o1 in SLOTS[s1]:
                for o2 in SLOTS[s2]:
                    for a in boxes(t1, v1, o1):
                        for b in boxes(t2, v2, o2):
                            if overlap(a, b):
                                problems.add((s1, v1, s2, v2, tuple(round(x, 2) for x in a[0]), tuple(round(x, 2) for x in b[0])))
for s in ("RAM", "HDD", "SSD_SATA", "SSD_M2"):
    for t, v in variants(s):
        for o1, o2 in itertools.combinations(SLOTS[s], 2):
            if any(overlap(a, b) for a in boxes(t, v, o1) for b in boxes(t, v, o2)):
                problems.add((s, v, "self"))
for s in slots:
    for t, v in variants(s):
        for o in SLOTS[s]:
            for a in boxes(t, v, o):
                for b in case_boxes:
                    if overlap(a, b): problems.add((s, v, "CASE", tuple(round(x, 2) for x in a[0]), tuple(b[0])))
# границы корпуса: всё внутри x 4..12.75, y 1..15, z 1..15
for s in slots:
    for t, v in variants(s):
        for o in SLOTS[s]:
            for a, b in boxes(t, v, o):
                if a[0] < 3.99 or b[0] > 12.75 or a[1] < 0.99 or b[1] > 15.01 or a[2] < 0.99 or b[2] > 15.01:
                    problems.add((s, v, "OUT", tuple(round(x, 2) for x in a), tuple(round(x, 2) for x in b)))
for p in sorted(problems, key=str)[:40]: print(p)
print("total", len(problems))
