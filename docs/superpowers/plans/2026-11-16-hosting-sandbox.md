# Песочница хостинга — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** страницы `*.lua` сайтов из `/sites/*` выполняются в отдельном процессе-«клетке», который не может выйти из папки сайта, управлять сервером, слушать чужие порты и висеть дольше лимита.

**Architecture:** `LuaMachine` получает необязательную `Jail` (корень + свои порты + время старта). `LuaApis.install` для такой машины ставит урезанные `fs/os/net/computer`; `Computer` не шлёт ей чужие события и убивает по тайм-ауту. BIOS исполняет `_PAGE` вместо `_PROCESS`, результат уходит родителю 4-м аргументом `process_exit`. `httpd` запускает страницы `/sites/*` через `computer.spawnPage`.

**Tech Stack:** Java 21, Cobalt (Lua 5.2), NeoForge 1.21.1; проверка — `tools/computer/HeadlessTest.java`.

**Spec:** `docs/superpowers/specs/2026-11-16-hosting-sandbox-design.md`

## Global Constraints

- Lua — Cobalt 5.2: нет `//`, `&`, `>>`, `<<`.
- Лимиты в одном месте (`Jail`): время `TIMEOUT_MS = 5000`, память `MEMORY = 512 * 1024`, ответ `MAX_RESULT = 64 * 1024`, страниц на сайт одновременно — 2 (в `httpd`), порты страницы — 40000–49999, исходящие — только 80 и 25.
- Ошибки в клетке на русском; реальный путь `/sites/<имя>` в сообщения не попадает.
- `/www` и обычный `computer.spawn` ведут себя как раньше.
- Тексты для игрока — по-русски; README обновить.

## Review Focus

1. Выход из папки: `../`, `/../../etc`, `\\`, `fs.copy/move` с одним путём внутри и другим снаружи — оба пути через клетку.
2. Закрыть/открыть чужой порт: `net.close(80)`, `net.open(25)` → ошибка; свой порт после смерти процесса закрывается.
3. Страница ждёт `os.pullEvent()` без фильтра — не должна получить чужой `net_message`/`process_exit`.
4. Страница вернула не строку (таблицу, nil, огромную строку) — `httpd` отвечает текстом/обрезкой, не падает.
5. Нет свободных процессов (`maxProcesses`) — `503`, `httpd` продолжает работать.

---

### Task 1: Клетка процесса в Java

**Files:**
- Create: `src/main/java/com/strikerr212/minepc/computer/Jail.java`
- Modify: `LuaMachine.java` (поле `jail`, лимит памяти), `Computer.java` (`spawnPage`, `machineDone` с результатом, фильтр событий, тайм-аут в `tick`)

**Interfaces:**
- Produces: `final class Jail { final String root; final Set<Integer> ports; final long startedMillis; static final long TIMEOUT_MS, MEMORY; static final int MAX_RESULT; String map(String luaPath) throws FsException; }` — `map` возвращает путь в общей ФС (без ведущего `/`) или бросает «Нет такого файла»; `/rom...` → `rom...` (только чтение проверяет вызывающий).
- Produces: `LuaMachine.jail` (`final Jail`, null у обычных), конструктор с доп. параметром `Jail`; `int Computer.spawnPage(LuaMachine parent, String root, String file, Map<?,?> request) throws LuaMachine.Crash`.
- Produces: `void Computer.machineDone(LuaMachine m, boolean ok, String error, Object result)`; старый 3-аргументный вызов → `result = null`. `process_exit` = (pid, ok, error, result).

- [ ] **Step 1:** в `Jail.map` нормализовать через `FileSystem.normalize("/" + p.replace('\\', '/'))`; результат `rom` / `rom/...` — как есть; иначе `root + "/" + norm` (пустой norm → `root`).
- [ ] **Step 2:** `Computer.netReceive`: процессу с `jail != null` — только если `jail.ports.contains(port)`. `queueAll`: процессы с клеткой пропускать. Таймеры (`startTimer(…, m.events)`) — как есть.
- [ ] **Step 3:** `LuaMachine.trackMemory/reserve`: для клетки лимит `min(computer.memoryLimit() свободное, Jail.MEMORY - liveBytes)`.
- [ ] **Step 4:** `Computer.tick`: процесс с клеткой старше `TIMEOUT_MS` → `kill`, `machineDone(m, false, "timeout", null)` (строка `"timeout"` — договорённость с `httpd`). При завершении процесса с клеткой — `closePort` всех `jail.ports`.
- [ ] **Step 5:** `LuaMachine.resume`: когда `mainRoutine` закончился, первое возвращённое значение (строка ≤ `MAX_RESULT`, длиннее — обрезать + `"\n…(ответ обрезан)"`) сохранить и передать в `machineDone`.
- [ ] **Step 6:** `./gradlew build` — компилируется. Коммит «Песочница: клетка процесса».

### Task 2: Урезанные API в клетке

**Files:** Modify `LuaApis.java` (`install`, `fs`, `open`, `os`, `computer`, `net`).

**Interfaces:**
- Consumes: `Jail.map`, `LuaMachine.jail`.
- Produces: глобалы в клетке — `fs`, `os`, `computer`, `net`, `unicode`, `data`; нет `term`, `auth`, `camera`, `sound`, `switch`, `bios`, `gpu`. Ошибка запрета: `"Недоступно на хостинге"`.

- [ ] **Step 1:** `path(a, i)` → для клетки через `m.jail.map`; передать `m` в `fsFn` (все функции `fs` и `open`). Запись в `rom/...` → «Только для чтения». `getDrive/mounts/getLabel` — в клетке скрыть (`mounts` → пустая таблица).
- [ ] **Step 2:** `os`: в клетке `shutdown`, `reboot`, `setComputerLabel` → ошибка запрета.
- [ ] **Step 3:** `computer`: в клетке нет `spawn`, `spawnPage`, `kill`, `send`, `processes`, `info`, `gpu`, `pushSignal` (последний — чтобы не подделывать события самому себе это безопасно, но не нужно).
- [ ] **Step 4:** `net`: `open` — только 40000–49999 и запомнить в `jail.ports`; `close` — только из `jail.ports`; `send` — только порты 80 и 25; `broadcast`, `ping`, `io`, `bmc*`, `peers`, `stats`, `wifi`, `hostname` с аргументом (смена имени) — запрет; `dns` — только чтение (`names`, поиск), без регистрации.
- [ ] **Step 5:** новый `computer.spawnPage(root, file, request)` вне клетки: `root` и `file` — пути ФС, файл должен существовать; вызывает `c.spawnPage`.
- [ ] **Step 6:** `./gradlew build`. Коммит «Песочница: урезанные API».

### Task 3: Запуск страницы в BIOS

**Files:** Modify `src/main/resources/data/minepc/computer/bios.lua` (рядом с блоком `_PROCESS`, стр. ~371); `Computer.spawnPage` кладёт `_PAGE = {file=…, request=…}` вместо `_PROCESS` (в `LuaMachine.init`).

- [ ] **Step 1:** в BIOS: если `_PAGE` — собрать окружение `{request = p.request, echo = …}` с `__index = _G`, `loadfile(p.file, "t", env)` (путь уже внутри клетки, `/` = папка сайта), `pcall`; вернуть строку (`return` или `table.concat(out)`); ошибка → `error(msg, 0)`.
- [ ] **Step 2:** `./gradlew build`. Коммит.

### Task 4: httpd запускает страницы клиентов в клетке

**Files:** Modify `src/main/resources/data/minepc/computer/rom/programs/httpd.lua` (`handle`, ~стр. 101–122).

- [ ] **Step 1:** если `root` начинается с `/sites/` и файл `.lua`: счётчик `busy[root]`; ≥ 2 → `503`, «# Сервер занят\nПопробуйте позже». `pcall(computer.spawnPage, root, file_внутри_сайта, request)`; ошибка (нет процессов) → `503`.
- [ ] **Step 2:** ждать `process_exit` с этим pid, не дольше 6 с (свой таймер); прочие события — игнорировать (как сейчас, `httpd` обслуживает по одному). `ok` → `200`, `result` (не строка → `""`); `error == "timeout"` или свой таймер → `504` «# Страница слишком долго отвечала»; иначе `500` «# Ошибка в <path>\n<error>».
- [ ] **Step 3:** `/www` — старый путь без изменений.
- [ ] **Step 4:** Коммит.

### Task 5: Тесты, README, CI

**Files:** Modify `tools/computer/HeadlessTest.java` (`testNet`, после проверки `VHOST`, стр. ~719), `README.md` (раздел хостинга).

- [ ] **Step 1:** в `testNet` загрузить на хостинг (как `/www/index.page` выше) страницы и проверить через `http.get('test.mine/<стр>')`:
  - `steal.lua`: `return tostring(fs.exists('/etc')) .. ' ' .. tostring(fs.exists('../../www/hosting.lua'))` → тело `false false`;
  - `off.lua`: `os.shutdown()` → код 500, текст `Недоступно на хостинге`; после — `beta` отвечает на `test.mine/more`;
  - `loop.lua`: `while true do end` → код 504; следующий запрос `test.mine/more` → 200;
  - `book.lua`: дописывает `request.params.msg` в `book.txt`, возвращает содержимое; два запроса → оба текста; файл лежит в `dirB/sites/test.mine/book.txt`;
  - `port.lua`: `return tostring(pcall(net.open, 80)) .. tostring(pcall(net.send, 'x', 22, 'hi'))` → `falsefalse`;
  - `spy.lua`: `net.open(41500) local t=os.startTimer(1) local e repeat e={os.pullEvent()} until e[1]=='timer' or e[1]=='net_message' return e[1]` и параллельно запрос к сайту → `timer` (чужой GET не виден);
  - `fetch.lua`: `return (require('http').get('test.mine/more'))` → содержит «Ещё страница».
  - Review Focus 4: `tbl.lua` `return {1}` → 200, пустое тело.
- [ ] **Step 2:** `python tools/computer/run_test.py <cobalt-*.jar>` — все новые `expect` проходят, старые тоже.
- [ ] **Step 3:** README: что могут и не могут страницы клиентов, лимиты.
- [ ] **Step 4:** `./gradlew build`, коммит, пуш в `computers`, зелёный CI (`gh run list --branch computers --limit 1`).
