"""Команды в запущенную игру (./gradlew runClient -PliveCtl) и ответ — см. client/LiveControl.java.
   python tools/live.py "tp 10 -59 20 0 30" "shot test"     — по команде в аргументе
   python tools/live.py -f команды.txt                       — из файла
Ждёт, пока игра выполнит пачку (до 60 с), и печатает ответ из run/live/out.txt."""
import os, sys, time

RUN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "run", "live")


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 1
    if args[0] == "-f":
        lines = open(args[1], encoding="utf-8").read().splitlines()
    else:
        lines = args
    if not os.path.exists(os.path.join(RUN, "ready.txt")):
        print("Игра не готова: нет run/live/ready.txt (запущена ли с -PliveCtl и вошла ли в мир?)")
        return 2
    done = os.path.join(RUN, "done.txt")
    before = open(done, encoding="utf-8").read().strip() if os.path.exists(done) else ""
    tmp = os.path.join(RUN, "cmd.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    os.replace(tmp, os.path.join(RUN, "cmd.txt"))
    for _ in range(600):
        time.sleep(0.1)
        if os.path.exists(done) and open(done, encoding="utf-8").read().strip() != before:
            sys.stdout.buffer.write(open(os.path.join(RUN, "out.txt"), "rb").read())
            return 0
    print("Игра не ответила за 60 с")
    return 3


if __name__ == "__main__":
    sys.exit(main())
