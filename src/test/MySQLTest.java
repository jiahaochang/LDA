package test;

import org.junit.Test;

import com.hundsun.news.processor.MysqlInfo;
import com.hundsun.news.processor.WordSegmentation;

public class MySQLTest {
	@Test
	public void mysqlTest(){
		WordSegmentation ws = new WordSegmentation();
		MysqlInfo mysqlInfo = new MysqlInfo("data/mysql.properties");
		ws.getDataFromMysql(mysqlInfo);
	}
}
