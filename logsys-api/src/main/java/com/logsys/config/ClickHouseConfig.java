package com.logsys.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
@MapperScan(basePackages = "com.logsys.dao.clickhouse", sqlSessionFactoryRef = "clickHouseSqlSessionFactory")
public class ClickHouseConfig {

    @Value("${spring.datasource.clickhouse.url}")
    private String url;

    @Value("${spring.datasource.clickhouse.username}")
    private String username;

    @Value("${spring.datasource.clickhouse.password}")
    private String password;

    @Bean(name = "clickHouseDataSource")
    public DataSource clickHouseDataSource() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        return new ClickHouseDataSource(url, props);
    }

    @Bean(name = "clickHouseSqlSessionFactory")
    public SqlSessionFactory clickHouseSqlSessionFactory(
            @Qualifier("clickHouseDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }
}
