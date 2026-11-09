-- Сеть: свой адрес, имя, скорость порта и кто в сети
local function label(text)
  if term.isColor() then term.setTextColor(colors.lightGray) end
  write(text)
  term.setTextColor(colors.white)
end

local function speedText(bytesPerSecond)
  local bits = bytesPerSecond * 800                       -- в игре байт/с = бит/с ÷ 800
  if bits >= 1e9 then return (string.format("%g", bits / 1e9):gsub("%.", ",")) .. " Гбит/с" end
  return string.format("%d Мбит/с", math.floor(bits / 1e6 + 0.5))
end

local name = net.hostname()
label("Адрес:    ") print(net.address())
label("Имя:      ") print(name ~= "" and name or "нет (задать: hostname имя)")
local speed = net.speed()
if speed > 0 then
  label("Кабель:   ") print(speedText(speed) .. " -> " .. textutils.formatSize(speed) .. "/с в игре")
  label("В сети:   ") print(#net.peers() .. " компьютер(ов), список: peers")
else
  label("Кабель:   ") print("не подключён")
end
local s = net.stats()
label("Трафик:   ") print("принято " .. textutils.formatSize(s.received) .. ", отправлено " .. textutils.formatSize(s.sent))
