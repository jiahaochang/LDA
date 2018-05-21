package com.hundsun.news.processor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.hundsun.news.inter.DataBaseInfo;

/**
 * Database infomation
 * 
 * @author Zhengzhe Xiang
 * 
 */
public class DbInfo implements DataBaseInfo{
	private String address;
	private Integer port;
	private String username;
	private String password;
	private String dbName;
	private String tableName;

	// 空构造函数
	public DbInfo() {
	}

	/**
	 * Construct a new DbInfo object with given variables </br>
	 * 包含成员变量的构造函数
	 * @param address
	 *            the ip of database
	 * @param port
	 *            the port of databse
	 * @param username user name of database
	 * @param password pass word of database
	 * @param dbName database name
	 * @param tableName collection/table name
	 */
	public DbInfo(String address, Integer port, String username, String password, String dbName, String tableName) {
		this.address = address;
		this.port = port;
		this.username = username;
		this.password = password;
		this.dbName = dbName;
		this.tableName = tableName;
	}

	// 
	/**
	 * Construct a new DbInfo object with configuration file </br>
	 * 从配置文件读取信息的构造函数
	 * @param filename the name of configuration fire
	 */
	public DbInfo(String filename) {
		Properties prop = new Properties();
		FileInputStream fis;
		try {
			fis = new FileInputStream(filename);
			prop.load(fis);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("找不到配置文件");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.address = prop.getProperty("address");
		this.port = Integer.parseInt(prop.getProperty("port"));
		this.username = prop.getProperty("username");
		this.password = prop.getProperty("password");
		this.dbName = prop.getProperty("dbName");
		this.tableName = prop.getProperty("tableName");

	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

}
