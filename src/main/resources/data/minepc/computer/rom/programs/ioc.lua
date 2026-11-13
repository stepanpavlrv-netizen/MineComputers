-- Контроллер ввода-вывода: ioc — список; ioc <кто> — состояние; ioc <кто> set <сторона> <0..15>;
-- ioc <кто> pulse <сторона> [тиков]; ioc <кто> mode <сторона> in|out; ioc <кто> watch; ioc <кто> name <имя>
local who, cmd, a, b = ...
local ioc = require("ioc")
local SIDE_RU = { north = "север", south = "юг", west = "запад", east = "восток", up = "верх", down = "низ" }

local function color(c) if term.isColor() then term.setTextColor(c) end end
local function pad(s, n) s = tostring(s) return s .. string.rep(" ", math.max(0, n - unicode.len(s))) end

local function show(st)
  color(colors.yellow)
  print((st.name ~= "" and st.name or "контроллер") .. "  " .. st.address)
  color(colors.white)
  for _, s in ipairs(ioc.sides) do
    local v = st.sides[s]
    local bar = string.rep("#", v.level) .. string.rep(".", 15 - v.level)
    local extra = v.fill and ("  заполнен " .. v.fill .. "/15") or ""
    local block = v.block and ("  " .. v.block:gsub("^minecraft:", "")) or ""
    if v.mode == "out" then color(colors.lightBlue) else color(colors.orange) end
    write(pad(SIDE_RU[s], 7) .. pad(v.mode == "out" and "выход" or "вход", 6))
    color(v.level > 0 and colors.red or colors.gray)
    write(bar)
    color(colors.white)
    print(string.format(" %2d%s%s", v.level, extra, block))
  end
  local players = {}
  for _, p in pairs(st.players) do players[#players + 1] = p end
  print("Игроки рядом: " .. (#players > 0 and table.concat(players, ", ") or "никого") .. "   свет: " .. st.light .. "   " .. st.time)
end

if not who then
  local list = ioc.list()
  if #list == 0 then print("Контроллеров в сети не видно") return end
  color(colors.yellow)
  print(pad("Имя", 17) .. pad("Адрес", 10) .. "Переходов")
  color(colors.white)
  for _, c in ipairs(list) do print(pad(c.name ~= "" and c.name or "-", 17) .. pad(c.address, 10) .. c.hops) end
  print("Подробнее: ioc <имя>")
  return
end

local st, err
if not cmd then
  st, err = ioc.get(who)
elseif cmd == "set" then
  st, err = ioc.set(who, a, tonumber(b or "15"))
elseif cmd == "pulse" then
  st, err = ioc.pulse(who, a, 15, tonumber(b or "10"))
elseif cmd == "mode" then
  st, err = ioc.mode(who, a, b)
elseif cmd == "name" then
  st, err = ioc.name(who, a or "")
elseif cmd == "watch" then
  st, err = ioc.watch(who)
  if not st then printError(err) return end
  print("Слежу за " .. who .. " (Ctrl+T — выход)")
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "io_change" then
      print(string.format("%s: %s %d -> %d", ev[2]:sub(1, 8), SIDE_RU[ev[3]] or ev[3], ev[5], ev[4]))
    elseif ev[1] == "io_player" then
      print(string.format("%s: %s %s", ev[2]:sub(1, 8), ev[3], ev[4] and "пришёл" or "ушёл"))
    end
  end
else
  print("Команды: set, pulse, mode, name, watch")
  return
end
if not st then printError(err) return end
show(st)
