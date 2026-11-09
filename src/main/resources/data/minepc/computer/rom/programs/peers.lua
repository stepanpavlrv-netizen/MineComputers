-- Кто ещё в сети: имя, адрес и сколько коммутаторов до него
local list = net.peers()
if net.speed() == 0 then print("Кабель не подключён") return end
if #list == 0 then print("В сети никого (включённых компьютеров не видно)") return end
if term.isColor() then term.setTextColor(colors.yellow) end
print(string.format("%-16s %-10s %s", "Имя", "Адрес", "Переходов"))
term.setTextColor(colors.white)
for _, p in ipairs(list) do
  print(string.format("%-16s %-10s %d", p.name ~= "" and p.name or "-", p.address:sub(1, 8), p.hops))
end
