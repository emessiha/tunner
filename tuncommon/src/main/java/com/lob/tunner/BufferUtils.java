package com.lob.tunner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;

public class BufferUtils {
    /**
     * Round size to 8 bytes. We padding non-8 bytes buffer to 8-byte buffer!!!
     * @param size
     * @return
     */
    public static int round(int size) {
        int round = size + 7;
        round >>= 3;
        round <<= 3;

        return round;
    }

    public static ByteBuffer toNioBuffer(ByteBuf buffer) {
        /*
        if (buffer.isDirect()) {
            return buffer.nioBuffer();
        }
        */
        final byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return ByteBuffer.wrap(bytes);
    }

    public static ByteBuf fromNioBuffer(ByteBuffer buffer) {
        ByteBuf data = PooledByteBufAllocator.DEFAULT.buffer();
        data.writeBytes(buffer);
        return data;
    }
}
