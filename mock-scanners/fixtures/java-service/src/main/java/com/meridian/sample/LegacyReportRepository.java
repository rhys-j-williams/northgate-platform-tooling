package com.meridian.sample;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LegacyReportRepository {

    private final Connection connection;

    public LegacyReportRepository(Connection connection) {
        this.connection = connection;
    }

    public ResultSet findByCustomer(String customerId) throws SQLException {
        Statement st = connection.createStatement();
        return st.executeQuery("SELECT * FROM REPORT WHERE CUSTOMER_ID = '" + customerId + "'");
    }
}
