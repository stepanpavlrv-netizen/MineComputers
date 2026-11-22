-- Разлом для Wired: окно-заставка, а сама игра (со своим главным меню) идёт на весь экран.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local DIR = APP_DIR

local LOGO = {
  "█▀█ ▄▀█ ▀█ █   █▀█ █▀▄▀█",
  "█▀▄ █▀█ █▄ █▄▄ █▄█ █ ▀ █",
}

return function()
  local app = { title = "Разлом", icon = "games", w = 66, h = 22, resizable = false }
  local win, lastErr

  local function readKV(p)
    local t = {}
    if fs.exists(p) then
      local f = fs.open(p, "r")
      if f then
        for line in (f.readAll() or ""):gmatch("[^\n]+") do
          local k, v = line:match("^([%w_]+)=(.*)$")
          if k then t[k] = v end
        end
        f.close()
      end
    end
    return t
  end
  local HOME = fs.isDir("/home") and "/home/" or "/"

  local function status()
    local sv = readKV(HOME .. ".razlom_save")
    local rec = tonumber(readKV(HOME .. ".razlom_rec").normal)
    local parts = {}
    if tonumber(sv.lvl) then parts[#parts + 1] = "сохранение: уровень " .. sv.lvl end
    if rec then parts[#parts + 1] = string.format("рекорд: %d:%02d", math.floor(rec / 60), math.floor(rec % 60)) end
    return #parts > 0 and table.concat(parts, " · ") or "Новая игра — с первого уровня"
  end

  local function run(mode)
    local fn, err = loadfile(fs.combine(DIR, "game.lua"))
    if not fn then lastErr = tostring(err) wm.redraw(win) return end
    G.release()                                  -- видеопамять заднего буфера — игре
    local ok, r, msg = pcall(fn, mode, "wired")
    if term and term.setMouseLock then pcall(term.setMouseLock, false) end   -- если игра упала, не отпустив мышь
    if term and term.setEscapeCapture then pcall(term.setEscapeCapture, false) end
    local gpu = component and component.gpu
    if gpu and gpu.clearImage then pcall(gpu.clearImage) end
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
    c.text(3, 7, "Станция «Глубина-7» над кислотным разломом захвачена. Пять уровней:", T.fg)
    c.text(3, 8, "станция, лаборатории, реактор, шахта и само Сердце разлома.", T.fg)
    c.text(3, 9, "Главное меню, настройки и выбор уровня — внутри игры.", T.fg)
    c.text(3, 11, "Мышь обзор · WASD · Shift бег · Ctrl присесть · ЛКМ огонь · ПКМ прицел", T.dim)
    c.text(3, 12, "E пульт · Tab карта · Esc пауза · Shift+Esc — закрыть экран компьютера", T.dim)
    c.text(3, 14, status(), T.warn or 0xffc857)
    local x = c.button(3, 16, "Играть", "play", "primary") + 1
    c.button(x, 16, "Облёт уровня", "demo")
    c.text(3, 18, "Нужна видеокарта MineComputers: чем мощнее, тем чётче и плавнее.", T.faint or T.dim)
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
