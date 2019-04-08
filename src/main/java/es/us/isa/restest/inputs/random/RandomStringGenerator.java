package es.us.isa.restest.inputs.random;

import org.apache.commons.lang3.RandomStringUtils;

public class RandomStringGenerator extends RandomGenerator {

    private int minLength;
    private int maxLength;
    private boolean includeAlphabetic;
    private boolean includeNumbers;
    private boolean includeSpecialCharacters;

    public RandomStringGenerator() {
        minLength = 0;
        maxLength = 10;
        includeAlphabetic = true;
        includeNumbers = false;
        includeSpecialCharacters = false;
    }

    public RandomStringGenerator(int minLength, int maxLength, boolean includeAlphabetic, boolean includeNumbers, boolean includeSpecialCharacters) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.includeAlphabetic = includeAlphabetic;
        this.includeNumbers = includeNumbers;
        this.includeSpecialCharacters = includeSpecialCharacters;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean getIncludeAlphabetic() {
        return includeAlphabetic;
    }

    public void setIncludeAlphabetic(boolean includeAlphabetic) {
        this.includeAlphabetic = includeAlphabetic;
    }

    public boolean getIncludeNumbers() {
        return includeNumbers;
    }

    public void setIncludeNumbers(boolean includeNumbers) {
        this.includeNumbers = includeNumbers;
    }

    public boolean getIncludeSpecialCharacters() {
        return includeSpecialCharacters;
    }

    public void setIncludeSpecialCharacters(boolean includeSpecialCharacters) {
        this.includeSpecialCharacters = includeSpecialCharacters;
    }

    @Override
    public String nextValue() {
        String generatedString = null;

        // Get string params
        int stringConf = 4*(includeAlphabetic ? 1 : 0) + 2*(includeNumbers ? 1 : 0) + 1*(includeSpecialCharacters ? 1 : 0);
        int stringLength = this.rand.nextInt(minLength, maxLength);

        switch(stringConf) {
            case 7:
                generatedString = RandomStringUtils.randomAscii(stringLength);
                break;
            case 6:
                generatedString = RandomStringUtils.randomAlphanumeric(stringLength);
                break;
            case 4:
                generatedString = RandomStringUtils.randomAlphabetic(stringLength);
                break;
            case 2:
                generatedString = RandomStringUtils.randomNumeric(stringLength);
                break;
            case 0:
                generatedString = "";
                break;
            case 5:
            case 3:
            case 1:
                generatedString = completeString(RandomStringUtils.randomAscii(stringLength), stringConf);
                break;
            default:
                // TODO: Is the following exception being properly used?
                throw new IllegalStateException("Illegal stringConf: " + stringConf);
        }

        return generatedString;
    }

    @Override
    public String nextValueAsString() {
        return nextValue();
    }

    private String completeString(String firstString, int stringConf) {
        String finalString = firstString;

        do {
            // Generate maximum-length ASCII string and then remove characters not permitted
            finalString += RandomStringUtils.randomAscii(maxLength - finalString.length());
            switch (stringConf) {
                case 5:
                    finalString = finalString.replaceAll("[0-9]", "");
                    break;
                case 3:
                    finalString = finalString.replaceAll("[A-Za-z]", "");
                    break;
                case 1:
                    finalString = finalString.replaceAll("[0-9A-Za-z]", "");
                    break;
                default:
                    throw new IllegalArgumentException("Illegal stringConf for completeString method: " + stringConf);
            }
        } while (finalString.length() < minLength); // Repeat until minimum-length constraint is satisfied

        return finalString;
    }
}
