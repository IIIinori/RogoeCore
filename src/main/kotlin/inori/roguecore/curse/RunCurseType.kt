package inori.roguecore.curse

enum class RunCurseType(
    val displayName: String,
    val description: String
) {
    FRAGILE("脆弱契印", "最大生命被压低，容错下降"),
    WITHERED("凋敝契印", "所有治疗效果大幅衰减"),
    VULNERABLE("易伤契印", "受到的伤害显著提高"),
    HOLLOW("空蚀契印", "你造成的伤害会被侵蚀削弱")
}
