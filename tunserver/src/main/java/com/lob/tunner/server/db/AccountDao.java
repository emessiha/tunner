package com.lob.tunner.server.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Access controldb.accounts table
 *
 * CREATE TABLE accounts ( " +
 "  id BIGINT not null primary key,
 "  name VARCHAR(128) not null unique,
 "  properties TEXT(65535) not null - this is a JSON string
 " ) engine=innodb
 */
public class AccountDao {
    private final static String SQL_GETALL = "SELECT id, name, properties FROM accounts";
    private final static String SQL_INSERT = "INSERT INTO accounts (id, name, properties) VALUE (?, ?, ?)";
    private final static String SQL_DELETE = "DELETE FROM accounts WHERE id=?";
    private final static String SQL_UPDATE = "UPDATE accounts set properties=? WHERE id=?";
    private final static String SQL_GET_BY_NAME = "SELECT id, name, properties FROM accounts WHERE name=?";
    private final static String SQL_GET_BY_ID = "SELECT id, name, properties FROM accounts WHERE id=?";

    /*
    * createTable must have dbName parameter although we didn't use it
    * Because we will use reflect to call createTable when create db
    */
    public static void createTable(Connection conn, String dbName) throws SQLException {
        String sql =
                "CREATE TABLE IF NOT EXISTS accounts (\n" +
                        "         id BIGINT not null primary key,\n" +
                        "         name VARCHAR(128) not null unique,\n" +
                        "         properties JSON not null \n" +
                        "        ) engine=innodb ROW_FORMAT=DYNAMIC";

        DaoUtils.executeUpdate(conn, sql, dbName);
    }


    private final static RecordLoader<Account> _LOADER = new RecordLoader<Account>() {
        @Override
        public Account load(final ResultSet rs) throws SQLException {
            Account account = new Account();
            account.setId(rs.getLong(1));
            account.setName(rs.getString(2));
            account.setProperties(rs.getString(3));
            return account;
        }
    };

    public static List<Account> getAll(Connection conn, String coreDb) throws SQLException {
        return DaoUtils.getList(conn, SQL_GETALL, coreDb, _LOADER::load);
    }


    /**
     * Remove all accounts from the accounts table
     */
    public static void removeAll(Connection conn, String dbName) throws SQLException {
        DaoUtils.executeUpdate(conn, "DELETE FROM accounts", dbName);
    }

    public static void add(Connection conn, String dbName, long id, String name, String properties) throws SQLException {
        String strProperties = properties==null ? "{}" : properties.toString();

        DaoUtils.executeUpdate(conn, SQL_INSERT, dbName, id, name, strProperties);
    }

    /*
    public static void add(@NonNull Connection conn, @NonNull Account account) throws SQLException {
        DaoUtils.executeUpdate(conn, SQL_INSERT, Constants.COREDB, account.getId(), account.getName(), account.getProperties());
    }

    public static void update(@NonNull Connection conn, long id, @NonNull Account account) throws SQLException {
        DaoUtils.executeUpdate(conn, SQL_UPDATE, Constants.COREDB, account.getProperties(), id);
    }

    public static void delete(@NonNull Connection conn, long id) throws SQLException {
        DaoUtils.executeUpdate(conn, SQL_DELETE, Constants.COREDB, id);
    }


    public static Account get(@NonNull Connection conn, @NonNull String name) throws SQLException {
        return DaoUtils.get(conn, SQL_GET_BY_NAME, Constants.COREDB, _LOADER::load, name);
    }


    public static Account get(@NonNull Connection conn, long id) throws SQLException {
        Preconditions.checkArgument(id > 0);
        return DaoUtils.get(conn, SQL_GET_BY_ID, Constants.COREDB, _LOADER::load, id);
    }


    public static void updateStatus(@NonNull Connection conn,
                                    long acctId,
                                    String newStatus) throws SQLException {
        Preconditions.checkArgument(acctId > 0 &&
            !StringUtils.isEmpty(newStatus) &&
            Account.isValidStatus(newStatus));

        Account account = get(conn, acctId);
        if (account == null)
            throw new IllegalArgumentException("No such account - " + acctId);
        JSONObject jprops = new JSONObject(account.getProperties());
        jprops.put("status", newStatus);

        DaoUtils.executeUpdate(conn, SQL_UPDATE, Constants.COREDB, jprops.toString(), acctId);
    }
     */
}
