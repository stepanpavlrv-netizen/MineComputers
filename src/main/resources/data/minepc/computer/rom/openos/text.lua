-- OpenOS: библиотека text (основное)
local text = {}
function text.trim(s) return (tostring(s):gsub("^%s+", ""):gsub("%s+$", "")) end
function text.padRight(s, n) s = s or ""; return s .. string.rep(" ", n - unicode.len(s)) end
function text.padLeft(s, n) s = s or ""; return string.rep(" ", n - unicode.len(s)) .. s end
function text.detab(s, tab) tab = tab or 8; return (s:gsub("\t", string.rep(" ", tab))) end
function text.split(s, delims, dropEmpty)
  local out = {}
  local pattern = "[^" .. table.concat(delims or { "%s" }) .. "]*"
  for part in s:gmatch(pattern) do
    if not dropEmpty or part ~= "" then out[#out + 1] = part end
  end
  return out
end
function text.tokenize(s)
  local out = {}
  for w in s:gmatch("%S+") do out[#out + 1] = w end
  return out
end
function text.wrap(value, width, maxWidth)
  local line, rest = unicode.sub(value, 1, width), unicode.sub(value, width + 1)
  return line, rest ~= "" and rest or nil, false
end
return text
