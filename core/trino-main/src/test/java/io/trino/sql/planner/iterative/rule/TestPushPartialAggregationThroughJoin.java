/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.sql.planner.assertions.PlanMatchPattern;
import io.trino.sql.planner.iterative.rule.test.BaseRuleTest;
import io.trino.sql.planner.iterative.rule.test.PlanBuilder;
import io.trino.sql.planner.plan.JoinNode.EquiJoinClause;
import io.trino.sql.tree.SymbolReference;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.SystemSessionProperties.PUSH_PARTIAL_AGGREGATION_THROUGH_JOIN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.sql.planner.assertions.PlanMatchPattern.aggregation;
import static io.trino.sql.planner.assertions.PlanMatchPattern.aggregationFunction;
import static io.trino.sql.planner.assertions.PlanMatchPattern.join;
import static io.trino.sql.planner.assertions.PlanMatchPattern.project;
import static io.trino.sql.planner.assertions.PlanMatchPattern.singleGroupingSet;
import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.planner.iterative.rule.test.PlanBuilder.expression;
import static io.trino.sql.planner.plan.AggregationNode.Step.PARTIAL;
import static io.trino.sql.planner.plan.JoinType.INNER;

public class TestPushPartialAggregationThroughJoin
        extends BaseRuleTest
{
    @Test
    public void testPushesPartialAggregationThroughJoin()
    {
        tester().assertThat(new PushPartialAggregationThroughJoin())
                .setSystemProperty(PUSH_PARTIAL_AGGREGATION_THROUGH_JOIN, "true")
                .on(p -> p.aggregation(ab -> ab
                        .source(
                                p.join(
                                        INNER,
                                        p.values(p.symbol("LEFT_EQUI"), p.symbol("LEFT_NON_EQUI"), p.symbol("LEFT_GROUP_BY"), p.symbol("LEFT_AGGR"), p.symbol("LEFT_HASH")),
                                        p.values(p.symbol("RIGHT_EQUI"), p.symbol("RIGHT_NON_EQUI"), p.symbol("RIGHT_GROUP_BY"), p.symbol("RIGHT_HASH")),
                                        ImmutableList.of(new EquiJoinClause(p.symbol("LEFT_EQUI"), p.symbol("RIGHT_EQUI"))),
                                        ImmutableList.of(p.symbol("LEFT_GROUP_BY"), p.symbol("LEFT_AGGR")),
                                        ImmutableList.of(p.symbol("RIGHT_GROUP_BY")),
                                        Optional.of(expression("LEFT_NON_EQUI <= RIGHT_NON_EQUI")),
                                        Optional.of(p.symbol("LEFT_HASH")),
                                        Optional.of(p.symbol("RIGHT_HASH"))))
                        .addAggregation(p.symbol("AVG", DOUBLE), PlanBuilder.aggregation("AVG", ImmutableList.of(new SymbolReference("LEFT_AGGR"))), ImmutableList.of(DOUBLE))
                        .singleGroupingSet(p.symbol("LEFT_GROUP_BY"), p.symbol("RIGHT_GROUP_BY"))
                        .step(PARTIAL)))
                .matches(project(ImmutableMap.of(
                                "LEFT_GROUP_BY", PlanMatchPattern.expression("LEFT_GROUP_BY"),
                                "RIGHT_GROUP_BY", PlanMatchPattern.expression("RIGHT_GROUP_BY"),
                                "AVG", PlanMatchPattern.expression("AVG")),
                        join(INNER, builder -> builder
                                .equiCriteria("LEFT_EQUI", "RIGHT_EQUI")
                                .filter("LEFT_NON_EQUI <= RIGHT_NON_EQUI")
                                .left(
                                        aggregation(
                                                singleGroupingSet("LEFT_EQUI", "LEFT_NON_EQUI", "LEFT_GROUP_BY", "LEFT_HASH"),
                                                ImmutableMap.of(Optional.of("AVG"), aggregationFunction("avg", ImmutableList.of("LEFT_AGGR"))),
                                                Optional.empty(),
                                                PARTIAL,
                                                values("LEFT_EQUI", "LEFT_NON_EQUI", "LEFT_GROUP_BY", "LEFT_AGGR", "LEFT_HASH")))
                                .right(
                                        values("RIGHT_EQUI", "RIGHT_NON_EQUI", "RIGHT_GROUP_BY", "RIGHT_HASH")))));
    }
}
