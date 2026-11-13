-- Браузер Майннета: browse [адрес]   (адрес — shop.mine, имя компьютера или его адрес, можно с путём)
-- Мышь: щелчок по ссылке, полю, кнопке; колесо — прокрутка. Клавиши: Tab — следующая ссылка или поле,
-- Enter — открыть, ↑↓ PgUp PgDn — прокрутка; без выбранного поля: g — адрес, r — обновить, b — назад, q — выход.
--
-- Разметка страниц (.page):
--   # Заголовок   ## Подзаголовок   ---  черта   > цитата
--   {red}цвет{}   [текст](адрес)   [?поле]   [?поле:подсказка]   [!Кнопка](адрес) — отправить поля формы
local http = require("http")
local ulen, usub = unicode.len, unicode.sub
local start = ...

local W, H = term.getSize()
local color = term.isColor()
local C = {
  bar = colors.gray, barText = colors.white, text = colors.white, title = colors.yellow, sub = colors.orange,
  link = colors.lightBlue, linkFocus = colors.cyan, quote = colors.lightGray, field = colors.gray, fieldFocus = colors.blue,
  button = colors.green, status = colors.lightGray, err = colors.red, rule = colors.gray,
}

local url, history, forward = nil, {}, {}
local lines, elements, scroll, focus = {}, {}, 0, nil
local fields = {}            -- имя → значение (поля формы на странице)
local status = ""
local editingAddress = false
local addressText = ""

-- ======================= Разметка → строки =======================

local function colorByName(n)
  if n == "" then return C.text end
  return colors[n] or C.text
end

--- Разбирает одну строку разметки на куски {text, fg, kind, target, name}.
local function parseInline(s, fg)
  local out, base = {}, fg
  local i = 1
  local cur = base
  local buf = ""
  local function flush() if buf ~= "" then out[#out + 1] = { text = buf, fg = cur } buf = "" end end
  while i <= #s do
    local c = s:sub(i, i)
    if c == "{" then
      local name, e = s:match("^{(%a*)}()", i)
      if e then
        flush()
        cur = name == "" and base or colorByName(name)
        i = e
      else
        buf = buf .. c
        i = i + 1
      end
    elseif c == "[" then
      local fname, hint, e1 = s:match("^%[%?([%w_]+):?([^%]]*)%]()", i)
      local btext, btarget, e2 = s:match("^%[!([^%]]+)%]%(([^%)]*)%)()", i)
      local ltext, ltarget, e3 = s:match("^%[([^%]]+)%]%(([^%)]*)%)()", i)
      if fname then
        flush()
        out[#out + 1] = { kind = "field", name = fname, hint = hint, fg = C.text }
        i = e1
      elseif btext then
        flush()
        out[#out + 1] = { kind = "button", text = "[ " .. btext .. " ]", target = btarget, fg = C.button }
        i = e2
      elseif ltext then
        flush()
        out[#out + 1] = { kind = "link", text = ltext, target = ltarget, fg = C.link }
        i = e3
      else
        buf = buf .. c
        i = i + 1
      end
    else
      buf = buf .. c
      i = i + 1
    end
  end
  flush()
  return out
end

local FIELD_W = 16

local function segLen(seg)
  if seg.kind == "field" then return FIELD_W + 2 end
  return ulen(seg.text)
end

--- Переносит куски по ширине экрана: список строк, каждая — список кусков.
local function wrap(segs, width)
  local rows, row, used = {}, {}, 0
  local function newRow() rows[#rows + 1] = row row, used = {}, 0 end
  for _, seg in ipairs(segs) do
    if seg.kind == "field" or seg.kind == "button" or seg.kind == "link" then
      local n = segLen(seg)
      if used > 0 and used + n > width then newRow() end
      row[#row + 1] = seg
      used = used + n
    else
      local text = seg.text
      while ulen(text) > 0 do
        local room = width - used
        if room <= 0 then newRow() room = width end
        if ulen(text) <= room then
          row[#row + 1] = { text = text, fg = seg.fg }
          used = used + ulen(text)
          text = ""
        else
          -- перенос по пробелу, если он есть
          local piece = usub(text, 1, room)
          local cut = piece:match("^.*() ")
          if cut and cut > 1 then piece = piece:sub(1, cut - 1) end
          row[#row + 1] = { text = piece, fg = seg.fg }
          text = usub(text, ulen(piece) + 1):gsub("^ ", "")
          newRow()
        end
      end
    end
  end
  rows[#rows + 1] = row
  return rows
end

local function layout(body, ctype)
  lines, elements = {}, {}
  local width = W
  if ctype ~= "page" then
    for line in (body .. "\n"):gmatch("(.-)\r?\n") do
      for _, r in ipairs(wrap({ { text = line, fg = C.text } }, width)) do lines[#lines + 1] = r end
    end
    return
  end
  for line in (body .. "\n"):gmatch("(.-)\r?\n") do
    local segs
    if line:match("^%-%-%-+%s*$") then
      segs = { { text = string.rep("-", width), fg = C.rule } }
    elseif line:match("^## ") then
      segs = parseInline(line:sub(4), C.sub)
    elseif line:match("^# ") then
      segs = parseInline(line:sub(3), C.title)
    elseif line:match("^> ") then
      segs = parseInline("| " .. line:sub(3), C.quote)
    else
      segs = parseInline(line, C.text)
    end
    for _, r in ipairs(wrap(segs, width)) do lines[#lines + 1] = r end
  end
  -- элементы: ссылки, поля, кнопки с координатами
  for y, row in ipairs(lines) do
    local x = 1
    for _, seg in ipairs(row) do
      if seg.kind then
        elements[#elements + 1] = { seg = seg, y = y, x = x, w = segLen(seg) }
        if seg.kind == "field" and fields[seg.name] == nil then fields[seg.name] = "" end
      end
      x = x + segLen(seg)
    end
  end
end

-- ======================= Экран =======================

local function setColors(fg, bg)
  if color then
    term.setTextColor(fg or C.text)
    term.setBackgroundColor(bg or colors.black)
  end
end

local function drawBar()
  term.setCursorPos(1, 1)
  setColors(C.barText, C.bar)
  term.clearLine()
  local left = " < > R  "
  term.write(left)
  local shown = editingAddress and addressText or ("mine://" .. (url or ""))
  local room = W - #left - 1
  if ulen(shown) > room then shown = usub(shown, ulen(shown) - room + 1) end
  if editingAddress then setColors(colors.black, colors.white) end
  term.write(shown)
  if editingAddress then
    term.write(string.rep(" ", math.max(0, room - ulen(shown))))
  end
  setColors()
end

local function drawStatus()
  term.setCursorPos(1, H)
  setColors(C.status, colors.black)
  term.clearLine()
  local s = status
  if s == "" then
    if focus and elements[focus] then
      local e = elements[focus].seg
      s = e.kind == "field" and "Поле «" .. e.name .. "»: печатайте; Tab — дальше"
        or ("-> " .. http.resolve(url, e.target))
    else
      s = "Tab ссылки  Enter открыть  g адрес  r обновить  b назад  q выход"
    end
  end
  if ulen(s) > W then s = usub(s, 1, W) end
  term.write(s)
  setColors()
end

local function drawPage()
  local rows = H - 2
  for i = 1, rows do
    term.setCursorPos(1, i + 1)
    setColors(C.text, colors.black)
    term.clearLine()
    local row = lines[scroll + i]
    if row then
      for _, seg in ipairs(row) do
        local isFocus = focus and elements[focus] and elements[focus].seg == seg
        if seg.kind == "field" then
          local v = fields[seg.name] or ""
          local shown = v
          if shown == "" and seg.hint and seg.hint ~= "" and not isFocus then shown = seg.hint end
          if ulen(shown) > FIELD_W then shown = usub(shown, ulen(shown) - FIELD_W + 1) end
          setColors(colors.white, isFocus and C.fieldFocus or C.field)
          term.write("[" .. shown .. string.rep(" ", FIELD_W - ulen(shown)) .. "]")
        elseif seg.kind == "link" then
          setColors(isFocus and colors.black or C.link, isFocus and C.linkFocus or colors.black)
          term.write(seg.text)
        elseif seg.kind == "button" then
          setColors(isFocus and colors.black or colors.white, isFocus and colors.lime or C.button)
          term.write(seg.text)
        else
          setColors(seg.fg, colors.black)
          term.write(seg.text)
        end
      end
    end
  end
  setColors()
end

local function draw()
  drawBar()
  drawPage()
  drawStatus()
  if focus and elements[focus] and elements[focus].seg.kind == "field" then
    local e = elements[focus]
    local v = fields[e.seg.name] or ""
    local sy = e.y - scroll + 1
    if sy >= 2 and sy <= H - 1 then
      term.setCursorPos(e.x + 1 + math.min(ulen(v), FIELD_W), sy)
      term.setCursorBlink(true)
      return
    end
  end
  if editingAddress then
    term.setCursorPos(math.min(W, 9 + ulen(addressText)), 1)
    term.setCursorBlink(true)
  else
    term.setCursorBlink(false)
  end
end

-- ======================= Переходы =======================

local function maxScroll() return math.max(0, #lines - (H - 2)) end

local function open(target, params, push)
  if not target or target == "" then return end
  status = "Загрузка " .. target .. " ..."
  drawStatus()
  local body, code, ctype = http.get(target, params, function(got, total)
    if total > 1 then
      status = string.format("Загрузка %s: %d%%", target, math.floor(got * 100 / total))
      drawStatus()
    end
  end)
  if push and url then history[#history + 1] = url forward = {} end
  url = target
  scroll, focus = 0, nil
  fields = {}
  if not body then
    layout("# Не открылось\n{red}" .. tostring(code) .. "{}\n\nПроверьте адрес и сеть: {lightBlue}peers{}, {lightBlue}dns who " ..
      (http.split(target)) .. "{}", "page")
    status = ""
  else
    if ctype == "file" then
      layout("# Файл " .. target .. "\nРазмер: " .. #body .. " байт.\nСкачать: {lightBlue}get " .. target .. "{}", "page")
    else
      layout(body, ctype)
    end
    status = code == 200 and "" or ("Код ответа " .. tostring(code))
  end
  draw()
end

local function activate(i)
  local e = elements[i]
  if not e then return end
  if e.seg.kind == "link" then
    open(http.resolve(url, e.seg.target), nil, true)
  elseif e.seg.kind == "button" then
    local params = {}
    for k, v in pairs(fields) do params[k] = v end
    open(http.resolve(url, e.seg.target), params, true)
  elseif e.seg.kind == "field" then
    focus = i
    draw()
  end
end

local function ensureVisible()
  if not focus or not elements[focus] then return end
  local y = elements[focus].y
  if y <= scroll then scroll = y - 1 end
  if y > scroll + H - 2 then scroll = y - (H - 2) end
end

-- ======================= Главный цикл =======================

term.clear()
if start then
  open(start, nil, false)
else
  local ok, names = net.dns("all")
  local list = {}
  if ok then for _, n in ipairs(names) do list[#list + 1] = "* [" .. n.name .. "](mine://" .. n.name .. ")" end end
  layout("# Майннет\nВведите адрес: нажмите {yellow}g{}.\n---\n## Сайты мира\n" ..
    (#list > 0 and table.concat(list, "\n") or "Пока никто не занял имя. Своё: {lightBlue}dns register моё-имя{}, сайт: {lightBlue}httpd{}"), "page")
  url = ""
  draw()
end

while true do
  local ev, a, b, c = os.pullEventRaw()
  if ev == "terminate" then break end
  local fieldFocused = focus and elements[focus] and elements[focus].seg.kind == "field"
  if editingAddress then
    if ev == "char" then
      addressText = addressText .. a
      drawBar() draw()
    elseif ev == "paste" then
      addressText = addressText .. a:gsub("\n", "")
      draw()
    elseif ev == "key" and a == "backspace" then
      addressText = usub(addressText, 1, math.max(0, ulen(addressText) - 1))
      draw()
    elseif ev == "key" and a == "enter" then
      editingAddress = false
      open((addressText:gsub("^mine://", "")), nil, true)
    elseif ev == "key" and (a == "escape" or a == "tab") then
      editingAddress = false
      draw()
    end
  elseif ev == "char" then
    if fieldFocused then
      local name = elements[focus].seg.name
      fields[name] = (fields[name] or "") .. a
      draw()
    elseif a == "q" then break
    elseif a == "g" then editingAddress = true addressText = "" draw()
    elseif a == "r" and url and url ~= "" then open(url, nil, false)
    elseif a == "b" and #history > 0 then
      forward[#forward + 1] = url
      open(table.remove(history), nil, false)
    end
  elseif ev == "paste" and fieldFocused then
    local name = elements[focus].seg.name
    fields[name] = (fields[name] or "") .. a:gsub("\n", " ")
    draw()
  elseif ev == "key" then
    if a == "tab" then
      if #elements > 0 then
        focus = (focus or 0) % #elements + 1
        ensureVisible()
      end
      draw()
    elseif a == "enter" then
      if fieldFocused then
        -- Enter в поле — нажать первую кнопку после него
        for i = focus + 1, #elements do
          if elements[i].seg.kind == "button" then activate(i) break end
        end
      elseif focus then
        activate(focus)
      end
    elseif a == "backspace" then
      if fieldFocused then
        local name = elements[focus].seg.name
        local v = fields[name] or ""
        fields[name] = usub(v, 1, math.max(0, ulen(v) - 1))
        draw()
      elseif #history > 0 then
        forward[#forward + 1] = url
        open(table.remove(history), nil, false)
      end
    elseif a == "escape" then
      focus = nil
      draw()
    elseif a == "up" then scroll = math.max(0, scroll - 1) draw()
    elseif a == "down" then scroll = math.min(maxScroll(), scroll + 1) draw()
    elseif a == "pageUp" then scroll = math.max(0, scroll - (H - 3)) draw()
    elseif a == "pageDown" then scroll = math.min(maxScroll(), scroll + (H - 3)) draw()
    end
  elseif ev == "mouse_scroll" then
    scroll = math.max(0, math.min(maxScroll(), scroll + a * 3))
    draw()
  elseif ev == "mouse_click" then
    local x, y = b, c
    if y == 1 then
      if x == 2 and #history > 0 then
        forward[#forward + 1] = url
        open(table.remove(history), nil, false)
      elseif x == 4 and #forward > 0 then
        history[#history + 1] = url
        open(table.remove(forward), nil, false)
      elseif x == 6 and url and url ~= "" then
        open(url, nil, false)
      elseif x >= 9 then
        editingAddress = true
        addressText = url or ""
        draw()
      end
    elseif y >= 2 and y <= H - 1 then
      local row = y - 1 + scroll
      local hit = nil
      for i, e in ipairs(elements) do
        if e.y == row and x >= e.x and x < e.x + e.w then hit = i break end
      end
      if hit then
        focus = hit
        activate(hit)
      else
        focus = nil
        draw()
      end
    end
  end
end
setColors()
term.setCursorBlink(false)
term.clear()
term.setCursorPos(1, 1)
