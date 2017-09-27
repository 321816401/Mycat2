package io.mycat.mycat2;

import java.io.IOException;

import io.mycat.mycat2.beans.conf.*;
import io.mycat.proxy.*;
import io.mycat.proxy.NIOAcceptor.ServerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.proxy.BufferPool;
import io.mycat.proxy.MycatReactorThread;
import io.mycat.proxy.NIOAcceptor;
import io.mycat.proxy.ProxyRuntime;
import io.mycat.proxy.man.AdminCommandResovler;
import io.mycat.proxy.man.ClusterNode;
import io.mycat.proxy.man.MyCluster;

public class ProxyStarter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyStarter.class);
	public static final ProxyStarter INSTANCE = new ProxyStarter();

	/**
	 * 用于初始化启动
	 * @throws IOException
	 */
	public void start() throws IOException {
		ProxyRuntime runtime = ProxyRuntime.INSTANCE;
		MycatConfig conf = runtime.getConfig();

		// 启动NIO Acceptor
		NIOAcceptor acceptor = new NIOAcceptor(new BufferPool(1024 * 10));
		acceptor.start();
		runtime.setAcceptor(acceptor);

		ClusterConfig clusterConfig = conf.getConfig(ConfigEnum.CLUSTER);
		ClusterBean clusterBean = clusterConfig.getCluster();
		if (clusterBean.isEnable()) {
			// 启动集群
			startCluster(runtime, clusterBean, acceptor);
		} else {
			// 未配置集群，直接启动
			startProxy(null);
		}
	}

	/**
	 * 集群模式下启动先启动admin对应的端口，等集群建立成功后才加载配置启动proxy
	 * @param runtime
	 * @param clusterBean
	 * @param acceptor
	 * @throws IOException
	 */
	private void startCluster(ProxyRuntime runtime, ClusterBean clusterBean, NIOAcceptor acceptor) throws IOException {
		// 集群开启状态，需要等集群启动，主节点确认完配置才能提供服务
		acceptor.startServerChannel(clusterBean.getIp(), clusterBean.getPort(), ServerType.CLUSTER);
		runtime.setAdminCmdResolver(new AdminCommandResovler());
		MyCluster cluster = new MyCluster(acceptor.getSelector(), clusterBean.getMyNodeId(), ClusterNode.parseNodesInf(clusterBean.getAllNodes()));
		runtime.setMyCLuster(cluster);
		cluster.initCluster();
	}

	/**
	 * 启动代理
	 * @param isLeader null 非集群节点，true 集群中的主节点，false 集群中的从节点
	 * @throws IOException
	 */
	public void startProxy(Boolean isLeader) throws IOException {
		ProxyRuntime runtime = ProxyRuntime.INSTANCE;
		MycatConfig conf = runtime.getConfig();
		NIOAcceptor acceptor = runtime.getAcceptor();

		startMycatServer(runtime,conf,acceptor,isLeader);

		BalancerConfig balancerConfig = conf.getConfig(ConfigEnum.BALANCER);
		BalancerBean balancerBean = balancerConfig.getBalancer();
        if (balancerBean.isEnable() || isLeader.booleanValue() == true){
            //开启负载均衡服务
            acceptor.startServerChannel(balancerBean.getIp(), balancerBean.getPort(), ServerType.LOAD_BALANCER);
        }
	}

	public void startMycatServer(ProxyRuntime runtime,MycatConfig conf,NIOAcceptor acceptor,Boolean isLeader) throws IOException{
		ProxyConfig proxyConfig = conf.getConfig(ConfigEnum.PROXY);
		ProxyBean proxyBean = proxyConfig.getProxy();
		if(acceptor.startServerChannel(proxyBean.getIp(), proxyBean.getPort(), ServerType.MYCAT)){
			startReactor();

			// 加载配置文件信息
			ConfigLoader.INSTANCE.loadAll();

			// 初始化连接
			conf.getMysqlRepMap().forEach((repName, repBean) -> {
				repBean.initMaster();
				repBean.getMetaBeans().forEach(metaBean -> {
					try {
						metaBean.init(repBean,ProxyRuntime.INSTANCE.maxdataSourceInitTime,repBean.getDataSourceInitStatus());
					} catch (IOException e) {
						LOGGER.error("error to init metaBean: {}", metaBean.getDsMetaBean().getHostName());
					}
				});
			});
		}
		// 主节点才启动心跳，非集群下也启动心跳
		if (isLeader == null || isLeader.booleanValue() == true) {
			runtime.startHeartBeatScheduler();
		}
	}

	public void stopProxy() {
		ProxyRuntime runtime = ProxyRuntime.INSTANCE;
		NIOAcceptor acceptor = runtime.getAcceptor();
		acceptor.stopServerChannel(false);
		//todo 关闭所有前后端连接？

		runtime.stopHeartBeatScheduler();
	}

	private void startReactor() throws IOException {
		// Mycat 2.0 Session Manager
		MycatReactorThread[] nioThreads = (MycatReactorThread[]) MycatRuntime.INSTANCE.getReactorThreads();
		int cpus = nioThreads.length;
		for (int i = 0; i < cpus; i++) {
			MycatReactorThread thread = new MycatReactorThread(new BufferPool(1024 * 10));
			thread.setName("NIO_Thread " + (i + 1));
			thread.start();
			nioThreads[i] = thread;
		}
	}
}
