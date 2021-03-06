package cz.cuni.mff.odcleanstore.fusiontool;

import cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.ResourceDescriptionConflictResolver;
import cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.urimapping.UriMappingIterable;
import cz.cuni.mff.odcleanstore.fusiontool.loaders.InputLoader;
import cz.cuni.mff.odcleanstore.fusiontool.util.EnumFusionCounters;
import cz.cuni.mff.odcleanstore.fusiontool.util.ProfilingTimeCounter;
import cz.cuni.mff.odcleanstore.fusiontool.writers.CloseableRDFWriter;
import cz.cuni.mff.odcleanstore.fusiontool.writers.UriMappingWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openrdf.model.Model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.*;

public class FusionRunnerTest {

    private FusionComponentFactory componentFactory;
    private UriMappingIterable uriMapping;
    private Model metadata;
    private InputLoader inputLoader;
    private ResourceDescriptionConflictResolver conflictResolver;
    private CloseableRDFWriter rdfWriter;
    private FusionExecutor executor;
    private UriMappingWriter canonicalUriWriter;
    private UriMappingWriter sameAsWriter;

    @Before
    public void setUp() throws Exception {
        componentFactory = mock(FusionComponentFactory.class);

        uriMapping = mock(UriMappingIterable.class);
        when(componentFactory.getUriMapping()).thenReturn(uriMapping);

        metadata = mock(Model.class);
        when(componentFactory.getMetadata()).thenAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(10);
                return metadata;
            }
        });

        inputLoader = mock(InputLoader.class);
        when(componentFactory.getInputLoader()).thenReturn(inputLoader);

        conflictResolver = mock(ResourceDescriptionConflictResolver.class);
        when(componentFactory.getConflictResolver(metadata, uriMapping)).thenReturn(conflictResolver);

        rdfWriter = mock(CloseableRDFWriter.class);
        when(componentFactory.getRDFWriter()).thenReturn(rdfWriter);

        executor = mock(FusionExecutor.class);
        when(componentFactory.getExecutor(uriMapping)).thenReturn(executor);

        canonicalUriWriter = mock(UriMappingWriter.class);
        when(componentFactory.getCanonicalUriWriter(uriMapping)).thenReturn(canonicalUriWriter);

        sameAsWriter = mock(UriMappingWriter.class);
        when(componentFactory.getSameAsLinksWriter()).thenReturn(sameAsWriter);
    }

    @Test
    public void runsFusionTool() throws Exception {
        FusionRunner runner = new FusionRunner(componentFactory);
        runner.setProfilingOn(true);
        runner.runFusionTool();

        verify(inputLoader).initialize(uriMapping);
        verify(executor).fuse(conflictResolver, inputLoader, rdfWriter);
        verify(canonicalUriWriter).write(uriMapping);
        verify(sameAsWriter).write(uriMapping);
    }

    @Test
    public void measuresTimeWhenProfilingIsOn() throws Exception {
        FusionRunner runner = new FusionRunner(componentFactory);
        runner.setProfilingOn(true);
        runner.runFusionTool();

        ProfilingTimeCounter<EnumFusionCounters> profiler = runner.getTimeProfiler();
        assertThat(profiler.getCounter(EnumFusionCounters.META_INITIALIZATION), greaterThan(0L));
    }

    @Test
    public void doesNotMeasureTimeWhenProfilingIsOff() throws Exception {
        FusionRunner runner = new FusionRunner(componentFactory);
        runner.setProfilingOn(false);
        runner.runFusionTool();

        ProfilingTimeCounter<EnumFusionCounters> profiler = runner.getTimeProfiler();
        assertThat(profiler.getCounter(EnumFusionCounters.META_INITIALIZATION), is(0L));
    }
}