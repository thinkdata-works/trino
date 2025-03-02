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
import io.trino.sql.planner.iterative.rule.test.BaseRuleTest;
import io.trino.sql.planner.iterative.rule.test.PlanBuilder;
import io.trino.sql.planner.plan.Assignments;
import io.trino.sql.tree.SymbolReference;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.sql.planner.assertions.PlanMatchPattern.aggregation;
import static io.trino.sql.planner.assertions.PlanMatchPattern.aggregationFunction;
import static io.trino.sql.planner.assertions.PlanMatchPattern.assignUniqueId;
import static io.trino.sql.planner.assertions.PlanMatchPattern.expression;
import static io.trino.sql.planner.assertions.PlanMatchPattern.filter;
import static io.trino.sql.planner.assertions.PlanMatchPattern.join;
import static io.trino.sql.planner.assertions.PlanMatchPattern.project;
import static io.trino.sql.planner.assertions.PlanMatchPattern.singleGroupingSet;
import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.planner.plan.AggregationNode.Step.SINGLE;
import static io.trino.sql.planner.plan.JoinType.LEFT;

public class TestTransformCorrelatedGlobalAggregationWithoutProjection
        extends BaseRuleTest
{
    @Test
    public void doesNotFireOnPlanWithoutCorrelatedJoinNode()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.values(p.symbol("a")))
                .doesNotFire();
    }

    @Test
    public void doesNotFireOnCorrelatedWithoutAggregation()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.values(p.symbol("a"))))
                .doesNotFire();
    }

    @Test
    public void doesNotFireOnUncorrelated()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(),
                        p.values(p.symbol("a")),
                        p.values(p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void doesNotFireOnCorrelatedWithNonScalarAggregation()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(ab -> ab
                                .source(p.values(p.symbol("a"), p.symbol("b")))
                                .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                .singleGroupingSet(p.symbol("b")))))
                .doesNotFire();
    }

    @Test
    public void doesNotFireOnMultipleProjections()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.project(
                                Assignments.of(p.symbol("expr_2"), PlanBuilder.expression("expr - 1")),
                                p.project(
                                        Assignments.of(p.symbol("expr"), PlanBuilder.expression("sum + 1")),
                                        p.aggregation(ab -> ab
                                                .source(p.values(p.symbol("a"), p.symbol("b")))
                                                .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                                .globalGrouping())))))
                .doesNotFire();
    }

    @Test
    public void rewritesOnSubqueryWithoutProjection()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(ab -> ab
                                .source(p.values(p.symbol("a"), p.symbol("b")))
                                .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                .globalGrouping())))
                .matches(
                        project(ImmutableMap.of("sum_1", expression("sum_1"), "corr", expression("corr")),
                                aggregation(ImmutableMap.of("sum_1", aggregationFunction("sum", ImmutableList.of("a"))),
                                        join(LEFT, builder -> builder
                                                .left(assignUniqueId("unique",
                                                        values(ImmutableMap.of("corr", 0))))
                                                .right(project(ImmutableMap.of("non_null", expression("true")),
                                                        values(ImmutableMap.of("a", 0, "b", 1))))))));
    }

    @Test
    public void rewritesOnSubqueryWithProjection()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.project(Assignments.of(p.symbol("expr"), PlanBuilder.expression("sum + 1")),
                                p.aggregation(ab -> ab
                                        .source(p.values(p.symbol("a"), p.symbol("b")))
                                        .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                        .globalGrouping()))))
                .doesNotFire();
    }

    @Test
    public void testSubqueryWithCount()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(ab -> ab
                                .source(p.values(p.symbol("a"), p.symbol("b")))
                                .addAggregation(p.symbol("count_rows"), PlanBuilder.aggregation("count", ImmutableList.of()), ImmutableList.of())
                                .addAggregation(p.symbol("count_non_null_values"), PlanBuilder.aggregation("count", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                .globalGrouping())))
                .matches(
                        project(
                                aggregation(ImmutableMap.of(
                                        "count_rows", aggregationFunction("count", ImmutableList.of()),
                                        "count_non_null_values", aggregationFunction("count", ImmutableList.of("a"))),
                                        join(LEFT, builder -> builder
                                                .left(assignUniqueId("unique",
                                                        values(ImmutableMap.of("corr", 0))))
                                                .right(project(ImmutableMap.of("non_null", expression("true")),
                                                        values(ImmutableMap.of("a", 0, "b", 1))))))));
    }

    @Test
    public void rewritesOnSubqueryWithDistinct()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(outerBuilder -> outerBuilder
                                .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                .addAggregation(p.symbol("count"), PlanBuilder.aggregation("count", ImmutableList.of()), ImmutableList.of())
                                .globalGrouping()
                                .source(p.aggregation(innerBuilder -> innerBuilder
                                        .singleGroupingSet(p.symbol("a"))
                                        .source(p.filter(
                                                PlanBuilder.expression("b > corr"),
                                                p.values(p.symbol("a"), p.symbol("b")))))))))
                .matches(
                        project(ImmutableMap.of("corr", expression("corr"), "sum_agg", expression("sum_agg"), "count_agg", expression("count_agg")),
                                aggregation(
                                        singleGroupingSet("corr", "unique"),
                                        ImmutableMap.of(Optional.of("sum_agg"), aggregationFunction("sum", ImmutableList.of("a")), Optional.of("count_agg"), aggregationFunction("count", ImmutableList.of())),
                                        ImmutableList.of(),
                                        ImmutableList.of("non_null"),
                                        Optional.empty(),
                                        SINGLE,
                                        aggregation(
                                                singleGroupingSet("corr", "unique", "non_null", "a"),
                                                ImmutableMap.of(),
                                                Optional.empty(),
                                                SINGLE,
                                                join(LEFT, builder -> builder
                                                        .filter("b > corr")
                                                        .left(
                                                                assignUniqueId(
                                                                        "unique",
                                                                        values("corr")))
                                                        .right(
                                                                project(
                                                                        ImmutableMap.of("non_null", expression("true")),
                                                                        filter(
                                                                                "true",
                                                                                values("a", "b")))))))));
    }

    @Test
    public void rewritesOnSubqueryWithDecorrelatableDistinct()
    {
        // distinct aggregation can be decorrelated in the subquery by PlanNodeDecorrelator
        // because the correlated predicate is equality comparison
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(outerBuilder -> outerBuilder
                                .addAggregation(p.symbol("sum"), PlanBuilder.aggregation("sum", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT))
                                .addAggregation(p.symbol("count"), PlanBuilder.aggregation("count", ImmutableList.of()), ImmutableList.of())
                                .globalGrouping()
                                .source(p.aggregation(innerBuilder -> innerBuilder
                                        .singleGroupingSet(p.symbol("a"))
                                        .source(p.filter(
                                                PlanBuilder.expression("b = corr"),
                                                p.values(p.symbol("a"), p.symbol("b")))))))))
                .matches(
                        project(ImmutableMap.of("corr", expression("corr"), "sum_agg", expression("sum_agg"), "count_agg", expression("count_agg")),
                                aggregation(
                                        singleGroupingSet("corr", "unique"),
                                        ImmutableMap.of(Optional.of("sum_agg"), aggregationFunction("sum", ImmutableList.of("a")), Optional.of("count_agg"), aggregationFunction("count", ImmutableList.of())),
                                        ImmutableList.of(),
                                        ImmutableList.of("non_null"),
                                        Optional.empty(),
                                        SINGLE,
                                        join(LEFT, builder -> builder
                                                .filter("b = corr")
                                                .left(
                                                        assignUniqueId(
                                                                "unique",
                                                                values("corr")))
                                                .right(
                                                        project(
                                                                ImmutableMap.of("non_null", expression("true")),
                                                                aggregation(
                                                                        singleGroupingSet("a", "b"),
                                                                        ImmutableMap.of(),
                                                                        Optional.empty(),
                                                                        SINGLE,
                                                                        filter(
                                                                                "true",
                                                                                values("a", "b")))))))));
    }

    @Test
    public void testWithPreexistingMask()
    {
        tester().assertThat(new TransformCorrelatedGlobalAggregationWithoutProjection(tester().getPlannerContext()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(ab -> ab
                                .source(p.values(p.symbol("a"), p.symbol("mask")))
                                .addAggregation(p.symbol("count_non_null_values"), PlanBuilder.aggregation("count", ImmutableList.of(new SymbolReference("a"))), ImmutableList.of(BIGINT), p.symbol("mask"))
                                .globalGrouping())))
                .matches(
                        project(
                                aggregation(
                                        singleGroupingSet("corr", "unique"),
                                        ImmutableMap.of(Optional.of("count_non_null_values"), aggregationFunction("count", ImmutableList.of("a"))),
                                        ImmutableList.of(),
                                        ImmutableList.of("new_mask"),
                                        Optional.empty(),
                                        SINGLE,
                                        project(
                                                ImmutableMap.of("new_mask", expression("mask AND non_null")),
                                                join(LEFT, builder -> builder
                                                        .left(assignUniqueId("unique",
                                                                values(ImmutableMap.of("corr", 0))))
                                                        .right(project(ImmutableMap.of("non_null", expression("true")),
                                                                values(ImmutableMap.of("a", 0, "mask", 1)))))))));
    }
}
