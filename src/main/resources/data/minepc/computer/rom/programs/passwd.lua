-- Сменить пароль.
--   passwd         — свой (спросит старый)
--   passwd <имя>   — чужой: администратору после sudo (sudo passwd <имя>)
local name = ... or auth.user()
if not name then
  if auth.open() then print("Пользователей нет. Добавить: users add <имя>") else printError("Сначала войдите") end
  return
end
local old
if not auth.isAdmin() then
  if name ~= auth.user() then printError("Чужой пароль меняет администратор: sudo passwd " .. name) return end
  write("Старый пароль: ")
  old = read("*")
end
write("Новый пароль: ")
local a = read("*")
write("Ещё раз: ")
local b = read("*")
if a ~= b then printError("Пароли не совпали") return end
local ok, err = auth.passwd(name, a, old)
if not ok then printError(err) return end
print("Пароль " .. name .. " изменён")
