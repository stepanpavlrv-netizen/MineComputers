-- OpenOS: библиотека sides
local sides = { bottom = 0, top = 1, back = 2, front = 3, right = 4, left = 5,
  down = 0, up = 1, north = 2, south = 3, west = 4, east = 5,
  negy = 0, posy = 1, negz = 2, posz = 3, negx = 4, posx = 5, forward = 3 }
for i, n in ipairs({ "top", "back", "front", "right", "left" }) do sides[i] = n end
sides[0] = "bottom"
return sides
