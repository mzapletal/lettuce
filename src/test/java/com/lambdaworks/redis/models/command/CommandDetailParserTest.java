package com.lambdaworks.redis.models.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.lambdaworks.redis.internal.LettuceSets;
import org.junit.Test;

import com.lambdaworks.redis.internal.LettuceLists;

public class CommandDetailParserTest {

    @Test
    public void testMappings() throws Exception {
        assertThat(CommandDetailParser.FLAG_MAPPING).hasSameSizeAs(CommandDetail.Flag.values());
    }

    @Test
    public void testEmptyList() throws Exception {

        List<CommandDetail> result = CommandDetailParser.parse(LettuceLists.newList());
        assertThat(result).isEmpty();
    }

    @Test
    public void testMalformedList() throws Exception {
        Object o = LettuceLists.newList("", "", "");
        List<CommandDetail> result = CommandDetailParser.parse(LettuceLists.newList(o));
        assertThat(result).isEmpty();
    }

    @Test
    public void testParse() throws Exception {
        Object o = LettuceLists.newList("get", "1", LettuceLists.newList("fast", "loading"), 1L, 2L, 3L);
        List<CommandDetail> result = CommandDetailParser.parse(LettuceLists.newList(o));
        assertThat(result).hasSize(1);

        CommandDetail commandDetail = result.get(0);
        assertThat(commandDetail.getName()).isEqualTo("get");
        assertThat(commandDetail.getArity()).isEqualTo(1);
        assertThat(commandDetail.getFlags()).hasSize(2);
        assertThat(commandDetail.getFirstKeyPosition()).isEqualTo(1);
        assertThat(commandDetail.getLastKeyPosition()).isEqualTo(2);
        assertThat(commandDetail.getKeyStepCount()).isEqualTo(3);
    }

    @Test
    public void testModel() throws Exception {
        CommandDetail commandDetail = new CommandDetail();
        commandDetail.setArity(1);
        commandDetail.setFirstKeyPosition(2);
        commandDetail.setLastKeyPosition(3);
        commandDetail.setKeyStepCount(4);
        commandDetail.setName("theName");
        commandDetail.setFlags(LettuceSets.newHashSet());

        assertThat(commandDetail.toString()).contains(CommandDetail.class.getSimpleName());
    }
}
