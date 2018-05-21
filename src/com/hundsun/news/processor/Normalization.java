package com.hundsun.news.processor;

import java.net.UnknownHostException;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;


/**
 * Step 1
 * unify all field in the database <br/>
 * Optional: this is useful when field in database is not standard
 * 数据库中有时候会有不规则字段（本来应该是String 的，变成了integer）
 * @author tokyo
 */
public class Normalization {
	
	public Normalization(){}
	
	public void formatFiledType(DbInfo dbinfo, String filed){
		String dbName = dbinfo.getDbName();
		String collectionName = dbinfo.getTableName();
		String address = dbinfo.getAddress();
		Integer port = dbinfo.getPort();
		Mongo mongo = null;
		try {
			mongo = new Mongo(address, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		DB db = mongo.getDB(dbName);
		DBCollection collection = db.getCollection(collectionName);
		DBCursor cur = collection.find();
		int objectIndex = 0;
		while (cur.hasNext()) {
			objectIndex ++;
			if (objectIndex %1000 == 0) {
				System.out.println("Current is: " + objectIndex);
			}
			DBObject dbObject = cur.next();
			if (dbObject.containsField(filed)) {
				Object fieldObject = dbObject.get(filed);
				if (fieldObject.getClass() != String.class) {
					collection.remove(dbObject);
				}
				dbObject.put(filed, fieldObject.toString());
				collection.insert(dbObject);
			}
		}
		mongo.close();
		System.out.println("Done");

	}
}
