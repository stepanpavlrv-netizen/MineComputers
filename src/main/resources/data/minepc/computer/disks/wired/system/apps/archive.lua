-- Wired: Архив — резервная копия папок на флешку или дискету и восстановление.
local G, T, U, wm, A = wired.gfx, wired.theme, wired.ui, wired.wm, wired.apps

local SOURCES = {
  { path = "/home", name = "Домой", on = true },
  { path = "/apps", name = "Программы", on = false },
  { path = "/www", name = "Сайт", on = false },
  { path = "/mail", name = "Почта", on = false },
  { path = "/wired/settings.cfg", name = "Настройки Wired", on = true },
}

local function drives()
  local out = {}
  for _, d in ipairs({ "/usb", "/disk", "/usb2", "/disk2" }) do
    if fs.isDir(d) then out[#out + 1] = d end
  end
  return out
end

local function backups(drive)
  local dir = fs.combine(drive, "archive")
  local out = {}
  if fs.isDir(dir) then
    for _, n in ipairs(fs.list(dir)) do if fs.isDir(fs.combine(dir, n)) then out[#out + 1] = n end end
  end
  table.sort(out, function(a, b) return a > b end)
  return out
end

local function size(path)
  if not fs.exists(path) then return 0 end
  if not fs.isDir(path) then return fs.getSize(path) or 0 end
  return A.size(path)
end

return function()
  local app = { title = "Архив", icon = "archive", w = 76, h = 26, minW = 60, minH = 20 }
  local st = { drive = drives()[1], sel = 1, busy = nil, sources = {} }
  for i, s in ipairs(SOURCES) do st.sources[i] = { path = s.path, name = s.name, on = s.on } end
  local win

  local function copyList(list, dst, done)
    local i = 0
    local function nextOne()
      i = i + 1
      local s = list[i]
      if not s then return done(true) end
      st.busy = "Копирую " .. s.name .. "…"
      wm.redraw(win)
      if not fs.exists(s.from) then return nextOne() end
      if fs.isDir(s.from) then
        A.copyTree(s.from, s.to, function(k, n) st.busy = s.name .. ": " .. k .. " из " .. n wm.redraw(win) end, function(ok, err)
          if not ok then return done(false, err) end
          nextOne()
        end)
      else
        local d = fs.getDir(s.to)
        if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
        if fs.exists(s.to) then fs.delete(s.to) end
        local ok, err = pcall(fs.copy, s.from, s.to)
        if not ok then return done(false, err) end
        nextOne()
      end
    end
    nextOne()
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    local ds = drives()
    if st.drive and not fs.isDir(st.drive) then st.drive = ds[1] end
    if not st.drive then st.drive = ds[1] end
    c.text(2, 2, "Что сохранить", T.fg)
    local total = 0
    for i, s in ipairs(st.sources) do
      local sz = size(s.path)
      if s.on then total = total + sz end
      c.check(3, 3 + i, G.pad(s.name, 18), s.on, "src")
      c.hit(3, 3 + i, 20, 1, "src", i)
      c.text(24, 3 + i, fs.exists(s.path) and textutils.formatSize(sz) or "нет", T.dim)
    end
    c.text(2, 10, "Куда", T.fg)
    if #ds == 0 then
      c.text(3, 11, "Вставьте флешку или дискету", T.warn)
    else
      local x = 3
      for _, d in ipairs(ds) do
        x = c.button(x, 11, d == "/usb" and "▭ Флешка" or (d:match("usb") and ("▭ " .. d) or ("▣ " .. (d == "/disk" and "Дискета" or d))),
          "drive", d == st.drive and "primary" or "normal", d) + 1
      end
      local okf, free = pcall(fs.getFreeSpace, st.drive)
      if okf and free then
        c.text(3, 12, "Свободно " .. textutils.formatSize(free) .. " · нужно " .. textutils.formatSize(total),
          free >= total and T.dim or T.err)
      end
    end
    c.button(3, 14, "Сохранить копию", "backup", (st.drive and not st.busy) and "primary" or "off")
    -- копии на носителе
    local rx = 42
    c.text(rx, 2, "Копии на носителе", T.fg)
    local list = st.drive and backups(st.drive) or {}
    if #list == 0 then c.text(rx + 1, 4, "Копий нет", T.faint) end
    for i, n in ipairs(list) do
      local y = 3 + i
      if y > c.h - 5 then break end
      local sel = i == st.sel
      if sel then c.fill(rx, y, c.w - rx, 1, T.sel) end
      c.text(rx + 1, y, n:gsub("_", " "), T.fg, sel and T.sel or T.win)
      c.hit(rx, y, c.w - rx, 1, "pick", i)
    end
    if list[st.sel] then
      local bx = rx
      bx = c.button(bx, c.h - 3, "Восстановить", "restore", st.busy and "off" or "normal") + 1
      c.button(bx, c.h - 3, "Удалить", "delete", st.busy and "off" or "normal")
    end
    if st.busy then c.text(2, c.h - 1, G.cut(st.busy, c.w - 3), T.warn) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" and ev ~= "dblclick" then
      if ev == "disk" or ev == "disk_eject" or ev == "focus" then wm.redraw(w) end
      return
    end
    if a == "src" and type(b) == "number" then st.sources[b].on = not st.sources[b].on
    elseif a == "drive" then st.drive, st.sel = b, 1
    elseif a == "pick" then st.sel = b
    elseif a == "backup" and st.drive and not st.busy then
      local name = os.date("%Y-%m-%d_%H-%M")
      local dst = fs.combine(st.drive, "archive/" .. name)
      local list = {}
      for _, s in ipairs(st.sources) do
        if s.on then list[#list + 1] = { name = s.name, from = s.path, to = fs.combine(dst, fs.combine(s.path, "")) } end
      end
      copyList(list, dst, function(ok, err)
        st.busy = nil
        wm.notify({ title = ok and "▤ Копия сохранена" or "Копия не сохранилась", text = ok and ("/" .. dst) or tostring(err) })
        wm.redraw(w)
      end)
    elseif a == "restore" then
      local name = backups(st.drive)[st.sel]
      if not name then return end
      wm.confirm("Восстановить", "Вернуть файлы из копии " .. name .. "? Текущие будут заменены.", function(ok)
        if not ok then return end
        local root = fs.combine(st.drive, "archive/" .. name)
        local list = {}
        for _, n in ipairs(fs.list(root)) do
          local p = fs.combine(root, n)
          if fs.isDir(p) then
            for _, m in ipairs(fs.list(p)) do
              list[#list + 1] = { name = n .. "/" .. m, from = fs.combine(p, m), to = "/" .. fs.combine(n, m) }
            end
          else
            list[#list + 1] = { name = n, from = p, to = "/" .. n }
          end
        end
        copyList(list, "/", function(ok2, err)
          st.busy = nil
          wm.notify({ title = ok2 and "▤ Файлы восстановлены" or "Не восстановилось", text = ok2 and name or tostring(err) })
          if ok2 then wm.refreshDesktop() end
          wm.redraw(w)
        end)
      end, "Восстановить")
    elseif a == "delete" then
      local name = backups(st.drive)[st.sel]
      if not name then return end
      wm.confirm("Удалить копию", "Удалить копию " .. name .. "?", function(ok)
        if ok then fs.delete(fs.combine(st.drive, "archive/" .. name)) st.sel = 1 wm.redraw(w) end
      end, "Удалить")
    end
    wm.redraw(w)
  end

  return app
end
