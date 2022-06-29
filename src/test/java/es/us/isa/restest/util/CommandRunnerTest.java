package es.us.isa.restest.util;

import org.junit.Test;

import static es.us.isa.restest.util.CommandRunner.runCommand;
import static org.junit.Assert.*;

public class CommandRunnerTest {

    @Test
    public void testRunCommand() throws RESTestException {
        runCommand("python3", new String[]{"./ml/python-scripts/train_predictor.py"});
    }

}