package com.lob.tunner.server.db;

import jdk.vm.ci.services.internal.ReflectionAccessJDK;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class DbOperator {
    private static Method _method = null;
    protected PreparedStatement _create(Connection conn, String sql)  {
        try {
            if (_method == null) {
                synchronized (this) {
                    if (_method == null) {
                        _method = conn.getClass().getMethod("prepareStatement", String.class);
                    }
                }
            }

            return (PreparedStatement) _method.invoke(conn, sql);
        }
        catch(Exception e) {
            return null;
        }
    }

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
