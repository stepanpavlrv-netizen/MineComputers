-- OpenOS: библиотека shell (текущая папка общая с командной строкой MineComputers)
local shell = {}
local aliases = { dir = "ls", list = "ls", move = "mv", rename = "mv", copy = "cp", del = "rm", md = "mkdir",
                  cls = "clear", more = "cat" }
local path = "/bin:/usr/bin:/home/bin:.:/rom/programs"
local cwd = "/"

function shell.getWorkingDirectory()
  if _NATIVE_SHELL then return _NATIVE_SHELL.dir() end
  return cwd
end
function shell.setWorkingDirectory(dir)
  local d = shell.resolve(dir)
  if not fs.isDir(d) then return nil, "not a directory" end
  if _NATIVE_SHELL then _NATIVE_SHELL.setDir(d) end
  cwd = "/" .. fs.combine(d, "")
  return true
end
function shell.getPath() return path end
function shell.setPath(p) path = p end
function shell.getAlias(a) return aliases[a] end
function shell.setAlias(a, v) aliases[a] = v end
function shell.aliases() return pairs(aliases) end

function shell.resolve(p, ext)
  p = tostring(p)
  if ext then
    if p:find("/") then
      local r = shell.resolve(p)
      if fs.exists(r) and not fs.isDir(r) then return r end
      if fs.exists(r .. "." .. ext) then return r .. "." .. ext end
      return nil, "file not found"
    end
    for dir in path:gmatch("[^:]+") do
      local base = dir == "." and shell.getWorkingDirectory() or dir
      local cand = "/" .. fs.combine(base, p)
      if fs.exists(cand .. "." .. ext) and not fs.isDir(cand .. "." .. ext) then return cand .. "." .. ext end
      if fs.exists(cand) and not fs.isDir(cand) then return cand end
    end
    return nil, "file not found"
  end
  if p:sub(1, 1) == "/" then return "/" .. fs.combine(p, "") end
  return "/" .. fs.combine(shell.getWorkingDirectory(), p)
end

--- Разобрать аргументы: обычные слова и ключи (-abc, --name=value).
function shell.parse(...)
  local args, opts = {}, {}
  local stop = false
  for _, a in ipairs({ ... }) do
    if not stop and type(a) == "string" and a == "--" then stop = true
    elseif not stop and type(a) == "string" and a:sub(1, 2) == "--" and #a > 2 then
      local k, v = a:sub(3):match("^([^=]+)=(.*)$")
      if k then opts[k] = v else opts[a:sub(3)] = true end
    elseif not stop and type(a) == "string" and a:sub(1, 1) == "-" and #a > 1 then
      for ch in a:sub(2):gmatch(".") do opts[ch] = true end
    else
      args[#args + 1] = a
    end
  end
  return args, opts
end

local function words(line)
  local out, cur, q = {}, nil, nil
  for ch in line:gmatch(".") do
    if q then
      if ch == q then q = nil else cur = (cur or "") .. ch end
    elseif ch == '"' or ch == "'" then q = ch; cur = cur or ""
    elseif ch:match("%s") then if cur then out[#out + 1] = cur; cur = nil end
    else cur = (cur or "") .. ch end
  end
  if cur then out[#out + 1] = cur end
  return out
end

--- Выполнить команду: shell.execute("prog arg1 arg2") или shell.execute("/path/prog.lua", env, args...).
function shell.execute(command, env, ...)
  local args = table.pack(...)
  local name
  if args.n == 0 then
    local w = words(command)
    name = table.remove(w, 1)
    args = table.pack(table.unpack(w))
  else
    name = command
  end
  if not name then return true end
  name = aliases[name] or name
  if name == "cd" and _NATIVE_SHELL then
    local ok, err = shell.setWorkingDirectory(args[1] or "/home")
    return ok ~= nil, err
  end
  local program = shell.resolve(name, "lua")
  if not program then return false, name .. ": file not found" end
  env = env or setmetatable({}, { __index = _G })
  if getmetatable(env) == nil then setmetatable(env, { __index = _G }) end
  local fn, err = loadfile(program, "t", env)
  if not fn then return false, err end
  local res = table.pack(xpcall(fn, function(e) return debug.traceback and debug.traceback(e, 2) or e end,
    table.unpack(args, 1, args.n)))
  if not res[1] then
    local msg = tostring(res[2])
    if msg ~= "interrupted" then io.stderr:write(msg .. "\n") end
    return false, msg
  end
  return true, table.unpack(res, 2, res.n)
end

return shell
