// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_shield_power.js
var priority = 149
var combatPower = 0.8
var attributeName = "护盾强度"
var attributeType = "DEFENSE"
var placeholder = "rogue_shield_power"

function onLoad(attr) {
  return attr
}

function runDefense(attr, entity, killer, handle) {
  var value = attr.getRandomValue(entity, handle) * 1.0
  if (value <= 0.0) return false
  attr.takeDamage(entity, value, handle)
  attr.storageValue(placeholder, value, handle)
  return true
}
