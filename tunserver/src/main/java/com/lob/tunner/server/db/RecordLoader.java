package com.lob.tunner.server.db;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface RecordLoader<T> {
    T load(ResultSet rs) throws SQLException;
}
