package com.lob.tunner.common;

public class Config {
    /**
     * Initialize configuration from arguments
     * @param args
     */
    public static void initialize(String[] args) {

    }

    public static String getServerAddress() {
        return "proxy";
    }

    public static int getServerPort() {
        return 22;
    }

    public static String getLocalAddress() {
        return "localhost";
    }

    public static int getLocalPort() {
        return 8080;
    }

    public static String getProxyAddress() {
        return "localhost";
    }

    public static int getProxyPort() {
        return 8888;
    }
}

