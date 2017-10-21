package io.mycat.mycat2.sqlannotations.blackList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.mycat2.MycatSession;
import io.mycat.mycat2.cmds.interceptor.BlockSqlCmd;
import io.mycat.mycat2.cmds.interceptor.SQLAnnotationChain;
import io.mycat.mycat2.sqlannotations.SQLAnnotation;
import io.mycat.mycat2.sqlparser.BufferSQLContext;

/**
 * Created by yanjunli on 2017/9/24.
 */
public class InsertAllow extends SQLAnnotation{
	
	private static final Logger logger = LoggerFactory.getLogger(InsertAllow.class);
			
    Object args;
    public InsertAllow() {
    	logger.debug("=>InsertAllow 对象本身的构造 初始化");
    }

    @Override
    public void init(Object args) {
        logger.debug("=>InsertAllow 动态注解初始化。 "+args);
        this.args=args;
    }

    @Override
    public boolean apply(MycatSession context,SQLAnnotationChain chain) {
    	if(!(boolean)args&&
    			(BufferSQLContext.INSERT_SQL == context.sqlContext.getSQLType())){
    		chain.setErrMsg("insert not allow ");
    		chain.addCmdChain(this,BlockSqlCmd.INSTANCE);
    		return false;
    	}
        return true;
    }
}
