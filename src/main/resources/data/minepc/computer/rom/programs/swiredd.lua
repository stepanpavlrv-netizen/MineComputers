-- Надзиратель SWired: работает в фоне на сервере.
--   swiredd          — запустить (и запускать при каждом включении)
--   swiredd stop     — остановить
--   swiredd status   — работает ли
-- Что делает: перезапускает упавшие службы (до 5 раз за 10 минут), пишет письмо админу, делает резервные копии
-- по расписанию, собирает журнал (входы, железо — от BMC) и отвечает программе «Серверы» по сети (порт 2200,
-- вход — пользователь-администратор этого сервера).
local cmd = ...
local S = require("swired")
local SELF = S.SWIREDD

local function running()
  for _, p in ipairs(computer.processes()) do
    if p.path == SELF and p.pid ~= computer.pid() then return p.pid end
  end
end

if cmd == "stop" then
  local pid = running()
  S.autostartLine(SELF .. " serve", false)
  if pid then computer.kill(pid) print("Надзиратель SWired остановлен") else print("Надзиратель SWired не запущен") end
  return
elseif cmd == "status" then
  print(running() and "Надзиратель SWired работает" or "Надзиратель SWired не запущен")
  return
elseif cmd ~= "serve" then
  S.autostartLine(SELF .. " serve", true)
  if running() then print("Надзиратель SWired уже работает") return end
  computer.spawn(SELF, "serve")
  print("Надзиратель SWired запущен (и будет запускаться при включении)")
  return
end

-- ======================= работа в фоне =======================

local RESTARTS, WINDOW, GRACE = 5, 600, 8
local sessions = {}
-- Состояние служб — в файле (его же правят окна SWired: «Запустить» снимает отметку «упала»); в памяти — только
-- времена последних перезапусков.
do
  local st = S.state()
  -- Сброс — только после включения компьютера: работают те, что в автозапуске. Если надзиратель просто перезапустили
  -- (или запустили вручную, когда службы уже работают), отметки «запущена вручную» не трогаем — иначе он их забывал
  -- и переставал поднимать упавшие службы.
  -- (состояние, записанное в этот же запуск компьютера, помечено им — S.saveState)
  if math.abs((st.boot or 0) - S.bootStamp()) > 5 then
    st.wanted = {}
    st.crashes = {}
    st.failed = {}
    st.started = {}
  end
  S.saveState(st)
end
local recent = {}
local bootAt = os.clock()
S.log("Сервер", "ok", "включён · SWired " .. S.VERSION)

net.open(S.PORT)

local function admin(name)
  for _, u in ipairs(auth and auth.users and auth.users() or {}) do
    if u.name == name then return u.admin end
  end
  -- пользователей нет — компьютер открыт: пускаем только с этого же компьютера, по сети — нет
  return false
end

local function key()
  local t = {}
  for i = 1, 24 do t[i] = string.format("%x", math.random(0, 15)) end
  return table.concat(t)
end

local running_ = {}

--- Надзор: упавшие службы — поднять, если можно; иначе отметить и написать админу.
local function supervise()
  local st = S.state()
  st.wanted, st.started, st.crashes, st.failed = st.wanted or {}, st.started or {}, st.crashes or {}, st.failed or {}
  -- первые секунды после включения BIOS ещё запускает службы из автозапуска: только смотрим
  local watchOnly = os.clock() - bootAt < GRACE
  local cfg = S.config()
  local auto = S.autostartLines()
  local now = os.time()
  local list = computer.processes()
  for _, d in ipairs(S.defs()) do
    local pid = S.pidOf(d.path, list)
    local want = S.wants(st, d, auto)
    local o = cfg.services[d.id] or {}
    if pid then
      if not running_[d.id] then st.started[d.id] = st.started[d.id] or now end
      running_[d.id] = true
    elseif watchOnly then
      -- ждём
    elseif want and not st.failed[d.id] then
      if running_[d.id] then
        S.log("Службы", "err", "«" .. d.name .. "» упала")
      end
      running_[d.id] = false
      st.started[d.id] = nil
      local r = {}
      for _, t in ipairs(recent[d.id] or {}) do if now - t < WINDOW then r[#r + 1] = t end end
      if o.restart ~= false and #r < RESTARTS then
        r[#r + 1] = now
        st.crashes[d.id] = (st.crashes[d.id] or 0) + 1
        local args = {}
        for a in (d.args or ""):gmatch("%S+") do args[#args + 1] = a end
        local ok = pcall(computer.spawn, d.path, table.unpack(args))
        S.log("Службы", ok and "warn" or "err", "«" .. d.name .. "» перезапущена (" .. #r .. "/" .. RESTARTS .. ")")
      elseif o.restart ~= false or running_[d.id] ~= nil then
        st.failed[d.id] = true
        S.log("Службы", "err", "«" .. d.name .. "» не поднимается — надзиратель сдался")
        if o.mail and o.mail ~= "" then
          S.mail(o.mail, "SWired: служба «" .. d.name .. "» упала",
            "Сервер " .. S.status().name .. ": служба «" .. d.name .. "» упала и не поднимается.\nЖурнал — в SWired.")
        end
      end
      recent[d.id] = r
    else
      running_[d.id] = pid ~= nil
    end
  end
  S.saveState(st)
end

--- Резервные копии по расписанию.
local function schedule()
  local now = os.time()
  for _, job in ipairs(S.backups()) do
    local t = S.nextRun(job, now)
    if t and t <= now then
      -- не делать копию за пропущенное время много раз подряд: один раз и дальше по расписанию
      pcall(S.runBackup, job.id)
    end
  end
end

--- Слишком большой ответ (больше 8 КБ) не уйдёт по сети: убрать лишнее.
local function slim(cmd_, v)
  if cmd_ == "status" and type(v) == "table" then
    if v.stats then v.stats.processes = nil end
    if v.hw then v.hw.log = nil end
  end
  return v
end

local function reply(to, port, id, ok, value, extra)
  local okSend, err = pcall(net.send, to, port, "SWIREDOK", id, ok, value, extra)
  if not okSend then pcall(net.send, to, port, "SWIREDOK", id, false, "Ответ слишком большой: " .. tostring(err)) end
end

local function handle(from, port, id, cmd_, session, args)
  args = type(args) == "table" and args or {}
  if cmd_ == "hello" then
    local s = S.status()
    local probs = {}
    for i = 1, math.min(3, #s.problems) do probs[i] = s.problems[i] end
    return reply(from, port, id, true, { name = s.name, version = S.VERSION, level = s.level, problems = probs,
      count = #s.problems, uptime = s.uptime, power = "on" })
  elseif cmd_ == "login" then
    local user, pw = tostring(args.user or ""), tostring(args.password or "")
    local ok, token, why, secs = pcall(auth.check, user, pw, from)
    if not ok or type(token) ~= "string" then
      local msg = "Неверное имя или пароль"
      if why == "wait" then msg = "Слишком часто: подождите " .. tostring(secs or 1) .. " с и попробуйте снова"
      elseif why == "locked" then msg = "После 5 ошибок адрес закрыт на " .. math.ceil((tonumber(secs) or 600) / 60) .. " мин"
      elseif not ok then msg = "Вход не удался: " .. tostring(token) end
      return reply(from, port, id, false, msg)
    end
    if not admin(user) then return reply(from, port, id, false, "«" .. user .. "» — не администратор этого сервера") end
    local k = key()
    -- пароль — только в памяти надзирателя, на время сеанса: им повышаются права для изменения пользователей
    sessions[k] = { user = user, from = from, until_ = os.time() + 3600, token = token, pw = pw }
    S.log("Вход по сети", "ok", "«Серверы»: вошёл " .. user .. " с " .. tostring(from):sub(1, 8))
    -- администратор вошёл — заодно перенести в журнал SWired свежие строки журнала входов
    pcall(auth.as, token)
    pcall(S.importAuth, pw)
    pcall(auth.as, nil)
    return reply(from, port, id, true, k)
  end
  local s = sessions[session or ""]
  if not s or s.from ~= from or s.until_ < os.time() then
    sessions[session or ""] = nil
    return reply(from, port, id, false, "Сеанс закончился — войдите снова", "Сеанс")
  end
  s.until_ = os.time() + 3600
  if cmd_ == "storePart" or cmd_ == "storedBackups" or cmd_ == "fetchPart" then
    local r = table.pack(pcall(S[cmd_], from, table.unpack(args, 1, args.n or #args)))
    if not r[1] then return reply(from, port, id, false, tostring(r[2])) end
    if r[2] == nil and r[3] then return reply(from, port, id, false, tostring(r[3])) end
    return reply(from, port, id, true, r[2], r[3])
  end
  if not S.REMOTE[cmd_] then return reply(from, port, id, false, "Нет такой команды: " .. tostring(cmd_)) end
  if cmd_ == "reboot" or cmd_ == "shutdown" then
    S.log("Сервер", "warn", (cmd_ == "reboot" and "перезагрузка" or "выключение") .. " по сети (" .. s.user .. ")")
    reply(from, port, id, true)
    os.sleep(0.5)
    if cmd_ == "reboot" then os.reboot() else os.shutdown() end
    return
  end
  local USERS = { addUser = 3, removeUser = 1, setAdmin = 2, passwd = 2, importAuth = 0 }
  local r
  if USERS[cmd_] then
    -- изменить пользователей: от имени вошедшего администратора, с повышением прав его паролем
    local a2 = {}
    for i = 1, USERS[cmd_] do a2[i] = args[i] end
    a2[USERS[cmd_] + 1] = s.pw
    pcall(auth.as, s.token)
    r = table.pack(pcall(S[cmd_], table.unpack(a2, 1, USERS[cmd_] + 1)))
    pcall(auth.as, nil)
  else
    r = table.pack(pcall(S[cmd_], table.unpack(args, 1, args.n or #args)))
  end
  if not r[1] then return reply(from, port, id, false, tostring(r[2])) end
  if r[2] == nil and r[3] then return reply(from, port, id, false, tostring(r[3])) end
  reply(from, port, id, true, slim(cmd_, r[2]), r[3])
end

local timer = os.startTimer(1)
local ticks = 0
while true do
  local ev = table.pack(os.pullEvent())
  if ev[1] == "timer" and ev[2] == timer then
    ticks = ticks + 1
    pcall(supervise)
    if ticks % 5 == 1 then
      pcall(function()
        local st = S.state()
        S.importLogs(st)
        S.saveState(st)
      end)
    end
    if ticks % 30 == 2 then pcall(schedule) end
    timer = os.startTimer(2)
  elseif ev[1] == "net_message" and ev[3] == S.PORT and ev[5] == "SWIRED" then
    local ok, err = pcall(handle, ev[2], ev[7], ev[6], ev[8], ev[9], ev[10])
    if not ok then pcall(reply, ev[2], ev[7], ev[6], false, "Ошибка на сервере: " .. tostring(err)) end
  end
end
