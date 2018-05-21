package com.hundsun.news.recommender;


public class Doc {
	public String docID;
	public String InfoTitle;
	public String Content;
	double[] topicVector;
	
	public Doc(){
		//初始化
		this.docID = null;	
		this.topicVector = null;	
	}
	
	/**
	 * 单独读取一篇文档时用的构造函数
	 * @param docName
	 */
	public Doc(String docID, double[] topicVector){
		//初始化
		this.docID = docID;	
		this.topicVector = topicVector;	
	}
	
	public Doc(String docID, String InfoTitle, String Content, double[] topicVector){
		//初始化
		this.docID = docID;	
		this.topicVector = topicVector;
		this.InfoTitle = InfoTitle;
		this.Content = Content;
	}
	
	

}
