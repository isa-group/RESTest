package es.us.isa.restest.inputs.boundary;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.inputs.random.RandomStringGenerator;

public class BoundaryStringConfigurator {
    private int minLength;                  // Lower boundary
    private int maxLength;                  // Upper boundary
    private int delta;                      // Value to add and subtract to max and min length
    private boolean includeEmptyString;     // Whether to include the empty string case
    private boolean includeNullCharacter;   // Whether to include the null character case

    public BoundaryStringConfigurator() {
        minLength = 0;
        maxLength = 1024;
        delta = 2;
        includeEmptyString = true;
        includeNullCharacter =  true;
    }

    
    /**
     * 
     * @param minLength Minimum length of the string to be generated
     * @param maxLength Maximum length of the string to be generated
     * @param delta Number of characters to be added/removed from min/max boundaries
     * @param includeEmptyString Empty string generations
     * @param includeNullCharacter	Include null characters ("\0")
     */
    public BoundaryStringConfigurator(int minLength, int maxLength, int delta, boolean includeEmptyString, boolean includeNullCharacter) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.delta = delta;
        this.includeEmptyString = includeEmptyString;
        this.includeNullCharacter = includeNullCharacter;
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

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public boolean getIncludeEmptyString() {
        return includeEmptyString;
    }

    public void setIncludeEmptyString(boolean includeEmptyString) {
        this.includeEmptyString = includeEmptyString;
    }

    public boolean getIncludeNullCharacter() {
        return includeNullCharacter;
    }

    public void setIncludeNullCharacter(boolean includeNullCharacter) {
        this.includeNullCharacter = includeNullCharacter;
    }

    // Return boundary values to be used with an InputValueIterator or RandomInputValueIterator
    public List<String> returnValues() {
        List<String> values = new ArrayList<>();

        if (includeEmptyString)
            values.add("");
        if (includeNullCharacter)
            values.add("\0");

        // Create RandomStringGenerators using configuration parameters
        List<RandomStringGenerator> strGens = createRandomStringGenerators(minLength, maxLength, delta);
        for (RandomStringGenerator strGen: strGens) {
            values.add(strGen.nextValue());
        }

        return values;
    }

    private List<RandomStringGenerator> createRandomStringGenerators(int minLength, int maxLength, int delta) {
        List<RandomStringGenerator> strGens = new ArrayList<>();

        // TODO: Create more generators or think of a different way to generate the strings

        // Alphabetic strings
        strGens.add(new RandomStringGenerator(minLength, minLength, true, false, false));
        strGens.add(new RandomStringGenerator(minLength+delta, minLength+delta, true, false, false));
        strGens.add(new RandomStringGenerator(maxLength, maxLength, true, false, false));
        strGens.add(new RandomStringGenerator(maxLength+delta, maxLength+delta, true, false, false));
        strGens.add(new RandomStringGenerator((int)Math.ceil(((double)minLength+maxLength)/2), (int)Math.ceil(((double)minLength+maxLength)/2), true, false, false));

        // ASCII strings
        strGens.add(new RandomStringGenerator(minLength, minLength, true, true, true));
        strGens.add(new RandomStringGenerator(minLength+delta, minLength+delta, true, true, true));
        strGens.add(new RandomStringGenerator(maxLength, maxLength, true, true, true));
        strGens.add(new RandomStringGenerator(maxLength+delta, maxLength+delta, true, true, true));
        strGens.add(new RandomStringGenerator((int)Math.ceil(((double)minLength+maxLength)/2), (int)Math.ceil(((double)minLength+maxLength)/2), true, true, true));

        // Special cases: (minLength-delta) and (maxLength-delta), only if the result is positive
        if (minLength - delta > 0) {
            strGens.add(new RandomStringGenerator(minLength-delta, minLength-delta, true, false, false));
            strGens.add(new RandomStringGenerator(minLength-delta, minLength-delta, true, true, true));
        }
        if (maxLength - delta > 0) {
            strGens.add(new RandomStringGenerator(maxLength-delta, maxLength-delta, true, false, false));
            strGens.add(new RandomStringGenerator(maxLength-delta, maxLength-delta, true, true, true));
        }

        return strGens;
    }
}
