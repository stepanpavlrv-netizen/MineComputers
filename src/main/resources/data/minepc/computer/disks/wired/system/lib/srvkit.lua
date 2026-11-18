-- Wired: общее для окон SWired (и «Серверов» в обычном Wired): чей сервер, загрузка данных в фоне, карточки.
local G, T, wm = wired.gfx, wired.theme, wired.wm
local K = {}

--- Сервер окна: arg.backend (по сети, из «Серверов») или этот компьютер.
function K.backend(arg)
  local S = require("swired")
  if type(arg) == "table" and arg.backend then return arg.backend, arg.name end
  local name = (net and net.hostname and net.hostname()) or ""
  if name == "" then name = (os.getComputerLabel and os.getComputerLabel()) or "" end
  if name == "" and net and net.address then name = net.address():sub(1, 8) end
  return S.here(), name
end

--- Выполнить fn в фоне (по сети ответ идёт секунды) и перерисовать окно. done(значение, ошибка).
function K.run(win, fn, done)
  wm.spawn(function()
    local r = table.pack(pcall(fn))
    if not r[1] then done(nil, tostring(r[2])) else done(r[2], r[3]) end
    if win and not win.closed then wm.redraw(win) end
  end)
end

--- Обновлять данные окна раз в sec секунд, пока оно открыто: fn() → значение, ошибка.
function K.poll(win, st, sec, fn, after)
  local busy = false
  local function tick()
    if win.closed then return end
    if not busy and not win.min then
      busy = true
      K.run(win, fn, function(v, e)
        busy = false
        if v ~= nil then st.data, st.err = v, nil else st.err = e or st.err end
        st.loaded = true
        if after then after(v, e) end
      end)
    end
    wm.after(sec, tick)
  end
  tick()
end

function K.color(level)
  if level == "ok" then return T.ok elseif level == "warn" then return T.warn elseif level == "err" then return T.err end
  return T.dim
end

function K.mark(level)
  if level == "ok" then return "✔" elseif level == "warn" then return "▲" elseif level == "err" then return "✕" end
  return "·"
end

--- Полоска заполнения: pct 0..100.
function K.meter(c, x, y, w, pct, color)
  pct = math.max(0, math.min(100, tonumber(pct) or 0))
  local n = math.floor(w * pct / 100 + 0.5)
  c.text(x, y, string.rep("█", n), color or (pct >= 85 and T.err or pct >= 60 and T.warn or T.accent))
  c.text(x + n, y, string.rep("░", w - n), T.faint)
end

--- Карточка: фон, заголовок мелким, рамка не нужна (на тёмном фоне окна она отделяется цветом).
function K.card(c, x, y, w, h, title)
  c.fill(x, y, w, h, T.side)
  if title then c.text(x + 1, y, G.cut(title, w - 2), T.dim, T.side) end
end

function K.uptime(sec)
  sec = math.floor(tonumber(sec) or 0)
  local d, h, m = math.floor(sec / 86400), math.floor(sec % 86400 / 3600), math.floor(sec % 3600 / 60)
  if d > 0 then return d .. " д " .. h .. " ч" end
  if h > 0 then return h .. " ч " .. m .. " мин" end
  return m .. " мин"
end

function K.size(n) return textutils.formatSize(tonumber(n) or 0) end

--- Строка «ожидание / ошибка» поверх пустого окна.
function K.waiting(c, st, y)
  if st.err and not st.data then
    c.text(3, y or 3, G.cut("Не получилось: " .. tostring(st.err), c.w - 4), T.err)
    return true
  elseif not st.data then
    c.text(3, y or 3, "Загружаю…", T.dim)
    return true
  end
  return false
end

--- Запросить пароль администратора (изменить пользователей, прочитать журнал входов на этом сервере). fn(пароль) или ничего при отмене.
function K.sudo(B, fn)
  if B.remote then return fn(nil) end
  if not auth or auth.open() then return fn(nil) end
  wm.prompt("Пароль администратора", "Нужны права администратора — ваш пароль (" .. tostring(auth.user() or "?") .. "):", "",
    function(v) if v and v ~= "" then fn(v) end end, "•")
end

return K
