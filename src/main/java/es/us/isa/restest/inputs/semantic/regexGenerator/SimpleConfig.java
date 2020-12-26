package es.us.isa.restest.inputs.semantic.regexGenerator;

import it.units.inginf.male.configuration.Configuration;
import it.units.inginf.male.configuration.DatasetContainer;
import it.units.inginf.male.generations.EmptyPopulationBuilder;
import it.units.inginf.male.generations.FlaggingNaivePopulationBuilder;
import it.units.inginf.male.generations.TokenizedPopulationBuilder;
import it.units.inginf.male.inputs.DataSet;
import it.units.inginf.male.objective.FlaggingAccuracyPrecisionLengthObjective;
import it.units.inginf.male.selections.best.BasicFlaggingLearningBestSelector;
import it.units.inginf.male.strategy.impl.MultithreadStrategy;
import it.units.inginf.male.terminalsets.FlaggingNgramsTerminalSetBuilder;
import it.units.inginf.male.terminalsets.TokenizedTerminalSetBuilder;
import java.util.Arrays;
import java.util.logging.Logger;

public class SimpleConfig {
    //Maximum unmatch_chars/match_chars ratio
    //and sets the maximum unmatch_chars/match_chars ratio; this value defines the margin size around the matches
    transient private final double STRIPING_DEFAULT_MARGIN_SIZE = 10;
    public int numberThreads;
    public int numberOfJobs;
    public int generations;
    public int populationSize;
    public DataSet dataset;
    public boolean populateOptionalFields;
    public boolean isStriped = false;
    public boolean isFlagging = false;

    /**
     * Percentange [0,100] of the number of the generations used for the Spared termination
     * criteria.
     */
    public double termination = 20.0;
    public String comment;

    public Configuration buildConfiguration(){
        assert !(isFlagging&&isStriped);

        Configuration configuration = new Configuration();
        configuration.setConfigName("Console config");
        configuration.getEvolutionParameters().setGenerations(generations);
        configuration.getEvolutionParameters().setPopulationSize(populationSize);
        configuration.setJobs(numberOfJobs);
        configuration.getStrategyParameters().put(MultithreadStrategy.THREADS_KEY, String.valueOf(numberThreads));

        int terminationGenerations = (int)(termination * configuration.getEvolutionParameters().getGenerations() / 100.0);
        if(termination==100.0){
            configuration.getStrategyParameters().put("terminationCriteria","false");
        } else {
            configuration.getStrategyParameters().put("terminationCriteria","true");
        }
        configuration.getStrategyParameters().put("terminationCriteriaGenerations", String.valueOf(terminationGenerations));
        //Added terminationCriteria for the second strategy
        configuration.getStrategyParameters().put("terminationCriteria2","false");

        if(dataset == null){
            throw new IllegalArgumentException("You must define a dataset");
        }
        dataset.populateUnmatchesFromMatches();
        DatasetContainer datasetContainer = new DatasetContainer(dataset);
        datasetContainer.createDefaultRanges((int) configuration.getInitialSeed());
        //checks if striping is needed
        dataset.updateStats();
        if(isStriped){
            Logger.getLogger(this.getClass().getName()).info("Enabled striping.");
            datasetContainer.setDataSetsStriped(true);
            datasetContainer.setDatasetStripeMarginSize(STRIPING_DEFAULT_MARGIN_SIZE);
            datasetContainer.setProposedNormalDatasetInterval(100);//terminationGenerations+50);
        }
        configuration.setDatasetContainer(datasetContainer); //remind that after setting the DataSetContainer.. we need to update configuration in order to invoke datacontainer update methods

        configuration.setup(); //initializes datasetcontainer, populationbuilder and terminalsetbuilder

        return configuration;
    }
}