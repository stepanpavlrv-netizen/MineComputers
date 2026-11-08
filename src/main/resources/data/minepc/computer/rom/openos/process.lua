-- OpenOS: библиотека process (минимально)
local process = {}
function process.info() return { path = "/", command = "", data = { vars = {}, handles = {} } } end
function process.running() return "/" end
function process.findProcess() return nil end
return process
