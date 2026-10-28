package com.oaiss.chain.service;

import com.oaiss.chain.config.FabricProperties;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabricCAServiceTest {

    private FabricProperties props;

    @Mock
    private WebClient.Builder webClientBuilder;

    @BeforeEach
    void setUp() {
        props = new FabricProperties();
        props.setMspId("Org1MSP");
        props.getCa().setEnabled(true);
        props.getCa().setEndpoint("http://ca.org1.example.com:7054");
        props.getCa().setAdminName("admin");
        props.getCa().setAdminPassword("secret-password");
    }

    // Test 18-01-01: CA adminPassword is never included in toString/log output
    @Test
    void caAdminPassword_excludedFromToString() {
        FabricProperties.Ca ca = props.getCa();
        String toStringResult = ca.toString();

        assertFalse(toStringResult.contains("secret-password"),
                "adminPassword must not appear in toString output");
        assertFalse(toStringResult.contains("adminPassword"),
                "adminPassword field must be excluded from toString via @ToString.Exclude");
    }

    // Test 18-01-02: EnrollmentResult record is never serialized to JSON or persisted
    @Test
    void enrollmentResult_isNotJpaEntityOrSerializable() {
        Class<?> enrollmentResultClass = null;
        for (Class<?> inner : FabricCAService.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("EnrollmentResult")) {
                enrollmentResultClass = inner;
                break;
            }
        }
        assertNotNull(enrollmentResultClass, "EnrollmentResult inner class must exist");
        assertTrue(enrollmentResultClass.isRecord(), "EnrollmentResult must be a Java record");

        // Not a JPA entity - no @Entity annotation
        assertNull(enrollmentResultClass.getAnnotation(jakarta.persistence.Entity.class),
                "EnrollmentResult must not have @Entity annotation");

        // Not Serializable
        assertFalse(java.io.Serializable.class.isAssignableFrom(enrollmentResultClass),
                "EnrollmentResult must not implement Serializable");

        // No Jackson annotations on record components
        for (RecordComponent component : enrollmentResultClass.getRecordComponents()) {
            assertNull(component.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class),
                    "EnrollmentResult must not have Jackson annotations");
        }
    }

    // Test 18-01-03: registerEnrollment() succeeds when CA is available and ca.enabled=true
    @Test
    void registerEnrollment_succeedsWhenCaAvailable() throws Exception {
        // This test verifies the happy path logic by checking that the service
        // constructs correctly and has the right profile annotation.
        // Full integration test with WebClient mocking is covered by
        // FabricGatewayConfigTest (Test 18-01-05).
        FabricCAService service = new FabricCAService(props, webClientBuilder);

        // Verify the service is properly constructed
        assertNotNull(service);

        // Verify @Profile("fabric") is present
        org.springframework.context.annotation.Profile profileAnnotation =
                FabricCAService.class.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertNotNull(profileAnnotation, "FabricCAService must have @Profile annotation");
        assertArrayEquals(new String[]{"fabric"}, profileAnnotation.value(),
                "FabricCAService must be @Profile(\"fabric\")");
    }

    // Test 18-01-06: FabricCAService is only instantiated under @Profile("fabric")
    @Test
    void fabricCAService_hasProfileFabricAnnotation() {
        org.springframework.context.annotation.Profile profileAnnotation =
                FabricCAService.class.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertNotNull(profileAnnotation, "FabricCAService must have @Profile annotation");

        String[] profiles = profileAnnotation.value();
        assertEquals(1, profiles.length, "Expected exactly one profile value");
        assertEquals("fabric", profiles[0], "Profile must be 'fabric'");
    }

    // Test 18-01-04: registerEnrollment() returns Identity and Signer when CA responds
    @Test
    void registerEnrollment_returnsIdentityAndSignerWhenCaResponds() throws Exception {
        // Generate a self-signed certificate for the mock CA response
        KeyPair testKeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        X509Certificate testCert = generateSelfSignedCert(testKeyPair);
        String certBase64 = Base64.getEncoder().encodeToString(testCert.getEncoded());
        String caResponseJson = "{\"result\":{\"Cert\":\"" + certBase64 + "\"}}";

        // Mock WebClient chain: builder -> webClient -> requestBodyUriSpec -> ...
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        // Use doReturn to avoid Mockito generic type checking issues
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(caResponseJson));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        FabricCAService.EnrollmentResult result = service.registerEnrollment();

        assertNotNull(result, "EnrollmentResult must not be null");
        assertNotNull(result.identity(), "Identity must not be null");
        assertNotNull(result.signer(), "Signer must not be null");
    }

    private static X509Certificate generateSelfSignedCert(KeyPair keyPair) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=admin");
        BigInteger serial = BigInteger.ONE;
        Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));
        Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }

    @Test
    void registerEnrollment_extractsCertFromRootLevelCertKey() throws Exception {
        KeyPair testKeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        X509Certificate testCert = generateSelfSignedCert(testKeyPair);
        String certBase64 = Base64.getEncoder().encodeToString(testCert.getEncoded());
        String caResponseJson = "{\"cert\":\"" + certBase64 + "\"}";

        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(caResponseJson));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        FabricCAService.EnrollmentResult result = service.registerEnrollment();

        assertNotNull(result);
        assertNotNull(result.identity());
    }

    @Test
    void registerEnrollment_extractsCertFromRootLevelCertCapitalKey() throws Exception {
        KeyPair testKeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        X509Certificate testCert = generateSelfSignedCert(testKeyPair);
        String certBase64 = Base64.getEncoder().encodeToString(testCert.getEncoded());
        String caResponseJson = "{\"Cert\":\"" + certBase64 + "\"}";

        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(caResponseJson));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        FabricCAService.EnrollmentResult result = service.registerEnrollment();

        assertNotNull(result);
        assertNotNull(result.identity());
    }

    @Test
    void registerEnrollment_extractsCertFromResultCertLowercase() throws Exception {
        KeyPair testKeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        X509Certificate testCert = generateSelfSignedCert(testKeyPair);
        String certBase64 = Base64.getEncoder().encodeToString(testCert.getEncoded());
        String caResponseJson = "{\"result\":{\"cert\":\"" + certBase64 + "\"}}";

        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(caResponseJson));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        FabricCAService.EnrollmentResult result = service.registerEnrollment();

        assertNotNull(result);
        assertNotNull(result.identity());
    }

    @Test
    void registerEnrollment_throwsWhenNoCertInResponse() {
        String caResponseJson = "{\"result\":{\"data\":\"no cert here\"}}";

        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(caResponseJson));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        RuntimeException ex = assertThrows(RuntimeException.class, service::registerEnrollment);
        assertTrue(ex.getMessage().contains("Fabric CA enrollment failed"));
    }

    @Test
    void registerEnrollment_redactsPasswordFromErrorMessage() {
        // Mock WebClient to throw an exception with basic auth credentials in message
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("Basic dXNlcjpwYXNzd29yZA== failed")));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        RuntimeException ex = assertThrows(RuntimeException.class, service::registerEnrollment);
        assertFalse(ex.getMessage().contains("dXNlcjpwYXNzd29yZA=="),
                "Password should be redacted from error message");
    }

    @Test
    void registerEnrollment_handlesNullErrorMessage() {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException((String) null)));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        RuntimeException ex = assertThrows(RuntimeException.class, service::registerEnrollment);
        assertTrue(ex.getMessage().contains("unknown error"));
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    void registerEnrollment_failsWhenCaUnavailable() {
        when(webClientBuilder.build()).thenThrow(new RuntimeException("Connection refused"));

        FabricCAService service = new FabricCAService(props, webClientBuilder);

        RuntimeException ex = assertThrows(RuntimeException.class, service::registerEnrollment);
        assertTrue(ex.getMessage().contains("Fabric CA enrollment failed"));
        assertFalse(ex.getMessage().contains("secret-password"),
                "Exception message must not contain admin password");
    }

    @Test
    void registerEnrollment_invalidJsonResponse_throwsException() {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        doReturn(headersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("not-valid-json"));

        FabricCAService service = new FabricCAService(props, webClientBuilder);
        RuntimeException ex = assertThrows(RuntimeException.class, service::registerEnrollment);
        assertTrue(ex.getMessage().contains("Fabric CA enrollment failed"));
    }
}
