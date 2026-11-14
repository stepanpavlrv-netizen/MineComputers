-- connect <компьютер> [пользователь] — войти на другой компьютер по сети (там должен работать sshd).
-- Команды выполняются там, вывод — здесь. exit — выйти. Ctrl+T — оборвать сеанс.
local host, user = ...
if not host then
  print("connect <компьютер> [пользователь] — удалённый вход (на том компьютере: sshd)")
  return
end
local function color(c) if term.isColor() then term.setTextColor(c) end end
if not user then
  write("Пользователь: ")
  user = read()
end
write("Пароль: ")
local pw = read("*")

local port = math.random(30000, 39999)
net.open(port)
local function finish()
  net.close(port)
  color(colors.white)
end

--- Ждать ответ kind с номером id (или любой из kinds), не дольше timeout. nil — Ctrl+T, false — время вышло.
local function wait(id, timeout, kinds)
  local timer = timeout and os.startTimer(timeout)
  while true do
    local ev = { os.pullEventRaw() }
    if ev[1] == "terminate" then return nil end
    if ev[1] == "timer" and ev[2] == timer then return false end
    if ev[1] == "net_message" and ev[3] == port and ev[6] == id and kinds[ev[5]] then return ev end
  end
end

local id = math.random(1, 1073741824)
local w, h = term.getSize()
if not net.send(host, 22, "SSH_HELLO", id, port, user, pw, w, h) then
  printError("Нет сети")
  finish()
  return
end
local ev = wait(id, 5, { SSH_OK = true, SSH_FAIL = true })
if ev == nil then finish() return end
if ev == false then
  printError("Нет ответа. На том компьютере должен работать sshd")
  finish()
  return
end
if ev[5] == "SSH_FAIL" then
  local why, secs, fails = ev[7], tonumber(ev[8]) or 0, tonumber(ev[9]) or 0
  if why == "open" then
    printError("На " .. host .. " нет пользователей — вход по сети закрыт")
  elseif why == "locked" then
    printError("Слишком много ошибок: ваш адрес закрыт на " .. math.ceil(secs / 60) .. " мин")
  elseif why == "wait" then
    printError("Слишком часто. Следующая попытка через " .. secs .. " с")
  else
    printError("Неверный пароль (" .. fails .. " из 5)")
    if secs > 0 then print("Следующая попытка через " .. secs .. " с") end
  end
  finish()
  return
end
local sid, name = ev[7], tostring(ev[8] or host)
color(colors.lime)
print("Вы вошли на " .. name .. " как " .. user .. ". exit — выйти")
color(colors.white)

--- Вывод с той стороны: \1x — смена цвета (x — номер цвета в шестнадцатеричном виде)
local function show(text)
  local i = 1
  while i <= #text do
    local j = text:find("\1", i, true)
    if not j then
      write(text:sub(i))
      break
    end
    if j > i then write(text:sub(i, j - 1)) end
    local c = tonumber(text:sub(j + 1, j + 1), 16)
    if c then color(c) end
    i = j + 2
  end
end

local dir = "/"
local history = {}
while true do
  color(colors.lime)
  write(user .. "@" .. name)
  color(colors.white)
  write(":")
  color(colors.lightBlue)
  write(dir)
  color(colors.white)
  write("$ ")
  local ok, line = pcall(read, nil, history)
  if not ok then line = "exit" end
  if line == "exit" or line == "logout" then
    net.send(host, 22, "SSH_BYE", 0, sid)
    print("Сеанс закрыт")
    break
  end
  if line:match("%S") then
    if history[#history] ~= line then history[#history + 1] = line end
    local cid = math.random(1, 1073741824)
    w, h = term.getSize()
    net.send(host, 22, "SSH_CMD", cid, sid, line, w, h, port)
    local broken = false
    while true do
      local e = wait(cid, 120, { SSH_OUT = true, SSH_READ = true, SSH_DONE = true, SSH_END = true })
      if e == nil then
        net.send(host, 22, "SSH_BYE", 0, sid)
        printError("^T — сеанс оборван")
        broken = true
        break
      elseif e == false then
        printError("Нет ответа от " .. name)
        broken = true
        break
      elseif e[5] == "SSH_OUT" then
        show(tostring(e[7] or ""))
      elseif e[5] == "SSH_READ" then
        local replace = e[7] ~= "" and e[7] or nil
        local okr, input = pcall(read, replace)
        net.send(host, 22, "SSH_LINE", cid, sid, okr and input or "")
      elseif e[5] == "SSH_DONE" then
        dir = tostring(e[7] or dir)
        color(colors.white)
        local x = term.getCursorPos()
        if x > 1 then print() end
        break
      elseif e[5] == "SSH_END" then
        printError(tostring(e[7] or "Сеанс закрыт"))
        broken = true
        break
      end
    end
    if broken then break end
  end
end
finish()
