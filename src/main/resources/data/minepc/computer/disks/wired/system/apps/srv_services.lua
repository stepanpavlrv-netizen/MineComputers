-- SWired: «Службы» — что работает в фоне: запустить, остановить, автозапуск, перезапуск при падении, письмо админу.
local G, T, wm, K = wired.gfx, wired.theme, wired.wm, wired.srvkit

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Службы — " .. name, icon = "svc", w = 104, h = 30, minW = 84, minH = 20 }
  local st = { sel = 1, msg = nil }
  local win
  local LEFT = 50

  local function reload()
    K.run(win, function()
      local list, e = B.services()
      if not list then return nil, e end
      local sv = list[st.sel]
      local log = sv and B.serviceLog(sv.id, 12) or {}
      return { list = list, log = log }
    end, function(v, e)
      if v then st.data, st.err = v, nil else st.err = e end
    end)
  end

  function app.init(w)
    win = w
    local function tick()
      if w.closed then return end
      if not w.min then reload() end
      wm.after(3, tick)
    end
    tick()
  end

  local function act(fn, ok)
    st.msg = "…"
    wm.redraw(win)
    K.run(win, fn, function(v, e)
      st.msg = v and ok or ("Не вышло: " .. tostring(e))
      reload()
    end)
  end

  local function stateText(sv)
    if sv.failed then return "упала, не поднимается", T.err end
    if sv.running then
      if sv.crashes > 0 then return "работает · падала " .. sv.crashes, T.warn end
      return "работает", T.ok
    end
    return "остановлена", T.dim
  end

  function app.draw(w, c)
    if K.waiting(c, st) then return end
    local list = st.data.list
    st.sel = math.max(1, math.min(st.sel, #list))
    -- список
    c.fill(1, 1, LEFT, c.h, T.win)
    c.text(4, 1, "Служба", T.faint)
    c.text(30, 1, "Состояние", T.faint)
    c.text(LEFT - 9, 1, "Сама", T.faint)
    c.hline(2, 2, LEFT - 2)
    for i, sv in ipairs(list) do
      local y = 2 + i
      if y > c.h - 3 then break end
      local sel = i == st.sel
      local bg = sel and T.sel or T.win
      c.fill(2, y, LEFT - 2, 1, bg)
      local txt, col = stateText(sv)
      c.text(3, y, sv.running and "●" or "○", sv.failed and T.err or (sv.running and T.ok or T.faint), bg)
      c.text(5, y, G.cut(sv.name, 24), T.fg, bg)
      c.text(30, y, G.cut(txt, LEFT - 41), col, bg)
      c.text(LEFT - 8, y, sv.autostart and "☑" or "☐", sv.autostart and T.accent or T.dim, bg)
      c.hit(2, y, LEFT - 12, 1, "pick", i)
      c.hit(LEFT - 9, y, 3, 1, "auto", i)
    end
    c.hline(2, c.h - 2, LEFT - 2)
    local bx = c.button(2, c.h - 1, "+ Своя служба…", "add")
    c.text(bx + 1, c.h - 1, G.cut("любая программа .lua в фоне", LEFT - bx - 2), T.faint)
    for y = 1, c.h do c.text(LEFT + 1, y, "│", T.line) end
    -- подробности
    local sv = list[st.sel]
    if not sv then return end
    local x, cw = LEFT + 3, c.w - LEFT - 3
    c.text(x, 2, G.cut(sv.name, cw), T.fg)
    c.text(x, 3, G.cut(sv.desc or "", cw), T.dim)
    local txt, col = stateText(sv)
    c.text(x, 4, G.cut(txt .. (sv.running and sv.since and (" · с " .. require("swired").fmtTime(sv.since)) or ""), cw), col)
    bx = x
    if sv.running then bx = c.button(bx, 6, "■ Остановить", "stop") + 1 else bx = c.button(bx, 6, "▶ Запустить", "start", "primary") + 1 end
    bx = c.button(bx, 6, "↻ Перезапустить", "restart") + 1
    if sv.id == "hostd" and not B.remote then bx = c.button(bx, 6, "«Хостинг»", "hosting") + 1 end
    if sv.custom then c.button(bx, 6, "Убрать", "remove") end
    K.card(c, x, 8, cw, 4, nil)
    c.check(x + 1, 8, G.cut("Запускать при включении сервера", cw - 4), sv.autostart, "autoSel")
    c.check(x + 1, 9, G.cut("Перезапускать, если упала (до 5 раз)", cw - 4), sv.restart, "restart_flag")
    c.check(x + 1, 10, G.cut("Письмо, если не поднимается" .. (sv.mail ~= "" and (": " .. sv.mail) or "…"), cw - 4), sv.mail ~= "", "mail")
    c.text(x, 13, "Последние строки службы", T.faint)
    local lh = c.h - 16
    c.fill(x, 14, cw, lh, T.field)
    local log = st.data.log or {}
    if #log == 0 then c.text(x + 1, 14, G.cut("служба ничего не пишет в /var/log/" .. sv.id .. ".log", cw - 2), T.faint, T.field) end
    for i = math.max(1, #log - lh + 1), #log do
      c.text(x + 1, 14 + i - math.max(1, #log - lh + 1), G.cut(log[i], cw - 2), T.label, T.field)
    end
    if st.msg then c.text(x, c.h - 1, G.cut(st.msg, cw), st.msg:find("^Не") and T.err or T.dim) end
    if st.err then c.text(x, c.h, G.cut("Связь: " .. st.err, cw), T.err) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    local list = st.data and st.data.list or {}
    local sv = list[st.sel]
    if a == "pick" then st.sel = b reload()
    elseif a == "auto" or a == "autoSel" then
      local s2 = a == "auto" and list[b] or sv
      if s2 then act(function() return B.set(s2.id, "autostart", not s2.autostart) end,
        s2.autostart and "«" .. s2.name .. "» не будет запускаться сама" or "«" .. s2.name .. "» будет запускаться при включении") end
    elseif not sv then return
    elseif a == "start" then act(function() return B.start(sv.id) end, "«" .. sv.name .. "» запущена")
    elseif a == "stop" then act(function() return B.stop(sv.id) end, "«" .. sv.name .. "» остановлена")
    elseif a == "restart" then act(function() return B.restart(sv.id) end, "«" .. sv.name .. "» перезапущена")
    elseif a == "restart_flag" then act(function() return B.set(sv.id, "restart", not sv.restart) end, "Сохранено")
    elseif a == "mail" then
      wm.prompt("Письмо при падении", "Кому писать (имя@компьютер), пусто — не писать:", sv.mail, function(v)
        if v ~= nil then act(function() return B.set(sv.id, "mail", v) end, v ~= "" and ("Письмо — на " .. v) or "Писем не будет") end
      end)
    elseif a == "hosting" then wired.apps.launch(wired.apps.get("hosting"))
    elseif a == "remove" then
      wm.confirm("Убрать службу", "Убрать «" .. sv.name .. "» из служб? Сама программа останется.", function(ok)
        if ok then act(function() return B.removeCustom(sv.id) end, "Убрано") end
      end, "Убрать")
    elseif a == "add" then
      wm.prompt("Своя служба", "Название службы:", "", function(title)
        if not title or title == "" then return end
        wm.prompt("Своя служба", "Программа на сервере (путь к .lua):", "/srv/", function(path)
          if not path or path == "" then return end
          act(function()
            local id, e = B.addCustom(title, path)
            if not id then return nil, e end
            B.set(id, "autostart", true)
            return B.start(id)
          end, "«" .. title .. "» добавлена и запущена")
        end)
      end)
    end
    wm.redraw(w)
  end

  return app
end
