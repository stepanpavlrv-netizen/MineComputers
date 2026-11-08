local from, to = ...
if not to then print("Использование: mv <откуда> <куда>"); return end
from, to = shell.resolve(from), shell.resolve(to)
if fs.isDir(to) then to = fs.combine(to, fs.getName(from)) end
fs.move(from, to)
