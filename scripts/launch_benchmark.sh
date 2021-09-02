# YOU NEED TO RUN THIS SCRIPT FROM THE PARENT DIRECTORY, I.E., THE ONE CONTAINING THE SRC/ FOLDER AND THE RESTEST.JAR EXECUTABLE

for propsFile in `find src/test/resources/taas_eval -name props.properties`
do
	java -jar restest.jar $propsFile &>/dev/null &
	sleep 1m
done