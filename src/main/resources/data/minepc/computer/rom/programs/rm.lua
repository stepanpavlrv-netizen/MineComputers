local args = { ... }
if #args == 0 then print("Использование: rm <путь>"); return end
for _, a in ipairs(args) do
  local p = shell.resolve(a)
  if not fs.exists(p) then printError("Нет такого файла: " .. a) else fs.delete(p) end
end
