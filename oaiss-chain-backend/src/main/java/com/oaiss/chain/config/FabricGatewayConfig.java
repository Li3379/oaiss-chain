package com.oaiss.chain.config;

import com.oaiss.chain.service.FabricCAService;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;

@Configuration
@Profile("fabric")
@EnableConfigurationProperties(FabricProperties.class)
@RequiredArgsConstructor
@Slf4j
public class FabricGatewayConfig {

    private final FabricProperties props;
    private final FabricCAService fabricCAService;

    @Bean(destroyMethod = "close")
    public Gateway fabricGateway() throws Exception {
        log.info("Connecting to Fabric peer at {} (TLS={})", props.getPeerEndpoint(), props.isTlsEnabled());

        ManagedChannel channel = newGrpcChannel();
        Identity identity;
        Signer signer;

        if (props.getCa().isEnabled()) {
            try {
                FabricCAService.EnrollmentResult result = fabricCAService.registerEnrollment();
                identity = result.identity();
                signer = result.signer();
                log.info("Using CA-issued identity");
            } catch (Exception e) {
                log.warn("CA enrollment failed, falling back to static crypto: {}", e.getMessage());
                identity = newIdentity();
                signer = newSigner();
                log.info("Using static crypto identity");
            }
        } else {
            identity = newIdentity();
            signer = newSigner();
            log.info("Using static crypto identity");
        }

        Gateway.Builder builder = Gateway.newInstance()
                .identity(identity)
                .signer(signer)
                .connection(channel);

        log.info("Fabric Gateway configured: mspId={}, channel={}, chaincode={}",
                props.getMspId(), props.getChannelName(), props.getChaincodeName());

        return builder.connect();
    }

    @Bean
    public Network fabricNetwork(Gateway gateway) {
        return gateway.getNetwork(props.getChannelName());
    }

    @Bean
    public Contract carbonContract(Network network) {
        return network.getContract(props.getChaincodeName());
    }

    private ManagedChannel newGrpcChannel() throws Exception {
        if (props.isTlsEnabled()) {
            var tlsCertReader = new InputStreamReader(
                    openResource(props.getPeerTlsCertPath()),
                    StandardCharsets.UTF_8);
            var tlsRootCert = Identities.readX509Certificate(tlsCertReader);

            SslContext sslContext = GrpcSslContexts.forClient()
                    .trustManager(tlsRootCert)
                    .build();

            return io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.forTarget(props.getPeerEndpoint())
                    .overrideAuthority(props.getPeerHostOverride())
                    .sslContext(sslContext)
                    .build();
        } else {
            log.warn("Fabric TLS disabled — using insecure connection (development only)");
            return io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.forTarget(props.getPeerEndpoint())
                    .usePlaintext()
                    .build();
        }
    }

    private Identity newIdentity() throws IOException, CertificateException {
        var certReader = new InputStreamReader(
                openResource(props.getCertPath()),
                StandardCharsets.UTF_8);
        var certificate = Identities.readX509Certificate(certReader);
        return new X509Identity(props.getMspId(), certificate);
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        var keyReader = new InputStreamReader(
                openResource(props.getKeyPath()),
                StandardCharsets.UTF_8);
        var privateKey = Identities.readPrivateKey(keyReader);
        return Signers.newPrivateKeySigner(privateKey);
    }

    private InputStream openResource(String location) throws IOException {
        return resolveResource(location).getInputStream();
    }

    Resource resolveResource(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("Fabric crypto path is blank");
        }
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length()));
        }
        return new FileSystemResource(Path.of(location));
    }
}
