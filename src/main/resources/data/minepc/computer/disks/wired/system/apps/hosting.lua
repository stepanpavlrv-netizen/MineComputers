-- Wired: Хостинг — для владельца сервера: включить хостинг, клиенты и их место, сайты, посещения, почта.
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
      c.fill(1, y, SIDE, 2, bg)
      if act then c.text(1, y, "▌", T.accent, bg) c.text(1, y + 1, "▌", T.accent, bg) end
      c.text(3, y, G.cut(cl.login, SIDE - 10), act and T.fg or T.dim, bg)
      c.text(SIDE - 6, y, tostring(#cl.sites) .. " с.", T.faint, bg)
      local frac = cl.used / math.max(1, cl.quota)
      local barW = SIDE - 16
      c.progress(3, y + 1, barW, math.min(1, frac), frac > 0.9 and T.err or T.accent)
      c.text(4 + barW, y + 1, G.cut(fmt(cl.used) .. "/" .. fmt(cl.quota), SIDE - barW - 4), T.faint, bg)
      c.hit(1, y, SIDE, 2, "client", i)
      y = y + 3
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
      bx = c.button(bx, 4, "Пароль", "passwd", "ghost") + 1
      c.button(bx, 4, "Удалить клиента", "del", "ghost")
      c.text(x0, 5, string.format("Занято %s из %s", fmt(cl.used), fmt(cl.quota)), T.dim)
      c.hline(x0, 6, rw)
      c.text(x0, 7, G.pad("Сайт", 24) .. G.pad("Размер", 10) .. G.pad("Сегодня", 9) .. G.pad("Неделя", 8) .. "Почта", T.faint)
      local yy = 8
      for i, s in ipairs(cl.sites) do
        if yy > c.h - 12 then break end
        local act = i == st.site
        if act then c.fill(x0, yy, rw, 1, T.sel) end
        local bg = act and T.sel or T.win
        c.text(x0, yy, G.cut(s.name, 23), act and T.fg or T.dim, bg)
        c.text(x0 + 24, yy, fmt(s.size), T.dim, bg)
        c.text(x0 + 34, yy, tostring(s.stats.today), s.stats.today > 0 and T.ok or T.dim, bg)
        c.text(x0 + 43, yy, tostring(s.stats.week), T.dim, bg)
        c.text(x0 + 51, yy, #s.boxes > 0 and (#s.boxes .. " ящ., " .. fmt(s.mail)) or "—", T.dim, bg)
        c.hit(x0, yy, rw, 1, "site", i)
        yy = yy + 1
      end
      if #cl.sites == 0 then c.text(x0, 8, "Сайтов нет — клиент загружает их сам (логин и пароль у него)", T.faint) end
      -- выбранный сайт: посещения по дням
      local s = cl.sites[st.site]
      if s then
        local gy = c.h - 10
        c.hline(x0, gy - 1, rw)
        c.text(x0, gy, s.name, T.accent)
        bx = x0 + G.len(s.name) + 2
        bx = c.button(bx, gy, "Открыть", "open", "ghost") + 1
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
