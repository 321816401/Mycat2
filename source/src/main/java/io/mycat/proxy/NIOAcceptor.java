package io.mycat.proxy;

/**
 * NIO Acceptor ,只用来接受新连接的请求，也负责处理管理端口的报文
 * @author wuzhihui
 *
 */
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import io.mycat.mycat2.loadbalance.LoadChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.proxy.man.AdminSession;
import io.mycat.proxy.man.DefaultAdminSessionManager;

public class NIOAcceptor extends ProxyReactorThread<Session> {
	private final static Logger logger = LoggerFactory.getLogger(NIOAcceptor.class);
	protected SessionManager<AdminSession> adminSessionMan;

	private ServerSocketChannel proxyServerSocketChannel;
	private ServerSocketChannel clusterServerSocketChannel;
	private ServerSocketChannel loadBalanceServerSocketChannel;

	public NIOAcceptor(BufferPool bufferPool) throws IOException {
		super(bufferPool);
		this.setName("NIO-Acceptor");
	}

	public void startServerChannel(String ip, int port,ServerType serverType)throws IOException {
		final ServerSocketChannel serverChannel = getServerSocketChannel(serverType);
		if (serverChannel != null && serverChannel.isOpen())
			return;

		if (serverType == ServerType.CLUSTER) {
			adminSessionMan = new DefaultAdminSessionManager();
			ProxyRuntime.INSTANCE.setAdminSessionManager(adminSessionMan);
			logger.info("opend cluster conmunite port on {}:{}", ip, port);
		}

		if(serverType == ServerType.LOAD_BALANCER){
			logger.info("opend load balance conmunite port on {}:{}", ip, port);
		}

		openServerChannel(selector, ip, port, serverType);
	}

	private ServerSocketChannel getServerSocketChannel(ServerType serverType){
		switch (serverType) {
		case MYCAT:
			return proxyServerSocketChannel;
		case CLUSTER:
			return clusterServerSocketChannel;
		case LOAD_BALANCER:
			return loadBalanceServerSocketChannel;
		default:
			throw new IllegalArgumentException("wrong server type.");
		}
	}

	public void stopServerChannel(boolean clusterServer) {
		ServerSocketChannel socketChannel = clusterServer ? clusterServerSocketChannel : proxyServerSocketChannel;
		if (socketChannel != null && socketChannel.isOpen()) {
			logger.warn("ServerSocketChannel close, {}", socketChannel);
			try {
				socketChannel.close();
			} catch (IOException e) {
				logger.warn("ServerSocketChannel close error, {}", socketChannel);
			}
		}
	}

	protected void processAcceptKey(ReactorEnv reactorEnv, SelectionKey curKey) throws IOException {
		ServerSocketChannel serverSocket = (ServerSocketChannel) curKey.channel();
		// 接收通道，设置为非阻塞模式
		final SocketChannel socketChannel = serverSocket.accept();
		socketChannel.configureBlocking(false);
		logger.info("new Client connected: " + socketChannel);
		ServerType serverType = (ServerType) curKey.attachment();
		ProxyRuntime proxyRuntime = ProxyRuntime.INSTANCE;
		// 获取附着的标识，即得到当前是否为集群通信端口
		if (serverType == ServerType.CLUSTER) {
			adminSessionMan.createSession(null, this.bufPool, selector, socketChannel, true);
		} else if(serverType == ServerType.LOAD_BALANCER){
			LoadChecker localLoadChecker = proxyRuntime.getLocalLoadChecker();
			//本地未超载,派发到本地
			if(!localLoadChecker.isOverLoad(null)){
				logger.debug("load balancer accepted. Dispatch to local");
				accept(reactorEnv,socketChannel,serverType);
			} else {
				logger.debug("load balancer accepted. Dispatch to remote");
				//TODO 派发至远程
			}
		}
		else {
			accept(reactorEnv,socketChannel,serverType);
		}
	}

	public void accept(ReactorEnv reactorEnv,SocketChannel socketChannel,ServerType serverType) throws IOException {
		// 找到一个可用的NIO Reactor Thread，交付托管
		if (reactorEnv.counter++ == Integer.MAX_VALUE) {
			reactorEnv.counter = 1;
		}
		int index = reactorEnv.counter % ProxyRuntime.INSTANCE.getNioReactorThreads();
		// 获取一个reactor对象
		ProxyReactorThread<?> nioReactor = ProxyRuntime.INSTANCE.getReactorThreads()[index];
		// 将通道注册到reactor对象上
		nioReactor.acceptNewSocketChannel(serverType, socketChannel);
	}

	@SuppressWarnings("unchecked")
	protected void processConnectKey(ReactorEnv reactorEnv, SelectionKey curKey) throws IOException {
		// only from cluster server socket
		SocketChannel curChannel = (SocketChannel) curKey.channel();
		try {
			if (curChannel.finishConnect()) {
				AdminSession session = adminSessionMan.createSession(curKey.attachment(), this.bufPool, selector,
						curChannel, false);
				NIOHandler<AdminSession> connectIOHandler = (NIOHandler<AdminSession>) session
						.getCurNIOHandler();
				connectIOHandler.onConnect(curKey, session, true, null);
			}

		} catch (ConnectException ex) {
			logger.warn("connect failed " + curChannel + " reason:" + ex);
			if (adminSessionMan.getDefaultSessionHandler() instanceof NIOHandler) {
				NIOHandler<AdminSession> connectIOHandler = (NIOHandler<AdminSession>) adminSessionMan
						.getDefaultSessionHandler();
				connectIOHandler.onConnect(curKey, null, false, null);

			}

		}
	}

	@SuppressWarnings("unchecked")
	protected void processReadKey(ReactorEnv reactorEnv, SelectionKey curKey) throws IOException {
		// only from cluster server socket
		Session session = (Session) curKey.attachment();
		session.getCurNIOHandler().onSocketRead(session);
	}

	@SuppressWarnings("unchecked")
	protected void processWriteKey(ReactorEnv reactorEnv, SelectionKey curKey) throws IOException {
		// only from cluster server socket
		Session session = (Session) curKey.attachment();
		session.getCurNIOHandler().onSocketWrite(session);
	}

	private void openServerChannel(Selector selector, String bindIp, int bindPort, ServerType serverType)
			throws IOException {
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		final InetSocketAddress isa = new InetSocketAddress(bindIp, bindPort);
		serverChannel.bind(isa);
		serverChannel.configureBlocking(false);
		serverChannel.register(selector, SelectionKey.OP_ACCEPT, serverType);
		if (serverType == ServerType.CLUSTER) {
			clusterServerSocketChannel = serverChannel;
		} else if (serverType == ServerType.LOAD_BALANCER) {
			loadBalanceServerSocketChannel = serverChannel;
		} else {
			proxyServerSocketChannel = serverChannel;
		}
	}

	public Selector getSelector() {
		return this.selector;
	}

	public enum ServerType {
		CLUSTER, LOAD_BALANCER, MYCAT;
	}
}
