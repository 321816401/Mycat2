package io.mycat.mycat2.sqlannotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.mycat2.MycatSession;
import io.mycat.mycat2.cmds.interceptor.SQLCachCmd;

public class SQLCach implements SQLAnnotation {

	public static final SQLCach INSTANCE = new SQLCach();
	
	private static final Logger logger = LoggerFactory.getLogger(SQLCach.class);
	
	/**
	 * 组装 mysqlCommand
	 */
	@Override
	public Boolean apply(MycatSession session) {
		session.getCmdChain().addCmdChain(this,SQLCachCmd.INSTANCE);
		return Boolean.TRUE;
	}

	@Override
	public void init(Object args) {

	}

	@Override
	public String getMethod() {
		return null;
	}

	@Override
	public void setMethod(String method) {

	}

}
