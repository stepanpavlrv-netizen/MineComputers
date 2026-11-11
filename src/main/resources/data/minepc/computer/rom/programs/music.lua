-- music: проигрыватель мелодий на звуковом чипе платы (без чипа — первым голосом на пищалке).
--   music              — список встроенных мелодий
--   music <имя>        — сыграть встроенную (korobeiniki, ode, elise)
--   music <файл>       — сыграть свою: каждая строка — голос, «tempo 140» — темп (четвертей в минуту)
-- Ноты: «E5/4» — ми пятой октавы четвертью, «C#4/8.» — восьмая с точкой, «R/2» — пауза.
-- Длину можно не писать: берётся длина предыдущей ноты. Q или Ctrl+T — остановить.
local args = { ... }

local TUNES = {
  korobeiniki = {
    title = "Коробейники (народная)", tempo = 150,
    "E5/4 B4/8 C5 D5/4 C5/8 B4 | A4/4 A4/8 C5 E5/4 D5/8 C5 | B4/4. C5/8 D5/4 E5 | C5 A4 A4 R" ..
    " | R/8 D5/4 F5/8 A5/4 G5/8 F5 | E5/4. C5/8 E5/4 D5/8 C5 | B4/4 B4/8 C5 D5/4 E5 | C5 A4 A4 R",
    "E2/8 E3 E2 E3 E2 E3 E2 E3 | A1 A2 A1 A2 A1 A2 A1 A2 | G#1 G#2 G#1 G#2 E2 E3 E2 E3 | A1 A2 A1 A2 A1 A2 A1 A2" ..
    " | D2 D3 D2 D3 D2 D3 D2 D3 | C2 C3 C2 C3 C2 C3 C2 C3 | B1 B2 B1 B2 E2 E3 E2 E3 | A1 A2 A1 A2 A1/4 R",
  },
  ode = {
    title = "Ода к радости (Бетховен)", tempo = 120,
    "E5/4 E5 F5 G5 | G5 F5 E5 D5 | C5 C5 D5 E5 | E5/4. D5/8 D5/2 | E5/4 E5 F5 G5 | G5 F5 E5 D5 | C5 C5 D5 E5 | D5/4. C5/8 C5/2",
    "C3/1 | G2 | C3 | G2 | C3 | G2 | C3 | G2/2 C3",
  },
  elise = {
    title = "К Элизе (Бетховен, начало)", tempo = 70,
    "E5/16 D#5 E5 D#5 E5 B4 D5 C5 | A4/8 R/16 C4 E4 A4 | B4/8 R/16 E4 G#4 B4 | C5/8 R/16 E4 E5 D#5" ..
    " | E5 D#5 E5 B4 D5 C5 | A4/8 R/16 C4 E4 A4 | B4/8 R/16 E4 C5 B4 | A4/4.",
    "R/4. | A2/16 E3 A3 R/8. | E2/16 E3 G#3 R/8. | A2/16 E3 A3 R/8. | R/4. | A2/16 E3 A3 R/8. | E2/16 E3 G#3 R/8. | A2/4.",
  },
}

local function usage()
  print("Мелодии: music korobeiniki | music ode | music elise | music <файл>")
  print("Свой файл: строка — голос, ноты как E5/4 C#4/8. R/2, «tempo 140» — темп.")
end

-- Разобрать голос в список {частота или nil, мс}
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
        f = sound and sound.note and sound.note(name)
        if not f then error("не понимаю ноту «" .. name .. "»", 0) end
      end
      out[#out + 1] = { f, ms }
    end
  end
  return out
end

local tune = args[1] and TUNES[args[1]:lower()]
if not args[1] then usage() return end
if not tune then
  local path = shell and shell.resolve and shell.resolve(args[1]) or args[1]
  if not fs.exists(path) then print("Нет такой мелодии или файла: " .. args[1]) usage() return end
  local f = io.open(path, "r")
  tune = { title = fs.getName(path), tempo = 120 }
  for l in f:lines() do
    local t = l:match("^%s*tempo%s+(%d+)")
    if t then tune.tempo = tonumber(t) elseif l:match("%S") and not l:match("^%s*%-%-") then tune[#tune + 1] = l end
  end
  f:close()
end

local quarter = 60000 / tune.tempo
local voices = {}
for i, line in ipairs(tune) do
  local ok, v = pcall(parse, line, quarter)
  if not ok then print("Голос " .. i .. ": " .. v) return end
  voices[i] = v
end

local chip = sound and sound.channels and sound.channels() > 0
local n = chip and math.min(#voices, sound.channels()) or 1
print("♪ " .. tune.title .. (chip and "" or " (на пищалке: звукового чипа нет)") .. " — Q или Ctrl+T, чтобы остановить")

-- Все голоса — в одну шкалу времени: моменты, где какой-то голос начинает или заканчивает ноту
local events = {}
for ch = 1, n do
  local t = 0
  for _, note in ipairs(voices[ch]) do
    if note[1] then
      events[#events + 1] = { t = t, ch = ch, f = note[1] }
      events[#events + 1] = { t = t + note[2] * 0.9, ch = ch }          -- 10% тишины между нотами
    end
    t = t + note[2]
  end
end
table.sort(events, function(a, b) if a.t ~= b.t then return a.t < b.t end return (a.f and 1 or 0) < (b.f and 1 or 0) end)

local stopped = false
local function waitOr(sec)
  local timer = os.startTimer(sec)
  while true do
    local e = { os.pullEventRaw() }
    if e[1] == "terminate" or (e[1] == "key" and e[2] == "q") then stopped = true return end
    if e[1] == "timer" and e[2] == timer then return end
  end
end

if not chip then
  -- Пищалка: только первый голос, нота за нотой
  for _, note in ipairs(voices[1]) do
    if stopped then break end
    if note[1] then computer.beep(note[1], note[2] * 0.9 / 1000) end
    waitOr(note[2] / 1000)
  end
  return
end

-- Голоса: мелодия — квадрат, бас — треугольник, остальные — пила
local waves = { sound.modes.square, sound.modes.triangle, sound.modes.sawtooth, sound.modes.sine }
for ch = 1, n do
  sound.setWave(ch, waves[(ch - 1) % #waves + 1])
  sound.setVolume(ch, ch == 1 and 0.55 or 0.75)
  sound.setADSR(ch, 4, 120, 0.6, 60)
end
sound.process()

local now, i = 0, 1
while i <= #events and not stopped do
  -- Набираем до полутора секунд звука и отправляем; пока играет — ждём (и слушаем клавишу Q)
  local batchEnd, first = now + 1500, i
  while i <= #events and events[i].t <= batchEnd do
    local e = events[i]
    if e.t > now then sound.delay(e.t - now) now = e.t end
    if e.f then sound.setFrequency(e.ch, e.f) sound.open(e.ch) else sound.close(e.ch) end
    i = i + 1
  end
  if i == first then sound.delay(batchEnd - now) now = batchEnd end   -- длинная пауза
  while true do
    local ok = sound.process()
    if ok then break end
    waitOr(0.1)
    if stopped then break end
  end
  while not stopped and sound.pending() > 1.0 do waitOr(0.1) end
end
if stopped then sound.stop() else while sound.pending() > 0 and not stopped do waitOr(0.1) end end
