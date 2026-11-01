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
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

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

        // 自签一张临时证书，而不是读 classpath 里的 fabric/crypto/user-cert.pem。
        //
        // 那个目录是 generate-fabric-crypto.sh 的运行时产物，且被 .gitignore 排除，
        // 因此干净克隆（以及任何 CI runner）上根本不存在该文件。
        // 测试靠「本地恰好生成过」而通过，是典型的 本地绿 / CI 红。
        // 证书内容与网关行为无关——这里只验证 CA 身份分支被走到。
        X509Certificate cert = selfSignedCertificate();
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

        // classpath 分支：只断言「认出了 classpath: 前缀并解析成 ClassPathResource」，
        // 不断言 exists()。
        //
        // 原断言要求 fabric/crypto/user-cert.pem 真实存在于 classpath 上。该文件是
        // generate-fabric-crypto.sh 的运行时产物且被 .gitignore 排除，干净克隆上不存在
        // → CI 必红。exists() 在这里检验的不是被测代码，而是「这台机器跑没跑过生成脚本」。
        Resource classpathResource = config.resolveResource("classpath:fabric/crypto/user-cert.pem");
        assertInstanceOf(ClassPathResource.class, classpathResource);

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

    /**
     * 生成一张一次性自签证书（BouncyCastle，已在 pom 的 compile 依赖中）。
     *
     * 不落盘、不依赖仓库里的任何 crypto 产物，因此在干净克隆与 CI 上都可运行。
     */
    private static X509Certificate selfSignedCertificate() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name subject = new X500Name("CN=fabric-gateway-test");
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(now.plus(1, ChronoUnit.HOURS));

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        try (var in = new ByteArrayInputStream(builder.build(signer).getEncoded())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }
}
