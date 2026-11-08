local args = { ... }
if #args == 0 then print("Использование: mkdir <папка>"); return end
for _, a in ipairs(args) do fs.makeDir(shell.resolve(a)) end
