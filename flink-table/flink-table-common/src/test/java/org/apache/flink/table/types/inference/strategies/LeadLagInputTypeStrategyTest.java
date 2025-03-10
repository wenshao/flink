/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.types.inference.strategies;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.inference.InputTypeStrategiesTestBase;

import java.util.stream.Stream;

/** Tests for {@link LeadLagInputTypeStrategy}. */
class LeadLagInputTypeStrategyTest extends InputTypeStrategiesTestBase {

    @Override
    protected Stream<TestSpec> testData() {
        return Stream.of(
                TestSpec.forStrategy(SpecificInputTypeStrategies.LEAD_LAG)
                        .calledWithArgumentTypes(
                                DataTypes.BIGINT(), DataTypes.SMALLINT(), DataTypes.INT())
                        .calledWithLiteralAt(1)
                        .expectSignature(
                                "f(<ANY>)\n"
                                        + "f(<ANY>, <NUMERIC>)\n"
                                        + "f(<COMMON>, <NUMERIC>, <COMMON>)")
                        .expectArgumentTypes(
                                DataTypes.BIGINT(), DataTypes.SMALLINT(), DataTypes.BIGINT()),
                TestSpec.forStrategy(SpecificInputTypeStrategies.LEAD_LAG)
                        .calledWithArgumentTypes(
                                DataTypes.BIGINT(), DataTypes.SMALLINT(), DataTypes.STRING())
                        .expectErrorMessage(
                                "The default value must have a"
                                        + " common type with the given expression. ARG0: BIGINT, "
                                        + "default: STRING"),
                TestSpec.forStrategy(SpecificInputTypeStrategies.LEAD_LAG)
                        .calledWithArgumentTypes(DataTypes.BIGINT(), DataTypes.STRING())
                        .expectErrorMessage(
                                "Unsupported argument type. "
                                        + "Expected type of family 'NUMERIC' but actual type was 'STRING'."));
    }
}
