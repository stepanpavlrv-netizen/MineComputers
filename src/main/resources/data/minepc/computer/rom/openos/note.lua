-- OpenOS: библиотека note — ноты для пищалки (computer.beep).
-- Ноты пишутся как "A4", "c#5", "Eb3"; числа — MIDI-номера (69 = A4 = 440 Гц).
local computer = require("computer")
local note = {}

local names = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" }
local base = { c = 0, d = 2, e = 4, f = 5, g = 7, a = 9, b = 11 }

--- MIDI-номер ноты: из названия ("A4") или частоты (число > 127 считается частотой).
function note.midi(n)
  if type(n) == "string" then
    local letter, acc, oct = n:lower():match("^([a-g])([#b]*)(%-?%d*)$")
    if not letter then error("не понимаю ноту: " .. n, 2) end
    local s = base[letter]
    for ch in acc:gmatch(".") do s = s + (ch == "#" and 1 or -1) end
    return (tonumber(oct) or 4) * 12 + 12 + s
  elseif type(n) == "number" then
    if n > 127 then return math.floor(69 + 12 * math.log(n / 440, 2) + 0.5) end
    return n
  end
  error("нота — строка или число", 2)
end

--- Частота в герцах: из названия ноты или MIDI-номера.
function note.freq(n)
  return 440 * 2 ^ ((note.midi(n) - 69) / 12)
end

--- Название ноты по MIDI-номеру или частоте.
function note.name(n)
  local m = note.midi(n)
  return names[m % 12 + 1] .. tostring(math.floor(m / 12) - 1)
end

--- Длительность в тиках Minecraft (20 в секунду).
function note.ticks(sec)
  return math.floor(sec * 20 + 0.5)
end

--- Сыграть ноту пищалкой. Не ждёт: писки встают в очередь.
function note.play(tone, duration)
  return computer.beep(note.freq(tone), duration or 0.2)
end

return note
