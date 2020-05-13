package com.lob.tunner.server.db;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.List;

public class DbUpdator extends DbOperator {
    private final Connection _conn;
    private final String _sql;
    private final Object[] _params;

    public DbUpdator(Connection conn, String sql, Object... params) throws SQLException {
        _conn = conn;

        this._sql = sql;
        _params = params;
    }

    public int update() throws SQLException {
        final String sql = "foo bar foo";
        PreparedStatement stmt = _conn.prepareStatement(sql);
        try {
            _bindParameters(stmt, _params);

            return stmt.executeUpdate();
        }
        finally {
            DaoUtils.closeQuietly(stmt);
        }
    }
}

