package com.lob.tunner.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DbQuery extends DbOperator {
    private final String _sql;
    private final Object[] _params;
    public DbQuery(String sql, Object ...params) {
        _sql = sql;
        _params = params;
    }

    public  <T> List<T> queryList(Connection conn, RecordLoader<T> rLoader) throws SQLException  {
        final String sql = _sql;

        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        try {
            _bindParameters(pstmt, _params);
            rs = pstmt.executeQuery();

            ArrayList<T> r = new ArrayList<>();
            while (rs.next()) {
                r.add(rLoader.load(rs));
            }

            return r;
        }
        finally {
            DaoUtils.closeQuietly(rs, pstmt);
        }
    }
}
