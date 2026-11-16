# MineComputers — правила для Claude

Мод для Minecraft **NeoForge 1.21.1**: компьютеры с Lua, железо по уровням, сети (кабели, Wi-Fi, стойки),
майннет (сайты, почта, хостинг), графическая система **Wired**. mod_id `minepc`, пакет `com.strikerr212.minepc`.
Репозиторий `stepanpavlrv-netizen/minecomputers`: работа идёт в ветке `computers`, на выпуске сливается в `main`.
Подробное описание мода — `README.md` (его же обновлять при каждой новой возможности).
**План работ и кто что делает — `PLAN.md`.** Над модом работают два Claude (Claude Code у автора и Claude в облаке):
перед работой `git pull` и загляни в `PLAN.md`, взятый пункт отметь там и запушь сразу.

## Как общаться

- **Только по-русски.** Код пишет Claude; автор мода — игрок и заказчик, не программист: объяснять человеческим
  языком, без внутренней кухни, но честно.
- **«Что думаешь», «надо подумать», «как думаешь, можно…»** — это вопрос: дать мнение или план, **код не писать**,
  пока не скажут «делай».
- **Визуальное** (модели, интерфейсы, окна) — сначала макет на согласование, потом код.
- **Озвучивать самому** подводные камни, которые заметил по ходу: дыры в безопасности, то, что не доделано или
  не проверено, решения, которые стоит принять автору. Не говорить «готово», пока не проверено (сборка, тесты,
  а для видимого — запуск или скриншот); если что-то проверено только частично — так и сказать.
- В конце работы — коротко: что сделано, как проверено, что осталось и что решить автору.
- Почту и личные данные автора никуда не передавать.

## Первая настройка (сделай сам при первом запуске, если ещё не сделано)

Проверь `claude plugin list`. Если чего-то из списка нет — поставь (это shell-команды, их можно запускать из сессии),
потом `/reload-plugins`:

```
claude plugin install jdtls-lsp@claude-plugins-official      # Java: ошибки компиляции сразу после правки
claude plugin install lua-lsp@claude-plugins-official        # Lua: ошибки синтаксиса в программах компьютера
claude plugin install github@claude-plugins-official         # репозиторий, Actions, релизы
claude plugin install superpowers@claude-plugins-official    # порядок работы: план, проверка перед «готово»
```

LSP-плагинам нужны сами языковые серверы в PATH: **jdtls** (нужен JDK 21 — он уже есть для сборки мода) и
**lua-language-server**. Для симулятора Wired — Python 3 и `pip install lupa pillow`. Для CI — `gh` (GitHub CLI),
вход `gh auth login`. Перед установкой программ в систему (не плагинов) — спроси автора одной строкой, каким
способом ставить (winget/scoop/вручную), и потом ставь сам. Ошибки плагинов видны в `/plugin` → Errors.

Не ставить без просьбы автора: Caveman (режет ответы), GSD (тяжёлый, у проекта свой цикл), плагины веб-дизайна.

### Графика: модели и текстуры (автор согласился)

Чтобы не делать модели вслепую, подключи редакторы как MCP-серверы (это сторонние проекты — ставить только из
оригинальных репозиториев, перед установкой самих программ спросить автора, каким способом):

- **Blockbench** (редактор моделей Minecraft, бесплатный) + **Blockbench MCP** — `github.com/jasonjgardner/blockbench-mcp-plugin`
  (плагин внутри Blockbench, подключается к Claude Code по инструкции в его README). Строить модель кубиками, рисовать
  текстуру, делать снимок модели и смотреть на него до отправки в игру. Экспорт — Java Block/Item JSON в
  `src/main/resources/assets/minepc/models/`, текстуры — в `textures/`.
- **Aseprite MCP / pixel-plugin** (`github.com/willibrandon/pixel-plugin`) — пиксель-арт 16×16, значки, кадры анимаций.
  Нужен сам Aseprite (платный; можно собрать из исходников) — только если автор захочет.
- Генераторы в `tools/assetgen/` остаются: модель из Blockbench, которая должна генерироваться скриптом (варианты,
  повороты), — перенести в генератор, а не держать две копии.

## Сборка и проверка

| Что | Команда |
|---|---|
| Собрать мод | `./gradlew build` (Windows: `.\gradlew build`) |
| Запустить игру с модом | `./gradlew runClient` |
| Игровые тесты на сервере | `./gradlew runGameTestServer` (тесты — `src/main/java/.../test/`) |
| Описания железа | `python tools/spectest/run.py` |
| Компьютеры без Minecraft (Lua, сеть, хостинг…) | `python tools/computer/run_test.py <путь к cobalt-*.jar>` — jar в `~/.gradle/caches` после сборки; тесты — `tools/computer/HeadlessTest.java` |
| Wired без Minecraft (окна, программы, PNG экрана) | `python tools/wiredsim/t12.py` и др. `t*.py` (`sim.py` — эмулятор с заглушками) |
| После добавления/удаления файлов в `computer/rom` или `computer/disks` | `python tools/computer/update_rom_list.py` — иначе файлы не попадут в игру |
| Строки перевода (ru + en сразу) | `python tools/addlang.py keys.json` (`{"ключ": ["рус", "eng"]}`) |

**CI** (`.github/workflows/build.yml`) на пуш в `computers`: сборка, игровые тесты, HeadlessTest, запуск сервера и
клиента, **скриншоты из игры** (сценарий — `client/CiScreenshots.java`, по тикам) → ветка `ci-screenshots`
(`git fetch origin ci-screenshots && git archive FETCH_HEAD | tar -x -C папка`; там же `client.log`, строки `[CI-…]`).
Проверка: `gh run list --branch computers --limit 1`, `gh run view <id> --log-failed`. Новую видимую возможность —
добавить в `CiScreenshots`, сетевую/программную — в `HeadlessTest`.

## Выпуск версии

1. `mod_version` в `gradle.properties` (0.MINOR.PATCH), README, при изменении Wired — его версия в
   `computer/ProgramDisks.java`, `disks/wired/install.lua`, `disks/wired/system/boot.lua`, `disks/wired/readme.txt`.
2. Коммит в `computers` (сообщение по-русски: что сделано), пуш, дождаться зелёного CI.
3. Слить: `git checkout main && git merge --ff-only computers && git push origin main && git checkout computers`.
4. Автор ставит мод так: удаляет `src`, распаковывает архив поверх папки MDK и запускает `.\gradlew runClient`.
   Архив: `git archive --format=zip --prefix=MDK-1.21.1-ModDevGradle-main/ -o MineComputers-X.Y.Z.zip HEAD -- . ':!gradle/wrapper/gradle-wrapper.properties'`.
   Если работаешь прямо в его папке с git — архив не нужен, достаточно `runClient`.

## Где что лежит

- Java: `src/main/java/com/strikerr212/minepc/` — `block/` (корпуса, мониторы, стойки), `computer/` (Lua-машина,
  API: `LuaApis.java`, `OcApis.java`, `Computer.java`), `lan/` (сеть, кабели, корды, DNS, коммутаторы, роутер),
  `hardware/` (уровни железа, `Perf.java` — экраны и скорости), `client/` (экраны, рендер, `CiScreenshots`).
- Программы компьютера (Lua): `src/main/resources/data/minepc/computer/` — `bios.lua`, `rom/programs/*.lua`
  (команды), `rom/openos/*.lua` (библиотеки: `http`, `hosting`, `hostadm`…), `disks/<id>/` (дискеты программ).
- **Wired**: `disks/wired/system/` — `boot.lua`, `lib/` (`wm` окна, `ui` элементы, `gfx`, `apps` список программ,
  `page` разметка сайтов), `apps/` (встроенные программы). Программы на дискетах: `disks/<mail|office|cameras|browser|site>/app/`.
- Модели и текстуры генерируются скриптами в `tools/assetgen/` (см. README, раздел про генераторы).

## Грабли, на которые уже наступали

- Lua — **Cobalt (5.2)**: нет `//`, `&`, `>>`, `<<` — считать через `math.floor` и деление.
- Java → Lua: списки и словари переводит `LuaApis.toLua`; новый тип данных — проверить, что он не превращается в строку.
- В Wired фоновые задачи (`wm.spawn`) получают события от окна; библиотеки, которые возвращают «чужие» события
  в очередь (`os.queueEvent`), там дублируют клики — для Wired передавать `quiet` (см. `hosting.connect`).
- Lua-код, который генерируется строкой из Python/Java, — проверять, что `\n` внутри Lua-строк экранирован
  (так ломалась панель хостинга).
- HeadlessTest смотрит на экран 50×16: длинный вывод `help` и т. п. вытесняет нужные строки.
- Дискеты программ обновляются сами по отпечатку файлов (`.version`); установленные программы — переустановкой.
- У автора Windows с русским именем пользователя: Gradle работает через `GRADLE_USER_HOME` (например `D:\gradle-home`),
  а `gradle/wrapper/gradle-wrapper.properties` может быть изменён локально (свой путь к Gradle) — **не коммитить его**
  (README → «Если что-то не запускается»).

## Что в очереди

Список, порядок и решения автора — в `PLAN.md` (уточнять у автора перед началом пункта).
