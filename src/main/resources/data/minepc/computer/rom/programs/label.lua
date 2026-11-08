local name = ...
if name then
  os.setComputerLabel(name)
  print("Имя компьютера: " .. name)
else
  print("Имя компьютера: " .. (os.getComputerLabel() or "(нет)"))
end
