local args = { ... }
local path = shell.resolve(args[1] or ".")
if not fs.isDir(path) then printError("Нет такой папки"); return end
local files, dirs = {}, {}
for _, f in ipairs(fs.list(path)) do
  if fs.isDir(fs.combine(path, f)) then dirs[#dirs + 1] = f else files[#files + 1] = f end
end
local w = term.getSize()
local function columns(list, color)
  if #list == 0 then return end
  local width = 0
  for _, f in ipairs(list) do width = math.max(width, unicode.len(f) + 2) end
  local perLine = math.max(1, math.floor(w / width))
  if term.isColor() then term.setTextColor(color) end
  for i, f in ipairs(list) do
    write(f .. string.rep(" ", width - unicode.len(f)))
    if i % perLine == 0 or i == #list then print() end
  end
  term.setTextColor(colors.white)
end
columns(dirs, colors.green)
columns(files, colors.white)
