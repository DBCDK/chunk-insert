/*
 * Copyright (C) 2018 DBC A/S (http://dbc.dk/)
 *
 * This is part of queue-all
 *
 * queue-all is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * queue-all is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.inserts;

import dk.dbc.ReThrowException;
import dk.dbc.ExitException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Morten Bøgeskov (mb@dbc.dk)
 */
public class ChunkInsert {

    private static final Logger log = LoggerFactory.getLogger(ChunkInsert.class);

    private static final Pattern POSTGRES_URL_REGEX = Pattern.compile("(?:postgres(?:ql)?://)?(?:([^:@]+)(?::([^@]*))@)?([^:/]+)(?::([1-9][0-9]*))?/(.+)");

    private static final Pattern SQL_PATTERN = Pattern.compile("\\s*(insert\\s+into\\s+[.0-9a-z_]+\\s*(?:\\(\\s*[.0-9a-z_]+(?:\\s*,\\s*[.0-9a-z_]+)*\\s*\\))\\s+)(select\\s+.*)", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface
    private interface ValueMapper {

        void map(PreparedStatement stmt, ResultSet resultSet);
    }

    /**
     * Process command line
     *
     * @param arguments the command line
     * @throws Exception If something fails
     */
    public void run(Arguments arguments) throws Exception {

        String sql = arguments.getSql();
        int commit = arguments.getCommit();
        Matcher matcher = SQL_PATTERN.matcher(sql);
        if (!matcher.matches())
            throw arguments.usage("`" + sql + "' is not a valid sql statement for this command");
        String insert = matcher.group(1);
        String select = matcher.group(2);
        log.debug("select = {}", select);

        DataSource dataSource = makeDataSource(arguments);
        try (Connection connection = dataSource.getConnection() ;
             Statement stmt = connection.createStatement() ;
             ResultSet resultSet = stmt.executeQuery(select)) {
            connection.setAutoCommit(false);
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            String insertStmt = makeInsert(insert, columnCount);
            List<ValueMapper> mapperList = makeMapperList(metaData);
            log.debug("insert = {}", insertStmt);
            try (PreparedStatement pstmt = connection.prepareStatement(insertStmt)) {
                int row = 0;
                while (resultSet.next()) {
                    mapperList.forEach(m -> m.map(pstmt, resultSet));
                    pstmt.execute();
                    if (++row % commit == 0) {
                        log.info("Row: {} - committing", row);
                        connection.commit();
                    }
                }
                if (row % commit != 0) {
                    log.info("Row: {} - committing", row);
                    connection.commit();
                }
                log.info("Done");
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ex2) {
                    log.error("Error rolling back: {}", ex2.getMessage());
                    log.debug("Error rolling back: ", ex2);
                }
                throw ex;
            }
        }
    }

    /**
     * Generate result set to prepared statement mappers
     *
     * @param metaData Data about the select statement
     * @return list of mappers
     * @throws SQLException If data is invalid
     */
    private List<ValueMapper> makeMapperList(ResultSetMetaData metaData) throws SQLException {
        int columnCount = metaData.getColumnCount();
        ArrayList<ValueMapper> list = new ArrayList<>(columnCount);
        for (int i = 1 ; i <= columnCount ; i++) {
            final int column = i;
            String columnName = metaData.getColumnName(column);
            String columnClassName = metaData.getColumnClassName(column);
            log.debug("columnName = {}; columnClassName = {}", columnName, columnClassName);
            switch (columnClassName) {
                case "java.math.BigDecimal":
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        BigDecimal value = resultSet.getBigDecimal(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.DECIMAL);
                        else
                            stmt.setBigDecimal(column, value);
                    }));
                    break;
                case "java.lang.String":
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        String value = resultSet.getString(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.VARCHAR);
                        else
                            stmt.setString(column, value);
                    }));
                    break;
                case "java.lang.Int":
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        Integer value = resultSet.getInt(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.INTEGER);
                        else
                            stmt.setInt(column, value);
                    }));
                    break;
                case "java.lang.Long":
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        Long value = resultSet.getLong(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.BIGINT);
                        else
                            stmt.setLong(column, value);
                    }));
                    break;
                case "java.lang.Boolean":
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        Boolean value = resultSet.getBoolean(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.BOOLEAN);
                        else
                            stmt.setBoolean(column, value);
                    }));
                    break;
                default:
                    log.error("columnName: {}, type: {} using generic (slow) mapper", columnName, columnClassName);
                    list.add((stmt, resultSet) -> ReThrowException.wrap(() -> {
                        Object value = resultSet.getObject(column);
                        if (resultSet.wasNull())
                            stmt.setNull(column, java.sql.Types.OTHER);
                        else
                            stmt.setObject(column, value);
                    }));
                    break;
            }
        }
        return list;
    }

    private String makeInsert(String insert, int columnCount) {
        StringBuilder sb = new StringBuilder(insert)
                .append(" VALUES(");
        for (int i = 0 ; i < columnCount ; i++) {
            if (i != 0)
                sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private static DataSource makeDataSource(Arguments arguments) throws ExitException {
        String url = arguments.getDb();
        PGSimpleDataSource ds = new PGSimpleDataSource();

        Matcher matcher = POSTGRES_URL_REGEX.matcher(url);
        if (matcher.matches()) {
            String user = matcher.group(1);
            String pass = matcher.group(2);
            String host = matcher.group(3);
            String port = matcher.group(4);
            String base = matcher.group(5);
            if (user != null)
                ds.setUser(user);
            if (pass != null)
                ds.setPassword(pass);
            ds.setServerName(host);
            if (port != null)
                ds.setPortNumber(Integer.parseUnsignedInt(port));
            ds.setDatabaseName(base);
            return ds;
        } else {
            throw arguments.usage(url + " is not a valid database connection");
        }
    }

}
