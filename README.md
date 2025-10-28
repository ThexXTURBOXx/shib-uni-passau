# Shibboleth API - Uni Passau
<p align="center">
  <a href="https://maven-badges.herokuapp.com/maven-central/de.femtopedia.studip/shib-uni-passau"><img src="https://maven-badges.herokuapp.com/maven-central/de.femtopedia.studip/shib-uni-passau/badge.svg"></a>
  <a href="https://github.com/ThexXTURBOXx/shib-uni-passau/releases"><img src="https://img.shields.io/github/release/thexxturboxx/shib-uni-passau.svg"></a>
</p>
This is a simple library for Java handling the Shibboleth Login Authentication for the University of Passau.

## Sample Project
There are [this repository](https://github.com/ThexXTURBOXx/studip-uni-passau) and [this repository](https://github.com/ThexXTURBOXx/studip-app-uni-passau) available, including source code.<br>
And of course its [release APK](http://femtopedia.de/studip/index.php).

## Including as dependency (Gradle)
Add the following snippet to your **build.gradle** and change the version number:
```groovy
repositories {
    mavenCentral()
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
```java
// Instantiate the Client
OAuthClient client = new OAuthClient();
try {
    // Set OAuth Credentials
    client.setupOAuth("CONSUMER_KEY", "CONSUMER_SECRET");
    // Print Authorization Url
    System.out.println(client.getAuthorizationUrl("callback_scheme://callback_url"));
    // Wait for user to input Verification Code
    client.verifyAccess(new Scanner(System.in).nextLine());
    // Get an example response from StudIP's API
    try (CustomAccessHttpResponse response = client.get("https://studip.uni-passau.de/studip/api.php/user")) {
        // Print every line of the response's body content
        for (String line : response.readLines()) {
            System.out.println(line);
        }
    }
    // Close the response
} catch (IOException | IllegalAccessException | OAuthException e) {
    // Print errors
    e.printStackTrace();
}
```

## Basic Usage (Shibboleth, Unsupported)
```java
// Instantiate the Client
ShibbolethClient client = new ShibbolethClient();
try {
    // Authenticate using your Login Credentials
    client.authenticate("USERNAME", "PASSWORD");
    // Get an example response from StudIP's API
    try (CustomAccessHttpResponse response = client.get("https://studip.uni-passau.de/studip/api.php/user")) {
        // Print every line of the response's body content
        for (String line : response.readLines()) {
            System.out.println(line);
        }
    }
    // Close the response
} catch (IOException | IllegalAccessException | OAuthException e) {
    // Print errors
    e.printStackTrace();
}
```

## How it works
I tested the server for literally hours to work out, how the authentication works (just using backwards engineering, Firefox's Network Debugging feature and Postman).<br>
The library uses the OkHttp3-Client, so it's compatible with Android SDK >= 21 and - hopefully for the most part - every platform.<br>
If you need compatibility with older Android SDKs, then check out older builds (<= 1.4.2), which use the Apache HTTP Client. But please be aware then, that those builds are highly outdated and not maintained anymore. Use at your own risk!
