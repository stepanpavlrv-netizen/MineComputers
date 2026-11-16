-- Wired: Хостинг — для владельца сервера: включить хостинг, клиенты и их место, срок работы, сайты, посещения,
-- почта, приостановка сайта.
-- То же, что команды hostd, только мышью. Данные — библиотека hostadm (общая с hostd).
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm

return function()
  local H = require("hostadm")
  local app = { title = "Хостинг", icon = "host", w = 100, h = 32, minW = 76, minH = 22 }
  local st = { clients = {}, sel = 1, site = 1, msg = nil, err = false }
  local win
  local SIDE = 30

  local function reload()
    st.clients = H.clients()
    if st.sel > #st.clients then st.sel = math.max(1, #st.clients) end
    local c = st.clients[st.sel]
    if c and st.site > #c.sites then st.site = math.max(1, #c.sites) end
  end

  local function say(text, bad)
    st.msg, st.err = text, bad
    if win then wm.redraw(win) end
  end

  local function tick()
    if win.closed then return end
    if not win.min then reload() wm.redraw(win) end
    wm.after(10, tick)
  end

  function app.init(w)
    win = w
    reload()
    wm.after(10, tick)
  end

  local function fmt(n) return textutils.formatSize(n) end
  local function short(iso) return H.fmtDate(iso):sub(1, 5) end

  --- Срок клиента одной строкой для списка: текст и цвет.
  local function termNote(cl)
    if not cl.till then return "без срока", T.faint end
    if cl.left < 0 then return "⌛ срок вышел " .. short(cl.till), T.err end
    if cl.left <= 3 then
      return "⌛ " .. (cl.left == 0 and "последний день" or ("через " .. cl.left .. (cl.left == 1 and " день" or " дня"))), T.warn
    end
    return "до " .. short(cl.till) .. " · " .. cl.left .. " дн.", T.dim
  end

  -- ======================= окна: срок работы и приостановка =======================

  local function plusDays(base, n)
    local y, m, d = base:match("^(%d+)-(%d+)-(%d+)$")
    return os.date("%Y-%m-%d", os.time({ year = tonumber(y), month = tonumber(m), day = tonumber(d), hour = 12 }) + n * 86400)
  end

  local function termDialog(cl)
    local choice = 2
    local base = (cl.till and cl.left >= 0) and cl.till or H.today()
    local f = U.field(H.fmtDate(plusDays(base, 30)), "date")
    local dlg
    local function close() wm.close(dlg, true) end
    local function save()
      local spec = choice == 1 and "+7" or choice == 2 and "+30" or choice == 3 and f.text or "-"
      local ok, res = H.setUntil(cl.login, spec)
      if not ok then say(res, true) return end
      close()
      reload()
      say("Срок работы сайтов " .. cl.login .. ": " .. (res and ("до " .. H.fmtDate(res)) or "без срока"))
    end
    dlg = wm.open({ title = "Срок работы — " .. cl.login, icon = "host", w = 50, h = 15, resizable = false,
      draw = function(w, c)
        c.text(3, 2, cl.till and ("Сейчас: " .. (cl.left < 0 and ("срок вышел " .. H.fmtDate(cl.till))
          or ("до " .. H.fmtDate(cl.till) .. " (осталось " .. cl.left .. " дн.)"))) or "Сейчас: без срока", T.dim)
        c.radio(3, 4, "продлить на 7 дней", choice == 1, "c1")
        c.text(27, 4, "→ до " .. H.fmtDate(plusDays(base, 7)), T.faint)
        c.radio(3, 5, "продлить на 30 дней", choice == 2, "c2")
        c.text(27, 5, "→ до " .. H.fmtDate(plusDays(base, 30)), T.faint)
        c.radio(3, 6, "до даты", choice == 3, "c3")
        f.focused = choice == 3
        c.field(14, 6, 14, f)
        c.hit(14, 6, 14, 1, "c3")
        c.radio(3, 7, "без срока", choice == 4, "c4")
        c.text(3, 9, "Срок действует на все сайты клиента. Когда он", T.faint)
        c.text(3, 10, "выйдет, посетители увидят заглушку; файлы и", T.faint)
        c.text(3, 11, "почта останутся.", T.faint)
        local bx = c.w - 24
        bx = c.button(bx, 13, "Отмена", "no") + 1
        c.button(bx, 13, "Сохранить", "ok", "primary")
      end,
      event = function(w, ev, a)
        if ev == "click" then
          if a == "ok" then save() return elseif a == "no" then close() return end
          local n = type(a) == "string" and tonumber(a:match("^c(%d)$"))
          if n then choice = n end
        elseif ev == "key" and a == "escape" then close() return
        elseif ev == "key" and a == "enter" then save() return
        elseif choice == 3 and (ev == "char" or ev == "key" or ev == "paste") then U.edit(f, ev, a) end
        wm.redraw(w)
      end })
  end

  --- Приостановить сайт (site) или всего клиента (site == nil).
  local function pauseDialog(cl, site)
    local target = site or cl.login
    local f = U.field("", "why")
    f.focused = true
    local dlg
    local function close() wm.close(dlg, true) end
    local function go()
      local ok, err = H.pause(target, f.text)
      close()
      if not ok then say(err, true) return end
      reload()
      say(site and ("Сайт " .. site .. " приостановлен: посетители видят заглушку, почта работает")
        or ("Все сайты клиента " .. cl.login .. " приостановлены"))
    end
    dlg = wm.open({ title = "Приостановить " .. (site or ("все сайты " .. cl.login)), icon = "host", w = 52, h = 12, resizable = false,
      draw = function(w, c)
        c.text(3, 2, "Посетители увидят «сайт временно не работает».", T.dim)
        c.text(3, 3, "Почта домена продолжит работать.", T.dim)
        c.text(3, 5, "Причина (необязательно):", T.fg)
        c.field(3, 6, c.w - 4, f)
        c.text(3, 7, "её увидят посетители и клиент в панели", T.faint)
        local bx = c.w - 30
        bx = c.button(bx, 10, "Отмена", "no") + 1
        c.button(bx, 10, "⏸ Приостановить", "ok", "primary")
      end,
      event = function(w, ev, a)
        if ev == "click" then
          if a == "ok" then go() elseif a == "no" then close() end
          return
        elseif ev == "key" and a == "escape" then close() return end
        if ev == "char" or ev == "key" or ev == "paste" then
          if U.edit(f, ev, a) == "enter" then go() return end
          wm.redraw(w)
        end
      end })
  end

  function app.draw(w, c)
    -- верх: служба
    local on = H.running()
    c.text(2, 1, on and "● Хостинг работает" or "○ Хостинг выключен", on and T.ok or T.dim)
    local bx = 22
    bx = c.button(bx, 1, on and "Выключить" or "Включить", on and "stop" or "start", on and "normal" or "primary") + 2
    if on then c.text(bx, 1, G.cut("Панель клиентов: " .. H.url(), c.w - bx - 1), T.faint) end
    local okF, free = pcall(fs.getFreeSpace, "/")
    local nSites = 0
    for _, cl in ipairs(st.clients) do nSites = nSites + #cl.sites end
    c.text(2, 2, string.format("Клиентов %d · сайтов %d%s", #st.clients, nSites,
      okF and free and (" · на диске свободно " .. fmt(free)) or ""), T.dim)

    -- клиенты
    c.fill(1, 4, SIDE, c.h - 3, T.side)
    c.text(2, 4, "КЛИЕНТЫ", T.faint, T.side)
    local y = 5
    for i, cl in ipairs(st.clients) do
      if y > c.h - 3 then break end
      local act = i == st.sel
      local bg = act and T.sel or T.side
      c.fill(1, y, SIDE, 3, bg)
      if act then for k = 0, 2 do c.text(1, y + k, "▌", T.accent, bg) end end
      c.text(3, y, G.cut(cl.login, SIDE - 10), act and T.fg or T.dim, bg)
      c.text(SIDE - 6, y, tostring(#cl.sites) .. " с.", T.faint, bg)
      local frac = cl.used / math.max(1, cl.quota)
      local barW = SIDE - 16
      c.progress(3, y + 1, barW, math.min(1, frac), frac > 0.9 and T.err or T.accent)
      c.text(4 + barW, y + 1, G.cut(fmt(cl.used) .. "/" .. fmt(cl.quota), SIDE - barW - 4), T.faint, bg)
      local note, col = termNote(cl)
      if cl.paused then note, col = "⏸ приостановлен · " .. note, T.warn end
      c.text(3, y + 2, G.cut(note, SIDE - 4), col, bg)
      c.hit(1, y, SIDE, 3, "client", i)
      y = y + 4
    end
    if #st.clients == 0 then c.text(3, 6, "Клиентов пока нет", T.faint, T.side) end
    c.button(2, c.h - 1, "+ Клиент", "add", "primary")

    -- выбранный клиент
    local cl = st.clients[st.sel]
    local x0 = SIDE + 3
    local rw = c.w - x0
    if not cl then
      c.text(x0, 5, "Хостинг держит сайты и почту других игроков на этом сервере.", T.fg)
      c.text(x0, 6, "1. Включите хостинг. 2. Заведите клиента (логин, пароль, место).", T.dim)
      c.text(x0, 7, "3. Клиент загружает сайт: «На хостинг» в Сайтостроителе или deploy.", T.dim)
    else
      c.text(x0, 4, cl.login, T.fg)
      bx = x0 + G.len(cl.login) + 2
      bx = c.button(bx, 4, "Место", "quota", "ghost") + 1
      -- в узком окне — короткие подписи кнопок
      local full = G.len(cl.login) + 2 + 76 <= rw
      bx = c.button(bx, 4, "Пароль", "passwd", "ghost") + 1
      bx = c.button(bx, 4, full and "◷ Срок работы" or "◷ Срок", "term", "ghost") + 1
      bx = c.button(bx, 4, cl.paused and (full and "▶ Включить всё" or "▶ Включить") or (full and "⏸ Приостановить всё" or "⏸ Пауза"),
        cl.paused and "resumeall" or "pauseall", "ghost") + 1
      c.button(bx, 4, full and "Удалить клиента" or "Удалить", "del", "ghost")
      -- срок работы и место
      local used = string.format("Занято %s из %s", fmt(cl.used), fmt(cl.quota))
      local room = rw - G.len(used) - 2
      c.text(x0, 5, "Срок работы: ", T.dim)
      local tx = x0 + 13
      if not cl.till then c.text(tx, 5, "без срока", T.fg)
      elseif cl.left < 0 then
        local s1 = G.cut("закончился " .. H.fmtDate(cl.till) .. " — сайты показывают заглушку", room - 13)
        c.text(tx, 5, s1, T.err)
      else
        local s1 = "до " .. H.fmtDate(cl.till)
        c.text(tx, 5, s1, cl.left <= 3 and T.warn or T.fg)
        c.text(tx + G.len(s1), 5, " · осталось " .. cl.left .. " дн.", T.dim)
      end
      local termLen = 13 + (not cl.till and 9 or cl.left < 0 and room - 13 or G.len("до 00.00.0000 · осталось " .. cl.left .. " дн."))
      if termLen < room then c.text(x0 + rw - G.len(used), 5, used, T.dim) end
      local top = 6
      if cl.paused then
        c.text(x0, 6, G.cut("⏸ Все сайты приостановлены" .. (cl.paused ~= "" and (": «" .. cl.paused .. "»") or ""), rw), T.warn)
        top = 7
      end
      c.hline(x0, top, rw)
      local W1 = math.max(14, math.min(24, rw - 60))
      c.text(x0, top + 1, G.pad("Сайт", W1) .. G.pad("Состояние", 18) .. G.pad("Размер", 10) .. G.pad("Сегодня", 9) .. G.pad("Неделя", 8) .. "Почта", T.faint)
      local yy = top + 2
      for i, s in ipairs(cl.sites) do
        if yy > c.h - 12 then break end
        local act = i == st.site
        if act then c.fill(x0, yy, rw, 1, T.sel) end
        local bg = act and T.sel or T.win
        local ok = s.state == "ok"
        c.text(x0, yy, G.cut(s.name, W1 - 1), act and T.fg or T.dim, bg)
        local sx = x0 + W1
        if ok then c.text(sx, yy, "● работает", T.ok, bg)
        elseif s.state == "paused" then c.text(sx, yy, "⏸ приостановлен", T.warn, bg)
        else c.text(sx, yy, "⌛ срок вышел", T.err, bg) end
        c.text(sx + 18, yy, fmt(s.size), T.dim, bg)
        c.text(sx + 28, yy, tostring(s.stats.today), s.stats.today > 0 and T.ok or T.dim, bg)
        c.text(sx + 37, yy, tostring(s.stats.week), T.dim, bg)
        c.text(sx + 45, yy, G.cut(#s.boxes > 0 and (#s.boxes .. " ящ., " .. fmt(s.mail)) or "—", math.max(1, rw - W1 - 45)), T.dim, bg)
        c.hit(x0, yy, rw, 1, "site", i)
        yy = yy + 1
        -- причина приостановки именно этого сайта — строкой ниже
        if s.sitePaused and s.reason and s.reason ~= "" and yy <= c.h - 12 then
          c.text(sx, yy, G.cut("«" .. s.reason .. "»", rw - W1), T.faint)
          yy = yy + 1
        end
      end
      if #cl.sites == 0 then c.text(x0, top + 2, "Сайтов нет — клиент загружает их сам (логин и пароль у него)", T.faint) end
      -- выбранный сайт: посещения по дням
      local s = cl.sites[st.site]
      if s then
        local gy = c.h - 10
        c.hline(x0, gy - 1, rw)
        c.text(x0, gy, s.name, T.accent)
        bx = x0 + G.len(s.name) + 2
        bx = c.button(bx, gy, "Открыть", "open", "ghost") + 1
        bx = c.button(bx, gy, s.sitePaused and "▶ Включить сайт" or "⏸ Приостановить сайт", s.sitePaused and "resumesite" or "pausesite", "ghost") + 1
        c.button(bx, gy, "Удалить сайт", "rmsite", "ghost")
        local sst = s.stats
        c.text(x0, gy + 1, G.cut(string.format("Гостей сегодня %d, за неделю %d · трафик за неделю %s · всего страниц %d",
          sst.todayUniq, sst.weekUniq, fmt(sst.weekBytes), sst.total), rw), T.dim)
        local maxv = 1
        for _, d in ipairs(sst.series) do if d.views > maxv then maxv = d.views end end
        local colW = math.max(4, math.min(8, math.floor(rw / 7)))
        for i, d in ipairs(sst.series) do
          local hx = x0 + (i - 1) * colW
          local hgt = math.floor(d.views * 10 / maxv + 0.5)    -- в полуклетках: до 5 строк
          for k = 0, 4 do
            local top, bot = (k * 2 + 2) <= hgt, (k * 2 + 1) <= hgt
            local ch = top and "█" or (bot and "▄" or " ")
            c.text(hx, gy + 7 - k, string.rep(ch, colW - 1), i == #sst.series and T.accent or 0x4fd1e8)
          end
          c.text(hx, gy + 8, G.cut(d.date, colW - 1), T.faint)
          if d.views > 0 then c.text(hx, gy + 2, tostring(d.views), T.dim) end
        end
      end
    end
    if st.msg then c.text(SIDE + 3, c.h, G.cut(st.msg, c.w - SIDE - 4), st.err and T.err or T.ok) end
  end

  local function ask(title, text, def, fn) wm.prompt(title, text, def or "", function(v) if v and v ~= "" then fn(v) end end) end

  function app.event(w, ev, a, b)
    if ev ~= "click" and ev ~= "dblclick" then
      if ev == "focus" then reload() wm.redraw(w) end
      return
    end
    local cl = st.clients[st.sel]
    if a == "client" then st.sel, st.site = b, 1
    elseif a == "site" then st.site = b
    elseif a == "start" then
      local pid, err = H.start()
      if pid then say("Хостинг включён и будет включаться сам. Панель клиентов: " .. H.url()) else say("Не запустился: " .. tostring(err), true) end
    elseif a == "stop" then
      wm.confirm("Хостинг", "Выключить хостинг? Сайты клиентов перестанут загружаться (сами файлы останутся).", function(ok)
        if ok then H.stop() say("Хостинг выключен") end
      end, "Выключить")
    elseif a == "add" then
      ask("Новый клиент", "Логин клиента:", "", function(login)
        ask("Новый клиент", "Пароль для «" .. login .. "»:", "", function(pass)
          ask("Новый клиент", "Место для «" .. login .. "», КБ:", "512", function(kb)
            local ok, err = H.add(login, pass, kb)
            if ok then
              reload()
              for i, x in ipairs(st.clients) do if x.login == login then st.sel = i end end
              say("Клиент " .. login .. " заведён. Сообщите ему логин, пароль и имя сервера")
            else say(err, true) end
          end)
        end)
      end)
    elseif a == "quota" and cl then
      ask("Место", "Сколько места клиенту «" .. cl.login .. "», КБ:", tostring(math.floor(cl.quota / 1024)), function(kb)
        local ok, err = H.quota(cl.login, kb)
        if ok then reload() say("Место клиента " .. cl.login .. ": " .. fmt(tonumber(kb) * 1024)) else say(err, true) end
      end)
    elseif a == "passwd" and cl then
      ask("Пароль", "Новый пароль для «" .. cl.login .. "»:", "", function(pass)
        local ok, err = H.passwd(cl.login, pass)
        if ok then say("Пароль клиента " .. cl.login .. " сменён") else say(err, true) end
      end)
    elseif a == "del" and cl then
      wm.confirm("Удалить клиента", "Удалить клиента «" .. cl.login .. "» вместе с его сайтами и почтой (" .. #cl.sites .. " сайт.)?", function(ok)
        if not ok then return end
        H.del(cl.login)
        reload()
        say("Клиент " .. cl.login .. " удалён")
      end, "Удалить")
    elseif a == "term" and cl then termDialog(cl)
    elseif a == "pauseall" and cl then pauseDialog(cl, nil)
    elseif a == "resumeall" and cl then
      H.resume(cl.login)
      say("Сайты клиента " .. cl.login .. " снова работают")
    elseif a == "pausesite" and cl and cl.sites[st.site] then pauseDialog(cl, cl.sites[st.site].name)
    elseif a == "resumesite" and cl and cl.sites[st.site] then
      local name = cl.sites[st.site].name
      H.resume(name)
      reload()
      local s2 = st.clients[st.sel] and st.clients[st.sel].sites[st.site]
      say("Сайт " .. name .. " снова работает" .. ((s2 and s2.state ~= "ok") and " (но приостановлен весь клиент или вышел срок)" or ""))
    elseif a == "open" and cl and cl.sites[st.site] then
      local e = wired.apps.get("browser")
      if e then wired.apps.launch(e, cl.sites[st.site].name) else say("Браузер не установлен (дискета «Браузер»)", true) end
    elseif a == "rmsite" and cl and cl.sites[st.site] then
      local name = cl.sites[st.site].name
      wm.confirm("Удалить сайт", "Удалить сайт " .. name .. " с хостинга (файлы, почту, имя .mine)?", function(ok)
        if not ok then return end
        H.removeSite(name)
        reload()
        say("Сайт " .. name .. " удалён")
      end, "Удалить")
    end
    reload()
    wm.redraw(w)
  end

  return app
end
