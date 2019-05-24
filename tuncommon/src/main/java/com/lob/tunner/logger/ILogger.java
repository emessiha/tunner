package com.lob.tunner.logger;

public abstract class ILogger {
    public static final int TRACE = 100;
    public static final int DEBUG = 200;
    public static final int INFO = 300;
    public static final int WARN = 400;
    public static final int ERROR = 500;
    public static final int FATAL = 600;

    abstract boolean enable(int type);

    abstract ILogger message(int level, String msg, String context);
    abstract ILogger message(int level, String msg, String context, Throwable e);

    abstract ILogger notice(int level, String cause, String action, String context);
    abstract ILogger notice(int level, String cause, String action, String context, Throwable e);
}
