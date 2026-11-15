-- Почта: следит за ящиком и сообщает о новых письмах.
local M = dofile(fs.combine(APP_DIR, "lib.lua"))
local seen = {}
local first = true
local rseen, rfirst, tick = {}, true, 0
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
  -- ящик на хостинге: раз в 30 секунд
  tick = tick + 1
  local cfg = M.remote()
  if cfg and (tick % 6 == 1) then
    local list = M.ask(cfg, "mail_list")
    if type(list) == "table" then
      for _, x in ipairs(list) do
        if not rseen[x.id] then
          rseen[x.id] = true
          if not rfirst and x.new then
            wired.wm.notify({ title = "☁ Письмо от " .. tostring(x.from), text = (x.subject ~= "" and x.subject or "(без темы)") .. " — " .. cfg.address,
              actions = { { "Открыть", function() wired.apps.launch(wired.apps.get("mail")) end } } })
          end
        end
      end
      rfirst = false
    end
  end
  local t = os.startTimer(5)
  repeat local _, id = os.pullEvent("timer") until id == t
end
