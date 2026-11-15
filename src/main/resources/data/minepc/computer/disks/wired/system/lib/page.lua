-- Wired: страницы Майннета (.page) — разбор разметки и рисование в окне (для Браузера и Сайтостроителя).
-- Разметка:  # Заголовок   ## Подзаголовок   ---  черта   > цитата   ![подпись](картинка.pix)
--            {red}цвет{}   [текст](адрес)   [?поле]   [?поле:подсказка]   [!Кнопка](адрес)
local G = wired.gfx
local P = {}

P.BG = 0x101018
P.C = { text = 0xe8e4f0, h1 = 0xffc857, h2 = 0xff8a3d, quote = 0x9a94ad, link = 0x4fd1e8, button = 0x2f9a5a,
        rule = 0x3a3550, field = 0x1f1b2c, fieldFocus = 0x2d3f73, image = 0x1a1626 }
P.NAMES = { white = 0xf0f0f0, orange = 0xf2b233, magenta = 0xe57fd8, lightBlue = 0x99b2f2, yellow = 0xdede6c,
            lime = 0x7fcc19, pink = 0xf2b2cc, gray = 0x6b6b6b, lightGray = 0x999999, cyan = 0x4c99b2,
            purple = 0xb266e5, blue = 0x3366cc, brown = 0x7f664c, green = 0x57a64e, red = 0xcc4c4c, black = 0x111111 }
P.FIELD_W = 18

--- Куски одной строки: { text, fg } или { kind = "link"/"field"/"button", ... }.
function P.inline(s, base)
  local out, cur, buf, i = {}, base, "", 1
  local function flush() if buf ~= "" then out[#out + 1] = { text = buf, fg = cur } buf = "" end end
  while i <= #s do
    local c = s:sub(i, i)
    if c == "{" then
      local name, e = s:match("^{(%a*)}()", i)
      if e then
        flush()
        cur = name == "" and base or (P.NAMES[name] or base)
        i = e
      else buf = buf .. c i = i + 1 end
    elseif c == "[" then
      local fname, hint, e1 = s:match("^%[%?([%w_]+):?([^%]]*)%]()", i)
      local btext, btarget, e2 = s:match("^%[!([^%]]+)%]%(([^%)]*)%)()", i)
      local ltext, ltarget, e3 = s:match("^%[([^%]]+)%]%(([^%)]*)%)()", i)
      if fname then flush() out[#out + 1] = { kind = "field", name = fname, hint = hint } i = e1
      elseif btext then flush() out[#out + 1] = { kind = "button", text = " " .. btext .. " ", target = btarget } i = e2
      elseif ltext then flush() out[#out + 1] = { kind = "link", text = ltext, target = ltarget } i = e3
      else buf = buf .. c i = i + 1 end
    else
      buf = buf .. c
      i = i + 1
    end
  end
  flush()
  return out
end

--- Блоки страницы (по строкам): { kind, segs, raw, line, src, alt }.
function P.parse(text, plain)
  local blocks = {}
  local n = 0
  for line in (tostring(text) .. "\n"):gmatch("(.-)\r?\n") do
    n = n + 1
    local b = { raw = line, line = n }
    if plain then b.kind, b.segs = "text", { { text = line, fg = P.C.text } }
    elseif line:match("^%-%-%-+%s*$") then b.kind = "rule"
    elseif line:match("^## ") then b.kind, b.segs = "h2", P.inline(line:sub(4), P.C.h2)
    elseif line:match("^# ") then b.kind, b.segs = "h1", P.inline(line:sub(3), P.C.h1)
    elseif line:match("^> ") then b.kind, b.segs = "quote", P.inline(line:sub(3), P.C.quote)
    elseif line:match("^!%[[^%]]*%]%([^%)]+%)%s*$") then
      b.kind = "image"
      b.alt, b.src = line:match("^!%[([^%]]*)%]%(([^%)]+)%)")
    else b.kind, b.segs = "text", P.inline(line, P.C.text) end
    blocks[n] = b
  end
  if #blocks > 1 and blocks[#blocks].raw == "" then blocks[#blocks] = nil end
  return blocks
end

local function segLen(s)
  if s.kind == "field" then return P.FIELD_W + 2 end
  return G.len(s.text)
end

--- Экранные строки: { segs = { {x, text, fg, kind, ...} }, block = номер, image = {src, row}, deco }.
function P.layout(blocks, width, images)
  local rows = {}
  for bi, b in ipairs(blocks) do
    if b.kind == "rule" then
      rows[#rows + 1] = { block = bi, deco = "rule" }
    elseif b.kind == "image" then
      local img = images and images(b.src)
      local h = img and math.ceil(img.h / 2) or 3
      for r = 1, h do rows[#rows + 1] = { block = bi, image = { src = b.src, row = r, alt = b.alt, img = img } } end
    else
      local row, used = { block = bi, segs = {} }, 0
      local pre = b.kind == "quote" and 2 or 0
      if pre > 0 then row.segs[1] = { x = 1, text = "│ ", fg = P.C.rule } used = pre end
      local function newRow()
        rows[#rows + 1] = row
        row, used = { block = bi, segs = {} }, pre
        if pre > 0 then row.segs[1] = { x = 1, text = "  ", fg = P.C.quote } end
      end
      for _, s in ipairs(b.segs) do
        if s.kind then
          local n = segLen(s)
          if used > pre and used + n > width then newRow() end
          local e = {}
          for k, v in pairs(s) do e[k] = v end
          e.x, e.block = used + 1, bi
          row.segs[#row.segs + 1] = e
          used = used + n
        else
          local text = s.text
          while G.len(text) > 0 do
            local room = width - used
            if room <= 0 then newRow() room = width - used end
            if G.len(text) <= room then
              row.segs[#row.segs + 1] = { x = used + 1, text = text, fg = s.fg }
              used = used + G.len(text)
              text = ""
            else
              local piece = G.sub(text, 1, room)
              local cut = piece:match("^.*() ")
              if cut and cut > 1 then piece = piece:sub(1, cut - 1) end
              row.segs[#row.segs + 1] = { x = used + 1, text = piece, fg = s.fg }
              text = G.sub(text, G.len(piece) + 1):gsub("^ ", "")
              newRow()
            end
          end
        end
      end
      rows[#rows + 1] = row
    end
  end
  return rows
end

--- Картинка .pix: первая строка «PIX1 ширина высота», дальше строки по ширина×6 шестнадцатеричных цифр ("------" — прозрачно).
function P.decodePix(data)
  local w, h, rest = tostring(data):match("^PIX1%s+(%d+)%s+(%d+)%s*\n(.*)$")
  if not w then return nil end
  w, h = tonumber(w), tonumber(h)
  if w < 1 or h < 1 or w > 320 or h > 400 then return nil end
  local px, y = {}, 0
  for line in (rest .. "\n"):gmatch("(.-)\r?\n") do
    if y >= h then break end
    for x = 0, w - 1 do
      local hex = line:sub(x * 6 + 1, x * 6 + 6)
      px[y * w + x + 1] = tonumber(hex, 16)
    end
    y = y + 1
  end
  return { w = w, h = h, px = px }
end

--- Нарисовать строки rows (с top) в окне c: x, y, w, h — область. opts: fields, focus, hover, bg.
function P.draw(c, rows, x, y, w, h, top, opts)
  opts = opts or {}
  local bg = opts.bg or P.BG
  c.fill(x, y, w, h, bg)
  for r = 1, h do
    local row = rows[top + r]
    if not row then break end
    local yy = y + r - 1
    local rbg = bg
    if opts.sel and opts.sel == row.block then
      rbg = opts.selBg or 0x2a2440
      c.fill(x, yy, w, 1, rbg)
      if opts.selMark then c.text(x - 1, yy, "▌", opts.selMark, bg) end
    end
    local bg = rbg
    if row.deco == "rule" then
      c.text(x, yy, string.rep("─", w), P.C.rule, bg)
      c.hit(x, yy, w, 1, "pblock", row.block)
    elseif row.image then
      local im = row.image
      local img = im.img
      if img then
        local py = (im.row - 1) * 2
        for i = 0, math.min(img.w, w) - 1 do
          local t = img.px[py * img.w + i + 1]
          local b = img.px[(py + 1) * img.w + i + 1] or t
          if t or b then c.half(x + i, yy, 1, t or bg, b or bg) end
        end
      elseif im.row == 2 then
        c.text(x, yy, G.cut("▦ " .. (im.alt ~= "" and im.alt or im.src), w), P.C.quote, P.C.image)
      end
      if im.row == 1 then c.hit(x, yy, w, 1, "pblock", row.block) end
    else
      c.hit(x, yy, w, 1, "pblock", row.block)   -- сначала строка целиком: ссылки и поля поверх неё
      for _, s in ipairs(row.segs) do
        local sx = x + s.x - 1
        if s.kind == "link" then
          local hot = opts.focus == s
          c.text(sx, yy, s.text, hot and P.C.h1 or P.C.link, bg)
          c.hit(sx, yy, G.len(s.text), 1, "plink", s)
        elseif s.kind == "button" then
          c.text(sx, yy, s.text, 0xffffff, opts.focus == s and 0x3fbf6a or P.C.button)
          c.hit(sx, yy, G.len(s.text), 1, "pbutton", s)
        elseif s.kind == "field" then
          local f = opts.fields and opts.fields[s.name] or ""
          local foc = opts.focus == s
          local fb = foc and P.C.fieldFocus or P.C.field
          c.text(sx, yy, " ", P.C.text, bg)
          c.fill(sx + 1, yy, P.FIELD_W, 1, fb)
          local shown = f ~= "" and f or (foc and "" or s.hint or "")
          if G.len(shown) > P.FIELD_W - 1 then shown = G.sub(shown, G.len(shown) - P.FIELD_W + 2) end
          c.text(sx + 1, yy, shown, f ~= "" and P.C.text or P.C.quote, fb)
          if foc then c.text(sx + 1 + math.min(G.len(f), P.FIELD_W - 1), yy, " ", fb, 0xe8e4f0) end
          c.hit(sx, yy, P.FIELD_W + 2, 1, "pfield", s)
        else
          c.text(sx, yy, s.text, s.fg or P.C.text, bg)
        end
      end
    end
  end
end

return P
