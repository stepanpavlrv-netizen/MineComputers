-- Проверка связи: дойдёт ли сообщение и за сколько тиков (туда и обратно)
local target = ...
if not target then print("Использование: ping <имя или адрес>") return end
if net.speed() == 0 then print("Кабель не подключён") return end
local function find()
  for _, p in ipairs(net.peers()) do
    if p.address == target or p.name:lower() == target:lower() or (#target >= 4 and p.address:sub(1, #target) == target) then
      return p
    end
  end
end
local ok = 0
for i = 1, 3 do
  local p = find()
  if p then
    local ticks = p.hops * 2 + 2                  -- по тику на каждый коммутатор туда и обратно и тик на ответ
    sleep(ticks / 20)
    if term.isColor() then term.setTextColor(colors.cyan) end
    write((p.name ~= "" and p.name or "?") .. " ")
    if term.isColor() then term.setTextColor(colors.lime) end
    write(p.address:sub(1, 8))
    term.setTextColor(colors.white)
    print(string.format(": %d переход(а), %d тика", p.hops, ticks))
    ok = ok + 1
  else
    sleep(1)
    print("нет ответа от " .. target)
  end
end
print(ok .. " из 3 дошли")
