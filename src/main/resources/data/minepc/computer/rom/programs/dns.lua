-- Имена Майннета (.mine): общие на весь мир, кто первый занял — того и имя.
--   dns                    — имена этого компьютера
--   dns register <имя>     — занять имя (до 4 на компьютер)
--   dns unregister <имя>   — освободить
--   dns who <имя>          — чьё имя (адрес)
--   dns all                — все имена мира
local cmd, name = ...
local function color(c) if term.isColor() then term.setTextColor(c) end end
local function list(rows)
  if #rows == 0 then print("Имён нет") return end
  for _, r in ipairs(rows) do
    color(colors.yellow) write(string.format("%-24s ", r.name))
    color(colors.white) print(r.address:sub(1, 8))
  end
end
if not cmd then
  local ok, rows = net.dns("names")
  if not ok then printError(rows) return end
  list(rows)
  if #rows == 0 then print("Занять: dns register <имя>   (сайт: httpd)") end
elseif cmd == "register" or cmd == "unregister" or cmd == "who" then
  if not name then print("dns " .. cmd .. " <имя>") return end
  local ok, res = net.dns(cmd == "who" and "lookup" or cmd, name)
  if not ok then printError(res) return end
  if cmd == "register" then print("Имя " .. res .. " теперь ваше. Запустите сайт: httpd")
  elseif cmd == "unregister" then print("Имя освобождено")
  else print(name .. " → " .. res) end
elseif cmd == "all" then
  local ok, rows = net.dns("all")
  if not ok then printError(rows) return end
  list(rows)
else
  print("dns [register|unregister|who|all] <имя>")
end
