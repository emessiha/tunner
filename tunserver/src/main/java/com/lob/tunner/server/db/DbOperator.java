package com.lob.tunner.server.db;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class DbOperator {
    protected void _bindParameters(PreparedStatement stmt, Object... params) throws SQLException {
        int idx = 1;
        for (Object param : params) {
            if (param instanceof String) {
                stmt.setString(idx, (String)param);
            }
            else if (param instanceof Long) {
                stmt.setLong(idx, (Long)param);
            }
            else if (param instanceof Integer) {
                stmt.setInt(idx, (Integer)param);
            }
            else if (param instanceof Double) {
                stmt.setDouble(idx, (Double)param);
            }
            else if (param instanceof Float) {
                stmt.setFloat(idx, (Float)param);
            }
            else if (param instanceof Boolean) {
                boolean b = (Boolean)param;
                stmt.setBoolean(idx, b);
            }
            else if (param instanceof byte[]) {
                stmt.setBlob(idx, new ByteArrayInputStream((byte[])param));
            }
            else if (param instanceof List) {
                List list = (List) param;
                for (Object o : list) {
                    if (o instanceof String) { // param is List<String>
                        stmt.setString(idx, (String) o);
                    }
                    else if (o instanceof Long) { // param is List<Long>
                        stmt.setLong(idx, (Long) o);
                    }
                    idx++;
                }
                continue;
            }
            else {
                if (param == null) {
                    stmt.setNull(idx, Types.NULL);
                } else {
                    stmt.setString(idx, param.toString());
                }
            }

            idx++;
        }
    }
}
