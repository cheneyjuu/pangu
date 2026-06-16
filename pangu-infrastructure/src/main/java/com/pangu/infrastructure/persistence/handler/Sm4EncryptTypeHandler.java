package com.pangu.infrastructure.persistence.handler;

import com.pangu.infrastructure.security.crypto.Sm4Util;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 国密 SM4 透明加解密 MyBatis 类型处理器
 * 用于对敏感属性（如姓名、身份证号）进行落盘自动加密、读取自动解密
 */
@Component
public class Sm4EncryptTypeHandler extends BaseTypeHandler<String> {

    @Value("${platform.security.sm4-key-hex}")
    private String sm4KeyHex;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 在将参数存入数据库前执行国密 SM4 加密
        ps.setString(i, Sm4Util.encryptHex(parameter, sm4KeyHex));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 从数据库读取密文并执行解密
        return Sm4Util.decryptHex(rs.getString(columnName), sm4KeyHex);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return Sm4Util.decryptHex(rs.getString(columnIndex), sm4KeyHex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return Sm4Util.decryptHex(cs.getString(columnIndex), sm4KeyHex);
    }
}
