package com.lob.tunner.client;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.common.Config;
import com.lob.tunner.logger.AutoLog;
import com.lob.tunner.logger.HexDump;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.SSHPacket;
import net.schmizz.sshj.connection.channel.direct.AbstractDirectChannel;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represtents one forwarded connection to remote tunnel server.
 *
 * Remote tunnel server will by default listen on 818 port
 */
public class Tunnel {
    private static final int _DEFAULT_PORT = 22;
    private static final int _FORWARD_PORT = 8080;

    private final ByteBuffer _padding = ByteBuffer.allocate(8);
    private final ByteBuffer _header = ByteBuffer.allocate(8);
    private final ByteBuffer _headerOut = ByteBuffer.allocate(8);
    private final ConcurrentHashMap<Integer, Connection> _connections = new ConcurrentHashMap<>();


    private final LinkedList<Block> _blocks = new LinkedList<>();
    private final Lock _lock = new ReentrantLock();
    private final Condition _empty = _lock.newCondition();

    private volatile boolean _stop = false;
    private final SSHClient _client;
    private final Thread _reader;
    private final Thread _writer;

    private String _target = "localhost";
    private int _port = _DEFAULT_PORT;
    private int _forwardPort = _FORWARD_PORT;
    private AbstractDirectChannel _channel = null;

    /**
     * Create a new tunnel
     */
    public Tunnel() {
        _client = new SSHClient();
        _reader = new Thread(this::_readerMain);
        _writer = new Thread(this::_writerMain);
    }

    public void multiplex(Connection conn) throws IOException {
        _connections.put(conn.getID(), conn);
    }

    public void remove(int connId) {
        _connections.remove(connId);
    }

    public boolean overloaded() {
        // too many connections on this!!
        return _connections.size() > 10;
    }

    public void start(String target, int forwardPort) throws IOException {
        start(target, _DEFAULT_PORT, forwardPort);
    }

    /**
     * Start the tunnel
     * @param target
     * @param port
     * @param forwardPort
     * @throws IOException
     */
    public void start(String target, int port, int forwardPort) throws IOException {
        _target = target;
        _port = port;
        _forwardPort = forwardPort;

        AutoLog.INFO.log("Try starting new tunnel to %s:%d and forwarding to %d ", target, port, forwardPort);
        _writer.start();
    }

    public void write(Block block) {
        _lock.lock();
        try {
            if(_blocks.isEmpty()) {
                _empty.signal();
            }

            _blocks.add(block);
        }
        finally {
            _lock.unlock();
        }
    }

    private void _connect() throws IOException {
        _client.addHostKeyVerifier(new PromiscuousVerifier());

        _client.loadKnownHosts();
        _client.connect(_target, _port);

        String user = Config.getServerUser();
        if(user == null || user.isEmpty()) {
            user = System.getProperty("user.name");
        }

        try {
            AutoLog.DEBUG.log("Try authenticate using system user - " + user);

            String key = Config.getServerKey();
            if(key != null && !key.isEmpty()) {
                OpenSSHKeyFile keyFile;
                keyFile = new OpenSSHKeyFile();
                keyFile.init(new File(key), new PasswordFinder() {
                    @Override
                    public char[] reqPassword(Resource<?> resource) {
                        final String pass = Config.getServerPass();
                        return pass.toCharArray();
                    }

                    @Override
                    public boolean shouldRetry(Resource<?> resource) {
                        return false;
                    }
                });
                _client.authPublickey(user, keyFile);
            }
            else {
                _client.authPassword(user, Config.getServerPass());
            }

            AutoLog.INFO.log("Tunnel established ...");
        }
        catch(UserAuthException uae) {
            AutoLog.ERROR.exception(uae).log("Cannot authenticate using default user - " + user);
            throw new IOException("Invalid username / password!");
        }

        /**
         * todo: we always try to forward to a port on remote "local" host
         */
        final String remoteHost = "127.0.0.1";
        final LocalPortForwarder.Parameters params = new LocalPortForwarder.Parameters(
                Config.getListenAddress(), Config.getListenPort(), remoteHost, _forwardPort
        );

        /**
         * Create a direct channel
         */
        _channel = new DirectTCPIPChannel(_client.getConnection(), params);
        _channel.open();
    }

    private Block _readBlock(ReadableByteChannel channel) throws IOException {
        while (_header.hasRemaining()) {
            channel.read(_header);
        }

        AutoLog.INFO.log("Read header - %s", HexDump.dumpHexString(_header.array()));

        _header.rewind();
        short typeSeq = _header.getShort();
        short len = _header.getShort();
        int conn = _header.getInt();

        // prepare for next block!!!!
        _header.clear();


        if(typeSeq == 0 && len == 0 && conn == 0) {
            // all 0 header!!! Ignore
            return null;
        }


        if(len == 0) {
            return new Block(conn, typeSeq);
        }
        else {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            while (buffer.hasRemaining()) {
                channel.read(buffer);
            }

            AutoLog.INFO.log("Read data - %s", HexDump.dumpHexString(buffer.array()));
            // If there are padding, let's get the padding
            int padding = len % 8;
            if (padding > 0) {
                _padding.rewind();
                _padding.limit(8 - padding);

                while(_padding.hasRemaining()) {
                    channel.read(_padding);
                }

                AutoLog.INFO.log("Read data - %s", HexDump.dumpHexString(_padding.array(), 0, 8 - padding));
            }

            // ready for writing out ...
            buffer.rewind();
            return new Block(conn, typeSeq, len, buffer);
        }
    }

    private void _readerMain() {
        final ReadableByteChannel channel = Channels.newChannel(_channel.getInputStream());

        // we are always 8 bytes!!!
        try {
            while (!_stop) {
                Block block = _readBlock(channel);

                if (block == null) {
                    continue;
                }

                int id = block.connection();

                if (block.type() == Block.BLOCK_DATA) {
                    AutoLog.INFO.log("Read in a new data block for connection %08x ...", id);
                    Connection conn = _connections.get(id);
                    if(conn == null) {
                        AutoLog.WARN.log("Found non-existing connection %08x", id);

                        write(new Block(id, BlockUtils.control(Block.CODE_ABORT)));

                        return;
                    }

                    conn.write(block.sequence(), block.data());
                }
                else {
                    TunnelManager.getInstance().handleControl(this, id, block.control(), block.data());
                }
            }
        }
        catch(IOException ioe) {
            AutoLog.ERROR.exception(ioe).log("Encounter exception while reading!");
            // todo: how to abort
        }
        finally {
            // ...
        }
    }

    private void _writeData(WritableByteChannel channel, ByteBuffer data) throws IOException {
        data.rewind();

        while(data.hasRemaining()) {
            channel.write(data);
        }
    }

    private void _writeNoise(WritableByteChannel channel) throws IOException {
        // ByteBuffer bb = ByteBuffer.allocate(1024);
        // Block block = new Block(0, BlockUtils.control(Block.CODE_ECHO), (short)1024, bb);
        Block block = new Block(0, BlockUtils.control(Block.CODE_ECHO));

        // TODO: let's add random length of payload here ...
        _writeBlock(channel, block);
    }

    private void _writeBlock(WritableByteChannel channel, Block block) throws IOException {
        short length = block.length();

        // 1. write header on channel
        _headerOut.clear();
        _headerOut.putShort(block.getTypeSeq());
        _headerOut.putShort(length);
        _headerOut.putInt(block.connection());

        _writeData(channel, _headerOut);

        // 2. if payload, write payload
        if(length > 0) {
            AutoLog.INFO.log("Written header - %s", HexDump.dumpHexString(_headerOut.array()));
            _writeData(channel, block.data());

            AutoLog.INFO.log("Written payload - %s", HexDump.dumpHexString(block.data().array()));

            // 3. if payload and payload size not multiple of 8, write padding bytes
            length %= 8;
            if(length > 0) {
                _writeData(channel, ByteBuffer.wrap(BlockUtils.PADDING, 0, (8 - length)));
                AutoLog.INFO.log("Written padding - %s", HexDump.dumpHexString(BlockUtils.PADDING, 0, 8 - length));
            }
        }
    }

    private void _writerMain() {
        try {
            AutoLog.INFO.log("Try starting a new tunnel by connecting to SSH server ...");
            _connect();

            AutoLog.INFO.log("Tunnel created, starting tunnel reader ...");
            _reader.start();

            OutputStream os = _channel.getOutputStream();
            WritableByteChannel channel = Channels.newChannel(os);

            while(!_stop) {
                // if there are data, write data, otherwise sleep and write background data ...
                _lock.lock();
                try {
                    if(_blocks.isEmpty()) {
                        if(!_empty.await(500, TimeUnit.MILLISECONDS)) {
                            // time out
                            if(false) {
                                // let's not flush for testing purpose ...
                                _writeNoise(channel);
                                os.flush();
                            }
                            continue;
                        }
                    }

                    Block block = _blocks.remove();
                    _writeBlock(channel, block);
                    os.flush();
                }
                finally {
                    _lock.unlock();
                }
            }

            AutoLog.INFO.log("Tunnel stopped");
        }
        catch(InterruptedException ie) {
            AutoLog.ERROR.exception(ie).log("Tunnel interrupted!");
        }
        catch(IOException ioe) {
            AutoLog.ERROR.exception(ioe).log("Cannot connect to remote SSH server!");
        }
        finally {
            if(_channel != null) {
                IOUtils.closeQuietly(_channel);
                _channel = null;
            }
        }
    }

    private static class DirectTCPIPChannel extends AbstractDirectChannel {
        final LocalPortForwarder.Parameters parameters;

        DirectTCPIPChannel(net.schmizz.sshj.connection.Connection conn, LocalPortForwarder.Parameters parameters) {
            super(conn, "direct-tcpip");
            this.parameters = parameters;
        }

        @Override
        protected SSHPacket buildOpenReq() {
            return (SSHPacket)((SSHPacket)((SSHPacket)((SSHPacket)super.buildOpenReq().putString(this.parameters.getRemoteHost())).putUInt32((long)this.parameters.getRemotePort())).putString(this.parameters.getLocalHost())).putUInt32((long)this.parameters.getLocalPort());
        }
    }
}
