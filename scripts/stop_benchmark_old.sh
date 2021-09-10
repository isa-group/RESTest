ps -a | grep /bin/java | while read processLine
do
  kill -9 `echo $processLine | sed -E 's/([0-9]+).*/\1/'`
done