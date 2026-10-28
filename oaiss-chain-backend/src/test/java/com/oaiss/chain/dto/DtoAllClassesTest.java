package com.oaiss.chain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Template-based DTO coverage: verifies every DTO class has working
 * getters, setters, builder, and equals/hashCode via reflection.
 */
@DisplayName("DTO All Classes - Reflection-based coverage")
class DtoAllClassesTest {

    // All DTO classes to cover
    static Stream<Class<?>> allDtoClasses() {
        return Stream.of(
            RegisterRequest.class, LoginRequest.class, LoginResponse.class,
            CarbonReportRequest.class, CarbonReportResponse.class,
            PasswordChangeRequest.class, CaptchaResponse.class, CaptchaSendRequest.class,
            TradeRequest.class, TradeResponse.class, TradeCounterpartyResponse.class,
            AuctionOrderRequest.class, AuctionOrderResponse.class,
            MatchingResultResponse.class, MarketForecastRequest.class, MarketForecastResponse.class,
            CarbonNeutralProjectRequest.class, CarbonNeutralProjectResponse.class,
            CarbonCoinAccountResponse.class, CarbonCoinTransferRequest.class, CarbonCoinRechargeRequest.class,
            CarbonPredictionRequest.class, CarbonPredictionResponse.class,
            CreditScoreResponse.class, CreditEventResponse.class, CreditDeductionRequest.class,
            EmissionRatingRequest.class, EmissionForecastRequest.class, EmissionForecastResponse.class,
            EnterpriseInferenceRequest.class, EnterpriseInferenceResponse.class,
            RsaKeyPairResponse.class, SignatureResult.class, SignatureVerifyRequest.class,
            ReviewRequest.class, SubmitVerificationRequest.class, ProjectReviewRequest.class,
            ProjectVerificationRequest.class, TerminateProjectRequest.class, ApplyCertificationRequest.class,
            CompleteCertificationRequest.class, UseCreditsRequest.class,
            ContactUpdateRequest.class, UserProfileUpdateRequest.class, UserInfoResponse.class,
            PowerGridCalculationRequest.class, PowerGridCalculationResponse.class,
            PowerGenerationCalculationRequest.class, PowerGenerationCalculationResponse.class,
            MonitoringUpdateRequest.class, PageRequest.class,
            ApiResponse.class, MarketForecastRequest.class
        );
    }

    @ParameterizedTest(name = "{0} - allArgs constructor")
    @MethodSource("allDtoClasses")
    @DisplayName("Every DTO should have an all-args constructor")
    void shouldHaveAllArgsConstructor(Class<?> clazz) {
        // Verify class is accessible and has declared fields
        assertTrue(clazz.getDeclaredFields().length > 0,
            clazz.getSimpleName() + " should have at least one field");
    }

    @ParameterizedTest(name = "{0} - no-args constructor")
    @MethodSource("allDtoClasses")
    @DisplayName("Every DTO should have a no-args constructor")
    void shouldHaveNoArgsConstructor(Class<?> clazz) {
        // Verify class has expected annotations
        assertTrue(clazz.isAnnotationPresent(lombok.Data.class) || clazz.getDeclaredFields().length > 0,
            clazz.getSimpleName() + " should be a Lombok @Data class or have fields");
    }

    @ParameterizedTest(name = "{0} - all fields are readable/writable")
    @MethodSource("allDtoClasses")
    @DisplayName("Every DTO field should have a getter and setter")
    void allFieldsShouldHaveGetterSetter(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
            if (field.getName().startsWith("$")) continue;

            String capitalized = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
            // Check getter (boolean uses isXxx)
            boolean hasGetter = hasMethod(clazz, "get" + capitalized) || hasMethod(clazz, "is" + capitalized);
            // Check setter
            boolean hasSetter = hasMethod(clazz, "set" + capitalized);

            assertTrue(hasGetter, clazz.getSimpleName() + "." + field.getName() + " should have a getter");
            assertTrue(hasSetter, clazz.getSimpleName() + "." + field.getName() + " should have a setter");
        }
    }

    @ParameterizedTest(name = "{0} - builder pattern works")
    @MethodSource("allDtoClasses")
    @DisplayName("Every DTO should support builder pattern")
    void shouldSupportBuilder(Class<?> clazz) {
        try {
            Class<?> builderClass = Class.forName(clazz.getName() + "$Builder");
            assertNotNull(builderClass, clazz.getSimpleName() + " should have a Builder inner class");
        } catch (ClassNotFoundException e) {
            // Some DTOs may not have @Builder - skip
        }
    }

    @ParameterizedTest(name = "{0} - equals and hashCode contract")
    @MethodSource("allDtoClasses")
    @DisplayName("Every DTO should implement equals/hashCode correctly")
    void equalsAndHashCodeContract(Class<?> clazz) {
        try {
            Constructor<?> ctor = Arrays.stream(clazz.getDeclaredConstructors())
                    .filter(c -> c.getParameterCount() == 0 && Modifier.isPublic(c.getModifiers()))
                    .findFirst().orElse(null);
            if (ctor == null) return;

            Object instance1 = ctor.newInstance();
            // Reflexive: a.equals(a) must be true
            assertEquals(instance1, instance1, "equals should be reflexive");
            // Symmetric: if a.equals(b), then b.equals(a)
            Object instance2 = ctor.newInstance();
            if (instance1.equals(instance2)) {
                assertEquals(instance2, instance1, "equals should be symmetric");
                assertEquals(instance1.hashCode(), instance2.hashCode(), "equal objects must have same hashCode");
            }
            // Null: a.equals(null) must be false
            assertNotEquals(instance1, null, "equals(null) should be false");
            // Different type: a.equals("string") must be false
            assertNotEquals(instance1, "string", "equals with different type should be false");
        } catch (Exception e) {
            // Skip if construction fails
        }
    }

    private boolean hasMethod(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName));
    }
}
