package com.oaiss.chain.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorCode constant tests")
class ErrorCodeTest {

    @Test
    @DisplayName("Constructor should be private to prevent instantiation")
    void constructor_shouldBePrivate() throws Exception {
        Constructor<ErrorCode> ctor = ErrorCode.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        assertDoesNotThrow(() -> ctor.newInstance());
    }

    @Test
    @DisplayName("Common error codes should have correct values (1xxx)")
    void commonErrorCodes_shouldHaveCorrectValues() {
        assertEquals(1000, ErrorCode.SYSTEM_ERROR);
        assertEquals(1001, ErrorCode.PARAM_ERROR);
        assertEquals(1002, ErrorCode.RESOURCE_NOT_FOUND);
        assertEquals(1003, ErrorCode.METHOD_NOT_SUPPORTED);
        assertEquals(1004, ErrorCode.REQUEST_TIMEOUT);
        assertEquals(1005, ErrorCode.SERVICE_UNAVAILABLE);
        assertEquals(1006, ErrorCode.DATABASE_ERROR);
        assertEquals(1007, ErrorCode.FILE_UPLOAD_ERROR);
        assertEquals(1008, ErrorCode.FILE_SIZE_EXCEEDED);
        assertEquals(1009, ErrorCode.OPERATION_IN_PROGRESS);
        assertEquals(1010, ErrorCode.REQUEST_TOO_FREQUENT);
        assertEquals(1011, ErrorCode.FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("Auth error codes should have correct values (2xxx)")
    void authErrorCodes_shouldHaveCorrectValues() {
        assertEquals(2000, ErrorCode.USER_NOT_LOGIN);
        assertEquals(2001, ErrorCode.LOGIN_FAILED);
        assertEquals(2002, ErrorCode.TOKEN_INVALID);
        assertEquals(2003, ErrorCode.TOKEN_EXPIRED);
        assertEquals(2004, ErrorCode.PERMISSION_DENIED);
        assertEquals(2005, ErrorCode.ACCOUNT_DISABLED);
        assertEquals(2006, ErrorCode.CAPTCHA_ERROR);
        assertEquals(2007, ErrorCode.CAPTCHA_EXPIRED);
        assertEquals(2008, ErrorCode.IP_VALIDATION_FAILED);
        assertEquals(2009, ErrorCode.ACCOUNT_EXISTS);
        assertEquals(2010, ErrorCode.PHONE_EXISTS);
        assertEquals(2011, ErrorCode.PASSWORD_WEAK);
        assertEquals(2012, ErrorCode.OLD_PASSWORD_ERROR);
        assertEquals(2013, ErrorCode.CANNOT_DISABLE_SELF);
    }

    @Test
    @DisplayName("Carbon error codes should have correct values (3xxx)")
    void carbonErrorCodes_shouldHaveCorrectValues() {
        assertEquals(3000, ErrorCode.CARBON_REPORT_NOT_FOUND);
        assertEquals(3001, ErrorCode.CARBON_DATA_SUBMIT_FAILED);
        assertEquals(3002, ErrorCode.CARBON_CALCULATION_FAILED);
        assertEquals(3003, ErrorCode.DATA_FORMAT_ERROR);
        assertEquals(3004, ErrorCode.DATA_MISSING);
        assertEquals(3005, ErrorCode.DATA_OUT_OF_RANGE);
        assertEquals(3006, ErrorCode.REPORT_ALREADY_SUBMITTED);
        assertEquals(3007, ErrorCode.REPORT_ALREADY_REVIEWED);
        assertEquals(3008, ErrorCode.SIGNATURE_VERIFICATION_FAILED);
        assertEquals(3009, ErrorCode.EMISSION_FACTOR_NOT_FOUND);
        assertEquals(3010, ErrorCode.INVALID_ACCOUNTING_PERIOD);
    }

    @Test
    @DisplayName("Trade error codes should have correct values (4xxx)")
    void tradeErrorCodes_shouldHaveCorrectValues() {
        assertEquals(4000, ErrorCode.TRADE_NOT_FOUND);
        assertEquals(4001, ErrorCode.INSUFFICIENT_BALANCE);
        assertEquals(4002, ErrorCode.INSUFFICIENT_QUOTA);
        assertEquals(4003, ErrorCode.AUCTION_ENDED);
        assertEquals(4004, ErrorCode.AUCTION_NOT_STARTED);
        assertEquals(4005, ErrorCode.BID_TOO_LOW);
        assertEquals(4006, ErrorCode.ORDER_CANCELLED);
        assertEquals(4007, ErrorCode.ORDER_COMPLETED);
        assertEquals(4008, ErrorCode.SAME_PARTY_ERROR);
        assertEquals(4009, ErrorCode.PEER_OFFLINE);
        assertEquals(4010, ErrorCode.TRADE_AMOUNT_EXCEEDED);
        assertEquals(4011, ErrorCode.ORDER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Blockchain error codes should have correct values (5xxx)")
    void blockchainErrorCodes_shouldHaveCorrectValues() {
        assertEquals(5000, ErrorCode.BLOCKCHAIN_CONNECTION_FAILED);
        assertEquals(5001, ErrorCode.CHAINCODE_INVOKE_FAILED);
        assertEquals(5002, ErrorCode.TX_COMMIT_FAILED);
        assertEquals(5003, ErrorCode.BLOCK_QUERY_FAILED);
        assertEquals(5004, ErrorCode.TX_QUERY_FAILED);
        assertEquals(5005, ErrorCode.CHANNEL_NOT_FOUND);
        assertEquals(5006, ErrorCode.IDENTITY_AUTH_FAILED);
        assertEquals(5007, ErrorCode.SMART_CONTRACT_ERROR);
        assertEquals(5008, ErrorCode.RSA_KEY_GENERATION_FAILED);
        assertEquals(5009, ErrorCode.RSA_KEY_PAIR_NOT_FOUND);
        assertEquals(5010, ErrorCode.RSA_SIGN_FAILED);
        assertEquals(5011, ErrorCode.RSA_VERIFY_FAILED);
        assertEquals(5012, ErrorCode.RSA_ENCRYPT_FAILED);
        assertEquals(5013, ErrorCode.RSA_DECRYPT_FAILED);
        assertEquals(5014, ErrorCode.RSA_KEY_EXPIRED);
        assertEquals(5015, ErrorCode.RSA_KEY_REVOKED);
    }

    @Test
    @DisplayName("ML error codes should have correct values (6xxx)")
    void mlErrorCodes_shouldHaveCorrectValues() {
        assertEquals(6000, ErrorCode.ML_SERVICE_UNAVAILABLE);
        assertEquals(6001, ErrorCode.ML_SERVICE_ERROR);
        assertEquals(6002, ErrorCode.ML_PREDICTION_FAILED);
        assertEquals(6003, ErrorCode.INSUFFICIENT_DATA);
    }
}
