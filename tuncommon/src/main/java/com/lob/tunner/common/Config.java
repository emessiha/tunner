package com.lob.tunner.common;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Config {
    /**
     * Initialize configuration from arguments
     * @param args
     */
    public static void initialize(String[] args, boolean isClient) {
        ArgumentParser parser = ArgumentParsers.newFor("TunClient").build()
                .defaultHelp(true)
                .description("Tunnel client options");

        parser.addArgument("-i", "--interface").setDefault("127.0.0.1")
                .help("Default local interface to listen on, default to 127.0.0.1");

        parser.addArgument("-l", "--listen").setDefault(8080)
                .help("Default local port to listen on, default to 8080");

        if(isClient) {
            parser.addArgument("-u", "--user")
                    .help("User name to connect to remote server");

            parser.addArgument("-k", "--key")
                    .help("Key file to use to connect to remote server");

            parser.addArgument("-p", "--password")
                    .help("Password to use to connect to remote server");

            parser.addArgument("-r", "--remotePort").setDefault(22)
                    .help("Remote server port to connect, default to 22");
            parser.addArgument("-f", "--forwardPort").setDefault(8080)
                    .help("Remote server port to tunnel to, default to 8080");

            parser.addArgument("server").nargs(1).required(true)
                    .help("Remote server address connect to for tunneling");
        }
        else {
            parser.addArgument("-m", "--mode").setDefault("prod")
                    .help("Mode running the server, could be 'test' or 'prod'");

            parser.addArgument("-s", "--server").setDefault("127.0.0.1")
                    .help("Remote proxy server address to forward tunneling connection, default to 127.0.0.1");

            parser.addArgument("-f", "--forwardPort").setDefault(8888)
                    .help("Remote proxy port to forward tunneling connection to, default to 8888");
        }

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        _localAddress = ns.getString("interface");
        _localPort = ns.getInt("listen");

        if(isClient) {
            _serverAddress = ns.getList("server").get(0).toString();
            _serverPort = ns.getInt("remotePort");
            _forwardPort = ns.getInt("forwardPort");

            _serverUser = ns.getString("user");
            _serverKey = ns.getString("key");
            _serverPass = ns.getString("password");
        }
        else {
            _mode = ns.getString("mode");
            _proxyAddress = ns.getString("server");
            _proxyPort = ns.getInt("forwardPort");
        }
    }

    private static String _serverAddress = "proxy";
    public static String getServerAddress() {
        return _serverAddress;
    }

    private static int _serverPort = 22;
    public static int getServerPort() {
        return _serverPort;
    }

    private static String _serverKey = "";
    public static String getServerKey() {
        return _serverKey;
    }

    private static String _serverUser = "";
    public static String getServerUser() {
        return _serverUser;
    }

    private static String _serverPass = "";
    public static String getServerPass() {
        return _serverPass;
    }

    private static int _forwardPort = 8080;
    public static int getForwardPort() {
        return _forwardPort;
    }

    private static String _localAddress = "127.0.0.1";
    public static String getLocalAddress() {
        return _localAddress;
    }

    private static int _localPort = 8080;
    public static int getLocalPort() {
        return _localPort;
    }

    private static String _proxyAddress = "127.0.0.1";
    public static String getProxyAddress() {
        return _proxyAddress;
    }

    private static int _proxyPort = 8888;
    public static int getProxyPort() {
        return _proxyPort;
    }

    private static String _mode = "prod";
    public static boolean isTestMode() {
        return "test".equalsIgnoreCase(_mode);
    }
}

