-- Почта для Wired: ящик, письма, ответ, новое письмо.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local M = dofile(fs.combine(APP_DIR, "lib.lua"))

local function compose(to, subject, body)
  local app = { title = "Новое письмо", icon = "mail", w = 70, h = 24, minW = 50, minH = 14 }
  local fTo, fSub = U.field(to or "", "to"), U.field(subject or "", "sub")
  local area = U.area(body or "")
  local focus = (to and to ~= "") and (subject and subject ~= "" and "body" or "sub") or "to"
  local status, busy
  local win

  local function setFocus(f)
    focus = f
    fTo.focused, fSub.focused, area.focused = f == "to", f == "sub", f == "body"
  end
  setFocus(focus)

  function app.init(w) win = w end

  function app.draw(w, c)
    c.text(2, 2, "Кому", T.dim)
    c.field(9, 2, c.w - 10, fTo, "имя@компьютер")
    c.text(2, 4, "Тема", T.dim)
    c.field(9, 4, c.w - 10, fSub)
    U.drawArea(c, 2, 6, c.w - 2, c.h - 8, area)
    c.hit(2, 6, c.w - 2, c.h - 8, "body")
    if status then c.text(2, c.h - 1, G.cut(status, c.w - 26), busy and T.dim or T.err) end
    local bx = c.w - 24
    bx = c.button(bx, c.h - 1, "Отмена", "cancel") + 1
    c.button(bx, c.h - 1, busy and "Отправляю…" or "Отправить", "send", busy and "off" or "primary")
  end

  local function send()
    if busy then return end
    busy, status = true, "Отправляю…"
    wm.redraw(win)
    local to, sub, body = fTo.text, fSub.text, U.areaText(area)
    wm.spawn(function() return M.send(to, sub, body) end, function(ok, r1, r2)
      busy = false
      if ok and r1 then
        wm.close(win, true)
        wm.notify({ title = "✉ Письмо отправлено", text = to .. " — " .. tostring(r2 or "доставлено") })
      else
        status = tostring(ok and r2 or r1)
        wm.redraw(win)
      end
    end)
  end

  function app.event(w, ev, a, b)
    if ev == "click" then
      if a == "to" or a == "sub" or a == "body" then setFocus(a)
      elseif a == "send" then send() return
      elseif a == "cancel" then wm.close(w) return end
    elseif ev == "key" and a == "tab" then
      setFocus(focus == "to" and "sub" or focus == "sub" and "body" or "to")
    elseif ev == "char" or ev == "key" or ev == "paste" then
      if focus == "body" then U.areaEdit(area, ev, a, w.bw - 4)
      else
        local r = U.edit(focus == "to" and fTo or fSub, ev, a)
        if r == "enter" then setFocus(focus == "to" and "sub" or "body") end
      end
    elseif ev == "resize" then
    else return end
    wm.redraw(w)
  end

  return app
end

return function()
  local app = { title = "Почта", icon = "mail", w = 100, h = 32, minW = 70, minH = 16 }
  local st = { box = M.me(), list = {}, sel = nil, scroll = 0, sent = false }
  local win
  local SIDE = 20

  local function dir()
    return st.sent and ("/mail/.sent/" .. M.me()) or ("/mail/" .. st.box)
  end

  local function reload()
    st.list = M.list(dir())
    if st.sel and st.sel > #st.list then st.sel = nil end
  end

  local function boxes()
    local out = { { name = M.me(), label = "Входящие" } }
    if fs.isDir("/mail") then
      for _, n in ipairs(fs.list("/mail")) do
        if n ~= M.me() and n:sub(1, 1) ~= "." and fs.isDir("/mail/" .. n) then out[#out + 1] = { name = n, label = n } end
      end
    end
    return out
  end

  function app.init(w)
    win = w
    reload()
    st.timer = wm.after(5, function() app.tick() end)
  end

  function app.tick()
    if win.closed then return end
    local n = #st.list
    reload()
    if #st.list ~= n then wm.redraw(win) end
    wm.after(5, function() app.tick() end)
  end

  function app.draw(w, c)
    -- панель
    local bx = 2
    bx = c.button(bx, 1, "✉ Написать", "new", "primary") + 1
    local m = st.sel and st.list[st.sel]
    bx = c.button(bx, 1, "↩ Ответить", "reply", (m and not st.sent) and "normal" or "off") + 1
    bx = c.button(bx, 1, "Удалить", "del", m and "normal" or "off") + 1
    local addr = M.me() .. "@" .. M.host()
    local on = M.running()
    local right = on and ("● ящик " .. addr) or "○ ящик выключен"
    c.text(c.w - G.len(right) - (on and 1 or 13), 1, right, on and T.ok or T.dim)
    if not on then c.button(c.w - 11, 1, "Включить", "start", "primary") end
    -- папки
    c.fill(1, 3, SIDE, c.h - 2, T.side)
    local y = 4
    for _, b in ipairs(boxes()) do
      local here = not st.sent and st.box == b.name
      local unread = 0
      for _, x in ipairs(here and st.list or {}) do if x.new then unread = unread + 1 end end
      if here then c.fill(1, y, SIDE, 1, T.sel) c.text(1, y, "▌", T.accent, T.sel) end
      local bg = here and T.sel or T.side
      c.text(3, y, "✉", T.fg, bg)
      c.text(5, y, G.cut(b.label, SIDE - 9), here and T.fg or T.dim, bg)
      if unread > 0 then c.text(SIDE - 3, y, tostring(unread), T.accent, bg) end
      c.hit(1, y, SIDE, 1, "box", b.name)
      y = y + 2
    end
    local here = st.sent
    if here then c.fill(1, y, SIDE, 1, T.sel) c.text(1, y, "▌", T.accent, T.sel) end
    c.text(3, y, "↗", T.fg, here and T.sel or T.side)
    c.text(5, y, "Отправленные", here and T.fg or T.dim, here and T.sel or T.side)
    c.hit(1, y, SIDE, 1, "sent")
    c.text(3, c.h - 1, G.cut(addr, SIDE - 3), T.faint, T.side)
    -- список
    local lx, lw = SIDE + 2, math.floor((c.w - SIDE) * 0.42)
    local rows = c.h - 4
    if #st.list == 0 then c.text(lx, 4, st.sent and "Пока ничего не отправлено" or "Писем нет", T.faint) end
    for i = 1, rows do
      local k = st.scroll + i
      local x = st.list[k]
      if not x then break end
      local yy = 3 + i
      local sel = st.sel == k
      local bg = sel and T.sel or T.win
      if sel then c.fill(lx, yy, lw, 1, T.sel) end
      c.text(lx, yy, x.new and "●" or " ", T.accent, bg)
      local who = st.sent and (x.to or "?") or x.from
      c.text(lx + 2, yy, G.cut(who, 14), (x.new or sel) and T.fg or T.dim, bg)
      c.text(lx + 17, yy, G.cut(x.subject ~= "" and x.subject or "(без темы)", lw - 25), (x.new or sel) and T.fg or T.dim, bg)
      c.text(lx + lw - 6, yy, (x.time:match("(%d+:%d+)") or ""), T.faint, bg)
      c.hit(lx, yy, lw, 1, "pick", k)
    end
    c.scrollbar(lx + lw, 4, rows, st.scroll, #st.list, rows)
    -- письмо
    local rx = lx + lw + 2
    local rw = c.w - rx
    if m then
      c.text(rx, 3, G.cut(m.subject ~= "" and m.subject or "(без темы)", rw), T.fg)
      c.text(rx, 4, G.cut((st.sent and ("Кому: " .. (m.to or "?")) or ("От: " .. m.from)), rw), T.dim)
      c.text(rx, 5, G.cut(m.time, rw), T.faint)
      c.hline(rx, 6, rw)
      local lines = G.wrap(m.body, rw)
      for i, l in ipairs(lines) do
        if 6 + i > c.h then break end
        c.text(rx, 6 + i, l, T.fg)
      end
    else
      c.text(rx, 4, "Выберите письмо", T.faint)
    end
  end

  function app.event(w, ev, a, b)
    if ev == "click" or ev == "dblclick" then
      if a == "new" then wm.open(compose())
      elseif a == "start" then M.start()
      elseif a == "box" then st.box, st.sent, st.sel, st.scroll = b, false, nil, 0 reload()
      elseif a == "sent" then st.sent, st.sel, st.scroll = true, nil, 0 reload()
      elseif a == "pick" then
        st.sel = b
        if not st.sent then M.markRead(st.list[b]) end
      elseif a == "reply" and st.sel then
        local m = st.list[st.sel]
        local quote = "\n\n> " .. table.concat(G.wrap(m.body, 60), "\n> ")
        wm.open(compose(m.from, (m.subject:match("^Re:") and "" or "Re: ") .. m.subject, quote))
      elseif a == "del" and st.sel then
        local m = st.list[st.sel]
        wm.confirm("Удалить письмо", "Удалить «" .. (m.subject ~= "" and m.subject or "без темы") .. "»?", function(ok)
          if ok then fs.delete(m.path) st.sel = nil reload() wm.redraw(w) end
        end, "Удалить")
      end
      wm.redraw(w)
    elseif ev == "scroll" then
      st.scroll = math.max(0, math.min(math.max(0, #st.list - (w.bh - 4)), st.scroll + a))
      wm.redraw(w)
    elseif ev == "key" then
      if a == "down" then st.sel = math.min(#st.list, (st.sel or 0) + 1)
      elseif a == "up" then st.sel = math.max(1, (st.sel or 2) - 1)
      elseif a == "delete" then return app.event(w, "click", "del")
      else return end
      if st.sel and st.list[st.sel] and not st.sent then M.markRead(st.list[st.sel]) end
      wm.redraw(w)
    elseif ev == "focus" then
      reload()
      wm.redraw(w)
    end
  end

  return app
end
