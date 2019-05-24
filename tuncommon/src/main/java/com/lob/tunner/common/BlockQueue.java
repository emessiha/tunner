package com.lob.tunner.common;

import java.util.LinkedList;

public class BlockQueue {
    private final LinkedList<Block> _queue = new LinkedList<Block>();

    private volatile boolean _empty = true;

    public void queue(Block block) {
        synchronized (_queue) {
            _queue.add(block);

            if(_empty) {
                _empty = false;
            }
        }
    }

    public Block dequeue() {
        synchronized (_queue) {
            Block block = _queue.poll();
            if(block == null && !_empty) {
                _empty = true;
            }

            return block;
        }
    }

    public final boolean empty() {
        return _empty;
    }
}
