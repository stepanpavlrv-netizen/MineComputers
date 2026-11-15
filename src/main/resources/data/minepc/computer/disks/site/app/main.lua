-- Сайтостроитель для Wired: страницы сайта из блоков (заголовок, текст, ссылка, форма, картинка…) без кода.
-- Страницы — в /www (*.page), публикация — httpd этого компьютера или хостинг на сервере (hostd).
local G, T, U, wm, P = wired.gfx, wired.theme, wired.ui, wired.wm, wired.page
local WWW = "/www"
local HTTPD = "/rom/programs/httpd.lua"

--- Окно «На хостинг»: сервер, логин, пароль, имя сайта → загрузка папки /www (только изменённые файлы).
local function hostingDialog(onDone)
  local hosting = require("hosting")
  local saved = {}
  local f = fs.exists(WWW .. "/.deploy") and fs.open(WWW .. "/.deploy", "r")
  if f then
    local line = f.readAll() or ""
    f.close()
    saved.server, saved.user, saved.site = line:match("^([^|]*)|([^|]*)|([^|\n]*)")
  end
  local fields = {
    { label = "Сервер", f = U.field(saved.server or "", "f1") },
    { label = "Логин", f = U.field(saved.user or "", "f2") },
    { label = "Пароль", f = U.field("", "f3", "•") },
    { label = "Имя сайта", f = U.field(saved.site or "", "f4") },
  }
  local focus = (saved.server and saved.server ~= "") and 3 or 1
  fields[focus].f.focused = true
  local st = { servers = nil, busy = nil, err = nil, done = nil }
  local win
  local app = { title = "Сайт на хостинг", icon = "site", w = 76, h = 22, resizable = false }

  local function setFocus(i)
    for k, x in ipairs(fields) do x.f.focused = k == i end
    focus = i
  end

  local function upload()
    local server, user, pass, site = fields[1].f.text, fields[2].f.text, fields[3].f.text, fields[4].f.text
    if server == "" or user == "" or site == "" then st.err = "Заполните сервер, логин и имя сайта" wm.redraw(win) return end
    st.err, st.busy, st.done = nil, "Подключаюсь…", nil
    wm.redraw(win)
    wm.spawn(function()
      local s = hosting.connect(server, user, pass, { quiet = true })
      return s:upload(WWW, site, function(i, n, file)
        st.busy = string.format("%d/%d  %s", i, n, file)
        wm.redraw(win)
      end)
    end, function(ok, url, sent, bytes)
      st.busy = nil
      if ok and url then
        local w = fs.open(WWW .. "/.deploy", "w")
        if w then w.write(server .. "|" .. user .. "|" .. site .. "\n") w.close() end
        st.done = url
        if onDone then onDone(url, server) end
        wm.notify({ title = "☁ Сайт на хостинге", text = url .. " — работает на сервере " .. server .. ", даже когда этот компьютер выключен" })
      else
        st.err = tostring(ok and sent or url)
      end
      wm.redraw(win)
    end)
  end

  function app.init(w)
    win = w
    wm.spawn(function() return hosting.discover(true) end, function(ok, list)
      st.servers = ok and list or {}
      wm.redraw(win)
    end)
  end

  function app.draw(w, c)
    c.text(3, 2, "Сайт будет жить на сервере: работает, даже когда этот компьютер выключен.", T.dim)
    c.text(3, 3, "Логин и пароль выдаёт владелец сервера (hostd add).", T.faint)
    for i, x in ipairs(fields) do
      local y = 4 + i * 2
      c.text(3, y, x.label, T.fg)
      c.field(15, y, c.w - 17, x.f)
    end
    c.text(15, 13, "например shop — будет shop.mine", T.faint)
    c.text(3, 16, "В сети:", T.dim)
    local bx = 11
    if st.servers == nil then c.text(bx, 16, "ищу серверы…", T.faint)
    elseif #st.servers == 0 then c.text(bx, 16, "серверов с хостингом не видно", T.faint)
    else
      for _, sv in ipairs(st.servers) do
        if bx > c.w - 10 then break end
        bx = c.button(bx, 16, sv.name, "server", fields[1].f.text == sv.name and "primary" or "normal", sv.name) + 1
      end
    end
    if st.busy then c.text(3, 18, G.cut(st.busy, c.w - 4), T.warn)
    elseif st.err then c.text(3, 18, G.cut(st.err, c.w - 4), T.err)
    elseif st.done then c.text(3, 18, G.cut("Готово: " .. st.done .. " — откройте в Браузере", c.w - 4), T.ok) end
    local b = c.w - 30
    b = c.button(b, c.h - 1, "Закрыть", "close") + 1
    c.button(b, c.h - 1, "☁ Загрузить", "go", st.busy and "off" or "primary")
  end

  function app.event(w, ev, a, b)
    if ev == "click" or ev == "dblclick" then
      if a == "close" then wm.close(w)
      elseif a == "go" and not st.busy then upload()
      elseif a == "server" then fields[1].f.text, fields[1].f.pos = b, G.len(b) setFocus(2)
      elseif type(a) == "string" and a:match("^f%d$") then setFocus(tonumber(a:sub(2))) end
      wm.redraw(w)
    elseif ev == "char" or ev == "key" or ev == "paste" then
      if ev == "key" and a == "escape" then wm.close(w) return end
      local r = U.edit(fields[focus].f, ev, a)
      if r == "tab" then setFocus(focus % #fields + 1)
      elseif r == "enter" then if focus < #fields then setFocus(focus + 1) elseif not st.busy then upload() end end
      wm.redraw(w)
    end
  end

  return wm.open(app)
end

local KINDS = {
  { id = "h1", name = "Заголовок", make = "# Заголовок" },
  { id = "h2", name = "Подзаголовок", make = "## Подзаголовок" },
  { id = "text", name = "Текст", make = "Новый абзац текста." },
  { id = "quote", name = "Цитата", make = "> Цитата" },
  { id = "rule", name = "Черта", make = "---" },
  { id = "link", name = "Ссылка", make = "[Другая страница](/about.page)" },
  { id = "form", name = "Поле и кнопка", make = "Ваше имя: [?name:имя] [!Отправить](/hello.lua)" },
  { id = "image", name = "Картинка", make = "![картинка](картинка.pix)" },
}
local COLORS = { { "", "обычный", 0xe8e4f0 }, { "red", "красный", 0xcc4c4c }, { "yellow", "жёлтый", 0xdede6c },
                 { "lime", "зелёный", 0x7fcc19 }, { "lightBlue", "голубой", 0x99b2f2 }, { "orange", "оранжевый", 0xf2b233 } }

local function httpdRunning()
  for _, p in ipairs(computer.processes()) do if p.path == HTTPD then return p.pid end end
end

local function siteName()
  local ok, names = net.dns("names")
  if ok and names and names[1] then return names[1].name end
  local h = net.hostname()
  return h ~= "" and h or net.address():sub(1, 8)
end

--- Разобрать строку на вид блока и текст без значков разметки начала строки.
local function split(line)
  if line:match("^%-%-%-+%s*$") then return "rule", "" end
  if line:match("^## ") then return "h2", line:sub(4) end
  if line:match("^# ") then return "h1", line:sub(3) end
  if line:match("^> ") then return "quote", line:sub(3) end
  if line:match("^!%[") then return "image", line end
  return "text", line
end
local function join(kind, text)
  if kind == "rule" then return "---" end
  if kind == "h1" then return "# " .. text end
  if kind == "h2" then return "## " .. text end
  if kind == "quote" then return "> " .. text end
  return text
end

return function()
  local app = { title = "Сайтостроитель", icon = "site", w = 120, h = 38, minW = 90, minH = 24 }
  local st = { pages = {}, page = nil, lines = {}, sel = 1, top = 0, dirty = false }
  local win, rows, blocks
  local fText = U.field("", "text")
  local msg

  local function listPages()
    st.pages = {}
    if fs.isDir(WWW) then
      for _, f in ipairs(fs.list(WWW)) do if f:match("%.page$") then st.pages[#st.pages + 1] = f end end
    end
    table.sort(st.pages, function(a, b)
      if a == "index.page" then return true elseif b == "index.page" then return false end
      return a < b
    end)
  end

  local function loadPage(name)
    st.page, st.lines, st.sel, st.top, st.dirty = name, {}, 1, 0, false
    local f = fs.open(fs.combine(WWW, name), "r")
    local text = f and f.readAll() or ""
    if f then f.close() end
    for l in (text .. "\n"):gmatch("(.-)\r?\n") do st.lines[#st.lines + 1] = l end
    while #st.lines > 1 and st.lines[#st.lines] == "" do st.lines[#st.lines] = nil end
    if #st.lines == 0 then st.lines[1] = "# " .. name:gsub("%.page$", "") end
  end

  local function savePage()
    if not st.page then return end
    if not fs.exists(WWW) then fs.makeDir(WWW) end
    local f = fs.open(fs.combine(WWW, st.page), "w")
    if f then f.write(table.concat(st.lines, "\n") .. "\n") f.close() end
    st.dirty = false
    msg = "Сохранено: " .. WWW .. "/" .. st.page
  end

  local function syncField()
    local line = st.lines[st.sel] or ""
    local _, text = split(line)
    fText.text, fText.pos = text, G.len(text)
  end

  local function retitle()
    if win then wm.setTitle(win, "Сайтостроитель — " .. siteName() .. "/" .. (st.page or "") .. (st.dirty and " •" or "")) end
  end

  local function changed()
    st.dirty = true
    retitle()
    wm.redraw(win)
  end

  local function images(src)
    local path = src:match("^/") and fs.combine(WWW, src) or fs.combine(WWW, src)
    if not fs.exists(path) then return nil end
    local f = fs.open(path, "r")
    if not f then return nil end
    local data = f.readAll()
    f.close()
    return P.decodePix(data)
  end

  function app.init(w)
    win = w
    listPages()
    if #st.pages == 0 then
      if not fs.exists(WWW) then fs.makeDir(WWW) end
      local f = fs.open(WWW .. "/index.page", "w")
      f.write("# Мой сайт\nДобро пожаловать! Этот сайт сделан в Сайтостроителе Wired.\n---\n## О нас\nРасскажите о себе здесь.\n[Страница «О нас»](/about.page)\n")
      f.close()
      listPages()
    end
    loadPage(st.pages[1])
    syncField()
    retitle()
  end

  local SIDE, PROPS = 22, 34

  function app.draw(w, c)
    -- страницы
    c.fill(1, 1, c.w, 1, T.bar)
    c.text(2, 1, "Страницы:", T.dim, T.bar)
    local x = 12
    for _, p in ipairs(st.pages) do
      local label = p:gsub("%.page$", "")
      local act = p == st.page
      x = c.button(x, 1, label, "page", act and "primary" or "normal", p) + 1
      if x > c.w - 66 then break end
    end
    x = c.button(x, 1, "+", "newpage") + 2
    local on = httpdRunning()
    local rx = c.w - 1
    local pub = on and "■ Остановить сайт" or "◉ Опубликовать"
    rx = rx - G.len(pub) - 2
    c.button(rx, 1, pub, on and "unpublish" or "publish", on and "normal" or "primary")
    rx = rx - 15
    c.button(rx, 1, "☁ На хостинг", "hosting")
    rx = rx - 13
    c.button(rx, 1, "▷ Просмотр", "preview")
    rx = rx - 13
    c.button(rx, 1, "Сохранить", "save", st.dirty and "normal" or "ghost")
    -- блоки
    c.fill(1, 2, SIDE, c.h - 1, T.side)
    c.text(2, 3, "ДОБАВИТЬ БЛОК", T.faint, T.side)
    for i, k in ipairs(KINDS) do
      local y = 3 + i * 2
      c.text(2, y, "+", T.accent, T.side)
      c.text(4, y, k.name, T.fg, T.side)
      c.hit(1, y, SIDE, 1, "add", i)
    end
    c.text(2, c.h - 3, "Щелчок по блоку на", T.faint, T.side)
    c.text(2, c.h - 2, "странице — выбрать", T.faint, T.side)
    -- страница
    local px, pw = SIDE + 2, c.w - SIDE - PROPS - 3
    blocks = P.parse(table.concat(st.lines, "\n"))
    rows = P.layout(blocks, pw - 4, images)
    local ph = c.h - 3
    -- держать выбранный блок на экране
    local first, last
    for i, r in ipairs(rows) do if r.block == st.sel then first = first or i last = i end end
    if first then
      if first <= st.top then st.top = first - 1 end
      if last > st.top + ph then st.top = last - ph end
    end
    c.fill(px - 1, 2, pw + 2, c.h - 1, 0x0b0912)
    P.draw(c, rows, px + 1, 3, pw - 2, ph, st.top, { sel = st.sel, selBg = 0x2a2440, selMark = T.accent, fields = {} })
    c.scrollbar(px + pw, 3, ph, st.top, #rows, ph)
    -- свойства
    local qx = c.w - PROPS + 1
    c.fill(qx - 1, 2, PROPS + 1, c.h - 1, T.side)
    local kind = split(st.lines[st.sel] or "")
    local kname = kind
    for _, k in ipairs(KINDS) do if k.id == kind then kname = k.name end end
    c.text(qx, 3, "СВОЙСТВА", T.faint, T.side)
    c.text(qx, 4, "Блок " .. st.sel .. " · " .. kname, T.fg, T.side)
    c.bg = T.side
    if kind ~= "rule" then
      c.text(qx, 6, kind == "image" and "Разметка картинки" or "Текст", T.dim, T.side)
      c.field(qx, 7, PROPS - 2, fText)
    end
    c.text(qx, 9, "Вид", T.dim, T.side)
    local kx, ky = qx, 10
    for _, k in ipairs({ { "h1", "Заг" }, { "h2", "Подзаг" }, { "text", "Текст" }, { "quote", "Цитата" } }) do
      kx = c.button(kx, ky, k[2], "kind", kind == k[1] and "primary" or "normal", k[1]) + 1
    end
    c.text(qx, 12, "Цвет текста", T.dim, T.side)
    for i, col in ipairs(COLORS) do
      c.text(qx + (i - 1) * 4, 13, "██", col[3], T.side)
      c.hit(qx + (i - 1) * 4, 13, 2, 1, "color", i)
    end
    c.text(qx, 15, "Вставить в текст", T.dim, T.side)
    local ix = qx
    ix = c.button(ix, 16, "ссылку", "inslink") + 1
    ix = c.button(ix, 16, "поле", "insfield") + 1
    c.button(ix, 16, "кнопку", "insbtn")
    c.text(qx, 18, "Блок", T.dim, T.side)
    local mx = qx
    mx = c.button(mx, 19, "↑", "up") + 1
    mx = c.button(mx, 19, "↓", "down") + 1
    mx = c.button(mx, 19, "Копия", "dup") + 1
    c.button(mx, 19, "Удалить", "del")
    c.hline(qx, 21, PROPS - 2)
    local name = siteName()
    c.text(qx, 22, on and "● сайт работает" or "○ сайт не запущен", on and T.ok or T.dim, T.side)
    c.text(qx, 23, G.cut(name .. "/" .. (st.page or ""), PROPS - 2), T.faint, T.side)
    local okn, names = net.dns("names")
    if not (okn and names and names[1]) then
      c.button(qx, 25, "Занять имя .mine", "dns")
    end
    if msg then c.text(qx, c.h - 1, G.cut(msg, PROPS - 2), T.warn, T.side) end
    c.bg = T.win
  end

  local function setLine(text)
    local kind = split(st.lines[st.sel] or "")
    st.lines[st.sel] = join(kind, text)
    changed()
  end

  local function insertText(s)
    fText.text = G.sub(fText.text, 1, fText.pos) .. s .. G.sub(fText.text, fText.pos + 1)
    fText.pos = fText.pos + G.len(s)
    setLine(fText.text)
  end

  function app.event(w, ev, a, b, x, y)
    msg = nil
    if ev == "click" or ev == "dblclick" then
      fText.focused = a == "text"
      if a == "page" then
        if st.dirty then savePage() end
        loadPage(b) syncField() retitle()
      elseif a == "newpage" then
        wm.prompt("Новая страница", "Имя страницы (латиницей, например about):", "", function(v)
          if v and v ~= "" then
            v = v:gsub("%.page$", ""):gsub("[^%w_%-]", "")
            if v == "" then return end
            if st.dirty then savePage() end
            local f = fs.open(fs.combine(WWW, v .. ".page"), "w")
            if f then f.write("# " .. v .. "\n[На главную](/)\n") f.close() end
            listPages()
            loadPage(v .. ".page")
            syncField()
            retitle()
            wm.redraw(w)
          end
        end)
      elseif a == "save" then savePage() retitle()
      elseif a == "preview" then
        savePage()
        local br = wired.apps.get("browser")
        if br then wired.apps.launch(br, WWW .. "/" .. st.page)
        else msg = "Нужен Браузер (дискета из деревни)" end
      elseif a == "publish" then
        savePage()
        local ok, err = true, nil
        if not httpdRunning() then ok, err = pcall(computer.spawn, HTTPD, "serve") end
        if ok then
          wired.apps.autostart(HTTPD .. " serve", true)
          local okn, names = net.dns("names")
          local addr = (okn and names and names[1]) and names[1].name or siteName()
          wm.notify({ title = "◉ Сайт опубликован", text = addr .. " — открывается в Браузере на любом компьютере сети и включается сам при запуске" })
          if not (okn and names and names[1]) then msg = "Займите имя .mine — по нему сайт найдут" end
        else
          msg = "Не запустился: " .. tostring(err)
        end
        retitle()
      elseif a == "hosting" then
        savePage()
        hostingDialog(function(url) msg = "На хостинге: " .. url retitle() wm.redraw(w) end)
      elseif a == "unpublish" then
        local pid = httpdRunning()
        if pid then pcall(computer.kill, pid) end
        wired.apps.autostart(HTTPD .. " serve", false)
        msg = "Сайт остановлен"
        retitle()
      elseif a == "dns" then
        wm.prompt("Имя сайта", "Имя в Майннете (например shop):", "", function(v)
          if v and v ~= "" then
            local ok, res = net.dns("register", v)
            msg = ok and ("Имя " .. tostring(res) .. " — ваше") or tostring(res)
            retitle()
            wm.redraw(w)
          end
        end)
      elseif a == "add" then
        table.insert(st.lines, st.sel + 1, KINDS[b].make)
        st.sel = st.sel + 1
        syncField()
        changed()
        return
      elseif a == "pblock" or a == "plink" or a == "pbutton" or a == "pfield" then
        st.sel = (a == "pblock") and b or b.block or st.sel
        syncField()
      elseif a == "kind" then
        local _, text = split(st.lines[st.sel] or "")
        st.lines[st.sel] = join(b, text)
        syncField()
        changed()
        return
      elseif a == "color" then
        local kind, text = split(st.lines[st.sel] or "")
        text = text:gsub("^{%a*}", ""):gsub("{}$", "")
        if COLORS[b][1] ~= "" then text = "{" .. COLORS[b][1] .. "}" .. text .. "{}" end
        st.lines[st.sel] = join(kind, text)
        syncField()
        changed()
        return
      elseif a == "inslink" then
        wm.prompt("Ссылка", "Текст и адрес через | (например: О нас | /about.page)", "", function(v)
          local t_, u = tostring(v or ""):match("^%s*(.-)%s*|%s*(.-)%s*$")
          if t_ and u ~= "" then insertText("[" .. t_ .. "](" .. u .. ")") end
        end)
      elseif a == "insfield" then
        wm.prompt("Поле формы", "Имя поля (латиницей) и подсказка через | (например: name | ваше имя)", "", function(v)
          local n, h = tostring(v or ""):match("^%s*([%w_]+)%s*|?%s*(.-)%s*$")
          if n then insertText("[?" .. n .. (h ~= "" and (":" .. h) or "") .. "]") end
        end)
      elseif a == "insbtn" then
        wm.prompt("Кнопка", "Надпись и адрес через | (например: Отправить | /hello.lua)", "", function(v)
          local t_, u = tostring(v or ""):match("^%s*(.-)%s*|%s*(.-)%s*$")
          if t_ and u ~= "" then insertText("[!" .. t_ .. "](" .. u .. ")") end
        end)
      elseif a == "up" and st.sel > 1 then
        st.lines[st.sel], st.lines[st.sel - 1] = st.lines[st.sel - 1], st.lines[st.sel]
        st.sel = st.sel - 1
        changed()
        return
      elseif a == "down" and st.sel < #st.lines then
        st.lines[st.sel], st.lines[st.sel + 1] = st.lines[st.sel + 1], st.lines[st.sel]
        st.sel = st.sel + 1
        changed()
        return
      elseif a == "dup" then
        table.insert(st.lines, st.sel + 1, st.lines[st.sel])
        st.sel = st.sel + 1
        changed()
        return
      elseif a == "del" then
        table.remove(st.lines, st.sel)
        if #st.lines == 0 then st.lines[1] = "" end
        st.sel = math.max(1, math.min(st.sel, #st.lines))
        syncField()
        changed()
        return
      end
      wm.redraw(w)
    elseif ev == "scroll" then
      st.top = math.max(0, math.min(math.max(0, #(rows or {}) - 3), st.top + a * 2))
      wm.redraw(w)
    elseif ev == "char" or ev == "paste" or ev == "key" then
      if ev == "key" and b and a == "s" then savePage() retitle() wm.redraw(w) return end
      if fText.focused then
        local r = U.edit(fText, ev, a)
        if r == true then setLine(fText.text) return end
        if r == "enter" then
          table.insert(st.lines, st.sel + 1, "")
          st.sel = st.sel + 1
          syncField()
          changed()
          return
        end
        wm.redraw(w)
        return
      end
      if ev == "key" then
        if a == "up" and st.sel > 1 then st.sel = st.sel - 1 syncField()
        elseif a == "down" and st.sel < #st.lines then st.sel = st.sel + 1 syncField()
        elseif a == "enter" then fText.focused = true
        elseif a == "delete" then return app.event(w, "click", "del")
        else return end
        wm.redraw(w)
      elseif ev == "char" then
        fText.focused = true
        U.edit(fText, ev, a)
        setLine(fText.text)
      end
    elseif ev == "resize" then
      wm.redraw(w)
    end
  end

  function app.close(w)
    if st.dirty then savePage() end
  end

  return app
end
