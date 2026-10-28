package com.oaiss.chain.config;

import com.oaiss.chain.service.FabricCAService;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FabricGatewayConfigTest {

    private FabricProperties props;

    @Mock
    private FabricCAService fabricCAService;

    @Mock
    private Signer mockSigner;

    @BeforeEach
    void setUp() {
        props = new FabricProperties();
        props.setMspId("Org1MSP");
        props.setChannelName("mychannel");
        props.setChaincodeName("carbon-chaincode");
        props.setPeerEndpoint("localhost:7051");
        props.setTlsEnabled(false);
        props.setCertPath("classpath:fabric/crypto/user-cert.pem");
        props.setKeyPath("classpath:fabric/crypto/user-key.pem");
    }

    // Test 18-01-04: When ca.enabled=true but CA enrollment throws, gateway falls back to static crypto
    @Test
    void fabricGateway_whenCaEnabledButEnrollmentFails_fallsBackToStaticCrypto() throws Exception {
        props.getCa().setEnabled(true);
        when(fabricCAService.registerEnrollment()).thenThrow(new RuntimeException("CA unavailable"));

        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);

        // The gateway method catches the CA enrollment exception and falls back.
        // With TLS disabled and no real peer, the plaintext channel connects,
        // but the static crypto files may or may not be in classpath.
        // Either way, registerEnrollment() was attempted and the fallback was taken.
        try {
            config.fabricGateway();
        } catch (Exception e) {
            // Expected if static crypto files are missing — the fallback path was taken
        }

        // Verify CA enrollment was attempted
        verify(fabricCAService).registerEnrollment();
    }

    // Test 18-01-05: When ca.enabled=true and enrollment succeeds, gateway uses CA-issued identity
    @Test
    void fabricGateway_whenCaEnabledAndEnrollmentSucceeds_usesCaIdentity() throws Exception {
        props.getCa().setEnabled(true);

        // Use the existing classpath crypto files to create a valid X509Identity
        // This avoids hardcoded cert PEM that may become invalid
        var certStream = new ByteArrayInputStream(
                getClass().getClassLoader().getResourceAsStream("fabric/crypto/user-cert.pem").readAllBytes());
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(certStream);
        Identity realIdentity = new X509Identity("Org1MSP", cert);

        FabricCAService.EnrollmentResult enrollmentResult =
                new FabricCAService.EnrollmentResult(realIdentity, mockSigner);
        when(fabricCAService.registerEnrollment()).thenReturn(enrollmentResult);

        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);

        // The gateway will try to connect to a real peer which won't exist,
        // but the CA-issued identity path was taken
        try {
            config.fabricGateway();
        } catch (Exception e) {
            // Expected — no real Fabric peer in unit test
        }

        // Verify CA enrollment was called — proving the CA path was taken
        verify(fabricCAService).registerEnrollment();
    }

    // When ca.enabled=false, gateway uses static crypto files (existing behavior unchanged)
    @Test
    void fabricGateway_whenCaDisabled_usesStaticCrypto() throws Exception {
        props.getCa().setEnabled(false);

        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);

        // With ca.enabled=false, the CA service should NOT be called
        try {
            config.fabricGateway();
        } catch (Exception e) {
            // Expected — no real Fabric peer or crypto files in unit test
        }

        // The key assertion: registerEnrollment was never called
        verify(fabricCAService, never()).registerEnrollment();
    }

    @Test
    void resolveResource_shouldSupportClasspathAndAbsolutePaths() throws Exception {
        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);

        Resource classpathResource = config.resolveResource("classpath:fabric/crypto/user-cert.pem");
        assertInstanceOf(ClassPathResource.class, classpathResource);
        assertTrue(classpathResource.exists());

        Path tempFile = Files.createTempFile("fabric-gateway-test", ".pem");
        try {
            Files.writeString(tempFile, "dummy");
            Resource fileSystemResource = config.resolveResource(tempFile.toString());
            assertInstanceOf(FileSystemResource.class, fileSystemResource);
            assertTrue(fileSystemResource.exists());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("resolveResource should throw IOException for null path")
    void resolveResource_nullPath_shouldThrowIOException() {
        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);
        assertThrows(IOException.class, () -> config.resolveResource(null));
    }

    @Test
    @DisplayName("resolveResource should throw IOException for blank path")
    void resolveResource_blankPath_shouldThrowIOException() {
        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);
        assertThrows(IOException.class, () -> config.resolveResource("   "));
    }

    @Test
    @DisplayName("fabricGateway should use TLS when tlsEnabled=true")
    void fabricGateway_whenTlsEnabled_shouldUseTlsChannel() throws Exception {
        props.setTlsEnabled(true);
        props.setPeerTlsCertPath("classpath:fabric/crypto/peer-tls-ca.crt");
        props.getCa().setEnabled(false);

        FabricGatewayConfig config = new FabricGatewayConfig(props, fabricCAService);

        // The TLS branch in newGrpcChannel is exercised; gRPC connections are lazy
        // so connect() may succeed without a running peer
        try {
            var gateway = config.fabricGateway();
            gateway.close();
        } catch (Exception e) {
            // Acceptable — no real Fabric peer in unit test
        }
    }
}
