# Shibboleth API - Uni Passau
<p align="center">
  <a href="https://lgtm.com/projects/g/ThexXTURBOXx/shib-uni-passau/alerts/"><img alt="Total alerts" src="https://img.shields.io/lgtm/alerts/g/ThexXTURBOXx/shib-uni-passau.svg?logo=lgtm&logoWidth=18"/></a>
  <a href="https://lgtm.com/projects/g/ThexXTURBOXx/shib-uni-passau/context:java"><img alt="Language grade: Java" src="https://img.shields.io/lgtm/grade/java/g/ThexXTURBOXx/shib-uni-passau.svg?logo=lgtm&logoWidth=18"/></a>
  <a href="https://travis-ci.com/ThexXTURBOXx/shib-uni-passau"><img src="https://travis-ci.com/ThexXTURBOXx/shib-uni-passau.svg?branch=master"></a>
  <a href="http://femtopedia.de/studip/index.php"><img src="https://img.shields.io/website-up-down-green-red/http/www.femtopedia.de/index.php.svg?label=Repository"></a>
  <a href="https://github.com/ThexXTURBOXx/shib-uni-passau/releases"><img src="https://img.shields.io/github/release/thexxturboxx/shib-uni-passau.svg"></a>
</p>
This is a simple library for Java handling the Shibboleth Login Authentication for the University of Passau.

## Sample Project
There is a [Repository available](https://github.com/ThexXTURBOXx/studip-app-uni-passau), including source code.<br>
And of course its [release APK](http://femtopedia.de/studip/index.php).

## Including as dependency (Gradle)
Add the following snippet to your **build.gradle**:
```Gradle
repositories {
    maven {
        url "http://femtopedia.de/maven"
    }
}
dependencies {
    implementation 'de.femtopedia.studip:shib-uni-passau:1.2'
}
```

## Including as dependency (Maven)
Add the following snippet to your **pom.xml**:
```xml
<repositories>
    <repository>
        <id>Femtopedia</id>
        <url>http://femtopedia.de/maven</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>de.femtopedia.studip</groupId>
        <artifactId>shib-uni-passau</artifactId>
        <version>1.2</version>
    </dependency>
</dependencies>
```

## Basic Usage
```Java
//Instantiate the Client
ShibbolethClient client = new ShibbolethClient();
try {
    //Authenticate using your Login Credentials
    client.authenticate("USERNAME", "PASSWORD");
    //Get an example response from StudIP'S API
    ShibHttpResponse response = client.get("https://studip.uni-passau.de/studip/api.php/user");
    //Get the site's content
    InputStream stream = response.getResponse().getEntity().getContent();
    try {
        //Print every line
        for (String line : ShibbolethClient.readLines(stream))
            System.out.println(line);
        //Close the stream
        stream.close();
    } finally {
        //Close the response
        response.close();
    }
} catch (IOException | IllegalAccessException e) {
    //Print errors
    e.printStackTrace();
} finally {
    //Shutdown the Client after you are done.
    client.shutdown();
}
```

## How it works
I tested the server for literally hours to work out, how the authentication works (just using backwards engineering, Firefox's Network Debugging feature and Postman).<br>
The library uses the Google HTTP Client Wrapper for the Apache HTTP Client Library, so it's compatible with Android and - hopefully for the most part - every platform.
