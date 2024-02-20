package graphql.execution.instrumentation;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.PublicApi;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static graphql.Assert.assertNotNull;

/**
 * This allows you to chain together a number of {@link graphql.execution.instrumentation.Instrumentation} implementations
 * and run them in sequence.  The list order of instrumentation objects is always guaranteed to be followed and
 * the {@link graphql.execution.instrumentation.InstrumentationState} objects they create will be passed back to the originating
 * implementation.
 *
 * @see graphql.execution.instrumentation.Instrumentation
 */
@SuppressWarnings("deprecation")
@PublicApi
public class ChainedInstrumentation implements Instrumentation {

    // This class is inspired from https://github.com/leangen/graphql-spqr/blob/master/src/main/java/io/leangen/graphql/GraphQLRuntime.java#L80

    protected final ImmutableList<Instrumentation> instrumentations;

    public ChainedInstrumentation(List<Instrumentation> instrumentations) {
        this.instrumentations = ImmutableList.copyOf(assertNotNull(instrumentations));
    }

    public ChainedInstrumentation(Instrumentation... instrumentations) {
        this(Arrays.asList(instrumentations));
    }

    /**
     * @return the list of instrumentations in play
     */
    public List<Instrumentation> getInstrumentations() {
        return instrumentations;
    }

    private <T> InstrumentationContext<T> chainedCtx(InstrumentationState state, BiFunction<Instrumentation, InstrumentationState, InstrumentationContext<T>> mapper) {
        // if we have zero or 1 instrumentations (and 1 is the most common), then we can avoid an object allocation
        // of the ChainedInstrumentationContext since it won't be needed
        if (instrumentations.isEmpty()) {
            return SimpleInstrumentationContext.noOp();
        }
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedInstrumentationContext<>(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    private <T> T chainedInstrument(InstrumentationState state, T input, ChainedInstrumentationFunction<Instrumentation, InstrumentationState, T, T> mapper) {
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = chainedInstrumentationState.getState(i);
            input = mapper.apply(instrumentation, specificState, input);
        }
        return input;
    }

    protected <T> ImmutableList<T> chainedMapAndDropNulls(InstrumentationState state, BiFunction<Instrumentation, InstrumentationState, T> mapper) {
        ImmutableList.Builder<T> result = ImmutableList.builderWithExpectedSize(instrumentations.size());
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = ((ChainedInstrumentationState) state).getState(i);
            T value = mapper.apply(instrumentation, specificState);
            if (value != null) {
                result.add(value);
            }
        }
        return result.build();
    }

    protected <T> void chainedConsume(InstrumentationState state, BiConsumer<Instrumentation, InstrumentationState> stateConsumer) {
        for (int i = 0; i < instrumentations.size(); i++) {
            Instrumentation instrumentation = instrumentations.get(i);
            InstrumentationState specificState = ((ChainedInstrumentationState) state).getState(i);
            stateConsumer.accept(instrumentation, specificState);
        }
    }

    @Override
    public InstrumentationState createState() {
        return Assert.assertShouldNeverHappen("createStateAsync should only ever be used");
    }

    @Override
    public @Nullable InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return Assert.assertShouldNeverHappen("createStateAsync should only ever be used");
    }

    @Override
    public @NotNull CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
        return ChainedInstrumentationState.combineAll(instrumentations, parameters);
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        // these assert methods have been left in so that we truly never call these methods, either in production nor in tests
        // later when the deprecated methods are removed, this will disappear.
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecution" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginExecution(parameters, specificState));
    }

    @Override
    @NotNull
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginParse" + " was called");
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginParse(parameters, specificState));
    }

    @Override
    @NotNull
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginValidation" + " was called");
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginValidation(parameters, specificState));
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecuteOperation" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginExecuteOperation(parameters, specificState));
    }

    @Override
    @NotNull
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginExecutionStrategy" + " was called");
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        if (instrumentations.isEmpty()) {
            return ExecutionStrategyInstrumentationContext.NOOP;
        }
        BiFunction<Instrumentation, InstrumentationState, ExecutionStrategyInstrumentationContext> mapper = (instrumentation, specificState) -> instrumentation.beginExecutionStrategy(parameters, specificState);
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedExecutionStrategyInstrumentationContext(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    @Override
    public @Nullable ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        if (instrumentations.isEmpty()) {
            return ExecuteObjectInstrumentationContext.NOOP;
        }
        BiFunction<Instrumentation, InstrumentationState, ExecuteObjectInstrumentationContext> mapper = (instrumentation, specificState) -> instrumentation.beginExecuteObject(parameters, specificState);
        ChainedInstrumentationState chainedInstrumentationState = (ChainedInstrumentationState) state;
         if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0), chainedInstrumentationState.getState(0));
        }
        return new ChainedExecuteObjectInstrumentationContext(chainedMapAndDropNulls(chainedInstrumentationState, mapper));
    }

    @ExperimentalApi
    @Override
    public InstrumentationContext<Object> beginDeferredField(InstrumentationState instrumentationState) {
        return new ChainedDeferredExecutionStrategyInstrumentationContext(chainedMapAndDropNulls(instrumentationState, Instrumentation::beginDeferredField));
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginSubscribedFieldEvent" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginSubscribedFieldEvent(parameters, specificState));
    }

    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginField" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginField" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldExecution(parameters, specificState));
    }

    @Override
    @NotNull
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldFetch" + " was called");
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldFetch(parameters, specificState));
    }


    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldComplete" + " was called");
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldCompletion(parameters, specificState));
    }


    @Override
    @NotNull
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "beginFieldListComplete" + " was called");
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldListComplete(parameters, specificState));
    }

    @Override
    public @Nullable InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        return chainedCtx(state, (instrumentation, specificState) -> instrumentation.beginFieldListCompletion(parameters, specificState));
    }

    @Override
    @NotNull
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionInput" + " was called");
    }

    @NotNull
    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, executionInput, (instrumentation, specificState, accumulator) -> instrumentation.instrumentExecutionInput(accumulator, parameters, specificState));
    }

    @Override
    @NotNull
    public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentDocumentAndVariables" + " was called");
    }

    @NotNull
    @Override
    public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, documentAndVariables, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentDocumentAndVariables(accumulator, parameters, specificState));
    }

    @Override
    @NotNull
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentSchema" + " was called");
    }

    @NotNull
    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, schema, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentSchema(accumulator, parameters, specificState));
    }

    @Override
    @NotNull
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionContext" + " was called");
    }

    @NotNull
    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, executionContext, (instrumentation, specificState, accumulator) ->
                instrumentation.instrumentExecutionContext(accumulator, parameters, specificState));
    }

    @Override
    @NotNull
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentDataFetcher" + " was called");
    }

    @NotNull
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return chainedInstrument(state, dataFetcher, (Instrumentation instrumentation, InstrumentationState specificState, DataFetcher<?> accumulator) ->
                instrumentation.instrumentDataFetcher(accumulator, parameters, specificState));
    }

    @Override
    @NotNull
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        return Assert.assertShouldNeverHappen("The deprecated " + "instrumentExecutionResult" + " was called");
    }

    @NotNull
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        ImmutableList<Map.Entry<Instrumentation, InstrumentationState>> entries = chainedMapAndDropNulls(state, AbstractMap.SimpleEntry::new);
        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.eachSequentially(entries, (entry, prevResults) -> {
            Instrumentation instrumentation = entry.getKey();
            InstrumentationState specificState = entry.getValue();
            ExecutionResult lastResult = prevResults.size() > 0 ? prevResults.get(prevResults.size() - 1) : executionResult;
            return instrumentation.instrumentExecutionResult(lastResult, parameters, specificState);
        });
        return resultsFuture.thenApply((results) -> results.isEmpty() ? executionResult : results.get(results.size() - 1));
    }

    static class ChainedInstrumentationState implements InstrumentationState {
        private final List<InstrumentationState> instrumentationStates;

        private ChainedInstrumentationState(List<InstrumentationState> instrumentationStates) {
            this.instrumentationStates = instrumentationStates;
        }

        private InstrumentationState getState(int index) {
            return instrumentationStates.get(index);
        }

        private static CompletableFuture<InstrumentationState> combineAll(List<Instrumentation> instrumentations, InstrumentationCreateStateParameters parameters) {
            Async.CombinedBuilder<InstrumentationState> builder = Async.ofExpectedSize(instrumentations.size());
            for (Instrumentation instrumentation : instrumentations) {
                // state can be null including the CF so handle that
                CompletableFuture<InstrumentationState> stateCF = Async.orNullCompletedFuture(instrumentation.createStateAsync(parameters));
                builder.add(stateCF);
            }
            return builder.await().thenApply(ChainedInstrumentationState::new);
        }
    }

    private static class ChainedInstrumentationContext<T> implements InstrumentationContext<T> {

        private final ImmutableList<InstrumentationContext<T>> contexts;

        ChainedInstrumentationContext(ImmutableList<InstrumentationContext<T>> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched(CompletableFuture<T> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(T result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    private static class ChainedExecutionStrategyInstrumentationContext implements ExecutionStrategyInstrumentationContext {

        private final ImmutableList<ExecutionStrategyInstrumentationContext> contexts;

        ChainedExecutionStrategyInstrumentationContext(ImmutableList<ExecutionStrategyInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched(CompletableFuture<ExecutionResult> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(ExecutionResult result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }

        @Override
        public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
            contexts.forEach(context -> context.onFieldValuesInfo(fieldValueInfoList));
        }

        @Override
        public void onFieldValuesException() {
            contexts.forEach(ExecutionStrategyInstrumentationContext::onFieldValuesException);
        }
    }

    private static class ChainedExecuteObjectInstrumentationContext implements ExecuteObjectInstrumentationContext {

        private final ImmutableList<ExecuteObjectInstrumentationContext> contexts;

        ChainedExecuteObjectInstrumentationContext(ImmutableList<ExecuteObjectInstrumentationContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched(CompletableFuture<Map<String, Object>> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(Map<String, Object> result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }

        @Override
        public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
            contexts.forEach(context -> context.onFieldValuesInfo(fieldValueInfoList));
        }

        @Override
        public void onFieldValuesException() {
            contexts.forEach(ExecuteObjectInstrumentationContext::onFieldValuesException);
        }
    }

    private static class ChainedDeferredExecutionStrategyInstrumentationContext implements InstrumentationContext<Object> {

        private final List<InstrumentationContext<Object>> contexts;

        ChainedDeferredExecutionStrategyInstrumentationContext(List<InstrumentationContext<Object>> contexts) {
            this.contexts = Collections.unmodifiableList(contexts);
        }

        @Override
        public void onDispatched(CompletableFuture<Object> result) {
            contexts.forEach(context -> context.onDispatched(result));
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
            contexts.forEach(context -> context.onCompleted(result, t));
        }
    }

    @FunctionalInterface
    private interface ChainedInstrumentationFunction<I, S, A, R> {

        R apply(I t, S u, A v);

    }


}

