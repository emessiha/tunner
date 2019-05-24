package com.lob.tunner;

public class BlockUtils {
    public static final byte[] PADDING = new byte[]{
            (byte)0xff,
            (byte)0xff,
            (byte)0xff,
            (byte)0xff,
            (byte)0xff,
            (byte)0xff,
            (byte)0xff,
            (byte)0xff
    };

    /**
     * Make a sequence by setting highest two bits to 01 (data block)
     * @param seq
     * @return
     */
    public static short sequence(short seq) {
        return (short)((short)0x4000 + (short)seq & 0x3FFF);
    }

    /**
     * Make a control code by setting highest two bits to 10 (control block)
     * @param code
     * @return
     */
    public static short control(short code) {
        return (short)((short)0x8000 + (short)code & 0x3FFF);
    }
}
