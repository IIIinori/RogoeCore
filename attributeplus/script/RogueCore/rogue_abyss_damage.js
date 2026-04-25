// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_abyss_damage.js
var priority = 141
var combatPower = 1.15
var attributeName = "深渊伤害"
var attributeType = "ATTACK"
var placeholder = "rogue_abyss_damage"

function onLoad(attr) {
  return attr
}

function runAttack(attr, attacker, entity, handle) {
  var value = attr.getRandomValue(attacker, handle) * 1.0
  if (value <= 0.0) return false
  attr.addDamage(attacker, value, handle)
  attr.storageValue(placeholder, value, handle)
  return true
}
