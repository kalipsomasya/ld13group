// 251RDC017 Jana Kuranova 16
// 251RDC054 Aļona Strahova 16
// 251RDC019 Marija Mičule 16

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Scanner;

public class Main {

    private static final byte[] MAGIC = new byte[] { 'B', 'W', 'H', '1' }; // BWT+MTF+RLE+Huffman

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String choiceStr;
        String sourceFile, resultFile, firstFile, secondFile;

        loop: while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            choiceStr = sc.nextLine().trim();

            switch (choiceStr) {
                case "comp":
                    System.out.print("source file name: ");
                    sourceFile = sc.nextLine().trim();
                    System.out.print("archive name: ");
                    resultFile = sc.nextLine().trim();
                    comp(sourceFile, resultFile);
                    break;
                case "decomp":
                    System.out.print("archive name: ");
                    sourceFile = sc.nextLine().trim();
                    System.out.print("file name: ");
                    resultFile = sc.nextLine().trim();
                    decomp(sourceFile, resultFile);
                    break;
                case "size":
                    System.out.print("file name: ");
                    sourceFile = sc.nextLine().trim();
                    size(sourceFile);
                    break;
                case "equal":
                    System.out.print("first file name: ");
                    firstFile = sc.nextLine().trim();
                    System.out.print("second file name: ");
                    secondFile = sc.nextLine().trim();
                    System.out.println(equal(firstFile, secondFile));
                    break;
                case "about":
                    about();
                    break;
                case "ratio":
                    System.out.print("original file name: ");
                    firstFile = sc.nextLine().trim();
                    System.out.print("archive file name: ");
                    secondFile = sc.nextLine().trim();
                    ratio(firstFile, secondFile);
                    break;
                case "exit":
                    break loop;
                default:
                    if (!choiceStr.isEmpty())
                        System.out.println("Unknown command. Allowed: comp, decomp, size, equal, about, ratio, exit");
            }
        }

        sc.close();
    }

    // ----------------- COMPRESS -----------------
    public static void comp(String sourceFile, String resultFile) {
        try {
            File inFile = new File(sourceFile);
            if (!inFile.exists() || !inFile.isFile()) {
                System.out.println("Source file not found: " + sourceFile);
                return;
            }

            byte[] original = readAllBytes(inFile);
            long originalLength = original.length;

            // 1) BWT
            BWTResult bwt = BWT.transform(original);

            // 2) MTF
            byte[] mtf = MTF.encode(bwt.lastColumn);

            // 3) RLE (pairs [count][value], count 1..255)
            byte[] rle = RLE.encode(mtf);

            // 4) frequency table for Huffman (over rle bytes)
            long[] freq = new long[256];
            for (byte b : rle) freq[b & 0xFF]++;

            // build Huffman tree & codes
            Huffman.Node root = Huffman.buildTree(freq);
            String[] codes = new String[256];
            if (root != null) Huffman.buildCodes(root, "", codes);

            // Write archive: MAGIC | originalLength(long) | bwtPrimaryIndex(int) | rleLength(int) | freq[256]*long | bitstream(huffman-encoded rle)
            try (FileOutputStream fos = new FileOutputStream(resultFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                dos.write(MAGIC);
                dos.writeLong(originalLength);
                dos.writeInt(bwt.primaryIndex);
                dos.writeInt(rle.length);
                for (int i = 0; i < 256; i++) dos.writeLong(freq[i]);
                dos.flush();

                BitOutputStream bitOut = new BitOutputStream(bos);
                for (byte b : rle) {
                    String code = codes[b & 0xFF];
                    if (code == null || code.length() == 0) code = "0";
                    for (char c : code.toCharArray()) bitOut.writeBit(c == '1' ? 1 : 0);
                }
                bitOut.flushBitsOnly();
                bos.flush();
            }

            long compressedLength = new File(resultFile).length();
            double ratio = compressedLength == 0 ? 0.0 : (double) originalLength / compressedLength;
            System.out.printf("Compression finished. Ratio: %.2f%n", ratio);

        } catch (IOException ex) {
            System.out.println("Error during compression: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    // ----------------- DECOMPRESS -----------------
    public static void decomp(String sourceFile, String resultFile) {
        try {
            File inFile = new File(sourceFile);
            if (!inFile.exists() || !inFile.isFile()) {
                System.out.println("Archive file not found: " + sourceFile);
                return;
            }

            try (FileInputStream fis = new FileInputStream(inFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream dis = new DataInputStream(bis)) {

                byte[] magicRead = new byte[4];
                dis.readFully(magicRead);
                if (!Arrays.equals(magicRead, MAGIC)) {
                    System.out.println("Not a valid archive (magic mismatch).");
                    return;
                }

                long originalLength = dis.readLong();
                int bwtPrimary = dis.readInt();
                int rleLength = dis.readInt();
                long[] freq = new long[256];
                for (int i = 0; i < 256; i++) freq[i] = dis.readLong();

                Huffman.Node root = Huffman.buildTree(freq);

                byte[] rle = new byte[rleLength];
                if (rleLength > 0 && root != null) {
                    BitInputStream bitIn = new BitInputStream(bis);
                    int produced = 0;
                    if (root.isLeaf()) {
                        byte sym = (byte) root.symbol;
                        for (int i = 0; i < rleLength; i++) rle[i] = sym;
                    } else {
                        Huffman.Node node = root;
                        while (produced < rleLength) {
                            int bit = bitIn.readBit();
                            if (bit == -1) break;
                            node = (bit == 0) ? node.left : node.right;
                            if (node.isLeaf()) {
                                rle[produced++] = (byte) node.symbol;
                                node = root;
                            }
                        }
                    }
                }

                // RLE decode -> mtf
                byte[] mtf = RLE.decode(rle);
                // MTF decode -> last column
                byte[] last = MTF.decode(mtf);
                // inverse BWT
                byte[] original = BWT.inverse(last, bwtPrimary);

                // write
                try (FileOutputStream fos = new FileOutputStream(resultFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    bos.write(original);
                    bos.flush();
                }
            }

            System.out.println("Decompression finished.");
        } catch (IOException ex) {
            System.out.println("Error during decompression: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    // ----------------- FILE UTIL -----------------
    public static void size(String sourceFile) {
        try {
            File f = new File(sourceFile);
            if (!f.exists() || !f.isFile()) {
                System.out.println("file not found");
                return;
            }
            System.out.println("size: " + f.length());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public static boolean equal(String firstFile, String secondFile) {
        try (FileInputStream f1 = new FileInputStream(firstFile);
             FileInputStream f2 = new FileInputStream(secondFile)) {

            int k1, k2;
            byte[] buf1 = new byte[4096];
            byte[] buf2 = new byte[4096];
            do {
                k1 = f1.read(buf1);
                k2 = f2.read(buf2);
                if (k1 != k2) return false;
                if (k1 > 0) {
                    for (int i = 0; i < k1; i++) {
                        if (buf1[i] != buf2[i]) return false;
                    }
                }
            } while (k1 != -1 && k2 != -1);
            return true;
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    public static void about() {
        System.out.println("251RDC017 Jana Kuranova 16");
        System.out.println("251RDC054 Aļona Strahova 16");
        System.out.println("251RDC019 Marija Mičule 16");
    }

    public static void ratio(String originalFile, String compressedFile) {
        try {
            File f1 = new File(originalFile);
            File f2 = new File(compressedFile);

            if (!f1.exists() || !f1.isFile()) {
                System.out.println("Original file not found");
                return;
            }
            if (!f2.exists() || !f2.isFile()) {
                System.out.println("Compressed file not found");
                return;
            }

            long sizeOriginal = f1.length();
            long sizeCompressed = f2.length();

            if (sizeCompressed == 0) {
                System.out.println("Compressed file is empty (ratio undefined)");
                return;
            }

            double ratio = (double) sizeOriginal / sizeCompressed;
            System.out.printf("ratio: %.2f%n", ratio);

        } catch (Exception e) {
            System.out.println("Error calculating ratio: " + e.getMessage());
        }
    }

    // ----------------- HELPERS: BWT, MTF, RLE, Huffman, BitStreams -----------------

    // ---------- BWT ----------
    private static class BWTResult {
        final byte[] lastColumn;
        final int primaryIndex;
        BWTResult(byte[] lastColumn, int primaryIndex) { this.lastColumn = lastColumn; this.primaryIndex = primaryIndex; }
    }

    private static class BWT {
        // Naive BWT: build rotations and sort them
        static BWTResult transform(byte[] input) {
            if (input == null || input.length == 0) return new BWTResult(new byte[0], 0);
            int n = input.length;
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            final byte[] data = input;
            java.util.Arrays.sort(idx, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    int i = a, j = b;
                    for (int k = 0; k < n; k++) {
                        int ca = data[(i + k) % n] & 0xFF;
                        int cb = data[(j + k) % n] & 0xFF;
                        if (ca != cb) return ca - cb;
                    }
                    return 0;
                }
            });
            byte[] last = new byte[n];
            int primary = -1;
            for (int r = 0; r < n; r++) {
                int start = idx[r];
                int lastPos = (start + n - 1) % n;
                last[r] = data[lastPos];
                if (start == 0) primary = r;
            }
            return new BWTResult(last, primary);
        }

        static byte[] inverse(byte[] last, int primary) {
            if (last == null || last.length == 0) return new byte[0];
            int n = last.length;
            // Build first column by sorting last
            int[] counts = new int[256];
            for (byte b : last) counts[b & 0xFF]++;
            int[] starts = new int[256];
            int sum = 0;
            for (int i = 0; i < 256; i++) {
                starts[i] = sum;
                sum += counts[i];
            }
            // compute occurrence ranks
            int[] occ = new int[n];
            int[] seen = new int[256];
            for (int i = 0; i < n; i++) {
                int v = last[i] & 0xFF;
                occ[i] = seen[v];
                seen[v]++;
            }
            // rebuild via LF-mapping
            byte[] res = new byte[n];
            int idx = primary;
            for (int i = n - 1; i >= 0; i--) {
                res[i] = last[idx];
                int v = last[idx] & 0xFF;
                idx = starts[v] + occ[idx];
            }
            return res;
        }
    }

    // ---------- MTF ----------
    private static class MTF {
        static byte[] encode(byte[] data) {
            if (data == null || data.length == 0) return new byte[0];
            byte[] symbols = new byte[256];
            for (int i = 0; i < 256; i++) symbols[i] = (byte) i;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (byte b : data) {
                int val = b & 0xFF;
                int pos = 0;
                while ((symbols[pos] & 0xFF) != val) pos++;
                baos.write(pos);
                // move to front
                byte sym = symbols[pos];
                System.arraycopy(symbols, 0, symbols, 1, pos);
                symbols[0] = sym;
            }
            return baos.toByteArray();
        }

        static byte[] decode(byte[] data) {
            if (data == null || data.length == 0) return new byte[0];
            byte[] symbols = new byte[256];
            for (int i = 0; i < 256; i++) symbols[i] = (byte) i;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (byte b : data) {
                int pos = b & 0xFF;
                byte sym = symbols[pos];
                baos.write(sym & 0xFF);
                // move to front
                System.arraycopy(symbols, 0, symbols, 1, pos);
                symbols[0] = sym;
            }
            return baos.toByteArray();
        }
    }

    // ---------- RLE (simple pair encoding) ----------
    private static class RLE {
        static byte[] encode(byte[] data) throws IOException {
            if (data == null || data.length == 0) return new byte[0];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = 0;
            while (i < data.length) {
                int j = i + 1;
                while (j < data.length && data[j] == data[i] && (j - i) < 255) j++;
                int count = j - i; // 1..255
                baos.write(count);
                baos.write(data[i]);
                i = j;
            }
            return baos.toByteArray();
        }

        static byte[] decode(byte[] rle) throws IOException {
            if (rle == null || rle.length == 0) return new byte[0];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = 0;
            while (i + 1 < rle.length) {
                int count = rle[i++] & 0xFF;
                byte val = rle[i++];
                for (int k = 0; k < count; k++) baos.write(val & 0xFF);
            }
            return baos.toByteArray();
        }
    }

    // ---------- HUFFMAN ----------
    private static class Huffman {
        private static class Node implements Comparable<Node> {
            final long freq;
            final int symbol; // 0..255 for leaf, -1 for internal
            final Node left, right;
            Node(long freq, int symbol, Node left, Node right) { this.freq = freq; this.symbol = symbol; this.left = left; this.right = right; }
            boolean isLeaf() { return left == null && right == null; }
            public int compareTo(Node o) {
                int c = Long.compare(this.freq, o.freq);
                if (c == 0) return Integer.compare(this.symbol, o.symbol);
                return c;
            }
        }

        static Node buildTree(long[] freq) {
            PriorityQueue<Node> pq = new PriorityQueue<>();
            for (int i = 0; i < 256; i++) if (freq[i] > 0) pq.add(new Node(freq[i], i, null, null));
            if (pq.isEmpty()) return null;
            if (pq.size() == 1) {
                Node only = pq.poll();
                return new Node(only.freq, -1, only, null);
            }
            while (pq.size() > 1) {
                Node a = pq.poll();
                Node b = pq.poll();
                Node p = new Node(a.freq + b.freq, Math.min(a.symbol, b.symbol), a, b);
                pq.add(p);
            }
            return pq.poll();
        }

        static void buildCodes(Node node, String prefix, String[] codes) {
            if (node == null) return;
            if (node.isLeaf()) {
                codes[node.symbol] = prefix.length() > 0 ? prefix : "0";
            } else {
                buildCodes(node.left, prefix + "0", codes);
                buildCodes(node.right, prefix + "1", codes);
            }
        }
    }

    // ---------- Bit streams ----------
    private static class BitOutputStream implements Closeable {
        private final OutputStream out;
        private int currentByte = 0;
        private int numBitsFilled = 0;
        BitOutputStream(OutputStream out) { this.out = out; }

        void writeBit(int b) throws IOException {
            currentByte = (currentByte << 1) | (b & 1);
            numBitsFilled++;
            if (numBitsFilled == 8) {
                out.write(currentByte);
                numBitsFilled = 0;
                currentByte = 0;
            }
        }

        // flush remaining bits but do NOT close underlying stream
        void flushBitsOnly() throws IOException {
            if (numBitsFilled > 0) {
                out.write(currentByte << (8 - numBitsFilled));
                numBitsFilled = 0;
                currentByte = 0;
            }
            out.flush();
        }

        @Override
        public void close() throws IOException {
            flushBitsOnly();
            // underlying stream left open (caller closes)
        }
    }

    private static class BitInputStream implements Closeable {
        private final InputStream in;
        private int currentByte = 0;
        private int numBitsRemaining = 0;
        BitInputStream(InputStream in) { this.in = in; }

        int readBit() throws IOException {
            if (numBitsRemaining == 0) {
                currentByte = in.read();
                if (currentByte == -1) return -1;
                numBitsRemaining = 8;
            }
            numBitsRemaining--;
            return (currentByte >> numBitsRemaining) & 1;
        }

        @Override
        public void close() throws IOException { in.close(); }
    }

    // ---------- IO helper ----------
    private static byte[] readAllBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return baos.toByteArray();
        }
    }
}
