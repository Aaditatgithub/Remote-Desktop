import java.util.Scanner;

public class HammingCode {

    // Function to calculate the parity bits
    public static int[] calculateHammingCode(int[] data) {
        int n = data.length;
        int r = 0;

        // Find the number of parity bits required
        while (Math.pow(2, r) < (n + r + 1)) {
            r++;
        }

        // Initialize the array to store Hamming Code (data bits + parity bits)
        int[] hammingCode = new int[n + r];

        // Fill the parity bits with -1 as placeholders
        int j = 0, k = 0;
        for (int i = 1; i <= hammingCode.length; i++) {
            if (Math.pow(2, k) == i) {
                hammingCode[i - 1] = -1; // Placeholder for parity bits
                k++;
            } else {
                hammingCode[i - 1] = data[j];
                j++;
            }
        }

        // Calculate parity bits
        for (int i = 0; i < r; i++) {
            int x = (int) Math.pow(2, i);
            for (int p = x; p <= hammingCode.length; p += (x * 2)) {
                for (int q = p - 1; q < (p + x - 1) && q < hammingCode.length; q++) {
                    if (hammingCode[q] != -1) {
                        hammingCode[x - 1] ^= hammingCode[q];
                    }
                }
            }
        }

        return hammingCode;
    }

    // Function to detect and correct a single-bit error
    public static int detectAndCorrectError(int[] receivedData) {
        int r = 0;
        while (Math.pow(2, r) < receivedData.length) {
            r++;
        }

        int errorPosition = 0;

        // Calculate the parity bits for error detection
        for (int i = 0; i < r; i++) {
            int x = (int) Math.pow(2, i);
            int parity = 0;
            for (int p = x; p <= receivedData.length; p += (x * 2)) {
                for (int q = p - 1; q < (p + x - 1) && q < receivedData.length; q++) {
                    parity ^= receivedData[q];
                }
            }
            if (parity != 0) {
                errorPosition += x;
            }
        }

        // If errorPosition is not 0, correct the error
        if (errorPosition > 0) {
            System.out.println("Error detected at position: " + errorPosition);
            receivedData[errorPosition - 1] ^= 1; // Correct the error
            System.out.println("Error corrected.");
        } else {
            System.out.println("No error detected.");
        }

        return errorPosition;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Input the original data (7 or 8 bits)
        System.out.println("Enter the number of bits (7 or 8): ");
        int bitLength = scanner.nextInt();
        int[] data = new int[bitLength];

        System.out.println("Enter the data bits (one by one): ");
        for (int i = 0; i < bitLength; i++) {
            data[i] = scanner.nextInt();
        }

        // Generate the Hamming code
        int[] hammingCode = calculateHammingCode(data);
        System.out.print("Generated Hamming Code: ");
        for (int bit : hammingCode) {
            System.out.print(bit);
        }
        System.out.println();

        // Simulate receiving the Hamming code with an error
        System.out.println("Enter the received Hamming Code (one by one): ");
        int[] receivedData = new int[hammingCode.length];
        for (int i = 0; i < receivedData.length; i++) {
            receivedData[i] = scanner.nextInt();
        }

        // Detect and correct any single-bit error
        detectAndCorrectError(receivedData);

        // Display the corrected Hamming code
        System.out.print("Corrected Hamming Code: ");
        for (int bit : receivedData) {
            System.out.print(bit);
        }
    }
}
