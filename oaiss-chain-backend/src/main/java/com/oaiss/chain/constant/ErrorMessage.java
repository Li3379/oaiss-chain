package com.oaiss.chain.constant;

/**
 * 错误消息键常量定义
 * Error Message Key Constants
 *
 * 与 i18n/messages_*.properties 文件中的键对应
 * Keys correspond to i18n/messages_*.properties files
 *
 * @author OAISS Team
 */
public final class ErrorMessage {

    private ErrorMessage() {
        // 防止实例化
    }

    // ==================== 公共错误 (1xxx) ====================

    /** 系统内部错误 */
    public static final String SYSTEM = "error.system";

    /** 请求参数错误 */
    public static final String PARAM = "error.param";

    /** 资源不存在 */
    public static final String RESOURCE_NOT_FOUND = "error.resource.not.found";

    /** 请求方法不支持 */
    public static final String METHOD_NOT_SUPPORTED = "error.method.not.supported";

    /** 请求超时 */
    public static final String REQUEST_TIMEOUT = "error.request.timeout";

    /** 服务暂时不可用 */
    public static final String SERVICE_UNAVAILABLE = "error.service.unavailable";

    /** 数据库操作失败 */
    public static final String DATABASE = "error.database";

    /** 文件上传失败 */
    public static final String FILE_UPLOAD = "error.file.upload";

    /** 文件大小超限 */
    public static final String FILE_SIZE_EXCEEDED = "error.file.size.exceeded";

    /** 文件类型不支持 */
    public static final String FILE_TYPE_NOT_SUPPORTED = "error.file.type.not.supported";

    /** 请求过于频繁 */
    public static final String REQUEST_TOO_FREQUENT = "error.request.too.frequent";

    // ==================== 认证授权错误 (2xxx) ====================

    /** 用户未登录 */
    public static final String USER_NOT_LOGIN = "error.user.not.login";

    /** 用户名或密码错误 */
    public static final String LOGIN_FAILED = "error.login.failed";

    /** Token无效 */
    public static final String TOKEN_INVALID = "error.token.invalid";

    /** Token已过期 */
    public static final String TOKEN_EXPIRED = "error.token.expired";

    /** 无权限访问 */
    public static final String PERMISSION_DENIED = "error.permission.denied";

    /** 账号已被禁用 */
    public static final String ACCOUNT_DISABLED = "error.account.disabled";

    /** 验证码错误 */
    public static final String CAPTCHA_ERROR = "error.captcha.error";

    /** 验证码已过期 */
    public static final String CAPTCHA_EXPIRED = "error.captcha.expired";

    /** 账号已存在 */
    public static final String ACCOUNT_EXISTS = "error.account.exists";

    /** 手机号已注册 */
    public static final String PHONE_EXISTS = "error.phone.exists";

    /** 密码强度不足 */
    public static final String PASSWORD_WEAK = "error.password.weak";

    /** 原密码错误 */
    public static final String OLD_PASSWORD_ERROR = "error.old.password.error";

    /** 不能禁用自己的账号 */
    public static final String CANNOT_DISABLE_SELF = "error.cannot.disable.self";

    // ==================== 碳核算模块错误 (3xxx) ====================

    /** 碳报告不存在 */
    public static final String CARBON_REPORT_NOT_FOUND = "error.carbon.report.not.found";

    /** 碳数据提交失败 */
    public static final String CARBON_DATA_SUBMIT_FAILED = "error.carbon.data.submit.failed";

    /** 碳核算计算失败 */
    public static final String CARBON_CALCULATION_FAILED = "error.carbon.calculation.failed";

    /** 数据格式错误 */
    public static final String DATA_FORMAT = "error.data.format";

    /** 数据缺失 */
    public static final String DATA_MISSING = "error.data.missing";

    /** 数据超出范围 */
    public static final String DATA_OUT_OF_RANGE = "error.data.out.of.range";

    /** 报告已提交，无法修改 */
    public static final String REPORT_ALREADY_SUBMITTED = "error.report.already.submitted";

    /** 报告已审核，无法修改 */
    public static final String REPORT_ALREADY_REVIEWED = "error.report.already.reviewed";

    /** 排放评级已存在 */
    public static final String EMISSION_RATING_EXISTS = "error.emission.rating.exists";

    /** 报告已上链 */
    public static final String REPORT_ON_CHAIN = "error.report.on.chain";

    /** 报告为草稿状态 */
    public static final String REPORT_DRAFT_REVIEW = "error.report.draft.review";

    /** 排放因子不存在 */
    public static final String EMISSION_FACTOR_NOT_FOUND = "error.emission.factor.not.found";

    /** 核算周期无效 */
    public static final String INVALID_ACCOUNTING_PERIOD = "error.invalid.accounting.period";

    // ==================== 碳交易模块错误 (4xxx) ====================

    /** 交易不存在 */
    public static final String TRADE_NOT_FOUND = "error.trade.not.found";

    /** 余额不足 */
    public static final String INSUFFICIENT_BALANCE = "error.insufficient.balance";

    /** 碳配额不足 */
    public static final String INSUFFICIENT_QUOTA = "error.insufficient.quota";

    /** 拍卖已结束 */
    public static final String AUCTION_ENDED = "error.auction.ended";

    /** 拍卖未开始 */
    public static final String AUCTION_NOT_STARTED = "error.auction.not.started";

    /** 出价过低 */
    public static final String BID_TOO_LOW = "error.bid.too.low";

    /** 订单已取消 */
    public static final String ORDER_CANCELLED = "error.order.cancelled";

    /** 订单已完成 */
    public static final String ORDER_COMPLETED = "error.order.completed";

    /** 交易双方不能相同 */
    public static final String SAME_PARTY = "error.same.party";

    /** 交易对方不在线 */
    public static final String PEER_OFFLINE = "error.peer.offline";

    /** 交易金额超出限制 */
    public static final String TRADE_AMOUNT_EXCEEDED = "error.trade.amount.exceeded";

    /** 挂单已存在 */
    public static final String ORDER_EXISTS = "error.order.exists";

    // ==================== 区块链模块错误 (5xxx) ====================

    /** 区块链连接失败 */
    public static final String BLOCKCHAIN_CONNECTION = "error.blockchain.connection";

    /** 链码调用失败 */
    public static final String CHAINCODE_INVOKE = "error.chaincode.invoke";

    /** 交易上链失败 */
    public static final String TX_COMMIT = "error.tx.commit";

    /** 区块查询失败 */
    public static final String BLOCK_QUERY = "error.block.query";

    /** 交易查询失败 */
    public static final String TX_QUERY = "error.tx.query";

    /** 通道不存在 */
    public static final String CHANNEL_NOT_FOUND = "error.channel.not.found";

    /** 身份验证失败 */
    public static final String IDENTITY_AUTH = "error.identity.auth";

    /** 智能合约执行错误 */
    public static final String SMART_CONTRACT = "error.smart.contract";

    /** RSA密钥对生成失败 */
    public static final String RSA_KEY_GENERATION = "error.rsa.key.generation";

    /** RSA密钥对不存在 */
    public static final String RSA_KEY_NOT_FOUND = "error.rsa.key.not.found";

    /** RSA签名失败 */
    public static final String RSA_SIGN = "error.rsa.sign";

    /** RSA验签失败 */
    public static final String RSA_VERIFY = "error.rsa.verify";

    /** RSA加密失败 */
    public static final String RSA_ENCRYPT = "error.rsa.encrypt";

    /** RSA解密失败 */
    public static final String RSA_DECRYPT = "error.rsa.decrypt";

    /** RSA密钥已过期 */
    public static final String RSA_KEY_EXPIRED = "error.rsa.key.expired";

    /** RSA密钥已失效 */
    public static final String RSA_KEY_REVOKED = "error.rsa.key.revoked";

    // ==================== AI预测模块错误 (6xxx) ====================

    /** ML服务不可用 */
    public static final String ML_SERVICE_UNAVAILABLE = "error.ml.service.unavailable";

    /** ML服务调用失败 */
    public static final String ML_SERVICE_ERROR = "error.ml.service.error";

    /** ML预测失败 */
    public static final String ML_PREDICTION_FAILED = "error.ml.prediction.failed";

    /** 数据不足 */
    public static final String INSUFFICIENT_DATA = "error.insufficient.data";
}
