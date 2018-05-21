package com.hundsun.news.test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hundsun.news.lda.LdaDataProc;
import com.hundsun.news.lda.LdaEstimator;
import com.hundsun.news.lda.LdaFilter;
import com.hundsun.news.lda.LdaFilterRule;
import com.hundsun.news.lda.LdaTrainer;
import com.hundsun.news.processor.DbInfo;
import com.hundsun.news.processor.MysqlInfo;
import com.hundsun.news.processor.Normalization;
import com.hundsun.news.processor.WordSegmentation;
import com.hundsun.news.recommender.Doc;
import com.hundsun.news.recommender.RecommenderWithTopicAndTFIDF;
import com.hundsun.news.recommender.TopicOptimization;
import com.hundsun.news.recommender.WordOptimizationByIDF;

/**
 * A complete workflow of the whole recommenation process
 * @author Zhengzhe Xiang
 *
 */
public class WorkFlowTest {

	public static void main(String args[]) {

		/*// 设置新闻数据库
		System.out.println("0. Start");
		DbInfo news_dbinfo = new DbInfo("./data/newsdb.properties");
		DbInfo behavior_dbinfo = new DbInfo("./data/behaviordb.properties");
		DbInfo recommend_dbinfo = new DbInfo("./data/recommenddb.properties");

		System.out.println("1. Normalization");
		Normalization norm = new Normalization();
		String filed1 = "JSID";
		norm.formatFiledType(news_dbinfo, filed1);
		norm.formatFiledType(recommend_dbinfo, filed1);
		String filed2 = "USERID";
		String filed3 = "NEWSID";
		norm.formatFiledType(behavior_dbinfo, filed2);
		norm.formatFiledType(behavior_dbinfo, filed3);

		//分词 word segmentor
		System.out.println("2. Segmentor");
		WordSegmentation wSeg = new WordSegmentation();
		wSeg.segmentorbyStanford(news_dbinfo);
		wSeg.segmentorbyStanford(recommend_dbinfo);*/
		
		MysqlInfo mySQLInfo = new MysqlInfo("./data/mysql.properties");
		DbInfo mongoDBinfo = new DbInfo("./data/mongo.properties");

		// 过滤器 filter stop words
		System.out.println("3. Filter");
		LdaFilterRule rule = new LdaFilterRule("./data/stopWords");
		LdaFilter filter = new LdaFilter(rule);
		LdaDataProc dp = new LdaDataProc(mySQLInfo, filter);
		String dbType = "mysql";
		dp.read(dbType);
		dp.process();

		// 开始训练
		System.out.println("4. Train");
		LdaTrainer tra;
		try {
			tra = new LdaTrainer("./data/parameters.properties", dp);
			tra.train();
		} catch (IOException e) {
			System.out.println("训练失败");
			e.printStackTrace();
		}

		// 对1个新输入的文档进行估计
		/*LdaDataProc newDocProc = new LdaDataProc("./data/news/news1", filter);
		newDocProc.read();
		newDocProc.process();
		LdaEstimator es;
		try {
			es = new LdaEstimator("./data/parameters.properties");
			es.estimate(newDocProc);

		} catch (IOException e) {
			System.out.println("估计失败");
			e.printStackTrace();
		}*/
		
		// 对数据库中的文档进行估计
		System.out.println("5. Estimator");
		try {
			LdaEstimator es = new LdaEstimator("./data/parameters.properties");
			es.estimate(mySQLInfo, dbType, mongoDBinfo);
			//es.estimate(recommend_dbinfo);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("对数据库中的主题估计失败");
			e.printStackTrace();
		}

		/*// get topic rank
		System.out.println("6. topic rank and idf");
		TopicOptimization topt = new TopicOptimization("./data/parameters.properties");
		try {
			Map<Integer, Double> topicRankMap = topt.getTopicRankMap();
			System.out.println(topicRankMap);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		WordOptimizationByIDF wOptIDF = new WordOptimizationByIDF("./data/parameters.properties");
		try {
			Map<String, Double> wordRankMap = wOptIDF.getWordIDFMap(mySQLInfo);
			System.out.println(wordRankMap);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// recommendation
		System.out.println("7. Recommender");

		int topM = 5;
		int topN = 10;
		List<String> userIdList = new LinkedList<String>();
		userIdList.add("YX");
		userIdList.add("DPP");
		userIdList.add("GLL");
		userIdList.add("LCX");
		userIdList.add("ZT");
		userIdList.add("CXY");
		userIdList.add("HXT");

		for (String userId : userIdList) {
			System.out.println("\n\n\n\n");
			RecommenderWithTopicAndTFIDF recommender = null;
			try {
				recommender = new RecommenderWithTopicAndTFIDF(news_dbinfo, behavior_dbinfo, userId, topM,
						"./data/parameters.properties");
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("userId is:" + recommender.userId);
			System.out.println("reading list is:");

			for (Doc doc : recommender.oldDocList) {
				System.out.println(doc.docID);
				System.out.println(doc.InfoTitle);
				// System.out.println(doc.Content);

			}

			System.out.println();
			System.out.println("recommendation list for " + recommender.userId + " is:");

			Map<Doc, Double> resultMap = recommender.recommend(recommend_dbinfo, topN);
			if (resultMap == null) {
				System.out.println("No Recommendation");
			} else {
				for (Entry<Doc, Double> item : resultMap.entrySet()) {
					System.out.println(item.getKey().docID);
					System.out.println(item.getKey().InfoTitle);
					// System.out.println(item.getKey().Content);
				}
			}
		}*/

	}

}
