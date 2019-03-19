# Shibboleth API - Uni Passau
<p align="center">
  <a href="https://lgtm.com/projects/g/ThexXTURBOXx/shib-uni-passau/alerts/"><img alt="Total alerts" src="https://img.shields.io/lgtm/alerts/g/ThexXTURBOXx/shib-uni-passau.svg?logo=lgtm&logoWidth=18"/></a>
  <a href="https://lgtm.com/projects/g/ThexXTURBOXx/shib-uni-passau/context:java"><img alt="Language grade: Java" src="https://img.shields.io/lgtm/grade/java/g/ThexXTURBOXx/shib-uni-passau.svg?logo=lgtm&logoWidth=18"/></a>
  <a href="https://travis-ci.com/ThexXTURBOXx/shib-uni-passau"><img src="https://travis-ci.com/ThexXTURBOXx/shib-uni-passau.svg?branch=master"></a>
  <a href="https://maven-badges.herokuapp.com/maven-central/de.femtopedia.studip/shib-uni-passau"><img src="https://maven-badges.herokuapp.com/maven-central/de.femtopedia.studip/shib-uni-passau/badge.svg"></a>
  <a href="https://github.com/ThexXTURBOXx/shib-uni-passau/releases"><img src="https://img.shields.io/github/release/thexxturboxx/shib-uni-passau.svg"></a>
</p>
This is a simple library for Java handling the Shibboleth Login Authentication for the University of Passau.

## Sample Project
There are [this repository](https://github.com/ThexXTURBOXx/studip-uni-passau) and [this repository](https://github.com/ThexXTURBOXx/studip-app-uni-passau) available, including source code.<br>
And of course its [release APK](http://femtopedia.de/studip/index.php).

## Including as dependency (Gradle)
Add the following snippet to your **build.gradle** and change the version number:
```Gradle
repositories {
    maven {
        jcenter()
    }
}
dependencies {
    implementation 'de.femtopedia.studip:shib-uni-passau:...'
}
```

## Including as dependency (Maven)
Add the following snippet to your **pom.xml** and change the version number:
```xml
<dependencies>
    <dependency>
        <groupId>de.femtopedia.studip</groupId>
        <artifactId>shib-uni-passau</artifactId>
        <version>...</version>
    </dependency>
</dependencies>
```

## Older builds
Older (unsupported) builds are available in my Maven repo here: [http://femtopedia.de/maven](http://femtopedia.de/maven)

## Basic Usage (OAuth)
```Java
//Instantiate the Client
OAuthClient client = new OAuthClient();
try {
    //Set OAuth Credentials
    client.setupOAuth("CONSUMER_KEY", "CONSUMER_SECRET");
    //Print Authorization Url
    System.out.println(client.getAuthorizationUrl("callback_scheme://callback_url"));
    //Wait for user to input Verification Code
    client.verifyAccess(new Scanner(System.in).nextLine());
    //Get an example response from StudIP'S API
    CustomAccessHttpResponse response = client.get("https://studip.uni-passau.de/studip/api.php/user");
    try {
        //Print every line of the response's body content
        for (String line : response.readLines()) {
            System.out.println(line);
        }
    } finally {
        //Close the response
        response.close();
    }
} catch (IOException | IllegalAccessException | OAuthException e) {
    //Print errors
    e.printStackTrace();
}
```

## Basic Usage (Shibboleth, Unsupported)
```Java
//Instantiate the Client
ShibbolethClient client = new ShibbolethClient();
try {
    //Authenticate using your Login Credentials
    client.authenticate("USERNAME", "PASSWORD");
    //Get an example response from StudIP'S API
    CustomAccessHttpResponse response = client.get("https://studip.uni-passau.de/studip/api.php/user");
    try {
        //Print every line of the response's body content
        for (String line : response.readLines()) {
            System.out.println(line);
        }
    } finally {
        //Close the response
        response.close();
    }
} catch (IOException | IllegalAccessException | OAuthException e) {
    //Print errors
    e.printStackTrace();
}
```

## How it works
I tested the server for literally hours to work out, how the authentication works (just using backwards engineering, Firefox's Network Debugging feature and Postman).<br>
The library uses the OkHttp3-Client, so it's compatible with Android SDK >= 21 and - hopefully for the most part - every platform.
