-- Список процессов (каждый работает на своём ядре процессора)
local names = { running = "работает", waiting = "ждёт", starting = "запуск", finished = "завершён" }
local list = computer.processes()
if term.isColor() then term.setTextColor(colors.yellow) end
print(string.format("%-4s %-9s %9s %8s  %s", "PID", "Состояние", "Память", "Команд", "Программа"))
term.setTextColor(colors.white)
for _, p in ipairs(list) do
  local ops = p.ops >= 1e6 and string.format("%.1fM", p.ops / 1e6) or string.format("%.0fK", p.ops / 1e3)
  print(string.format("%-4d %-9s %9s %8s  %s", p.pid, names[p.status] or p.status,
    textutils.formatSize(p.memory), ops, p.path))
end
local l = computer.limits()
print("Ядер: " .. l.cores .. ", процессов: " .. #list .. " из " .. l.maxProcesses)
