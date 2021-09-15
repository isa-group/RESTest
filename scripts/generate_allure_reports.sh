#! /bin/bash

# YOU NEED TO RUN THIS SCRIPT FROM THE PARENT DIRECTORY, I.E., THE ONE CONTAINING THE SRC/ FOLDER AND THE RESTEST.JAR EXECUTABLE

allureCommand='unknownCommand'
if [[ "$OSTYPE" == "linux-gnu"* || "$OSTYPE" == "darwin"* || "$OSTYPE" == "freebsd"* ]]; then
  allureCommand='allure/bin/allure'
elif [[ "$OSTYPE" == "cygwin"* || "$OSTYPE" == "msys"* || "$OSTYPE" == "win32"* ]]; then
  allureCommand='allure/bin/allure.bat'
fi

for allureFolder in `ls target/allure-results | sed 's#/##'`
do
  cp src/main/resources/allure-categories.json target/allure-results/$allureFolder/categories.json
	$allureCommand generate -c target/allure-results/$allureFolder -o target/allure-reports/$allureFolder
	sleep 1m
done