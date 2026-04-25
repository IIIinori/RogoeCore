// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_damage_reduce.js
var priority = 148
var combatPower = 1
var attributeName = "减伤比例"
var attributeType = "DEFENSE"
var placeholder = "rogue_damage_reduce"

function onLoad(attr) {
  return attr
}

function runDefense(attr, entity, killer, handle) {
  var value = Math.min(90.0, attr.getRandomValue(entity, handle) * 1.0)
  if (value <= 0.0) return false
  var reduced = attr.getDamage(entity, handle) * value / 100.0
  if (reduced <= 0.0) return false
  attr.takeDamage(entity, reduced, handle)
  attr.storageValue(placeholder, reduced, handle)
  return true
}
