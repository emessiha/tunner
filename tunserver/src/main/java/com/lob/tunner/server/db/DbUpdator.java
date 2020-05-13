package com.lob.tunner.server.db;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.List;

public class DbUpdator {
    private final Connection _conn;
    private final String _sql;
    private final PreparedStatement _stmt;

    public DbUpdator(Connection conn, String sql, Object ...params) throws SQLException {
        _conn = conn;

        this._sql = sql;
        _stmt = conn.prepareStatement(sql);
        _bindParameters(params);
    }

    public int update() throws SQLException  {
        try {
            return _stmt.executeUpdate(_sql);
        }
        finally {
            DaoUtils.closeQuietly(_stmt);
        }
    }

    private void _bindParameters(Object... params) throws SQLException {
        int idx = 1;
        for (Object param : params) {
            if (param instanceof String) {
                _stmt.setString(idx, (String)param);
            }
            else if (param instanceof Long) {
                _stmt.setLong(idx, (Long)param);
            }
            else if (param instanceof Integer) {
                _stmt.setInt(idx, (Integer)param);
            }
            else if (param instanceof Double) {
                _stmt.setDouble(idx, (Double)param);
            }
            else if (param instanceof Float) {
                _stmt.setFloat(idx, (Float)param);
            }
            else if (param instanceof Boolean) {
                boolean b = (Boolean)param;
                _stmt.setBoolean(idx, b);
            }
            else if (param instanceof byte[]) {
                _stmt.setBlob(idx, new ByteArrayInputStream((byte[])param));
            }
            else if (param instanceof List) {
                List list = (List) param;
                for (Object o : list) {
                    if (o instanceof String) { // param is List<String>
                        _stmt.setString(idx, (String) o);
                    }
                    else if (o instanceof Long) { // param is List<Long>
                        _stmt.setLong(idx, (Long) o);
                    }
                    idx++;
                }
                continue;
            }
            else {
                if (param == null) {
                    _stmt.setNull(idx, Types.NULL);
                } else {
                    _stmt.setString(idx, param.toString());
                }
            }

            idx++;
        }
    }
}
