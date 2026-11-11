-- Железо компьютера и его настоящие лимиты
local i, l = computer.info(), computer.limits()
local function num(n)
  if n >= 1e6 then return string.format("%.1f млн", n / 1e6) end
  if n >= 1e3 then return string.format("%.0f тыс.", n / 1e3) end
  return tostring(math.floor(n))
end
local used = l.memory - computer.freeMemory()
print("Процессор:   " .. i.cpu)
print("Скорость:    " .. num(l.opsPerSecond) .. " команд/с на ядро, ядер: " .. l.cores)
print("Нагрузка:    " .. num(computer.opsUsed()) .. " команд за последнюю секунду")
print("Память:      " .. textutils.formatSize(used) .. " из " .. textutils.formatSize(l.memory))
print("Графика:     " .. (i.gpu ~= "" and i.gpu or "нет (текстовый режим)"))
print("Экран:       до " .. l.maxWidth .. "×" .. l.maxHeight .. ", " .. l.depth .. " бит цвета")
print("Отрисовка:   " .. num(l.gpuCellsPerSecond) .. " клеток/с, видеопамять " .. num(l.vram) .. " клеток")
print("Оценка:      " .. i.score .. " очков")
print(string.format("Работает:    %.0f с", computer.uptime()))
