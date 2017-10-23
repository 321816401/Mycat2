package io.mycat.mycat2.sqlannotations.blackList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.mycat2.MycatSession;
import io.mycat.mycat2.cmds.interceptor.SQLAnnotationChain;
import io.mycat.mycat2.cmds.interceptor.SQLAnnotationCmd;
import io.mycat.mycat2.sqlannotations.SQLAnnotation;
import io.mycat.mycat2.sqlparser.BufferSQLContext;

/**
 * Created by yanjunli on 2017/9/24.
 */
public class TruncateAllow extends SQLAnnotation{
	
	private static final Logger logger = LoggerFactory.getLogger(TruncateAllow.class);
		
    public TruncateAllow() {
    	logger.debug("=>TruncateAllow 对象本身的构造 初始化");
    }

    @Override
    public void init(Object args) {
        logger.debug("=>TruncateAllow 动态注解初始化。 "+args);
        BlackListMeta meta = new BlackListMeta();
        meta.setAllow((boolean)args);
        setSqlAnnoMeta(meta);
    }

    @Override
    public boolean apply(MycatSession context,SQLAnnotationChain chain) {
    	BlackListMeta meta = (BlackListMeta) getSqlAnnoMeta();
    	if(!meta.isAllow()&&
    			(BufferSQLContext.TRUNCATE_SQL == context.sqlContext.getSQLType())){
    		
    		SQLAnnotationCmd blockSqlCmd =  getSqlAnnoMeta().getSQLAnnotationCmd();
    		blockSqlCmd.setSqlAnnotationChain(chain);
    		blockSqlCmd.setErrMsg("truncate not allow ");
    		chain.addCmdChain(this,blockSqlCmd);
    		return false;
    	}
        return true;
    }
}
