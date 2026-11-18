-- SWired: «Пользователи» — кто может входить на сервер (с клавиатуры и по сети), администраторы, пароли.
local G, T, wm, K = wired.gfx, wired.theme, wired.wm, wired.srvkit

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Пользователи — " .. name, icon = "users", w = 96, h = 28, minW = 76, minH = 18 }
  local st = { sel = 1, msg = nil }
  local win
  local LEFT = 36
  local COLORS = { 0x4fd1e8, 0xc77dff, 0xffc857, 0x5fd38a, 0xff8a3d, 0x6b95ff }

  local function reload()
    K.run(win, function() return B.users() end, function(v, e) if v then st.data, st.err = v, nil else st.err = e end end)
  end

  function app.init(w)
    win = w
    reload()
  end

  --- Изменение: спросить пароль администратора (на этом сервере), выполнить, обновить список.
  local function change(fn, ok)
    K.sudo(B, function(pw)
      st.msg = "…"
      wm.redraw(win)
      K.run(win, function() return fn(pw) end, function(v, e)
        st.msg = v and ok or ("Не вышло: " .. tostring(e))
        reload()
      end)
    end)
  end

  local function askPassword(title, done)
    wm.prompt(title, "Новый пароль (хотя бы 4 знака):", "", function(p1)
      if not p1 or p1 == "" then return end
      wm.prompt(title, "Ещё раз тот же пароль:", "", function(p2)
        if p2 == nil then return end
        if p1 ~= p2 then wm.alert(title, "Пароли не совпали — попробуйте ещё раз.") return end
        done(p1)
      end, "•")
    end, "•")
  end

  function app.draw(w, c)
    if K.waiting(c, st) then return end
    local list = st.data
    c.fill(1, 1, LEFT, c.h, T.side)
    if #list == 0 then
      c.text(3, 2, "Пользователей нет:", T.fg, T.side)
      c.text(3, 3, G.cut("сервер открыт для всех.", LEFT - 4), T.warn, T.side)
    end
    st.sel = math.max(1, math.min(st.sel, #list))
    local S = require("swired")
    for i, u in ipairs(list) do
      local y = 1 + (i - 1) * 2
      if y > c.h - 3 then break end
      local sel = i == st.sel
      local bg = sel and T.sel or T.side
      c.fill(1, y, LEFT, 2, bg)
      c.text(2, y, " " .. (u.name:sub(1, 1)):upper() .. " ", T.ink, COLORS[(i - 1) % #COLORS + 1])
      c.text(6, y, G.cut(u.name, LEFT - 20), T.fg, bg)
      if u.admin then c.text(LEFT - 13, y, " админ ", T.ink, 0xc77dff) end
      c.text(6, y + 1, G.cut(u.me and "это вы" or (u.last and ("был(а) " .. S.fmtTime(u.last)) or "ещё не входил(а)"), LEFT - 7), T.dim, bg)
      c.hit(1, y, LEFT, 2, "pick", i)
    end
    c.button(2, c.h, "+ Добавить пользователя", "add", "primary")
    local u = list[st.sel]
    local x, cw = LEFT + 3, c.w - LEFT - 3
    if u then
      c.text(x, 2, G.cut(u.name, cw), T.fg)
      c.text(x, 3, G.cut((u.admin and "администратор" or "пользователь") .. (u.last and (" · последний вход " .. S.fmtTime(u.last)) or ""), cw), T.dim)
      K.card(c, x, 5, cw, 5, "Что можно")
      c.text(x + 1, 6, G.cut("✔ входить с клавиатуры сервера и по сети (connect " .. name .. " " .. u.name .. ")", cw - 2), T.fg, T.side)
      c.text(x + 1, 7, G.cut((u.admin and "✔ " or "✕ ") .. "службы, пользователи, выключение, «Серверы» по сети", cw - 2), u.admin and T.fg or T.dim, T.side)
      c.text(x + 1, 8, G.cut((u.admin and "✔ " or "✕ ") .. "журнал и sudo", cw - 2), u.admin and T.fg or T.dim, T.side)
      K.card(c, x, 11, cw, 4, "Вход и защита")
      c.text(x + 1, 12, G.cut("После 5 неверных паролей подряд адрес закрывается на 10 минут.", cw - 2), T.fg, T.side)
      c.text(x + 1, 13, G.cut("Клиенты хостинга входят по своим логинам — они в «Хостинге».", cw - 2), T.dim, T.side)
      local bx = x
      bx = c.button(bx, 16, "Сменить пароль", "passwd") + 1
      bx = c.button(bx, 16, u.admin and "Снять администратора" or "Сделать администратором", "admin") + 1
      if not u.me then c.button(bx, 16, "Удалить…", "del") end
    end
    if st.msg then c.text(x, c.h - 1, G.cut(st.msg, cw), st.msg:find("^Не") and T.err or T.ok) end
    if B.remote then c.text(x, c.h, G.cut("По сети: изменения — от имени администратора, который вошёл в «Серверы».", cw), T.faint) end
    if st.err then c.text(x, c.h, G.cut("Связь: " .. st.err, cw), T.err) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    local list = st.data or {}
    local u = list[st.sel]
    if a == "pick" then st.sel = b
    elseif a == "add" then
      wm.prompt("Новый пользователь", "Имя (латиницей, без пробелов):", "", function(nm)
        if not nm or nm == "" then return end
        askPassword("Пароль для " .. nm, function(pw)
          wm.confirm("Новый пользователь", "Сделать «" .. nm .. "» администратором сервера?", function(adm)
            change(function(sp) return B.addUser(nm, pw, adm, sp) end, "Добавлен «" .. nm .. "»")
          end, "Да, админ")
        end)
      end)
    elseif not u then return
    elseif a == "passwd" then
      askPassword("Пароль для " .. u.name, function(pw)
        change(function(sp) return B.passwd(u.name, pw, sp) end, "Пароль «" .. u.name .. "» сменён")
      end)
    elseif a == "admin" then
      change(function(sp) return B.setAdmin(u.name, not u.admin, sp) end, u.admin and "«" .. u.name .. "» больше не администратор" or "«" .. u.name .. "» теперь администратор")
    elseif a == "del" then
      wm.confirm("Удалить пользователя", "Удалить «" .. u.name .. "»? Его файлы в /home останутся.", function(ok)
        if ok then change(function(sp) return B.removeUser(u.name, sp) end, "«" .. u.name .. "» удалён") end
      end, "Удалить")
    end
    wm.redraw(w)
  end

  return app
end
