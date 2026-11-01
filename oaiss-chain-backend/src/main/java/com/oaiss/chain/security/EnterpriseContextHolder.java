package com.oaiss.chain.security;

import com.oaiss.chain.enums.UserTypeEnum;

/**
 * 企业上下文持有者
 * Enterprise Context Holder
 * 
 * <p>使用ThreadLocal存储当前请求的企业上下文信息，用于数据隔离</p>
 * <p>Uses ThreadLocal to store the current request's enterprise context for data isolation</p>
 * 
 * @author OAISS Team
 */
public class EnterpriseContextHolder {

    private static final ThreadLocal<Long> ENTERPRISE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_TYPE = new ThreadLocal<>();

    private EnterpriseContextHolder() {
        // 私有构造函数，防止实例化
    }

    /**
     * 设置企业ID
     *
     * @param enterpriseId 企业ID
     */
    public static void setEnterpriseId(Long enterpriseId) {
        ENTERPRISE_ID.set(enterpriseId);
    }

    /**
     * 获取企业ID
     *
     * @return 企业ID，如果未设置则返回null
     */
    public static Long getEnterpriseId() {
        return ENTERPRISE_ID.get();
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置用户类型
     *
     * @param userType 用户类型
     */
    public static void setUserType(Integer userType) {
        USER_TYPE.set(userType);
    }

    /**
     * 获取用户类型
     *
     * @return 用户类型
     */
    public static Integer getUserType() {
        return USER_TYPE.get();
    }

    /**
     * 判断当前用户是否为企业管理员
     *
     * @return 是否为企业管理员
     */
    public static boolean isEnterpriseUser() {
        Integer userType = getUserType();
        return userType != null && userType.equals(UserTypeEnum.ENTERPRISE.getCode());
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return 是否为管理员
     */
    public static boolean isAdmin() {
        Integer userType = getUserType();
        return userType != null && userType.equals(UserTypeEnum.ADMIN.getCode());
    }

    /**
     * 判断当前用户是否为审核员
     *
     * @return 是否为审核员
     */
    public static boolean isReviewer() {
        Integer userType = getUserType();
        return userType != null && userType.equals(UserTypeEnum.REVIEWER.getCode());
    }

    /**
     * 清除当前线程的上下文
     * 必须在请求结束时调用，防止内存泄漏
     */
    public static void clear() {
        ENTERPRISE_ID.remove();
        USER_ID.remove();
        USER_TYPE.remove();
    }

    /**
     * 从JwtUserDetails初始化上下文
     *
     * @param userDetails 用户详情
     */
    public static void initFromJwtUserDetails(JwtUserDetails userDetails) {
        if (userDetails != null) {
            setUserId(userDetails.getUserId());
            setUserType(userDetails.getUserType());
            setEnterpriseId(userDetails.getEnterpriseId());
        }
    }
}
