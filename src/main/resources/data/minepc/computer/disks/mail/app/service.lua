-- Почта: следит за ящиком и сообщает о новых письмах.
local M = dofile(fs.combine(APP_DIR, "lib.lua"))
local seen = {}
local first = true
while true do
  local box = "/mail/" .. M.me()
  if fs.isDir(box) then
    for _, f in ipairs(fs.list(box)) do
      local path = fs.combine(box, f)
      if not seen[path] then
        seen[path] = true
        if not first then
          local m = M.parse(path)
          if m and m.new then
            wired.wm.notify({ title = "✉ Письмо от " .. m.from, text = m.subject ~= "" and m.subject or "(без темы)",
              actions = { { "Открыть", function() wired.apps.launch(wired.apps.get("mail")) end } } })
          end
        end
      end
    end
  end
  first = false
  local t = os.startTimer(5)
  repeat local _, id = os.pullEvent("timer") until id == t
end
