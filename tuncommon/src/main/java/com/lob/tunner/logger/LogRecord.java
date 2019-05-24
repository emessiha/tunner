package com.lob.tunner.logger;

public abstract class LogRecord {
    public abstract void log(String message);
    public void log(String format, Object ...args) {
        log(String.format(format, args));
    }

    public abstract LogRecord exception(Throwable t);

    /**
     * Provide action description to corresponding log
     * @return
     */
    public abstract LogRecord action(String action);
    public abstract LogRecord action(String format, Object ...args);

    /**
     * Context shall be the last part of the log, it will implicitly call record() method
     */
    public abstract void context(String context);
    public abstract void context(String format, Object ...args);

    /**
     * @return
     */
    static LogRecord loggerRecorder(final ILogger logger, final int level) {
        return new LogRecord() {
            String action = null;
            String context = null;
            Throwable throwable = null;

            @Override
            public void log(String msg) {
                if(this.action != null) {
                    if(throwable != null) {
                        logger.notice(level, msg, action, context, throwable);
                    }
                    else {
                        logger.notice(level, msg, action, context);
                    }
                }
                else {
                    if(throwable != null) {
                        logger.message(level, msg, context, throwable);
                    }
                    else {
                        logger.message(level, msg, context);
                    }
                }

                //
                // So it can be reused if want!!!
                action = null;
                context = null;
                throwable = null;
            }

            @Override
            public LogRecord exception(Throwable t) {
                throwable = t;
                return this;
            }

            @Override
            public LogRecord action(String str) {
                action = str;
                return this;
            }

            @Override
            public LogRecord action(String format, Object... args) {
                action = String.format(format, args);
                return this;
            }

            @Override
            public void context(String str) {
                context = str;
            }

            @Override
            public void context(String format, Object... args) {
                context = String.format(format, args);
            }
        };
    }

    /**
     * Dummy recorder without generating anything ...
     *
     * @return
     */
    static LogRecord _dummy = new LogRecord() {
        @Override
        public void log(String message) { }

        @Override
        public LogRecord exception(Throwable t) { return this; }

        @Override
        public LogRecord action(String str) {
            return this;
        }

        @Override
        public LogRecord action(String format, Object... args) {
            return this;
        }

        @Override
        public void context(String str) {
            // do nothing
        }

        @Override
        public void context(String format, Object... args) {
            // do nothing
        }
    };

    static LogRecord dummyRecorder() {
        return _dummy;
    }
}
