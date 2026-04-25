// RogueCore AttributePlus script. Deploy to plugins/AttributePlus/script/RogueCore/rogue_attack_speed.js
var priority = -1
var combatPower = 0.5
var attributeName = "攻击速度"
var attributeType = "OTHER"
var placeholder = "rogue_attack_speed"

function onLoad(attr) {
  return attr
}

function run(attr, entity, handle) {
  try {
    var value = attr.getRandomValue(entity, handle) * 1.0
    var attribute = Packages.org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED
    var inst = entity.getAttribute(attribute)
    if (inst != null) {
      var target = Math.min(20, 4 + value * 0.01)
      inst.setBaseValue(target)
      return true
    }
  } catch (e) {}
  return false
}
