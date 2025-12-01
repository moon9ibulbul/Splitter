@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/gradle/gradle-wrapper/8.9/gradle-wrapper-8.9.jar

if not exist "%WRAPPER_JAR%" (
    echo gradle-wrapper.jar missing, downloading...
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'" || goto downloadFail
)
goto afterDownload

downloadFail:
echo Failed to download gradle-wrapper.jar
exit /b 1

afterDownload:
set JAVA_EXE=java.exe
if not "%JAVA_HOME%"=="" set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set DEFAULT_JVM_OPTS=-Xmx64m -Xms64m

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -Dorg.gradle.appname=Gradle -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
