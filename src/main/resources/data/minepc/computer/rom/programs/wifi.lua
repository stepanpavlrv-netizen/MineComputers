-- Wi-Fi: состояние, поиск сетей, подключение
local cmd, a1, a2 = ...
local color = term.isColor()
local function c(col) if color then term.setTextColor(col) end end

local function bars(signal)
  local n = signal >= 75 and 4 or signal >= 50 and 3 or signal >= 25 and 2 or 1
  return ("#"):rep(n) .. ("."):rep(4 - n)
end

local st = net.wifi.status()
if not st then
  c(colors.red) print("Нет Wi-Fi адаптера") c(colors.white)
  print("Вставьте USB Wi-Fi адаптер в USB-порт корпуса")
  return
end

if cmd == nil or cmd == "status" then
  c(colors.lightGray) write("Адаптер: ") c(colors.white) print(st.adapter)
  if st.ssid == "" then
    print("Сеть не выбрана. wifi scan — найти сети")
  elseif st.connected then
    c(colors.lightGray) write("Сеть: ") c(colors.cyan) print(st.ssid)
    c(colors.lightGray) write("Сигнал: ") c(st.signal >= 35 and colors.lime or colors.yellow)
    print(bars(st.signal) .. " " .. st.signal .. "%")
    c(colors.lightGray) write("Скорость: ") c(colors.white) print(st.speed .. " Мбит/с")
  else
    c(colors.lightGray) write("Сеть: ") c(colors.white) print(st.ssid)
    c(colors.red) print("Не подключено: " .. (st.error or "подключение…")) c(colors.white)
  end
  c(colors.white)
elseif cmd == "scan" then
  local list = net.wifi.scan()
  if #list == 0 then print("Сетей не слышно (подождите секунду после включения)") return end
  for _, n in ipairs(list) do
    c(n.signal >= 35 and colors.lime or colors.yellow) write(bars(n.signal) .. " ")
    c(n.ssid == st.ssid and colors.cyan or colors.white) write(n.ssid)
    c(colors.lightGray)
    print(string.format("  %d%%  %d Мбит/с%s", n.signal, n.speed, n.secured and "  [пароль]" or ""))
  end
  c(colors.white)
elseif cmd == "connect" then
  if not a1 then print("Использование: wifi connect <сеть> [пароль]") return end
  net.wifi.connect(a1, a2)
  write("Подключение к " .. a1 .. " ")
  for i = 1, 6 do
    sleep(0.5)
    st = net.wifi.status()
    if st.ssid == a1 and (st.connected or st.error) then break end
    write(".")
  end
  print()
  if st.connected then
    c(colors.lime) print("Подключено: сигнал " .. st.signal .. "%, " .. st.speed .. " Мбит/с")
  else
    c(colors.red) print("Не удалось: " .. (st.error or "нет ответа"))
  end
  c(colors.white)
elseif cmd == "off" or cmd == "disconnect" then
  net.wifi.disconnect()
  print("Wi-Fi отключён")
else
  print("Использование: wifi [scan | connect <сеть> [пароль] | off]")
end
