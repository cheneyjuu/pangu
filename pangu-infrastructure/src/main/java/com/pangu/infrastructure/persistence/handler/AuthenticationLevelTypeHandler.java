package com.pangu.infrastructure.persistence.handler;

import com.pangu.domain.model.user.AuthenticationLevel;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 认证等级枚举 AuthenticationLevel 对照数据库 SMALLINT(Integer) 映射处理器
 */
@MappedTypes(AuthenticationLevel.class)
public class AuthenticationLevelTypeHandler extends BaseTypeHandler<AuthenticationLevel> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, AuthenticationLevel parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getValue());
    }

    @Override
    public AuthenticationLevel getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int val = rs.getInt(columnName);
        return rs.wasNull() ? null : AuthenticationLevel.of(val);
    }

    @Override
    public AuthenticationLevel getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int val = rs.getInt(columnIndex);
        return rs.wasNull() ? null : AuthenticationLevel.of(val);
    }

    @Override
    public AuthenticationLevel getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int val = cs.getInt(columnIndex);
        return cs.wasNull() ? null : AuthenticationLevel.of(val);
    }
}
