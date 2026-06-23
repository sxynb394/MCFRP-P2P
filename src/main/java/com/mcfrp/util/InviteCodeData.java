package com.mcfrp.util;

/**
 * 邀请码数据实体
 * 包含 frps 地址、端口、密钥及房间名
 */
public class InviteCodeData {
    private final String frpsAddr;
    private final int frpsPort;
    private final String secretKey;
    private final String serverName;

    public InviteCodeData(String frpsAddr, int frpsPort, String secretKey, String serverName) {
        this.frpsAddr = frpsAddr;
        this.frpsPort = frpsPort;
        this.secretKey = secretKey;
        this.serverName = serverName;
    }

    public String getFrpsAddr() { return frpsAddr; }
    public int getFrpsPort() { return frpsPort; }
    public String getSecretKey() { return secretKey; }
    public String getServerName() { return serverName; }

    @Override
    public String toString() {
        return "InviteCodeData{frpsAddr='" + frpsAddr + "', frpsPort=" + frpsPort
            + ", secretKey='" + secretKey.substring(0, Math.min(8, secretKey.length())) + "...'"
            + ", serverName='" + serverName + "'}";
    }
}
