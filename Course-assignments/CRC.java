public class CRC {

    public static void main(String[] args) {
        String data = "110011100001";
        String generator = "1011";
        
        String encodedData = encodeData(data, generator);
        System.out.println("Encoded Data: " + encodedData);
        
        boolean isValid = checkData(encodedData, generator);
        System.out.println("Is data valid? " + isValid);
    }

    // Method to perform XOR operation
    private static String xor(String a, String b) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < b.length(); i++) {
            result.append(a.charAt(i) == b.charAt(i) ? '0' : '1');
        }
        return result.toString();
    }

    // Method to perform CRC division
    private static String mod2div(String dividend, String divisor) {
        int pick = divisor.length();
        String tmp = dividend.substring(0, pick);

        while (pick < dividend.length()) {
            if (tmp.charAt(0) == '1') {
                tmp = xor(divisor, tmp) + dividend.charAt(pick);
            } else {
                tmp = xor(new String(new char[pick]).replace('\0', '0'), tmp) + dividend.charAt(pick);
            }
            pick += 1;
        }

        if (tmp.charAt(0) == '1') {
            tmp = xor(divisor, tmp);
        } else {
            tmp = xor(new String(new char[pick]).replace('\0', '0'), tmp);
        }

        return tmp;
    }

    // Method to encode data using CRC
    private static String encodeData(String data, String generator) {
        int dataLen = data.length();
        int generatorLen = generator.length();

        String appendedData = (data + new String(new char[generatorLen - 1]).replace('\0', '0'));
        String remainder = mod2div(appendedData, generator);

        return data + remainder;
    }

    // Method to check if received data is valid
    private static boolean checkData(String encodedData, String generator) {
        String remainder = mod2div(encodedData, generator);
        for (char c : remainder.toCharArray()) {
            if (c == '1') {
                return false;
            }
        }
        return true;
    }
}
