-- Браузер Майннета для Wired: вкладки, адрес, закладки, ссылки, формы, картинки, загрузка файлов.
local G, T, U, wm, P = wired.gfx, wired.theme, wired.ui, wired.wm, wired.page
local http = require("http")
local BOOKMARKS = "/home/.bookmarks"

-- ======================= загрузка =======================

--- Страница с сайта (внутри фоновой задачи). Возвращает текст, код, тип или nil, ошибка.
local function fetch(url, params, progress)
  if url:match("^file:") then
    local path = url:gsub("^file:", "")
    local f = fs.open(path, "r")
    if not f then return nil, "Нет файла " .. path end
    local body = f.readAll() or ""
    f.close()
    local ctype = path:match("%.page$") and "page" or ((path:match("%.txt$") or path:match("%.md$")) and "text" or "file")
    return body, 200, ctype
  end
  if net.speed() == 0 then return nil, "Нет сети: подключите кабель или Wi-Fi" end
  local host, path = http.split(url)
  if host == "" then return nil, "Пустой адрес" end
  local port = math.random(40000, 49999)
  net.open(port)
  local id = math.random(1, 1000000000)
  if not net.send(host, http.PORT, "GET", id, port, path, params or {}) then
    net.close(port)
    return nil, "Нет сети"
  end
  local parts, got, total, status, ctype = {}, 0, 1, nil, nil
  local timer = os.startTimer(5)
  local err
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "RESP" and ev[6] == id then
      local part = ev[7]
      total, status, ctype = ev[8], ev[9], ev[10]
      if not parts[part] then parts[part] = ev[11] or "" got = got + 1 end
      if progress then progress(got, total) end
      if got >= total then break end
      timer = os.startTimer(5)
    elseif ev[1] == "timer" and ev[2] == timer then
      err = got > 0 and "Ответ оборвался: связь слишком медленная или пропала"
        or ("«" .. host .. "» не отвечает: нет такого сайта, сайт выключен или до него нет сети")
      break
    end
  end
  net.close(port)
  if err then return nil, err end
  local body = {}
  for i = 1, total do body[i] = parts[i] or "" end
  return table.concat(body), status, ctype
end

local function loadBookmarks()
  local out = {}
  local f = fs.exists(BOOKMARKS) and fs.open(BOOKMARKS, "r")
  if f then
    for line in (f.readAll() or ""):gmatch("[^\n]+") do
      local t, u = line:match("^(.-)|(.+)$")
      if u then out[#out + 1] = { title = t, url = u } end
    end
    f.close()
  end
  return out
end

local function saveBookmarks(list)
  local f = fs.open(BOOKMARKS, "w")
  if not f then return end
  for _, b in ipairs(list) do f.write(b.title .. "|" .. b.url .. "\n") end
  f.close()
end

-- ======================= окно =======================

return function(start)
  local app = { title = "Браузер", icon = "web", w = 110, h = 36, minW = 60, minH = 16 }
  local tabs, cur = {}, 1
  local marks = loadBookmarks()
  local images = {}
  local win
  local addr = U.field("", "addr")

  local function tab() return tabs[cur] end

  local function newTab(url)
    tabs[#tabs + 1] = { url = url, title = "Новая вкладка", rows = {}, blocks = {}, top = 0, fields = {}, back = {}, fwd = {},
                        status = "" }
    cur = #tabs
  end

  local function relayout(t)
    local width = math.max(20, math.min((win and win.bw or 100) - 4, 96))
    t.rows = P.layout(t.blocks, width, function(src)
      local key = (t.base or "") .. "|" .. src
      return images[key]
    end)
  end

  local function wantImages(t)
    for _, b in ipairs(t.blocks) do
      if b.kind == "image" then
        local url = t.url:match("^file:") and ("file:" .. fs.combine(fs.getDir((t.url:gsub("^file:", ""))), b.src))
          or http.resolve(t.url, b.src)
        local key = (t.base or "") .. "|" .. b.src
        if images[key] == nil then
          images[key] = false
          wm.spawn(function() return fetch(url) end, function(ok, body)
            if ok and body then images[key] = P.decodePix(body) or false end
            relayout(t)
            wm.redraw(win)
          end)
        end
      end
    end
  end

  local function show(t, body, ctype)
    t.body, t.ctype = body, ctype
    t.blocks = P.parse(body or "", ctype ~= "page")
    t.top, t.focus = 0, nil
    t.title = (ctype == "page" and (body or ""):match("^# ([^\n]+)")) or t.url
    t.title = t.title:gsub("{%a*}", "")
    t.base = t.url
    relayout(t)
    wantImages(t)
  end

  local function open(url, params, how)
    local t = tab()
    url = tostring(url or ""):gsub("^mine://", "")
    if url == "" then return end
    if how ~= "nav" and t.url and t.url ~= "" then t.back[#t.back + 1] = t.url t.fwd = {} end
    t.url, t.loading, t.err, t.status = url, true, nil, "Загрузка…"
    addr.text, addr.pos, addr.focused = url, G.len(url), false
    wm.setTitle(win, "Браузер — " .. url)
    wm.redraw(win)
    wm.spawn(function()
      return fetch(url, params, function(got, total)
        t.status = "Загрузка… " .. got .. " из " .. total
        wm.redraw(win)
      end)
    end, function(ok, body, code, ctype)
      t.loading = false
      if not ok or not body then
        t.err = tostring(ok and code or body)
        show(t, "# Страница не открылась\n" .. t.err .. "\n\n[Попробовать ещё раз](" .. url .. ")", "page")
        t.status = t.err
      elseif ctype == "file" then
        local dir = "/home/Загрузки"
        if not fs.exists(dir) then fs.makeDir(dir) end
        local _, upath = http.split(url)
        local name = fs.getName(upath)
        if name == "" or name == "root" then name = "файл" end
        local f = fs.open(fs.combine(dir, name), "w")
        if f then f.write(body) f.close() end
        t.status = "Скачано: " .. dir .. "/" .. name
        wm.notify({ title = "⇩ Файл скачан", text = dir .. "/" .. name,
          actions = { { "Открыть папку", function() wired.apps.launch(wired.apps.get("files"), dir) end } } })
        if #t.back > 0 then t.url = table.remove(t.back) addr.text = t.url end
      else
        show(t, body, ctype)
        t.status = (code == 404 and "Нет такой страницы · " or "Готово · ") .. textutils.formatSize(#body)
      end
      wm.setTitle(win, "Браузер — " .. (t.title or url))
      wm.redraw(win)
    end)
  end

  local function elements(t)
    local list = {}
    for _, r in ipairs(t.rows) do
      for _, s in ipairs(r.segs or {}) do if s.kind then list[#list + 1] = s end end
    end
    return list
  end

  local function activate(t, s)
    if not s then return end
    if s.kind == "link" then open(http.resolve(t.url, s.target))
    elseif s.kind == "button" then
      local params = {}
      for k, v in pairs(t.fields) do params[k] = v end
      open(http.resolve(t.url, s.target), params)
    elseif s.kind == "field" then t.focus = s end
  end

  function app.init(w)
    win = w
    newTab(nil)
    if type(start) == "string" and start ~= "" then
      open(start:match("^/") and ("file:" .. start) or start)
    else
      show(tab(), "# Браузер Майннета\nНаберите адрес сайта вверху: например {yellow}shop.mine{}.\n\n" ..
        "## Как сделать свой сайт\nСайтостроитель (дискета из деревни) или команда {lightBlue}httpd{} в Терминале.\n---\n" ..
        "Имена сайтов: {lightBlue}dns all{} в Терминале.", "page")
      tab().title = "Новая вкладка"
      addr.focused = true
    end
  end

  function app.draw(w, c)
    local t = tab()
    -- вкладки
    c.fill(1, 1, c.w, 1, T.bar)
    local x = 1
    for i, tb in ipairs(tabs) do
      local name = G.cut(tb.title or "вкладка", 18)
      local wdt = G.len(name) + 6
      if x + wdt > c.w - 4 then break end
      local act = i == cur
      local bg = act and T.win or T.side
      c.fill(x, 1, wdt, 1, bg)
      c.text(x + 1, 1, "◍", act and T.link or T.faint, bg)
      c.text(x + 3, 1, name, act and T.fg or T.dim, bg)
      c.text(x + wdt - 2, 1, "✕", T.faint, bg)
      c.hit(x, 1, wdt - 2, 1, "tab", i)
      c.hit(x + wdt - 2, 1, 2, 1, "closetab", i)
      x = x + wdt + 1
    end
    c.text(x + 1, 1, "+", T.dim, T.bar)
    c.hit(x, 1, 3, 1, "newtab")
    -- адрес
    local bx = 2
    bx = c.button(bx, 2, "←", "back", #t.back > 0 and "ghost" or "off")
    bx = c.button(bx, 2, "→", "fwd", #t.fwd > 0 and "ghost" or "off")
    bx = c.button(bx, 2, t.loading and "✕" or "↻", "reload", "ghost") + 1
    local aw = c.w - bx - 10
    c.field(bx, 2, aw, addr, "адрес сайта, например shop.mine")
    if not addr.focused and t.url and not t.url:match("^file:") then
      c.text(bx, 2, "◆", T.ok, T.field)
    end
    local starred = false
    for _, m in ipairs(marks) do if m.url == t.url then starred = true end end
    c.button(c.w - 8, 2, starred and "★" or "☆", "star", "ghost")
    c.button(c.w - 4, 2, "⌂", "home", "ghost")
    -- закладки
    local mx = 2
    for i, m in ipairs(marks) do
      local label = "★ " .. G.cut(m.title, 16)
      if mx + G.len(label) > c.w - 2 then break end
      c.text(mx, 3, label, T.dim)
      c.hit(mx, 3, G.len(label), 1, "mark", i)
      mx = mx + G.len(label) + 3
    end
    if #marks == 0 then c.text(2, 3, "Закладок нет: ☆ — добавить страницу", T.faint) end
    -- страница
    local ph = c.h - 4
    local pw = math.min(c.w, 100)
    local px = math.floor((c.w - pw) / 2) + 1
    c.fill(1, 4, c.w, ph, P.BG)
    P.draw(c, t.rows, px + 2, 4, pw - 3, ph, t.top, { fields = t.fields, focus = t.focus })
    c.scrollbar(c.w, 4, ph, t.top, #t.rows, ph)
    -- статус
    c.fill(1, c.h, c.w, 1, T.bar)
    local hint = t.status or ""
    c.text(2, c.h, G.cut(hint, c.w - 4), T.faint, T.bar)
  end

  function app.event(w, ev, a, b, x, y)
    local t = tab()
    if ev == "click" or ev == "dblclick" then
      addr.focused = a == "addr"
      if a == "addr" then addr.pos = G.len(addr.text)
      elseif a == "back" and #t.back > 0 then t.fwd[#t.fwd + 1] = t.url open(table.remove(t.back), nil, "nav")
      elseif a == "fwd" and #t.fwd > 0 then t.back[#t.back + 1] = t.url open(table.remove(t.fwd), nil, "nav")
      elseif a == "reload" and t.url then open(t.url, nil, "nav")
      elseif a == "home" then
        local m = marks[1]
        if m then open(m.url) end
      elseif a == "star" and t.url then
        local found
        for i, m in ipairs(marks) do if m.url == t.url then found = i end end
        if found then table.remove(marks, found) else marks[#marks + 1] = { title = G.cut(t.title or t.url, 24), url = t.url } end
        saveBookmarks(marks)
      elseif a == "mark" then open(marks[b].url)
      elseif a == "tab" then cur = b addr.text = tab().url or "" addr.pos = G.len(addr.text)
      elseif a == "closetab" then
        table.remove(tabs, b)
        if #tabs == 0 then wm.close(w) return end
        cur = math.min(cur, #tabs)
        addr.text = tab().url or ""
      elseif a == "newtab" then
        newTab(nil)
        show(tab(), "# Новая вкладка\nНаберите адрес вверху.", "page")
        tab().title = "Новая вкладка"
        addr.text, addr.pos, addr.focused = "", 0, true
      elseif a == "plink" or a == "pbutton" or a == "pfield" then activate(t, b)
      elseif a == "pblock" then t.focus = nil end
      wm.redraw(w)
    elseif ev == "scroll" then
      t.top = math.max(0, math.min(math.max(0, #t.rows - (w.bh - 4)), t.top + a * 3))
      wm.redraw(w)
    elseif ev == "char" or ev == "key" or ev == "paste" then
      if addr.focused then
        local r = U.edit(addr, ev, a)
        if r == "enter" then
          addr.focused = false
          local u = addr.text
          open(u:match("^/") and ("file:" .. u) or u)
        elseif ev == "key" and a == "escape" then addr.focused = false addr.text = t.url or "" end
        wm.redraw(w)
        return
      end
      if t.focus and t.focus.kind == "field" then
        local name = t.focus.name
        local v = t.fields[name] or ""
        if ev == "char" then t.fields[name] = v .. a
        elseif ev == "paste" then t.fields[name] = v .. tostring(a):gsub("\n", " ")
        elseif a == "backspace" then t.fields[name] = G.sub(v, 1, math.max(0, G.len(v) - 1))
        elseif a == "enter" then
          -- Enter в поле — нажать первую кнопку страницы
          for _, s in ipairs(elements(t)) do if s.kind == "button" then activate(t, s) break end end
        elseif a == "tab" then
          local list, idx = elements(t), 0
          for i, s in ipairs(list) do if s == t.focus then idx = i end end
          t.focus = list[idx % #list + 1]
        elseif a == "escape" then t.focus = nil end
        wm.redraw(w)
        return
      end
      if ev ~= "key" then return end
      if a == "tab" then
        local list, idx = elements(t), 0
        for i, s in ipairs(list) do if s == t.focus then idx = i end end
        t.focus = list[idx % math.max(1, #list) + 1]
      elseif a == "enter" and t.focus then activate(t, t.focus)
      elseif a == "down" then t.top = math.min(math.max(0, #t.rows - (w.bh - 4)), t.top + 1)
      elseif a == "up" then t.top = math.max(0, t.top - 1)
      elseif a == "pageDown" or a == "pagedown" then t.top = math.min(math.max(0, #t.rows - (w.bh - 4)), t.top + w.bh - 6)
      elseif a == "pageUp" or a == "pageup" then t.top = math.max(0, t.top - (w.bh - 6))
      elseif a == "backspace" and #t.back > 0 then t.fwd[#t.fwd + 1] = t.url open(table.remove(t.back), nil, "nav") return
      elseif a == "f5" and t.url then open(t.url, nil, "nav") return
      elseif a == "l" and b then addr.focused = true addr.pos = G.len(addr.text)
      else return end
      wm.redraw(w)
    elseif ev == "resize" then
      for _, tb in ipairs(tabs) do relayout(tb) end
      wm.redraw(w)
    end
  end

  return app
end
