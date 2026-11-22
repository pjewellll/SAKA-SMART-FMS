package com.sakasmart;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Small, dependency-free QR Code encoder (byte mode, error correction level M,
 * versions 1-10 = up to 213 bytes). Used to show the phone-scanner link on screen
 * so the phone can open it by scanning instead of typing the address.
 */
final class QrCode {
    private static final int[] ECC_PER_BLOCK_M = {0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOCKS_M        = {0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    private QrCode(int version, byte[] dataCodewords) {
        size = version * 4 + 17;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];
        drawFunctionPatterns(version);
        placeCodewords(addEccAndInterleave(dataCodewords, version));
        int bestMask = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(mask);
            drawFormatBits(mask);
            long penalty = penalty();
            if (penalty < bestPenalty) { bestPenalty = penalty; bestMask = mask; }
            applyMask(mask); // XOR again = undo
        }
        applyMask(bestMask);
        drawFormatBits(bestMask);
    }

    /** Encodes text as UTF-8. Throws IllegalArgumentException when it is too long. */
    static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int version = 1; version <= 10; version++) {
            int capacityBits = dataCodewords(version) * 8;
            int countBits = version < 10 ? 8 : 16;
            int needed = 4 + countBits + data.length * 8;
            if (needed > capacityBits) continue;
            BitBuffer bb = new BitBuffer();
            bb.append(0x4, 4);
            bb.append(data.length, countBits);
            for (byte b : data) bb.append(b & 0xFF, 8);
            bb.append(0, Math.min(4, capacityBits - bb.length()));
            bb.append(0, (8 - bb.length() % 8) % 8);
            for (int pad = 0xEC; bb.length() < capacityBits; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
            return new QrCode(version, bb.toBytes());
        }
        throw new IllegalArgumentException("Text is too long for the QR code.");
    }

    boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size && modules[y][x]; }
    int size() { return size; }

    BufferedImage toImage(int scale, int border) {
        int dim = (size + border * 2) * scale;
        BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < dim; y++)
            for (int x = 0; x < dim; x++)
                img.setRGB(x, y, dark(x / scale - border, y / scale - border)
                    ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        return img;
    }

    // ------------------------------------------------------------------ capacity
    private static int rawModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }
    private static int dataCodewords(int ver) {
        return rawModules(ver) / 8 - ECC_PER_BLOCK_M[ver] * BLOCKS_M[ver];
    }

    // ------------------------------------------------------------------ drawing
    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void drawFunctionPatterns(int ver) {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignmentPositions(ver);
        for (int i = 0; i < pos.length; i++)
            for (int j = 0; j < pos.length; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.length - 1) || (i == pos.length - 1 && j == 0)) continue;
                drawAlignment(pos[i], pos[j]);
            }
        drawFormatBits(0); // reserve the area; real value written after mask choice
        if (ver >= 7) drawVersion(ver);
    }

    private int[] alignmentPositions(int ver) {
        if (ver == 1) return new int[0];
        int numAlign = ver / 7 + 2;
        int step = (ver * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) setFunction(x, y, dist != 2 && dist != 4);
            }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private void drawFormatBits(int mask) {
        int data = (0 << 3) | mask; // ECC level M = 00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion(int ver) {
        int rem = ver;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (ver << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private static boolean bit(int value, int index) { return ((value >>> index) & 1) != 0; }

    // ------------------------------------------------------------------ codewords
    private byte[] addEccAndInterleave(byte[] data, int ver) {
        int numBlocks = BLOCKS_M[ver], eccLen = ECC_PER_BLOCK_M[ver];
        int raw = rawModules(ver) / 8;
        int numShort = numBlocks - raw % numBlocks;
        int shortLen = raw / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortLen - eccLen + (i < numShort ? 0 : 1));
            k += dat.length;
            byte[] ecc = rsRemainder(dat, divisor);
            if (i < numShort) dat = Arrays.copyOf(dat, dat.length + 1);
            byte[] block = Arrays.copyOf(dat, shortLen + 1);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            blocks[i] = block;
        }
        byte[] result = new byte[raw];
        for (int i = 0, k = 0; i < blocks[0].length; i++)
            for (int j = 0; j < blocks.length; j++)
                if (i != shortLen - eccLen || j >= numShort) result[k++] = blocks[j][i];
        return result;
    }

    private void placeCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3] & 0xFF, 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean invert = switch (mask) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                if (invert && !isFunction[y][x]) modules[y][x] ^= true;
            }
    }

    // ------------------------------------------------------------------ penalty
    private long penalty() {
        long total = 0;
        for (int pass = 0; pass < 2; pass++) { // rule 1 + rule 3, rows then columns
            for (int a = 0; a < size; a++) {
                int run = 1;
                StringBuilder line = new StringBuilder(size);
                for (int b = 0; b < size; b++) {
                    boolean cur = pass == 0 ? modules[a][b] : modules[b][a];
                    line.append(cur ? '1' : '0');
                    if (b > 0) {
                        boolean prev = pass == 0 ? modules[a][b - 1] : modules[b - 1][a];
                        if (cur == prev) { run++; if (run == 5) total += 3; else if (run > 5) total++; }
                        else run = 1;
                    }
                }
                String s = line.toString();
                for (String pattern : new String[] {"10111010000", "00001011101"})
                    for (int idx = s.indexOf(pattern); idx >= 0; idx = s.indexOf(pattern, idx + 1)) total += 40;
            }
        }
        for (int y = 0; y < size - 1; y++) // rule 2
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) total += 3;
            }
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int all = size * size;
        int k = (Math.abs(dark * 20 - all * 10) + all - 1) / all - 1;
        return total + Math.max(0, k) * 10L;
    }

    // ------------------------------------------------------------------ Reed-Solomon
    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) mul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = mul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) mul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static final class BitBuffer {
        private final java.util.ArrayList<Boolean> bits = new java.util.ArrayList<>();
        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
        }
        int length() { return bits.size(); }
        byte[] toBytes() {
            byte[] out = new byte[(bits.size() + 7) / 8];
            for (int i = 0; i < bits.size(); i++) if (bits.get(i)) out[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
            return out;
        }
    }
}
