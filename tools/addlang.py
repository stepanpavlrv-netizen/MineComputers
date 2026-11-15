"""addlang.py keys.json — дописать ключи в ru_ru.json и en_us.json (заменить, если уже есть).
keys.json: {"ключ": ["по-русски", "in English"], ...}; можно передать /dev/stdin."""
import json, os, sys, collections
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "minepc", "lang") + os.sep
new = json.load(open(sys.argv[1], encoding="utf-8") if sys.argv[1] != "-" else sys.stdin)
for i, f in enumerate(["ru_ru.json", "en_us.json"]):
    data = json.load(open(D + f, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    for k, v in new.items():
        data[k] = v[i]
    with open(D + f, "w", encoding="utf-8") as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write("\n")
print("ok", len(new))
