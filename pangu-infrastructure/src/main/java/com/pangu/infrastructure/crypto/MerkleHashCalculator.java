package com.pangu.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Merkle Hash 工具类（SHA256）。
 *
 * <p>用于投票分母快照、Waiver payload 等需要「行级可追溯 + 整体防篡改」的场景。
 * 算法：
 * <ul>
 *   <li>叶子层：原文 SHA256 → 64-hex</li>
 *   <li>内部层：相邻两叶子拼接（左+右）→ SHA256；奇数节点末位与自身配对（标准 Bitcoin Merkle 风格）</li>
 *   <li>单叶子情形：直接返回该叶子 hash 作为 root</li>
 * </ul>
 */
public final class MerkleHashCalculator {

    private MerkleHashCalculator() {
    }

    /**
     * 单条字符串 SHA256（64-hex）。
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 计算 Merkle root。空列表抛出异常（强制业务避免误用）。
     *
     * @param leafHashes 叶子层已经过 SHA256 的 64-hex 字符串列表
     * @return 64-hex Merkle root
     */
    public static String merkleRoot(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("leafHashes must not be empty");
        }
        if (leafHashes.size() == 1) {
            return leafHashes.get(0);
        }
        List<String> currentLevel = new ArrayList<>(leafHashes);
        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>((currentLevel.size() + 1) / 2);
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                nextLevel.add(sha256Hex(left + right));
            }
            currentLevel = nextLevel;
        }
        return currentLevel.get(0);
    }
}
