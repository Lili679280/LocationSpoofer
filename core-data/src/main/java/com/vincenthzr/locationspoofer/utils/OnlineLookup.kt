package com.vincenthzr.locationspoofer.utils

/** 联网查询周边环境数据（WiGLE Wi-Fi / OpenCellID 基站）的结果。查不到就是查不到，不编造数据 */
sealed interface OnlineLookup {
    /** 查到了：[json] 是数据的 JSON 数组字符串，共 [count] 条 */
    data class Found(val json: String, val count: Int) : OnlineLookup

    /** 请求成功，但这附近没有记录 */
    data object NoData : OnlineLookup

    /** Token 被拒绝（无效、格式不对、账号问题） */
    data object Unauthorized : OnlineLookup

    /** 其他失败：[reason] 由界面换成对应语言的提示，[httpCode] 只在服务器出错时有意义 */
    data class Failed(val reason: FailReason, val httpCode: Int = 0) : OnlineLookup

    enum class FailReason { RATE_LIMITED, SERVER_ERROR, NETWORK }
}

/** 保存 Token 时的联网检测结果 */
enum class TokenCheck {
    VALID,
    INVALID,

    /** 格式不对（WiGLE 只填了 API Token、没有 API 名），没有发请求 */
    BAD_FORMAT,

    /** 连不上服务器，无法判断 */
    NETWORK_ERROR
}
