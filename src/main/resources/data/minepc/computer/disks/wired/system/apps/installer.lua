-- Wired: установка программы с дискеты или флешки (папка app с описанием app.lua).
local G, T, I, U, wm, A = wired.gfx, wired.theme, wired.icons, wired.ui, wired.wm, wired.apps

local NEEDS = {
  net = { "⇄", 0x4fd1e8, "Сеть", "обмен сообщениями с другими компьютерами" },
  camera = { "◉", 0xff4f5e, "Камеры", "смотреть и поворачивать камеры" },
  sound = { "♪", 0xc77dff, "Звук", "проигрывать звуки и музыку" },
  disk = { "▣", 0xffc857, "Диски", "читать и писать дискеты и флешки" },
  io = { "⚡", 0xffc857, "Ввод-вывод", "управлять контроллерами ввода-вывода" },
}

return function(src)
  src = type(src) == "string" and src or "/disk/app"
  local m, err = A.manifest(src)
  local app = { title = "Установка программы", icon = "disk", w = 78, h = 30, resizable = false }
  local st = { phase = m and "ready" or "error", done = 0, total = 0, file = "", desk = true }
  local win
  local art = m and (I.load(fs.combine(src, "icon.pix")) or I.ART[m.icon or ""] or I.ART.app)
  local size = m and A.size(src) or 0

  function app.init(w) win = w end

  function app.draw(w, c)
    if not m then
      c.text(3, 2, "Это не установочный диск", T.fg)
      c.text(3, 3, G.cut(tostring(err), c.w - 4), T.dim)
      c.button(c.w - 10, c.h - 1, "Закрыть", "close")
      return
    end
    c.fill(2, 2, 13, 8, T.sel)
    c.sprite(4, 3, art)
    c.text(18, 2, m.name, T.fg)
    c.text(19 + G.len(m.name), 2, m.version, T.dim)
    local desc = m.description or ""
    c.text(18, 3, G.cut(desc, c.w - 20), T.dim)
    c.text(18, 5, G.cut("Источник: " .. m.dir, c.w - 20), T.faint)
    c.text(18, 6, "Нужна система Wired " .. tostring(m.wired or "1.0") .. " и новее", T.faint)
    local installed = fs.exists("/apps/" .. m.id)
    if installed then c.text(18, 8, "Уже установлена — будет обновлена", T.warn) end
    c.hline(2, 11, c.w - 2)
    c.text(2, 13, "Программе понадобится", T.fg)
    local y = 15
    for _, n in ipairs(m.needs or {}) do
      local d = NEEDS[n]
      if d then
        c.text(3, y, d[1], d[2])
        c.text(5, y, d[3], T.fg)
        c.text(22, y, G.cut(d[4], c.w - 24), T.dim)
        y = y + 1
      end
    end
    local okf, free = pcall(fs.getFreeSpace, "/")
    c.text(3, y, "▤", 0xffc857)
    c.text(5, y, textutils.formatSize(size) .. " на диске", T.fg)
    if okf and free then c.text(22, y, "свободно " .. textutils.formatSize(free), T.dim) end
    c.text(2, 21, "Куда", T.fg)
    c.fill(9, 21, 34, 1, T.field)
    c.text(10, 21, "/apps/" .. m.id, T.fg, T.field)
    c.check(2, 23, "Значок на рабочем столе и в «Пуске»", st.desk, "desk")
    c.hline(2, 25, c.w - 2)
    if st.phase == "copy" then
      c.text(2, 26, G.cut("Копирую файлы…  " .. st.file, c.w - 4), T.dim)
      c.progress(2, 27, c.w - 12, st.total > 0 and st.done / st.total or 0)
      c.text(c.w - 8, 27, math.floor(100 * st.done / math.max(1, st.total)) .. " %", T.fg)
    elseif st.phase == "done" then
      c.text(2, 26, "Готово: «" .. m.name .. "» установлена", T.ok)
      local bx = c.w - 24
      bx = c.button(bx, c.h - 1, "Закрыть", "close") + 1
      c.button(bx, c.h - 1, "Открыть", "open", "primary")
    elseif st.phase == "fail" then
      c.text(2, 26, G.cut("Не установилась: " .. tostring(st.err), c.w - 4), T.err)
      c.button(c.w - 10, c.h - 1, "Закрыть", "close")
    else
      local bx = c.w - 26
      bx = c.button(bx, c.h - 1, "Отмена", "close") + 1
      c.button(bx, c.h - 1, installed and "Обновить" or "Установить", "go", "primary")
    end
  end

  function app.event(w, ev, a)
    if ev == "click" then
      if a == "close" then wm.close(w)
      elseif a == "desk" then st.desk = not st.desk wm.redraw(w)
      elseif a == "open" then wm.close(w) A.launch(A.get(m.id))
      elseif a == "go" and st.phase == "ready" then
        st.phase = "copy"
        wm.redraw(w)
        A.install(src, function(i, n, f)
          st.done, st.total, st.file = i, n, f
          wm.redraw(w)
        end, function(ok, e)
          st.phase = ok and "done" or "fail"
          st.err = e
          wm.redraw(w)
          if ok then wm.notify({ title = "Программа установлена", text = "«" .. m.name .. "» — на рабочем столе и в «Пуске»" }) end
        end)
      end
    elseif ev == "key" and a == "escape" and st.phase ~= "copy" then
      wm.close(w)
    end
  end

  return app
end
