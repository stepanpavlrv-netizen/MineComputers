-- Документы: текст со стилями абзацев (заголовки, список, цитата), цветом и выделением. Файлы .wdoc.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm

local PAPER, INK, INK2 = 0xf3efe6, 0x2a2430, 0x6d6577
local STYLES = {
  { id = "p", name = "Абзац" }, { id = "h1", name = "Заг 1" }, { id = "h2", name = "Заг 2" },
  { id = "li", name = "• Список" }, { id = "q", name = "❝ Цитата" },
}
local COLORS = { { nil, "■", INK }, { 0xc2253a, "■", 0xc2253a }, { 0x2f5fbf, "■", 0x2f5fbf }, { 0x2f8a4a, "■", 0x2f8a4a } }
local HL = 0xffe27a

-- ======================= документ =======================

local function para(style, text)
  local p = { s = style or "p", ch = {}, fg = {}, hl = {} }
  for i = 1, G.len(text or "") do p.ch[i] = G.sub(text, i, i) end
  return p
end

local function ptext(p) return table.concat(p.ch) end

-- вставка и удаление символа (цвета и выделение — разреженные массивы, table.insert для них не годится)
local function insAt(p, i, ch, fg, hl)
  for k = #p.ch, i, -1 do p.ch[k + 1], p.fg[k + 1], p.hl[k + 1] = p.ch[k], p.fg[k], p.hl[k] end
  p.ch[i], p.fg[i], p.hl[i] = ch, fg, hl
end
local function delAt(p, i)
  local n = #p.ch
  for k = i, n - 1 do p.ch[k], p.fg[k], p.hl[k] = p.ch[k + 1], p.fg[k + 1], p.hl[k + 1] end
  p.ch[n], p.fg[n], p.hl[n] = nil, nil, nil
end

local function runs(arr, n)
  local out = {}
  local i = 1
  while i <= n do
    local v = arr[i]
    if v then
      local j = i
      while j < n and arr[j + 1] == v do j = j + 1 end
      out[#out + 1] = { i, j, v }
      i = j + 1
    else
      i = i + 1
    end
  end
  return out
end

local function save(doc, path)
  local t = { v = 1, paras = {} }
  for _, p in ipairs(doc) do
    t.paras[#t.paras + 1] = { s = p.s, t = ptext(p), f = runs(p.fg, #p.ch), h = runs(p.hl, #p.ch) }
  end
  local d = fs.getDir(path)
  if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
  local f = fs.open(path, "w")
  if not f then return false, "не открылся файл" end
  f.write(textutils.serialize(t))
  f.close()
  return true
end

local function load(path)
  local f = fs.open(path, "r")
  if not f then return nil, "нет файла" end
  local text = f.readAll() or ""
  f.close()
  if not path:match("%.wdoc$") then
    local doc = {}
    for line in (text .. "\n"):gmatch("(.-)\n") do doc[#doc + 1] = para("p", line) end
    if #doc > 1 and #doc[#doc].ch == 0 then table.remove(doc) end
    return doc
  end
  local fn = _G.load("return " .. text, "doc", "t", {})
  local ok, t = pcall(fn or function() end)
  if not ok or type(t) ~= "table" or type(t.paras) ~= "table" then return nil, "файл повреждён" end
  local doc = {}
  for _, r in ipairs(t.paras) do
    local p = para(r.s, r.t)
    for _, x in ipairs(r.f or {}) do for i = x[1], x[2] do p.fg[i] = x[3] end end
    for _, x in ipairs(r.h or {}) do for i = x[1], x[2] do p.hl[i] = x[3] end end
    doc[#doc + 1] = p
  end
  if #doc == 0 then doc[1] = para("p", "") end
  return doc
end

-- ======================= окно =======================

return function(path)
  local app = { title = "Документы", icon = "doc", w = 100, h = 34, minW = 64, minH = 16 }
  local doc = { para("h1", "Новый документ"), para("p", "") }
  local st = { path = type(path) == "string" and path or nil, cp = 2, ci = 0, ap = nil, ai = nil, top = 0, dirty = false }
  local win, lines, PW, PX
  local msg

  if st.path then
    local d, err = load(st.path)
    if d then doc = d st.cp, st.ci = 1, 0 else msg = "Не открылся: " .. tostring(err) end
  end

  local function title()
    local name = st.path and fs.getName(st.path) or "новый документ"
    return "Документы — " .. name .. (st.dirty and " •" or "")
  end
  app.title = title()

  local function retitle() if win then wm.setTitle(win, title()) end end

  -- Разметка на экранные строки: { p = номер абзаца, a = первый символ, b = последний, deco = "rule"|nil }
  local function prefix(p) return p.s == "li" and "• " or (p.s == "q" and "│ " or "") end
  local function layout(width)
    lines = {}
    for pi, p in ipairs(doc) do
      local pre = G.len(prefix(p))
      local w = math.max(8, width - pre)
      local n = #p.ch
      if n == 0 then lines[#lines + 1] = { p = pi, a = 1, b = 0 } end
      local a = 1
      while a <= n do
        local b = math.min(n, a + w - 1)
        if b < n then
          local cut
          for k = b, a, -1 do if p.ch[k] == " " then cut = k break end end
          if cut and cut > a then b = cut end
        end
        lines[#lines + 1] = { p = pi, a = a, b = b }
        a = b + 1
      end
      if p.s == "h1" then lines[#lines + 1] = { p = pi, deco = "rule", a = 1, b = 0 } end
    end
  end

  local function cursorLine()
    for li, l in ipairs(lines) do
      if not l.deco and l.p == st.cp then
        local nxt = lines[li + 1]
        local last = not nxt or nxt.p ~= st.cp or nxt.deco
        if (st.ci >= l.a - 1 and st.ci <= l.b - 1) or (st.ci >= l.a - 1 and last) or (l.b < l.a and st.ci == 0) then
          return li, st.ci - (l.a - 1)
        end
      end
    end
    return 1, 0
  end

  local function selRange()
    if not st.ap then return nil end
    local a1, a2, b1, b2 = st.ap, st.ai, st.cp, st.ci
    if a1 > b1 or (a1 == b1 and a2 > b2) then a1, a2, b1, b2 = b1, b2, a1, a2 end
    if a1 == b1 and a2 == b2 then return nil end
    return a1, a2, b1, b2
  end

  local function inSel(pi, i)
    local a1, a2, b1, b2 = selRange()
    if not a1 then return false end
    if pi < a1 or pi > b1 then return false end
    if pi == a1 and i <= a2 then return false end
    if pi == b1 and i > b2 then return false end
    return true
  end

  local function changed() st.dirty = true retitle() wm.redraw(win) end

  local function deleteSel()
    local a1, a2, b1, b2 = selRange()
    if not a1 then return false end
    local A, B = doc[a1], doc[b1]
    local keep = { ch = {}, fg = {}, hl = {} }
    for i = 1, a2 do keep.ch[i], keep.fg[i], keep.hl[i] = A.ch[i], A.fg[i], A.hl[i] end
    for i = b2 + 1, #B.ch do
      local k = #keep.ch + 1
      keep.ch[k], keep.fg[k], keep.hl[k] = B.ch[i], B.fg[i], B.hl[i]
    end
    A.ch, A.fg, A.hl = keep.ch, keep.fg, keep.hl
    for _ = a1 + 1, b1 do table.remove(doc, a1 + 1) end
    st.cp, st.ci, st.ap, st.ai = a1, a2, nil, nil
    return true
  end

  local function insertText(text)
    deleteSel()
    local p = doc[st.cp]
    for ch in text:gmatch("[\1-\127\194-\244][\128-\191]*") do
      if ch == "\n" then
        local np = para((p.s == "li") and "li" or "p", "")
        for i = st.ci + 1, #p.ch do
          local k = #np.ch + 1
          np.ch[k], np.fg[k], np.hl[k] = p.ch[i], p.fg[i], p.hl[i]
        end
        for i = #p.ch, st.ci + 1, -1 do p.ch[i], p.fg[i], p.hl[i] = nil, nil, nil end
        table.insert(doc, st.cp + 1, np)
        st.cp, st.ci, p = st.cp + 1, 0, np
      elseif ch ~= "\r" then
        insAt(p, st.ci + 1, ch, st.ci > 0 and p.fg[st.ci] or nil, false)
        st.ci = st.ci + 1
      end
    end
  end

  local function eachSel(fn)
    local a1, a2, b1, b2 = selRange()
    if not a1 then return false end
    for pi = a1, b1 do
      local p = doc[pi]
      local i1 = pi == a1 and a2 + 1 or 1
      local i2 = pi == b1 and b2 or #p.ch
      for i = i1, i2 do fn(p, i) end
    end
    return true
  end

  local function setStyle(s)
    local a1, _, b1 = selRange()
    a1, b1 = a1 or st.cp, b1 or st.cp
    for pi = a1, b1 do doc[pi].s = s end
    changed()
  end

  local function saveAs(force)
    local function go(p)
      if not p or p == "" then return end
      if not p:match("%.wdoc$") then p = p .. ".wdoc" end
      local ok, err = save(doc, p)
      if ok then st.path, st.dirty = "/" .. fs.combine(p, ""), false msg = "Сохранено: " .. st.path
      else msg = "Не сохранилось: " .. tostring(err) end
      retitle()
      wm.redraw(win)
    end
    if st.path and not force then go(st.path) return end
    wm.prompt("Сохранить документ", "Файл:", st.path or "/home/документ.wdoc", go)
  end

  local function words()
    local n = 0
    for _, p in ipairs(doc) do for _ in ptext(p):gmatch("%S+") do n = n + 1 end end
    return n
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    -- панель
    local x = 2
    local cur = doc[st.cp].s
    for _, s in ipairs(STYLES) do
      x = c.button(x, 1, s.name, "style", s.id == cur and "primary" or "normal", s.id) + 1
    end
    x = x + 1
    for i, col in ipairs(COLORS) do
      c.text(x, 1, " " .. col[2] .. " ", col[3], T.btn)
      c.hit(x, 1, 3, 1, "color", i)
      x = x + 4
    end
    c.text(x, 1, " ▌выделить ", INK, HL)
    c.hit(x, 1, 11, 1, "hl")
    x = x + 12
    x = c.button(x, 1, "✕", "clear") + 2
    local fx = c.w - 31
    if fx > x then
      fx = c.button(fx, 1, "Открыть", "open") + 1
      fx = c.button(fx, 1, "Сохранить", "save") + 1
      c.button(fx, 1, "Как…", "saveas")
    end
    -- страница
    c.fill(1, 2, c.w, c.h - 2, 0x0b0912)
    PW = math.min(c.w - 4, 84)
    PX = math.floor((c.w - PW) / 2) + 1
    c.fill(PX, 2, PW, c.h - 2, PAPER)
    local inner = PW - 8
    layout(inner)
    local rows = c.h - 4
    local cl = cursorLine()
    if cl <= st.top then st.top = cl - 1 end
    if cl > st.top + rows then st.top = cl - rows end
    st.top = math.max(0, st.top)
    for r = 1, rows do
      local l = lines[st.top + r]
      if not l then break end
      local y = 2 + r
      local p = doc[l.p]
      local x0 = PX + 4
      if l.deco == "rule" then
        c.text(x0, y, string.rep("═", math.min(inner, math.max(4, #p.ch))), 0xc2253a, PAPER)
      else
        local pre = prefix(p)
        local base = INK
        if p.s == "h1" then base = 0xc2253a elseif p.s == "h2" then base = 0x2f5fbf elseif p.s == "q" then base = INK2 end
        if l.a == 1 and pre ~= "" then c.text(x0, y, pre, p.s == "q" and 0xb8b0c4 or base, PAPER) end
        local px = x0 + G.len(pre)
        for i = l.a, l.b do
          local sel = inSel(l.p, i)
          local bg = sel and 0xc9d8f5 or (p.hl[i] or PAPER)
          c.text(px + i - l.a, y, p.ch[i], p.fg[i] or base, bg)
        end
        c.hit(PX, y, PW, 1, "line", st.top + r)
      end
    end
    -- курсор
    local li, col = cursorLine()
    if wm.focus == w and li > st.top and li <= st.top + rows then
      local l = lines[li]
      local p = doc[l.p]
      local cx = PX + 4 + G.len(prefix(p)) + col
      local ch = p.ch[st.ci + 1] or " "
      c.text(cx, 2 + li - st.top, ch, PAPER, T.accent)
    end
    c.text(2, c.h, G.cut(msg or (words() .. " слов · абзац " .. st.cp .. " из " .. #doc), c.w - 3), T.faint)
  end

  local function moveTo(li, col)
    local l = lines[li]
    if not l then return end
    if l.deco then l = lines[li - 1] or l end
    st.cp = l.p
    local maxc = math.max(0, l.b - l.a + 1)
    local nxt = lines[li + 1]
    if nxt and nxt.p == l.p and not nxt.deco and col >= maxc then col = maxc - 1 end
    st.ci = (l.a - 1) + math.max(0, math.min(col, maxc))
  end

  local function colAt(li, lx)
    local l = lines[li]
    if not l or l.deco then return 0 end
    return lx - (PX + 4 + G.len(prefix(doc[l.p])))
  end

  function app.event(w, ev, a, b, x, y)
    msg = nil
    if ev == "click" or ev == "dblclick" then
      if a == "style" then setStyle(b) return
      elseif a == "color" then
        if eachSel(function(p, i) p.fg[i] = COLORS[b][1] end) then changed() end
        return
      elseif a == "hl" then
        local all = true
        eachSel(function(p, i) if not p.hl[i] then all = false end end)
        if eachSel(function(p, i) p.hl[i] = (not all) and HL or false end) then changed() end
        return
      elseif a == "clear" then
        if eachSel(function(p, i) p.fg[i], p.hl[i] = nil, false end) then changed() end
        return
      elseif a == "save" then saveAs(false) return
      elseif a == "saveas" then saveAs(true) return
      elseif a == "open" then
        wm.prompt("Открыть документ", "Файл:", st.path or "/home/", function(p)
          if p and p ~= "" then
            local d, err = load(p)
            if d then doc, st.path, st.cp, st.ci, st.ap, st.top, st.dirty = d, "/" .. fs.combine(p, ""), 1, 0, nil, 0, false
            else msg = "Не открылся: " .. tostring(err) end
            retitle()
            wm.redraw(w)
          end
        end)
        return
      elseif a == "line" then
        moveTo(b, colAt(b, x))
        st.ap, st.ai = st.cp, st.ci
        st.drag = true
        if ev == "dblclick" then
          local p = doc[st.cp]
          local i1, i2 = st.ci, st.ci
          while i1 > 0 and p.ch[i1] ~= " " do i1 = i1 - 1 end
          while i2 < #p.ch and p.ch[i2 + 1] ~= " " do i2 = i2 + 1 end
          st.ap, st.ai, st.ci = st.cp, i1, i2
        end
      end
      wm.redraw(w)
    elseif ev == "mouse_drag" and st.drag then
      local li = st.top + (x - 2)
      if lines[li] and not lines[li].deco then moveTo(li, colAt(li, b)) wm.redraw(w) end
    elseif ev == "mouse_up" then
      st.drag = false
    elseif ev == "scroll" then
      st.top = math.max(0, math.min(#lines - 3, st.top + a * 2))
      -- курсор следует за экраном, чтобы прокрутка не возвращалась
      local li = cursorLine()
      if li <= st.top then moveTo(st.top + 1, 0) elseif li > st.top + w.bh - 4 then moveTo(st.top + w.bh - 4, 0) end
      wm.redraw(w)
    elseif ev == "char" or ev == "paste" then
      insertText(tostring(a))
      changed()
    elseif ev == "key" then
      local shift, ctrl = x, b
      local k = a
      if ctrl and k == "s" then saveAs(false) return end
      if ctrl and k == "a" then
        st.ap, st.ai = 1, 0
        st.cp, st.ci = #doc, #doc[#doc].ch
        wm.redraw(w)
        return
      end
      local p = doc[st.cp]
      local moving = k == "left" or k == "right" or k == "up" or k == "down" or k == "home" or k == "end"
      if moving then
        if shift and not st.ap then st.ap, st.ai = st.cp, st.ci elseif not shift then st.ap = nil end
      end
      if k == "enter" then insertText("\n") changed() return
      elseif k == "backspace" then
        if not deleteSel() then
          if st.ci > 0 then
            delAt(p, st.ci)
            st.ci = st.ci - 1
          elseif st.cp > 1 then
            local prev = doc[st.cp - 1]
            local n = #prev.ch
            for i = 1, #p.ch do prev.ch[n + i], prev.fg[n + i], prev.hl[n + i] = p.ch[i], p.fg[i], p.hl[i] end
            table.remove(doc, st.cp)
            st.cp, st.ci = st.cp - 1, n
          elseif p.s ~= "p" then p.s = "p" end
        end
        changed()
        return
      elseif k == "delete" then
        if not deleteSel() then
          if st.ci < #p.ch then
            delAt(p, st.ci + 1)
          elseif st.cp < #doc then
            local nx = doc[st.cp + 1]
            local n = #p.ch
            for i = 1, #nx.ch do p.ch[n + i], p.fg[n + i], p.hl[n + i] = nx.ch[i], nx.fg[i], nx.hl[i] end
            table.remove(doc, st.cp + 1)
          end
        end
        changed()
        return
      elseif k == "left" then
        if st.ci > 0 then st.ci = st.ci - 1 elseif st.cp > 1 then st.cp = st.cp - 1 st.ci = #doc[st.cp].ch end
      elseif k == "right" then
        if st.ci < #p.ch then st.ci = st.ci + 1 elseif st.cp < #doc then st.cp, st.ci = st.cp + 1, 0 end
      elseif k == "up" or k == "down" then
        local li, col = cursorLine()
        local d = k == "up" and -1 or 1
        local t = li + d
        if lines[t] and lines[t].deco then t = t + d end
        if lines[t] then moveTo(t, col) end
      elseif k == "home" then
        local li = cursorLine()
        st.ci = lines[li].a - 1
      elseif k == "end" then
        local li = cursorLine()
        local l = lines[li]
        local nxt = lines[li + 1]
        st.ci = (nxt and nxt.p == l.p and not nxt.deco) and (l.b - 1) or l.b
      elseif k == "pageDown" or k == "pagedown" then
        local li, col = cursorLine() moveTo(math.min(#lines, li + w.bh - 5), col)
      elseif k == "pageUp" or k == "pageup" then
        local li, col = cursorLine() moveTo(math.max(1, li - w.bh + 5), col)
      else
        return
      end
      wm.redraw(w)
    elseif ev == "resize" or ev == "focus" or ev == "blur" then
      wm.redraw(w)
    end
  end

  function app.close(w)
    if st.dirty and not st.asked then
      st.asked = true
      wm.confirm("Документ не сохранён", "Закрыть без сохранения?", function(ok)
        if ok then wm.close(w, true) else st.asked = false end
      end, "Закрыть")
      return false
    end
  end

  return app
end
