-- Имя компьютера в сети (то же, что label)
local name = ...
if name then net.hostname(name) end
local n = net.hostname()
print(n ~= "" and n or "Имени нет. Задать: hostname имя")
