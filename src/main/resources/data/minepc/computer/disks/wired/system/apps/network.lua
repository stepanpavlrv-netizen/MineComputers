-- Wired: Сеть — свой адрес и имя, кто в сети (с проверкой связи), коммутаторы (порты, VLAN), Wi-Fi.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm

local VLANS = { "", "red", "orange", "yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta",
  "pink", "white", "light_gray", "gray", "brown", "black" }
local VLAN_RGB = { red = 0xb02e26, orange = 0xf9801d, yellow = 0xfed83d, lime = 0x80c71f, green = 0x5e7c16,
  cyan = 0x169c9c, light_blue = 0x3ab3da, blue = 0x3c44aa, purple = 0x8932b8, magenta = 0xc74ebd, pink = 0xf38baa,
  white = 0xf9fffe, light_gray = 0x9d9d97, gray = 0x474f52, brown = 0x835432, black = 0x1d1d21 }
local KINDS = { desk = "Коммутатор", rack = "Стоечный коммутатор", rackbus = "Шина стоек", router = "Wi-Fi роутер",
  radio = "Радиомодем" }
local SIDES = { front = "спереди", back = "сзади", left = "слева", right = "справа", cord = "корд", bus = "сервер", wan = "WAN" }

local function speedText(bps)
  local bits = bps * 800
  if bits >= 1e9 then return (string.format("%g", bits / 1e9):gsub("%.", ",")) .. " Гбит/с" end
  return string.format("%d Мбит/с", math.floor(bits / 1e6 + 0.5))
end

local function bars(sig)
  local n = sig >= 75 and 4 or sig >= 50 and 3 or sig >= 25 and 2 or 1
  return ("▮"):rep(n) .. ("▯"):rep(4 - n)
end

return function()
  local app = { title = "Сеть", icon = "net", w = 84, h = 28, minW = 64, minH = 18 }
  local st = { tab = 1, pings = {}, pingIds = {}, sw = nil, scan = nil, scroll = 0 }
  local win
  local hasSwitch = type(switch) == "table"

  local function tick()
    if win.closed then return end
    if not win.min then wm.redraw(win) end
    wm.after(2, tick)
  end

  function app.init(w) win = w tick() end

  local function ping(addr)
    local id = net.ping(addr)
    if id then
      st.pingIds[id] = addr
      st.pings[addr] = "…"
      wm.after(2, function() if st.pings[addr] == "…" then st.pings[addr] = "нет ответа" wm.redraw(win) end end)
    end
  end

  local function row(c, y, label, value, color)
    c.text(3, y, label, T.dim)
    c.text(18, y, G.cut(value, c.w - 20), color or T.fg)
  end

  local TABS = { "Обзор", "Компьютеры", "Коммутаторы", "Wi-Fi" }

  function app.draw(w, c)
    local x = 2
    for i, name in ipairs(TABS) do
      local act = i == st.tab
      c.text(x, 1, " " .. name .. " ", act and T.fg or T.dim, act and T.sel or T.win)
      if act then c.text(x, 2, string.rep("▔", G.len(name) + 2), T.accent) end
      c.hit(x, 1, G.len(name) + 2, 1, "tab", i)
      x = x + G.len(name) + 3
    end
    local speed = net.speed()
    if st.tab == 1 then
      local name = net.hostname()
      row(c, 4, "Адрес", net.address())
      row(c, 5, "Имя", name ~= "" and name or "нет")
      c.button(c.w - 14, 5, "Изменить", "rename")
      row(c, 7, "Подключение", speed > 0 and speedText(speed) or "не подключено", speed > 0 and T.ok or T.err)
      local ws = net.wifi and net.wifi.status() or nil
      if ws and ws.connected then row(c, 8, "Wi-Fi", ws.ssid .. "  " .. bars(ws.signal) .. " " .. ws.signal .. "%", T.fg)
      elseif ws then row(c, 8, "Wi-Fi", ws.ssid ~= "" and ("не подключено: " .. (ws.error or "…")) or "адаптер есть, сеть не выбрана", T.dim)
      else row(c, 8, "Wi-Fi", "нет адаптера", T.faint) end
      local s = net.stats()
      row(c, 10, "Принято", textutils.formatSize(s.received))
      row(c, 11, "Отправлено", textutils.formatSize(s.sent))
      row(c, 13, "В сети", speed > 0 and (#net.peers() .. " компьютер(ов)") or "—")
      if speed == 0 then
        c.text(3, 15, "Подключите кабель к компьютеру или вставьте USB Wi-Fi адаптер", T.warn)
      end
    elseif st.tab == 2 then
      if speed == 0 then c.text(3, 4, "Нет сети", T.faint) return end
      local list = net.peers()
      c.text(3, 3, G.pad("Имя", 20) .. G.pad("Адрес", 12) .. G.pad("Переходов", 11) .. "Связь", T.dim)
      if #list == 0 then c.text(3, 5, "Включённых компьютеров не видно", T.faint) end
      for i, p in ipairs(list) do
        local y = 3 + i
        if y > c.h - 1 then break end
        c.text(3, y, G.pad(p.name ~= "" and p.name or "—", 20), T.fg)
        c.text(23, y, G.pad(p.address:sub(1, 8), 12), T.dim)
        c.text(35, y, tostring(p.hops), T.dim)
        local pr = st.pings[p.address]
        if pr then c.text(46, y, G.cut(pr, c.w - 60), pr:match("мс") and T.ok or T.dim) end
        c.button(c.w - 22, y, "Пинг", "ping", "ghost", p.address)
        c.button(c.w - 14, y, "Написать", "write", "ghost", p.address)
      end
      c.text(3, c.h, "«Написать» откроет Чат с личной перепиской", T.faint)
    elseif st.tab == 3 then
      if not hasSwitch then c.text(3, 4, "Нет доступа к коммутаторам", T.faint) return end
      local list = switch.list()
      if #list == 0 then c.text(3, 4, "Управляемых коммутаторов в сети не видно", T.faint) return end
      local y = 3
      for _, s in ipairs(list) do
        local act = st.sw == s.id
        if act then c.fill(2, y, 30, 1, T.sel) end
        c.text(3, y, G.cut((KINDS[s.kind] or s.kind) .. " " .. s.id, 28), act and T.fg or T.dim, act and T.sel or T.win)
        c.hit(2, y, 30, 1, "sw", s.id)
        y = y + 1
        if y > c.h then break end
      end
      if not st.sw then st.sw = list[1].id end
      local ports, err = switch.ports(st.sw)
      local x0 = 34
      if not ports then c.text(x0, 3, G.cut(tostring(err), c.w - x0), T.err) return end
      c.text(x0, 3, G.pad("Порт", 6) .. G.pad("Где", 12) .. G.pad("VLAN", 12) .. "Устройство", T.dim)
      for i, p in ipairs(ports) do
        local yy = 3 + i
        if yy > c.h - 1 then break end
        local dot = not p.enabled and T.err or p.link and T.ok or T.faint
        c.text(x0, yy, "●", dot)
        c.text(x0 + 2, yy, tostring(p.port), T.fg)
        c.text(x0 + 6, yy, G.cut((SIDES[p.side] or p.side) .. " " .. p.n, 11), T.dim)
        local vl = p.vlan ~= "" and p.vlan or "общий"
        c.text(x0 + 18, yy, "■", VLAN_RGB[p.vlan] or T.faint)
        c.text(x0 + 20, yy, G.cut(vl, 9), T.fg)
        c.hit(x0 + 18, yy, 11, 1, "vlan", p.port)
        local dev = (p.device ~= "" and p.device or "—") .. ((p.label and p.label ~= "") and (" [" .. p.label .. "]") or "")
        c.text(x0 + 30, yy, G.cut(dev, math.max(1, c.w - x0 - 40)), T.dim)
        c.button(c.w - 7, yy, p.enabled and "вкл" or "выкл", "port", p.enabled and "normal" or "off", p.port)
        if not p.enabled then c.hit(c.w - 7, yy, 6, 1, "port", p.port) end
      end
      c.text(x0, c.h, "Щёлкните VLAN — сменить цвет, «вкл» — выключить порт", T.faint)
    else
      local ws = net.wifi and net.wifi.status() or nil
      if not ws then
        c.text(3, 4, "Нет Wi-Fi адаптера", T.fg)
        c.text(3, 5, "Вставьте USB Wi-Fi адаптер в USB-порт корпуса", T.dim)
        return
      end
      row(c, 3, "Адаптер", ws.adapter)
      if ws.connected then
        row(c, 4, "Сеть", ws.ssid .. "  " .. bars(ws.signal) .. " " .. ws.signal .. "%  " .. ws.speed .. " Мбит/с", T.ok)
        c.button(c.w - 14, 4, "Отключить", "wifioff")
      else
        row(c, 4, "Сеть", ws.ssid ~= "" and (ws.ssid .. ": " .. (ws.error or "подключение…")) or "не выбрана", T.dim)
      end
      c.button(3, 6, "Найти сети", "scan", "primary")
      if st.scan then
        if #st.scan == 0 then c.text(3, 8, "Сетей не слышно — подождите секунду и поищите снова", T.faint) end
        for i, n in ipairs(st.scan) do
          local y = 7 + i
          if y > c.h then break end
          local cur = n.ssid == ws.ssid
          c.text(3, y, bars(n.signal), n.signal >= 35 and T.ok or T.warn)
          c.text(9, y, G.pad(n.ssid, 24), cur and T.accent or T.fg)
          c.text(34, y, n.signal .. "%  " .. n.speed .. " Мбит/с" .. (n.secured and "  · пароль" or ""), T.dim)
          if not (cur and ws.connected) then c.button(c.w - 14, y, "Подключить", "connect", "ghost", i) end
        end
      end
    end
  end

  local function wifiConnect(n, pass)
    net.wifi.connect(n.ssid, pass)
    local tries = 0
    local function check()
      tries = tries + 1
      local s = net.wifi.status()
      if s and s.ssid == n.ssid and (s.connected or s.error) or tries > 8 then
        wm.notify({ title = "Wi-Fi", text = (s and s.connected) and ("Подключено к " .. n.ssid) or ("Не удалось: " .. tostring(s and s.error or "нет ответа")) })
        wm.redraw(win)
        return
      end
      wm.after(0.5, check)
    end
    wm.after(0.5, check)
  end

  function app.event(w, ev, a, b, c3, d4)
    if ev == "net_pong" then
      local addr = st.pingIds[b]
      if addr then st.pingIds[b] = nil st.pings[addr] = string.format("%d мс, переходов %d", c3 or 0, d4 or 0) wm.redraw(w) end
      return
    end
    if ev ~= "click" and ev ~= "dblclick" then return end
    if a == "tab" then st.tab = b
    elseif a == "rename" then
      wm.prompt("Имя компьютера", "Имя в сети (его видят другие, по нему пишут письма):", net.hostname(), function(v)
        if v then net.hostname(v) wm.redraw(w) end
      end)
    elseif a == "ping" then ping(b)
    elseif a == "write" then
      local chat = dofile("/wired/apps/chat.lua")
      wired.chatState.cur = "@" .. b
      wired.chatState.unread["@" .. b] = nil
      local open = wm.find(function(x) return x.icon == "chat" end)
      if open then wm.focusWin(open) wm.redraw(open) else wm.open(chat()) end
    elseif a == "sw" then st.sw = b
    elseif a == "port" or a == "vlan" then
      local ports = switch.ports(st.sw) or {}
      for _, p in ipairs(ports) do
        if p.port == b then
          local ok, err
          if a == "port" then ok, err = switch.set(st.sw, b, not p.enabled)
          else
            local idx = 1
            for i, v in ipairs(VLANS) do if v == p.vlan then idx = i end end
            local nextV = VLANS[idx % #VLANS + 1]
            ok, err = switch.set(st.sw, b, p.enabled, nextV == "" and "none" or nextV)
          end
          if not ok then wm.notify({ title = "Коммутатор", text = tostring(err) }) end
        end
      end
    elseif a == "scan" then st.scan = net.wifi.scan()
    elseif a == "wifioff" then net.wifi.disconnect()
    elseif a == "connect" then
      local n = st.scan and st.scan[b]
      if n and n.secured then
        wm.prompt("Wi-Fi " .. n.ssid, "Пароль сети:", "", function(v) if v then wifiConnect(n, v) end end)
      elseif n then wifiConnect(n) end
    end
    wm.redraw(w)
  end

  return app
end
