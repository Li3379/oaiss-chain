package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import lombok.Getter;

/**
 * 碳核算业务异常
 * Carbon Accounting Exception
 * 
 * @author OAISS Team
 */
@Getter
public class CarbonException extends BusinessException {

    public CarbonException(Integer code, String message) {
        super(code, message);
    }

    public CarbonException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 碳报告不存在
     */
    public static CarbonException reportNotFound(Long reportId) {
        return new CarbonException(ErrorCode.CARBON_REPORT_NOT_FOUND, 
                "碳报告不存在: " + reportId);
    }

    /**
     * 数据提交失败
     */
    public static CarbonException submitFailed(String reason) {
        return new CarbonException(ErrorCode.CARBON_DATA_SUBMIT_FAILED, 
                "碳数据提交失败: " + reason);
    }

    /**
     * 核算计算失败
     */
    public static CarbonException calculationFailed(String reason) {
        return new CarbonException(ErrorCode.CARBON_CALCULATION_FAILED, 
                "碳核算计算失败: " + reason);
    }

    /**
     * 数据格式错误
     */
    public static CarbonException dataFormatError(String field) {
        return new CarbonException(ErrorCode.DATA_FORMAT_ERROR, 
                "数据格式错误: " + field);
    }

    /**
     * 数据缺失
     */
    public static CarbonException dataMissing(String field) {
        return new CarbonException(ErrorCode.DATA_MISSING, 
                "必要数据缺失: " + field);
    }

    /**
     * 数据超出范围
     */
    public static CarbonException dataOutOfRange(String field, Object value) {
        return new CarbonException(ErrorCode.DATA_OUT_OF_RANGE, 
                "数据超出有效范围: " + field + " = " + value);
    }

    /**
     * 报告已提交
     */
    public static CarbonException reportAlreadySubmitted(Long reportId) {
        return new CarbonException(ErrorCode.REPORT_ALREADY_SUBMITTED, 
                "报告已提交，无法修改: " + reportId);
    }

    /**
     * 报告已审核
     */
    public static CarbonException reportAlreadyReviewed(Long reportId) {
        return new CarbonException(ErrorCode.REPORT_ALREADY_REVIEWED, 
                "报告已审核，无法修改: " + reportId);
    }

    /**
     * 签名验证失败
     */
    public static CarbonException signatureVerificationFailed() {
        return new CarbonException(ErrorCode.SIGNATURE_VERIFICATION_FAILED, 
                "RSA签名验证失败");
    }

    /**
     * 排放因子不存在
     */
    public static CarbonException emissionFactorNotFound(String factorCode) {
        return new CarbonException(ErrorCode.EMISSION_FACTOR_NOT_FOUND, 
                "排放因子不存在: " + factorCode);
    }

    /**
     * 核算周期无效
     */
    public static CarbonException invalidAccountingPeriod(String period) {
        return new CarbonException(ErrorCode.INVALID_ACCOUNTING_PERIOD, 
                "核算周期无效: " + period);
    }
}
