# RESTest
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://travis-ci.com/isa-group/RESTest.svg?branch=develop)](https://travis-ci.com/isa-group/RESTest)

RESTest is a framework for automated black-box testing of RESTful web APIs. It follows a model-based approach, where test cases are automatically derived from the OpenAPI Specification (OAS) of the API under test. No access to the source code is required, which makes it possible to test APIs written in any programming language, running in local or remote servers.

## RESTest Wiki
In this page you can find a brief description of how RESTest works and an illustrating example. If you want to read the full documentation, please visit the [Wiki](https://github.com/isa-group/RESTest/wiki). 

## How does it work?
The figure below shows how RESTest works. It takes as input the OAS specification of the API under test, considered the *system model*. The specification can optionally describe inter-parameter dependencies using the IDL4OAS extension (see examples [here](https://github.com/isa-group/IDLReasoner/blob/master/src/test/resources/OAS_example.yaml#L45) and [here](https://github.com/isa-group/IDLReasoner/tree/master/src/test/resources)). Then, a so-called *test model* is automatically generated from the system model including test-specific configuration data. The default test model can be manually enriched with fine-grained configuration details 
such as test data generation settings. Then, both the system and the test models are leveraged for the generation of abstract test cases following user-defined test case generation strategies such as random testing. In parallel, inter-parameter dependencies, if any, are fed into the tool [IDLReasoner](https://github.com/isa-group/IDLReasoner), providing support for their automated analysis during test case generation, for instance, to check whether an API call satisfies all the inter-parameter dependencies defined in the specification. Finally, abstract test cases are transformed into platform-specific executable test cases and they are executed.

![RESTest](docs/Approach8.png)

## Quickstart guide
You can clone the project by executing the following command from the command line:
````
git clone -b develop https://github.com/isa-group/RESTest.git
````
Then, open RESTest with your favourite IDE.
### Testing my first API
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
Next step is to create a properties file, which is the setup of the test. You must set the paths to the API documentation file and the test configuration file. We also suggest to specify the number of test cases you want to generate. Here's an example:
````properties
numtestcases=50                             # Number of test cases - this is not mandatory, but recommended; defaults to 10
oaispecpath=path/to/oas/api/spec/file.yaml  # Path to OAS specification file
confpath=path/to/test/conf/file.yaml        # Path to test configuration file
````
You need to edit the [the following line of IterativeExample](https://github.com/isa-group/RESTest/blob/master/src/main/java/es/us/isa/restest/main/IterativeExample.java#L62) to put the path to the properties file:
````java
setEvaluationParameters("path/to/the/properties/file.properties");
````
Finally, run the main method of IterativeExample. RESTest will do the rest.
### An example: Spotify
Spotify offers a RESTful API whose endpoints return JSON data. We will use the Spotify OpenAPI specification stored in the [APIs-guru repository](https://github.com/APIs-guru/openapi-directory/blob/master/APIs/spotify.com/v1/swagger.yaml). For this example we will save the yaml file into ``src/test/resources/Spotify`` - we suggest to save the OAS specification and the test configuration files of each API into ``src/test/resources/api_name`` -.\
\
We only want to test two operations: [get an album](https://developer.spotify.com/documentation/web-api/reference/albums/get-album/) and [get an artist](https://developer.spotify.com/console/get-artist/), so we will have to use a filter for each one. For each filter, we need to specify the path and the HTTP methods we want to test. We don't have to include the base path of the endpoint, as it is defined in the OAS specification file:
````java
//Path where OAS specification file is stored
String specPath = "src/test/resources/Spotify/swagger.yaml";

//Path where we want the test configuration file to be stored
String confPath = "src/test/resources/Spotify/testConf.yaml";

OpenAPISpecification spec = new OpenAPISpecification(specPath);

List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();

//We create the filter for the first operation: get an album
TestConfigurationFilter albumFilter = new TestConfigurationFilter();
albumFilter.setPath("/albums/{id}");     //This is the endpoint of the operation
albumFilter.addGetMethod();              //It is a GET operation, so we only add the GET method to the operation

//We create the filter for the second operation: get an artist
TestConfigurationFilter artistFilter = new TestConfigurationFilter();
artistFilter.setPath("/artists/{id}");      //This is the endpoint of the operation
artistFilter.addGetMethod();                                  //It is a GET operation, so we only add the GET method to the operation

//Adding the filters to the list
filters.add(albumFilter);
filters.add(artistFilter);

//Generating the test configuration file:
DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
gen.generate(confPath, filters);
````
This is the resulting test configuration file:
````yaml
---
auth:
  required: true
  queryParams: []
  headerParams: []
  apiKeysPath: null
  headersPath: null
testConfiguration:
  testPaths:
  - testPath: /albums/{id}
    operations:
    - operationId: <SET OPERATION ID>
      method: get
      testParameters:
      - name: id
        weight: null
        generator:
          type: RandomEnglishWord
          genParameters:
          - name: maxWords
            values:
            - 1
            objectValues: null
      - name: market
        weight: 0.5
        generator:
          type: RandomEnglishWord
          genParameters:
          - name: maxWords
            values:
            - 1
            objectValues: null
      paramDependencies: null
      expectedResponse: 200
  - testPath: /artists/{id}
    operations:
    - operationId: <SET OPERATION ID>
      method: get
      testParameters:
      - name: id
        weight: null
        generator:
          type: RandomEnglishWord
          genParameters:
          - name: maxWords
            values:
            - 1
            objectValues: null
      paramDependencies: null
      expectedResponse: 200
````
As Spotify requires an access token to make requests to its API, we need to connect Spotify Developers to our Spotify account to get a token. Spotify denotes that the access token goes into the ``Authorization`` field as a header parameter. We specify this configuration into the test configuration file:
````yaml
auth:
  required: true
  queryParams: []
  headerParams: 
  - name: Authorization
    value: Bearer <YOUR ACCESS TOKEN>
  apiKeysPath: null
  headersPath: null
...
````
You can also use a JSON file to store and use your tokens. We recommend it because you can store several tokens:
````json
{
     "Authorization": [
         "Bearer <YOUR ACCESS TOKEN 1>",
         "Bearer <YOUR ACCESS TOKEN 2>",
        
        "...",
        
         "Bearer <YOUR ACCESS TOKEN N>"
     ]
}
````
In this case, you must denote the path to this JSON file in the test configuration file. The file **must** be stored into ``src/main/resources/auth`` folder. The access token of Spotify goes into the header, so we will specify the path to the JSON file in the ``headersPath`` field.
````yaml
auth:
  required: true
  queryParams: []
  headerParams: []
  apiKeysPath: null
  headersPath: path/to/tokens/file.json   #This is a relative path; the base path is src/main/resources/auth/
...
````
As you can see, the test configuration file can be modified in many ways. Take a look at our developers guide for more information.\
\
Now we have to create the properties file, defining the paths of the OAS specification file and the test configuration file. Then we are ready to test the Spotify API.
