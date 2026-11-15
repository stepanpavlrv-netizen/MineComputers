-- Wired: Калькулятор — кнопки и клавиатура, скобки, проценты, степень, история.
local G, T, wm = wired.gfx, wired.theme, wired.wm

local KEYS = {
  { "C", "(", ")", "⌫" },
  { "7", "8", "9", "÷" },
  { "4", "5", "6", "×" },
  { "1", "2", "3", "−" },
  { "0", ",", "^", "+" },
  { "%", "±", "√", "=" },
}

local function fmt(v)
  if v ~= v then return "ошибка" end
  if v == math.huge or v == -math.huge then return "деление на 0" end
  if v == math.floor(v) and math.abs(v) < 1e15 then return string.format("%d", v) end
  local s = string.format("%.10g", v)
  return (s:gsub("%.", ","))
end

local function eval(expr)
  local e = expr:gsub("×", "*"):gsub("÷", "/"):gsub("−", "-"):gsub(",", "."):gsub("√", "sqrt")
  e = e:gsub("(%d+%.?%d*)%%", "(%1/100)")
  if e:find("[^%d%.%+%-%*/%^%(%)%s%a]") then return nil, "непонятный знак" end
  for word in e:gmatch("%a+") do if word ~= "sqrt" then return nil, "непонятно: " .. word end end
  local fn = load("return " .. e, "calc", "t", { sqrt = math.sqrt })
  if not fn then return nil, "ошибка в записи" end
  local ok, v = pcall(fn)
  if not ok or type(v) ~= "number" then return nil, "ошибка" end
  return v
end

return function()
  local app = { title = "Калькулятор", icon = "calc", w = 34, h = 22, resizable = false }
  local st = { expr = "", result = nil, hist = {} }
  local win

  local function press(k)
    if k == "C" then st.expr, st.result = "", nil
    elseif k == "⌫" then st.expr = G.sub(st.expr, 1, math.max(0, G.len(st.expr) - 1))
    elseif k == "=" then
      local v, err = eval(st.expr)
      if v then
        table.insert(st.hist, 1, st.expr .. " = " .. fmt(v))
        while #st.hist > 4 do table.remove(st.hist) end
        st.expr, st.result = fmt(v):gsub("^деление на 0$", ""), nil
      else st.result = err end
    elseif k == "±" then
      if st.expr:sub(1, 1) == "-" then st.expr = st.expr:sub(2) else st.expr = "-" .. st.expr end
    elseif k == "√" then st.expr = st.expr .. "√("
    else
      if st.result and k:match("%d") then st.expr = "" end
      st.expr = st.expr .. k
      st.result = nil
    end
    wm.redraw(win)
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    c.fill(2, 1, c.w - 2, 5, T.field)
    for i, h in ipairs(st.hist) do
      if i > 2 then break end
      local s = G.cut(h, c.w - 4)
      c.text(c.w - 1 - G.len(s), 3 - i, s, T.faint, T.field)
    end
    local e = st.expr == "" and "0" or st.expr
    if G.len(e) > c.w - 4 then e = "…" .. G.sub(e, G.len(e) - c.w + 6) end
    c.text(c.w - 1 - G.len(e), 4, e, T.fg, T.field)
    local preview = st.result or ((st.expr ~= "" and eval(st.expr)) and ("= " .. fmt(eval(st.expr))) or "")
    c.text(c.w - 1 - G.len(preview), 5, preview, st.result and T.err or T.dim, T.field)
    local bw = math.floor((c.w - 2) / 4)
    for r, row in ipairs(KEYS) do
      for i, k in ipairs(row) do
        local x, y = 2 + (i - 1) * bw, 4 + r * 2 + 1
        local op = k:match("[÷×−%+=%^]")
        local bg = k == "=" and T.accent or (op and T.btnHi or T.btn)
        c.fill(x, y, bw - 1, 1, bg)
        c.text(x + math.floor((bw - 1 - G.len(k)) / 2), y, k, k == "=" and T.ink or T.fg, bg)
        c.hit(x, y, bw - 1, 1, "key", k)
      end
    end
  end

  function app.event(w, ev, a, b)
    if ev == "click" or ev == "dblclick" then press(b)
    elseif ev == "char" then
      local map = { ["*"] = "×", ["/"] = "÷", ["-"] = "−", ["."] = ",", ["="] = "=" }
      if a:match("[%d%+%(%)%^%%,]") then press(a) elseif map[a] then press(map[a]) end
    elseif ev == "paste" then st.expr = st.expr .. tostring(a) wm.redraw(w)
    elseif ev == "key" then
      if a == "enter" then press("=") elseif a == "backspace" then press("⌫") elseif a == "escape" or a == "delete" then press("C") end
    end
  end

  return app
end
