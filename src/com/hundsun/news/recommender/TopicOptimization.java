package com.hundsun.news.recommender;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.hundsun.news.lda.FileUtil;





/**
 * 
 * @author tokyo
 * filter bad topic
 */
public class TopicOptimization {
	
	private String resultPath;
	private String phiFilePath;
	private String phiFileName;
	private String thetaFilePath;
	private String thetaFileName;
	
	public TopicOptimization(){}
	
	public TopicOptimization(String paramFileName){
		Properties prop = new Properties();
		try {
			FileInputStream fis = new FileInputStream(new File(paramFileName));
			prop.load(fis);
			this.resultPath = prop.getProperty("ldaResultsPath");
			this.phiFilePath = prop.getProperty("phiFilePath");
			this.phiFileName = prop.getProperty("phiFileName");
			this.thetaFilePath = prop.getProperty("thetaFilePath");
			this.thetaFileName = prop.getProperty("thetaFileName");
			
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Map<Integer, Double> getTopicRankMap() throws IOException {
		Map<Integer, Double> unsortedTopicRankMap = new HashMap<Integer, Double>();
		// ∂¡»°phi”ÎtermToIndexMap
		System.out.println("reading phi");
		Double[][] phi = FileUtil.readMatrixFromFile(phiFilePath, phiFileName);
		System.out.println("reading theta");
		Double[][] theta = FileUtil.readMatrixFromFile(thetaFilePath, thetaFileName);
		int topicNum = phi.length;
		int wordCount = phi[0].length;
		int docNum = theta.length;

		System.out.println("computing topicDocScore");
		Double[] noiseDocVector = new Double[docNum];
		for (int i = 0; i < noiseDocVector.length; i++) {
			noiseDocVector[i] = 1.0 / docNum;
		}

		double alpha = 1.0 / docNum;
		Double[][] topicDocMatrix = new Double[topicNum][docNum];
		for (int topicIndex = 0; topicIndex < topicNum; topicIndex++) {
			double topicProbInAll = 0.0;
			for (int docIndex = 0; docIndex < docNum; docIndex++) {
				topicProbInAll += theta[docIndex][topicIndex];
			}
			double bottom = topicProbInAll + docNum * alpha;
			for (int docIndex = 0; docIndex < docNum; docIndex++) {
				double top = theta[docIndex][topicIndex] + alpha;
				topicDocMatrix[topicIndex][docIndex] = top / bottom;
			}
		}

		double[] topicScoreVectorInDoc = new double[topicNum];
		for (int topicIndex = 0; topicIndex < topicScoreVectorInDoc.length; topicIndex++) {
			double topicScore = getCosDistance(topicDocMatrix[topicIndex], noiseDocVector);
			topicScoreVectorInDoc[topicIndex] = topicScore;
		}

		System.out.println("computing topicWordScore");
		Double[] noiseWordVector = new Double[wordCount];
		for (int i = 0; i < noiseWordVector.length; i++) {
			noiseWordVector[i] = 1.0 / wordCount;
		}

		double[] topicScoreVectorInWord = new double[topicNum];
		for (int topicIndex = 0; topicIndex < topicScoreVectorInWord.length; topicIndex++) {
			double topicScore = getCosDistance(phi[topicIndex], noiseWordVector);
			topicScoreVectorInWord[topicIndex] = topicScore;
		}

		double topicScoreSumInDoc = 0.0;
		double topicScoreSumInWord = 0.0;
		for (int topicIndex = 0; topicIndex < topicNum; topicIndex++) {
			topicScoreSumInDoc += topicScoreVectorInDoc[topicIndex];
			topicScoreSumInWord += topicScoreVectorInWord[topicIndex];
		}
		double wordScoreWeight = topicScoreSumInDoc / topicScoreSumInWord;

		for (int topicIndex = 0; topicIndex < topicNum; topicIndex++) {
			double value = topicScoreVectorInDoc[topicIndex] / wordScoreWeight + topicScoreVectorInWord[topicIndex];
			unsortedTopicRankMap.put(topicIndex, value/2);
		}
		LinkedHashMap<Integer, Double> sortedTopicRankMap = new LinkedHashMap<Integer, Double>();
		List<Entry<Integer, Double>> rankList = new ArrayList<Entry<Integer, Double>>(unsortedTopicRankMap.entrySet());
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
		while (iterator.hasNext()) {
			Entry<Integer, Double> entry = iterator.next();
			Integer key = entry.getKey();
			Double value = entry.getValue();
			sortedTopicRankMap.put(key, value);
		}
		this.saveMapToFile(sortedTopicRankMap, "topicRankScoreMap");
		return sortedTopicRankMap;
	}

	private double getCosDistance(Double[] topicVector1, Double[] topicVector2) {
		double similarity = 0.0;
		double fenzi = 0.0;
		double user1Fenmu = 0.0;
		double user2Fenmu = 0.0;
		for (int i = 0; i < topicVector1.length; i++) {
			double vector1Value = topicVector1[i];
			double vector2Value = topicVector2[i];

			fenzi = fenzi + vector1Value * vector2Value;

			user1Fenmu = user1Fenmu + Math.pow(vector1Value, 2);
			user2Fenmu = user2Fenmu + Math.pow(vector2Value, 2);
		}
		// ≈–∂œ
		if (user1Fenmu * user2Fenmu == 0) {
			similarity = 0;
		} else {
			similarity = fenzi / (Math.pow((user1Fenmu * user2Fenmu), 0.5));
		}
		//distance instead of similarity
		double distance = 1.0 - similarity;
		return distance;
	}
	
	public void saveMapToFile(Map<Integer, Double> rankMap, String fileName){
		String filePath = this.resultPath;
		ArrayList<String> lines = new ArrayList<String>();
		for (Entry<Integer, Double> termToIndex : rankMap.entrySet()) {
			String line = termToIndex.getKey()+":"+termToIndex.getValue();
			lines.add(line);
		}
		FileUtil.writeLines(filePath + fileName, lines);
	}
	
	/*
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		//Map<Integer, Double> topicRankMap = getTopicRankMap();
		//System.out.println(topicRankMap);
		
		
//		double averageSimilarity = getWordRankMap();
//		System.out.println(averageSimilarity);

	}*/

}
