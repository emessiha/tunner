package com.lob.tunner.server.db;

import java.sql.Connection;
import java.sql.SQLException;

public class DbUpdator {
    private final String _sql;

    public DbUpdator(String dbName, String sql) {
        this._sql = sql;
    }

    public void update(Connection conn) throws SQLException  {
        DaoUtils.executeUpdate(conn, _sql);
    }
}
