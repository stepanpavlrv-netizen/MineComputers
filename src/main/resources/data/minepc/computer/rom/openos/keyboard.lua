-- OpenOS: библиотека keyboard (коды клавиш как в OpenComputers / LWJGL2)
local keyboard = { pressedChars = {}, pressedCodes = {} }

keyboard.keys = {
  ["1"] = 2, ["2"] = 3, ["3"] = 4, ["4"] = 5, ["5"] = 6, ["6"] = 7, ["7"] = 8, ["8"] = 9, ["9"] = 10, ["0"] = 11,
  a = 30, b = 48, c = 46, d = 32, e = 18, f = 33, g = 34, h = 35, i = 23, j = 36, k = 37, l = 38, m = 50,
  n = 49, o = 24, p = 25, q = 16, r = 19, s = 31, t = 20, u = 22, v = 47, w = 17, x = 45, y = 21, z = 44,
  apostrophe = 40, at = 145, back = 14, backslash = 43, capital = 58, colon = 146, comma = 51, enter = 28,
  equals = 13, grave = 41, lbracket = 26, lcontrol = 29, lmenu = 56, lshift = 42, minus = 12,
  numlock = 69, pause = 197, period = 52, rbracket = 27, rcontrol = 157, rmenu = 184, rshift = 54,
  scroll = 70, semicolon = 39, slash = 53, space = 57, stop = 149, tab = 15, underline = 147,
  up = 200, down = 208, left = 203, right = 205, home = 199, ["end"] = 207, pageUp = 201, pageDown = 209,
  insert = 210, delete = 211, f1 = 59, f2 = 60, f3 = 61, f4 = 62, f5 = 63, f6 = 64, f7 = 65, f8 = 66,
  f9 = 67, f10 = 68, f11 = 87, f12 = 88, numpadenter = 156, escape = 1,
}
-- Обратная таблица: код -> имя
for name, code in pairs(keyboard.keys) do
  if type(code) == "number" and not keyboard.keys[code] then keyboard.keys[code] = name end
end

-- Имя клавиши MineComputers -> код OpenComputers
keyboard.native = {
  enter = 28, backspace = 14, tab = 15, space = 57, delete = 211, insert = 210, home = 199, ["end"] = 207,
  pageUp = 201, pageDown = 209, up = 200, down = 208, left = 203, right = 205, ctrl = 29, shift = 42,
  alt = 56, escape = 1,
}
for i = 1, 12 do keyboard.native["f" .. i] = keyboard.keys["f" .. i] end
for ch in ("abcdefghijklmnopqrstuvwxyz0123456789"):gmatch(".") do keyboard.native[ch] = keyboard.keys[ch] end

function keyboard.isKeyDown(charOrCode)
  if type(charOrCode) == "number" then return keyboard.pressedCodes[charOrCode] == true end
  return keyboard.pressedChars[charOrCode] == true
end
function keyboard.isControlDown()
  return keyboard.pressedCodes[29] == true or keyboard.pressedCodes[157] == true
end
function keyboard.isShiftDown()
  return keyboard.pressedCodes[42] == true or keyboard.pressedCodes[54] == true
end
function keyboard.isAltDown()
  return keyboard.pressedCodes[56] == true or keyboard.pressedCodes[184] == true
end
function keyboard.isControl(char)
  return type(char) == "number" and (char < 0x20 or (char >= 0x7F and char <= 0x9F))
end

return keyboard
