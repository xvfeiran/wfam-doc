package com.bosch.cn.em.mfd.core.config;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "custom.smb.enabled", havingValue = "true", matchIfMissing = false)
public class SmbjConfig {

    @Value("${custom.smb.user}")
    private String user;

    @Value("${custom.smb.password}")
    private String password;

    @Value("${custom.smb.domain}")
    private String domain;

    @Value("${custom.smb.host}")
    private String host;

    @Value("${custom.smb.share-name}")
    private String shareName;

    @Bean
    public GenericObjectPool<DiskShare> diskSharePool() {
        GenericObjectPoolConfig<DiskShare> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(10); // Maximum number of connections in the pool
        poolConfig.setMinIdle(0);   // Minimum number of idle connections
        poolConfig.setMaxIdle(5);   // Maximum number of idle connections
        poolConfig.setMaxWait(Duration.ofMillis(30000)); // Maximum wait time for a connection (30 seconds)
        poolConfig.setTestOnBorrow(true);   // Validate connections before borrowing
        poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(60000)); // Soft minimum idle time before considering eviction
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(30000)); // Time between runs of the idle object evictor thread (30 seconds)
        return new GenericObjectPool<>(new DiskShareFactory(), poolConfig);
    }

    @PreDestroy
    public void closeSmbClient() {
        Optional.ofNullable(diskSharePool()).ifPresent(GenericObjectPool::close);
    }

    private class DiskShareFactory implements PooledObjectFactory<DiskShare> {

        @Override
        public PooledObject<DiskShare> makeObject() throws Exception {
            SMBClient smbClient = new SMBClient(SmbConfig.builder().withEncryptData(true).build()); // NOSONAR
            Connection connection = smbClient.connect(host);
            AuthenticationContext authenticationContext = new AuthenticationContext(user, password.toCharArray(), domain);
            Session session = connection.authenticate(authenticationContext);
            DiskShare diskShare = (DiskShare) session.connectShare(shareName);
            return new DefaultPooledObject<>(diskShare);
        }

        @Override
        public void destroyObject(PooledObject<DiskShare> p) throws Exception {
            log.debug("Destroying DiskShare object");
            DiskShare diskShare = p.getObject();
            if (diskShare != null) {
                diskShare.close();
            }
        }

        @Override
        public boolean validateObject(PooledObject<DiskShare> p) {
            log.debug("Validating DiskShare object");
            DiskShare diskShare = p.getObject();
            try {
                // Use a method that verifies the connection is still valid
                diskShare.list("");
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<DiskShare> p) throws Exception {
            // No-op
        }

        @Override
        public void passivateObject(PooledObject<DiskShare> p) throws Exception {
            // No-op
        }
    }
}
