 import java.io.File;
import java.text.DecimalFormat;

public class ld13vremja {
    public static void main(String[] args) throws Exception {

        // ====== ВРЕМЯ: старт ======
        long start = System.nanoTime();

        // ====== ТВОЙ КОД СЖАТИЯ ЗДЕСЬ ======
        // Например: compress("input.html", "output.bin");
        // ===================================

        long end = System.nanoTime();
        // ====== ВРЕМЯ: конец ======

        // ====== Время выполнения ======
        double millis = (end - start) / 1_000_000.0;
        System.out.println("Время выполнения: " + millis + " ms");


        // ====== КОЭФФИЦИЕНТ СЖАТИЯ ======
        File original   = new File("input.html");
        File compressed = new File("output.bin");

        double ratio = (double) original.length() / compressed.length();
        DecimalFormat df = new DecimalFormat("#.##");

        System.out.println("Коэффициент сжатия: " + df.format(ratio) + "x");
    }
}


