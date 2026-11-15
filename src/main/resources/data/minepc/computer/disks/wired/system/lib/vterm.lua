-- Wired: окно-терминал. Программа командной строки работает в сопрограмме, а её term пишет в окно.
local G = wired.gfx
local T = wired.theme
local wm = wired.wm
local floor, max, min = math.floor, math.max, math.min
local V = {}

local realTerm = term
local function palette(i)
  local ok, c = pcall(realTerm.getPaletteColor, i)
  return ok and c or 0xffffff
end

--- Виртуальный экран w × h: строки символов и цветов.
local function screen(w, h)
  local s = { w = w, h = h, cx = 1, cy = 1, fg = palette(0), bg = 0x000000, fgi = 0, bgi = 15, blink = false, rows = {} }
  local function blank() local r = { c = {}, f = {}, b = {} } for x = 1, s.w do r.c[x], r.f[x], r.b[x] = " ", s.fg, s.bg end return r end
  for y = 1, h do s.rows[y] = blank() end
  s.blank = blank
  return s
end

local function api(s, onChange)
  local t = {}
  local function changed(y1, y2) onChange(y1, y2 or y1) end
  function t.write(text)
    text = tostring(text)
    local y = s.cy
    if y < 1 or y > s.h then s.cx = s.cx + G.len(text) return end
    local r = s.rows[y]
    for i = 1, G.len(text) do
      local x = s.cx + i - 1
      if x >= 1 and x <= s.w then r.c[x], r.f[x], r.b[x] = G.sub(text, i, i), s.fg, s.bg end
    end
    s.cx = s.cx + G.len(text)
    changed(y)
  end
  function t.blit(text, fgs, bgs) t.write(text) end
  function t.clear()
    for y = 1, s.h do s.rows[y] = s.blank() end
    changed(1, s.h)
  end
  function t.clearLine()
    if s.cy >= 1 and s.cy <= s.h then s.rows[s.cy] = s.blank() changed(s.cy) end
  end
  function t.getCursorPos() return s.cx, s.cy end
  function t.setCursorPos(x, y)
    local oy = s.cy
    s.cx, s.cy = floor(x), floor(y)
    changed(min(oy, s.cy), max(oy, s.cy))
  end
  function t.setCursorBlink(b) s.blink = b and true or false changed(s.cy) end
  function t.getCursorBlink() return s.blink end
  function t.getSize() return s.w, s.h end
  t.getResolution = t.getSize
  function t.maxResolution() return s.w, s.h end
  function t.setResolution() return false end
  function t.scroll(n)
    n = floor(n)
    if n > 0 then
      for _ = 1, min(n, s.h) do table.remove(s.rows, 1) s.rows[s.h] = s.blank() end
    elseif n < 0 then
      for _ = 1, min(-n, s.h) do table.remove(s.rows, s.h) table.insert(s.rows, 1, s.blank()) end
    end
    changed(1, s.h)
  end
  function t.isColor() return true end
  t.isColour = t.isColor
  function t.getDepth() return 24 end
  function t.setTextColor(i) s.fgi = i; s.fg = palette(i) end
  function t.setBackgroundColor(i) s.bgi = i; s.bg = palette(i) end
  function t.getTextColor() return s.fgi end
  function t.getBackgroundColor() return s.bgi end
  function t.setTextColorRGB(c) s.fg = c end
  function t.setBackgroundColorRGB(c) s.bg = c end
  function t.getTextColorRGB() return s.fg end
  function t.getBackgroundColorRGB() return s.bg end
  t.setTextColour, t.setBackgroundColour = t.setTextColor, t.setBackgroundColor
  t.getTextColour, t.getBackgroundColour = t.getTextColor, t.getBackgroundColor
  t.getPaletteColor = realTerm.getPaletteColor
  t.setPaletteColor = function() end
  t.open = realTerm.open
  return t
end

--- Простая «оболочка» для программ, которым нужен shell (resolve, dir), когда они запущены не из Терминала.
function V.miniShell(dir)
  dir = fs.combine(dir or "home", "")
  local sh = {}
  function sh.dir() return "/" .. dir end
  function sh.setDir(d) dir = fs.combine(d, "") end
  function sh.resolve(p)
    if p:sub(1, 1) == "/" then return fs.combine(p, "") end
    return fs.combine(dir, p)
  end
  function sh.resolveProgram(name)
    local c = name:find("/") and { sh.resolve(name), sh.resolve(name) .. ".lua" }
      or { fs.combine(dir, name), fs.combine(dir, name .. ".lua"), "rom/programs/" .. name .. ".lua", "programs/" .. name .. ".lua",
           "usr/bin/" .. name .. ".lua", "bin/" .. name .. ".lua", "home/bin/" .. name .. ".lua" }
    for _, p in ipairs(c) do if fs.exists(p) and not fs.isDir(p) then return p end end
  end
  function sh.run(name, ...)
    local p = sh.resolveProgram(name)
    if not p then printError("Нет такой команды: " .. tostring(name)) return false end
    return os.run({ shell = sh }, p, ...)
  end
  function sh.exit() end
  return sh
end

--- Окно с программой path (аргументы args). opts: title, w, h, onExit(win, ok).
function V.app(path, args, opts)
  opts = opts or {}
  local app = { title = opts.title or "Терминал", icon = opts.icon or "term", w = opts.w or 82, h = opts.h or 26 }
  local s, co, filter, nshell, dead
  local win

  local function onChange(y1, y2)
    if win and not win.min then wm.invalidate(win.bx, win.by + y1 - 1, win.bw, y2 - y1 + 1) end
  end

  local function resize()
    local w, h = max(10, win.bw), max(3, win.bh)
    if s and s.w == w and s.h == h then return end
    local old = s
    s = screen(w, h)
    if old then
      s.fg, s.bg, s.fgi, s.bgi, s.blink = old.fg, old.bg, old.fgi, old.bgi, old.blink
      local shift = max(0, old.cy - h)
      for y = 1, h do
        local r = old.rows[y + shift]
        if r then for x = 1, min(w, old.w) do s.rows[y].c[x], s.rows[y].f[x], s.rows[y].b[x] = r.c[x], r.f[x], r.b[x] end end
      end
      s.cx, s.cy = min(old.cx, w), old.cy - shift
    end
    app.term = api(s, onChange)
  end

  local function resume(...)
    if dead then return end
    local savedTerm, savedShell = term, _G._NATIVE_SHELL
    _G.term = app.term
    _G._NATIVE_SHELL = nshell
    local ok, res = coroutine.resume(co, ...)
    nshell = _G._NATIVE_SHELL
    _G.term, _G._NATIVE_SHELL = savedTerm, savedShell
    if not ok then
      dead = true
      app.term.setTextColor(14)
      local x, y = app.term.getCursorPos()
      if x > 1 then app.term.setCursorPos(1, math.min(s.h, y + 1)) end
      app.term.write(tostring(res))
    elseif coroutine.status(co) == "dead" then
      dead = true
    else
      filter = res
    end
    if dead then
      if opts.onExit then opts.onExit(win, ok) end
      if ok and opts.closeOnExit ~= false then wm.close(win, true) else wm.setTitle(win, app.title .. (ok and " — завершено" or " — ошибка")) end
    end
  end

  function app.init(w)
    win = w
    resize()
    nshell = opts.shell or V.miniShell(opts.dir or (path and fs.getDir(path)) or "home")
    co = coroutine.create(function()
      if opts.run then return opts.run() end
      local env = setmetatable({ shell = nshell }, { __index = _G })
      local fn, err = loadfile(path, env)
      if not fn then error(err, 0) end
      return fn(table.unpack(args or {}))
    end)
    resume()
  end

  function app.draw(w, c)
    if not s then return end
    for y = 1, min(s.h, c.h) do
      local r = s.rows[y]
      local x = 1
      while x <= s.w do
        local f, b = r.f[x], r.b[x]
        local x2 = x
        local parts = {}
        while x2 <= s.w and r.f[x2] == f and r.b[x2] == b do parts[#parts + 1] = r.c[x2] x2 = x2 + 1 end
        c.text(x, y, table.concat(parts), f, b)
        x = x2
      end
    end
    if wm.focus == w and s.blink and not dead and s.cx >= 1 and s.cx <= s.w and s.cy >= 1 and s.cy <= s.h then
      local r = s.rows[s.cy]
      c.text(s.cx, s.cy, r.c[s.cx], r.b[s.cx], r.f[s.cx] ~= r.b[s.cx] and r.f[s.cx] or T.fg)
    end
  end

  local INPUT = { key = true, key_up = true, char = true, paste = true, mouse_click = true, mouse_drag = true,
                  mouse_up = true, scroll = true }

  function app.event(w, ev, ...)
    if ev == "resize" then
      resize()
      if co and not dead then resume("term_resize") end
      return
    end
    if ev == "focus" or ev == "blur" then wm.redraw(w) return end
    if ev == "click" or ev == "dblclick" then return end
    if dead then return end
    if ev == "scroll" then ev = "mouse_scroll" end
    if filter == nil or filter == ev or ev == "terminate" then
      resume(ev, ...)
    end
  end

  function app.close(w)
    if not dead and co then
      dead = true
      if opts.onExit then opts.onExit(w, false) end
    end
  end

  return app
end

return V
