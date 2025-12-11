// 251RDC017 Jana Kuranova 16
// 251RDC054 Aļona Strahova 16
// 251RDC019 Marija Mičule 16

// Importē nepieciešamās bibliotēkas darbam ar failiem un datu struktūrām
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Scanner;

// Galvenā klase
public class MainWithComm {

    // Galvenā metode - programmas sākumpunkts
    public static void main(String[] args) {
        // Izveido Scanner objektu lietotāja ievades lasīšanai
        Scanner sc = new Scanner(System.in);
        // Mainīgais komandas nosaukuma glabāšanai
        String choiseStr;
        // Mainīgie failu nosaukumu glabāšanai
        String sourceFile, resultFile, firstFile, secondFile;

        // Bezgalīgs cikls komandu apstrādei
        loop: while (true) {

            // Nolasa nākamo komandu no lietotāja
            choiseStr = sc.next();

            // Pārbauda, kura komanda ir ievadīta
            switch (choiseStr) {
                case "comp":
                    // Komanda faila kompresēšanai
                    System.out.print("source file name: ");
                    sourceFile = sc.next(); // Nolasa avota faila nosaukumu
                    System.out.print("archive name: ");
                    resultFile = sc.next(); // Nolasa arhīva nosaukumu
                    comp(sourceFile, resultFile); // Izsauc kompresēšanas metodi
                    break;
                case "decomp":
                    // Komanda faila dekompresēšanai
                    System.out.print("archive name: ");
                    sourceFile = sc.next(); // Nolasa arhīva nosaukumu
                    System.out.print("file name: ");
                    resultFile = sc.next(); // Nolasa rezultāta faila nosaukumu
                    decomp(sourceFile, resultFile); // Izsauc dekompresēšanas metodi
                    break;
                case "size":
                    // Komanda faila izmēra parādīšanai
                    System.out.print("file name: ");
                    sourceFile = sc.next(); // Nolasa faila nosaukumu
                    size(sourceFile); // Izsauc izmēra pārbaudes metodi
                    break;
                case "equal":
                    // Komanda divu failu salīdzināšanai
                    System.out.print("first file name: ");
                    firstFile = sc.next(); // Nolasa pirmā faila nosaukumu
                    System.out.print("second file name: ");
                    secondFile = sc.next(); // Nolasa otrā faila nosaukumu
                    System.out.println(equal(firstFile, secondFile)); // Salīdzina failus un izvada rezultātu
                    break;
                case "about":
                    // Komanda informācijas par autoriem parādīšanai
                    about();
                    break;
                case "exit":
                    // Komanda programmas izbeigšanai
                    break loop; // Iziet no bezgalīgā cikla
                default:
                    // Ja komanda nav atpazīta
                    if (!choiseStr.isEmpty())
                        System.out.println("Unknown command. Allowed: comp, decomp, size, equal, about, exit");
            }
        }
        // Aizver Scanner objektu
        sc.close();
    }

    // Metode faila kompresēšanai
    public static void comp(String sourceFile, String resultFile) {
        try {
            // Izveido File objektu avota failam
            File vFaile = new File(sourceFile);
            // Pārbauda, vai fails eksistē un ir fails (nevis direktorija)
            if (!vFaile.exists() || !vFaile.isFile()) {
                System.out.println("Source file not found: " + sourceFile);
                return;
            }

            // Nolasa visus baitus no faila
            byte[] originals = visiBaiti(vFaile);
            // Saglabā oriģinālā faila garumu
            long origGarums = originals.length;

            // 1) Pielieto Burrows-Wheeler Transform (BWT)
            BWTResult bwt = BWT.transform(originals);

            // 2) Pielieto Move-To-Front (MTF) kodēšanu
            byte[] mtf = MTF.encode(bwt.lastColumn);

            // 3) Pielieto Run-Length Encoding (RLE) - pāru formātā [skaits][vērtība]
            byte[] rle = RLE.encode(mtf);

            // 4) Izveido frekvenču tabulu Huffman kodēšanai
            long[] biezhums = new long[256];
            for (byte b : rle) biezhums[b & 0xFF]++; // Skaita katru baitu

            // Būvē Huffman koku un ģenerē kodus
            Huffman.Node root = Huffman.buildTree(biezhums);
            String[] codes = new String[256]; // Masīvs Huffman kodiem
            if (root != null) Huffman.buildCodes(root, "", codes); // Ģenerē kodus

            // Raksta arhīvu: MAGIC | origGarums(long) | bwtPrimaryIndex(int) | rleLength(int) | biezhums[256]*long | bitstream(huffman-kodēts rle)
            try (FileOutputStream fos = new FileOutputStream(resultFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                dos.write(MAGIC); // Raksta maģisko identifikatoru
                dos.writeLong(origGarums); // Raksta oriģinālo garumu
                dos.writeInt(bwt.primaryIndex); // Raksta BWT primāro indeksu
                dos.writeInt(rle.length); // Raksta RLE datu garumu
                for (int i = 0; i < 256; i++) dos.writeLong(biezhums[i]); // Raksta frekvenču tabulu
                dos.flush(); // Izvieto buferus

                // Izveido bitu izvades plūsmu
                BitOutputStream bitOut = new BitOutputStream(bos);
                // Kodē katru RLE baitu ar Huffman kodu
                for (byte b : rle) {
                    String code = codes[b & 0xFF]; // Iegūst Huffman kodu
                    if (code == null || code.length() == 0) code = "0"; // Ja nav koda, izmanto "0"
                    for (char c : code.toCharArray()) bitOut.writeBit(c == '1' ? 1 : 0); // Raksta bitus
                }
                bitOut.flushBitsOnly(); // Izvieto atlikušos bitus
                bos.flush(); // Izvieto buferus
            }

            // Aprēķina saspiestā faila izmēru
            long saspiestsGarums = new File(resultFile).length();
            // Aprēķina kompresijas koeficientu
            double ratio = saspiestsGarums == 0 ? 0.0 : (double) origGarums / saspiestsGarums;
            System.out.printf("Compression finished. Ratio: %.2f%n", ratio);

        } catch (IOException ex) {
            // Apstrādā kļūdas kompresēšanas laikā
            System.out.println("Error during compression: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    // Metode faila dekompresēšanai
    public static void decomp(String sourceFile, String resultFile) {
        try {
            // Izveido File objektu arhīva failam
            File vFaile = new File(sourceFile);
            // Pārbauda, vai arhīvs eksistē
            if (!vFaile.exists() || !vFaile.isFile()) {
                System.out.println("Archive file not found: " + sourceFile);
                return;
            }

            // Atver arhīva failu lasīšanai
            try (FileInputStream fis = new FileInputStream(vFaile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream dis = new DataInputStream(bis)) {

                // Nolasa un pārbauda maģisko identifikatoru
                byte[] magicRead = new byte[4];
                dis.readFully(magicRead);
                if (!Arrays.equals(magicRead, MAGIC)) {
                    System.out.println("Not a valid archive (magic mismatch).");
                    return;
                }

                // Nolasa metadatus
                long origGarums = dis.readLong(); // Oriģinālais garums
                int bwtPrimary = dis.readInt(); // BWT primārais indekss
                int rleGarums = dis.readInt(); // RLE datu garums
                // Nolasa frekvenču tabulu
                long[] freq = new long[256];
                for (int i = 0; i < 256; i++) freq[i] = dis.readLong();

                // Atjauno Huffman koku
                Huffman.Node root = Huffman.buildTree(freq);

                // Dekodē Huffman kodētos datus
                byte[] rle = new byte[rleGarums];
                if (rleGarums > 0 && root != null) {
                    BitInputStream bitIn = new BitInputStream(bis);
                    int produced = 0; // Saražoto baitu skaits
                    if (root.isLeaf()) {
                        // Ja tikai viens simbols
                        byte sym = (byte) root.symbol;
                        for (int i = 0; i < rleGarums; i++) rle[i] = sym;
                    } else {
                        // Dekodē, ejot pa Huffman koku
                        Huffman.Node node = root;
                        while (produced < rleGarums) {
                            int bit = bitIn.readBit(); // Nolasa nākamo bitu
                            if (bit == -1) break; // Ja beigas
                            node = (bit == 0) ? node.left : node.right; // Iet pa koku
                            if (node.isLeaf()) {
                                // Ja sasniedzis lapu, pievieno simbolu
                                rle[produced++] = (byte) node.symbol;
                                node = root; // Atgriežas pie saknes
                            }
                        }
                    }
                }

                // Dekodē RLE -> MTF
                byte[] mtf = RLE.decode(rle);
                // Dekodē MTF -> pēdējā kolonna
                byte[] last = MTF.decode(mtf);
                // Pielieto apgriezto BWT
                byte[] originals = BWT.inverse(last, bwtPrimary);

                // Raksta atjaunoto failu
                try (FileOutputStream fos = new FileOutputStream(resultFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    bos.write(originals); // Raksta visus baitus
                    bos.flush();
                }
            }
        } catch (IOException ex) {
            // Apstrādā kļūdas dekompresēšanas laikā
            System.out.println("Error during decompression: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    // Metode faila izmēra parādīšanai
    public static void size(String sourceFile) {
        try {
            // Atver failu
            FileInputStream f = new FileInputStream(sourceFile);
            // Parāda pieejamo baitu skaitu
            System.out.println("size: " + f.available());
            f.close(); // Aizver failu
        }
        catch (IOException ex) {
            // Parāda kļūdas ziņojumu
            System.out.println(ex.getMessage());
        }
    }

    // Metode divu failu salīdzināšanai
    public static boolean equal(String firstFile, String secondFile) {
        try {
            // Atver abus failus
            FileInputStream f1 = new FileInputStream(firstFile);
            FileInputStream f2 = new FileInputStream(secondFile);
            int k1, k2; // Nolasīto baitu skaits
            byte[] buf1 = new byte[1000]; // Buferis pirmajam failam
            byte[] buf2 = new byte[1000]; // Buferis otrajam failam
            do {
                // Nolasa abus failus pa 1000 baitiem
                k1 = f1.read(buf1);
                k2 = f2.read(buf2);
                // Ja nolasīts dažāds baitu skaits, faili nav vienādi
                if (k1 != k2) {
                    f1.close();
                    f2.close();
                    return false;
                }
                // Salīdzina katru baitu
                for (int i=0; i<k1; i++) {
                    if (buf1[i] != buf2[i]) {
                        f1.close();
                        f2.close();
                        return false; // Ja atšķiras, nav vienādi
                    }
                }
            } while (!(k1 == -1 && k2 == -1)); // Turpina līdz abu failu beigām
            // Aizver failus
            f1.close();
            f2.close();
            return true; // Faili ir vienādi
        }
        catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    // Metode informācijas par autoriem parādīšanai
    public static void about() {
        System.out.println("251RDC017 Jana Kuranova 16");
        System.out.println("251RDC054 Aļona Strahova 16");
        System.out.println("251RDC019 Marija Mičule 16");
    }

    // ----------------- PALĪGKLASES: BWT, MTF, RLE, Huffman, BitStreams -----------------

    // Maģiskais identifikators arhīva atpazīšanai
    private static final byte[] MAGIC = new byte[] { 'B', 'W', 'H', '1' }; // BWT+MTF+RLE+Huffman

    // ---------- BWT (Burrows-Wheeler Transform) ----------
    // Klase BWT rezultāta glabāšanai
    private static class BWTResult {
        final byte[] lastColumn; // Pēdējā kolonna pēc transformācijas
        final int primaryIndex; // Primārais indekss (kur bija oriģinālā virkne)
        BWTResult(byte[] lastColumn, int primaryIndex) {
            this.lastColumn = lastColumn;
            this.primaryIndex = primaryIndex;
        }
    }

    // Klase BWT transformācijai
    private static class BWT {
        // BWT transformācijas metode
        static BWTResult transform(byte[] input) {
            // Ja ievade ir tukša, atgriež tukšu rezultātu
            if (input == null || input.length == 0) return new BWTResult(new byte[0], 0);
            int n = input.length;
            // Izveido masīvu ar indeksiem visām rotācijām
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            final byte[] data = input;
            // Kārto rotācijas leksikogrāfiski
            java.util.Arrays.sort(idx, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    int i = a, j = b;
                    // Salīdzina rotācijas simbolu pa simbolam
                    for (int k = 0; k < n; k++) {
                        int ca = data[(i + k) % n] & 0xFF;
                        int cb = data[(j + k) % n] & 0xFF;
                        if (ca != cb) return ca - cb;
                    }
                    return 0;
                }
            });
            // Izveido pēdējo kolonnu
            byte[] last = new byte[n];
            int primary = -1; // Primārais indekss
            for (int r = 0; r < n; r++) {
                int start = idx[r]; // Rotācijas sākuma pozīcija
                int lastPos = (start + n - 1) % n; // Pēdējā pozīcija
                last[r] = data[lastPos]; // Pēdējais simbols
                if (start == 0) primary = r; // Atrod oriģinālo rindu
            }
            return new BWTResult(last, primary);
        }

        // BWT apgrieztā transformācija
        static byte[] inverse(byte[] last, int primary) {
            if (last == null || last.length == 0) return new byte[0];
            int n = last.length;
            // Izveido pirmo kolonnu, kārtojot pēdējo
            int[] counts = new int[256]; // Skaita simbolu frekvences
            for (byte b : last) counts[b & 0xFF]++;
            int[] starts = new int[256]; // Sākuma pozīcijas katram simbolam
            int sum = 0;
            for (int i = 0; i < 256; i++) {
                starts[i] = sum;
                sum += counts[i];
            }
            // Aprēķina katras pozīcijas rangu
            int[] occ = new int[n];
            int[] seen = new int[256];
            for (int i = 0; i < n; i++) {
                int v = last[i] & 0xFF;
                occ[i] = seen[v]; // Simbola sastapšanas reizes
                seen[v]++;
            }
            // Atjauno oriģinālo virkni, izmantojot LF-mapping
            byte[] res = new byte[n];
            int idx = primary; // Sāk ar primāro indeksu
            for (int i = n - 1; i >= 0; i--) {
                res[i] = last[idx]; // Pievieno simbolu
                int v = last[idx] & 0xFF;
                idx = starts[v] + occ[idx]; // Aprēķina nākamo indeksu
            }
            return res;
        }
    }

    // ---------- MTF (Move-To-Front) ----------
    private static class MTF {
        // MTF kodēšana
        static byte[] encode(byte[] data) {
            if (data == null || data.length == 0) return new byte[0];
            // Izveido simbolu sarakstu (0-255)
            byte[] symbols = new byte[256];
            for (int i = 0; i < 256; i++) symbols[i] = (byte) i;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Apstrādā katru baitu
            for (byte b : data) {
                int val = b & 0xFF;
                int pos = 0;
                // Atrod simbola pozīciju sarakstā
                while ((symbols[pos] & 0xFF) != val) pos++;
                baos.write(pos); // Raksta pozīciju
                // Pārvieto simbolu uz priekšu
                byte sym = symbols[pos];
                System.arraycopy(symbols, 0, symbols, 1, pos);
                symbols[0] = sym;
            }
            return baos.toByteArray();
        }

        // MTF dekodēšana
        static byte[] decode(byte[] data) {
            if (data == null || data.length == 0) return new byte[0];
            // Izveido simbolu sarakstu (0-255)
            byte[] symbols = new byte[256];
            for (int i = 0; i < 256; i++) symbols[i] = (byte) i;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Apstrādā katru pozīciju
            for (byte b : data) {
                int pos = b & 0xFF;
                byte sym = symbols[pos]; // Iegūst simbolu no pozīcijas
                baos.write(sym & 0xFF); // Raksta simbolu
                // Pārvieto simbolu uz priekšu
                System.arraycopy(symbols, 0, symbols, 1, pos);
                symbols[0] = sym;
            }
            return baos.toByteArray();
        }
    }

    // ---------- RLE (Run-Length Encoding) ----------
    private static class RLE {
        // RLE kodēšana (vienkārša pāru kodēšana)
        static byte[] encode(byte[] data) throws IOException {
            if (data == null || data.length == 0) return new byte[0];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = 0;
            while (i < data.length) {
                int j = i + 1;
                // Skaita vienādus baitus (maksimāli 255)
                while (j < data.length && data[j] == data[i] && (j - i) < 255) j++;
                int count = j - i; // Atkārtojumu skaits (1..255)
                baos.write(count); // Raksta skaitu
                baos.write(data[i]); // Raksta vērtību
                i = j;
            }
            return baos.toByteArray();
        }

        // RLE dekodēšana
        static byte[] decode(byte[] rle) throws IOException {
            if (rle == null || rle.length == 0) return new byte[0];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = 0;
            while (i + 1 < rle.length) {
                int count = rle[i++] & 0xFF; // Nolasa skaitu
                byte val = rle[i++]; // Nolasa vērtību
                // Raksta vērtību count reizes
                for (int k = 0; k < count; k++) baos.write(val & 0xFF);
            }
            return baos.toByteArray();
        }
    }

    // ---------- HUFFMAN ----------
    private static class Huffman {
        // Huffman koka mezgls
        private static class Node implements Comparable<Node> {
            final long freq; // Frekvence
            final int symbol; // Simbols (0..255 lapai, -1 iekšējam mezglam)
            final Node left, right; // Kreisais un labais bērns
            Node(long freq, int symbol, Node left, Node right) {
                this.freq = freq;
                this.symbol = symbol;
                this.left = left;
                this.right = right;
            }
            boolean isLeaf() { return left == null && right == null; } // Pārbauda, vai ir lapa
            public int compareTo(Node o) {
                // Salīdzina pēc frekvences
                int c = Long.compare(this.freq, o.freq);
                if (c == 0) return Integer.compare(this.symbol, o.symbol);
                return c;
            }
        }

        // Būvē Huffman koku
        static Node buildTree(long[] freq) {
            // Izveido prioritātes rindu
            PriorityQueue<Node> pq = new PriorityQueue<>();
            // Pievieno visus simbolus ar frekvenci > 0
            for (int i = 0; i < 256; i++) if (freq[i] > 0) pq.add(new Node(freq[i], i, null, null));
            if (pq.isEmpty()) return null; // Ja nav simbolu
            if (pq.size() == 1) {
                // Ja tikai viens simbols, izveido minimālo koku
                Node only = pq.poll();
                return new Node(only.freq, -1, only, null);
            }
            // Būvē koku, apvienojot mezglus
            while (pq.size() > 1) {
                Node a = pq.poll(); // Mazākais
                Node b = pq.poll(); // Nākamais mazākais
                // Izveido vecāka mezglu
                Node p = new Node(a.freq + b.freq, Math.min(a.symbol, b.symbol), a, b);
                pq.add(p);
            }
            return pq.poll(); // Atgriež saknes mezglu
        }

        // Būvē Huffman kodus
        static void buildCodes(Node node, String prefix, String[] codes) {
            if (node == null) return;
            if (node.isLeaf()) {
                // Ja lapa, saglabā kodu
                codes[node.symbol] = prefix;
            } else {
                // Rekursīvi apstrādā kreiso un labo pusi
                buildCodes(node.left, prefix + "0", codes);
                buildCodes(node.right, prefix + "1", codes);
            }
        }
    }

    // ---------- Bitu plūsmas ----------
    // Bitu izvades plūsma
    private static class BitOutputStream implements java.io.Closeable {
        private final OutputStream out; // Pamata izvades plūsma
        private int currentByte = 0; // Pašreizējais baits
        private int numBitsFilled = 0; // Aizpildīto bitu skaits
        BitOutputStream(OutputStream out) { this.out = out; }

        // Raksta vienu bitu
        void writeBit(int b) throws IOException {
            currentByte = (currentByte << 1) | (b & 1); // Pievieno bitu
            numBitsFilled++;
            if (numBitsFilled == 8) {
                // Ja baits pilns, raksta to
                out.write(currentByte);
                numBitsFilled = 0;
                currentByte = 0;
            }
        }

        // Izvieto atlikušos bitus (neaizver pamata plūsmu)
        void flushBitsOnly() throws IOException {
            if (numBitsFilled > 0) {
                // Papildina ar nullēm un raksta
                out.write(currentByte << (8 - numBitsFilled));
                numBitsFilled = 0;
                currentByte = 0;
            }
            out.flush();
        }

        @Override
        public void close() throws IOException {
            flushBitsOnly();
            // Pamata plūsma paliek atvērta (izsaucējs to aizver)
        }
    }

    // Bitu ievades plūsma
    private static class BitInputStream implements java.io.Closeable {
        private final InputStream in; // Pamata ievades plūsma
        private int tekoshBit = 0; // Pašreizējais baits
        private int numursBitAtl = 0; // Atlikušo bitu skaits
        BitInputStream(InputStream in) { this.in = in; }

        // Nolasa vienu bitu
        int readBit() throws IOException {
            if (numursBitAtl == 0) {
                // Ja nav atlikušo bitu, nolasa jaunu baitu
                tekoshBit = in.read();
                if (tekoshBit == -1) return -1; // Faila beigas
                numursBitAtl = 8;
            }
            numursBitAtl--;
            // Atgriež nākamo bitu
            return (tekoshBit >> numursBitAtl) & 1;
        }

        @Override
        public void close() throws IOException { in.close(); }
    }

    // ---------- IO palīgmetode ----------
    // Nolasa visus baitus no faila
    private static byte[] visiBaiti(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; // Buferis 8KB
            int r;
            // Nolasa failu pa blokiem
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return baos.toByteArray(); // Atgriež visu saturu
        }
    }
}