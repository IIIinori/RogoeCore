// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_frost_damage.js
var priority = 140
var combatPower = 1
var attributeName = "冰霜伤害"
var attributeType = "ATTACK"
var placeholder = "rogue_frost_damage"

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
