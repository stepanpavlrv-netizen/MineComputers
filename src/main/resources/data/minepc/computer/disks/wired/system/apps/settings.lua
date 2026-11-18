-- Wired: Параметры — экран и масштаб, оформление, пользователи, программы, о системе.
local G, T, I, U, wm, A = wired.gfx, wired.theme, wired.icons, wired.ui, wired.wm, wired.apps

local SECTIONS = { "Экран", "Оформление", "Пользователи", "Программы", "О системе" }

return function(arg)
  local app = { title = "Параметры", icon = "gear", w = 84, h = 28, minW = 64, minH = 20 }
  local st = { sec = type(arg) == "number" and arg or 1, sel = 1, busy = nil }
  local win
  local SIDE = 20

  local function terminal(cmd, title)
    wm.open(wired.vterm.app("/rom/shell.lua", {}, { title = title, closeOnExit = false, run = function()
      local env = setmetatable({}, { __index = _G })
      local words = {}
      for w in cmd:gmatch("%S+") do words[#words + 1] = w end
      local path = "/rom/programs/" .. words[1] .. ".lua"
      table.remove(words, 1)
      local ok = os.run(env, path, table.unpack(words))
      print()
      print(ok and "Готово. Окно можно закрыть." or "Окно можно закрыть.")
      while true do os.pullEvent("never") end
    end }))
  end

  local function drawScreen(c, x)
    local s = wired.settings
    c.text(x, 2, "Масштаб", T.fg)
    c.text(x, 3, G.cut("Всё на экране — текст, значки и окна — крупнее", c.w - x), T.dim)
    local bx = x
    local auto = type(s.scale) ~= "number"
    bx = c.button(bx, 5, "Авто" .. (auto and (" · " .. wired.scalePercent() .. " %") or ""), "scale", auto and "primary" or "normal", "auto") + 1
    for _, v in ipairs({ 100, 125, 150, 200, 250, 300 }) do
      bx = c.button(bx, 5, v .. " %", "scale", v == s.scale and "primary" or "normal", v) + 1
    end
    local mw, mh = G.gpu.maxResolution()
    c.text(x, 7, G.cut("Экран " .. mw .. "×" .. mh .. " · рабочая область " .. G.W .. "×" .. G.H, c.w - x), T.faint)
    c.text(x, 10, "Подсказка", T.fg)
    c.text(x, 11, G.cut("«Авто» подбирает масштаб под видеокарту: на ур. 5 (экран 320×100)", c.w - x), T.dim)
    c.text(x, 12, G.cut("это 200 %. Игры всё равно рисуют во весь экран.", c.w - x), T.dim)
  end

  local function drawLook(c, x)
    local s = wired.settings
    c.text(x, 2, "Тема", T.fg)
    c.radio(x, 3, "Тёмная (Wired)", s.theme ~= "light", "theme_dark")
    c.radio(x + 20, 3, "Светлая", s.theme == "light", "theme_light")
    c.text(x, 6, "Цвет акцента", T.fg)
    for i, col in ipairs(T.ACCENTS) do
      c.text(x + (i - 1) * 4, 7, "██", col)
      c.hit(x + (i - 1) * 4, 7, 2, 2, "accent", col)
      if col == T.accent then c.text(x + (i - 1) * 4, 8, "▔▔", T.fg) end
    end
    c.text(x, 11, "Обои", T.fg)
    c.radio(x, 12, "Провода на закате", s.wallpaper == "wires" or s.wallpaper == nil, "wall_wires")
    c.radio(x + 22, 12, "Серверная", s.wallpaper == "rack", "wall_rack")
    c.radio(x + 36, 12, "Однотонные", s.wallpaper == "plain", "wall_plain")
  end

  local function drawUsers(c, x)
    local open = not auth or auth.open()
    if open then
      c.text(x, 2, "Пользователей нет — компьютер открыт для всех", T.fg)
      c.text(x, 3, G.cut("Добавьте администратора: при включении будет вход по паролю.", c.w - x), T.dim)
      c.button(x, 5, "Добавить пользователя", "useradd", "primary")
      return
    end
    c.text(x, 2, "Вы вошли как " .. tostring(auth.user() or "?") .. (wired.isAdmin() and " (администратор)" or ""), T.fg)
    c.text(x, 4, "Пользователи", T.faint)
    local y = 5
    for _, u in ipairs(auth.users() or {}) do
      c.text(x + 1, y, u.admin and "★" or "·", u.admin and T.warn or T.dim)
      c.text(x + 3, y, u.name, T.fg)
      if u.admin then c.text(x + 22, y, "администратор", T.dim) end
      y = y + 1
      if y > c.h - 4 then break end
    end
    local bx = x
    bx = c.button(bx, c.h - 1, "Сменить пароль", "passwd") + 1
    if wired.isAdmin() then
      bx = c.button(bx, c.h - 1, "Добавить", "useradd") + 1
      c.button(bx, c.h - 1, "Журнал входов", "journal")
    end
  end

  local function drawApps(c, x)
    local list = A.installed()
    c.text(x, 2, "Установленные программы", T.fg)
    c.text(x, 4, "Имя", T.faint)
    c.text(x + 26, 4, "Версия", T.faint)
    c.text(x + 36, 4, "Размер", T.faint)
    c.hline(x, 5, c.w - x)
    if #list == 0 then
      c.text(x, 7, "Пока ничего не установлено.", T.dim)
      c.text(x, 8, G.cut("Программы — на дискетах из сундуков в деревнях.", c.w - x), T.dim)
    end
    st.sel = math.max(1, math.min(st.sel, #list))
    for i, e in ipairs(list) do
      local y = 5 + i
      if y > c.h - 5 then break end
      local sel = i == st.sel
      local bg = sel and T.sel or T.win
      if sel then c.fill(x, y, c.w - x, 1, T.sel) end
      local mini = I.MINI[e.icon] or I.MINI.app
      c.text(x + 1, y, mini[1], mini[2], bg)
      c.text(x + 3, y, G.cut(e.name, 22), T.fg, bg)
      c.text(x + 26, y, e.m.version, T.dim, bg)
      c.text(x + 36, y, textutils.formatSize(A.size(e.m.dir)), T.dim, bg)
      c.hit(x, y, c.w - x, 1, "pick", i)
    end
    local e = list[st.sel]
    if e then
      c.hline(x, c.h - 3, c.w - x)
      c.text(x, c.h - 2, G.cut(e.name .. " " .. e.m.version, c.w - x), T.fg)
      local bx = x
      bx = c.button(bx, c.h - 1, "Открыть", "open", "normal", e.id) + 1
      local drive = fs.isDir("/disk") and "/disk" or (fs.isDir("/usb") and "/usb")
      bx = c.button(bx, c.h - 1, "▣ Записать на " .. (drive == "/usb" and "флешку" or "дискету"), "write", drive and "primary" or "off", e.id) + 1
      c.button(bx, c.h - 1, "Удалить", "remove", "normal", e.id)
      if st.busy then c.text(x, c.h - 2, G.pad(st.busy, c.w - x), T.warn) end
      if not drive then c.text(x + 30, c.h - 2, "вставьте пустую дискету", T.faint) end
    end
  end

  local function drawAbout(c, x)
    local info = computer.info and computer.info() or {}
    c.text(x, 2, wired.server and ("◆ SWIRED " .. wired.SVERSION .. " · на основе Wired " .. wired.VERSION) or ("◆ WIRED " .. wired.VERSION), T.accent)
    c.text(x, 3, wired.server and "Система для серверов MineComputers" or "Графическая система MineComputers", T.dim)
    local rows = {
      { "Компьютер", os.getComputerLabel and os.getComputerLabel() or "—" },
      { "Процессор", tostring(info.cpu or "—") },
      { "Память", tostring(info.ram or "?") .. " КБ" },
      { "Видеокарта", tostring(info.gpu or "—") },
      { "Экран", G.W .. "×" .. G.H .. (G.back > 0 and " · двойная буферизация" or "") },
    }
    for i, r in ipairs(rows) do
      c.text(x, 4 + i * 2, r[1], T.dim)
      c.text(x + 14, 4 + i * 2, G.cut(r[2], c.w - x - 15), T.fg)
    end
    -- установочный диск: Wired — для компьютеров, SWired — для серверов в стойке
    local drive = fs.isDir("/usb") and "/usb" or (fs.isDir("/disk") and "/disk")
    local y = 4 + #rows * 2 + 2
    c.text(x, y, "Установочный диск", T.fg)
    c.text(x, y + 1, G.cut(drive and ("Запишется на " .. (drive == "/usb" and "флешку" or "дискету") .. " " .. drive .. " — всё на ней сотрётся.")
      or "Вставьте флешку (или дискету) — на неё запишется установка системы.", c.w - x), T.dim)
    local bx2 = c.button(x, y + 3, "Создать флешку SWired (для сервера)", "mkinst", drive and "primary" or "off", true) + 1
    c.button(bx2, y + 3, "Создать диск Wired", "mkinst", drive and "normal" or "off", false)
    if st.busy then c.text(x, y + 5, G.pad(st.busy, c.w - x), T.warn) end
    c.text(x, c.h - 1, "present day · present time", T.faint)
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    c.fill(1, 1, SIDE, c.h, T.side)
    for i, name in ipairs(SECTIONS) do
      local y = i * 2
      local here = i == st.sec
      if here then c.fill(1, y, SIDE, 1, T.sel) c.text(1, y, "▌", T.accent, T.sel) end
      c.text(3, y, name, here and T.fg or T.dim, here and T.sel or T.side)
      c.hit(1, y, SIDE, 1, "sec", i)
    end
    local x = SIDE + 3
    if st.sec == 1 then drawScreen(c, x)
    elseif st.sec == 2 then drawLook(c, x)
    elseif st.sec == 3 then drawUsers(c, x)
    elseif st.sec == 4 then drawApps(c, x)
    else drawAbout(c, x) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then
      if ev == "focus" or ev == "disk" or ev == "disk_eject" then wm.redraw(w) end
      return
    end
    local s = wired.settings
    if a == "sec" then st.sec = b
    elseif a == "scale" then
      s.scale = b
      wired.saveSettings()
      wired.applyScale()
      wm.layout()
    elseif a == "theme_dark" or a == "theme_light" then
      s.theme = a == "theme_light" and "light" or "dark"
      wired.saveSettings()
      wired.applyTheme()
    elseif a == "accent" then
      s.accent = b
      wired.saveSettings()
      wired.applyTheme()
    elseif a == "wall_wires" or a == "wall_plain" or a == "wall_rack" then
      s.wallpaper = a == "wall_plain" and "plain" or a == "wall_rack" and "rack" or "wires"
      wired.saveSettings()
      wired.applyTheme()
    elseif a == "useradd" then terminal("users add", "Новый пользователь")
    elseif a == "passwd" then terminal("passwd", "Смена пароля")
    elseif a == "journal" then terminal("journal", "Журнал входов")
    elseif a == "pick" then st.sel = b
    elseif a == "open" then A.launch(A.get(b))
    elseif a == "remove" then
      wm.confirm("Удалить программу", "Удалить «" .. b .. "» с компьютера?", function(ok)
        if ok then A.uninstall(b) wm.redraw(w) end
      end, "Удалить")
    elseif a == "mkinst" then
      local drive = fs.isDir("/usb") and "/usb" or (fs.isDir("/disk") and "/disk")
      if not drive then return end
      local what = b and "SWired" or "Wired"
      wm.confirm("Установочный диск " .. what, "Записать установку " .. what .. " на " .. drive .. "? Всё, что на нём есть, сотрётся.", function(ok)
        if not ok then return end
        st.busy = "Записываю…"
        A.makeInstaller(drive, b, function(i, n) st.busy = "Записываю… " .. math.floor(100 * i / n) .. " %" wm.redraw(w) end, function(okW, err)
          st.busy = nil
          wm.notify({ title = okW and ("Готово: диск " .. what) or "Не записалось",
            text = okW and (b and "Вставьте флешку в сервер стойки и включите его — установка начнётся сама" or "Вставьте диск в компьютер и включите его") or tostring(err) })
          wm.redraw(w)
        end)
      end, "Записать")
    elseif a == "write" then
      local drive = fs.isDir("/disk") and "/disk" or "/usb"
      st.busy = "Записываю…"
      A.writeTo(b, drive, function(i, n) st.busy = "Записываю… " .. i .. " из " .. n wm.redraw(w) end, function(ok, err)
        st.busy = nil
        wm.notify({ title = ok and "Записано на диск" or "Не записалось", text = ok and ("«" .. b .. "» — теперь её можно установить с этого диска") or tostring(err) })
        wm.redraw(w)
      end)
    end
    wm.redraw(w)
  end

  return app
end
