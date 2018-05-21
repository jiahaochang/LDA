package com.hundsun.news.recommender;


public class Doc {
	public String docID;
	public String InfoTitle;
	public String Content;
	double[] topicVector;
	
	public Doc(){
		//��ʼ��
		this.docID = null;	
		this.topicVector = null;	
	}
	
	/**
	 * ������ȡһƪ�ĵ�ʱ�õĹ��캯��
	 * @param docName
	 */
	public Doc(String docID, double[] topicVector){
		//��ʼ��
		this.docID = docID;	
		this.topicVector = topicVector;	
	}
	
	public Doc(String docID, String InfoTitle, String Content, double[] topicVector){
		//��ʼ��
		this.docID = docID;	
		this.topicVector = topicVector;
		this.InfoTitle = InfoTitle;
		this.Content = Content;
	}
	
	

}
