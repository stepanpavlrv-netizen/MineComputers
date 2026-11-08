local path = ...
if not path then print("Использование: cat <файл>"); return end
path = shell.resolve(path)
if not fs.exists(path) or fs.isDir(path) then printError("Нет такого файла"); return end
local f = fs.open(path, "r")
local text = f.readAll()
f.close()
print(text)
