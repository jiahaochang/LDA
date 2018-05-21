/**
 * 
 */
package com.hundsun.news.lda;

import java.util.ArrayList;

import com.hundsun.news.topicmodel.Filter;
import com.hundsun.news.topicmodel.FilterRule;



/**
 * @author rivercrab3
 *
 */
public class LdaFilter extends Filter {

	/* (non-Javadoc)
	 * @see com.xiang.topicmodel.framework.Filter#filter(java.util.ArrayList)
	 */
	private FilterRule rule;
	
	/**
	 * construct a new rule
	 * @param rule
	 */
	public LdaFilter(FilterRule rule){
		this.rule = rule;
	}
	
	@Override
	public void filter(ArrayList data) {
		// TODO Auto-generated method stub
		Integer docNum = data.size();
		for(int i = 0; i < docNum; ++i){
			ArrayList<String> words = (ArrayList<String>) data.get(i);
			ArrayList<String> fitWords = new ArrayList<String>();
			for(String word: words){
				if(this.rule.isFit(word)){
					fitWords.add(word);
				}
			}
			data.set(i,fitWords);
		}
	}

}
