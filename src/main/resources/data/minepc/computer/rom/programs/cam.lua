-- Камеры наблюдения.
--   cam                       — камеры в сети
--   cam <имя>                 — смотреть. Стрелки или WASD — повернуть, + и − (колесо) — зум, мышью тянуть — повернуть,
--                               n — ночное видение, p — патруль, r — разрешение, f — частота, q — выход
--   cam wall <имя> <имя> ...  — видеостена: до 4 камер на экране
--   cam turn <имя> <вбок> [вверх]   cam look <имя> <угол> [наклон]   cam set <имя> res|fps|night|patrol|name <значение>
local args = { ... }
local cmd = args[1]

local function color(c) if term.isColor() then term.setTextColor(c) end end
local function pad(s, n) s = tostring(s) return s .. string.rep(" ", math.max(0, n - unicode.len(s))) end
local function kb(bytes) return bytes >= 1048576 and string.format("%.1f МБ/с", bytes / 1048576) or string.format("%d КБ/с", math.floor(bytes / 1024)) end

if not cmd then
  local list = camera.list()
  if #list == 0 then print("Камер в сети не видно: подключите камеру кабелем к коммутатору") return end
  color(colors.yellow)
  print(pad("Имя", 14) .. pad("Адрес", 10) .. pad("Кадр", 10) .. pad("Линия", 11) .. "Переходов")
  color(colors.white)
  for _, c in ipairs(list) do
    print(pad(c.name ~= "" and c.name or "-", 14) .. pad(c.address, 10) .. pad(c.width .. "×" .. c.height, 10) ..
      pad(kb(c.link), 11) .. c.hops)
  end
  print("Смотреть: cam <имя>")
  return
end

if cmd == "turn" or cmd == "look" then
  local r, err = camera[cmd](args[2] or "", tonumber(args[3] or "0"), tonumber(args[4] or "0"))
  if not r then printError(err) return end
  print(string.format("%s: угол %+.0f°, наклон %+.0f°", args[2], r.yaw, r.pitch))
  return
elseif cmd == "set" then
  local v = args[4]
  local val = tonumber(v)
  if v == "on" or v == "вкл" then val = true elseif v == "off" or v == "выкл" then val = false end
  if args[3] == "name" then val = v or "" end
  local r, err = camera.set(args[2] or "", args[3] or "", val)
  if not r then printError(err) return end
  print(string.format("%s: %d×%d, %d к/с, поток %s, по линии %s", r.name ~= "" and r.name or r.address, r.width, r.height, r.fps,
    kb(r.stream), kb(r.link)))
  return
end

---------------------------------------------------------------- просмотр
local gpu = component.gpu
if not gpu.canvas then print("Нужна видеокарта MineComputers с кадровым буфером") return end
local names = {}
if cmd == "wall" then
  for i = 2, math.min(5, #args) do names[#names + 1] = args[i] end
  if #names == 0 then print("cam wall <имя> <имя> ...") return end
else
  names[1] = cmd
end
for _, n in ipairs(names) do
  local _, err = camera.info(n)
  if err then printError(err) return end
end

local W, H = gpu.maxResolution()
local oldW, oldH = gpu.getResolution()
gpu.setResolution(W, H)
if gpu.maxDepth() >= 24 then pcall(gpu.setDepth, 24) end
local CW, CH = W, (H - 1) * 2
local cv, cerr = gpu.canvas(CW, CH)
if not cv then
  gpu.setResolution(oldW, oldH)
  printError("Не хватает видеопамяти для кадра " .. CW .. "×" .. CH .. ": " .. tostring(cerr))
  return
end

-- Точка экрана — 6×4,5 пикселя, поэтому картинка 16:9 занимает ширину = высота × 4/3 точек.
local function fit(x0, y0, w, h)
  local pw = w
  local ph = math.floor(pw * 3 / 4 + 0.5)
  if ph > h then ph = h pw = math.floor(ph * 4 / 3 + 0.5) end
  return x0 + math.floor((w - pw) / 2), y0 + math.floor((h - ph) / 2), pw, ph
end

local cells = {}
if #names == 1 then
  local x, y, w, h = fit(0, 0, CW, CH)
  cells[1] = { name = names[1], x = x, y = y, w = w, h = h }
else
  local cw, ch = math.floor(CW / 2), math.floor(CH / 2)
  for i, n in ipairs(names) do
    local gx, gy = (i - 1) % 2, math.floor((i - 1) / 2)
    local x, y, w, h = fit(gx * cw + 1, gy * ch + 2, cw - 2, ch - 3)
    cells[i] = { name = n, x = x, y = y, w = w, h = h }
  end
end
local focus = 1
local infos = {}
local function refresh()
  for i, c in ipairs(cells) do
    local r = camera.info(c.name)
    if r then infos[i] = r end
  end
end
refresh()

local fps = {}
local function bar()
  term.setCursorPos(1, 1)
  if term.isColor() then term.setBackgroundColor(colors.gray) term.setTextColor(colors.white) end
  term.clearLine()
  local c, info = cells[focus], infos[focus] or {}
  local blink = math.floor(computer.uptime() * 2) % 2 == 0
  if term.isColor() then term.setTextColor(blink and colors.red or colors.gray) end
  term.write(" ● ")
  if term.isColor() then term.setTextColor(colors.white) end
  local left = string.format("%s  %d×%d  %d/%d к/с  %+.0f° %+.0f°  ×%.1f%s%s", c.name, info.width or 0, info.height or 0,
    fps[focus] or 0, info.fps or 0, info.yaw or 0, info.pitch or 0, 70 / (info.fov or 70),
    info.night and "  ночь" or "", info.patrol and "  патруль" or "")
  term.write(left)
  local hint = "стрелки поворот  +/- зум  n ночь  p патруль  r f качество  q выход"
  if unicode.len(left) + unicode.len(hint) + 6 < W then
    term.setCursorPos(W - unicode.len(hint), 1)
    if term.isColor() then term.setTextColor(colors.lightGray) end
    term.write(hint)
  end
  if term.isColor() then term.setBackgroundColor(colors.black) term.setTextColor(colors.white) end
end

local function draw()
  cv:clear(0x050607)
  for i, c in ipairs(cells) do
    local r, err = cv:camera(c.name, c.x, c.y, c.w, c.h)
    c.err = err
    c.loading = (r == -1)
    if r and r > 0 then fps[i] = r end
    if #cells > 1 and i == focus then
      cv:fill(c.x - 1, c.y - 1, c.w + 2, 1, 0xFFD84A)
      cv:fill(c.x - 1, c.y + c.h, c.w + 2, 1, 0xFFD84A)
    end
  end
  cv:present(1, 2)
  for i, c in ipairs(cells) do
    local tx, ty = c.x + 1, math.floor(c.y / 2) + 2
    if #cells > 1 then
      term.setCursorPos(tx + 1, ty)
      if term.isColor() then term.setTextColor(colors.white) term.setBackgroundColor(colors.black) end
      term.write(" " .. c.name .. " ")
    end
    if c.err or c.loading then
      local msg = c.err or "Собираю картинку..."
      term.setCursorPos(math.max(1, tx + math.floor((c.w - unicode.len(msg)) / 2)), ty + math.floor(c.h / 4))
      if term.isColor() then term.setTextColor(c.err and colors.red or colors.lightGray) term.setBackgroundColor(colors.black) end
      term.write(msg)
    end
  end
  bar()
end

local function act(fn, ...)
  local r, err = fn(cells[focus].name, ...)
  if r then infos[focus] = r end
end

local RES_N, FPS_LIST = 7, { 5, 10, 15, 20, 30 }
local function nextFps(cur)
  for i, v in ipairs(FPS_LIST) do if v == cur then return FPS_LIST[i % #FPS_LIST + 1] end end
  return 10
end

local timer = os.startTimer(0)
local frame, lastInfo = 0, computer.uptime()
local dragX, dragY
while true do
  local ev = { os.pullEventRaw() }
  local e = ev[1]
  if e == "terminate" then break
  elseif e == "timer" and ev[2] == timer then
    local t = computer.uptime()
    draw()
    frame = frame + 1
    if t - lastInfo > 2 then refresh() lastInfo = t end
    local want = 0
    for i = 1, #cells do want = math.max(want, fps[i] or 5) end
    timer = os.startTimer(math.max(0, 1 / math.max(1, want) - (computer.uptime() - t)))
  elseif e == "key" then
    local k = ev[2]
    if k == "left" or k == "a" then act(camera.turn, -5, 0)
    elseif k == "right" or k == "d" then act(camera.turn, 5, 0)
    elseif k == "up" or k == "w" then act(camera.turn, 0, 5)
    elseif k == "down" or k == "s" then act(camera.turn, 0, -5)
    elseif k == "tab" and #cells > 1 then focus = focus % #cells + 1
    elseif k == "q" then break
    end
  elseif e == "char" then
    local ch = ev[2]
    local info = infos[focus] or {}
    if ch == "+" or ch == "=" then act(camera.zoom, (info.fov or 70) - 5)
    elseif ch == "-" then act(camera.zoom, (info.fov or 70) + 5)
    elseif ch == "n" or ch == "т" then act(camera.set, "night", not info.night)
    elseif ch == "p" or ch == "з" then act(camera.set, "patrol", not info.patrol)
    elseif ch == "r" or ch == "к" then act(camera.set, "res", (info.res or 4) % RES_N + 1)
    elseif ch == "f" or ch == "а" then act(camera.set, "fps", nextFps(info.fps or 10))
    elseif ch == "й" then break
    end
  elseif e == "mouse_click" then
    dragX, dragY = ev[3], ev[4]
    for i, c in ipairs(cells) do
      local px, py = ev[3] - 1, (ev[4] - 2) * 2
      if px >= c.x and px < c.x + c.w and py >= c.y and py < c.y + c.h then focus = i end
    end
  elseif e == "mouse_drag" and dragX then
    local info = infos[focus] or {}
    local k = (info.fov or 70) / math.max(10, cells[focus].h / 2)
    local dx, dy = ev[3] - dragX, ev[4] - dragY
    if dx ~= 0 or dy ~= 0 then act(camera.turn, dx * k, -dy * k) end
    dragX, dragY = ev[3], ev[4]
  elseif e == "mouse_up" then
    dragX, dragY = nil, nil
  elseif e == "mouse_scroll" then
    local info = infos[focus] or {}
    act(camera.zoom, (info.fov or 70) + ev[2] * 5)
  end
end
cv:free()
gpu.setResolution(oldW, oldH)
if term.isColor() then term.setBackgroundColor(colors.black) term.setTextColor(colors.white) end
term.clear()
term.setCursorPos(1, 1)
