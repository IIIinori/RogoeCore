// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_elite_damage.js
var priority = 145
var combatPower = 1.1
var attributeName = "精英伤害"
var attributeType = "ATTACK"
var placeholder = "rogue_elite_damage"

function onLoad(attr) {
  return attr
}

function targetContains(entity, words) {
  var text = ""
  try { text = text + " " + String(entity.getName()) } catch (e) {}
  try { if (entity.getCustomName() != null) text = text + " " + String(entity.getCustomName()) } catch (e) {}
  text = text.toLowerCase()
  for (var i = 0; i < words.length; i++) {
    if (text.indexOf(String(words[i]).toLowerCase()) >= 0) return true
  }
  return false
}

function runAttack(attr, attacker, entity, handle) {
  if (!targetContains(entity, ["精英", "elite"])) return false
  var value = attr.getRandomValue(attacker, handle) * 1.0
  if (value <= 0.0) return false
  var damage = attr.getDamage(attacker, handle) * value / 100.0
  if (damage <= 0.0) return false
  attr.addDamage(attacker, damage, handle)
  attr.storageValue(placeholder, damage, handle)
  return true
}
