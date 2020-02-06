# RESTest
RESTest is a model-based testing framework that provides randomly generated test cases of an API from its specification. It mainly supports RESTful APIs described with the OAS design language.
## Contents
1. [Description of the workflow](#description-of-the-workflow)
* [Things to take into account](#things-to-take-into-account)
# Description of the workflow
It is composed of the following steps:
* **Generation of the test configuration file.** Due to the inability to create test cases only with the API documentation, the framework implements a functionality which automatically creates a test configuration file from the API specification. It specifies the necessary authorization data (i.e. an API key) and provides sample values to a specific parameter, inter alia information. These automatically generated files should be edited to augment it, as they are quite simple.
* **Generation of test abstract cases.** Once the test configuration file has been generated and edited, RESTest is able to create test cases using a specific data generator. Each test case includes the necessary parameters to be properly executed. These test cases are domain-independent, so they can be instantiated in any framework.
* **Generation of REST-Assured test cases.** Next step is to concretize the abstract test cases, instantiating them in a framework using a [source code writer](https://github.com/isa-group/RESTest/blob/develop/src/main/java/es/us/isa/restest/testcases/writers/RESTAssuredWriter.java). RESTest uses [REST Assured](https://github.com/rest-assured/rest-assured) to test and validate the RESTful APIs. The source code writer generates a Java class with JUnit test cases ready to be executed.
* **Execution of test cases and computation of statistics.** Finally, the JUnit test cases are run. At the time this README was last edited (February 2020), the test cases only assert if the status code of the API response is less than 500. Once the test cases are executed, RESTest computes the results and displays them in a GUI. The GUI provides an easy visualization of bugs and API coverage using charts. RESTest uses [Allure framework](http://allure.qatools.ru/) to implement the interface. 

**TODO - Test coverage criteria** 
### Things to take into account
* If you want to obtain coverage stats in CSV, you need to create a [CSVReportManager](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/util/CSVReportManager.java), but also modify the [Writer](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/testcases/writers/RESTAssuredWriter.java). The reason for this is that output coverage (status codes, response body properties...) can only be computed after the test suite is executed, therefore the Writer needs to instrument the code to compute the output coverage after running the automatically generated test cases. The code should be similar to the following:
```java
CSVReportManager csvReportManager = new CSVReportManager(testDataDir, coverageDataDir);
RESTAssuredWriter writer = new RESTAssuredWriter(...);

// YOU NEED TO INCLUDE THE FOLLOWING TWO LINES TO COMPUTE OUTPUT COVERAGE
writer.setEnableStats(true);
writer.setAPIName(APIName);
```
