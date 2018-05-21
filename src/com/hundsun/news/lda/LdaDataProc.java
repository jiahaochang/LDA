package com.hundsun.news.lda;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import com.hundsun.news.inter.DataBaseInfo;
import com.hundsun.news.processor.DbInfo;
import com.hundsun.news.topicmodel.DataProcessor;
import com.hundsun.news.topicmodel.Filter;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class LdaDataProc extends DataProcessor {

	public String fileDir;
	public DataBaseInfo dbinfo;
	public ArrayList<ArrayList<String>> docs;
	public Map<String, Integer> termToIndexMap;
	public ArrayList<String> indexToTermMap;
	public Map<String, Integer> termCountMap;

	public LdaDataProc() {
	}

	public LdaDataProc(String fileDir, Filter filter) {
		this.fileDir = fileDir;
		this.dataFilter = filter;
		this.docs = new ArrayList<ArrayList<String>>();
		this.termToIndexMap = new HashMap<String, Integer>();
		this.indexToTermMap = new ArrayList<String>();
	}

	public LdaDataProc(DataBaseInfo dbinfo, Filter filter) {
		this.dbinfo = dbinfo;
		this.dataFilter = filter;
		this.docs = new ArrayList<ArrayList<String>>();
		this.termToIndexMap = new HashMap<String, Integer>();
		this.indexToTermMap = new ArrayList<String>();
	}

	private ArrayList<String> readFile(File file) {
		BufferedReader reader = null;
		ArrayList<String> words = new ArrayList<String>();
		try {
			reader = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = reader.readLine()) != null) {
				StringTokenizer strTok = new StringTokenizer(line);
				while (strTok.hasMoreTokens()) {
					String token = strTok.nextToken();
					words.add(token.toLowerCase().trim());
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return words;
	}

	public void read(String dbType) {
		// 如果是数据库信息
		if (this.fileDir == null && this.dbinfo != null) {
			Connection conn = null;
			Statement stmt = null;
			// "jdbc:mysql://183.129.253.170:3306/ghtorrent";
			final String DB_URL = dbinfo.getAddress() + ":" + dbinfo.getPort() + "/" + dbinfo.getDbName();

			// 数据库的用户名与密码，需要根据自己的设置
			final String USER = dbinfo.getUsername();
			final String PASS = dbinfo.getPassword();
			try {
				// 注册 JDBC 驱动
				Class.forName("com.mysql.jdbc.Driver");

				// 打开链接
				System.out.println("连接数据库...");
				conn = DriverManager.getConnection(DB_URL, USER, PASS);

				// 执行查询
				System.out.println(" 实例化Statement对象...");
				stmt = conn.createStatement();
				String sql;
				sql = "SELECT body_new FROM issue_body_new";
				ResultSet rs = stmt.executeQuery(sql);
				// 展开结果集数据库
				while (rs.next()) {
					// 通过字段检索
					// 通过字段检索
					String body = rs.getString("body_new");
					ArrayList<String> words = new ArrayList<String>();
					StringTokenizer strTok = new StringTokenizer(body);
					while (strTok.hasMoreTokens()) {
						String token = strTok.nextToken();
						words.add(token.toLowerCase().trim());
					}
					this.docs.add(words);
				}
				// 完成后关闭
				rs.close();
				stmt.close();
				conn.close();
			} catch (SQLException se) {
				// 处理 JDBC 错误
				se.printStackTrace();
			} catch (Exception e) {
				// 处理 Class.forName 错误
				e.printStackTrace();
			} finally {
				// 关闭资源
				try {
					if (stmt != null)
						stmt.close();
				} catch (SQLException se2) {
				} // 什么都不做
				try {
					if (conn != null)
						conn.close();
				} catch (SQLException se) {
					se.printStackTrace();
				}
			}

		}
	}

	@Override
	public void read() {
		// 如果是数据库信息
		if (this.fileDir == null && this.dbinfo != null) {
			Mongo mongo = null;
			String address = dbinfo.getAddress();
			Integer port = dbinfo.getPort();
			String dbName = this.dbinfo.getDbName();
			String collectionName = this.dbinfo.getTableName();
			try {
				mongo = new Mongo(address, port);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			DB db = mongo.getDB(dbName);

			DBCollection newsCollection = db.getCollection(collectionName);
			DBCursor newsCur = newsCollection.find();
			while (newsCur.hasNext()) {
				DBObject newsDbObject = newsCur.next();
				if (newsDbObject.containsField("titleWords")) {
					String docID = (String) newsDbObject.get("JSID");
					String titleWords = (String) newsDbObject.get("titleWords");
					String contentWords = (String) newsDbObject.get("contentWords");
					String docContent = titleWords + " " + contentWords;
					ArrayList<String> words = new ArrayList<String>();
					StringTokenizer strTok = new StringTokenizer(docContent);
					while (strTok.hasMoreTokens()) {
						String token = strTok.nextToken();
						words.add(token.toLowerCase().trim());
					}
					this.docs.add(words);
				}
			}
			mongo.close();

		} else if (this.fileDir != null && this.dbinfo == null) {// 如果输入的是目录,这意味着训练
			File dir = new File(this.fileDir);
			if (dir.isDirectory()) {
				for (File docFile : dir.listFiles()) {
					ArrayList<String> words = this.readFile(docFile);
					this.docs.add(words);
				}
			}
			// 如果输入的是文件，这意味着估计
			if (dir.isFile()) {
				ArrayList<String> words = this.readFile(dir.getAbsoluteFile());
				this.docs.add(words);
			}
		}
	}

	@Override
	public void process() {
		// TODO Auto-generated method stub
		// 先过滤
		this.dataFilter.filter(this.docs);
		// 再统计
		for (ArrayList<String> doc : (ArrayList<ArrayList<String>>) this.docs) {
			for (String word : doc) {
				if (!this.termToIndexMap.containsKey(word)) {
					Integer newIndex = this.termToIndexMap.size();
					this.termToIndexMap.put(word, newIndex);
					this.indexToTermMap.add(word);
				}
			}
		}
		// 赋值
		this.data = this.docs;
	}

	public ArrayList getData() {
		return (ArrayList) this.data;
	}

	// added by tokyo
	/**
	 * 保存termToInDexMap
	 */
	public void savetermToIndexMap(String filePath) {

		String fileName = "termToIndexMap";
		ArrayList<String> lines = new ArrayList<String>();
		for (Entry<String, Integer> termToIndex : termToIndexMap.entrySet()) {
			String line = termToIndex.getKey() + ":" + termToIndex.getValue();
			lines.add(line);
		}
		FileUtil.writeLines(filePath + fileName, lines);
	}

	@Override
	public void setFilter(Filter filter) {
		// TODO Auto-generated method stub

	}

}
