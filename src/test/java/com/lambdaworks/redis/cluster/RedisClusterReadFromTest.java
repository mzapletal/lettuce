package com.lambdaworks.redis.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.ReadFrom;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.common.collect.ImmutableList;
import com.lambdaworks.redis.FastShutdown;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.api.StatefulRedisClusterConnection;
import com.lambdaworks.redis.cluster.api.sync.RedisAdvancedClusterCommands;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings("unchecked")
public class RedisClusterReadFromTest extends AbstractClusterTest {

    protected RedisAdvancedClusterCommands<String, String> sync;
    protected StatefulRedisClusterConnection<String, String> connection;

    @BeforeClass
    public static void setupClient() throws Exception {
        setupClusterClient();
        clusterClient = new RedisClusterClient(ImmutableList.of(RedisURI.Builder.redis(host, port1).build()));
    }

    @AfterClass
    public static void shutdownClient() {
        shutdownClusterClient();
        FastShutdown.shutdown(clusterClient);
    }

    @Before
    public void before() throws Exception {
        clusterRule.getClusterClient().reloadPartitions();
        connection = clusterClient.connect();
        sync = connection.sync();
    }

    @After
    public void after() throws Exception {
        sync.close();
    }

    @Test
    public void defaultTest() throws Exception {
        assertThat(connection.getReadFrom()).isEqualTo(ReadFrom.MASTER);
    }

    @Test
    public void readWriteMaster() throws Exception {
        connection.setReadFrom(ReadFrom.MASTER);
        sync.set(key, value);
        assertThat(sync.get(key)).isEqualTo(value);
    }

    @Test
    public void readWriteMasterPreferred() throws Exception {
        connection.setReadFrom(ReadFrom.MASTER_PREFERRED);
        sync.set(key, value);
        assertThat(sync.get(key)).isEqualTo(value);
    }

    @Test
    public void readWriteSlave() throws Exception {
        connection.setReadFrom(ReadFrom.SLAVE);

        sync.set(key, "value1");

        connection.getConnection(host, port2).sync().waitForReplication(1, 1000);
        assertThat(sync.get(key)).isEqualTo("value1");
    }

    @Test
    public void readWriteNearest() throws Exception {
        connection.setReadFrom(ReadFrom.NEAREST);

        sync.set(key, "value1");

        connection.getConnection(host, port2).sync().waitForReplication(1, 1000);
        assertThat(sync.get(key)).isEqualTo("value1");
    }
}
