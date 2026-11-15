-- Wired: Пульт — контроллеры ввода-вывода в сети: стороны, уровни сигнала, импульсы, кто рядом.
-- Запросы асинхронные: ответ приходит событием io_state/io_error, окно при этом не замирает.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local SIDES = { "north", "south", "west", "east", "up", "down" }
local RU = { north = "Север", south = "Юг", west = "Запад", east = "Восток", up = "Верх", down = "Низ" }

return function()
  local app = { title = "Пульт", icon = "pult", w = 84, h = 26, minW = 66, minH = 18 }
  local st = { list = {}, sel = nil, state = nil, busy = false, err = nil, searching = false }
  local pending = {}
  local win

  local function req(target, cmd, cb, ...)
    if net.speed() == 0 then st.err = "Нет сети: подключите кабель или Wi-Fi" return end
    local id = net.io(target, cmd, ...)
    if not id then st.err = "Нет сети" return end
    pending[id] = cb
    wm.after(2, function()
      if pending[id] then
        pending[id] = nil
        cb(nil, "«" .. tostring(target) .. "» не отвечает")
      end
    end)
  end

  local function apply(res, err)
    if res then st.state, st.err = res, nil else st.err = err end
    wm.redraw(win)
  end

  local function select(c)
    st.sel, st.state, st.err = c, nil, nil
    req(c.address, "get", apply)
    req(c.address, "watch", function() end)
  end

  local function search()
    st.searching = true
    local found = {}
    if net.speed() == 0 then st.searching = false st.err = "Нет сети: подключите кабель или Wi-Fi" return end
    local left = 0
    for _, p in ipairs(net.peers()) do
      left = left + 1
      local hops = p.hops
      req(p.address, "get", function(res)
        left = left - 1
        if res then found[#found + 1] = { name = res.name or "", address = res.address or p.address, hops = hops } end
        if left == 0 then
          table.sort(found, function(x, y) return x.hops < y.hops end)
          st.list, st.searching = found, false
          if not st.sel and found[1] then select(found[1]) end
          wm.redraw(win)
        end
      end)
    end
    if left == 0 then st.searching = false end
  end

  function app.init(w) win = w search() end

  function app.close()
    if st.sel and net.speed() > 0 then pcall(net.io, st.sel.address, "unwatch") end
  end

  function app.draw(w, c)
    local lw = 24
    c.fill(1, 1, lw, c.h, T.menu)
    c.text(2, 1, "Контроллеры", T.dim, T.menu)
    for i, k in ipairs(st.list) do
      local y = 1 + i
      if y > c.h - 2 then break end
      local act = st.sel and st.sel.address == k.address
      if act then c.fill(1, y, lw, 1, T.sel) end
      c.text(2, y, "⚡", T.warn, act and T.sel or T.menu)
      c.text(4, y, G.cut(k.name ~= "" and k.name or k.address:sub(1, 8), lw - 5), act and T.fg or T.dim, act and T.sel or T.menu)
      c.hit(1, y, lw, 1, "pick", i)
    end
    if #st.list == 0 then
      c.text(2, 3, st.searching and "Ищу…" or "Не найдено", T.faint, T.menu)
    end
    c.text(2, c.h, st.searching and "  Ищу…  " or "↻ Обновить", T.dim, T.menu)
    c.hit(1, c.h, lw, 1, "search")
    local x0 = lw + 3
    if st.err then c.text(x0, c.h, G.cut(st.err, c.w - x0), T.err) end
    local s = st.state
    if not st.sel then
      c.text(x0, 3, "Контроллер ввода-вывода управляет редстоуном по сети:", T.fg)
      c.text(x0, 4, "поставьте его, подключите к сети — и он появится слева.", T.dim)
      return
    end
    if not s then c.text(x0, 3, "Спрашиваю…", T.dim) return end
    c.text(x0, 1, G.cut(s.name ~= "" and s.name or "Контроллер", 30), T.fg)
    c.text(x0 + G.len(G.cut(s.name ~= "" and s.name or "Контроллер", 30)) + 2, 1, s.address, T.faint)
    c.button(c.w - 15, 1, "Переименовать", "rename", "ghost")
    c.text(x0, 3, G.pad("Сторона", 9) .. G.pad("Режим", 9) .. "Уровень", T.dim)
    local barX = x0 + 18
    for i, side in ipairs(SIDES) do
      local v = s.sides and s.sides[side] or { mode = "in", level = 0 }
      local y = 3 + i * 2 - 1
      c.text(x0, y, RU[side], T.fg)
      local out = v.mode == "out"
      c.text(x0 + 9, y, out and "выход" or "вход ", out and 0x6b95ff or 0xff8a3d)
      c.hit(x0 + 9, y, 6, 1, "mode", side)
      for k = 1, 15 do
        local on = k <= v.level
        c.text(barX + k - 1, y, on and "█" or "░", on and T.accent or T.faint)
      end
      if out then c.hit(barX, y, 15, 1, "level", side) end
      c.text(barX + 16, y, string.format("%2d", v.level), v.level > 0 and T.fg or T.dim)
      local bx = barX + 20
      if out then
        bx = c.button(bx, y, "Вкл", "set", v.level == 15 and "primary" or "normal", { side, 15 }) + 1
        bx = c.button(bx, y, "Выкл", "set", "normal", { side, 0 }) + 1
        c.button(bx, y, "Импульс", "pulse", "normal", side)
      else
        local info = (v.fill and ("заполнен " .. v.fill .. "/15  ") or "") .. (v.block and v.block:gsub("^minecraft:", "") or "")
        c.text(bx, y, G.cut(info, math.max(1, c.w - bx)), T.dim)
      end
    end
    local players = {}
    for _, p in pairs(s.players or {}) do players[#players + 1] = p end
    local y = 3 + #SIDES * 2 + 1
    c.text(x0, y, "Рядом: " .. (#players > 0 and table.concat(players, ", ") or "никого"), T.fg)
    c.text(x0, y + 1, "Свет " .. tostring(s.light or "?") .. " · " .. tostring(s.time or ""), T.dim)
    c.text(x0, c.h - 1, "Щёлкните «вход/выход» — сменить режим, по полоске — задать уровень", T.faint)
  end

  function app.event(w, ev, a, b, c3, d4)
    if ev == "io_state" or ev == "io_error" then
      local cb = pending[b]
      if cb then
        pending[b] = nil
        if ev == "io_state" then cb(c3) else cb(nil, c3) end
      end
      return
    elseif ev == "io_change" then
      if st.sel and st.state and st.state.sides and (a == st.sel.address or st.sel.address:sub(1, #a) == a or a:sub(1, #st.sel.address) == st.sel.address) then
        local v = st.state.sides[b]
        if v then v.level = c3 end
        wm.redraw(w)
      end
      return
    elseif ev == "io_player" then
      if st.sel then req(st.sel.address, "get", apply) end
      return
    end
    if ev ~= "click" and ev ~= "dblclick" then return end
    local addr = st.sel and st.sel.address
    if a == "pick" then select(st.list[b])
    elseif a == "search" and not st.searching then search()
    elseif a == "set" and addr then req(addr, "set", apply, b[1], b[2])
    elseif a == "pulse" and addr then req(addr, "pulse", apply, b, 15, 10)
    elseif a == "mode" and addr then
      local v = st.state.sides[b]
      req(addr, "mode", apply, b, v.mode == "out" and "in" or "out")
    elseif a == "level" and addr then
      -- уровень по месту щелчка (lx)
      local lx = c3
      local barX = 24 + 3 + 18
      local level = lx and math.max(0, math.min(15, lx - barX + 1)) or 15
      req(addr, "set", apply, b, level)
    elseif a == "rename" and addr then
      wm.prompt("Имя контроллера", "Новое имя:", st.state and st.state.name or "", function(v)
        if v then
          req(addr, "name", function(res, err)
            apply(res, err)
            for _, k in ipairs(st.list) do if k.address == addr and res then k.name = res.name end end
          end, v)
        end
      end)
    end
    wm.redraw(w)
  end

  return app
end
