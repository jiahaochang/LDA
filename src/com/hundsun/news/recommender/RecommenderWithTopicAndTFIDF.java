package com.hundsun.news.recommender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import com.hundsun.news.lda.FileUtil;
import com.hundsun.news.processor.DbInfo;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

/**
 * 
 * @author tokyo best 5. recommend with topic vector + TFIDF
 */
public class RecommenderWithTopicAndTFIDF {

	static long MILLISECONDS_IN_YEAR = 365 * 24 * 3600 * 1000L;
	public String userId;
	public List<Doc> oldDocList;
	Set<String> oldDocIdSet;
	Integer profileSize;
	Map<Integer, Double> favoriteTopicWeightMap;
	Map<Integer, Double> favoriteTopMTopicWeightMap;
	Map<Integer, Double> topicRankScoreMap;
	Map<String, Double> userKeyWordsFrequencyMap;
	Map<String, Double> wordIDFMap;
	Set<String> stopWordSet;

	private String paramFileName;

	/**
	 * 5.1 construct recommender which consider news topic and TFIDF value
	 */
	public RecommenderWithTopicAndTFIDF() {

	}

	/**
	 * 5.1 construct recommender which consider news topic and TFIDF value
	 * 
	 * @param paramFileName
	 */
	public RecommenderWithTopicAndTFIDF(String paramFileName) {
		this.paramFileName = paramFileName;
	}

	/**
	 * 5.1 construct recommender which consider news topic and TFIDF value
	 * 
	 * @param newsdb
	 *            database of news read by user in the past
	 * @param behaviordb
	 *            users' reading behaviors, which consist of userId-newsId-time
	 * @param userId
	 *            the Id of target user
	 * @param topM
	 *            how many topics should we use, 1 <= topM <= n, n is the topic
	 *            number in LDA
	 * @param paramFileName
	 *            config file name
	 * @throws IOException
	 */
	public RecommenderWithTopicAndTFIDF(DbInfo newsdb, DbInfo behaviordb, String userId, int topM, String paramFileName)
			throws IOException {
		this.paramFileName = paramFileName;
		Properties prop = new Properties();
		FileInputStream fis = new FileInputStream(new File(this.paramFileName));
		prop.load(fis);
		String filePath = prop.getProperty("ldaResultsPath");
		String trsFileName = prop.getProperty("topicRankScoreFileName");
		String widfFileName = prop.getProperty("wordIDFFileName");
		fis.close();

		this.userId = userId;
		this.oldDocList = new ArrayList<Doc>();
		this.oldDocIdSet = new HashSet<String>();
		this.favoriteTopicWeightMap = new HashMap<>();
		this.favoriteTopMTopicWeightMap = new HashMap<>();
		this.topicRankScoreMap = FileUtil.readTopicRankScoreMapfromFile(filePath, trsFileName);
//		System.out.println("this.topicRankScoreMap: " + this.topicRankScoreMap);
		this.profileSize = topM;
		this.wordIDFMap = FileUtil.readWordIDFMapfromFile(filePath, widfFileName);
		this.userKeyWordsFrequencyMap = new HashMap<String, Double>();
		ArrayList<String> stopWords = new ArrayList<String>();
		FileUtil.readLines("./data/stopWords", stopWords);
		this.stopWordSet = new HashSet<>(stopWords);

		String address = newsdb.getAddress();
		Integer port = newsdb.getPort();
		String dbName = newsdb.getDbName();
		String newsTableName = newsdb.getTableName();
		String behTableName = behaviordb.getTableName();
		
		Mongo mongo = null;
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);

		DBCollection userRecordCollection = db.getCollection(behTableName);
		DBCollection newsCollection = db.getCollection(newsTableName);
		DBCursor userRecordCur = userRecordCollection.find(new BasicDBObject("USERID", userId));
//		System.out.println("userRecordCur.count(): " + userRecordCur.count());
		while (userRecordCur.hasNext()) {
			DBObject userRecordDbObject = userRecordCur.next();
			String newsID = (String) userRecordDbObject.get("NEWSID");
			String timeString = (String) userRecordDbObject.get("TIME");
			DBObject newsDbObject = newsCollection.findOne(new BasicDBObject("JSID", newsID));
			if (newsDbObject == null) {
				continue;
			} else {
				BasicDBList topicDBList = (BasicDBList) newsDbObject.get("topicVector");
				if (topicDBList == null) {
					continue;
				}
				String InfoTitle = (String) newsDbObject.get("InfoTitle");
				String Content = (String) newsDbObject.get("Content");
				// get words from read news
				String titleWords = (String) newsDbObject.get("titleWords");
				String contentWords = (String) newsDbObject.get("contentWords");
				for (String word : titleWords.split(" ")) {
					if (!this.stopWordSet.contains(word)) {
						if (this.userKeyWordsFrequencyMap.containsKey(word)) {
							double oldValue = this.userKeyWordsFrequencyMap.get(word);
							this.userKeyWordsFrequencyMap.put(word, oldValue + 1.0);
						} else {
							this.userKeyWordsFrequencyMap.put(word, 1.0);
						}
					}
				}
				for (String word : contentWords.split(" ")) {
					if (!this.stopWordSet.contains(word)) {
						if (this.userKeyWordsFrequencyMap.containsKey(word)) {
							double oldValue = this.userKeyWordsFrequencyMap.get(word);
							this.userKeyWordsFrequencyMap.put(word, oldValue + 1.0);
						} else {
							this.userKeyWordsFrequencyMap.put(word, 1.0);
						}
					}
				}

				// get topic vector and top M topic from read news
				double[] topicVector = new double[topicDBList.size()];
				int i = 0;
				for (Object object : topicDBList) {
					topicVector[i] = (double) object;
					i++;
				}
				// get time decay weight
				double timeDecayWeight = getTimeDecayWeigh(timeString);

				Map<Integer, Double> topMTopicMap = getTopMTopicMap(topicVector, this.profileSize);
				for (Entry<Integer, Double> item : topMTopicMap.entrySet()) {
					int key = item.getKey();
					double topicRankScore = this.topicRankScoreMap.get(key);
					double value = item.getValue() * topicRankScore * timeDecayWeight;
					if (this.favoriteTopicWeightMap.containsKey(key)) {
						double oldValue = this.favoriteTopicWeightMap.get(key);
						double newValue = oldValue + value;
						this.favoriteTopicWeightMap.put(key, newValue);
					} else {
						this.favoriteTopicWeightMap.put(key, value);
					}
					this.favoriteTopicWeightMap.containsKey(key);
				}
				Doc doc = new Doc(newsID, InfoTitle, Content, topicVector);
				this.oldDocList.add(doc);
				this.oldDocIdSet.add(newsID);
			}
		}
		List<Entry<Integer, Double>> rankList = new ArrayList<Entry<Integer, Double>>(
				this.favoriteTopicWeightMap.entrySet());
		Collections.sort(rankList, new Comparator<Map.Entry<Integer, Double>>() {
			public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
				double result = o2.getValue() - o1.getValue();
				if (result > 0) {
					return 1;
				} else if (result < 0) {
					return -1;
				}
				return 0;
			}
		});
		Iterator<Entry<Integer, Double>> iterator = rankList.iterator();
		for (int i = 0; i < this.profileSize; i++) {
			if (iterator.hasNext()) {
				Entry<Integer, Double> entry = iterator.next();
				Integer key = entry.getKey();
				Double value = entry.getValue();
				this.favoriteTopMTopicWeightMap.put(key, value);
			}
		}
		mongo.close();
	}

	/**
	 * get the weight of news according to the reading time <br/>
	 * In general, user's recent news has higher weight
	 * 
	 * @param timeString
	 *            the reading time of a news
	 * @return the time weight of news
	 */
	private double getTimeDecayWeigh(String timeString) {
		double timeWeight;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");

		Date currentTime = new Date();
		Date readTime = null;
		try {
			readTime = sdf.parse(timeString);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (readTime != null) {
			long interval = currentTime.getTime() - readTime.getTime();
			double year = interval * 1.0 / RecommenderWithTopicAndTFIDF.MILLISECONDS_IN_YEAR;
			double ex = Math.exp(-year);
			timeWeight = ex / (1 + ex);
		} else {
			timeWeight = 0.0;
		}

		return timeWeight;
	}

	/**
	 * get topM topic <br/>
	 * for example, the topic vector is (0.5, 0.4, 0.1) and topM=2, then only
	 * 0.5 and 0.4 is used in construct recommender
	 * 
	 * @param topicVector
	 *            the topic vector of news
	 * @param topM
	 *            how many topics should we use, 1 <= topM <= n, n is the topic
	 *            number in LDA
	 * @return
	 */
	private Map<Integer, Double> getTopMTopicMap(double[] topicVector, int topM) {
		Map<Integer, Double> topNTopicMap = new HashMap<>();
		Set<Integer> biggerIndexSet = new HashSet<>();
		for (int i = 0; i < topM; i++) {
			double maxValue = 0.0;
			int maxIndex = -1;
			for (int topicIndex = 0; topicIndex < topicVector.length; topicIndex++) {
				double topicValue = topicVector[topicIndex];
				if (topicValue > maxValue && !biggerIndexSet.contains(topicIndex)) {
					maxValue = topicValue;
					maxIndex = topicIndex;
				}
			}
			biggerIndexSet.add(maxIndex);
			topNTopicMap.put(maxIndex, maxValue);
		}
		return topNTopicMap;
	}

	/**
	 * recommend news for target user
	 * 
	 * @param recdb
	 *            database of new news
	 * @param topN
	 *            how many news should we recommend
	 * @return the recommended topN news and corresponding rank score
	 */
	public Map<Doc, Double> recommend(DbInfo recdb, int topN) {
		Map<Doc, Double> unsortedResultMap = new HashMap<Doc, Double>();
		Map<Doc, Double> sortedResultMap = null;
		if (this.oldDocIdSet.size() == 0) {

		} else {
			String address = recdb.getAddress();
			Integer port = recdb.getPort();
			String dbName = recdb.getDbName();
			String tabName = recdb.getTableName();
			Mongo mongo = null;
			try {
				mongo = new Mongo(address, port);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			DB db = mongo.getDB(dbName);

			DBCollection newsCollection = db.getCollection(tabName);
			// DBCollection newsCollection = db.getCollection("newsofupdate");
			DBCursor newsCur = newsCollection.find();
			while (newsCur.hasNext()) {
				DBObject newsDbObject = newsCur.next();
				String newsID = (String) newsDbObject.get("JSID");
				// 过滤已阅新闻
				if (this.oldDocIdSet.contains(newsID)) {
					continue;
				}
				BasicDBList topicDBList = (BasicDBList) newsDbObject.get("topicVector");
				String InfoTitle = (String) newsDbObject.get("InfoTitle");
				String Content = (String) newsDbObject.get("Content");

				double keyWordSimilarity = 0.0;
				// get rank score of keywords
				String titleWords = (String) newsDbObject.get("titleWords");
				String contentWords = (String) newsDbObject.get("contentWords");
				Map<String, Double> newsKeyWordsSet = new HashMap<>();
				for (String word : titleWords.split(" ")) {
					if (!this.stopWordSet.contains(word)) {
						if (newsKeyWordsSet.containsKey(word)) {
							double oldValue = newsKeyWordsSet.get(word);
							newsKeyWordsSet.put(word, oldValue + 1.0);
						} else {
							newsKeyWordsSet.put(word, 1.0);
						}
					}
				}
				for (String word : contentWords.split(" ")) {
					if (!this.stopWordSet.contains(word)) {
						if (newsKeyWordsSet.containsKey(word)) {
							double oldValue = newsKeyWordsSet.get(word);
							newsKeyWordsSet.put(word, oldValue + 1.0);
						} else {
							newsKeyWordsSet.put(word, 1.0);
						}
					}
				}
				// keyWordSimilarity =
				// getWeightedJaccardSimilarity(this.userKeyWordsFrequencyMap,
				// newsKeyWordsSet, this.wordRankMap);
				keyWordSimilarity = getTFIDFCosineSimilarity(this.userKeyWordsFrequencyMap, newsKeyWordsSet,
						this.wordIDFMap);
				// get rank score of topicsgetWeightedCosineSimilarity
				double[] topicVector = new double[topicDBList.size()];
				int i = 0;
				for (Object object : topicDBList) {
					topicVector[i] = (double) object;
					i++;
				}
				Map<Integer, Double> topNTopicMap = getTopMTopicMap(topicVector, this.profileSize);

				double topicSimilarity = 0.0;
				for (Integer index : this.favoriteTopMTopicWeightMap.keySet()) {
					double weight = this.favoriteTopMTopicWeightMap.get(index);
					if (topNTopicMap.containsKey(index)) {
						topicSimilarity += weight * topNTopicMap.get(index);
					}
				}

				Doc doc = new Doc(newsID, InfoTitle, Content, topicVector);
				unsortedResultMap.put(doc, topicSimilarity * (keyWordSimilarity + 1.0));
			}
			sortedResultMap = getTopNofSortedMap(unsortedResultMap, topN);
			mongo.close();
		}

		return sortedResultMap;
	}

	/**
	 * get the TFIDF similarity of user's profile and new news
	 * 
	 * @param userKeyWordsFrequencyMap2
	 *            all words of news read by the target user
	 * @param newsKeyWordFrequencyMap
	 *            all words of a news may be recommended
	 * @param wordIDFMap2
	 *            IDF value of all words
	 * @return TFIDF similarity
	 */
	private double getTFIDFCosineSimilarity(Map<String, Double> userKeyWordsFrequencyMap2,
			Map<String, Double> newsKeyWordFrequencyMap, Map<String, Double> wordIDFMap2) {
		// TODO Auto-generated method stub
		double weightedCosineSimilarity = 0.0;
		double fenzi = 0.0;
		double userFenmu = 0.0;
		double newsFenmu = 0.0;

		Set<String> wordSet = new HashSet<>();
		// wordSet.addAll(userKeyWordsFrequencyMap2.keySet());//to increase
		// efficiency
		wordSet.addAll(newsKeyWordFrequencyMap.keySet());
		wordSet.retainAll(wordIDFMap2.keySet());
		for (String word : wordSet) {
			double userValue = 0.0;
			double newsValue = 0.0;
			double wordIDFValue = wordIDFMap2.get(word);
			if (userKeyWordsFrequencyMap2.containsKey(word)) {
				userValue = userKeyWordsFrequencyMap2.get(word) * wordIDFValue;
				userValue = Math.atan(userValue);
			}
			if (newsKeyWordFrequencyMap.containsKey(word)) {
				newsValue = newsKeyWordFrequencyMap.get(word) * wordIDFValue;
				newsValue = Math.atan(newsValue);
			}
			fenzi += userValue * newsValue;
			userFenmu += Math.pow(userValue, 2);
			newsFenmu += Math.pow(newsValue, 2);
		}

		if (userFenmu * newsFenmu == 0) {
			weightedCosineSimilarity = 0;
		} else {
			double fenmu = (Math.pow((userFenmu * newsFenmu), 0.5));
			weightedCosineSimilarity = fenzi / fenmu;
		}
		return weightedCosineSimilarity;
	}

	/**
	 * get topN sorted news and rankscore map
	 * 
	 * @param unsortedResultMap
	 *            unsorted news and rankscore map
	 * @param topN
	 *            how many news should we recommend
	 * @return topN sorted news and rankscore map
	 */
	private Map<Doc, Double> getTopNofSortedMap(Map<Doc, Double> unsortedResultMap, int topN) {
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

		List<Map.Entry<Doc, Double>> rankList = new ArrayList<>();
		rankList.addAll(unsortedResultMap.entrySet());
		Collections.sort(rankList, new Comparator<Map.Entry<Doc, Double>>() {
			public int compare(Map.Entry<Doc, Double> o1, Map.Entry<Doc, Double> o2) {
				double result = o2.getValue() - o1.getValue();
				if (result > 0) {
					return 1;
				} else if (result < 0) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		LinkedHashMap<Doc, Double> rankMap = new LinkedHashMap<>();
		Iterator<Entry<Doc, Double>> iterator = rankList.iterator();
		for (int i = 0; i < topN; i++) {
			if (iterator.hasNext()) {
				Entry<Doc, Double> entry = iterator.next();
				Doc key = entry.getKey();
				Double value = entry.getValue();
				rankMap.put(key, value);
			}
		}

		return rankMap;
	}

	public void test(DbInfo newsdb, DbInfo behdb, DbInfo recdb, String userId, int topN, int topM) throws IOException {
		System.out.println("\n\n\n\n");
		RecommenderWithTopicAndTFIDF recommender = new RecommenderWithTopicAndTFIDF(newsdb, behdb, userId, topM,
				"./data/parameters.properties");
		// System.out.println("recommender.userKeyWordsFrequencyMap");
		// System.out.println(recommender.userKeyWordsFrequencyMap);
		System.out.println("userId is:" + recommender.userId);
		System.out.println("reading list is:");

		for (Doc doc : recommender.oldDocList) {
			System.out.println(doc.docID);
			System.out.println(doc.InfoTitle);
			// System.out.println(doc.Content);
			// double[] topicVector = doc.topicVector;
			// for (int i = 0; i < topicVector.length; i++) {
			// System.out.println(i + ": " + topicVector[i]);
			// }
		}

		System.out.println();
		System.out.println("recommendation list for " + recommender.userId + " is:");

		Map<Doc, Double> resultMap = recommender.recommend(recdb, topN);
		if (resultMap == null) {
			System.out.println("No Recommendation");
		} else {
			for (Entry<Doc, Double> item : resultMap.entrySet()) {
				System.out.println(item.getKey().docID);
				System.out.println(item.getKey().InfoTitle);
				// System.out.println(item.getKey().Content);
				// System.out.println(item.getValue());
				// double[] topicVector = item.getKey().topicVector;
				// for (int i = 0; i < topicVector.length; i++) {
				// System.out.println(i + ": " + topicVector[i]);
				// }
			}
		}

	}

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		int topM = 30;
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
			// test(userId, topN, topM);
		}
	}

}
