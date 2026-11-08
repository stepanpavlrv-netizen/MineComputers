-- Остановить процесс по номеру (номера показывает ps)
local pid = tonumber((...))
if not pid then print("Использование: kill <номер процесса>"); return end
if computer.kill(pid) then
  print("Процесс " .. pid .. " остановлен")
else
  printError("Нет такого процесса (главный остановить нельзя)")
end
