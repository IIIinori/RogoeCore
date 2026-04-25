// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_boss_damage.js
var priority = 146
var combatPower = 1.2
var attributeName = "首领伤害"
var attributeType = "ATTACK"
var placeholder = "rogue_boss_damage"

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
  if (!targetContains(entity, ["boss", "首领", "王", "主", "君", "化身", "领主", "总演", "看守者", "裁定者", "亲王", "主脑", "之心", "炉心", "沼主"])) return false
  var value = attr.getRandomValue(attacker, handle) * 1.0
  if (value <= 0.0) return false
  var damage = attr.getDamage(attacker, handle) * value / 100.0
  if (damage <= 0.0) return false
  attr.addDamage(attacker, damage, handle)
  attr.storageValue(placeholder, damage, handle)
  return true
}
