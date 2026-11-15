-- Таблицы: ячейки, формулы (=СУММ(A1:A5), =B2*3, ЕСЛИ…), несколько листов, диаграмма. Файлы .wtab.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm

local COLS, ROWS, CW = 26, 200, 11
local LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

local function colName(c) return LETTERS:sub(c, c) end
local function ref(c, r) return colName(c) .. r end
local function parseRef(s)
  local l, n = tostring(s):upper():match("^%$?([A-Z])%$?(%d+)$")
  if not l then return nil end
  return LETTERS:find(l, 1, true), tonumber(n)
end

-- ======================= формулы =======================

local function flat(args, out)
  out = out or {}
  for _, v in ipairs(args) do
    if type(v) == "table" then flat(v, out) else out[#out + 1] = v end
  end
  return out
end
local function nums(...)
  local out = {}
  for _, v in ipairs(flat({ ... })) do if type(v) == "number" then out[#out + 1] = v end end
  return out
end

local FN = {}
FN.SUM = function(...) local s = 0 for _, v in ipairs(nums(...)) do s = s + v end return s end
FN.AVERAGE = function(...) local n = nums(...) if #n == 0 then error("#ДЕЛ/0!", 0) end return FN.SUM(...) / #n end
FN.MIN = function(...) local n = nums(...) return #n > 0 and math.min(table.unpack(n)) or 0 end
FN.MAX = function(...) local n = nums(...) return #n > 0 and math.max(table.unpack(n)) or 0 end
FN.COUNT = function(...) return #nums(...) end
FN.IF = function(c, a, b) if c and c ~= 0 then return a == nil and true or a end return b == nil and false or b end
FN.ROUND = function(x, d) local m = 10 ^ (d or 0) return math.floor((tonumber(x) or 0) * m + 0.5) / m end
FN.ABS = function(x) return math.abs(tonumber(x) or 0) end
FN.SQRT = function(x) return math.sqrt(tonumber(x) or 0) end
FN.CONCAT = function(...) local o = {} for _, v in ipairs(flat({ ... })) do o[#o + 1] = tostring(v) end return table.concat(o) end
FN.LEN = function(s) return G.len(tostring(s or "")) end
local RU = { ["СУММ"] = "SUM", ["СРЗНАЧ"] = "AVERAGE", ["МИН"] = "MIN", ["МАКС"] = "MAX", ["СЧЁТ"] = "COUNT", ["СЧЕТ"] = "COUNT",
             ["ЕСЛИ"] = "IF", ["ОКРУГЛ"] = "ROUND", ["МОДУЛЬ"] = "ABS", ["КОРЕНЬ"] = "SQRT", ["СЦЕП"] = "CONCAT", ["ДЛСТР"] = "LEN" }

--- Перевести формулу в выражение Lua. Ссылки → V("A1"), диапазоны → R("A1","B5"), функции → F.SUM(...).
local function compile(src)
  local out, i, n = {}, 1, #src
  while i <= n do
    local ch = src:sub(i, i)
    local rest = src:sub(i)
    local word = rest:match("^[%a_\128-\255][%w_\128-\255]*")
    if ch:match("%s") then
      out[#out + 1] = " "
      i = i + 1
    elseif ch == '"' then
      local j = src:find('"', i + 1, true)
      if not j then error("нет закрывающей кавычки", 0) end
      out[#out + 1] = string.format("%q", src:sub(i + 1, j - 1))
      i = j + 1
    elseif rest:match("^%$?%a%$?%d+:%$?%a%$?%d+") then
      local a, b = rest:match("^(%$?%a%$?%d+):(%$?%a%$?%d+)")
      out[#out + 1] = 'R("' .. a:gsub("%$", ""):upper() .. '","' .. b:gsub("%$", ""):upper() .. '")'
      i = i + #a + 1 + #b
    elseif rest:match("^%$?%a%$?%d+") and not rest:match("^%$?%a%$?%d+[%a_(]") then
      local r = rest:match("^(%$?%a%$?%d+)")
      out[#out + 1] = 'V("' .. r:gsub("%$", ""):upper() .. '")'
      i = i + #r
    elseif word then
      local up = word:upper()
      local fname = RU[word] or RU[up] or (FN[up] and up)
      if up == "TRUE" or word == "ИСТИНА" then out[#out + 1] = "true"
      elseif up == "FALSE" or word == "ЛОЖЬ" then out[#out + 1] = "false"
      elseif fname then out[#out + 1] = "F." .. fname
      else error("неизвестно: " .. word, 0) end
      i = i + #word
    elseif rest:match("^%d") or rest:match("^%.%d") then
      local num = rest:match("^%d*%.?%d+")
      out[#out + 1] = num
      i = i + #num
    elseif rest:sub(1, 2) == "<>" then out[#out + 1] = "~=" i = i + 2
    elseif rest:sub(1, 2) == "<=" or rest:sub(1, 2) == ">=" then out[#out + 1] = rest:sub(1, 2) i = i + 2
    elseif ch == "=" then out[#out + 1] = "==" i = i + 1
    elseif ch == "&" then out[#out + 1] = ".." i = i + 1
    elseif ch == ";" then out[#out + 1] = "," i = i + 1
    elseif ch:match("[%+%-%*/%^%(%),<>]") then out[#out + 1] = ch i = i + 1
    elseif ch == "%" then out[#out + 1] = "/100" i = i + 1
    else error("непонятный знак: " .. ch, 0) end
  end
  return table.concat(out)
end

-- ======================= книга =======================

local function newSheet(name) return { name = name, cells = {} } end

local function saveBook(book, path)
  local t = { v = 1, sheets = {} }
  for _, sh in ipairs(book) do t.sheets[#t.sheets + 1] = { name = sh.name, cells = sh.cells } end
  local d = fs.getDir(path)
  if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
  local f = fs.open(path, "w")
  if not f then return false, "не открылся файл" end
  f.write(textutils.serialize(t))
  f.close()
  return true
end

local function loadBook(path)
  local f = fs.open(path, "r")
  if not f then return nil, "нет файла" end
  local text = f.readAll() or ""
  f.close()
  local fn = load("return " .. text, "book", "t", {})
  local ok, t = pcall(fn or function() end)
  if not ok or type(t) ~= "table" or type(t.sheets) ~= "table" then return nil, "файл повреждён" end
  local book = {}
  for _, s in ipairs(t.sheets) do book[#book + 1] = { name = tostring(s.name or "Лист"), cells = type(s.cells) == "table" and s.cells or {} } end
  if #book == 0 then book[1] = newSheet("Лист 1") end
  return book
end

local function fmt(v)
  if type(v) == "number" then
    if v ~= v then return "#ЧИСЛО!" end
    if v == math.huge or v == -math.huge then return "#ДЕЛ/0!" end
    if v == math.floor(v) and math.abs(v) < 1e15 then return string.format("%d", v) end
    local s = string.format("%.2f", v):gsub("0+$", ""):gsub("%.$", "")
    return (s:gsub("%.", ","))
  elseif type(v) == "boolean" then return v and "ИСТИНА" or "ЛОЖЬ"
  elseif v == nil then return "" end
  return tostring(v)
end

-- ======================= окно =======================

return function(path)
  local app = { title = "Таблицы", icon = "table", w = 104, h = 34, minW = 60, minH = 14 }
  local book = { newSheet("Лист 1") }
  local st = { path = type(path) == "string" and path or nil, sheet = 1, cc = 1, cr = 1, ac = nil, ar = nil,
               sx = 0, sy = 0, edit = nil, chart = false, dirty = false }
  local win, cache, busy
  local msg

  if st.path then
    local b, err = loadBook(st.path)
    if b then book = b else msg = "Не открылся: " .. tostring(err) end
  end

  local function sheet() return book[st.sheet] end
  local function title()
    return "Таблицы — " .. (st.path and fs.getName(st.path) or "новая книга") .. (st.dirty and " •" or "")
  end
  app.title = title()
  local function retitle() if win then wm.setTitle(win, title()) end end

  -- значение ячейки (с формулами)
  local value
  local function raw(r) return sheet().cells[r] end
  value = function(r)
    if cache[r] ~= nil then
      if cache[r] == busy then error("#ЦИКЛ!", 0) end
      return cache[r]
    end
    local s = raw(r)
    local v
    if s == nil or s == "" then v = nil
    elseif s:sub(1, 1) == "=" then
      cache[r] = busy
      local ok, res = pcall(function()
        local code = compile(s:sub(2))
        local env = { F = FN, math = math,
          V = function(x) local y = value(x) if y == nil then return 0 end return y end,
          R = function(a, b)
            local c1, r1 = parseRef(a)
            local c2, r2 = parseRef(b)
            local out = {}
            for rr = math.min(r1, r2), math.max(r1, r2) do
              for c = math.min(c1, c2), math.max(c1, c2) do out[#out + 1] = value(ref(c, rr)) or 0 end
            end
            return out
          end }
        local fn, err = load("return " .. code, "formula", "t", env)
        if not fn then error("#ОШИБКА!", 0) end
        return fn()
      end)
      if ok then v = res else
        local e = tostring(res)
        v = e:match("#[^%s]+") or "#ОШИБКА!"
      end
    else
      local num = tonumber((s:gsub(",", ".")))
      v = num or s
    end
    cache[r] = v == nil and false or v
    if v == nil then cache[r] = nil end
    return v
  end

  local function recalc() cache = {} busy = {} end
  recalc()

  local function selRect()
    local c1, r1 = st.ac or st.cc, st.ar or st.cr
    return math.min(c1, st.cc), math.min(r1, st.cr), math.max(c1, st.cc), math.max(r1, st.cr)
  end

  local function setCell(r, text)
    if text == "" then text = nil end
    sheet().cells[r] = text
    st.dirty = true
    recalc()
    retitle()
  end

  local function visible(c)
    local cols = math.max(1, math.floor((c.w - 5 - (st.chart and 40 or 0)) / (CW + 1)))
    local rows = c.h - 5
    return cols, rows
  end

  local function follow()
    local cols, rows = visible(win.c)
    if st.cc <= st.sx then st.sx = st.cc - 1 end
    if st.cc > st.sx + cols then st.sx = st.cc - cols end
    if st.cr <= st.sy then st.sy = st.cr - 1 end
    if st.cr > st.sy + rows then st.sy = st.cr - rows end
  end

  local function startEdit(text)
    st.edit = U.field(text, "fx")
    st.edit.focused = true
  end

  local function commit()
    if st.edit then
      setCell(ref(st.cc, st.cr), st.edit.text)
      st.edit = nil
    end
  end

  local function saveAs(force)
    local function go(p)
      if not p or p == "" then return end
      if not p:match("%.wtab$") then p = p .. ".wtab" end
      local ok, err = saveBook(book, p)
      if ok then st.path, st.dirty = "/" .. fs.combine(p, ""), false msg = "Сохранено: " .. st.path
      else msg = "Не сохранилось: " .. tostring(err) end
      retitle()
      wm.redraw(win)
    end
    if st.path and not force then go(st.path) return end
    wm.prompt("Сохранить таблицу", "Файл:", st.path or "/home/таблица.wtab", go)
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    local cur = ref(st.cc, st.cr)
    -- строка формул
    c.text(2, 1, " " .. cur .. " ", T.fg, T.btn)
    c.text(8, 1, "fx", T.ok)
    local fw = c.w - 44
    if st.edit then c.field(11, 1, fw, st.edit)
    else
      c.fill(11, 1, fw, 1, T.field)
      c.text(12, 1, G.cut(raw(cur) or "", fw - 2), T.fg, T.field)
      c.hit(11, 1, fw, 1, "fx")
    end
    local bx = 12 + fw
    bx = c.button(bx, 1, "▤", "chart", st.chart and "primary" or "normal") + 1
    bx = c.button(bx, 1, "Открыть", "open") + 1
    bx = c.button(bx, 1, "Сохранить", "save") + 1
    c.button(bx, 1, "Как…", "saveas")
    -- сетка
    local cols, rows = visible(c)
    local y0 = 3
    local c1, r1, c2, r2 = selRect()
    c.fill(1, y0, 4, 1, T.title)
    for i = 1, cols do
      local col = st.sx + i
      if col > COLS then break end
      local x = 5 + (i - 1) * (CW + 1)
      local insel = col >= c1 and col <= c2
      c.fill(x, y0, CW + 1, 1, insel and T.sel or T.title)
      c.text(x + math.floor(CW / 2), y0, colName(col), insel and T.fg or T.dim, insel and T.sel or T.title)
      c.hit(x, y0, CW + 1, 1, "colhead", col)
    end
    for j = 1, rows do
      local row = st.sy + j
      if row > ROWS then break end
      local y = y0 + j
      local insel = row >= r1 and row <= r2
      c.fill(1, y, 4, 1, insel and T.sel or T.title)
      c.text(4 - #tostring(row), y, tostring(row), insel and T.fg or T.dim, insel and T.sel or T.title)
      for i = 1, cols do
        local col = st.sx + i
        if col > COLS then break end
        local x = 5 + (i - 1) * (CW + 1)
        local r = ref(col, row)
        local sel = col >= c1 and col <= c2 and row >= r1 and row <= r2
        local bg = (col == st.cc and row == st.cr) and T.sel or (sel and T.hover or ((row % 2 == 0) and T.side or T.win))
        c.fill(x, y, CW, 1, bg)
        local ok, v = pcall(value, r)
        if not ok then v = tostring(v):match("#[^%s]+") or "#ОШИБКА!" end
        local s = fmt(v)
        if s ~= "" then
          local col_ = (type(v) == "string" and v:sub(1, 1) == "#") and T.err or T.fg
          if type(v) == "number" then c.text(x + CW - math.min(CW, G.len(s)), y, G.cut(s, CW), col_, bg)
          else c.text(x, y, G.cut(s, CW), col_, bg) end
        end
        c.text(x + CW, y, "│", T.line, (row % 2 == 0) and T.side or T.win)
        c.hit(x, y, CW + 1, 1, "cell", r)
      end
    end
    if c1 <= st.sx + cols and c2 > st.sx and r1 <= st.sy + rows and r2 > st.sy then
      -- рамка у текущей ячейки
      local x = 5 + (st.cc - st.sx - 1) * (CW + 1)
      local y = y0 + st.cr - st.sy
      if st.cc > st.sx and st.cc <= st.sx + cols and st.cr > st.sy and st.cr <= st.sy + rows then
        c.text(x - 1, y, "▐", T.accent, nil)
        c.text(x + CW, y, "▌", T.accent, nil)
      end
    end
    -- диаграмма
    if st.chart then
      local dx, dw = c.w - 39, 38
      c.fill(dx, y0, dw, rows + 1, T.side)
      c.text(dx + 2, y0 + 1, "Диаграмма выделенного", T.fg, T.side)
      local bars = {}
      local labelCol = c1 > 1 and c1 - 1 or nil
      for rr = r1, r2 do
        for cc = c1, c2 do
          local ok, v = pcall(value, ref(cc, rr))
          if ok and type(v) == "number" then
            local lab = labelCol and fmt(select(2, pcall(value, ref(labelCol, rr)))) or ref(cc, rr)
            bars[#bars + 1] = { v = v, l = lab }
          end
        end
      end
      if #bars == 0 then
        c.text(dx + 2, y0 + 3, "Выделите числа (Shift+стрелки", T.dim, T.side)
        c.text(dx + 2, y0 + 4, "или протяните мышью)", T.dim, T.side)
      else
        local maxv = 0
        for _, b in ipairs(bars) do maxv = math.max(maxv, math.abs(b.v)) end
        local n = math.min(#bars, math.floor((rows - 3) / 2))
        local palette = { T.accent, T.link, T.warn, T.ok, 0x6b95ff, 0xc77dff }
        for i = 1, n do
          local b = bars[i]
          local y = y0 + 2 + (i - 1) * 2
          c.text(dx + 2, y, G.pad(G.cut(b.l, 10), 10), T.dim, T.side)
          local len = maxv > 0 and math.floor(math.abs(b.v) / maxv * (dw - 22) + 0.5) or 0
          c.text(dx + 13, y, string.rep("█", len), palette[(i - 1) % #palette + 1], T.side)
          c.text(dx + 14 + len, y, fmt(b.v), T.fg, T.side)
        end
      end
    end
    -- листы
    local sx = 2
    for i, sh in ipairs(book) do
      local label = " " .. sh.name .. " "
      local act = i == st.sheet
      c.text(sx, c.h, label, act and T.fg or T.dim, act and T.sel or T.win)
      if act then c.text(sx, c.h - 1, string.rep("▁", G.len(label)), T.accent) end
      c.hit(sx, c.h, G.len(label), 1, "sheet", i)
      sx = sx + G.len(label) + 1
    end
    c.text(sx, c.h, " + ", T.dim, T.btn)
    c.hit(sx, c.h, 3, 1, "addsheet")
    -- сумма выделенного
    local sum, cnt = 0, 0
    if c1 ~= c2 or r1 ~= r2 then
      for rr = r1, r2 do for cc = c1, c2 do
        local ok, v = pcall(value, ref(cc, rr))
        if ok and type(v) == "number" then sum, cnt = sum + v, cnt + 1 end
      end end
    end
    local info = msg or (cnt > 0 and ("Сумма " .. fmt(sum) .. " · среднее " .. fmt(sum / cnt) .. " · чисел " .. cnt) or "")
    c.text(c.w - G.len(info) - 1, c.h, info, T.faint)
  end

  local function move(dc, dr, shift)
    commit()
    if shift then if not st.ac then st.ac, st.ar = st.cc, st.cr end else st.ac, st.ar = nil, nil end
    st.cc = math.max(1, math.min(COLS, st.cc + dc))
    st.cr = math.max(1, math.min(ROWS, st.cr + dr))
    follow()
  end

  function app.event(w, ev, a, b, x, y)
    msg = nil
    if ev == "click" or ev == "dblclick" then
      if a == "cell" then
        commit()
        local c_, r_ = parseRef(b)
        if ev == "dblclick" then
          st.cc, st.cr, st.ac, st.ar = c_, r_, nil, nil
          startEdit(raw(b) or "")
        else
          st.cc, st.cr, st.ac, st.ar = c_, r_, c_, r_
          st.dragging = true
        end
      elseif a == "fx" then
        if not st.edit then startEdit(raw(ref(st.cc, st.cr)) or "") end
      elseif a == "colhead" then
        commit()
        st.ac, st.ar, st.cc, st.cr = b, 1, b, ROWS
      elseif a == "chart" then st.chart = not st.chart
      elseif a == "save" then commit() saveAs(false)
      elseif a == "saveas" then commit() saveAs(true)
      elseif a == "open" then
        wm.prompt("Открыть таблицу", "Файл:", st.path or "/home/", function(p)
          if p and p ~= "" then
            local bk, err = loadBook(p)
            if bk then book, st.path, st.sheet, st.dirty = bk, "/" .. fs.combine(p, ""), 1, false recalc()
            else msg = "Не открылась: " .. tostring(err) end
            retitle()
            wm.redraw(w)
          end
        end)
      elseif a == "sheet" then
        commit()
        if ev == "dblclick" then
          wm.prompt("Переименовать лист", "Имя листа:", book[b].name, function(v)
            if v and v ~= "" then book[b].name = v st.dirty = true retitle() wm.redraw(w) end
          end)
        end
        st.sheet = b
        recalc()
      elseif a == "addsheet" then
        commit()
        book[#book + 1] = newSheet("Лист " .. (#book + 1))
        st.sheet = #book
        st.dirty = true
        recalc()
        retitle()
      end
      wm.redraw(w)
    elseif ev == "mouse_drag" and st.dragging then
      local cols = visible(w.c)
      local i = math.floor((b - 5) / (CW + 1)) + 1
      local j = x - 3
      if i >= 1 and i <= cols and j >= 1 then
        st.cc = math.max(1, math.min(COLS, st.sx + i))
        st.cr = math.max(1, math.min(ROWS, st.sy + j))
        wm.redraw(w)
      end
    elseif ev == "mouse_up" then
      st.dragging = false
      if st.ac == st.cc and st.ar == st.cr then st.ac, st.ar = nil, nil end
    elseif ev == "scroll" then
      st.sy = math.max(0, math.min(ROWS - 1, st.sy + a * 3))
      wm.redraw(w)
    elseif ev == "key" then
      local k, ctrl, shift = a, b, x
      if ctrl and k == "s" then commit() saveAs(false) return end
      if st.edit then
        if k == "escape" then st.edit = nil
        elseif k == "enter" then commit() move(0, 1)
        elseif k == "tab" then commit() move(1, 0)
        else U.edit(st.edit, ev, k) end
        wm.redraw(w)
        return
      end
      if k == "up" then move(0, -1, shift)
      elseif k == "down" then move(0, 1, shift)
      elseif k == "left" then move(-1, 0, shift)
      elseif k == "right" or k == "tab" then move(1, 0, shift)
      elseif k == "enter" or k == "f2" then startEdit(raw(ref(st.cc, st.cr)) or "")
      elseif k == "delete" or k == "backspace" then
        local c1, r1, c2, r2 = selRect()
        for rr = r1, r2 do for cc = c1, c2 do sheet().cells[ref(cc, rr)] = nil end end
        st.dirty = true
        recalc()
        retitle()
      elseif k == "pageDown" or k == "pagedown" then move(0, w.bh - 6)
      elseif k == "pageUp" or k == "pageup" then move(0, -(w.bh - 6))
      else return end
      wm.redraw(w)
    elseif ev == "char" or ev == "paste" then
      if not st.edit then startEdit("") end
      U.edit(st.edit, ev, a)
      wm.redraw(w)
    elseif ev == "resize" or ev == "focus" then
      wm.redraw(w)
    end
  end

  function app.close(w)
    if st.dirty and not st.asked then
      st.asked = true
      wm.confirm("Таблица не сохранена", "Закрыть без сохранения?", function(ok)
        if ok then wm.close(w, true) else st.asked = false end
      end, "Закрыть")
      return false
    end
  end

  return app
end
