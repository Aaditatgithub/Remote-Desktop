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

    public static String nrzl(String data) {        // bit - 1 -> +ve voltage else negative
        StringBuilder result = new StringBuilder();
        for (char bit : data.toCharArray()) {
            result.append(bit == '1' ? "+ " : "- ");
        }
        return result.toString();
    }

    public static String nrzi(String data) {    // if data element is 1 and polarity previously was 1 then change the polarity
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
        // only +ve polarity exists, if 1 : + else -
        StringBuilder result = new StringBuilder();
        for (char bit : data.toCharArray()) {
            result.append(bit == '1' ? "+ " : "0 ");
        }
        return result.toString();
    }

    public static String bipolar(String data) {

        // inverse polarity on occurence of 1
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

        // mapping ratio = 1/2 
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
