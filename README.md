# Android-Safe-browser
A simple Webview based browser with a whitelist of allowed URLs.
It downloads content using the Phone's internal downloader.
[![google-play-badge.png](https://mitmachim.top/assets/uploads/files/1666811423893-google-play-badge.png)](https://play.google.com/store/apps/details?id=aiv.ashivered.book.safebrowser) 

## Change default URL 
Open `app/src/main/java/com/webview/app/MainActivity.java` and replace `https://ashivered.github.io/listofurls.html` on line **81** with the URL for your website
```java
mWebView.loadUrl("https://ashivered.github.io/listofurls.html");
```
the default URL will be loaded when you open the app.

## Change URLs in the Whitelist
Open `app/src/main/java/com/webview/app/MainActivity.java` and replace the URLs in line **85** with your URLs.

## Change to a Blacklist Browser (List of disallowed URLs)
Reverse the condition (in lines 87-95):
```java
@Override
public boolean shouldOverrideUrlLoading(WebView view, String url) {
    String host = Uri.parse(url).getHost();
    if (whiteHosts.contains(host)) {
        return false;
    } else {
        blockString();
        return true;
    }
```
to

```java
@Override
public boolean shouldOverrideUrlLoading(WebView view, String url) {
    String host = Uri.parse(url).getHost();
    if (whiteHosts.contains(host)) {
        blockString();
        return true;
    } else {
        return false;
    }
```


