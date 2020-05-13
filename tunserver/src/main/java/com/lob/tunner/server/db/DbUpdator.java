package com.lob.tunner.server.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DbUpdator {
    private final String _sql;

    public DbUpdator(String sql) {
        this._sql = sql;
    }

    public void update(Connection conn) throws SQLException  {
        Statement stmt = conn.createStatement();
        try {
            stmt.executeUpdate(_sql);
        }
        finally {
            DaoUtils.closeQuietly(stmt);
        }
    }
}
