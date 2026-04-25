package inori.roguecore.summary

/**
 * 本次 run 的结束原因。
 */
enum class RunEndReason(val displayName: String) {
    ONGOING("进行中"),
    CLEAR("通关结算"),
    DEATH("死亡结算"),
    EXTRACT("安全撤离"),
    LEAVE("主动离开")
}
