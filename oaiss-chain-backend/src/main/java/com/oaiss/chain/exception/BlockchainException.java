package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import lombok.Getter;

/**
 * 区块链业务异常
 * Blockchain Exception
 * 
 * @author OAISS Team
 */
@Getter
public class BlockchainException extends BusinessException {

    public BlockchainException(Integer code, String message) {
        super(code, message);
    }

    public BlockchainException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 区块链连接失败
     */
    public static BlockchainException connectionFailed(String reason) {
        return new BlockchainException(ErrorCode.BLOCKCHAIN_CONNECTION_FAILED, 
                "区块链连接失败: " + reason);
    }

    /**
     * 链码调用失败
     */
    public static BlockchainException chaincodeInvokeFailed(String chaincodeName, String reason) {
        return new BlockchainException(ErrorCode.CHAINCODE_INVOKE_FAILED, 
                String.format("链码调用失败: %s - %s", chaincodeName, reason));
    }

    /**
     * 交易上链失败
     */
    public static BlockchainException txCommitFailed(String txId, String reason) {
        return new BlockchainException(ErrorCode.TX_COMMIT_FAILED, 
                String.format("交易上链失败: %s - %s", txId, reason));
    }

    /**
     * 区块查询失败
     */
    public static BlockchainException blockQueryFailed(Long blockNumber, String reason) {
        return new BlockchainException(ErrorCode.BLOCK_QUERY_FAILED, 
                String.format("区块查询失败: #%d - %s", blockNumber, reason));
    }

    /**
     * 交易查询失败
     */
    public static BlockchainException txQueryFailed(String txId, String reason) {
        return new BlockchainException(ErrorCode.TX_QUERY_FAILED, 
                String.format("交易查询失败: %s - %s", txId, reason));
    }

    /**
     * 通道不存在
     */
    public static BlockchainException channelNotFound(String channelName) {
        return new BlockchainException(ErrorCode.CHANNEL_NOT_FOUND, 
                "通道不存在: " + channelName);
    }

    /**
     * 身份验证失败
     */
    public static BlockchainException identityAuthFailed(String userId) {
        return new BlockchainException(ErrorCode.IDENTITY_AUTH_FAILED, 
                "身份验证失败: " + userId);
    }

    /**
     * 智能合约执行错误
     */
    public static BlockchainException smartContractError(String contractName, String reason) {
        return new BlockchainException(ErrorCode.SMART_CONTRACT_ERROR, 
                String.format("智能合约执行错误: %s - %s", contractName, reason));
    }

    // ==================== RSA相关异常 ====================

    /**
     * RSA密钥对生成失败
     */
    public static BlockchainException rsaKeyGenerationFailed(String reason) {
        return new BlockchainException(ErrorCode.RSA_KEY_GENERATION_FAILED, 
                "RSA密钥对生成失败: " + reason);
    }

    /**
     * RSA密钥对不存在
     */
    public static BlockchainException rsaKeyPairNotFound(Long userId) {
        return new BlockchainException(ErrorCode.RSA_KEY_PAIR_NOT_FOUND, 
                "用户RSA密钥对不存在: userId=" + userId);
    }

    /**
     * RSA签名失败
     */
    public static BlockchainException rsaSignFailed(String reason) {
        return new BlockchainException(ErrorCode.RSA_SIGN_FAILED, 
                "RSA签名失败: " + reason);
    }

    /**
     * RSA验签失败
     */
    public static BlockchainException rsaVerifyFailed(String reason) {
        return new BlockchainException(ErrorCode.RSA_VERIFY_FAILED, 
                "RSA验签失败: " + reason);
    }

    /**
     * RSA加密失败
     */
    public static BlockchainException rsaEncryptFailed(String reason) {
        return new BlockchainException(ErrorCode.RSA_ENCRYPT_FAILED, 
                "RSA加密失败: " + reason);
    }

    /**
     * RSA解密失败
     */
    public static BlockchainException rsaDecryptFailed(String reason) {
        return new BlockchainException(ErrorCode.RSA_DECRYPT_FAILED, 
                "RSA解密失败: " + reason);
    }

    /**
     * RSA密钥已过期
     */
    public static BlockchainException rsaKeyExpired(Long userId) {
        return new BlockchainException(ErrorCode.RSA_KEY_EXPIRED, 
                "用户RSA密钥已过期: userId=" + userId);
    }

    /**
     * RSA密钥已失效
     */
    public static BlockchainException rsaKeyRevoked(Long userId) {
        return new BlockchainException(ErrorCode.RSA_KEY_REVOKED, 
                "用户RSA密钥已失效: userId=" + userId);
    }
}
