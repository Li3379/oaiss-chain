package com.oaiss.chain.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorMessage constant tests")
class ErrorMessageTest {

    @Test
    @DisplayName("Constructor should be private to prevent instantiation")
    void constructor_shouldBePrivate() throws Exception {
        Constructor<?> ctor = null;
        Constructor<?>[] ctors = ErrorMessage.class.getDeclaredConstructors();
        for (Constructor<?> c : ctors) {
            if (c.getParameterCount() == 0) {
                ctor = c;
                break;
            }
        }
        assertNotNull(ctor);
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    @DisplayName("Common error messages should have correct key format")
    void commonErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.system", ErrorMessage.SYSTEM);
        assertEquals("error.param", ErrorMessage.PARAM);
        assertEquals("error.resource.not.found", ErrorMessage.RESOURCE_NOT_FOUND);
        assertEquals("error.method.not.supported", ErrorMessage.METHOD_NOT_SUPPORTED);
        assertEquals("error.request.timeout", ErrorMessage.REQUEST_TIMEOUT);
        assertEquals("error.service.unavailable", ErrorMessage.SERVICE_UNAVAILABLE);
        assertEquals("error.database", ErrorMessage.DATABASE);
        assertEquals("error.file.upload", ErrorMessage.FILE_UPLOAD);
        assertEquals("error.file.size.exceeded", ErrorMessage.FILE_SIZE_EXCEEDED);
        assertEquals("error.file.type.not.supported", ErrorMessage.FILE_TYPE_NOT_SUPPORTED);
        assertEquals("error.request.too.frequent", ErrorMessage.REQUEST_TOO_FREQUENT);
    }

    @Test
    @DisplayName("Auth error messages should have correct key format")
    void authErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.user.not.login", ErrorMessage.USER_NOT_LOGIN);
        assertEquals("error.login.failed", ErrorMessage.LOGIN_FAILED);
        assertEquals("error.token.invalid", ErrorMessage.TOKEN_INVALID);
        assertEquals("error.token.expired", ErrorMessage.TOKEN_EXPIRED);
        assertEquals("error.permission.denied", ErrorMessage.PERMISSION_DENIED);
        assertEquals("error.account.disabled", ErrorMessage.ACCOUNT_DISABLED);
        assertEquals("error.captcha.error", ErrorMessage.CAPTCHA_ERROR);
        assertEquals("error.captcha.expired", ErrorMessage.CAPTCHA_EXPIRED);
        assertEquals("error.account.exists", ErrorMessage.ACCOUNT_EXISTS);
        assertEquals("error.phone.exists", ErrorMessage.PHONE_EXISTS);
        assertEquals("error.password.weak", ErrorMessage.PASSWORD_WEAK);
        assertEquals("error.old.password.error", ErrorMessage.OLD_PASSWORD_ERROR);
        assertEquals("error.cannot.disable.self", ErrorMessage.CANNOT_DISABLE_SELF);
    }

    @Test
    @DisplayName("Carbon error messages should have correct key format")
    void carbonErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.carbon.report.not.found", ErrorMessage.CARBON_REPORT_NOT_FOUND);
        assertEquals("error.carbon.data.submit.failed", ErrorMessage.CARBON_DATA_SUBMIT_FAILED);
        assertEquals("error.carbon.calculation.failed", ErrorMessage.CARBON_CALCULATION_FAILED);
        assertEquals("error.data.format", ErrorMessage.DATA_FORMAT);
        assertEquals("error.data.missing", ErrorMessage.DATA_MISSING);
        assertEquals("error.data.out.of.range", ErrorMessage.DATA_OUT_OF_RANGE);
        assertEquals("error.report.already.submitted", ErrorMessage.REPORT_ALREADY_SUBMITTED);
        assertEquals("error.report.already.reviewed", ErrorMessage.REPORT_ALREADY_REVIEWED);
        assertEquals("error.emission.rating.exists", ErrorMessage.EMISSION_RATING_EXISTS);
        assertEquals("error.report.on.chain", ErrorMessage.REPORT_ON_CHAIN);
        assertEquals("error.report.draft.review", ErrorMessage.REPORT_DRAFT_REVIEW);
        assertEquals("error.emission.factor.not.found", ErrorMessage.EMISSION_FACTOR_NOT_FOUND);
        assertEquals("error.invalid.accounting.period", ErrorMessage.INVALID_ACCOUNTING_PERIOD);
    }

    @Test
    @DisplayName("Trade error messages should have correct key format")
    void tradeErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.trade.not.found", ErrorMessage.TRADE_NOT_FOUND);
        assertEquals("error.insufficient.balance", ErrorMessage.INSUFFICIENT_BALANCE);
        assertEquals("error.insufficient.quota", ErrorMessage.INSUFFICIENT_QUOTA);
        assertEquals("error.auction.ended", ErrorMessage.AUCTION_ENDED);
        assertEquals("error.auction.not.started", ErrorMessage.AUCTION_NOT_STARTED);
        assertEquals("error.bid.too.low", ErrorMessage.BID_TOO_LOW);
        assertEquals("error.order.cancelled", ErrorMessage.ORDER_CANCELLED);
        assertEquals("error.order.completed", ErrorMessage.ORDER_COMPLETED);
        assertEquals("error.same.party", ErrorMessage.SAME_PARTY);
        assertEquals("error.peer.offline", ErrorMessage.PEER_OFFLINE);
        assertEquals("error.trade.amount.exceeded", ErrorMessage.TRADE_AMOUNT_EXCEEDED);
        assertEquals("error.order.exists", ErrorMessage.ORDER_EXISTS);
    }

    @Test
    @DisplayName("Blockchain error messages should have correct key format")
    void blockchainErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.blockchain.connection", ErrorMessage.BLOCKCHAIN_CONNECTION);
        assertEquals("error.chaincode.invoke", ErrorMessage.CHAINCODE_INVOKE);
        assertEquals("error.tx.commit", ErrorMessage.TX_COMMIT);
        assertEquals("error.block.query", ErrorMessage.BLOCK_QUERY);
        assertEquals("error.tx.query", ErrorMessage.TX_QUERY);
        assertEquals("error.channel.not.found", ErrorMessage.CHANNEL_NOT_FOUND);
        assertEquals("error.identity.auth", ErrorMessage.IDENTITY_AUTH);
        assertEquals("error.smart.contract", ErrorMessage.SMART_CONTRACT);
        assertEquals("error.rsa.key.generation", ErrorMessage.RSA_KEY_GENERATION);
        assertEquals("error.rsa.key.not.found", ErrorMessage.RSA_KEY_NOT_FOUND);
        assertEquals("error.rsa.sign", ErrorMessage.RSA_SIGN);
        assertEquals("error.rsa.verify", ErrorMessage.RSA_VERIFY);
        assertEquals("error.rsa.encrypt", ErrorMessage.RSA_ENCRYPT);
        assertEquals("error.rsa.decrypt", ErrorMessage.RSA_DECRYPT);
        assertEquals("error.rsa.key.expired", ErrorMessage.RSA_KEY_EXPIRED);
        assertEquals("error.rsa.key.revoked", ErrorMessage.RSA_KEY_REVOKED);
    }

    @Test
    @DisplayName("ML error messages should have correct key format")
    void mlErrorMessages_shouldHaveCorrectFormat() {
        assertEquals("error.ml.service.unavailable", ErrorMessage.ML_SERVICE_UNAVAILABLE);
        assertEquals("error.ml.service.error", ErrorMessage.ML_SERVICE_ERROR);
        assertEquals("error.ml.prediction.failed", ErrorMessage.ML_PREDICTION_FAILED);
        assertEquals("error.insufficient.data", ErrorMessage.INSUFFICIENT_DATA);
    }

    @Test
    @DisplayName("All message keys should start with 'error.' prefix")
    void allMessageKeys_shouldStartWithErrorPrefix() {
        String[] keys = {
            ErrorMessage.SYSTEM, ErrorMessage.PARAM, ErrorMessage.RESOURCE_NOT_FOUND,
            ErrorMessage.METHOD_NOT_SUPPORTED, ErrorMessage.REQUEST_TIMEOUT,
            ErrorMessage.SERVICE_UNAVAILABLE, ErrorMessage.DATABASE, ErrorMessage.FILE_UPLOAD,
            ErrorMessage.FILE_SIZE_EXCEEDED, ErrorMessage.FILE_TYPE_NOT_SUPPORTED,
            ErrorMessage.REQUEST_TOO_FREQUENT, ErrorMessage.USER_NOT_LOGIN, ErrorMessage.LOGIN_FAILED,
            ErrorMessage.TOKEN_INVALID, ErrorMessage.TOKEN_EXPIRED, ErrorMessage.PERMISSION_DENIED,
            ErrorMessage.ACCOUNT_DISABLED, ErrorMessage.CAPTCHA_ERROR, ErrorMessage.CAPTCHA_EXPIRED,
            ErrorMessage.ACCOUNT_EXISTS, ErrorMessage.PHONE_EXISTS, ErrorMessage.PASSWORD_WEAK,
            ErrorMessage.OLD_PASSWORD_ERROR, ErrorMessage.CANNOT_DISABLE_SELF
        };
        for (String key : keys) {
            assertTrue(key.startsWith("error."), "Key should start with 'error.': " + key);
        }
    }
}
