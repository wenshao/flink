---
title: "Joining"
weight: 3 
type: docs
aliases:
  - /dev/stream/operators/joining.html
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Joining

## Window Join

A window join joins the elements of two streams that share a common key and lie in the same window. These windows can be defined by using a [window assigner]({{< ref "docs/dev/datastream/operators/windows" >}}#window-assigners) and are evaluated on elements from both of the streams.

The elements from both sides are then passed to a user-defined `JoinFunction` or `FlatJoinFunction` where the user can emit results that meet the join criteria.

The general usage can be summarized as follows:

```java
stream.join(otherStream)
    .where(<KeySelector>)
    .equalTo(<KeySelector>)
    .window(<WindowAssigner>)
    .apply(<JoinFunction>);
```

Some notes on semantics:
- The creation of pairwise combinations of elements of the two streams behaves like an inner-join, meaning elements from one stream will not be emitted if they don't have a corresponding element from the other stream to be joined with.
- Those elements that do get joined will have as their timestamp the largest timestamp that still lies in the respective window. For example a window with `[5, 10)` as its boundaries would result in the joined elements having 9 as their timestamp.

In the following section we are going to give an overview over how different kinds of window joins behave using some exemplary scenarios.

### Tumbling Window Join

When performing a tumbling window join, all elements with a common key and a common tumbling window are joined as pairwise combinations and passed on to a `JoinFunction` or `FlatJoinFunction`. Because this behaves like an inner join, elements of one stream that do not have elements from another stream in their tumbling window are not emitted!

{{< img src="/fig/tumbling-window-join.svg" width="80%" >}}

As illustrated in the figure, we define a tumbling window with the size of 2 milliseconds, which results in windows of the form `[0,1], [2,3], ...`. The image shows the pairwise combinations of all elements in each window which will be passed on to the `JoinFunction`. Note that in the tumbling window `[6,7]` nothing is emitted because no elements exist in the green stream to be joined with the orange elements ⑥ and ⑦.

```java
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import java.time.Duration;
 
...

DataStream<Integer> orangeStream = ...;
DataStream<Integer> greenStream = ...;

orangeStream.join(greenStream)
    .where(<KeySelector>)
    .equalTo(<KeySelector>)
    .window(TumblingEventTimeWindows.of(Duration.ofMillis(2)))
    .apply (new JoinFunction<Integer, Integer, String> (){
        @Override
        public String join(Integer first, Integer second) {
            return first + "," + second;
        }
    });
```

### Sliding Window Join

When performing a sliding window join, all elements with a common key and common sliding window are joined as pairwise combinations and passed on to the `JoinFunction` or `FlatJoinFunction`. Elements of one stream that do not have elements from the other stream in the current sliding window are not emitted! Note that some elements might be joined in one sliding window but not in another!

{{< img src="/fig/sliding-window-join.svg" width="80%" >}}

In this example we are using sliding windows with a size of two milliseconds and slide them by one millisecond, resulting in the sliding windows `[-1, 0],[0,1],[1,2],[2,3], …`.<!-- TODO: Can -1 actually exist?--> The joined elements below the x-axis are the ones that are passed to the `JoinFunction` for each sliding window. Here you can also see how for example the orange ② is joined with the green ③ in the window `[2,3]`, but is not joined with anything in the window `[1,2]`.

```java
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import java.time.Duration;

...

DataStream<Integer> orangeStream = ...;
DataStream<Integer> greenStream = ...;

orangeStream.join(greenStream)
    .where(<KeySelector>)
    .equalTo(<KeySelector>)
    .window(SlidingEventTimeWindows.of(Duration.ofMillis(2) /* size */, Duration.ofMillis(1) /* slide */))
    .apply (new JoinFunction<Integer, Integer, String> (){
        @Override
        public String join(Integer first, Integer second) {
            return first + "," + second;
        }
    });
```

### Session Window Join

When performing a session window join, all elements with the same key that when _"combined"_ fulfill the session criteria are joined in pairwise combinations and passed on to the `JoinFunction` or `FlatJoinFunction`. Again this performs an inner join, so if there is a session window that only contains elements from one stream, no output will be emitted!

{{< img src="/fig/session-window-join.svg" width="80%" >}} 

Here we define a session window join where each session is divided by a gap of at least 1ms. There are three sessions, and in the first two sessions the joined elements from both streams are passed to the `JoinFunction`. In the third session there are no elements in the green stream, so ⑧ and ⑨ are not joined!

```java
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import java.time.Duration;
 
...

DataStream<Integer> orangeStream = ...;
DataStream<Integer> greenStream = ...;

orangeStream.join(greenStream)
    .where(<KeySelector>)
    .equalTo(<KeySelector>)
    .window(EventTimeSessionWindows.withGap(Duration.ofMillis(1)))
    .apply (new JoinFunction<Integer, Integer, String> (){
        @Override
        public String join(Integer first, Integer second) {
            return first + "," + second;
        }
    });
```

## Interval Join

The interval join joins elements of two streams (we'll call them A & B for now) with a common key and where elements of stream B have timestamps that lie in a relative time interval to timestamps of elements in stream A.

This can also be expressed more formally as
`b.timestamp ∈ [a.timestamp + lowerBound; a.timestamp + upperBound]` or 
`a.timestamp + lowerBound <= b.timestamp <= a.timestamp + upperBound`

where a and b are elements of A and B that share a common key. Both the lower and upper bound can be either negative or positive as long as the lower bound is always smaller or equal to the upper bound. The interval join currently only performs inner joins.

When a pair of elements are passed to the `ProcessJoinFunction`, they will be assigned with the larger timestamp (which can be accessed via the `ProcessJoinFunction.Context`) of the two elements.

{{< hint info >}}
The interval join currently only supports event time.
{{< /hint >}}

{{< img src="/fig/interval-join.svg" width="80%" >}} 

In the example above, we join two streams 'orange' and 'green' with a lower bound of -2 milliseconds and an upper bound of +1 millisecond. Be default, these boundaries are inclusive, but `.lowerBoundExclusive()` and `.upperBoundExclusive()` can be applied to change the behaviour.

Using the more formal notation again this will translate to 

`orangeElem.ts + lowerBound <= greenElem.ts <= orangeElem.ts + upperBound`

as indicated by the triangles.

```java
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import java.time.Duration;

...

DataStream<Integer> orangeStream = ...;
DataStream<Integer> greenStream = ...;

orangeStream
    .keyBy(<KeySelector>)
    .intervalJoin(greenStream.keyBy(<KeySelector>))
    .between(Duration.ofMillis(-2), Duration.ofMillis(1))
    .process (new ProcessJoinFunction<Integer, Integer, String>(){

        @Override
        public void processElement(Integer left, Integer right, Context ctx, Collector<String> out) {
            out.collect(left + "," + right);
        }
    });
```

{{< top >}}
