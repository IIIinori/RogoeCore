// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_elite_damage.js
var priority = 145
var combatPower = 1.1
var attributeName = "精英伤害"
var attributeType = "ATTACK"
var placeholder = "rogue_elite_damage"

function onLoad(attr) {
  return attr
}

function runAttack(attr, attacker, entity, handle) {
  var value = attr.getRandomValue(attacker, handle) * 1.0
  if (value <= 0.0) return false
  var damage = attr.getDamage(attacker, handle) * value / 100.0
  if (damage <= 0.0) return false
  attr.addDamage(attacker, damage, handle)
  attr.storageValue(placeholder, damage, handle)
  return true
}
