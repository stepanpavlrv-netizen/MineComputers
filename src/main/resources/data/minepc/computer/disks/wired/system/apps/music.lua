-- Wired: Музыка — проигрыватель мелодий на звуковом чипе (без чипа — первым голосом на пищалке).
-- Встроенные мелодии те же, что у программы music; свои — файлы .mus в /home/Музыка
-- (строка — голос, ноты «E5/4 C#4/8. R/2», строка «tempo 140» — темп). Играет и при закрытом окне.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local DIR = "/home/Музыка"

local M = wired.musicState
if not M then
  M = { playing = false, paused = false, idx = nil, pos = 0, gen = 0, volume = 1, loop = false }
  wired.musicState = M
end

local function builtin()
  local env = setmetatable({}, { __index = _G })
  local fn = loadfile("/rom/programs/music.lua", env)
  local ok, t = pcall(fn or function() end, "--tunes")
  return ok and type(t) == "table" and t or {}
end

local function readTune(path)
  local f = fs.open(path, "r")
  if not f then return nil end
  local tune = { title = fs.getName(path):gsub("%.mus$", ""):gsub("%.txt$", ""), tempo = 120 }
  for l in (f.readAll() or ""):gmatch("[^\r\n]+") do
    local t = l:match("^%s*tempo%s+(%d+)")
    if t then tune.tempo = tonumber(t)
    elseif l:match("%S") and not l:match("^%s*%-%-") then tune[#tune + 1] = l end
  end
  f.close()
  return tune
end

local function playlist()
  local list = {}
  local b = builtin()
  for _, id in ipairs({ "korobeiniki", "ode", "elise" }) do
    if b[id] then list[#list + 1] = { title = b[id].title, tune = b[id] } end
  end
  for id, t in pairs(b) do
    if id ~= "korobeiniki" and id ~= "ode" and id ~= "elise" then list[#list + 1] = { title = t.title, tune = t } end
  end
  if fs.isDir(DIR) then
    local names = fs.list(DIR)
    table.sort(names)
    for _, n in ipairs(names) do
      if n:match("%.mus$") or n:match("%.txt$") then
        list[#list + 1] = { title = n:gsub("%.mus$", ""):gsub("%.txt$", ""), path = fs.combine(DIR, n), mine = true }
      end
    end
  end
  return list
end

local SEMI = { C = -9, D = -7, E = -5, F = -4, G = -2, A = 0, B = 2 }
--- Частота ноты «C#4», «Bb3», «A4» (ля первой октавы — 440 Гц).
local function noteFreq(name)
  if sound and sound.note then
    local ok, f = pcall(sound.note, name)
    if ok and f then return f end
  end
  local l, acc, oct = name:match("^([A-Ga-g])([#b]?)(%-?%d*)$")
  if not l then return nil end
  local n = SEMI[l:upper()] + (acc == "#" and 1 or acc == "b" and -1 or 0) + ((tonumber(oct) or 4) - 4) * 12
  return 440 * 2 ^ (n / 12)
end

local function parse(line, quarterMs)
  local out, len, dotted = {}, 4, false
  for tok in line:gmatch("%S+") do
    if tok ~= "|" then
      local name, l, dot = tok:match("^([A-Ga-gRr][#b]?%-?%d*)/?(%d*)(%.?)$")
      if not name then error("не понимаю ноту «" .. tok .. "»", 0) end
      if l ~= "" then len = tonumber(l); dotted = dot == "." elseif dot == "." then dotted = true end
      local ms = quarterMs * 4 / len * (dotted and 1.5 or 1)
      local f = nil
      if not name:match("^[Rr]") then
        f = noteFreq(name)
        if not f then error("не понимаю ноту «" .. name .. "»", 0) end
      end
      out[#out + 1] = { f, ms }
    end
  end
  return out
end

--- Подготовить мелодию: все голоса в одну шкалу событий.
local function compile(tune)
  local quarter = 60000 / (tune.tempo or 120)
  local voices = {}
  for i, line in ipairs(tune) do
    local ok, v = pcall(parse, line, quarter)
    if not ok then return nil, "Голос " .. i .. ": " .. tostring(v) end
    voices[i] = v
  end
  if #voices == 0 then return nil, "В мелодии нет нот" end
  local chip = sound and sound.channels and sound.channels() > 0
  local n = chip and math.min(#voices, sound.channels()) or 1
  local events, total = {}, 0
  for ch = 1, n do
    local t = 0
    for _, note in ipairs(voices[ch]) do
      if note[1] then
        events[#events + 1] = { t = t, ch = ch, f = note[1] }
        events[#events + 1] = { t = t + note[2] * 0.9, ch = ch }
      end
      t = t + note[2]
    end
    total = math.max(total, t)
  end
  table.sort(events, function(a, b) if a.t ~= b.t then return a.t < b.t end return (a.f and 1 or 0) < (b.f and 1 or 0) end)
  return { events = events, total = total, voices = voices, n = n, chip = chip }
end

local function redrawWins()
  for win in pairs(M.wins or {}) do if win.closed then M.wins[win] = nil else wm.redraw(win) end end
end

local function stopSound()
  if sound and sound.stop then pcall(sound.stop) end
end

local play

--- Проиграть M.song с позиции M.pos (мс). Работает фоновой задачей; gen отменяет старую.
local function runner(gen)
  local song = M.song
  local function alive() return M.gen == gen and M.playing and not M.paused end
  local function wait(sec)
    local timer = os.startTimer(sec)
    repeat local _, id = os.pullEvent("timer") until id == timer or not alive()
  end
  M.started = computer.uptime() - M.pos / 1000
  if not song.chip then
    local t = 0
    for _, note in ipairs(song.voices[1]) do
      if not alive() then return end
      if t + note[2] > M.pos then
        if note[1] then pcall(computer.beep, note[1], note[2] * 0.9 / 1000) end
        wait(note[2] / 1000)
      end
      t = t + note[2]
    end
  else
    local waves = { sound.modes.square, sound.modes.triangle, sound.modes.sawtooth, sound.modes.sine }
    for ch = 1, song.n do
      sound.setWave(ch, waves[(ch - 1) % #waves + 1])
      sound.setVolume(ch, (ch == 1 and 0.55 or 0.75) * M.volume)
      sound.setADSR(ch, 4, 120, 0.6, 60)
    end
    sound.process()
    local ev = song.events
    local i = 1
    while i <= #ev and ev[i].t < M.pos do i = i + 1 end
    local now = M.pos
    while i <= #ev and alive() do
      local batchEnd, first = now + 1500, i
      while i <= #ev and ev[i].t <= batchEnd do
        local e = ev[i]
        if e.t > now then sound.delay(e.t - now) now = e.t end
        if e.f then sound.setFrequency(e.ch, e.f) sound.open(e.ch) else sound.close(e.ch) end
        i = i + 1
      end
      if i == first then sound.delay(batchEnd - now) now = batchEnd end
      while alive() and not sound.process() do wait(0.1) end
      while alive() and sound.pending() > 1.0 do wait(0.1) end
    end
    while alive() and sound.pending() > 0 do wait(0.1) end
  end
  if M.gen ~= gen then return end
  if alive() then
    M.playing, M.pos = false, 0
    if M.loop then play(M.idx) return end
    -- следующая по списку
    local list = playlist()
    if M.idx and list[M.idx + 1] and M.auto then play(M.idx + 1) return end
  end
  redrawWins()
end

play = function(idx, from)
  local list = playlist()
  local item = list[idx]
  if not item then return false end
  local tune = item.tune or readTune(item.path)
  if not tune then return false, "не читается файл" end
  local song, err = compile(tune)
  if not song then return false, err end
  stopSound()
  M.gen = M.gen + 1
  M.song, M.idx, M.title = song, idx, item.title
  M.playing, M.paused, M.pos = true, false, from or 0
  M.err = nil
  local gen = M.gen
  wm.spawn(function() runner(gen) end, function(ok, err2)
    if not ok then M.err = tostring(err2) M.playing = false end
    redrawWins()
  end)
  redrawWins()
  return true
end

local function elapsed()
  if not M.song then return 0 end
  if M.playing and not M.paused and M.started then
    return math.min(M.song.total, (computer.uptime() - M.started) * 1000)
  end
  return M.pos
end

local function pause()
  if not M.playing then return end
  if M.paused then
    play(M.idx, M.pos)
  else
    M.pos = elapsed()
    M.paused = true
    M.gen = M.gen + 1
    stopSound()
  end
end

local function stop()
  M.gen = M.gen + 1
  M.playing, M.paused, M.pos = false, false, 0
  stopSound()
end

local function fmtTime(ms)
  local s = math.floor(ms / 1000)
  return string.format("%d:%02d", math.floor(s / 60), s % 60)
end

local TEMPLATE = [[
-- Своя мелодия: каждая строка — голос (первая — мелодия, вторая — бас).
-- Нота: буква, октава, длина: E5/4 — ми пятой октавы четвертью, C#4/8. — восьмая с точкой, R/2 — пауза.
tempo 120
C5/4 D5 E5 C5 | C5 D5 E5 C5 | E5 F5 G5/2 | E5/4 F5 G5/2
C3/2 G2 | C3 G2 | C3 G2 | C3 G2
]]

return function(arg)
  local app = { title = "Музыка", icon = "music", w = 78, h = 24, minW = 60, minH = 16 }
  local list = playlist()
  local sel = M.idx or 1
  local win
  M.auto = M.auto ~= false
  local autoplay
  if type(arg) == "string" and fs.exists(arg) then
    -- открыли файл из Проводника: если он не в папке Музыка — скопировать туда
    if fs.getDir(arg) ~= DIR:sub(2) then
      if not fs.exists(DIR) then fs.makeDir(DIR) end
      local dst = fs.combine(DIR, fs.getName(arg))
      if not fs.exists(dst) then pcall(fs.copy, arg, dst) end
      arg = "/" .. dst
    end
    list = playlist()
    for i, it in ipairs(list) do if it.path and ("/" .. fs.combine(it.path, "")) == ("/" .. fs.combine(arg, "")) then sel = i autoplay = i end end
  end

  local function tick()
    if win.closed then return end
    if M.playing and not M.paused and not win.min then wm.redraw(win) end
    wm.after(0.25, tick)
  end

  function app.init(w)
    win = w
    M.wins = M.wins or {}
    M.wins[w] = true
    tick()
    if autoplay then local ok, err = play(autoplay) if not ok and err then M.err = err end end
  end

  function app.close(w) if M.wins then M.wins[w] = nil end end

  function app.draw(w, c)
    local lw = 28
    c.fill(1, 1, lw, c.h, T.menu)
    c.text(2, 1, "Мелодии", T.dim, T.menu)
    for i, it in ipairs(list) do
      local y = 1 + i
      if y > c.h - 2 then break end
      local act = i == sel
      local now = M.idx == i and M.playing
      if act then c.fill(1, y, lw, 1, T.sel) end
      c.text(2, y, now and (M.paused and "‖" or "♪") or (it.mine and "·" or " "), now and T.accent or T.faint, act and T.sel or T.menu)
      c.text(4, y, G.cut(it.title, lw - 5), act and T.fg or T.dim, act and T.sel or T.menu)
      c.hit(1, y, lw, 1, "pick", i)
    end
    c.text(2, c.h - 1, "+ Своя мелодия", T.dim, T.menu)
    c.hit(1, c.h - 1, lw, 1, "new")
    c.text(2, c.h, "▤ Папка Музыка", T.dim, T.menu)
    c.hit(1, c.h, lw, 1, "folder")

    local x0, mw = lw + 3, c.w - lw - 3
    local cur = list[sel]
    local showing = M.idx == sel and M.song
    c.text(x0, 2, G.cut(cur and cur.title or "—", mw), T.fg)
    local chipText = (sound and sound.channels and sound.channels() > 0) and ("звуковой чип, голосов: " .. sound.channels())
      or "звукового чипа нет — играет пищалка, один голос"
    c.text(x0, 3, G.cut(chipText, mw), T.faint)
    -- визуализация: текущая нота каждого голоса — столбик
    local vy, vh = 5, math.max(3, c.h - 12)
    c.fill(x0, vy, mw - 1, vh, T.field)
    if showing then
      local t = elapsed()
      local active = {}
      for _, e in ipairs(M.song.events) do
        if e.t > t then break end
        active[e.ch] = e.f
      end
      local colW = math.max(2, math.min(9, math.floor((mw - 2) / math.max(1, M.song.n))))
      local left = x0 + math.floor((mw - 1 - colW * M.song.n) / 2) - 1
      for ch = 1, M.song.n do
        local f = active[ch]
        if f and M.playing and not M.paused then
          local level = math.max(1, math.min(vh * 2, math.floor((math.log(f) - math.log(55)) / (math.log(1760) - math.log(55)) * vh * 2 + 0.5)))
          local x = left + 1 + (ch - 1) * colW
          local full = math.floor(level / 2)
          local color = ({ T.accent, 0x4fd1e8, 0xc77dff, 0x5fd38a })[(ch - 1) % 4 + 1]
          for k = 0, full - 1 do c.text(x, vy + vh - 1 - k, string.rep("█", colW - 1), color, T.field) end
          if level % 2 == 1 then c.text(x, vy + vh - 1 - full, string.rep("▄", colW - 1), color, T.field) end
        end
      end
    else
      c.text(x0 + 2, vy + math.floor(vh / 2), "Выберите мелодию и нажмите ▶", T.faint, T.field)
    end
    -- полоса времени
    local py = vy + vh + 1
    local total = showing and M.song.total or 0
    local t = showing and elapsed() or 0
    c.text(x0, py, fmtTime(t), T.dim)
    c.progress(x0 + 6, py, mw - 14, total > 0 and t / total or 0)
    c.text(c.w - 6, py, fmtTime(total), T.dim)
    -- кнопки
    local by = py + 2
    local bx = x0
    bx = c.button(bx, by, "◀◀", "prev") + 1
    local playingHere = showing and M.playing and not M.paused
    bx = c.button(bx, by, playingHere and "‖ Пауза" or "▶ Играть", "play", "primary") + 1
    bx = c.button(bx, by, "■", "stop", M.playing and "normal" or "off") + 1
    bx = c.button(bx, by, "▶▶", "next") + 2
    c.check(bx, by, "Повтор", M.loop, "loop")
    c.check(bx + 11, by, "Подряд", M.auto, "auto")
    c.text(x0, by + 2, "Громкость", T.dim)
    for k = 1, 10 do
      local on = k <= math.floor(M.volume * 10 + 0.5)
      c.text(x0 + 10 + k - 1, by + 2, on and "▮" or "▯", on and T.accent or T.faint)
    end
    c.hit(x0 + 10, by + 2, 10, 1, "vol")
    if cur and cur.mine then c.button(c.w - 11, by + 2, "Править", "edit", "ghost") end
    if M.err then c.text(x0, c.h, G.cut(M.err, mw), T.err) end
  end

  local function start(i)
    local ok, err = play(i)
    if not ok and err then M.err = err end
  end

  function app.event(w, ev, a, b, lx)
    if ev == "key" and a == "space" then ev, a = "click", "play" end
    if ev ~= "click" and ev ~= "dblclick" then
      if ev == "focus" then list = playlist() wm.redraw(w) end
      return
    end
    M.err = nil
    if a == "pick" then
      sel = b
      if ev == "dblclick" then start(sel) end
    elseif a == "play" then
      if M.idx == sel and M.playing then pause() else start(sel) end
    elseif a == "stop" then stop()
    elseif a == "prev" then sel = math.max(1, sel - 1) if M.playing then start(sel) end
    elseif a == "next" then sel = math.min(#list, sel + 1) if M.playing then start(sel) end
    elseif a == "loop" then M.loop = not M.loop
    elseif a == "auto" then M.auto = not M.auto
    elseif a == "vol" then
      M.volume = math.max(0.1, math.min(1, (lx - (28 + 3 + 10) + 1) / 10))
      if M.playing and not M.paused then play(M.idx, elapsed()) end
    elseif a == "folder" then
      if not fs.exists(DIR) then fs.makeDir(DIR) end
      wired.apps.launch(wired.apps.get("files"), DIR)
    elseif a == "new" then
      wm.prompt("Своя мелодия", "Название:", "Моя мелодия", function(v)
        if not v or v == "" then return end
        if not fs.exists(DIR) then fs.makeDir(DIR) end
        local p = fs.combine(DIR, v:gsub("[/\\]", "_") .. ".mus")
        if not fs.exists(p) then local f = fs.open(p, "w") f.write(TEMPLATE) f.close() end
        list = playlist()
        wm.open(wired.vterm.app("/rom/programs/edit.lua", { p }, { title = "Правка — " .. fs.getName(p), dir = DIR }))
      end)
    elseif a == "edit" then
      local it = list[sel]
      if it and it.path then
        wm.open(wired.vterm.app("/rom/programs/edit.lua", { it.path }, { title = "Правка — " .. fs.getName(it.path), dir = DIR }))
      end
    end
    wm.redraw(w)
  end

  return app
end
