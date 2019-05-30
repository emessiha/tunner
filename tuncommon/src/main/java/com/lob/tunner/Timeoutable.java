package com.lob.tunner;

import com.lob.tunner.logger.AutoLog;

abstract public class Timeoutable {
    private long _lastWrite = System.currentTimeMillis();
    private long _lastRead = System.currentTimeMillis();

    public void updateRead() {
        _lastRead = System.currentTimeMillis();
    }

    public long lastRead() {
        return _lastRead;
    }

    public void updateWrite() {
        _lastWrite = System.currentTimeMillis();
    }

    public long lastWrite() {
        return _lastWrite;
    }



    abstract public void timeout(long now);
}
