-- OpenOS: библиотека term (поверх экрана MineComputers)
local nt = term              -- встроенный экран MineComputers
local ocTerm = {}

local function size() return nt.getSize() end

function ocTerm.isAvailable() return true end
function ocTerm.gpu() return require("component").gpu end
function ocTerm.screen() return computer.componentAddress("screen") end
function ocTerm.keyboard() return computer.componentAddress("keyboard") end
function ocTerm.getViewport()
  local w, h = size()
  return w, h, 0, 0, 1, 1
end
function ocTerm.getGlobalArea()
  local w, h = size()
  return 1, 1, w, h
end
function ocTerm.clear()
  nt.clear()
  nt.setCursorPos(1, 1)
end
function ocTerm.clearLine()
  nt.clearLine()
  local _, y = nt.getCursorPos()
  nt.setCursorPos(1, y)
end
function ocTerm.getCursor() return nt.getCursorPos() end
function ocTerm.setCursor(x, y) nt.setCursorPos(x, y) end
local blink = false
function ocTerm.getCursorBlink() return blink end
function ocTerm.setCursorBlink(b) blink = b and true or false; nt.setCursorBlink(blink) end
function ocTerm.bind() return true end
function ocTerm.pull(...) return require("event").pull(...) end

local function newline()
  local w, h = size()
  local _, y = nt.getCursorPos()
  if y >= h then
    nt.scroll(1)
    nt.setCursorPos(1, h)
  else
    nt.setCursorPos(1, y + 1)
  end
end

--- Написать текст: \n — новая строка, \t — табуляция; wrap — переносить длинные строки.
function ocTerm.write(value, wrap)
  local text = tostring(value)
  local w = size()
  for piece, sep in text:gmatch("([^\n\r\t]*)([\n\r\t]?)") do
    while #piece > 0 do
      local x = nt.getCursorPos()
      local room = w - x + 1
      if room <= 0 then
        if not wrap then break end
        newline()
        room = w
      end
      local n = unicode.len(piece)
      if n <= room or not wrap then
        nt.write(piece)
        piece = ""
      else
        nt.write(unicode.sub(piece, 1, room))
        piece = unicode.sub(piece, room + 1)
        newline()
      end
    end
    if sep == "\n" then newline()
    elseif sep == "\r" then local _, y = nt.getCursorPos(); nt.setCursorPos(1, y)
    elseif sep == "\t" then
      local x, y = nt.getCursorPos()
      nt.setCursorPos(x + 8 - (x - 1) % 8, y)
    end
    if sep == "" and piece == "" then break end
  end
end

--- Прочитать строку (со строкой \n на конце, как в OpenOS).
function ocTerm.read(history, dobreak, hint, pwchar)
  if type(history) == "table" and history.pwchar then pwchar = history.pwchar end
  local hist = type(history) == "table" and history or nil
  local ok, line = pcall(read, pwchar and unicode.sub(pwchar, 1, 1) or nil, hist)
  if not ok then return nil end
  if hist and line ~= "" then hist[#hist + 1] = line end
  return line .. "\n"
end

return ocTerm
