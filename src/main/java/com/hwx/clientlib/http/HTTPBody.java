package com.hwx.clientlib.http;

/**
 * Created by ajain on 9/15/15.
 */
public class HTTPBody {

    String bodyText;

    public HTTPBody(){}

    public HTTPBody(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }
}
