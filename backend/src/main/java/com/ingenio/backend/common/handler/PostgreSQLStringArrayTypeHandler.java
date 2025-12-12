package com.ingenio.backend.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PostgreSQL TEXT[] 数组类型处理器
 *
 * 功能：
 * - 将Java List<String>转换为PostgreSQL TEXT[]数组
 * - 将PostgreSQL TEXT[]数组转换为Java List<String>
 * - 正确处理PostgreSQL数组格式：{element1,element2,...}
 *
 * 使用场景：
 * - ProjectEntity.tags字段（标签数组）
 * - 其他需要存储字符串数组的字段
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@MappedTypes(List.class)
public class PostgreSQLStringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    /**
     * 设置非空参数
     * 将Java List<String>转换为PostgreSQL Array对象
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setArray(i, null);
        } else {
            Connection connection = ps.getConnection();
            // 将List<String>转换为String[]数组，然后创建PostgreSQL Array对象
            String[] array = parameter.toArray(new String[0]);
            Array sqlArray = connection.createArrayOf("text", array);
            ps.setArray(i, sqlArray);
        }
    }

    /**
     * 获取可空结果（根据列名）
     * 将PostgreSQL Array转换为Java List<String>
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return getListFromArray(rs.getArray(columnName));
    }

    /**
     * 获取可空结果（根据列索引）
     * 将PostgreSQL Array转换为Java List<String>
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return getListFromArray(rs.getArray(columnIndex));
    }

    /**
     * 获取可空结果（用于存储过程）
     * 将PostgreSQL Array转换为Java List<String>
     */
    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return getListFromArray(cs.getArray(columnIndex));
    }

    /**
     * 将PostgreSQL Array对象转换为Java List<String>
     *
     * @param array PostgreSQL Array对象
     * @return Java List<String>，如果array为null则返回空List
     */
    private List<String> getListFromArray(Array array) throws SQLException {
        if (array == null) {
            return new ArrayList<>();
        }

        // 将Array对象转换为Java数组
        Object arrayObject = array.getArray();

        if (arrayObject == null) {
            return new ArrayList<>();
        }

        // PostgreSQL返回的是String[]类型
        if (arrayObject instanceof String[]) {
            return Arrays.asList((String[]) arrayObject);
        }

        // 如果是Object[]，转换为String[]
        if (arrayObject instanceof Object[]) {
            Object[] objects = (Object[]) arrayObject;
            List<String> result = new ArrayList<>(objects.length);
            for (Object obj : objects) {
                result.add(obj != null ? obj.toString() : null);
            }
            return result;
        }

        return new ArrayList<>();
    }
}
