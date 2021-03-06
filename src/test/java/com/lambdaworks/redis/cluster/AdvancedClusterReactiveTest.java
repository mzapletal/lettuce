package com.lambdaworks.redis.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.lambdaworks.redis.*;
import com.lambdaworks.redis.cluster.api.sync.RedisAdvancedClusterCommands;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import rx.Observable;
import rx.exceptions.CompositeException;

import com.google.common.collect.Lists;
import com.lambdaworks.RandomKeys;
import com.lambdaworks.redis.api.sync.RedisCommands;
import com.lambdaworks.redis.cluster.api.rx.RedisAdvancedClusterReactiveCommands;
import com.lambdaworks.redis.cluster.api.rx.RedisClusterReactiveCommands;
import com.lambdaworks.redis.cluster.api.sync.RedisClusterCommands;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.commands.rx.RxSyncInvocationHandler;

/**
 * @author Mark Paluch
 */
public class AdvancedClusterReactiveTest extends AbstractClusterTest {

    public static final String KEY_ON_NODE_1 = "a";
    public static final String KEY_ON_NODE_2 = "b";

    private RedisAdvancedClusterReactiveCommands<String, String> commands;
    private RedisCommands<String, String> syncCommands;

    @Before
    public void before() throws Exception {
        commands = clusterClient.connectClusterAsync().getStatefulConnection().reactive();
        syncCommands = RxSyncInvocationHandler.sync(commands.getStatefulConnection());
    }

    @After
    public void after() throws Exception {
        commands.close();
    }

    @Test(expected = RedisException.class)
    public void unknownNodeId() throws Exception {

        commands.getConnection("unknown");
    }

    @Test(expected = RedisException.class)
    public void invalidHost() throws Exception {
        commands.getConnection("invalid-host", -1);
    }

    @Test
    public void doWeirdThingsWithClusterconnections() throws Exception {

        assertThat(clusterClient.getPartitions()).hasSize(4);

        for (RedisClusterNode redisClusterNode : clusterClient.getPartitions()) {
            RedisClusterReactiveCommands<String, String> nodeConnection = commands.getConnection(redisClusterNode.getNodeId());

            nodeConnection.close();

            RedisClusterReactiveCommands<String, String> nextConnection = commands.getConnection(redisClusterNode.getNodeId());
            assertThat(commands).isNotSameAs(nextConnection);
        }
    }

    @Test
    public void msetCrossSlot() throws Exception {

        Observable<String> mset = commands.mset(RandomKeys.MAP);
        List<String> result = Lists.newArrayList(mset.toBlocking().toIterable());
        assertThat(result).hasSize(1).contains("OK");

        for (String mykey : RandomKeys.KEYS) {
            String s1 = syncCommands.get(mykey);
            assertThat(s1).isEqualTo(RandomKeys.MAP.get(mykey));
        }
    }

    @Test
    public void msetnxCrossSlot() throws Exception {

        List<Boolean> result = Lists.newArrayList(commands.msetnx(RandomKeys.MAP).toBlocking().toIterable());

        assertThat(result).hasSize(1).contains(true);

        for (String mykey : RandomKeys.KEYS) {
            String s1 = syncCommands.get(mykey);
            assertThat(s1).isEqualTo(RandomKeys.MAP.get(mykey));
        }
    }

    @Test
    public void mgetCrossSlot() throws Exception {

        msetCrossSlot();

        Map<Integer, List<String>> partitioned = SlotHash.partition(new Utf8StringCodec(), RandomKeys.KEYS);
        assertThat(partitioned.size()).isGreaterThan(100);

        Observable<String> observable = commands.mget(RandomKeys.KEYS.toArray(new String[RandomKeys.COUNT]));
        List<String> result = observable.toList().toBlocking().single();

        assertThat(result).hasSize(RandomKeys.COUNT);
        assertThat(result).isEqualTo(RandomKeys.VALUES);
    }

    @Test
    public void mgetCrossSlotStreaming() throws Exception {

        msetCrossSlot();

        ListStreamingAdapter<String> result = new ListStreamingAdapter<>();

        Observable<Long> observable = commands.mget(result, RandomKeys.KEYS.toArray(new String[RandomKeys.COUNT]));
        Long count = getSingle(observable);

        assertThat(result.getList()).hasSize(RandomKeys.COUNT);
        assertThat(count).isEqualTo(RandomKeys.COUNT);
    }

    @Test
    public void delCrossSlot() throws Exception {

        msetCrossSlot();

        Observable<Long> observable = commands.del(RandomKeys.KEYS.toArray(new String[RandomKeys.COUNT]));
        Long result = getSingle(observable);

        assertThat(result).isEqualTo(RandomKeys.COUNT);

        for (String mykey : RandomKeys.KEYS) {
            String s1 = syncCommands.get(mykey);
            assertThat(s1).isNull();
        }
    }

    @Test
    public void unlinkCrossSlot() throws Exception {

        msetCrossSlot();

        Observable<Long> observable = commands.unlink(RandomKeys.KEYS.toArray(new String[RandomKeys.COUNT]));
        Long result = getSingle(observable);

        assertThat(result).isEqualTo(RandomKeys.COUNT);

        for (String mykey : RandomKeys.KEYS) {
            String s1 = syncCommands.get(mykey);
            assertThat(s1).isNull();
        }
    }

    @Test
    public void clientSetname() throws Exception {

        String name = "test-cluster-client";

        assertThat(clusterClient.getPartitions().size()).isGreaterThan(0);

        getSingle(commands.clientSetname(name));

        for (RedisClusterNode redisClusterNode : clusterClient.getPartitions()) {
            RedisClusterCommands<String, String> nodeConnection = commands.getStatefulConnection().sync()
                    .getConnection(redisClusterNode.getNodeId());
            assertThat(nodeConnection.clientList()).contains(name);
        }
    }

    @Test(expected = Exception.class)
    public void clientSetnameRunOnError() throws Exception {
        getSingle(commands.clientSetname("not allowed"));
    }

    @Test
    public void dbSize() throws Exception {

        writeKeysToTwoNodes();

        Long dbsize = getSingle(commands.dbsize());
        assertThat(dbsize).isEqualTo(2);
    }

    @Test
    public void flushall() throws Exception {

        writeKeysToTwoNodes();

        assertThat(getSingle(commands.flushall())).isEqualTo("OK");

        Long dbsize = syncCommands.dbsize();
        assertThat(dbsize).isEqualTo(0);
    }

    @Test
    public void flushdb() throws Exception {

        writeKeysToTwoNodes();

        assertThat(getSingle(commands.flushdb())).isEqualTo("OK");

        Long dbsize = syncCommands.dbsize();
        assertThat(dbsize).isEqualTo(0);
    }

    @Test
    public void keys() throws Exception {

        writeKeysToTwoNodes();

        List<String> result = commands.keys("*").toList().toBlocking().single();

        assertThat(result).contains(KEY_ON_NODE_1, KEY_ON_NODE_2);
    }

    @Test
    public void keysStreaming() throws Exception {

        writeKeysToTwoNodes();
        ListStreamingAdapter<String> result = new ListStreamingAdapter<>();

        assertThat(getSingle(commands.keys(result, "*"))).isEqualTo(2);
        assertThat(result.getList()).contains(KEY_ON_NODE_1, KEY_ON_NODE_2);
    }

    @Test
    public void randomKey() throws Exception {

        writeKeysToTwoNodes();

        assertThat(getSingle(commands.randomkey())).isIn(KEY_ON_NODE_1, KEY_ON_NODE_2);
    }

    @Test
    public void scriptFlush() throws Exception {
        assertThat(getSingle(commands.scriptFlush())).isEqualTo("OK");
    }

    @Test
    public void scriptKill() throws Exception {
        assertThat(getSingle(commands.scriptKill())).isEqualTo("OK");
    }

    @Test
    @Ignore("Run me manually, I will shutdown all your cluster nodes so you need to restart the Redis Cluster after this test")
    public void shutdown() throws Exception {
        commands.shutdown(true).subscribe();
    }

    @Test
    public void readFromSlaves() throws Exception {

        RedisClusterReactiveCommands<String, String> connection = commands.getConnection(host, port4);
        connection.readOnly().toBlocking().first();
        commands.set(key, value).toBlocking().first();
        NodeSelectionAsyncTest.waitForReplication(commands.getStatefulConnection().async(), key, port4);

        AtomicBoolean error = new AtomicBoolean();
        connection.get(key).doOnError(throwable -> error.set(true)).toBlocking().toFuture().get();

        assertThat(error.get()).isFalse();

        connection.readWrite().toBlocking().first();

        try {
            connection.get(key).doOnError(throwable -> error.set(true)).toBlocking().first();
            fail("Missing exception");
        } catch (Exception e) {
            assertThat(error.get()).isTrue();
        }
    }

    @Test
    public void clusterScan() throws Exception {

        RedisAdvancedClusterCommands<String, String> sync = commands.getStatefulConnection().sync();
        sync.mset(RandomKeys.MAP);

        Set<String> allKeys = Sets.newHashSet();

        KeyScanCursor<String> scanCursor = null;
        do {

            if (scanCursor == null) {
                scanCursor = getSingle(commands.scan());
            } else {
                scanCursor = getSingle(commands.scan(scanCursor));
            }
            allKeys.addAll(scanCursor.getKeys());
        } while (!scanCursor.isFinished());

        assertThat(allKeys).containsAll(RandomKeys.KEYS);

    }

    @Test
    public void clusterScanWithArgs() throws Exception {

        RedisAdvancedClusterCommands<String, String> sync = commands.getStatefulConnection().sync();
        sync.mset(RandomKeys.MAP);

        Set<String> allKeys = Sets.newHashSet();

        KeyScanCursor<String> scanCursor = null;
        do {

            if (scanCursor == null) {
                scanCursor = getSingle(commands.scan(ScanArgs.Builder.matches("a*")));
            } else {
                scanCursor = getSingle(commands.scan(scanCursor, ScanArgs.Builder.matches("a*")));
            }
            allKeys.addAll(scanCursor.getKeys());
        } while (!scanCursor.isFinished());

        assertThat(allKeys).containsAll(RandomKeys.KEYS.stream().filter(k -> k.startsWith("a")).collect(Collectors.toList()));

    }

    @Test
    public void clusterScanStreaming() throws Exception {

        RedisAdvancedClusterCommands<String, String> sync = commands.getStatefulConnection().sync();
        sync.mset(RandomKeys.MAP);

        ListStreamingAdapter<String> adapter = new ListStreamingAdapter<>();

        StreamScanCursor scanCursor = null;
        do {

            if (scanCursor == null) {
                scanCursor = getSingle(commands.scan(adapter));
            } else {
                scanCursor = getSingle(commands.scan(adapter, scanCursor));
            }
        } while (!scanCursor.isFinished());

        assertThat(adapter.getList()).containsAll(RandomKeys.KEYS);

    }

    @Test
    public void clusterScanStreamingWithArgs() throws Exception {

        RedisAdvancedClusterCommands<String, String> sync = commands.getStatefulConnection().sync();
        sync.mset(RandomKeys.MAP);

        ListStreamingAdapter<String> adapter = new ListStreamingAdapter<>();

        StreamScanCursor scanCursor = null;
        do {

            if (scanCursor == null) {
                scanCursor = getSingle(commands.scan(adapter, ScanArgs.Builder.matches("a*")));
            } else {
                scanCursor = getSingle(commands.scan(adapter, scanCursor, ScanArgs.Builder.matches("a*")));
            }
        } while (!scanCursor.isFinished());

        assertThat(adapter.getList()).containsAll(
                RandomKeys.KEYS.stream().filter(k -> k.startsWith("a")).collect(Collectors.toList()));

    }

    private <T> T getSingle(Observable<T> observable) {
        return observable.toBlocking().single();
    }

    private void writeKeysToTwoNodes() {
        syncCommands.set(KEY_ON_NODE_1, value);
        syncCommands.set(KEY_ON_NODE_2, value);
    }
}
