-- Загрузить сайт на хостинг (сервер с hostd): сайт работает там круглосуточно, даже когда этот компьютер выключен.
--   deploy                          — ещё раз туда же, куда в прошлый раз (запомнено в /www/.deploy)
--   deploy <сервер> <сайт> [папка]  — загрузить папку (по умолчанию /www) как сайт «сайт.mine»
--   deploy info <сервер>            — мои сайты на сервере и место
--   deploy remove <сервер> <сайт>   — удалить сайт с хостинга
--   deploy servers                  — серверы с хостингом в сети
-- Логин и пароль хостинга выдаёт владелец сервера (hostd add). Загружаются только изменённые файлы.
local args = { ... }
local hosting = require("hosting")

local function color(c) if term.isColor() then term.setTextColor(c) end end

local function ask(label, mask)
  write(label)
  return read(mask)
end

local function remembered(dir)
  local f = fs.exists(fs.combine(dir, ".deploy")) and fs.open(fs.combine(dir, ".deploy"), "r")
  if not f then return nil end
  local line = f.readAll() or ""
  f.close()
  local server, user, site = line:match("^([^|]*)|([^|]*)|([^|\n]*)")
  return server, user, site
end

local function remember(dir, server, user, site)
  local f = fs.open(fs.combine(dir, ".deploy"), "w")
  if f then f.write(server .. "|" .. user .. "|" .. site .. "\n") f.close() end
end

if args[1] == "servers" then
  local list = hosting.discover()
  if #list == 0 then print("Серверов с хостингом в сети не видно") return end
  for _, s in ipairs(list) do
    color(colors.yellow) write(s.name .. "  ") color(colors.lightGray)
    print(s.address:sub(1, 8) .. "  сайтов: " .. s.sites .. (s.free and ("  свободно " .. textutils.formatSize(s.free)) or ""))
  end
  color(colors.white)
  return
end

if args[1] == "info" or args[1] == "remove" then
  local server = args[2]
  if not server or (args[1] == "remove" and not args[3]) then print("deploy " .. args[1] .. " <сервер>" .. (args[1] == "remove" and " <сайт>" or "")) return end
  local s = hosting.connect(server, ask("Логин: "), ask("Пароль: ", "*"))
  if args[1] == "remove" then
    local ok, err = s:remove(args[3])
    if ok then print("Сайт " .. tostring(ok) .. " удалён с хостинга") else printError(err) end
    return
  end
  local info, err = s:info()
  if not info then printError(err) return end
  print(string.format("%s: занято %s из %s", info.user, textutils.formatSize(info.used), textutils.formatSize(info.quota)))
  if info.left then
    color(info.left < 0 and colors.red or info.left <= 3 and colors.yellow or colors.lightGray)
    print(info.left < 0 and ("Срок работы закончился " .. hosting.fmtDate(info.till) .. " — сайты показывают заглушку")
      or ("Срок работы: до " .. hosting.fmtDate(info.till) .. ", осталось " .. info.left .. " дн."))
  end
  if info.paused then color(colors.yellow) print("⏸ Все сайты приостановлены владельцем хостинга" .. (info.paused ~= "" and (": " .. info.paused) or "")) end
  for _, site in ipairs(info.sites) do
    color(colors.cyan) write("  " .. site.name) color(colors.lightGray)
    write(string.format("  %s, файлов %d", textutils.formatSize(site.size), site.files))
    if site.state == "paused" then color(colors.yellow) write("  ⏸ приостановлен") end
    print()
  end
  color(colors.white)
  if #info.sites == 0 then print("Сайтов нет. Загрузить: deploy " .. server .. " <сайт>") end
  return
end

local server, site, dir = args[1], args[2], args[3]
local user
dir = shell and shell.resolve and dir and shell.resolve(dir) or dir or "/www"
if not server then
  local s, u, n = remembered(dir)
  if not s then
    print("deploy <сервер> <сайт> [папка]  — серверы с хостингом: deploy servers")
    return
  end
  server, user, site = s, u, n
  print("Загружаю " .. dir .. " → " .. site .. " на " .. server)
end
if not site then print("deploy <сервер> <сайт> [папка]") return end
if not fs.isDir(dir) then printError("Нет папки " .. dir) return end
if not user or user == "" then user = ask("Логин хостинга: ") end
local pass = ask("Пароль: ", "*")
local s = hosting.connect(server, user, pass)
local _, y = term.getCursorPos()
local url, sent, bytes, state = s:upload(dir, site, function(i, n, file)
  term.setCursorPos(1, y)
  term.clearLine()
  write(string.format("%d/%d %s", i, n, file))
end)
print()
if not url then printError(url == nil and tostring(sent) or "") return end
remember(dir, server, user, site)
color(colors.lime)
print("Готово: " .. url .. " — работает на сервере " .. server)
color(colors.white)
print(string.format("Отправлено файлов: %d (%s); остальные не менялись", sent or 0, textutils.formatSize(bytes or 0)))
local warn = hosting.warning(state)
if warn then color(colors.yellow) print(warn) color(colors.white) end
