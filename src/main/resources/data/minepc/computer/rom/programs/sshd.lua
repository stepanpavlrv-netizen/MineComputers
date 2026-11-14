-- Вход по сети (порт 22): с другого компьютера — connect <этот компьютер> [пользователь].
--   sshd        — запустить в фоне
--   sshd stop   — остановить
-- Пускает только пользователей этого компьютера (users add) по паролю; после 5 ошибок адрес закрыт на 10 минут,
-- всё пишется в журнал (sudo journal). При атаке шлёт письмо на адрес из journal notify.
local cmd = ...
local SELF = "/rom/programs/sshd.lua"
local PORT = 22

local function running()
  for _, p in ipairs(computer.processes()) do
    if p.path == SELF and p.pid ~= computer.pid() then return p.pid end
  end
end

local function hostName()
  local ok, names = net.dns("names")
  if ok and names[1] then return names[1].name end
  local h = net.hostname()
  return h ~= "" and h or net.address():sub(1, 8)
end

local function tokenize(line)
  local words, current, quote = {}, nil, nil
  for ch in line:gmatch(".") do
    if quote then
      if ch == quote then quote = nil else current = (current or "") .. ch end
    elseif ch == '"' or ch == "'" then
      quote = ch
      current = current or ""
    elseif ch:match("%s") then
      if current then words[#words + 1] = current; current = nil end
    else
      current = (current or "") .. ch
    end
  end
  if current then words[#words + 1] = current end
  return words
end

local KEYBOARD = { key = true, key_up = true, char = true, paste = true, mouse_click = true, mouse_up = true,
  mouse_drag = true, mouse_scroll = true }

local function serve()
  net.open(PORT)
  local sessions = {}
  local me = hostName()

  -- Выполнить строку команды в сеансе: вывод — по сети, ввод (read) — оттуда же
  local function exec(sess, line, id)
    local out, size, curColor = {}, 0, colors.white
    local function flush()
      if #out == 0 then return end
      net.send(sess.from, sess.reply, "SSH_OUT", id, table.concat(out))
      out, size = {}, 0
    end
    local function emit(s)
      out[#out + 1] = s
      size = size + #s
      if size > 1500 then flush() end
    end
    local function setColor(c)
      if type(c) ~= "number" then return end
      curColor = c
      emit("\1" .. string.format("%x", c % 16))
    end
    local function text(...)
      local parts = {}
      for i = 1, select("#", ...) do parts[i] = tostring((select(i, ...))) end
      return table.concat(parts, "\t")
    end
    local cx, cy = 1, 1
    local fake = setmetatable({
      write = function(s) s = tostring(s) emit(s) cx = cx + unicode.len(s) end,
      blit = function(s) emit(tostring(s)) end,
      getSize = function() return sess.w, sess.h end,
      getCursorPos = function() return cx, cy end,
      setCursorPos = function(x, y)
        if y and y ~= cy then emit("\n") end
        cx, cy = x or cx, y or cy
      end,
      clear = function() end,
      clearLine = function() end,
      scroll = function() end,
      isColor = function() return true end,
      isColour = function() return true end,
      setTextColor = setColor,
      setTextColour = setColor,
      getTextColor = function() return curColor end,
      getTextColour = function() return curColor end,
      setBackgroundColor = function() end,
      setBackgroundColour = function() end,
      getBackgroundColor = function() return colors.black end,
      setCursorBlink = function() end,
    }, { __index = term })
    local env
    local function remoteRead(replace)
      flush()
      net.send(sess.from, sess.reply, "SSH_READ", id, replace or "")
      local deadline = computer.uptime() + 300
      while computer.uptime() < deadline do
        local ev = { os.pullEvent("net_message") }
        if ev[2] == sess.from and ev[3] == PORT and ev[5] == "SSH_LINE" and ev[7] == sess.id then
          return tostring(ev[8] or "")
        end
      end
      error("Нет ответа от вошедшего", 0)
    end
    local sh = {}
    local function runLine(l)
      local words = tokenize(l)
      local name = table.remove(words, 1)
      if not name then return true end
      if name == "cd" then
        local target = words[1] and sh.resolve(words[1]) or ""
        if fs.isDir(target) then sess.dir = target else env.printError("Нет такой папки") end
        return true
      end
      local path = sh.resolveProgram(name)
      if not path then
        env.printError("Нет такой команды: " .. name)
        return false
      end
      local fn, err = loadfile(path, env)
      if not fn then env.printError(err) return false end
      local ok, e = pcall(fn, table.unpack(words))
      if not ok then
        if e and e ~= "" then env.printError(e) end
        return false
      end
      return true
    end
    function sh.dir() return "/" .. sess.dir end
    function sh.setDir(d) sess.dir = fs.combine(d, "") end
    function sh.resolve(p)
      if p:sub(1, 1) == "/" then return fs.combine(p, "") end
      return fs.combine(sess.dir, p)
    end
    function sh.resolveProgram(name)
      local c
      if name:find("/") then
        local p = sh.resolve(name)
        c = { p, p .. ".lua" }
      else
        c = { fs.combine(sess.dir, name), fs.combine(sess.dir, name .. ".lua"), "programs/" .. name .. ".lua",
          "rom/programs/" .. name .. ".lua", "usr/bin/" .. name .. ".lua", "bin/" .. name .. ".lua", "home/bin/" .. name .. ".lua" }
      end
      for _, p in ipairs(c) do
        if fs.exists(p) and not fs.isDir(p) then return p end
      end
    end
    function sh.run(...) return runLine(table.concat({ ... }, " ")) end
    function sh.exit() end
    env = setmetatable({
      term = fake,
      shell = sh,
      write = function(s) s = tostring(s) emit(s) return 0 end,
      print = function(...) emit(text(...) .. "\n") return 1 end,
      printError = function(...)
        local old = curColor
        setColor(colors.red)
        emit(text(...) .. "\n")
        setColor(old)
      end,
      read = remoteRead,
      io = setmetatable({
        write = function(...) for i = 1, select("#", ...) do emit(tostring((select(i, ...)))) end end,
        read = function() return remoteRead() end,
      }, { __index = io }),
      os = setmetatable({
        pullEvent = function(f)
          if f == nil or KEYBOARD[f] then error("В удалённом сеансе нет клавиатуры", 0) end
          return os.pullEvent(f)
        end,
        pullEventRaw = function(f)
          if f == nil or KEYBOARD[f] then error("В удалённом сеансе нет клавиатуры", 0) end
          return os.pullEventRaw(f)
        end,
      }, { __index = os }),
    }, { __index = _G })
    if not auth.as(sess.token) then
      net.send(sess.from, sess.reply, "SSH_END", id, "Сеанс больше не действует — войдите снова")
      sessions[sess.id] = nil
      return
    end
    local ok, err = pcall(runLine, line)
    if not ok then env.printError(err) end
    auth.unsudo()
    auth.as(nil)
    flush()
    net.send(sess.from, sess.reply, "SSH_DONE", id, "/" .. sess.dir)
  end

  local function mailAlert(from)
    local to = auth.notify()
    if to == "" then return end
    local user, host = to:match("^([^@]+)@(.+)$")
    if not host then return end
    local body = "На " .. me .. " подбирают пароль: 5 ошибок подряд с адреса " .. tostring(from) ..
      ".\nАдрес закрыт на 10 минут. Журнал: sudo journal"
    net.send(host, 25, "MAIL", math.random(1, 1073741824), PORT, user, "охрана@" .. me, "Попытка входа", body)
    auth.note("письмо владельцу: «попытка входа» → " .. to)
  end

  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "auth_alert" then
      mailAlert(ev[2])
    elseif ev[1] == "net_message" and ev[3] == PORT then
      local from, kind, id = ev[2], ev[5], ev[6]
      local now = computer.uptime()
      for sid, s in pairs(sessions) do
        if now - s.last > 900 then sessions[sid] = nil end
      end
      if kind == "SSH_HELLO" then
        local reply, user, pw = tonumber(ev[7]), tostring(ev[8] or ""), tostring(ev[9] or "")
        if reply then
          if auth.open() then
            net.send(from, reply, "SSH_FAIL", id, "open")
          else
            local token, why, wait, fails = auth.check(user, pw, from:sub(1, 8))
            if token then
              local sid = string.format("%08x", math.random(0, 2147483647))
              sessions[sid] = { id = sid, token = token, user = user, from = from, reply = reply, dir = "",
                w = tonumber(ev[10]) or 50, h = tonumber(ev[11]) or 16, last = now }
              net.send(from, reply, "SSH_OK", id, sid, me)
            else
              net.send(from, reply, "SSH_FAIL", id, why, wait, fails)
            end
          end
        end
      elseif kind == "SSH_CMD" then
        local s = sessions[ev[7]]
        if s and s.from == from then
          s.last = now
          s.w, s.h = tonumber(ev[9]) or s.w, tonumber(ev[10]) or s.h
          exec(s, tostring(ev[8] or ""), id)
        elseif tonumber(ev[11]) then
          net.send(from, tonumber(ev[11]), "SSH_END", id, "Сеанс закрыт — войдите снова")
        end
      elseif kind == "SSH_BYE" then
        local s = sessions[ev[7]]
        if s and s.from == from then
          auth.note("выход " .. s.user .. " с " .. from:sub(1, 8))
          sessions[ev[7]] = nil
        end
      end
    end
  end
end

if cmd == "serve" then
  serve()
elseif cmd == "stop" then
  local pid = running()
  if pid and computer.kill(pid) then print("Вход по сети выключен") else print("sshd не запущен") end
else
  if auth.open() then
    printError("На компьютере нет пользователей — по сети пускать некого.")
    print("Сначала: users add <имя>")
    return
  end
  local pid = running()
  if pid then print("sshd уже работает (процесс " .. pid .. ")") return end
  pid = computer.spawn(SELF, "serve")
  print("Вход по сети работает (процесс " .. pid .. "). С другого компьютера: connect " .. hostName())
end
