package io.opentracing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ThreadLocalActiveSpanTest {
    private ThreadLocalActiveSpanSource source;

    @Before
    public void setUp() throws Exception {
        source = new ThreadLocalActiveSpanSource();
    }

    @After
    public void tearDown() throws Exception {
        source.clearThreadLocal();
    }

    @Test
    public void continuation() throws Exception {
        Span span = mock(Span.class);

        // Quasi try-with-resources (this is 1.6).
        ActiveSpan activeSpan = source.adopt(span);
        ActiveSpan.Continuation continued = null;
        try {
            assertNotNull(activeSpan);
            continued = activeSpan.defer();
        } finally {
            activeSpan.close();
        }

        // Make sure the Span was not finished since there was a defer().
        verify(span, never()).finish();

        // Activate the continuation.
        try {
            activeSpan = continued.activate();
        } finally {
            activeSpan.close();
        }

        // Now the Span should be finished.
        verify(span, times(1)).finish();

        // And now it's no longer active.
        ActiveSpan missingSpan = source.activeSpan();
        assertNull(missingSpan);
    }

    @Test
    public void implicitSpanStack() throws Exception {
        Span backgroundSpan = mock(Span.class);
        Span foregroundSpan = mock(Span.class);

        // Quasi try-with-resources (this is 1.6).
        ActiveSpan backgroundActive = source.adopt(backgroundSpan);
        try {
            assertNotNull(backgroundActive);

            // Activate a new ActiveSpan on top of the background one.
            ActiveSpan foregroundActive = source.adopt(foregroundSpan);
            try {
                ActiveSpan shouldBeForeground = source.activeSpan();
                assertEquals(foregroundActive, shouldBeForeground);
            } finally {
                foregroundActive.close();
            }

            // And now the backgroundActive should be reinstated.
            ActiveSpan shouldBeBackground = source.activeSpan();
            assertEquals(backgroundActive, shouldBeBackground);
        } finally {
            backgroundActive.close();
        }

        // The background and foreground Spans should be finished.
        verify(backgroundSpan, times(1)).finish();
        verify(foregroundSpan, times(1)).finish();

        // And now nothing is active.
        ActiveSpan missingSpan = source.activeSpan();
        assertNull(missingSpan);
    }

}