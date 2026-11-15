-- Wired: Чат — каналы (как программа chat: порт 4000 + номер) и личные сообщения (порт 3999).
-- Сообщения принимает служба, поэтому пока окно закрыто, ничего не теряется: придёт уведомление.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local PM = 3999
local PALETTE = { 0x4fd1e8, 0xff8a3d, 0x5fd38a, 0xff7fbf, 0xffc857, 0x6b95ff, 0xc77dff }

local S = wired.chatState
if not S then
  S = { joined = { 1 }, cur = "1", log = {}, unread = {}, names = {}, wins = {} }
  wired.chatState = S
end

local function me()
  local n = net.hostname()
  return n ~= "" and n or net.address():sub(1, 8)
end

local function colorFor(name)
  local s = 0
  for i = 1, #name do s = s + name:byte(i) end
  return PALETTE[s % #PALETTE + 1]
end

local function add(key, from, text, mine)
  S.log[key] = S.log[key] or {}
  local l = S.log[key]
  l[#l + 1] = { from = tostring(from), text = tostring(text), t = os.date("%H:%M"), mine = mine }
  while #l > 200 do table.remove(l, 1) end
end

local function openPorts()
  if net.speed() == 0 then return end
  pcall(net.open, PM)
  for _, ch in ipairs(S.joined) do pcall(net.open, 4000 + ch) end
end

local function title(key)
  if key:sub(1, 1) == "@" then return "✉ " .. (S.names[key] or key:sub(2, 9)) end
  return "# канал " .. key
end

local function visibleIn(key)
  for win in pairs(S.wins) do
    if not win.closed and not win.min and wm.focus == win and S.cur == key then return true end
  end
  return false
end

local function redrawAll()
  for win in pairs(S.wins) do if win.closed then S.wins[win] = nil else wm.redraw(win) end end
end

--- Приём сообщений (служба Wired).
local function service()
  local opened = false
  while true do
    if not opened and net.speed() > 0 then openPorts() opened = true end
    local t = os.startTimer(5)
    local ev
    repeat ev = { os.pullEvent() } until ev[1] == "net_message" or (ev[1] == "timer" and ev[2] == t)
    if ev[1] == "net_message" then
      local port, addr = ev[3], ev[2]
      local key
      if port == PM then
        key = "@" .. addr
        S.names[key] = tostring(ev[5] or addr:sub(1, 8))
      elseif port > 4000 and port < 4100 then
        for _, ch in ipairs(S.joined) do if 4000 + ch == port then key = tostring(ch) end end
      end
      if key and type(ev[6]) == "string" then
        local from = tostring(ev[5] or addr:sub(1, 8))
        add(key, from, ev[6])
        if not visibleIn(key) then
          S.unread[key] = (S.unread[key] or 0) + 1
          wm.notify({ title = "◌ " .. from .. (key:sub(1, 1) == "@" and " (лично)" or (" · #" .. key)), text = ev[6],
            onClick = function() S.cur = key wired.apps.launch(wired.apps.get("chat")) end })
        end
        redrawAll()
      end
    end
  end
end

local function app()
  local a = { title = "Чат", icon = "chat", w = 84, h = 28, minW = 50, minH = 14 }
  local input = U.field("", "input")
  input.focused = true
  local scroll = 0       -- строк от низа
  local win
  openPorts()

  local function send()
    local text = input.text
    if text == "" then return end
    if net.speed() == 0 then wm.notify({ title = "Чат", text = "Нет сети: подключите кабель или Wi-Fi" }) return end
    local ok
    if S.cur:sub(1, 1) == "@" then ok = net.send(S.cur:sub(2), PM, me(), text)
    else ok = net.broadcast(4000 + tonumber(S.cur), me(), text) end
    if ok ~= false then add(S.cur, me(), text, true) else add(S.cur, "чат", "не отправлено") end
    input.text, input.pos, scroll = "", 0, 0
  end

  function a.init(w)
    win = w
    S.wins[w] = true
    S.unread[S.cur] = nil
  end

  function a.close(w) S.wins[w] = nil end

  function a.draw(w, c)
    local sw = 20
    c.fill(1, 1, sw, c.h, T.menu)
    c.text(2, 1, "Каналы", T.dim, T.menu)
    local y = 2
    for _, ch in ipairs(S.joined) do
      local key = tostring(ch)
      local act = key == S.cur
      if act then c.fill(1, y, sw, 1, T.sel) end
      c.text(2, y, "# канал " .. key, act and T.fg or T.dim, act and T.sel or T.menu)
      if S.unread[key] then c.text(sw - 3, y, tostring(math.min(99, S.unread[key])), T.ink, T.accent) end
      c.hit(1, y, sw, 1, "chan", key)
      y = y + 1
    end
    c.text(2, y, "+ канал", T.faint, T.menu)
    c.hit(1, y, sw, 1, "join")
    y = y + 2
    c.text(2, y, "В сети", T.dim, T.menu)
    y = y + 1
    local peers = net.speed() > 0 and net.peers() or {}
    for _, p in ipairs(peers) do
      if y > c.h then break end
      local key = "@" .. p.address
      local name = p.name ~= "" and p.name or p.address:sub(1, 8)
      S.names[key] = name
      local act = key == S.cur
      if act then c.fill(1, y, sw, 1, T.sel) end
      c.text(2, y, "●", T.ok, act and T.sel or T.menu)
      c.text(4, y, G.cut(name, sw - 8), act and T.fg or T.dim, act and T.sel or T.menu)
      if S.unread[key] then c.text(sw - 3, y, tostring(math.min(99, S.unread[key])), T.ink, T.accent) end
      c.hit(1, y, sw, 1, "chan", key)
      y = y + 1
    end
    -- личные переписки с теми, кого сейчас нет в сети
    for key in pairs(S.log) do
      if key:sub(1, 1) == "@" and y <= c.h then
        local online = false
        for _, p in ipairs(peers) do if "@" .. p.address == key then online = true end end
        if not online then
          local act = key == S.cur
          if act then c.fill(1, y, sw, 1, T.sel) end
          c.text(2, y, "○", T.faint, act and T.sel or T.menu)
          c.text(4, y, G.cut(S.names[key] or key:sub(2, 9), sw - 8), T.faint, act and T.sel or T.menu)
          c.hit(1, y, sw, 1, "chan", key)
          y = y + 1
        end
      end
    end
    if #peers == 0 then c.text(2, y, net.speed() == 0 and "нет сети" or "никого", T.faint, T.menu) end
    -- переписка
    local x0, mw = sw + 2, c.w - sw - 2
    c.text(x0, 1, title(S.cur), T.fg)
    if S.cur:sub(1, 1) ~= "@" then
      c.text(x0 + G.len(title(S.cur)) + 2, 1, "порт " .. (4000 + tonumber(S.cur)), T.faint)
    end
    c.hline(x0, 2, mw)
    local rows = {}
    for _, m in ipairs(S.log[S.cur] or {}) do
      local lines = G.wrap(m.text, math.max(8, mw - 2))
      rows[#rows + 1] = { m.t .. " ", T.faint, m.from, m.mine and T.accent or colorFor(m.from), lines[1] }
      for i = 2, #lines do rows[#rows + 1] = { "", nil, nil, nil, lines[i], indent = true } end
    end
    local h = c.h - 4
    local maxScroll = math.max(0, #rows - h)
    if scroll > maxScroll then scroll = maxScroll end
    local first = math.max(1, #rows - h + 1 - scroll)
    for i = first, math.min(#rows, first + h - 1) do
      local r = rows[i]
      local yy = 3 + i - first
      if r.indent then
        c.text(x0 + 2, yy, r[5], T.fg)
      else
        c.text(x0, yy, r[1], r[2])
        c.text(x0 + 6, yy, r[3], r[4])
        local rest = G.cut(r[5], math.max(1, mw - 8 - G.len(r[3])))
        c.text(x0 + 7 + G.len(r[3]), yy, rest, T.fg)
      end
    end
    if #rows == 0 then
      c.text(x0, 4, S.cur:sub(1, 1) == "@" and "Напишите — сообщение увидит только этот компьютер"
        or "Здесь пока тихо. Сообщения увидят все в сети на этом канале", T.faint)
    end
    c.scrollbar(c.w, 3, h, first - 1, #rows, h)
    c.field(x0, c.h, mw - 13, input, "Сообщение… (Enter — отправить)")
    c.button(c.w - 11, c.h, "Отправить", "send", input.text ~= "" and "primary" or "off")
  end

  function a.event(w, ev, x, y)
    if ev == "click" or ev == "dblclick" then
      if x == "chan" then S.cur = y S.unread[y] = nil scroll = 0
      elseif x == "send" then send()
      elseif x == "input" then input.focused = true
      elseif x == "join" then
        wm.prompt("Канал", "Номер канала (1–99):", "", function(v)
          local n = tonumber(v or "")
          if n and n >= 1 and n <= 99 then
            n = math.floor(n)
            local have = false
            for _, ch in ipairs(S.joined) do if ch == n then have = true end end
            if not have then S.joined[#S.joined + 1] = n table.sort(S.joined) end
            pcall(net.open, 4000 + n)
            S.cur = tostring(n)
            wm.redraw(w)
          end
        end)
      end
      wm.redraw(w)
    elseif ev == "scroll" then
      scroll = math.max(0, scroll - x * 2)
      wm.redraw(w)
    elseif ev == "key" or ev == "char" or ev == "paste" then
      if ev == "key" and x == "pageUp" then scroll = scroll + 5
      elseif ev == "key" and x == "pageDown" then scroll = math.max(0, scroll - 5)
      elseif U.edit(input, ev, x) == "enter" then send() end
      wm.redraw(w)
    elseif ev == "focus" then
      S.unread[S.cur] = nil
      wm.redraw(w)
    end
  end

  return a
end

return setmetatable({ service = service }, { __call = function(_, arg) return app(arg) end })
