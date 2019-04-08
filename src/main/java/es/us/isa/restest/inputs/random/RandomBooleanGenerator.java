package es.us.isa.restest.inputs.random;

public class RandomBooleanGenerator extends RandomGenerator {

    private double trueProbability = 0.5;

    public RandomBooleanGenerator() {
        super();
    }

    public RandomBooleanGenerator(float trueProbability) {
        super();

        this.trueProbability = trueProbability;
    }

    public double getTrueProbability() {
        return trueProbability;
    }

    public void setTrueProbability(double trueProbability) {
        this.trueProbability = trueProbability;
    }

    @Override
    public Object nextValue() {
        Boolean value = true;
        if (rand.nextUniform(0, 1) > trueProbability) {
            value = false;
        }

        return value;
    }

    @Override
    public String nextValueAsString() {
        return nextValue().toString();
    }
}
