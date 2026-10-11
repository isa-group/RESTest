@echo off
rem Runs RESTest from a release, on Windows: the same job as bin\restest beside it, which says why
rem it is the one place that knows how RESTest starts.
rem
rem Written for cmd.exe's quirks, each of which has broken a launcher like this one somewhere:
rem - The number Java ends with is passed on as it is: RESTest's exit codes each mean something, and
rem   a launcher that turns every failure into 1 makes "could not test" read as "found faults".
rem - Every line runs on its own, with no ( ) blocks: inside a block, the error level and the paths
rem   are read before the block runs, and a path with a ) in it, such as C:\Program Files (x86), ends
rem   one.
rem - No delayed expansion: it removes every ! from the arguments, a key handed over with --auth
rem   included.
rem - The arguments go on exactly as they were typed, rather than rebuilt one by one, which splits
rem   them on = ; and , .
rem
rem When there is no Java it can use, it answers 127, as bin\restest does.
rem
rem Ctrl-C makes cmd.exe ask "Terminate batch job (Y/N)?" once Java has stopped, and whatever is
rem answered, the number is then cmd.exe's rather than RESTest's. bin\restest, run from Git Bash,
rem does not ask.
rem
rem No percent sign appears in these remarks: cmd.exe expands them even here.

setlocal

for %%i in ("%~dp0..") do set "RESTEST_HOME=%%~fi"

if not defined JAVA_HOME goto javaFromPath
set "RESTEST_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%RESTEST_JAVA%" goto javaFound
echo restest: JAVA_HOME is %JAVA_HOME%, and there is no Java in it. 1>&2
echo Point JAVA_HOME at a Java 21 or later, or unset it to use the java on the PATH. 1>&2
exit /b 127

:javaFromPath
set "RESTEST_JAVA=java"
where java >nul 2>nul
if not errorlevel 1 goto javaFound
echo restest: RESTest needs Java 21 or later, and there is no java on the PATH. 1>&2
echo Install one - Eclipse Temurin, https://adoptium.net/, is a free choice - or set JAVA_HOME to it. 1>&2
exit /b 127

:javaFound
rem Which Java it is, from the line of java -version that says so rather than from its first line,
rem which is a notice when JAVA_TOOL_OPTIONS is set. The call in front keeps cmd.exe from taking the
rem quotes off a Java under a folder with a space in its name. A java that cannot even say its version
rem gives no such line, and is no Java either.
set "RESTEST_JAVA_VERSION="
for /f "tokens=1-3" %%a in ('call "%RESTEST_JAVA%" -version 2^>^&1') do if "%%b"=="version" set "RESTEST_JAVA_VERSION=%%~c"
if not defined RESTEST_JAVA_VERSION goto javaUnknown
rem 21.0.12 and 26-ea give 21 and 26; the 1.8.0_402 of Java 8 gives 8.
for /f "tokens=1 delims=.-_+" %%a in ("%RESTEST_JAVA_VERSION%") do set "RESTEST_JAVA_MAJOR=%%a"
if "%RESTEST_JAVA_MAJOR%"=="1" for /f "tokens=2 delims=." %%a in ("%RESTEST_JAVA_VERSION%") do set "RESTEST_JAVA_MAJOR=%%a"
set /a RESTEST_JAVA_MAJOR=RESTEST_JAVA_MAJOR 2>nul
if %RESTEST_JAVA_MAJOR% LSS 21 goto javaTooOld

rem The database driver loads a native library. Without this, Java 24 and later greet a first-time
rem user with four warnings before a single request has been sent.
"%RESTEST_JAVA%" --enable-native-access=ALL-UNNAMED -cp "%RESTEST_HOME%\lib\*" io.restest.cli.Restest %*
exit /b %ERRORLEVEL%

:javaUnknown
echo restest: %RESTEST_JAVA% does not say which Java it is, so RESTest cannot start on it. It said: 1>&2
"%RESTEST_JAVA%" -version 1>&2 2>&1
exit /b 127

:javaTooOld
echo restest: RESTest needs Java 21 or later, and %RESTEST_JAVA% is Java %RESTEST_JAVA_VERSION%. 1>&2
echo Set JAVA_HOME to a Java 21 or later. 1>&2
exit /b 127
