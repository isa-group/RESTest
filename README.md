# RESTest
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.com/isa-group/RESTest.svg)](https://travis-ci.com/isa-group/RESTest)
[![Test coverage](https://sonarcloud.io/api/project_badges/measure?project=isa-group_RESTest&metric=coverage)](https://sonarcloud.io/component_measures?id=isa-group_RESTest&metric=Coverage)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=isa-group_RESTest&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=isa-group_RESTest&metric=Maintainability)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=isa-group_RESTest&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=isa-group_RESTest&metric=Reliability)
[![Security](https://sonarcloud.io/api/project_badges/measure?project=isa-group_RESTest&metric=security_rating)](https://sonarcloud.io/component_measures?id=isa-group_RESTest&metric=Security)

RESTest is a framework for automated black-box testing of RESTful web APIs. It follows a model-based approach, where test cases are automatically derived from the [OpenAPI Specification (OAS)](https://www.openapis.org/) of the API under test. No access to the source code is required, which makes it possible to test APIs written in any programming language, running in local or remote servers.

## RESTest Wiki
In this page you can find a brief description of how RESTest works and an illustrating example. If you want to read the full documentation, please visit the [Wiki](https://github.com/isa-group/RESTest/wiki). 

## How does it work?
The figure below shows how RESTest works:

1. **Test model generation**: RESTest takes as input the OAS specification of the API under test, considered the *system model*. A [*test model*](https://github.com/isa-group/RESTest/wiki/Test-configuration-files) is automatically generated from the system model including test-specific configuration data. The default test model can be manually enriched with fine-grained configuration details such as test data generation settings.

1. **Abstract test case generation**: The system and the test models drive the generation of abstract test cases following user-defined test case generation strategies such as random testing. If the API under test contains [inter-parameter dependencies](https://github.com/isa-group/RESTest/wiki/Inter-parameter-dependencies), then constraint-based testing can be applied, specifying the dependencies in the OAS specification using the IDL4OAS extension (see examples [here](https://github.com/isa-group/IDLReasoner/blob/master/src/test/resources/OAS_example.yaml#L45) and [here](https://github.com/isa-group/IDLReasoner/tree/master/src/test/resources)). Requests satisfying all inter-parameter dependencies are automatically generated thanks to [IDLReasoner](https://github.com/isa-group/IDLReasoner).

1. **Test case generation**: The abstract test cases are instantiated into a specific programming language or testing framework using a [test writer](https://github.com/isa-group/RESTest/wiki/Test-writers). RESTest currently supports the generation of [REST Assured](http://rest-assured.io/) and [Postman](https://www.postman.com/) test cases.

1. **Test case execution**: The test cases are executed and a set of reports and stats are generated. Stats are machine-readable, and the test reports can be graphically visualized thanks to [Allure](http://allure.qatools.ru/).

1. **Feedback collection**: [Test case generators](https://github.com/isa-group/RESTest/wiki/Test-case-generators) can react to the test outputs (i.e., the stats generated in the previous step) to create more sophisticated test cases. An example of this is the [search-based module of the RESTest framework](https://github.com/isa-group/RESTest-search-based), currently in beta.

![RESTest](docs/RESTest_v3.png)

## What can I do with RESTest?
Check out the following demo video, where we discuss some of the things that you can do with RESTest, both from the user and the developer point of view. The showcase shown is the video is available at http://betty.us.es/restest-showcase-demo/.

<a href="https://youtu.be/TnGkwMDBDt4"><img src="docs/play_video.png" alt="RESTest demo video" width="300" /></a>

## Quickstart guide
To get started with RESTest, download the code and move to the parent directory:
````
git clone https://github.com/isa-group/RESTest.git
cd RESTest
````

### Maven configuration
To build and run RESTest, you **MUST** include the dependencies in the `lib` folder on your local Maven repository (e.g., `~/.m2` folder in Mac). You can do it as follows:
```sh
mvn install:install-file -Dfile=lib/JSONmutator.jar -DgroupId=es.us.isa -DartifactId=json-mutator -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=lib/IDL.jar -DgroupId=es.us.isa -DartifactId=idl -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file -Dfile=lib/IDLreasoner.jar
```

### Setting up RESTest

Let's try RESTest with some API, for example, [Bikewise](https://bikewise.org/). Follow these steps:

1. **Get the OAS specification of the API under test**. For Bikewise, it is available at the following path: `src/test/resources/Bikewise/swagger.yaml`.

1. **Generate the test configuration file**. From the OAS spec, we can automatically generate the [test configuration file](https://github.com/isa-group/RESTest/wiki/Test-configuration-files). To do so, run the [CreateTestConf](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/main/CreateTestConf.java) class, located under the `es.us.isa.restest.main` package. The test configuration file will be generated at the location `src/test/resources/Bikewise/testConf.yaml`.

1. **(Optional) Modify the test configuration file to tailor your needs**. For example, you can remove some operations you are not interested to test. For more info, visit the [Wiki](https://github.com/isa-group/RESTest/wiki/Test-configuration-files).

1. **Configure RESTest execution**. To set things like number of test cases to generate, testing technique, etc., you need to create a [RESTest configuration file](https://github.com/isa-group/RESTest/wiki/RESTest-configuration-files). You can find the RESTest configuration file for the Bikewise API at `src/test/resources/Bikewise/bikewise.properties`. With this configuration, a total of 60 test cases will be generated in three iterations, without delay between them, and the test outputs and reports will be stored under the folders `target/<type_of_data>/bikewise`:

```properties
# CONFIGURATION PARAMETERS

# Test case generator
generator=CBT

# Number of test cases to be generated per operation on each iteration
testsperoperation=5

# OAS specification
oas.path=src/test/resources/Bikewise/swagger.yaml

# Test configuration file
conf.path=src/test/resources/Bikewise/fullConf.yaml

# Directory where the test cases will be generated  
test.target.dir=src/generation/java/bikewise

# Experiment name (for naming related folders and files)
experiment.name=bikewise

# Name of the test class to be generated
testclass.name=BikewiseTest

# Measure input coverage
coverage.input=true

# Measure output coverage
coverage.output=true

# Enable CSV statistics
stats.csv=true

# Maximum number of test cases to be generated
numtotaltestcases=60

# Optional delay between each iteration (in seconds)
delay=-1

# Ratio of faulty test cases to be generated (negative testing)
faulty.ratio=0.4

# CONFIGURATION SETTINGS FOR CONSTRAINT-BASED TESTING

# Ratio of faulty test cases to be generated due to broken dependencies.
faulty.dependency.ratio=0.5

# Number of test cases after which new test data will be loaded.
reloadinputdataevery=100

# Max number of data values for each parameter
inputdatamaxvalues=1000
```

5. **Run RESTest**. Edit [the following line of TestGenerationAndExecution](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/main/TestGenerationAndExecution.java#L36) to set the path to the RESTest configuration file. Then, run the [TestGenerationAndExecution](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/main/TestGenerationAndExecution.java) class, located under the `es.us.isa.restest.main` package.

````java
setEvaluationParameters("src/test/resources/Bikewise/bikewise.properties");
````

### Generated test cases and test reports

RESTest generates REST Assured test cases like the following one:

```java
@Test
public void test_s4p3ksipllk1_GETversionincidentsformat() {
	String testResultId = "test_s4p3ksipllk1_GETversionincidentsformat";

	nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
	csvFilter.setTestResultId(testResultId);
	statusCode5XXFilter.setTestResultId(testResultId);
	nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
	validationFilter.setTestResultId(testResultId);

	try {
		Response response = RestAssured
		.given()
			.log().all()
			.queryParam("per_page", "67")
			.queryParam("incident_type", "crash")
			.queryParam("apikey_2", "ghi")
			.queryParam("occurred_before", "76")
			.queryParam("occurred_after", "8")
			.queryParam("page", "12")
			.queryParam("apikey_1", "abc")
			.filter(allureFilter)
			.filter(statusCode5XXFilter)
			.filter(nominalOrFaultyTestCaseFilter)
			.filter(validationFilter)
			.filter(csvFilter)
		.when()
			.get("/v2/incidents");

		response.then().log().all();
		System.out.println("Test passed.");
	} catch (RuntimeException ex) {
		System.err.println(ex.getMessage());
		fail(ex.getMessage());
	}
}
```

This test case makes a GET request to the endpoint `/v2/incidents` with several query parameters. Then it asserts that:
  - The status code is not 500 or higher (server error).
  - The status code is in the range 2XX if the request is valid or 4XX if the request is faulty.
  - The response conforms to the OAS specification of the API.

Finally, test failures are collected and they can be easily spotted and analyzed in a user-friendly GUI, built with [Allure](http://allure.qatools.ru/). To do so, open the file `target/allure-reports/bikewise/index.html` in your browser:

![Allure](docs/Allure.png)

## Running RESTest as a JAR
Instead of from an IDE like IntelliJ IDEA, you can also run RESTest as a fat JAR. You have two options:

### Option 1: Build RESTest from source

To package RESTest as a fat JAR file, run the following command in the root directory:

```
mvn clean install -DskipTests
```

Then, run the JAR file passing as argument the path to the properties file, for example:

```
java -jar target/restest-full.jar src/test/resources/Bikewise/bikewise.properties
```

### Option 2: Download the latest release

Go to the [releases page](https://github.com/isa-group/RESTest/releases) and download the latest one. RESTest releases consist of ZIP files which, once uncompressed, provide the directory structure and the necessary resources to run RESTest as a JAR. You can test the same example shown in the quickstart guide by running the following command:

```
java -jar restest.jar src/test/resources/Folder/api.properties
```

## License
RESTest is distributed under the [GNU General Public License v3.0](LICENSE).

RESTest includes Allure Framework &copy; 2019 Qameta Software OÜ. It is used under the the terms of the Apache 2.0 License, which can be obtained from http://www.apache.org/licenses/LICENSE-2.0.

RESTest also includes MiniZinc &copy; 2014-2020 Monash University and Data61, CSIRO. Its source code is available from [GitHub](https://github.com/MiniZinc/libminizinc) under the MPL 2.0 License, which can be obtained from https://www.mozilla.org/en-US/MPL/2.0.

### Icon credits
This README and some pages of the Wiki use icons provided by [Freepik](https://www.flaticon.com/authors/freepik), available at [Flaticon](https://www.flaticon.com/).
