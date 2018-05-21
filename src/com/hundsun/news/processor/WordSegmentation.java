package com.hundsun.news.processor;

import java.net.UnknownHostException;
import java.sql.*;
import java.util.Properties;

import com.hundsun.news.inter.DataBaseInfo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * Step 2 split sentence of news into words <br/>
 * In Chinese, �ִ�
 * 
 * @author tokyo
 *
 */
public class WordSegmentation {
	private static final String basedir = System.getProperty("SegDemo", "Stanford");

	/**
	 * split every news of dbinfo into words
	 * 
	 * @param dbinfo:
	 *            inlclude address, port, username, password, dbName, tableName.
	 */
	public void segmentorbyStanford(DataBaseInfo dbinfo) {
		String address = dbinfo.getAddress();
		Integer port = dbinfo.getPort();
		String dbName = dbinfo.getDbName();
		String tableName = dbinfo.getTableName();

		Mongo mongo = null;
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);
		CRFClassifier<CoreLabel> segmenter = getSegmenter();
		DBCollection newsCollection = db.getCollection(tableName);
		DBCursor newsCur = newsCollection.find();
		int i = 0;
		while (newsCur.hasNext()) {
			i++;
			if (i % 1000 == 0) {
				System.out.println("segmentor�� ��   " + i + "����Ϣ");
			}
			DBObject newsDbObject = newsCur.next();

			// replace all number with blank
			String infoTitleString = ((String) newsDbObject.get("InfoTitle")).replaceAll("[0-9]+", " ");
			String ContentString = ((String) newsDbObject.get("Content")).replaceAll("[0-9]+", " ");

			String titleWords = segmenter.segmentString(infoTitleString).toString().replaceAll("\\pP|\\pS", "");
			String contentWords = segmenter.segmentString(ContentString).toString().replaceAll("\\pP|\\pS", "");
			newsDbObject.put("titleWords", titleWords);
			newsDbObject.put("contentWords", contentWords);

			newsCollection.save(newsDbObject);
		}

		mongo.close();

		System.out.println("news segment over");
	}

	/**
	 * 
	 */
	public void getDataFromMysql(DataBaseInfo mysql) {
		Connection conn = null;
		Statement stmt = null;
		PreparedStatement ps = null;
		CRFClassifier<CoreLabel> segmenter = getSegmenter();
		// JDBC �����������ݿ� URL
		final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
		// final String DB_URL = "jdbc:mysql://183.129.253.170:3306/ghtorrent";
		final String DB_URL = mysql.getAddress() + ":" + mysql.getPort() + "/" + mysql.getDbName();

		// ���ݿ���û��������룬��Ҫ�����Լ�������
		final String USER = mysql.getUsername();
		final String PASS = mysql.getPassword();
		try {
			// ע�� JDBC ����
			Class.forName("com.mysql.jdbc.Driver");

			// ������
			System.out.println("�������ݿ�...");
			conn = DriverManager.getConnection(DB_URL, USER, PASS);

			// ִ�в�ѯ
			System.out.println(" ʵ����Statement����...");
			stmt = conn.createStatement();
			String sql;
			sql = "SELECT * FROM " + mysql.getTableName();
			ResultSet rs = stmt.executeQuery(sql);
			// չ����������ݿ�
			while (rs.next()) {
				// ͨ���ֶμ���
				String body = rs.getString("body");
				String body1 = body.replaceAll("[0-9]+", " ");

				String body2 = segmenter.segmentString(body1).toString().replaceAll("\\pP|\\pS", "");
				// �������
				System.out.println("������body: " + body2);
				String inserSql = "INSERT INTO issue_body_new " + " VALUES (?,?,?,?,?)"; 
				System.out.println(inserSql);
				ps = conn.prepareStatement(inserSql);
				ps.setInt(1, rs.getInt("id"));
				ps.setInt(2, rs.getInt("commit_id"));
				ps.setInt(3, rs.getInt("comment_id"));
				ps.setString(4, body);
				ps.setString(5, body2);
				
				ps.executeUpdate();
			}
			// ��ɺ�ر�
			rs.close();
			stmt.close();
			conn.close();
		} catch (SQLException se) {
			// ���� JDBC ����
			se.printStackTrace();
		} catch (Exception e) {
			// ���� Class.forName ����
			e.printStackTrace();
		} finally {
			// �ر���Դ
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			} // ʲô������
			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		System.out.println("Goodbye!");
	}

	/**
	 * get segmentor in Stanford tools
	 * 
	 * @return word segmentor
	 */
	private static CRFClassifier<CoreLabel> getSegmenter() {
		Properties props = new Properties();
		props.setProperty("sighanCorporaDict", basedir);
		// props.setProperty("NormalizationTable", "data/norm.simp.utf8");
		// props.setProperty("normTableEncoding", "UTF-8");
		// below is needed because CTBSegDocumentIteratorFactory accesses it
		props.setProperty("serDictionary", basedir + "/dict-chris6.ser.gz");
		props.setProperty("inputEncoding", "UTF-8");
		props.setProperty("sighanPostProcessing", "true");

		CRFClassifier<CoreLabel> segmenter = new CRFClassifier<CoreLabel>(props);
		segmenter.loadClassifierNoExceptions(basedir + "/ctb.gz", props);
		return segmenter;
	}

}
