-- SWired: «Журнал» — всё, что случилось на сервере: входы, службы, железо (от BMC), сайты, копии.
local G, T, U, wm, K = wired.gfx, wired.theme, wired.ui, wired.wm, wired.srvkit

local CHIPS = {
  { nil, "Все" }, { "logins", "Входы" }, { "services", "Службы" }, { "hardware", "Железо" }, { "sites", "Сайты" },
  { "backups", "Копии" }, { "errors", "Только ошибки" },
}

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Журнал — " .. name, icon = "journal", w = 104, h = 30, minW = 76, minH = 16 }
  local st = { filter = nil, find = U.field("", "find"), scroll = 0 }
  local win

  local function reload()
    K.run(win, function()
      local list, total = B.journal(200, st.filter, st.find.text)
      if not list then return nil, total end
      return { list = list, total = total }
    end, function(v, e) if v then st.data, st.err = v, nil else st.err = e end end)
  end

  function app.init(w)
    win = w
    local function tick()
      if w.closed then return end
      if not w.min then reload() end
      wm.after(5, tick)
    end
    tick()
  end

  function app.draw(w, c)
    local x = 2
    for i, ch in ipairs(CHIPS) do
      local on = st.filter == ch[1]
      x = c.button(x, 1, ch[2], "chip", on and "primary" or "normal", i) + 1
    end
    local fw = math.max(14, c.w - x - 1)
    c.field(c.w - fw, 1, fw, st.find, "⌕ найти…")
    if K.waiting(c, st, 4) then return end
    c.text(2, 3, "Когда", T.faint)
    c.text(15, 3, "Откуда", T.faint)
    c.text(31, 3, "Что случилось", T.faint)
    c.hline(2, 4, c.w - 3)
    local list = st.data.list
    local rows = c.h - 6
    st.scroll = math.max(0, math.min(st.scroll, #list - rows))
    local S = require("swired")
    for i = 1, rows do
      local e = list[i + st.scroll]
      if not e then break end
      local y = 4 + i
      local bg = e.level == "err" and T.mix(T.win, T.err, 0.12) or T.win
      if bg ~= T.win then c.fill(2, y, c.w - 3, 1, bg) end
      c.text(2, y, S.fmtTime(e.time), T.dim, bg)
      c.text(15, y, G.cut(e.src, 14), T.fg, bg)
      c.text(29, y, K.mark(e.level), K.color(e.level), bg)
      c.text(31, y, G.cut(e.text, c.w - 33), T.fg, bg)
    end
    if #list == 0 then c.text(3, 6, "Ничего не нашлось.", T.dim) end
    c.scrollbar(c.w, 5, rows, st.scroll, #list, rows)
    c.hline(2, c.h - 1, c.w - 3)
    c.text(2, c.h, G.cut("Показано " .. math.min(#list, rows) .. " из " .. st.data.total .. " · хранятся последние 400 событий", c.w - 30), T.dim)
    local bx = c.button(c.w - 22, c.h, "Сохранить в файл", "save")
    if not B.remote and auth and not auth.open() then
      c.button(c.w - 48, c.h, "↻ Входы (пароль)", "auth")
    end
    if st.msg then c.text(2, c.h - 1, G.cut(st.msg, c.w - 3), st.msg:find("^Не") and T.err or T.ok) end
    if st.err then c.text(2, c.h - 1, G.cut("Связь: " .. st.err, c.w - 3), T.err) end
  end

  function app.event(w, ev, a, b)
    if ev == "click" then
      if a == "chip" then st.filter = CHIPS[b][1] st.scroll = 0 st.find.focused = false reload()
      elseif a == "find" then st.find.focused = true
      elseif a == "auth" then
        -- журнал входов читает только администратор: пароль — на время чтения
        K.sudo(B, function(pw)
          K.run(w, function() return B.importAuth(pw) end, function(v, e)
            st.msg = v and "Входы перенесены из журнала входов" or ("Не вышло: " .. tostring(e))
            reload()
          end)
        end)
      elseif a == "save" and st.data then
        local S = require("swired")
        local path = "/home/journal-" .. os.date("%Y-%m-%d_%H%M") .. ".txt"
        local lines = {}
        for _, e in ipairs(st.data.list) do lines[#lines + 1] = S.fmtTime(e.time) .. "  " .. e.src .. "  " .. e.text end
        pcall(fs.makeDir, "/home")
        local f = fs.open(path, "w")
        if f then f.write(table.concat(lines, "\n") .. "\n") f.close() end
        wm.notify({ title = "Журнал сохранён", text = path })
      else st.find.focused = false end
    elseif ev == "scroll" then st.scroll = st.scroll + (a or 0) * 3
    elseif st.find.focused and (ev == "char" or ev == "key" or ev == "paste") then
      U.edit(st.find, ev, a)
      st.scroll = 0
      reload()
    end
    wm.redraw(w)
  end

  return app
end
