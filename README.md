# RESTest
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.com/isa-group/RESTest.svg?branch=develop)](https://travis-ci.com/isa-group/RESTest)

RESTest is a model-based testing framework that provides randomly generated test cases of an API from its specification. It mainly supports RESTful APIs described with the OAS design language.

### How does it work?
RESTest creates a default test configuration file from the API specification. With this file, RESTest generates random test cases using data generators. Then, those test cases are transformed into JUnit test cases that make API requests - RESTest uses [REST Assured](https://github.com/rest-assured/rest-assured) to test and validate the API requests generated -. Lastly, the JUnit suite is executed and the API responses are used to compute statistics, such as the percentage of the successful tests, the failed tests classified by their types or the coverage of the tests.

## Quickstart guide
You can clone the project by executing the following command from the command line:
````
git clone -b develop https://github.com/isa-group/RESTest.git
````
Then, open RESTest with your favourite IDE.
###Testing my first API
First, you have to create a test configuration file. RESTest provides you a functionality that produces it from the API specification:
```java
String specPath = "path/to/oas/api/spec/file.yaml";
String confPath = "path/to/generate/testConf.yaml";
OpenAPISpecification spec = new OpenAPISpecification(specPath);

DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
gen.generate(confPath);
```
You can filter the generation of the test configuration file by path and method:
````java
List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
TestConfigurationFilter filter = new TestConfigurationFilter();
filter.setPath("path/you/want/to/test");    // null = All paths
filter.addAllMethods();

//Another method filters:
//filter.addGetMethod();
//filter.addPostMethod();
//filter.addPutMethod();
//filter.addDeleteMethod();

filters.add(filter);
gen.generate(confPath, filters);
````
We strongly recommend to modify the test configuration file generated as it is pretty simple. For example, you will need to add the authentication parameters to the file if the API requires an API key or an OAuth token. Our developers guide includes more information.\
\
Next step is to create a properties file, which is the setup of the test. You must set the number of tests to generate, the API documentation file and the test configuration file:
````properties
numtestcases=50
oaispecpath=path/to/oas/api/spec/file.yaml
confpath=path/to/test/conf/file.yaml
````
You need to edit the [the following line of IterativeExample](https://github.com/isa-group/RESTest/blob/develop/src/main/java/es/us/isa/restest/main/IterativeExample.java#L62) to put the path to the properties file:
````java
setEvaluationParameters("path/to/the/properties/file.properties");
````
Finally, run the main method of IterativeExample. RESTest will do the rest.
