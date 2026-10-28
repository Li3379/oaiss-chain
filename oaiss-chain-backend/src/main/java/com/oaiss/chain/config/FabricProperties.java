package com.oaiss.chain.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "fabric")
public class FabricProperties {

    private boolean enabled = false;
    private String mspId = "Org1MSP";
    private String channelName = "mychannel";
    private String chaincodeName = "carbon-chaincode";
    private String peerEndpoint = "peer0.org1.example.com:7051";
    private String peerHostOverride = "peer0.org1.example.com";
    private boolean tlsEnabled = true;
    private String peerTlsCertPath = "classpath:fabric/crypto/peer-tls-ca.crt";
    private String certPath = "classpath:fabric/crypto/user-cert.pem";
    private String keyPath = "classpath:fabric/crypto/user-key.pem";
    private int connectTimeout = 30;
    private int submitTimeout = 60;
    private Ca ca = new Ca();

    @Data
    public static class Ca {
        private boolean enabled = false;
        private String endpoint = "http://ca.org1.example.com:7054";
        private String adminName = "admin";
        @ToString.Exclude
        private String adminPassword;
    }
}
