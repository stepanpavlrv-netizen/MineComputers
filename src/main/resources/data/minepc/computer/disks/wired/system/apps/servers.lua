-- Wired: «Серверы» — серверы с SWired в сети: что с ними, и те же окна, что на экране самого сервера (по сети).
local G, T, wm, K = wired.gfx, wired.theme, wired.wm, wired.srvkit

local SECTIONS = {
  { "srv_server", "Сводка", "srv" }, { "srv_services", "Службы", "svc" }, { "srv_health", "Состояние", "health" },
  { "srv_journal", "Журнал", "journal" }, { "srv_users", "Пользователи", "users" }, { "srv_backup", "Копии", "backup" },
}

return function()
  local S = require("swired")
  local app = { title = "Серверы", icon = "servers", w = 100, h = 30, minW = 80, minH = 20 }
  local st = { list = {}, sel = 1, sessions = {}, scanning = false, msg = nil }
  local win
  local LEFT = 30

  local function saved()
    local s = wired.settings.servers
    return type(s) == "table" and s or {}
  end

  local function scan()
    if st.scanning then return end
    st.scanning = true
    K.run(win, function() return S.discover(1.5, true) end, function(found)
      st.scanning = false
      local byAddr = {}
      local out = {}
      for _, v in ipairs(found or {}) do byAddr[v.address] = true out[#out + 1] = v end
      for _, a in ipairs(saved()) do
        if not byAddr[a] then out[#out + 1] = { address = a, name = a, offline = true } end
      end
      -- старый выбор сохраняем по адресу
      local cur = st.list[st.sel] and st.list[st.sel].address
      st.list = out
      for i, v in ipairs(out) do if v.address == cur then st.sel = i end end
    end)
  end

  function app.init(w)
    win = w
    local function tick()
      if w.closed then return end
      if not w.min then scan() end
      wm.after(15, tick)
    end
    tick()
  end

  local function session(sv) return sv and st.sessions[sv.address] end

  local function refresh(sv)
    local se = session(sv)
    if not se then return end
    K.run(win, function() return se.backend.status() end, function(v, e)
      if v then se.status, se.err = v, nil else se.err = e end
    end)
  end

  local function login(sv)
    wm.prompt("Вход на " .. tostring(sv.name), "Администратор этого сервера:", "admin", function(u)
      if not u or u == "" then return end
      wm.prompt("Вход на " .. tostring(sv.name), "Пароль " .. u .. ":", "", function(p)
        if not p then return end
        local R = S.remote(sv.address, u, p, { quiet = true })
        st.msg = "Вхожу…"
        wm.redraw(win)
        K.run(win, function() return R.login() end, function(ok, e)
          if ok then
            st.sessions[sv.address] = { backend = R, user = u }
            st.msg = nil
            refresh(sv)
          else
            st.msg = "Не пустили: " .. tostring(e)
          end
        end)
      end, "•")
    end)
  end

  function app.draw(w, c)
    c.fill(1, 1, LEFT, c.h, T.side)
    c.text(2, 1, st.scanning and "Ищу серверы…" or "Серверы в сети", T.dim, T.side)
    st.sel = math.max(1, math.min(st.sel, #st.list))
    for i, sv in ipairs(st.list) do
      local y = 2 + (i - 1) * 3
      if y > c.h - 3 then break end
      local sel = i == st.sel
      local bg = sel and T.sel or T.side
      c.fill(1, y, LEFT, 3, bg)
      local col = sv.offline and T.faint or K.color(sv.level)
      c.text(2, y, "●", col, bg)
      c.text(4, y, G.cut(tostring(sv.name), LEFT - 5), T.fg, bg)
      c.text(4, y + 1, G.cut(tostring(sv.address):sub(1, 8) .. (sv.version and (" · SWired " .. sv.version) or ""), LEFT - 5), T.dim, bg)
      local note = sv.offline and "не отвечает" or ((sv.count or 0) > 0 and (K.mark(sv.level) .. " замечаний: " .. sv.count) or "всё в порядке")
      c.text(4, y + 2, G.cut(note, LEFT - 5), sv.offline and T.faint or col, bg)
      c.hit(1, y, LEFT, 3, "pick", i)
    end
    if #st.list == 0 and not st.scanning then
      c.text(2, 3, G.cut("Серверов с SWired не видно.", LEFT - 3), T.fg, T.side)
      c.text(2, 4, G.cut("Проверьте кабель и надзиратель", LEFT - 3), T.dim, T.side)
      c.text(2, 5, G.cut("(swiredd) на сервере.", LEFT - 3), T.dim, T.side)
    end
    local bx = c.button(2, c.h, "↻", "scan")
    c.button(bx + 1, c.h, "+ По адресу", "addaddr")
    local sv = st.list[st.sel]
    local x, cw = LEFT + 3, c.w - LEFT - 3
    if not sv then
      c.text(x, 2, "Выберите сервер слева.", T.dim)
      return
    end
    local se = session(sv)
    c.text(x, 2, G.cut(tostring(sv.name), cw - 18), T.fg)
    c.text(x, 3, G.cut(tostring(sv.address), cw), T.faint)
    if sv.offline then
      c.text(x, 5, "Сервер не отвечает: выключен, нет сети или не запущен надзиратель SWired.", T.warn)
      c.text(x, 6, G.cut("Выключенный сервер можно включить через его BMC (Терминал: bmc " .. tostring(sv.name) .. " power on).", cw), T.dim)
      return
    end
    if not se then
      c.text(x, 5, G.cut("Работает " .. K.uptime(sv.uptime) .. " · " .. ((sv.count or 0) > 0 and ("замечаний: " .. sv.count) or "всё в порядке"), cw), K.color(sv.level))
      local y = 6
      for _, p in ipairs(sv.problems or {}) do
        c.text(x, y, K.mark(p.level), K.color(p.level))
        c.text(x + 2, y, G.cut(p.text, cw - 2), T.fg)
        y = y + 1
      end
      c.button(x, y + 1, "Войти как администратор", "login", "primary")
      c.text(x, y + 3, G.cut("Нужен пользователь-администратор того сервера. Пароль никуда не сохраняется.", cw), T.faint)
      if st.msg then c.text(x, y + 5, G.cut(st.msg, cw), st.msg:find("^Не") and T.err or T.dim) end
      return
    end
    c.fill(x, 5, cw, 1, T.mix(T.win, T.ok, 0.15))
    c.text(x + 1, 5, G.cut("● Подключено как " .. se.user, cw - 16), T.ok, T.mix(T.win, T.ok, 0.15))
    c.button(c.w - 15, 5, "Отключиться", "logout")
    local s = se.status
    local y = 7
    if s then
      c.text(x, y, G.cut((s.level == "ok" and "Всё в порядке" or s.level == "warn" and "Есть замечания" or "Нужно вмешаться") ..
        " · работает " .. K.uptime(s.uptime), cw), K.color(s.level))
      y = y + 1
      for i, p in ipairs(s.problems or {}) do
        if i > 4 then break end
        c.text(x, y, K.mark(p.level), K.color(p.level))
        c.text(x + 2, y, G.cut(p.text, cw - 2), T.fg)
        y = y + 1
      end
    elseif se.err then
      c.text(x, y, G.cut("Связь: " .. se.err, cw), T.err)
      y = y + 1
    else
      c.text(x, y, "Загружаю…", T.dim)
      y = y + 1
    end
    y = y + 1
    c.text(x, y, "Открыть на этом компьютере", T.faint)
    y = y + 1
    local col = 0
    local bw = math.floor((cw - 2) / 3)
    for i, sec in ipairs(SECTIONS) do
      local bxx = x + col * (bw + 1)
      c.fill(bxx, y, bw, 3, T.side)
      local mini = wired.icons.MINI[sec[3]] or wired.icons.MINI.app
      c.text(bxx + 1, y + 1, mini[1], mini[2], T.side)
      c.text(bxx + 3, y + 1, G.cut(sec[2], bw - 4), T.fg, T.side)
      c.hit(bxx, y, bw, 3, "section", i)
      col = col + 1
      if col == 3 then col = 0 y = y + 4 end
    end
    y = y + (col > 0 and 4 or 0)
    local b2 = c.button(x, y, "> Терминал (connect)", "term")
    c.button(b2 + 1, y, "Экран через BMC", "bmc")
    c.text(x, c.h, G.cut("Окна открываются отдельно и работают с этим сервером по сети.", cw), T.faint)
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    local sv = st.list[st.sel]
    if a == "pick" then st.sel = b st.msg = nil refresh(st.list[b])
    elseif a == "scan" then scan()
    elseif a == "addaddr" then
      wm.prompt("Добавить сервер", "Имя или адрес сервера:", "", function(v)
        if not v or v == "" then return end
        local list = saved()
        list[#list + 1] = v
        wired.settings.servers = list
        wired.saveSettings()
        scan()
      end)
    elseif not sv then return
    elseif a == "login" then login(sv)
    elseif a == "logout" then st.sessions[sv.address] = nil
    elseif a == "section" then
      local se = session(sv)
      if se then wired.apps.launch(wired.apps.get(SECTIONS[b][1]), { backend = se.backend, name = tostring(sv.name) }) end
    elseif a == "term" then
      local se = session(sv)
      wm.open(wired.vterm.app("/rom/programs/connect.lua", { tostring(sv.address), se and se.user or nil },
        { title = "Терминал — " .. tostring(sv.name) }))
    elseif a == "bmc" then
      wm.open(wired.vterm.app("/rom/programs/bmc.lua", { tostring(sv.name), "console" }, { title = "BMC — " .. tostring(sv.name) }))
    end
    wm.redraw(w)
  end

  return app
end
