package inori.roguecore.accessory

enum class AccessoryEffectType(val displayName: String) {
    SOUL_DEBT_INTEREST_REDUCTION("债务减息"),
    SOUL_DEBT_DEADLINE_BONUS("债务宽限"),
    SOUL_DEBT_PENALTY_SHIELD_CHANCE("债务担保"),
    DELAYED_REWARD_BONUS_PERCENT("托管增益"),
    DELAYED_REWARD_ROOM_REDUCTION("托管加速"),
    DELAYED_REWARD_EARLY_KEEP_PERCENT("提前保留"),
    PROPHECY_REWARD_BONUS_PERCENT("预言收益"),
    PROPHECY_MISS_REDUCTION("预言减损"),
    ROUTE_CHAIN_TOLERANCE_BONUS("路线容错"),
    ROUTE_CHAIN_REWARD_BONUS("路线奖励"),
    BOON_OFFER_BONUS("神恩候选"),
    BOON_ECHO_CHANCE("回响概率"),
    BOON_MUTATION_CHANCE("变质概率"),
    ACCESSORY_DROP_CHANCE("饰品掉率"),
    ACCESSORY_RARITY_LUCK("饰品幸运"),
    HIDDEN_ACCESSORY_BONUS("隐藏饰品");
}
