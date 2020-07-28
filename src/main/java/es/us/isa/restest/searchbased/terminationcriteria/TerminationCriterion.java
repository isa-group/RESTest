package es.us.isa.restest.searchbased.terminationcriteria;

import java.util.function.Predicate;

import org.uma.jmetal.algorithm.Algorithm;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public interface TerminationCriterion extends Predicate<SearchBasedAlgorithm>{

}
