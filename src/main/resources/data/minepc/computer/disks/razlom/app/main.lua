-- Разлом для Wired: окно-заставка, а сама игра идёт на весь экран (пока она открыта, система ждёт).
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local DIR = APP_DIR

local LOGO = {
  "█▀█ ▄▀█ ▀█ █   █▀█ █▀▄▀█",
  "█▀▄ █▀█ █▄ █▄▄ █▄█ █ ▀ █",
}

return function()
  local app = { title = "Разлом", icon = "games", w = 66, h = 22, resizable = false }
  local win, lastErr

  local function record()
    for _, p in ipairs({ "/home/.razlom", "/.razlom" }) do
      if fs.exists(p) then
        local f = fs.open(p, "r")
        if f then
          local v = tonumber(f.readAll())
          f.close()
          if v then return string.format("Рекорд: %d:%02d", math.floor(v / 60), math.floor(v % 60)) end
        end
      end
    end
    return "Рекорда пока нет"
  end

  local function run(mode)
    local fn, err = loadfile(fs.combine(DIR, "game.lua"))
    if not fn then lastErr = tostring(err) wm.redraw(win) return end
    G.release()                                  -- видеопамять заднего буфера — игре
    local ok, r, msg = pcall(fn, mode, "wired")
    wm.layout()                                  -- вернуть буфер и перерисовать рабочий стол
    if not ok then lastErr = tostring(r)
    elseif r == false then lastErr = tostring(msg)
    else lastErr = nil end
    wm.redraw(win)
  end

  function app.init(w) win = w end

  function app.draw(w, c)
    c.fill(1, 1, c.w, 5, 0x14111d)
    for i, l in ipairs(LOGO) do c.text(math.floor((c.w - G.len(l)) / 2) + 1, 1 + i, l, 0xff8a3d, 0x14111d) end
    c.text(3, 7, "Станция над кислотным разломом захвачена. Уничтожьте всех —", T.fg)
    c.text(3, 8, "откроется портал. Настоящее 3D: мост над залом, балконы, взгляд", T.fg)
    c.text(3, 9, "вверх и вниз, прыжок на взрыве плазмы.", T.fg)
    c.text(3, 11, "W/S/A/D ходьба · ←/→ поворот · ↑/↓ взгляд · пробел прыжок", T.dim)
    c.text(3, 12, "Ctrl или F огонь · 1/2/3 оружие · P пауза · Q выход", T.dim)
    c.text(3, 14, record(), T.warn or 0xffc857)
    local x = c.button(3, 16, "Играть", "play", "primary") + 1
    c.button(x, 16, "Облёт уровня", "demo")
    c.text(3, 18, "Нужна видеокарта MineComputers: чем мощнее, тем плавнее.", T.faint or T.dim)
    if lastErr then c.text(3, 20, G.cut("Ошибка: " .. lastErr, c.w - 4), T.err or 0xff4f5e) end
  end

  function app.event(w, ev, a)
    if ev == "click" or ev == "dblclick" then
      if a == "play" then run("play") elseif a == "demo" then run("demo") end
    elseif ev == "key" and a == "enter" then
      run("play")
    end
  end

  return app
end
