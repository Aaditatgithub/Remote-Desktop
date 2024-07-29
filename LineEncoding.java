public class LineEncoding {

    public static void main(String[] args) {
        String data = "10111001";

        System.out.println("NRZ-L: " + nrzl(data));
        System.out.println("NRZ-I: " + nrzi(data));
        System.out.println("Unipolar: " + unipolar(data));
        System.out.println("Bipolar: " + bipolar(data));
        System.out.println("Manchester: " + manchester(data));
        System.out.println("Differential Manchester: " + differentialManchester(data));
    }

    public static String nrzl(String data) {
        StringBuilder result = new StringBuilder();
        for (char bit : data.toCharArray()) {
            result.append(bit == '1' ? "+ " : "- ");
        }
        return result.toString();
    }

    public static String nrzi(String data) {
        StringBuilder result = new StringBuilder();
        char prev = '-';
        for (char bit : data.toCharArray()) {
            if (bit == '1') {
                prev = (prev == '+') ? '-' : '+';
            }
            result.append(prev).append(" ");
        }
        return result.toString();
    }

    public static String unipolar(String data) {
        StringBuilder result = new StringBuilder();
        for (char bit : data.toCharArray()) {
            result.append(bit == '1' ? "+ " : "0 ");
        }
        return result.toString();
    }

    public static String bipolar(String data) {
        StringBuilder result = new StringBuilder();
        boolean positive = true;
        for (char bit : data.toCharArray()) {
            if (bit == '1') {
                result.append(positive ? "+ " : "- ");
                positive = !positive;
            } else {
                result.append("0 ");
            }
        }
        return result.toString();
    }

    public static String manchester(String data) {
        StringBuilder result = new StringBuilder();
        for (char bit : data.toCharArray()) {
            result.append(bit == '1' ? "+ - " : "- + ");
        }
        return result.toString();
    }

    public static String differentialManchester(String data) {
        StringBuilder result = new StringBuilder();
        char prev = '-';
        for (char bit : data.toCharArray()) {
            if (bit == '1') {
                prev = (prev == '+') ? '-' : '+';
            }
            result.append(prev).append((prev == '+') ? "- " : "+ ");
        }
        return result.toString();
    }
}
