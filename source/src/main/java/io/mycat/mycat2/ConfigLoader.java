package io.mycat.mycat2;

import io.mycat.mycat2.beans.conf.DatasourceConfig;
import io.mycat.mycat2.beans.conf.ReplicaIndexConfig;
import io.mycat.mycat2.beans.conf.SchemaConfig;
import io.mycat.proxy.ConfigEnum;
import io.mycat.proxy.Configurable;
import io.mycat.proxy.ProxyRuntime;
import io.mycat.util.YamlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Desc:
 *
 * @date: 13/09/2017
 * @author: gaozhiwen
 */
public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    public static final ConfigLoader INSTANCE = new ConfigLoader();

    public static final String DIR_CONF = "conf" + File.separator;
    public static final String DIR_PREPARE = "prepare" + File.separator;
    public static final String DIR_ARCHIVE = "archive" + File.separator;

    private ConfigLoader() {}

    public void loadAll() throws IOException {
        // 保证文件夹存在
        YamlUtil.createDirectoryIfNotExists(DIR_PREPARE);
        YamlUtil.createDirectoryIfNotExists(DIR_ARCHIVE);

        loadConfig(false, ConfigEnum.REPLICA_INDEX, null);
        loadConfig(false, ConfigEnum.DATASOURCE, null);
        loadConfig(false, ConfigEnum.SCHEMA, null);

        // 清空prepare文件夹
        YamlUtil.clearDirectory(DIR_PREPARE, null);
    }

    public void load(ConfigEnum configEnum, Integer targetVersion) throws IOException {
        loadConfig(true, configEnum, targetVersion);
        YamlUtil.clearDirectory(DIR_PREPARE, configEnum.getFileName());
    }

    /**
     * 加载指定配置文件
     * @param needAchive 是否需要归档
     * @param configEnum 配置文件的枚举
     * @param targetVersion 指定的版本号
     * @throws IOException
     */
    private void loadConfig(boolean needAchive, ConfigEnum configEnum, Integer targetVersion) throws IOException {
        // 加载replica-index
        String fileName = configEnum.getFileName();
        MycatConfig conf = ProxyRuntime.INSTANCE.getConfig();

        Integer curVersion = conf.getConfigVersion(configEnum);
        if (needAchive) {
            Integer repVersion = YamlUtil.archive(fileName, curVersion, targetVersion);
            if (repVersion == null) {
                return;
            }
            curVersion = repVersion;
        }

        LOGGER.debug("load config for {}", configEnum);
        conf.putConfig(configEnum, (Configurable) YamlUtil.load(fileName, configEnum.getClazz()), curVersion);
    }
}
