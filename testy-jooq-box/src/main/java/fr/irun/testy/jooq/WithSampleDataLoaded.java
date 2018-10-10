package fr.irun.testy.jooq;

import fr.irun.testy.jooq.model.RelationalDataSet;
import org.jooq.DSLContext;
import org.jooq.UpdatableRecord;
import org.junit.jupiter.api.extension.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

public class WithSampleDataLoaded implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_TRACKER = "sampleTracker";

    private final WithDslContext wDsl;

    private final List<? extends UpdatableRecord<?>> records;

    private WithSampleDataLoaded(Extension wDsl, List<? extends UpdatableRecord<?>> records) {
        this.wDsl = (WithDslContext) wDsl;
        this.records = records;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        getStore(context).put(P_TRACKER, new Tracker());
        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.attach(records);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Tracker tracker = getStore(context).get(P_TRACKER, Tracker.class);
        if (tracker == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        }

        if (tracker.skipNext.getAndSet(false))
            return;

        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.attach(records);
        dslContext.batchDelete(records).execute();
        dslContext.batchStore(records).execute();
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName()));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return Tracker.class.equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DSLContext.class.equals(type))
            return getStore(extensionContext).get(P_TRACKER);

        throw new NoSuchElementException(P_TRACKER);
    }

    public static SampleLoaderBuilder builder(Extension ex) {
        return new SampleLoaderBuilder(ex);
    }

    public static class SampleLoaderBuilder {
        private final Extension dslExtension;
        private final List<? extends UpdatableRecord<?>> records = new ArrayList<>();

        SampleLoaderBuilder(Extension dslExtension) {
            this.dslExtension = dslExtension;
        }

        public SampleLoaderBuilder addDataset(RelationalDataSet dataset) {
            records.addAll(dataset.records());
            return this;
        }

        public WithSampleDataLoaded build() {
            return new WithSampleDataLoaded(dslExtension, records);
        }
    }

    public static class Tracker {
        private final AtomicBoolean skipNext = new AtomicBoolean(false);

        public void skipNextSampleLoad() {
            skipNext.set(true);
        }
    }
}
