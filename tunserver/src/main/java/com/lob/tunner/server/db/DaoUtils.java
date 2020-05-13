package com.lob.tunner.server.db;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * An utility class implement DAO functions
 *
 * As we are multi-tenant system, we would ask user to always pass in dbName after SQL so we know
 * on which database we shall execute the SQL
 */
public class DaoUtils {
    public final static int ERROR_MYSQL_DUPLICATE_KEY = 1062;

    private static DataSource _connPool = null;

    public void setConnPool(DataSource connPool) {
        DaoUtils._connPool = connPool;
    }

    public static void waitForReady(long timeoutInMs) throws SQLException {
        long startEpochInMs = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - startEpochInMs >= timeoutInMs) {
                throw new SQLException("Timed out to wait for MySQL be ready - " + timeoutInMs);
            }

            Connection conn = null;
            try {
                conn = getConnection();
                Long upTimeInSeconds = _get(conn, "show global status like 'uptime'", rs -> rs.getLong(2));
                if (null != upTimeInSeconds) {
                    System.out.println("MySQL uptime - " + upTimeInSeconds);
                    break;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println("Init db connection failed, sleep and retry ...");
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException ie) {
                    // ignore
                }
            }
            finally {
                closeQuietly(conn);
            }
        }
    }

    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            }
            catch (SQLException ex) {}
        }
    }


    public static void rollbackQuietly(final Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit() && !conn.isReadOnly())
                    conn.rollback();
            }
            catch (SQLException e) {
                // silently swallow it
            }
        }
    }


    public static void closeQuietly(final ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            }
            catch (SQLException e) {
                // silently swallow it
            }
        }
    }


    public static void closeQuietly(final Connection conn, final ResultSet rs) {
        closeQuietly(rs);
        closeQuietly(conn);
    }


    public static void closeQuietly(final ResultSet rs, final Statement stmt) {
        closeQuietly(rs);
        closeQuietly(stmt);
    }


    public static void closeQuietly(final Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            }
            catch (SQLException e) {
                // silently swallow it
            }
        }
    }


    public static Connection getConnection() throws SQLException {
        // return _connPool.getConnection();

        // Use our managed version to get more insights
        return _connPool.getConnection();
    }

    public static Connection getConnection(boolean autoCommit) throws SQLException {
        Connection conn = getConnection();
        if (conn.getAutoCommit() != autoCommit)
            conn.setAutoCommit(autoCommit);
        return conn;
    }


    public static Connection getConnection(String driver,
                                           String url,
                                           String username,
                                           String password) throws SQLException, ClassNotFoundException {
        Class.forName(driver);
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Check if the given database exists
     */
    public static boolean dbExists(final Connection conn, final String dbName) throws SQLException {
        ResultSet rs = conn.getMetaData().getCatalogs();
        try {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name.equals(dbName))
                    return true;
            }

            return false;
        }
        finally {
            closeQuietly(rs);
        }
    }


    public static boolean tableExists(Connection conn, String dbName, String tableName) throws SQLException {
        String[] types = {"TABLE"};
        ResultSet rs = null;
        try {
            rs = conn.getMetaData().getTables(dbName, null, tableName, types);
            if(rs.next()) {
                return true;
            }
            else {
                return false;
            }
        }
        finally {
            closeQuietly(rs);
        }
    }

    public static boolean columnExists(Connection conn, String dbName, String tableName, String column) throws SQLException {
        ResultSet rs = null;
        try {
            rs = conn.getMetaData().getColumns(dbName, null, tableName, column);
            return rs.next();
        }
        finally {
            closeQuietly(rs);
        }
    }


    public static void executeUpdate(final Connection conn, final String sqlQuery, final String dbName) throws SQLException {
        conn.setCatalog(dbName);

        Statement stmt = conn.createStatement();
        try {
            stmt.executeUpdate(sqlQuery);
        }
        finally {
            closeQuietly(stmt);
        }
    }


    /**
     * Create a database using innodb as the engine, and UTF-8 as charset and collation
     * // TODO Create DB test
     */
    public static void createDb(final Connection conn,
                                final String dbName) throws SQLException {
        // create the control db
        System.out.println("Create a database - " + dbName);
        String sql = "CREATE DATABASE IF NOT EXISTS ? DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci";
        executeUpdate(conn, sql, null, dbName);
    }


    public static void dropDb(Connection conn, String dbName) throws SQLException {
        System.out.println("Drop a database " + dbName);
        String sql = "DROP DATABASE IF EXISTS " + dbName;
        executeUpdate(conn, sql, null);
    }


    public static void dropDbQuietly(Connection conn, String dbName) {
        try {
            dropDb(conn, dbName);
        }
        catch (SQLException ignore) {
            // dumb
        }
    }

    public static void dropTable(Connection conn,
                                 String dbName,
                                 String tableName) throws SQLException {
        System.out.println("Drop a table " + tableName + " from database " + dbName);
        String sql = "DROP TABLE " + tableName;
        executeUpdate(conn, sql, dbName);
    }


    public static void dropTableQuietly(Connection conn,
                                        String dbName,
                                        String tableName) throws SQLException {
        try {
            dropTable(conn, dbName, tableName);
        }
        catch (Exception e) {
            // ignore
        }
    }

    public static <T> List<T> getList(Connection conn,
                                      String sql,
                                      String dbName,
                                      RecordLoader<T> rLoader,
                                      Object... params) throws SQLException {
        conn.setCatalog(dbName);
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        try {
            _bindParameters(pstmt, params);
            rs = pstmt.executeQuery();
            ArrayList<T> r = new ArrayList<>();
            while (rs.next()) {
                r.add(rLoader.load(rs));
            }

            return r;
        }
        finally {
            closeQuietly(rs, pstmt);
        }
    }

    public static <T> T get(Connection conn,
                            String sql,
                            String dbName,
                            RecordLoader<T> rLoader,
                            Object... params) throws SQLException {
        conn.setCatalog(dbName);
        return _get(conn, sql, rLoader, params);
    }

    private static <T> T _get(Connection conn,
                              String sql,
                              RecordLoader<T> rLoader,
                              Object...params) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        try {
            _bindParameters(pstmt, params);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rLoader.load(rs);
            }

            return null;
        }
        finally {
            closeQuietly(rs, pstmt);
        }
    }

    private final static RecordLoader<Integer> _INT_LOADER = new RecordLoader<Integer>() {
        @Override
        public Integer load(final ResultSet rs) throws SQLException {
            return rs.getInt(1);
        }
    };


    public static Integer getInt(Connection conn,
                                 String sql,
                                 String dbName,
                                 Object... params) throws SQLException {
        return get(conn, sql, dbName, _INT_LOADER::load, params);
    }


    public static List<Integer> getIntList(Connection conn,
                                           String sql,
                                           String dbName,
                                           Object... params) throws SQLException {
        return getList(conn, sql, dbName, _INT_LOADER::load, params);
    }


    private final static RecordLoader<Long> _LONG_LOADER = new RecordLoader<Long>() {
        @Override
        public Long load(final ResultSet rs) throws SQLException {
            return rs.getLong(1);
        }
    };


    public static Long getLong(Connection conn,
                               String sql,
                               String dbName,
                               Object... params) throws SQLException {
        conn.setCatalog(dbName);
        return get(conn, sql, dbName, _LONG_LOADER::load, params);
    }


    public static List<Long> getLongList(Connection conn,
                                         String sql,
                                         String dbName,
                                         Object... params) throws SQLException {
        conn.setCatalog(dbName);
        return getList(conn, sql, dbName, _LONG_LOADER::load, params);
    }


    private final static RecordLoader<String> _STRING_LOADER = new RecordLoader<String>() {
        @Override
        public String load(final ResultSet rs) throws SQLException {
            return rs.getString(1);
        }
    };

    public static String getString(Connection conn, String sql, String dbName, Object... params) throws SQLException {
        return get(conn, sql, dbName, _STRING_LOADER::load, params);
    }


    public static <K, V> Map<K, V> getMap(Connection conn,
                                          String sql,
                                          String dbName,
                                          RecordLoader<Map.Entry<K, V>> loader,
                                          Object... params)
            throws SQLException {
        conn.setCatalog(dbName);

        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        try {
            _bindParameters(pstmt, params);
            rs = pstmt.executeQuery();
            Map<K, V> r = new HashMap<>();
            while (rs.next()) {
                Map.Entry<K, V> p = loader.load(rs);
                r.put(p.getKey(), p.getValue());
            }

            return r;
        }
        finally {
            closeQuietly(rs, pstmt);
        }
    }



    private final static RecordLoader<Map.Entry<Long, Long>> _LONG_LONG_LOADER = new RecordLoader<Map.Entry<Long, Long>>() {
        @Override
        public Map.Entry<Long, Long> load(final ResultSet rs) throws SQLException {
            return new KeyValueHolder<Long, Long>(rs.getLong(1), rs.getLong(2));
        }
    };

    public static Map<Long, Long> getMapOfLongToLong(Connection conn,
                                                     String sql,
                                                     String dbName,
                                                     Object... params) throws SQLException {
        return getMap(conn, sql, dbName, _LONG_LONG_LOADER::load, params);
    }


    public static int executeUpdate(Connection conn,
                                    String sql,
                                    String dbName,
                                    Object... params) throws SQLException {
        if(dbName == null || dbName.isEmpty()) {
            conn.setCatalog(dbName);
        }

        PreparedStatement pstmt = conn.prepareStatement(sql);
        try {
            _bindParameters(pstmt, params);
            return pstmt.executeUpdate();
        }
        finally {
            closeQuietly(pstmt);
        }
    }


    public static long executeUpdateWithLastInsertId(Connection conn,
                                                     String sql,
                                                     String dbName,
                                                     Object... params) throws SQLException {
        conn.setCatalog(dbName);
        PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ResultSet keys = null;
        try {
            _bindParameters(pstmt, params);
            pstmt.executeUpdate();

            keys = pstmt.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
        finally {
            closeQuietly(keys, pstmt);
        }
    }

    private static void _bindParameters(PreparedStatement pstmt,
                                        Object... params) throws SQLException {
        int idx = 1;
        for (Object param : params) {
            if (param instanceof String) {
                pstmt.setString(idx, (String)param);
            }
            else if (param instanceof Long) {
                pstmt.setLong(idx, (Long)param);
            }
            else if (param instanceof Integer) {
                pstmt.setInt(idx, (Integer)param);
            }
            else if (param instanceof Double) {
                pstmt.setDouble(idx, (Double)param);
            }
            else if (param instanceof Float) {
                pstmt.setFloat(idx, (Float)param);
            }
            else if (param instanceof Boolean) {
                boolean b = (Boolean)param;
                pstmt.setBoolean(idx, b);
            }
            else if (param instanceof byte[]) {
                pstmt.setBlob(idx, new ByteArrayInputStream((byte[])param));
            }
            else if (param instanceof List) {
                List list = (List) param;
                for (Object o : list) {
                    if (o instanceof String) { // param is List<String>
                        pstmt.setString(idx, (String) o);
                    }
                    else if (o instanceof Long) { // param is List<Long>
                        pstmt.setLong(idx, (Long) o);
                    }
                    idx++;
                }
                continue;
            }
            else {
              if (param == null) {
                  pstmt.setNull(idx, Types.NULL);
              } else {
                  pstmt.setString(idx, param.toString());
              }
            }

            idx++;
        }
    }


    public static byte[] readBlobAsByteArray(ResultSet rs,
                                             int columnIdx) throws SQLException {
        try {
            InputStream s = rs.getBinaryStream(columnIdx);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int nread = 0;
            while ((nread = s.read(buffer)) > 0) {
                bos.write(buffer, 0, nread);
            }

            return bos.toByteArray();
        }
        catch (IOException e) {
            throw new SQLException(e);
        }
    }


    public static boolean getBoolean(ResultSet rs,
                                     int columnIdx) throws SQLException {
        return rs.getString(columnIdx).equalsIgnoreCase("y");
    }


    public static List<String> getTableList(Connection conn,
                                            String dbName,
                                            String tableNamePattern) throws SQLException {
        String[] types = {"TABLE"};
        List<String> tables = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = conn.getMetaData().getTables(dbName, null, tableNamePattern, types);
            while (rs.next()) {
                tables.add(rs.getString(3));
            }
            return tables;
        }
        finally {
            closeQuietly(rs);
        }
    }
}