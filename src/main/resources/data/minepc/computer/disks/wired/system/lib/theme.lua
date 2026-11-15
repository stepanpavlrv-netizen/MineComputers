-- Wired: цвета оформления (тёмная и светлая тема, цвет акцента).
local T = {}

local DARK = {
  desk = 0x0d0b14, win = 0x16131f, side = 0x1b1827, title = 0x262037, titleIn = 0x1d1929, bar = 0x110e1a,
  line = 0x2e2842, field = 0x0f0c17, btn = 0x2c2540, btnHi = 0x3a3152, fg = 0xece8f4, dim = 0x8d87a3,
  faint = 0x4d4763, hover = 0x221d31, menu = 0x15121f, shade = 0x060509, ok = 0x5fd38a, warn = 0xffc857,
  err = 0xff4f5e, link = 0x4fd1e8, label = 0xd6d0e4,
}
local LIGHT = {
  desk = 0xd9d4e2, win = 0xf6f4f9, side = 0xebe7f1, title = 0xdcd6e6, titleIn = 0xe9e5ef, bar = 0xe2dde9,
  line = 0xccc5d8, field = 0xffffff, btn = 0xd9d3e3, btnHi = 0xcbc3d8, fg = 0x1d1a24, dim = 0x6b6578,
  faint = 0xa59fb3, hover = 0xe6e1ee, menu = 0xf1eef5, shade = 0xb5aec2, ok = 0x2f9a5a, warn = 0xb07d12,
  err = 0xd23a48, link = 0x1f7f93, label = 0x1d1a24,
}

T.ACCENTS = { 0xff4f5e, 0x4fd1e8, 0xffc857, 0x5fd38a, 0x6b95ff, 0xc77dff }
T.white, T.black = 0xffffff, 0x08070c

--- Смешать два цвета: t = 0 — первый, 1 — второй.
function T.mix(a, b, t)
  local function ch(c, s) return math.floor(c / s) % 256 end
  local r = ch(a, 65536) + (ch(b, 65536) - ch(a, 65536)) * t
  local g = ch(a, 256) + (ch(b, 256) - ch(a, 256)) * t
  local bl = a % 256 + (b % 256 - a % 256) * t
  return math.floor(r + 0.5) * 65536 + math.floor(g + 0.5) * 256 + math.floor(bl + 0.5)
end

function T.apply(s)
  local src = s.theme == "light" and LIGHT or DARK
  for k, v in pairs(src) do T[k] = v end
  T.dark = s.theme ~= "light"
  T.accent = s.accent or T.ACCENTS[1]
  T.sel = T.mix(T.win, T.accent, T.dark and 0.22 or 0.25)
  T.accentDim = T.mix(T.accent, T.black, 0.35)
  T.ink = T.black                -- текст на кнопке цвета акцента
end

return T
