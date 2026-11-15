-- Wired: Часы — время крупно, будильники, секундомер, таймер. Будильники и таймер звонят и при закрытом окне.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local CFG = "/wired/alarms.cfg"

-- Цифры 3×5 из блоков
local DIGITS = {
  ["0"] = { "███", "█ █", "█ █", "█ █", "███" }, ["1"] = { " █ ", "██ ", " █ ", " █ ", "███" },
  ["2"] = { "███", "  █", "███", "█  ", "███" }, ["3"] = { "███", "  █", "███", "  █", "███" },
  ["4"] = { "█ █", "█ █", "███", "  █", "  █" }, ["5"] = { "███", "█  ", "███", "  █", "███" },
  ["6"] = { "███", "█  ", "███", "█ █", "███" }, ["7"] = { "███", "  █", "  █", "  █", "  █" },
  ["8"] = { "███", "█ █", "███", "█ █", "███" }, ["9"] = { "███", "█ █", "███", "  █", "███" },
  [":"] = { "   ", " █ ", "   ", " █ ", "   " },
}

local function big(c, x, y, s, color)
  for i = 1, #s do
    local d = DIGITS[s:sub(i, i)]
    if d then for r = 1, 5 do c.text(x + (i - 1) * 4, y + r - 1, d[r], color) end end
  end
end

local C = {}

function C.loadAlarms()
  local list = {}
  if fs.exists(CFG) then
    local f = fs.open(CFG, "r")
    local fn = load("return " .. (f.readAll() or ""), "alarms", "t", {})
    f.close()
    local ok, t = pcall(fn or function() end)
    if ok and type(t) == "table" then list = t end
  end
  return list
end

function C.saveAlarms(list)
  local f = fs.open(CFG, "w")
  if f then f.write(textutils.serialize(list)) f.close() end
end

local function ring(title, text)
  wm.notify({ title = "◷ " .. title, text = text, timeout = 30 })
  for i = 0, 2 do pcall(computer.beep, 880 + i * 220, 0.15) end
end

--- Служба: раз в 10 секунд проверить будильники.
function C.service()
  local fired = {}
  while true do
    local now = os.date("%H:%M")
    for _, a in ipairs(C.loadAlarms()) do
      if a.on and a.time == now and fired[a.time .. (a.label or "")] ~= os.date("%d") then
        fired[a.time .. (a.label or "")] = os.date("%d")
        ring("Будильник " .. a.time, a.label ~= "" and a.label or "Пора!")
      end
    end
    if wired.clockTimer and wired.clockTimer.ends and computer.uptime() >= wired.clockTimer.ends then
      wired.clockTimer.ends = nil
      ring("Таймер", "Время вышло")
    end
    local t = os.startTimer(2)
    repeat local _, id = os.pullEvent("timer") until id == t
  end
end

function C.app()
  local app = { title = "Часы", icon = "clock", w = 56, h = 22, minW = 46, minH = 18 }
  local st = { tab = 1, alarms = C.loadAlarms(), sw = 0, swStart = nil, laps = {} }
  wired.clockTimer = wired.clockTimer or { minutes = 5 }
  local win

  local function tick()
    if win.closed then return end
    wm.redraw(win)
    wm.after(st.tab == 3 and st.swStart and 0.2 or 1, tick)
  end

  function app.init(w) win = w tick() end

  local function swTime()
    local t = st.sw + (st.swStart and (computer.uptime() - st.swStart) or 0)
    return t
  end

  function app.draw(w, c)
    local x = 2
    for i, name in ipairs({ "Часы", "Будильник", "Секундомер", "Таймер" }) do
      local act = i == st.tab
      c.text(x, 1, " " .. name .. " ", act and T.fg or T.dim, act and T.sel or T.win)
      if act then c.text(x, 2, string.rep("▔", G.len(name) + 2), T.accent) end
      c.hit(x, 1, G.len(name) + 2, 1, "tab", i)
      x = x + G.len(name) + 3
    end
    if st.tab == 1 then
      local s = os.date("%H:%M")
      big(c, math.floor((c.w - #s * 4) / 2) + 1, 5, s, T.accent)
      local d = os.date("%d.%m.%Y")
      c.text(math.floor((c.w - G.len(d)) / 2) + 1, 12, d, T.fg)
      local gt = os.gameTime and os.gameTime() or nil
      if gt then
        local h = math.floor(gt) % 24
        local m = math.floor((gt - math.floor(gt)) * 60)
        local line = string.format("В игре %02d:%02d · день %d", h, m, os.day and os.day() or 0)
        c.text(math.floor((c.w - G.len(line)) / 2) + 1, 14, line, T.dim)
      end
    elseif st.tab == 2 then
      c.text(2, 4, "Будильники звонят и при закрытом окне", T.dim)
      for i, a in ipairs(st.alarms) do
        local y = 5 + i
        if y > c.h - 3 then break end
        c.check(2, y, a.time .. "  " .. (a.label or ""), a.on, "toggle")
        c.hit(2, y, 30, 1, "toggle", i)
        c.button(c.w - 9, y, "Удалить", "delalarm", "ghost", i)
      end
      if #st.alarms == 0 then c.text(2, 6, "Будильников нет", T.faint) end
      c.button(2, c.h - 1, "+ Будильник", "addalarm", "primary")
    elseif st.tab == 3 then
      local t = swTime()
      local s = string.format("%02d:%02d", math.floor(t / 60), math.floor(t) % 60)
      big(c, math.floor((c.w - #s * 4) / 2) + 1, 4, s, T.fg)
      c.text(math.floor(c.w / 2) + 10, 8, string.format(",%d", math.floor((t * 10) % 10)), T.dim)
      local bx = math.floor((c.w - 34) / 2) + 1
      bx = c.button(bx, 11, st.swStart and "Пауза" or "Старт", "sw", "primary") + 1
      bx = c.button(bx, 11, "Круг", "lap", st.swStart and "normal" or "off") + 1
      c.button(bx, 11, "Сброс", "swreset")
      for i, l in ipairs(st.laps) do
        if 12 + i > c.h then break end
        c.text(math.floor((c.w - 20) / 2), 12 + i, string.format("Круг %d   %02d:%04.1f", #st.laps - i + 1, math.floor(l / 60), l % 60), T.dim)
      end
    else
      local tm = wired.clockTimer
      local left = tm.ends and math.max(0, tm.ends - computer.uptime()) or tm.minutes * 60
      local s = string.format("%02d:%02d", math.floor(left / 60), math.floor(left) % 60)
      big(c, math.floor((c.w - #s * 4) / 2) + 1, 4, s, tm.ends and T.accent or T.fg)
      local bx = math.floor((c.w - 34) / 2) + 1
      if not tm.ends then
        bx = c.button(bx, 11, "− 1 мин", "tmin", "normal", -1) + 1
        bx = c.button(bx, 11, "+ 1 мин", "tmin", "normal", 1) + 1
        c.button(bx, 11, "Старт", "tstart", "primary")
      else
        c.button(bx, 11, "Остановить", "tstop", "primary")
      end
      c.text(2, c.h - 1, "Когда время выйдет — придёт уведомление со звуком", T.faint)
    end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" and ev ~= "dblclick" then return end
    if a == "tab" then st.tab = b
    elseif a == "toggle" and type(b) == "number" then st.alarms[b].on = not st.alarms[b].on C.saveAlarms(st.alarms)
    elseif a == "delalarm" then table.remove(st.alarms, b) C.saveAlarms(st.alarms)
    elseif a == "addalarm" then
      wm.prompt("Будильник", "Время ЧЧ:ММ и подпись (например 07:30 подъём):", "", function(v)
        local h, m, label = tostring(v or ""):match("^%s*(%d%d?)[:%.](%d%d)%s*(.-)%s*$")
        if h and tonumber(h) < 24 and tonumber(m) < 60 then
          st.alarms[#st.alarms + 1] = { time = string.format("%02d:%02d", tonumber(h), tonumber(m)), label = label, on = true }
          C.saveAlarms(st.alarms)
          wm.redraw(w)
        end
      end)
    elseif a == "sw" then
      if st.swStart then st.sw = swTime() st.swStart = nil else st.swStart = computer.uptime() end
    elseif a == "lap" and st.swStart then table.insert(st.laps, 1, swTime())
    elseif a == "swreset" then st.sw, st.swStart, st.laps = 0, nil, {}
    elseif a == "tmin" then wired.clockTimer.minutes = math.max(1, math.min(180, wired.clockTimer.minutes + b))
    elseif a == "tstart" then wired.clockTimer.ends = computer.uptime() + wired.clockTimer.minutes * 60
    elseif a == "tstop" then wired.clockTimer.ends = nil end
    wm.redraw(w)
  end

  return app
end

-- Как программа: return function(arg) → окно; как служба: dofile(...).service
local mod = setmetatable({ service = C.service }, { __call = function(_, arg) return C.app(arg) end })
return mod
