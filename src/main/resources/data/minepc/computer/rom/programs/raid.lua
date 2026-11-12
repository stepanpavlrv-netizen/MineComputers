-- RAID-контроллер серверной платы: диски в отсеках работают вместе
--   raid                         — состояние массива и дисков
--   raid create mirror|stripe|raid5 — собрать массив из всех дисков в отсеках (данные на них сотрутся)
--   raid delete                  — разобрать массив (данные массива пропадут)
local cmd, arg = ...
local color = term.isColor()
local function c(col) if color then term.setTextColor(col) end end

local info = bios.raid()
if not info then
  print("Нет RAID-контроллера: он есть только на серверной плате.")
  return
end

local function size(b)
  if b >= 1048576 then return string.format("%.1f МБ", b / 1048576) end
  return string.format("%d КБ", math.floor(b / 1024))
end

local function confirm(text)
  c(colors.yellow) print(text) c(colors.white)
  write("Продолжить? (да/нет): ")
  local a = (read() or ""):lower()
  return a == "да" or a == "y" or a == "yes" or a == "д"
end

if cmd == nil or cmd == "status" then
  local a = info.array
  if a then
    c(colors.lightGray) write("Массив:  ") c(colors.cyan) print(a.name)
    c(colors.lightGray) write("Диски:   ") c(colors.white) print(a.present .. " из " .. a.members)
    c(colors.lightGray) write("Состоян: ")
    if a.state == "ok" then c(colors.lime) print("исправен")
    elseif a.state == "degraded" then c(colors.yellow) print("без резерва — замените диск")
    elseif a.state == "rebuilding" then c(colors.yellow) print("восстановление " .. (a.rebuild or 0) .. "%")
    elseif a.state == "failed" then c(colors.red) print("развалился — не хватает дисков")
    else c(colors.lightGray) print("не собран") end
    if a.path then c(colors.lightGray) write("Путь:    ") c(colors.white) print(a.path) end
  else
    print("Массива нет. raid create mirror|stripe|raid5 — собрать")
  end
  c(colors.lightGray) print("Отсеки:")
  for i, d in ipairs(info.disks) do
    c(colors.lightGray) write(string.format("  %d  ", i))
    if d.empty then
      c(colors.gray) print("пусто")
    else
      local kind = d.kind == "hdd" and "HDD" or "SSD"
      c(colors.white) write(kind .. " " .. size(d.size))
      if d.failed then c(colors.red) write("  отказал")
      elseif d.rebuilding then c(colors.yellow) write("  восстанавливается")
      elseif d.wear >= 90 then c(colors.yellow) write("  износ " .. d.wear .. "% — замените")
      else c(colors.lightGray) write("  износ " .. d.wear .. "%") end
      if d.member then c(colors.cyan) write("  [в массиве]") end
      print()
    end
  end
  c(colors.white)
elseif cmd == "create" then
  if not arg then print("raid create mirror (зеркало) | stripe (полоса) | raid5") return end
  if not confirm("Все диски в отсеках войдут в массив, данные на них сотрутся.") then return end
  local ok, err = bios.raidCreate(arg)
  if not ok then c(colors.red) print(err) c(colors.white) return end
  c(colors.lime) print("Массив создан. Перезагрузка…") c(colors.white)
  sleep(1)
  os.reboot()
elseif cmd == "delete" then
  if not confirm("Массив будет разобран, его данные пропадут.") then return end
  local ok, err = bios.raidDelete()
  if not ok then c(colors.red) print(err) c(colors.white) return end
  c(colors.lime) print("Массив разобран. Перезагрузка…") c(colors.white)
  sleep(1)
  os.reboot()
else
  print("raid [status] | raid create mirror|stripe|raid5 | raid delete")
end
