/**
 * 
 */
package com.hundsun.news.lda;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import com.hundsun.news.inter.DataBaseInfo;
import com.hundsun.news.processor.DbInfo;
import com.hundsun.news.topicmodel.Estimator;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

/**
 * inference the topic of new news/text
 * 
 * @author Zhengzhe Xiang
 *
 */
public class LdaEstimator extends Estimator {

	float alpha; // doc-topic dirichlet prior parameter
	float beta; // topic-word dirichlet prior parameter
	int V, K, M;// vocabulary size, topic number, document number
	String paramFileName;

	int iterations;// Times of iterations
	int saveStep;// The number of iterations between two saving
	int beginSaveIters;// Begin save model at this iteration

	static Map<String, Integer> termToIndexMap;
	static Double[][] phi;

	public LdaEstimator(String paramFileName) throws IOException {
		this.paramFileName = paramFileName;

		Properties prop = new Properties();
		File pFile = new File(paramFileName);
		FileInputStream pfin = new FileInputStream(pFile);
		prop.load(pfin);
		this.alpha = Float.parseFloat(prop.getProperty(("alpha")));
		this.beta = Float.parseFloat(prop.getProperty(("beta")));
		this.iterations = Integer.parseInt(prop.getProperty(("iterations")));
		this.K = Integer.parseInt(prop.getProperty(("K")));
		this.saveStep = Integer.parseInt(prop.getProperty(("saveStep")));
		this.beginSaveIters = Integer.parseInt(prop.getProperty(("beginSaveIters")));
	}

	/**
	 * ��������δ�����ĵ����в���
	 * 
	 * @param n
	 * @param d_nk
	 * @param d_nkSum
	 * @param d_nkt
	 * @param d_nktSum
	 * @param phi
	 * @return
	 */
	private int sampleTopicZ(int n, int[] d_z, int[] d_doc, int[] d_nk, Integer d_nkSum, int[][] d_nkt, int[] d_nktSum,
			Double[][] phi) {

		int d_V = d_nkt[0].length;
		int oldTopic = d_z[n];
		int t = d_doc[n];

		d_nk[oldTopic]--;
		d_nkt[oldTopic][t]--;
		d_nkSum--;
		d_nktSum[oldTopic]--;
		// ���� p(z_i = k|z_-i, w)
		double[] p = new double[K];
		for (int k = 0; k < K; k++) {
			p[k] = (d_nkt[k][t] + beta) / (d_nktSum[k] + d_V * beta) * phi[k][t];
		}
		// ���ö��ַ�ȷ��topic
		for (int k = 1; k < K; k++) {
			p[k] += p[k - 1];
		}
		double u = Math.random() * p[K - 1]; // p[] is unnormalised
		int newTopic;
		for (newTopic = 0; newTopic < K; newTopic++) {
			if (u < p[newTopic]) {
				break;
			}
		}
		// ��������ĵ���n���ʵ�topic
		d_nk[newTopic]++;
		d_nkt[newTopic][t]++;
		d_nkSum++;
		d_nktSum[newTopic]++;
		return newTopic;

	}

	private double[] classify(LdaDataProc ldp, Map<String, Integer> termToIndexMap, Double[][] phi) {
		ArrayList<ArrayList<String>> docs = ldp.docs;
		if (docs.size() > 1 || docs.size() < 1) {
			return null;
		}
		ArrayList<String> words = docs.get(0);
		ArrayList<String> oldWords = words;
		ArrayList<String> newWords = new ArrayList<String>();
		Set<String> wordSet = new HashSet<String>();

		// ��ѵ��������û�е�word�ų�����ͨ������ȥ��
		for (String word : oldWords) {
			if (termToIndexMap.containsKey(word)) {
				newWords.add(word);
				wordSet.add(word);
			}
		}

		// �������
		int nWords = newWords.size();
		int d_V = phi[0].length;

		// System.out.println(oldWords.size()+","+nWords+","+d_V);
		// d_thetaΪ����ֲ�
		double[] d_theta = new double[K];
		// d_z��¼ÿ��word������
		int[] d_z = new int[nWords];

		// d_nk��ʾ topic1 topic2 ... topicK
		// doc 0.1 0.1 ... 0.1
		int[] d_nk = new int[K];
		// d_nkSum��ʾd_nk��ÿ�е�sum
		int d_nkSum = 0;
		// System.out.println(d_V);
		int[][] d_nkt = new int[K][d_V];
		int[] d_nktSum = new int[K];

		// d_doc��ʾÿ���ʵ����
		int[] d_doc = new int[nWords];

		// ��doc�е�word���

		for (int i = 0; i < nWords; ++i) {
			String word = newWords.get(i);
			int index = termToIndexMap.get(word);
			d_doc[i] = index;
		}

		// ��ʼ��topic
		for (int i = 0; i < nWords; ++i) {
			int initTopic = (int) (Math.random() * K);
			d_z[i] = initTopic;
			// number of words in doc m assigned to topic initTopic add 1
			d_nk[initTopic]++;
			// number of terms doc[m][n] assigned to topic initTopic add 1
			// System.out.println(d_nkt.length+","+d_nkt[0].length+","+initTopic+","+d_doc[i]);
			d_nkt[initTopic][d_doc[i]]++;
			// total number of words assigned to topic initTopic add 1
			d_nktSum[initTopic]++;
		}
		d_nkSum = nWords;

		// ��ʼ����d_theta
		int d_iterations = 100;
		int sample_step = 5;
		for (int i = 0; i < d_iterations; i++) {

			// �Բ����������в�������
			if (i % d_iterations == sample_step) {
				for (int k = 0; k < K; k++) {
					d_theta[k] = (d_nk[k] + alpha) / (d_nkSum + K * alpha);
				}
			}

			// �ظ����м���˹����,����d_z
			for (int n = 0; n < nWords; n++) {
				// Sample from p(z_i|z_-i, w)
				// �������ö�̬���ٶ���һ��sampleTopicZ����
				int newTopic = sampleTopicZ(n, d_z, d_doc, d_nk, d_nkSum, d_nkt, d_nktSum, phi);
				d_z[n] = newTopic;
			}
		}

		// ��һ������

		double d_thetaSum = 0.0;
		for (double x : d_theta) {
			d_thetaSum += x;
		}
		for (int i = 0; i < d_theta.length; ++i) {
			d_theta[i] = d_theta[i] / d_thetaSum;
		}

		/*
		 * ��ӡ���ݿ��� for (int x: d_nk){ System.out.print(x+" "); }
		 * System.out.println(); for (int x = 0; x<nWords;++x){
		 * System.out.print(newWords.get(x)+":"+d_z[x]+"|"); }
		 * System.out.println();
		 */

		return d_theta;

	}

	public double[] estimate(LdaDataProc newDoc) throws IOException {

		// ��ȡphi��termToIndexMap
		if (LdaEstimator.termToIndexMap == null || LdaEstimator.phi == null) {
			Properties prop = new Properties();
			FileInputStream fis = new FileInputStream(new File(this.paramFileName));
			prop.load(fis);
			String phiFilePath = prop.getProperty("phiFilePath");
			String termFilePath = prop.getProperty("termToIndexPath");
			String termFileName = prop.getProperty("termToIndexFileName");
			String phiFileName = prop.getProperty("phiFileName");
			fis.close();

			LdaEstimator.termToIndexMap = FileUtil.readMapfromFile(phiFilePath, termFileName);
			LdaEstimator.phi = FileUtil.readMatrixFromFile(termFilePath, phiFileName);
		}

		// ��ʼ��Model

		// ��ʼ����
		double[] res = this.classify(newDoc, LdaEstimator.termToIndexMap, LdaEstimator.phi);
		/*
		 * for (int i = 0;i<res.length;++i){
		 * System.out.println("topic"+i+":"+res[i]); }
		 */
		return res;
	}

	public void estimate(DataBaseInfo dbinfo, String outPutPath, DataBaseInfo mongoInfo) throws IOException {
		/**
		 * ����mongoDB����
		 */
		Mongo mongo = null;
		String address = mongoInfo.getAddress();
		Integer port = mongoInfo.getPort();
		String dbName = mongoInfo.getDbName();
		String tableName = mongoInfo.getTableName();
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);

		DBCollection dbCollection = db.getCollection(tableName);
		List<DBObject> objs = new ArrayList<DBObject>();

		/***********************************/
		Connection conn = null;
		Statement stmt = null;
		// "jdbc:mysql://183.129.253.170:3306/ghtorrent";
		final String DB_URL = dbinfo.getAddress() + ":" + dbinfo.getPort() + "/" + dbinfo.getDbName();
		// ���ݿ���û��������룬��Ҫ�����Լ�������
		final String USER = dbinfo.getUsername();
		final String PASS = dbinfo.getPassword();

		BufferedWriter writer = new BufferedWriter(new FileWriter(outPutPath));

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
			sql = "SELECT * FROM issue_body_new";
			ResultSet rs = stmt.executeQuery(sql);
			// չ����������ݿ�
			while (rs.next()) {
				// ͨ���ֶμ���
				int id = rs.getInt("id");
				int commit_id = rs.getInt("commit_id");
				int comment_id = rs.getInt("comment_id");
				String body = rs.getString("body");
				String docContent = rs.getString("body_new");
				// ��ʼ����
				ArrayList<String> words = new ArrayList<String>();
				StringTokenizer strTok = new StringTokenizer(docContent);
				while (strTok.hasMoreTokens()) {
					String token = strTok.nextToken();
					words.add(token.toLowerCase().trim());
				}
				LdaDataProc ldp = new LdaDataProc();
				ldp.docs = new ArrayList<ArrayList<String>>();
				ldp.docs.add(words);
				double[] res = this.estimate(ldp);
				for (double tmp : res) {
					System.out.print(tmp + " ");
				}
				System.out.println();
				// newsDbObject.put("topicVector", res);
				// newsCollection.save(newsDbObject);
				objs.add(new BasicDBObject("id", id).append("commit_id", commit_id).append("comment_id", comment_id)
						.append("body", body).append("topicVector", res));

			}
			dbCollection.insert(objs);
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
		writer.close();

	}

	/**
	 * compute the topic of new news/text ��ѵ����ģ�ͼ��㡰�µ����š��ı����Ļ���
	 * 
	 * @param dbinfo
	 * @throws IOException
	 */
	public void estimate(DataBaseInfo dbinfo) throws IOException {
		Mongo mongo = null;
		String address = dbinfo.getAddress();
		Integer port = dbinfo.getPort();
		String dbName = dbinfo.getDbName();
		String tableName = dbinfo.getTableName();
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);

		DBCollection newsCollection = db.getCollection(tableName);
		DBCursor newsCur = newsCollection.find();
		int i = 0;
		while (newsCur.hasNext()) {
			i++;
			if (i % 1000 == 0) {
				System.out.println("current is: " + i);
			}
			DBObject newsDbObject = newsCur.next();

			// ???
			if (newsDbObject.containsField("titleWords")) {
				String docID = (String) newsDbObject.get("JSID");
				String titleWords = (String) newsDbObject.get("titleWords");
				String contentWords = (String) newsDbObject.get("contentWords");
				String docContent = titleWords + " " + contentWords;
				// ��ʼ����
				ArrayList<String> words = new ArrayList<String>();
				StringTokenizer strTok = new StringTokenizer(docContent);
				while (strTok.hasMoreTokens()) {
					String token = strTok.nextToken();
					words.add(token.toLowerCase().trim());
				}
				LdaDataProc ldp = new LdaDataProc();
				ldp.docs = new ArrayList<ArrayList<String>>();
				ldp.docs.add(words);
				double[] res = this.estimate(ldp);
				newsDbObject.put("topicVector", res);
				newsCollection.save(newsDbObject);
			}
		}
		mongo.close();
		System.out.println("inference Done!");

	}
}
