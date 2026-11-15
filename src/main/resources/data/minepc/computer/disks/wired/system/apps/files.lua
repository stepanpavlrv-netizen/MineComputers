-- Wired: Проводник — папки, диски, дискеты и флешки; установочный диск — кнопка «Установить».
local G, T, I, U, wm, A = wired.gfx, wired.theme, wired.icons, wired.ui, wired.wm, wired.apps

local function norm(p) return "/" .. fs.combine(p or "", "") end

local KIND = {
  lua = { "◆", 0x4fd1e8 }, txt = { "≡", 0x6b95ff }, cfg = { "≡", 0x8d87a3 }, pix = { "▦", 0xff4f5e },
  wdoc = { "≡", 0x6b95ff }, wtab = { "▦", 0x5fd38a }, wml = { "▦", 0xff8a3d }, wav = { "♪", 0xc77dff },
}

return function(arg)
  local st = { path = norm(type(arg) == "string" and arg or "/home"), back = {}, fwd = {}, sel = nil, scroll = 0, items = {} }
  local app = { title = "Проводник", icon = "files", w = 88, h = 30, minW = 56, minH = 14 }
  local win
  local SIDE = 18

  local function reload()
    local items = {}
    if fs.isDir(st.path) then
      for _, name in ipairs(fs.list(st.path)) do
        local p = fs.combine(st.path, name)
        local dir = fs.isDir(p)
        items[#items + 1] = { name = name, dir = dir, size = dir and 0 or (fs.getSize(p) or 0) }
      end
    end
    table.sort(items, function(a, b)
      if a.dir ~= b.dir then return a.dir end
      return a.name:lower() < b.name:lower()
    end)
    st.items = items
    st.banner = fs.exists(fs.combine(st.path, "app/app.lua")) and A.manifest(fs.combine(st.path, "app")) or nil
    if st.sel and st.sel > #items then st.sel = nil end
    local name = st.path == "/" and "Диск" or fs.getName(st.path)
    if st.path == "/disk" then name = "Дискета" elseif st.path == "/usb" then name = "Флешка" elseif st.path == "/home" then name = "Домой" end
    if win then wm.setTitle(win, "Проводник — " .. name) else app.title = "Проводник — " .. name end
  end

  local function go(p, how)
    p = norm(p)
    if not fs.isDir(p) then wm.alert("Проводник", "Нет такой папки: " .. p) return end
    if how ~= "nav" then st.back[#st.back + 1] = st.path; st.fwd = {} end
    st.path, st.sel, st.scroll = p, nil, 0
    reload()
    wm.redraw(win)
  end

  local function full(item) return norm(fs.combine(st.path, item.name)) end

  local function openItem(item)
    local p = full(item)
    if item.dir then
      if fs.exists(fs.combine(p, "app.lua")) and (st.path:match("^/disk") or st.path:match("^/usb")) then
        A.launch(A.get("installer"), p)
        return
      end
      go(p)
      return
    end
    local ext = item.name:match("%.([%w]+)$")
    local owner = ext and A.forExt(ext:lower())
    if owner then A.launch(owner, p) return end
    if ext == "lua" then
      wm.open(wired.vterm.app(p, {}, { title = item.name, closeOnExit = false, dir = st.path }))
    else
      wm.open(wired.vterm.app("/rom/programs/edit.lua", { p }, { title = "Правка — " .. item.name, dir = st.path }))
    end
  end

  local function listGeom(c)
    local y0 = 4 + (st.banner and 5 or 0)
    return SIDE + 2, y0, c.w - SIDE - 3, c.h - y0 - 1
  end

  function app.init(w)
    win = w
    reload()
  end

  function app.draw(w, c)
    -- панель
    local x = 2
    x = c.button(x, 1, "←", "back", #st.back > 0 and "normal" or "off") + 1
    x = c.button(x, 1, "→", "fwd", #st.fwd > 0 and "normal" or "off") + 1
    x = c.button(x, 1, "↑", "up", st.path ~= "/" and "normal" or "off") + 2
    c.fill(x, 1, c.w - x - 1, 1, T.field)
    c.text(x + 1, 1, G.cut(st.path, c.w - x - 3), T.fg, T.field)
    c.hit(x, 1, c.w - x - 1, 1, "path")
    local bx = 2
    bx = c.button(bx, 2, "+ Папка", "newdir", "ghost")
    local has = st.sel ~= nil
    bx = c.button(bx, 2, "Переименовать", "rename", has and "ghost" or "off")
    bx = c.button(bx, 2, "Копировать", "copy", has and "ghost" or "off")
    bx = c.button(bx, 2, "Вставить", "paste", st.clip and "ghost" or "off")
    bx = c.button(bx, 2, "Удалить", "del", has and "ghost" or "off")
    -- слева
    c.fill(1, 3, SIDE, c.h - 2, T.side)
    for i, p in ipairs(A.places()) do
      local y = 2 + i * 2
      if y > c.h - 3 then break end
      local here = st.path == p.path
      if here then c.fill(1, y, SIDE, 1, T.sel) c.text(1, y, "▌", T.accent, T.sel) end
      c.text(3, y, p.mark, p.color, here and T.sel or T.side)
      c.text(5, y, G.cut(p.name, SIDE - 6), here and T.fg or T.dim, here and T.sel or T.side)
      c.hit(1, y, SIDE, 1, "place", p.path)
    end
    local okc, cap = pcall(fs.getCapacity, st.path)
    local okf, free = pcall(fs.getFreeSpace, st.path)
    if okc and okf and cap and cap > 0 then
      local used = cap - free
      c.text(3, c.h - 1, G.cut(textutils.formatSize(used) .. " из " .. textutils.formatSize(cap), SIDE - 3), T.faint, T.side)
      local bw = SIDE - 5
      local n = math.floor(bw * used / cap + 0.5)
      c.text(3, c.h, string.rep("█", n), T.accent, T.side)
      c.text(3 + n, c.h, string.rep("░", bw - n), T.faint, T.side)
    end
    -- установочный диск
    local mx, y0, mw, rows = listGeom(c)
    if st.banner then
      local b = st.banner
      c.fill(mx, 4, mw, 4, T.sel)
      c.text(mx + 2, 5, "▣", 0xff4f5e, T.sel)
      c.text(mx + 4, 5, "Установочный диск", T.dim, T.sel)
      c.text(mx + 4, 6, G.cut(b.name .. " " .. b.version .. (b.description and (" · " .. b.description) or ""), mw - 22), T.fg, T.sel)
      c.button(mx + mw - 15, 5, "Установить", "install", "primary")
    end
    -- список
    c.text(mx, y0, "Имя", T.faint)
    c.text(mx + mw - 12, y0, "Размер", T.faint)
    c.hline(mx, y0 + 1, mw)
    local first = st.scroll
    for i = 1, rows - 1 do
      local k = first + i
      local it = st.items[k]
      if not it then break end
      local y = y0 + 1 + i
      local sel = st.sel == k
      local bg = sel and T.sel or T.win
      if sel then c.fill(mx, y, mw, 1, T.sel) end
      local ext = it.name:match("%.([%w]+)$")
      local mark = it.dir and { "■", 0xffc857 } or KIND[ext or ""] or { "·", T.dim }
      c.text(mx + 1, y, mark[1], mark[2], bg)
      c.text(mx + 3, y, G.cut(it.name, mw - 18), T.fg, bg)
      if not it.dir then c.text(mx + mw - 12, y, textutils.formatSize(it.size), T.dim, bg) end
      c.hit(mx, y, mw, 1, "item", k)
    end
    if #st.items == 0 then c.text(mx + 1, y0 + 2, "Папка пуста", T.faint) end
    c.scrollbar(c.w, y0 + 2, rows - 1, st.scroll, #st.items, rows - 1)
    local status = #st.items .. " объект(ов)" .. (st.clip and ("   · в буфере: " .. fs.getName(st.clip)) or "")
    c.text(mx, c.h, G.cut(status, mw), T.faint)
  end

  local function selected() return st.sel and st.items[st.sel] end

  local function act(id, data)
    if id == "back" and #st.back > 0 then
      st.fwd[#st.fwd + 1] = st.path
      go(table.remove(st.back), "nav")
    elseif id == "fwd" and #st.fwd > 0 then
      st.back[#st.back + 1] = st.path
      go(table.remove(st.fwd), "nav")
    elseif id == "up" and st.path ~= "/" then go(fs.getDir(st.path))
    elseif id == "path" then
      wm.prompt("Перейти", "Папка:", st.path, function(v) if v and v ~= "" then go(v) end end)
    elseif id == "place" then go(data)
    elseif id == "item" then st.sel = data wm.redraw(win)
    elseif id == "install" then A.launch(A.get("installer"), fs.combine(st.path, "app"))
    elseif id == "newdir" then
      wm.prompt("Новая папка", "Имя папки:", "", function(v)
        if v and v ~= "" then
          local ok, err = pcall(fs.makeDir, fs.combine(st.path, v))
          if not ok then wm.alert("Не получилось", tostring(err)) end
          reload() wm.redraw(win)
        end
      end)
    elseif id == "rename" and selected() then
      local it = selected()
      wm.prompt("Переименовать", "Новое имя:", it.name, function(v)
        if v and v ~= "" and v ~= it.name then
          local ok, err = pcall(fs.move, fs.combine(st.path, it.name), fs.combine(st.path, v))
          if not ok then wm.alert("Не получилось", tostring(err)) end
          reload() wm.redraw(win)
        end
      end)
    elseif id == "copy" and selected() then
      st.clip = full(selected())
      wm.redraw(win)
    elseif id == "paste" and st.clip then
      local src = st.clip
      if not fs.exists(src) then st.clip = nil wm.redraw(win) return end
      local name = fs.getName(src)
      local dst = fs.combine(st.path, name)
      if fs.exists(dst) then
        local base, ext = name:match("^(.-)(%.[^.]*)$")
        dst = fs.combine(st.path, (base or name) .. " (копия)" .. (ext or ""))
      end
      if fs.isDir(src) then
        A.copyTree(src, dst, nil, function(ok, err)
          if not ok then wm.alert("Не скопировалось", tostring(err)) end
          reload() wm.redraw(win)
        end)
      else
        local ok, err = pcall(fs.copy, src, dst)
        if not ok then wm.alert("Не скопировалось", tostring(err)) end
        reload() wm.redraw(win)
      end
    elseif id == "del" and selected() then
      local it = selected()
      wm.confirm("Удалить", "Удалить «" .. it.name .. "»" .. (it.dir and " со всем содержимым?" or "?"), function(ok)
        if ok then
          local okd, err = pcall(fs.delete, fs.combine(st.path, it.name))
          if not okd then wm.alert("Не удалилось", tostring(err)) end
          st.sel = nil
          reload() wm.redraw(win)
        end
      end, "Удалить")
    end
  end

  function app.event(w, ev, a, b, x, y)
    if ev == "click" then act(a, b)
    elseif ev == "dblclick" then
      if a == "item" and st.items[b] then st.sel = b openItem(st.items[b]) else act(a, b) end
    elseif ev == "scroll" then
      local _, _, _, rows = listGeom(win.c)
      st.scroll = math.max(0, math.min(#st.items - (rows - 1), st.scroll + a))
      wm.redraw(w)
    elseif ev == "key" then
      local n = #st.items
      if a == "down" then st.sel = math.min(n, (st.sel or 0) + 1)
      elseif a == "up" then st.sel = math.max(1, (st.sel or 2) - 1)
      elseif a == "enter" and selected() then openItem(selected()) return
      elseif a == "backspace" then act("up") return
      elseif a == "delete" then act("del") return
      else return end
      local _, _, _, rows = listGeom(win.c)
      if st.sel and st.sel > st.scroll + rows - 1 then st.scroll = st.sel - rows + 1 end
      if st.sel and st.sel <= st.scroll then st.scroll = st.sel - 1 end
      wm.redraw(w)
    elseif ev == "disk" or ev == "disk_eject" or ev == "focus" then
      if fs.isDir(st.path) then reload() else go("/home", "nav") end
      wm.redraw(w)
    end
  end

  return app
end
