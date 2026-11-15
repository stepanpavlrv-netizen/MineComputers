-- Wired: Терминал — обычная командная строка в окне. exit — закрыть окно.
return function(arg)
  -- «Родительская» оболочка: exit закрывает окно, а не выходит из сеанса пользователя
  local parent = { dir = function() return type(arg) == "string" and arg or "/home" end, setDir = function() end }
  return wired.vterm.app("/rom/shell.lua", {}, { title = "Терминал", icon = "term", w = 84, h = 26, shell = parent })
end
