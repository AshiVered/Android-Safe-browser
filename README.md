![image](https://raw.githubusercontent.com/AshiVered/support-israel-banner/main/assets/support-israel-banner.jpg)


# Android-Safe-browser
A simple GeckoView based browser with a whitelist of allowed URLs.
It downloads content using the Phone's internal downloader.

[![Get it on Google Play](assets/google-play-badge.png)](https://play.google.com/store/apps/details?id=aiv.ashivered.safebrowser)

## TODO list
* ~ Move to base on Mozzila GeckoView, no Android System WebView ~ DONE!
* Real UI (No HTML)
* ~ Load url list&catgories list from server, for example with json. ~ DONE!

## Change default URL 
Open `app/src/main/java/com/webview/app/MainActivity.java` and replace in line **111/112** with the URL for your website
```java
    private static final String URL_HOME             = "https://ashivered.github.io/SafeBrowserResources/index.html";
    private static final String URL_HOME_NONEWS      = "https://ashivered.github.io/SafeBrowserResources/index_nonews.html";
```
the default URL will be loaded when you open the app.

## Change URL of the Whitelist
Open `app/src/main/java/com/webview/app/MainActivity.java` and replace the URLs in line **109/110** with your URLs.

```java
    private static final String URL_WHITELIST_NEWS   = "https://ashivered.github.io/SafeBrowserResources/list_news.json";
    private static final String URL_WHITELIST_NONEWS = "https://ashivered.github.io/SafeBrowserResources/list_nonews.json";
```

## Donate me

This is an open-source project, developed with love and dedicated to its users. I invested a great deal of time in it without any compensation. If you would like to show your appreciation, you can donate using the following link.

https://ko-fi.com/ashivered


