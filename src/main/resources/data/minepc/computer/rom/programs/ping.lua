-- Проверка связи: эхо-запрос уходит по сети, компьютер на том конце отвечает,
-- время — настоящее (туда и обратно, с задержкой коммутаторов и скоростью самой медленной связи)
local target, count = ...
if not target then print("Использование: ping <имя или адрес> [сколько раз]") return end
count = tonumber(count or "") or 4
if net.speed() == 0 then print("Кабель не подключён") return end

local function nameOf(address)
  for _, p in ipairs(net.peers()) do
    if p.address == address then return p.name end
  end
  return ""
end

local ok, total, best, worst = 0, 0, math.huge, 0
for i = 1, count do
  local id = net.ping(target)
  if not id then print("Не удалось отправить запрос") return end
  local timer = os.startTimer(2)
  local from, ms, hops
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "net_pong" and ev[3] == id then
      from, ms, hops = ev[2], ev[4], ev[5]
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      break
    end
  end
  if from then
    ok = ok + 1
    total = total + ms
    best, worst = math.min(best, ms), math.max(worst, ms)
    local name = nameOf(from)
    if term.isColor() then term.setTextColor(colors.cyan) end
    write(name ~= "" and (name .. " ") or "")
    if term.isColor() then term.setTextColor(colors.lime) end
    write(from:sub(1, 8))
    term.setTextColor(colors.white)
    print(string.format(": время %d мс, переходов %d", ms, hops))
  else
    if term.isColor() then term.setTextColor(colors.red) end
    print("нет ответа от " .. target)
    term.setTextColor(colors.white)
  end
  if i < count then sleep(0.5) end
end
if ok > 0 then
  print(string.format("%d из %d дошли, время: мин %d, сред %d, макс %d мс", ok, count, best, math.floor(total / ok + 0.5), worst))
else
  print(string.format("0 из %d дошли", count))
end
