package com.oaiss.chain.constant;

/**
 * 错误码常量定义
 * Error Code Constants
 * 
 * 命名规范: [模块][操作][错误类型]
 * 前缀定义:
 *   - COMMON (1xxx): 公共错误
 *   - AUTH (2xxx): 认证授权错误
 *   - CARBON (3xxx): 碳核算模块错误
 *   - TRADE (4xxx): 碳交易模块错误
 *   - BLOCKCHAIN (5xxx): 区块链模块错误
 * 
 * @author OAISS Team
 */
public final class ErrorCode {

    private ErrorCode() {
        // 防止实例化
    }

    // ==================== 公共错误 (1xxx) ====================
    
    /** 系统内部错误 */
    public static final int SYSTEM_ERROR = 1000;
    
    /** 请求参数错误 */
    public static final int PARAM_ERROR = 1001;
    
    /** 资源不存在 */
    public static final int RESOURCE_NOT_FOUND = 1002;
    
    /** 请求方法不支持 */
    public static final int METHOD_NOT_SUPPORTED = 1003;
    
    /** 请求超时 */
    public static final int REQUEST_TIMEOUT = 1004;
    
    /** 服务暂时不可用 */
    public static final int SERVICE_UNAVAILABLE = 1005;
    
    /** 数据库操作失败 */
    public static final int DATABASE_ERROR = 1006;
    
    /** 文件上传失败 */
    public static final int FILE_UPLOAD_ERROR = 1007;
    
    /** 文件大小超限 */
    public static final int FILE_SIZE_EXCEEDED = 1008;

    /** 操作正在处理中 */
    public static final int OPERATION_IN_PROGRESS = 1009;

    /** 请求过于频繁 */
    public static final int REQUEST_TOO_FREQUENT = 1010;

    /** 文件类型不支持 */
    public static final int FILE_TYPE_NOT_SUPPORTED = 1011;

    // ==================== 认证授权错误 (2xxx) ====================
    
    /** 用户未登录 */
    public static final int USER_NOT_LOGIN = 2000;
    
    /** 用户名或密码错误 */
    public static final int LOGIN_FAILED = 2001;
    
    /** Token无效 */
    public static final int TOKEN_INVALID = 2002;
    
    /** Token已过期 */
    public static final int TOKEN_EXPIRED = 2003;
    
    /** 无权限访问 */
    public static final int PERMISSION_DENIED = 2004;
    
    /** 账号已被禁用 */
    public static final int ACCOUNT_DISABLED = 2005;
    
    /** 验证码错误 */
    public static final int CAPTCHA_ERROR = 2006;
    
    /** 验证码已过期 */
    public static final int CAPTCHA_EXPIRED = 2007;
    
    /** IP地址验证失败 */
    public static final int IP_VALIDATION_FAILED = 2008;
    
    /** 账号已存在 */
    public static final int ACCOUNT_EXISTS = 2009;
    
    /** 手机号已注册 */
    public static final int PHONE_EXISTS = 2010;
    
    /** 密码强度不足 */
    public static final int PASSWORD_WEAK = 2011;
    
    /** 原密码错误 */
    public static final int OLD_PASSWORD_ERROR = 2012;

    /** 不能禁用自己的账号 */
    public static final int CANNOT_DISABLE_SELF = 2013;

    // ==================== 碳核算模块错误 (3xxx) ====================
    
    /** 碳报告不存在 */
    public static final int CARBON_REPORT_NOT_FOUND = 3000;
    
    /** 碳数据提交失败 */
    public static final int CARBON_DATA_SUBMIT_FAILED = 3001;
    
    /** 碳核算计算失败 */
    public static final int CARBON_CALCULATION_FAILED = 3002;
    
    /** 数据格式错误 */
    public static final int DATA_FORMAT_ERROR = 3003;
    
    /** 数据缺失 */
    public static final int DATA_MISSING = 3004;
    
    /** 数据超出范围 */
    public static final int DATA_OUT_OF_RANGE = 3005;
    
    /** 报告已提交，无法修改 */
    public static final int REPORT_ALREADY_SUBMITTED = 3006;
    
    /** 报告已审核，无法修改 */
    public static final int REPORT_ALREADY_REVIEWED = 3007;
    
    /** RSA签名验证失败 */
    public static final int SIGNATURE_VERIFICATION_FAILED = 3008;
    
    /** 排放因子不存在 */
    public static final int EMISSION_FACTOR_NOT_FOUND = 3009;
    
    /** 核算周期无效 */
    public static final int INVALID_ACCOUNTING_PERIOD = 3010;

    // ==================== 碳交易模块错误 (4xxx) ====================
    
    /** 交易不存在 */
    public static final int TRADE_NOT_FOUND = 4000;
    
    /** 余额不足 */
    public static final int INSUFFICIENT_BALANCE = 4001;
    
    /** 碳配额不足 */
    public static final int INSUFFICIENT_QUOTA = 4002;
    
    /** 拍卖已结束 */
    public static final int AUCTION_ENDED = 4003;
    
    /** 拍卖未开始 */
    public static final int AUCTION_NOT_STARTED = 4004;
    
    /** 出价过低 */
    public static final int BID_TOO_LOW = 4005;
    
    /** 订单已取消 */
    public static final int ORDER_CANCELLED = 4006;
    
    /** 订单已完成 */
    public static final int ORDER_COMPLETED = 4007;
    
    /** 交易双方不能相同 */
    public static final int SAME_PARTY_ERROR = 4008;
    
    /** P2P交易对方不在线 */
    public static final int PEER_OFFLINE = 4009;
    
    /** 交易金额超出限制 */
    public static final int TRADE_AMOUNT_EXCEEDED = 4010;
    
    /** 挂单已存在 */
    public static final int ORDER_ALREADY_EXISTS = 4011;

    // ==================== 区块链模块错误 (5xxx) ====================
    
    /** 区块链连接失败 */
    public static final int BLOCKCHAIN_CONNECTION_FAILED = 5000;
    
    /** 链码调用失败 */
    public static final int CHAINCODE_INVOKE_FAILED = 5001;
    
    /** 交易上链失败 */
    public static final int TX_COMMIT_FAILED = 5002;
    
    /** 区块查询失败 */
    public static final int BLOCK_QUERY_FAILED = 5003;
    
    /** 交易查询失败 */
    public static final int TX_QUERY_FAILED = 5004;
    
    /** 通道不存在 */
    public static final int CHANNEL_NOT_FOUND = 5005;
    
    /** 身份验证失败 */
    public static final int IDENTITY_AUTH_FAILED = 5006;
    
    /** 智能合约执行错误 */
    public static final int SMART_CONTRACT_ERROR = 5007;
    
    /** RSA密钥对生成失败 */
    public static final int RSA_KEY_GENERATION_FAILED = 5008;
    
    /** RSA密钥对不存在 */
    public static final int RSA_KEY_PAIR_NOT_FOUND = 5009;
    
    /** RSA签名失败 */
    public static final int RSA_SIGN_FAILED = 5010;
    
    /** RSA验签失败 */
    public static final int RSA_VERIFY_FAILED = 5011;
    
    /** RSA加密失败 */
    public static final int RSA_ENCRYPT_FAILED = 5012;
    
    /** RSA解密失败 */
    public static final int RSA_DECRYPT_FAILED = 5013;
    
    /** RSA密钥已过期 */
    public static final int RSA_KEY_EXPIRED = 5014;
    
    /** RSA密钥已失效 */
    public static final int RSA_KEY_REVOKED = 5015;

    // ==================== AI预测模块错误 (6xxx) ====================

    /** ML服务不可用 */
    public static final int ML_SERVICE_UNAVAILABLE = 6000;

    /** ML服务调用失败 */
    public static final int ML_SERVICE_ERROR = 6001;

    /** ML预测失败 */
    public static final int ML_PREDICTION_FAILED = 6002;

    /** 数据不足 */
    public static final int INSUFFICIENT_DATA = 6003;
}
