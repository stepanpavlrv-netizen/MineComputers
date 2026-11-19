-- Камеры для Wired: найти камеры в сети, смотреть по одной, сеткой, все сразу или по очереди.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local gpu = G.gpu

local function kb(b) return b >= 1048576 and string.format("%.1f МБ/с", b / 1048576) or string.format("%d КБ/с", math.floor(b / 1024)) end
local FPS = { 5, 10, 15, 20, 30 }
local K = require("camkeys")

return function()
  local app = { title = "Камеры", icon = "cam", w = 120, h = 40, minW = 70, minH = 20 }
  local st = { mode = "list", cams = {}, picked = {}, sel = 1, grid = 4, view = {}, focus = 1, cycle = false, every = 5,
               infos = {}, fps = {}, scroll = 0 }
  local win, cv, cvw, cvh
  local timer

  local function ensureCanvas(w)
    local need_w, need_h = w.bw, w.bh * 2
    if cv and cvw == need_w and cvh == need_h then return cv end
    if cv then pcall(function() cv:free() end) end
    cv, cvw, cvh = nil, nil, nil
    if not gpu.canvas then return nil end
    local c, err = gpu.canvas(need_w, need_h)
    if c then cv, cvw, cvh = c, need_w, need_h else st.err = "Не хватает видеопамяти: " .. tostring(err) end
    return cv
  end

  st.tried = {}
  local function refreshList()
    local ok, list = pcall(camera.list)
    st.cams = (ok and type(list) == "table") and list or {}
    -- закрытые паролем: один раз за сеанс пробуем сохранённый пароль
    for _, c in ipairs(st.cams) do
      if c.locked and not st.tried[c.address] then
        st.tried[c.address] = true
        if K.auto(c) then c.locked = false end
      end
    end
    if st.sel > #st.cams then st.sel = math.max(1, #st.cams) end
  end

  local function camByName(name)
    for _, c in ipairs(st.cams) do
      if c.address == name or c.name == name then return c end
    end
  end

  -- Окошко пароля камеры: done(true) — вошли, done(false) — отмена.
  local function askPassword(name, done)
    local f = U.field("", "f", "*")
    f.focused = true
    local remember, err = true, nil
    local dlg
    local function finish(ok)
      wm.close(dlg, true)
      done(ok)
    end
    local function try()
      if f.text == "" then return end
      local r, e = K.login(name, f.text, remember)
      if r then
        local c = camByName(name)
        if c then c.locked = false end
        finish(true)
      else
        err = e
        f.text, f.pos = "", 0
        wm.redraw(dlg)
      end
    end
    dlg = wm.open({ title = "Камера «" .. name .. "»", icon = "cam", w = math.min(46, G.W - 2), h = 9, resizable = false,
      draw = function(w, c)
        c.text(3, 2, "Камера закрыта паролем.", T.fg)
        c.field(3, 3, c.w - 4, f)
        c.check(3, 5, "Запомнить на этом компьютере", remember, "remember")
        if err then c.text(3, 6, G.cut(err, c.w - 4), T.err) end
        local bx = c.w - 21
        bx = c.button(bx, 7, "Отмена", "no") + 1
        c.button(bx, 7, "Открыть", "ok", "primary")
      end,
      event = function(w, ev, a)
        if ev == "click" then
          if a == "ok" then try()
          elseif a == "no" then finish(false)
          elseif a == "remember" then remember = not remember wm.redraw(w) end
        elseif ev == "key" and a == "escape" then finish(false)
        elseif ev == "char" or ev == "key" or ev == "paste" then
          if U.edit(f, ev, a) == "enter" then try() return end
          wm.redraw(w)
        end
      end })
    dlg.app.close = function() done(false) end
  end

  -- Перед просмотром: у закрытых камер спросить пароль по очереди; отменённые — не показывать.
  local function unlockThen(names, fn)
    local out, i = {}, 0
    local function step()
      i = i + 1
      local name = names[i]
      if not name then fn(out) return end
      local c = camByName(name)
      if c and c.locked then
        askPassword(name, function(ok)
          if ok then out[#out + 1] = name end
          step()
        end)
      else
        out[#out + 1] = name
        step()
      end
    end
    step()
  end

  local function info(name)
    local r = camera.info(name)
    if r then st.infos[name] = r end
    return st.infos[name] or {}
  end

  -- точка экрана 6×4,5 пикселя: картинка 16:9 → ширина = высота × 4/3 точек
  local function fit(x0, y0, w, h)
    local pw = w
    local ph = math.floor(pw * 3 / 4 + 0.5)
    if ph > h then ph = h pw = math.floor(ph * 4 / 3 + 0.5) end
    return x0 + math.floor((w - pw) / 2), y0 + math.floor((h - ph) / 2), pw, ph
  end

  -- ячейки сетки (в точках кадрового буфера, тело окна без верхней строки)
  local function cells()
    local names = st.view
    local out = {}
    local panel = (#names == 1) and 34 or 0
    local W, H = cvw - panel, cvh - 4
    local n = #names
    local cols = n <= 1 and 1 or (n <= 4 and 2 or (n <= 9 and 3 or 4))
    local rows = math.ceil(n / cols)
    local cw, ch = math.floor(W / cols), math.floor(H / rows)
    for i, name in ipairs(names) do
      local gx, gy = (i - 1) % cols, math.floor((i - 1) / cols)
      local x, y, w, h = fit(gx * cw + 1, 4 + gy * ch + 1, cw - 2, ch - 2)
      out[i] = { name = name, x = x, y = y, w = w, h = h }
    end
    return out
  end

  local function schedule()
    if timer or win.closed then return end
    local delay = 2
    if st.mode == "view" then
      local want = 5
      for _, n in ipairs(st.view) do want = math.max(want, info(n).fps or 5) end
      delay = 1 / want
    end
    timer = wm.after(delay, function()
      timer = nil
      if win.closed then return end
      if st.mode == "list" then refreshList() end
      if not win.min then wm.redraw(win) end
      schedule()
    end)
  end

  local cycleNext
  local function retitle()
    wm.setTitle(win, "Камеры — " .. (#st.view == 1 and st.view[1] or ("сетка из " .. #st.view)) .. (st.cycle and " (по очереди)" or ""))
  end

  local function openView(names, unlocked)
    if #names == 0 then return end
    if not unlocked then
      unlockThen(names, function(list)
        if #list > 0 and not win.closed then
          openView(list, true)
          if st.wantCycle then st.wantCycle = nil st.cycle, st.cyclePos = true, 0 cycleNext() end
        end
        st.wantCycle = nil
      end)
      return
    end
    st.mode, st.view, st.focus = "view", names, 1
    st.all = names
    retitle()
    wm.redraw(win)
  end

  function cycleNext()
    if not st.cycle or st.mode ~= "view" or win.closed then return end
    st.cyclePos = (st.cyclePos or 0) % #st.all + 1
    st.view = { st.all[st.cyclePos] }
    retitle()
    wm.redraw(win)
    wm.after(st.every, cycleNext)
  end

  local function act(fn, ...)
    local name = st.view[st.focus] or st.view[1]
    if not name then return end
    local r = fn(name, ...)
    if r then st.infos[name] = r end
    wm.redraw(win)
  end

  function app.init(w)
    win = w
    refreshList()
    for i, c in ipairs(st.cams) do if i <= 4 then st.picked[c.name ~= "" and c.name or c.address] = true end end
    schedule()
  end

  local function camName(c) return c.name ~= "" and c.name or c.address end

  local function listHeader(c)
    c.text(2, 1, "Камеры в сети", T.fg)
    c.text(17, 1, "· найдено " .. #st.cams, T.dim)
    c.button(c.w - 12, 1, "↻ Найти", "rescan")
    c.hline(2, 2, c.w - 2)
  end

  local function drawList(w, c)
    if #st.cams == 0 then
      listHeader(c)
      c.text(2, 4, "Камер не видно.", T.fg)
      c.text(2, 5, "Подключите камеру кабелем к коммутатору этой сети и нажмите «Найти».", T.dim)
      return
    end
    local rowH = 5
    local rows = math.floor((c.h - 5) / rowH)
    st.scroll = math.max(0, math.min(st.scroll, #st.cams - rows))
    local cvok = ensureCanvas(w)
    if cvok then cv:clear(T.win) end
    for i = 1, rows do
      local k = st.scroll + i
      local cam = st.cams[k]
      if not cam then break end
      local y = 3 + (i - 1) * rowH
      local name = camName(cam)
      local sel = k == st.sel
      if cvok then
        local px, py = 6, (y - 1) * 2
        if sel then cv:fill(0, py, cvw, (rowH - 1) * 2, T.sel) end
        if cam.locked then
          cv:fill(px, py, 16, (rowH - 1) * 2, 0x14111d)
        else
          local r, err = cv:camera(name, px, py, 16, (rowH - 1) * 2)
          if not r and err then cv:fill(px, py, 16, (rowH - 1) * 2, 0x14111d) end
        end
      end
    end
    if cvok then c.canvas(cv, 1, 1) end
    listHeader(c)
    for i = 1, rows do
      local k = st.scroll + i
      local cam = st.cams[k]
      if not cam then break end
      local y = 3 + (i - 1) * rowH
      local name = camName(cam)
      local sel = k == st.sel
      local bg = sel and T.sel or T.win
      c.text(2, y + 1, st.picked[name] and "☑" or "☐", st.picked[name] and T.accent or T.faint, bg)
      c.hit(1, y, 4, rowH - 1, "pick", name)
      c.text(25, y, name, T.fg, bg)
      c.text(25, y + 1, cam.address, T.faint, bg)
      c.text(25, y + 2, cam.width .. "×" .. cam.height .. " · линия " .. kb(cam.link or 0) .. " · переходов " .. (cam.hops or 0), T.dim, bg)
      if cam.locked then
        c.text(8, y + 1, "пароль", T.warn, 0x14111d)
        c.text(70, y, "● закрыта паролем", T.warn, bg)
      elseif cam.password then
        c.text(70, y, "● в сети · пароль ✓", T.ok, bg)
      else
        c.text(70, y, "● в сети", T.ok, bg)
      end
      c.button(c.w - 12, y + 1, "Открыть", "open1", "normal", name)
      c.hit(5, y, 60, rowH - 1, "row", k)
    end
    local n = 0
    for _ in pairs(st.picked) do n = n + 1 end
    local by = c.h
    c.hline(2, by - 1, c.w - 2)
    c.text(2, by, "Выбрано " .. n .. ":", T.dim)
    local bx = 14
    bx = c.button(bx, by, "Сеткой", "grid", n > 0 and "primary" or "off") + 1
    bx = c.button(bx, by, "Все сразу", "all", "normal") + 1
    bx = c.button(bx, by, "По очереди · " .. st.every .. " с", "cycle", n > 0 and "normal" or "off") + 1
  end

  local function viewBar(c)
    local bx = 2
    bx = c.button(bx, 1, "← Все камеры", "back") + 2
    c.text(bx, 1, "Сетка:", T.dim)
    bx = bx + 7
    for _, g in ipairs({ { 1, "1" }, { 4, "2×2" }, { 9, "3×3" }, { 99, "все" } }) do
      bx = c.button(bx, 1, g[2], "setgrid", (#st.view == math.min(g[1], #st.all) and not st.cycle) and "primary" or "normal", g[1]) + 1
    end
    bx = bx + 1
    bx = c.button(bx, 1, "По очереди", "togglecycle", st.cycle and "primary" or "normal") + 1
    bx = c.button(bx, 1, "−", "every", "normal", -1)
    c.text(bx, 1, " " .. st.every .. " с ", T.fg)
    bx = c.button(bx + G.len(" " .. st.every .. " с "), 1, "+", "every", "normal", 1)
  end

  local function drawView(w, c)
    if not ensureCanvas(w) then
      viewBar(c)
      c.text(2, 3, st.err or "Нужна видеокарта MineComputers с кадровым буфером", T.err)
      return
    end
    cv:clear(0x050607)
    cv:fill(0, 0, cvw, 2, T.win)
    local list = cells()
    for i, cell in ipairs(list) do
      local r, err = cv:camera(cell.name, cell.x, cell.y, cell.w, cell.h)
      cell.err, cell.loading = err, r == -1
      if r and r > 0 then st.fps[cell.name] = r end
      if #list > 1 and i == st.focus then
        cv:fill(cell.x - 1, cell.y - 1, cell.w + 2, 1, T.accent)
        cv:fill(cell.x - 1, cell.y + cell.h, cell.w + 2, 1, T.accent)
      end
    end
    c.canvas(cv, 1, 1)
    viewBar(c)
    for i, cell in ipairs(list) do
      local tx, ty = cell.x + 1, math.floor(cell.y / 2) + 1
      c.text(tx, ty, " " .. cell.name .. " ", T.white, 0x141019)
      local inf = st.infos[cell.name] or {}
      local rec = " ● " .. (st.fps[cell.name] or 0) .. " к/с "
      c.text(cell.x + cell.w - G.len(rec), ty, rec, T.err, 0x141019)
      if cell.err or cell.loading then
        local m = cell.err or "Собираю картинку…"
        c.text(tx + math.max(0, math.floor((cell.w - G.len(m)) / 2)), ty + math.floor(cell.h / 4), m, cell.err and T.err or T.dim, 0x050607)
      end
      c.hit(cell.x + 1, ty, cell.w, math.floor(cell.h / 2), "cell", i)
    end
    if #list == 1 then
      local name = list[1].name
      local inf = info(name)
      local px = c.w - 32
      c.fill(px - 1, 2, 34, c.h - 1, T.side)
      local bg = T.side
      c.bg = T.side
      c.text(px, 3, G.cut(name, 30), T.fg, bg)
      c.text(px, 4, (inf.width or 0) .. "×" .. (inf.height or 0) .. " · " .. (inf.fps or 0) .. " к/с", T.dim, bg)
      c.text(px, 6, "Поворот", T.fg, bg)
      c.text(px + 4, 7, " ▲ ", T.fg, T.btn) c.hit(px + 4, 7, 3, 1, "turn", "up")
      c.text(px, 8, " ◀ ", T.fg, T.btn) c.hit(px, 8, 3, 1, "turn", "left")
      c.text(px + 8, 8, " ▶ ", T.fg, T.btn) c.hit(px + 8, 8, 3, 1, "turn", "right")
      c.text(px + 4, 9, " ▼ ", T.fg, T.btn) c.hit(px + 4, 9, 3, 1, "turn", "down")
      c.text(px + 14, 7, string.format("угол %+.0f°", inf.yaw or 0), T.dim, bg)
      c.text(px + 14, 8, string.format("наклон %+.0f°", inf.pitch or 0), T.dim, bg)
      c.text(px, 11, "Зум", T.fg, bg)
      c.text(px, 12, " − ", T.fg, T.btn) c.hit(px, 12, 3, 1, "zoom", 5)
      local z = 70 / (inf.fov or 70)
      local zn = math.max(0, math.min(18, math.floor((z - 1) / 4 * 18 + 0.5)))
      c.text(px + 4, 12, string.rep("█", zn) .. string.rep("░", 18 - zn), T.link, bg)
      c.text(px + 23, 12, " + ", T.fg, T.btn) c.hit(px + 23, 12, 3, 1, "zoom", -5)
      c.text(px + 27, 12, string.format("×%.1f", z), T.dim, bg)
      c.check(px, 14, "Ночное видение", inf.night, "night")
      c.check(px, 15, "Патруль (сам поворачивается)", inf.patrol, "patrol")
      c.text(px, 17, "Качество", T.fg, bg)
      c.text(px, 18, " − ", T.fg, T.btn) c.hit(px, 18, 3, 1, "res", -1)
      c.text(px + 4, 18, "разрешение " .. (inf.res or "?"), T.dim, bg)
      c.text(px + 23, 18, " + ", T.fg, T.btn) c.hit(px + 23, 18, 3, 1, "res", 1)
      c.button(px, 20, "Частота: " .. (inf.fps or 10) .. " к/с", "fps")
      c.text(px, c.h - 2, "← → соседняя камера", T.faint, bg)
      c.text(px, c.h - 1, "колесо — зум, мышью — поворот", T.faint, bg)
      c.bg = T.win
    end
  end

  function app.draw(w, c)
    if st.mode == "list" then drawList(w, c) else drawView(w, c) end
  end

  function app.event(w, ev, a, b, x, y)
    if ev == "click" or ev == "dblclick" then
      if a == "rescan" then refreshList()
      elseif a == "pick" then st.picked[b] = not st.picked[b] or nil
      elseif a == "row" then
        st.sel = b
        if ev == "dblclick" then openView({ camName(st.cams[b]) }) end
      elseif a == "open1" then openView({ b })
      elseif a == "grid" or a == "cycle" then
        local names = {}
        for _, cam in ipairs(st.cams) do if st.picked[camName(cam)] then names[#names + 1] = camName(cam) end end
        if a == "cycle" and #names > 0 then
          st.wantCycle = true
          openView(names)
        else
          openView(names)
        end
      elseif a == "all" then
        local names = {}
        for _, cam in ipairs(st.cams) do names[#names + 1] = camName(cam) end
        openView(names)
      elseif a == "back" then
        st.mode, st.cycle = "list", false
        wm.setTitle(w, "Камеры")
        refreshList()
      elseif a == "setgrid" then
        st.cycle = false
        local n = math.min(b, #st.all)
        local start = math.max(1, math.min(st.focus, #st.all - n + 1))
        st.view = {}
        for i = start, start + n - 1 do st.view[#st.view + 1] = st.all[i] end
        st.focus = 1
      elseif a == "togglecycle" then
        st.cycle = not st.cycle
        if st.cycle then st.cyclePos = 0 cycleNext() else st.view = st.all end
      elseif a == "every" then st.every = math.max(2, math.min(60, st.every + b))
      elseif a == "cell" and st.view[b] and select(2, camera.info(st.view[b])) and tostring(select(2, camera.info(st.view[b]))):find("паролем") then
        askPassword(st.view[b], function() wm.redraw(w) end)
      elseif a == "cell" then
        st.focus = b
        if ev == "dblclick" and #st.view > 1 then
          st.view = { st.view[b] }
          st.focus = 1
        end
        st.dragX, st.dragY = x, y
      elseif a == "turn" then
        local d = { up = { 0, 5 }, down = { 0, -5 }, left = { -5, 0 }, right = { 5, 0 } }
        act(camera.turn, d[b][1], d[b][2])
      elseif a == "zoom" then act(camera.zoom, (info(st.view[1]).fov or 70) + b)
      elseif a == "night" then act(camera.set, "night", not info(st.view[1]).night)
      elseif a == "patrol" then act(camera.set, "patrol", not info(st.view[1]).patrol)
      elseif a == "res" then act(camera.set, "res", math.max(1, math.min(7, (info(st.view[1]).res or 4) + b)))
      elseif a == "fps" then
        local cur = info(st.view[1]).fps or 10
        local nxt = 10
        for i, v in ipairs(FPS) do if v == cur then nxt = FPS[i % #FPS + 1] end end
        act(camera.set, "fps", nxt)
      end
      if st.mode == "view" then retitle() end
      wm.redraw(w)
    elseif ev == "mouse_drag" and st.dragX and st.mode == "view" then
      local inf = info(st.view[st.focus] or st.view[1])
      local k = (inf.fov or 70) / 40
      local dx, dy = b - st.dragX, x - st.dragY
      if dx ~= 0 or dy ~= 0 then act(camera.turn, dx * k, -dy * k) end
      st.dragX, st.dragY = b, x
    elseif ev == "mouse_up" then
      st.dragX = nil
    elseif ev == "scroll" then
      if st.mode == "list" then st.scroll = math.max(0, st.scroll + a)
      else act(camera.zoom, (info(st.view[st.focus] or st.view[1]).fov or 70) + a * 5) end
      wm.redraw(w)
    elseif ev == "key" and st.mode == "view" then
      if a == "left" and #st.view == 1 and #st.all > 1 then
        local i = 1
        for k, n in ipairs(st.all) do if n == st.view[1] then i = k end end
        st.view = { st.all[(i - 2) % #st.all + 1] }
      elseif a == "right" and #st.view == 1 and #st.all > 1 then
        local i = 1
        for k, n in ipairs(st.all) do if n == st.view[1] then i = k end end
        st.view = { st.all[i % #st.all + 1] }
      elseif a == "up" then act(camera.turn, 0, 5) return
      elseif a == "down" then act(camera.turn, 0, -5) return
      elseif a == "escape" then return app.event(w, "click", "back")
      else return end
      wm.redraw(w)
    elseif ev == "char" and st.mode == "view" then
      local inf = info(st.view[st.focus] or st.view[1])
      if a == "+" or a == "=" then act(camera.zoom, (inf.fov or 70) - 5)
      elseif a == "-" then act(camera.zoom, (inf.fov or 70) + 5)
      elseif a == "n" or a == "т" then act(camera.set, "night", not inf.night) end
    elseif ev == "resize" then
      wm.redraw(w)
    end
  end

  function app.close(w)
    if cv then pcall(function() cv:free() end) cv = nil end
  end

  return app
end
