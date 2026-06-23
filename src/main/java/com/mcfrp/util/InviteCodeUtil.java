package com.mcfrp.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 邀请码工具类
 * 使用二进制压缩 Base64 格式
 *
 * 数据结构：
 *  - 版本号     1字节   固定 0x01
 *  - 标志位     1字节   预留扩展
 *  - frpsAddr 长度 1字节
 *  - frpsAddr  N字节   域名或 IP 字符串（UTF-8）
 *  - frpsPort  2字节   大端序（Big-Endian）
 *  - secretKey 长度 1字节
 *  - secretKey N字节   密钥字符串（UTF-8）
 *  - serverName 长度 1字节
 *  - serverName N字节  房间名字符串（UTF-8）
 */
public class InviteCodeUtil {
    private static final String PREFIX = "MCFRP://";
    private static final byte VERSION = 0x01;
    private static final byte FLAGS = 0x00;
    private static final int MAX_STR_LEN = 200;
    private static final int MIN_BODY_LEN = 8;
    private static final int MAX_PORT = 65535;

    public static String encode(InviteCodeData data) {
        if (data == null) {
            throw new IllegalArgumentException("InviteCodeData is null");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            byte[] frpsAddrBytes = data.getFrpsAddr().getBytes(StandardCharsets.UTF_8);
            byte[] secretKeyBytes = data.getSecretKey().getBytes(StandardCharsets.UTF_8);
            byte[] serverNameBytes = data.getServerName().getBytes(StandardCharsets.UTF_8);

            if (frpsAddrBytes.length > MAX_STR_LEN
                || secretKeyBytes.length > MAX_STR_LEN
                || serverNameBytes.length > MAX_STR_LEN) {
                throw new IllegalArgumentException("String length exceeds " + MAX_STR_LEN);
            }

            dos.writeByte(VERSION);
            dos.writeByte(FLAGS);
            dos.writeByte(frpsAddrBytes.length);
            dos.write(frpsAddrBytes);
            dos.writeShort(data.getFrpsPort() & 0xFFFF);
            dos.writeByte(secretKeyBytes.length);
            dos.write(secretKeyBytes);
            dos.writeByte(serverNameBytes.length);
            dos.write(serverNameBytes);

            byte[] raw = baos.toByteArray();
            String encoded = Base64.getEncoder().withoutPadding().encodeToString(raw);
            return PREFIX + encoded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode invite code", e);
        }
    }

    public static InviteCodeData decode(String code) {
        if (code == null || !code.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid invite code format: missing MCFRP:// prefix");
        }

        String encoded = code.substring(PREFIX.length()).trim();
        if (encoded.length() < MIN_BODY_LEN) {
            throw new IllegalArgumentException("Invalid invite code format: body too short");
        }
        if (!encoded.matches("[A-Za-z0-9+/]+=*")) {
            throw new IllegalArgumentException("Invalid invite code format: illegal Base64 characters");
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 content", e);
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(raw);
             DataInputStream dis = new DataInputStream(bais)) {

            byte version = dis.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported invite code version: " + version);
            }

            byte flags = dis.readByte();

            int frpsAddrLen = dis.readByte() & 0xFF;
            if (frpsAddrLen <= 0 || frpsAddrLen > MAX_STR_LEN) {
                throw new IllegalArgumentException("Invalid frpsAddr length: " + frpsAddrLen);
            }
            byte[] frpsAddrBytes = new byte[frpsAddrLen];
            dis.readFully(frpsAddrBytes);
            String frpsAddr = new String(frpsAddrBytes, StandardCharsets.UTF_8).trim();

            int frpsPort = dis.readShort() & 0xFFFF;
            if (frpsPort <= 0 || frpsPort > MAX_PORT) {
                throw new IllegalArgumentException("Invalid frpsPort: " + frpsPort);
            }

            int secretKeyLen = dis.readByte() & 0xFF;
            if (secretKeyLen <= 0 || secretKeyLen > MAX_STR_LEN) {
                throw new IllegalArgumentException("Invalid secretKey length: " + secretKeyLen);
            }
            byte[] secretKeyBytes = new byte[secretKeyLen];
            dis.readFully(secretKeyBytes);
            String secretKey = new String(secretKeyBytes, StandardCharsets.UTF_8).trim();

            if ((flags & 0x01) != 0) {
                int authTokenLen = dis.readByte() & 0xFF;
                if (authTokenLen > 0 && authTokenLen <= MAX_STR_LEN) {
                    dis.skipBytes(authTokenLen);
                }
            }

            int serverNameLen = dis.readByte() & 0xFF;
            if (serverNameLen <= 0 || serverNameLen > MAX_STR_LEN) {
                throw new IllegalArgumentException("Invalid serverName length: " + serverNameLen);
            }
            byte[] serverNameBytes = new byte[serverNameLen];
            dis.readFully(serverNameBytes);
            String serverName = new String(serverNameBytes, StandardCharsets.UTF_8).trim();

            if (frpsAddr.isEmpty() || secretKey.isEmpty() || serverName.isEmpty()) {
                throw new IllegalArgumentException("Invite code contains empty field(s)");
            }

            return new InviteCodeData(frpsAddr, frpsPort, secretKey, serverName);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed invite code binary", e);
        }
    }

    public static String generateSecretKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isInviteCode(String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        if (!trimmed.startsWith(PREFIX)) return false;
        String body = trimmed.substring(PREFIX.length()).trim();
        if (body.length() < 20) return false;
        if (!body.matches("[A-Za-z0-9+/]+=*")) return false;
        return true;
    }
}
