-- sudo <команда> — выполнить команду с правами администратора (спросит ваш пароль).
-- Нужно для users, passwd <чужое имя>, journal. Права действуют только на эту команду.
local args = { ... }
if #args == 0 then
  print("sudo <команда> — выполнить с правами администратора")
  return
end
if not auth.open() then
  local me = auth.user()
  if not me then printError("Сначала войдите") return end
  write("Пароль " .. me .. ": ")
  local ok, err = auth.sudo(read("*"))
  if not ok then printError(err) return end
else
  auth.sudo("")
end
local ok, err = pcall(shell.run, table.concat(args, " "))
auth.unsudo()
if not ok then printError(err) end
