/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.elasticsearch.common.Strings;

/**
 * @author wusheng
 */
public class H2AlarmQueryDAO implements IAlarmQueryDAO {
    private JDBCHikariCPClient client;

    public H2AlarmQueryDAO(JDBCHikariCPClient client) {
        this.client = client;
    }

    @Override
    public Alarms getAlarm(Scope scope, String keyword, int limit, int from, long startTB,
        long endTB) throws IOException {

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);
        sql.append("from ").append(AlarmRecord.INDEX_NAME).append(" where ");
        sql.append(" 1=1 ");
        if (startTB != 0 && endTB != 0) {
            sql.append(" and ").append(AlarmRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startTB);
            sql.append(" and ").append(AlarmRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endTB);
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(AlarmRecord.ALARM_MESSAGE).append(" like '%").append(keyword).append("%' ");
        }
        sql.append(" order by ").append(AlarmRecord.START_TIME).append(" desc ");

        Alarms alarms = new Alarms();
        try (Connection connection = client.getConnection()) {

            try (ResultSet resultSet = client.executeQuery(connection, "select count(1) total from (select 1 " + sql.toString() + " )", parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    alarms.setTotal(resultSet.getInt("total"));
                }
            }

            this.buildLimit(sql, from, limit);

            try (ResultSet resultSet = client.executeQuery(connection, "select * " + sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    AlarmMessage message = new AlarmMessage();
                    message.setId(resultSet.getString(AlarmRecord.ID0));
                    message.setMessage(resultSet.getString(AlarmRecord.ALARM_MESSAGE));
                    message.setStartTime(resultSet.getLong(AlarmRecord.START_TIME));
                    message.setScope(Scope.valueOf(resultSet.getInt(AlarmRecord.SCOPE)));

                    alarms.getMsgs().add(message);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return alarms;
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }
}
