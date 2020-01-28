# RESTest
RESTest

### Things to take into account
* If you want to obtain coverage stats in CSV, you need to create a [CSVReportManager](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/util/CSVReportManager.java), but also modify the [Writer](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/testcases/writers/RESTAssuredWriter.java). The reason for this is that output coverage (status codes, response body properties...) can only be computed after the test suite is executed, therefore the Writer needs to instrument the code to compute the output coverage after running the automatically generated test cases. The code should be similar to the following:
```java
CSVReportManager csvReportManager = new CSVReportManager(testDataDir, coverageDataDir);
RESTAssuredWriter writer = new RESTAssuredWriter(...);

// YOU NEED TO INCLUDE THE FOLLOWING TWO LINES TO COMPUTE OUTPUT COVERAGE
writer.setEnableStats(true);
writer.setAPIName(APIName);
```
