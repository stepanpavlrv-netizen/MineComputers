-- OpenOS: библиотека filesystem
local filesystem = {}

local function canon(path)
  path = tostring(path or "")
  local abs = path:sub(1, 1) == "/"
  if not abs and _NATIVE_SHELL then path = fs.combine(_NATIVE_SHELL.dir(), path) end
  return "/" .. fs.combine(path, "")
end

function filesystem.canonical(path) return canon(path) end
function filesystem.concat(...)
  local parts = { ... }
  local p = table.concat(parts, "/")
  local lead = p:sub(1, 1) == "/"
  local r = fs.combine(p, "")
  return lead and "/" .. r or r
end
function filesystem.segments(path)
  local out = {}
  for part in tostring(path):gmatch("[^/]+") do out[#out + 1] = part end
  return out
end
function filesystem.name(path)
  local trailing = tostring(path):sub(-1) == "/"
  local n = tostring(path):match("([^/]+)/?$")
  if not n then return nil end
  return trailing and n .. "/" or n
end
function filesystem.path(path)
  local p = tostring(path):match("^(.*/)[^/]+/?$")
  return p or ""
end
function filesystem.exists(path) return fs.exists(canon(path)) end
function filesystem.isDirectory(path) return fs.isDir(canon(path)) end
function filesystem.size(path)
  local p = canon(path)
  return fs.exists(p) and fs.getSize(p) or 0
end
function filesystem.lastModified(path) return fs.lastModified(canon(path)) end
function filesystem.isLink() return false end
function filesystem.link() return nil, "unsupported" end
function filesystem.makeDirectory(path)
  local p = canon(path)
  if fs.exists(p) then return nil, "file or directory with that name already exists" end
  local ok, err = pcall(fs.makeDir, p)
  if not ok then return nil, err end
  return true
end
function filesystem.remove(path)
  local p = canon(path)
  if not fs.exists(p) then return nil, "no such file or directory" end
  local ok, err = pcall(fs.delete, p)
  if not ok then return nil, err end
  return true
end
function filesystem.rename(a, b)
  local ok, err = pcall(fs.move, canon(a), canon(b))
  if not ok then return nil, err end
  return true
end
function filesystem.copy(a, b)
  local pa, pb = canon(a), canon(b)
  if fs.exists(pb) and not fs.isDir(pb) then pcall(fs.delete, pb) end
  local ok, err = pcall(fs.copy, pa, pb)
  if not ok then return nil, err end
  return true
end
function filesystem.list(path)
  local p = canon(path)
  if not fs.isDir(p) then return nil, "no such file or directory" end
  local names = fs.list(p)
  local i = 0
  return function()
    i = i + 1
    local n = names[i]
    if n == nil then return nil end
    return fs.isDir(fs.combine(p, n)) and n .. "/" or n
  end
end
function filesystem.open(path, mode)
  return io.open(canon(path), mode)
end
function filesystem.get(path)
  local component = require("component")
  local drive = fs.getDrive(canon(path)) or "/"
  local addr = computer.componentAddress("fs:" .. drive:gsub("^/", ""))
  return component.proxy(addr), drive
end
function filesystem.mounts()
  local component = require("component")
  local list, i = fs.mounts(), 0
  return function()
    i = i + 1
    local m = list[i]
    if not m then return nil end
    return component.proxy(computer.componentAddress("fs:" .. m.path:gsub("^/", ""))), m.path
  end
end
function filesystem.proxy(filter)
  local component = require("component")
  for addr, t in component.list("filesystem", true) do
    local p = component.proxy(addr)
    if addr:sub(1, #filter) == filter or p.getLabel() == filter then return p end
  end
  return nil, "no such file system"
end
function filesystem.mount() return nil, "unsupported" end
function filesystem.umount() return false end
function filesystem.isAutorunEnabled() return false end
function filesystem.setAutorunEnabled() end
function filesystem.realPath(path) return canon(path) end

return filesystem
