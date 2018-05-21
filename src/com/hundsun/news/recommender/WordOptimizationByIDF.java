package com.hundsun.news.recommender;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hundsun.news.inter.DataBaseInfo;
import com.hundsun.news.lda.FileUtil;
import com.hundsun.news.processor.DbInfo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

/**
 * 
 * @author tokyo
 * 4.3 get all word IDF score and save in file
 * wordIDFMap
 */
public class WordOptimizationByIDF {
	class Documents{
		ArrayList<Document> docs; 
		public Documents(){
			docs = new ArrayList<Document>();
		}
    }
	
	class Document{
		String docName;
		ArrayList<String> words;
		int[] docWords;

		public Document(String docName, String docContent){
			this.docName = docName;	
			ArrayList<String> words = new ArrayList<String>();
			FileUtil.tokenizeAndLowerCase(docContent, words);
			this.words = words;
		}
		public boolean isNoiseWord(String string) {
			// filter @xxx and URL
			if(string.matches(".*www\\..*") || string.matches(".*\\.com.*") || 
					string.matches(".*http:.*") ){
				return true;
			}
			//¹ýÂËÊý×Ö
			Pattern MY_PATTERN = Pattern.compile("[a-zA-Z\u4e00-\u9fa5]+");
			Matcher m = MY_PATTERN.matcher(string);
			if (!m.matches()) {
				return true;
			} else {
				return false;
			}
		}
    }

	private String resultPath;
	
	public WordOptimizationByIDF(String paramFileName){
		Properties prop = new Properties();
		try {
			FileInputStream fis = new FileInputStream(new File(paramFileName));
			prop.load(fis);
			this.resultPath = prop.getProperty("ldaResultsPath");
			
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Map<String, Double> getWordIDFMap(DataBaseInfo dbinfo) {
//		Map<Integer, Double> unsortedWordRankMap = new HashMap<Integer, Double>();
		Map<String, Double> unsortedWordIDFMap = new HashMap<String, Double>();
		Map<String, Set<String>> wordDocNameSetMap = new HashMap<>();
		Documents documentSet = this.new Documents();
		
		String dbName = dbinfo.getDbName();
		String collectionName = dbinfo.getTableName();
		String address = dbinfo.getAddress();
		Integer port = dbinfo.getPort();
		
		//added by tokyo 
		Mongo mongo = null;
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);

		DBCollection newsCollection = db.getCollection(collectionName);
		DBCursor newsCur = newsCollection.find();
		int i = 0;
		while (newsCur.hasNext()) {
			i++;
			if (i % 1000 == 0) {
				System.out.println("current is: " + i);
			}
			DBObject newsDbObject = newsCur.next();
			
			if (newsDbObject.containsField("titleWords")) {
				String docID = newsDbObject.get("_id").toString();
				String titleWords = (String) newsDbObject.get("titleWords");
				String contentWords = (String) newsDbObject.get("contentWords");
				String docContent = titleWords + " " + contentWords;
				Document newDoc = new Document(docID, docContent);
				documentSet.docs.add(newDoc);
			}
		}
		mongo.close();
		
		for (Document document : documentSet.docs) {
			String docName = document.docName;
			ArrayList<String> words = document.words;
			if (words == null) {
				System.out.println("docName: " + docName);
				continue;
			}else {
			}
			for (String word : words) {
				if (wordDocNameSetMap.containsKey(word)) {
					Set<String> docNameSet = wordDocNameSetMap.get(word);
					docNameSet.add(docName);
					wordDocNameSetMap.put(word, docNameSet);
				}else {
					Set<String> docNameSet = new HashSet<>();
					docNameSet.add(docName);
					wordDocNameSetMap.put(word, docNameSet);
				}
			}
		}
//		System.out.println(wordDocNameSetMap);

		for (Entry<String, Set<String>> element : wordDocNameSetMap.entrySet()) {
			String key = element.getKey();
			double value = element.getValue().size();
			double idf = Math.log(documentSet.docs.size() / (value + 1));
			unsortedWordIDFMap.put(key, idf);
		}
//		System.out.println(unsortedWordRankMap);

		LinkedHashMap<String, Double> sortedWordIDFMap = new LinkedHashMap<String, Double>();
		List<Entry<String, Double>> rankList = new ArrayList<Entry<String, Double>>(unsortedWordIDFMap.entrySet());
		Collections.sort(rankList, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
				double result = o2.getValue() - o1.getValue();
				if (result > 0) {
					return 1;
				} else if (result < 0) {
					return -1;
				}
				return 0;
			}
		});
		Iterator<Entry<String, Double>> iterator = rankList.iterator();
		while (iterator.hasNext()) {
			Entry<String, Double> entry = iterator.next();
			String key = entry.getKey();
			Double value = entry.getValue();
			sortedWordIDFMap.put(key, value);
		}
		saveWordIDFMapToFile(sortedWordIDFMap);
		return sortedWordIDFMap;
	}
	
	private void saveWordIDFMapToFile(Map<String, Double> rankMap){
		String filePath = this.resultPath;
		String fileName = "wordIDFMap";
		ArrayList<String> lines = new ArrayList<String>();
		for (Entry<String, Double> termToIndex : rankMap.entrySet()) {
			String line = termToIndex.getKey()+":"+termToIndex.getValue();
			lines.add(line);
		}
		FileUtil.writeLines(filePath + fileName, lines);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
//		WordOptimizationByIDF wordOptimizationByIDF = new WordOptimizationByIDF();
//		Map<String, Double> wordRankMap = wordOptimizationByIDF.getWordIDFMap();
//		System.out.println(wordRankMap);
//		double averageSimilarity = getWordRankMap();
//		System.out.println(averageSimilarity);

	}

}
