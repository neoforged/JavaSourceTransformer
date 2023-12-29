package net.neoforged.jst.cli;

import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

class OrderedParallelWorkQueue implements AutoCloseable {
    private final Deque<Future<List<WorkResult>>> pending;
    private final FileSink sink;
    private final int maxQueueDepth;

    public OrderedParallelWorkQueue(FileSink sink, int maxQueueDepth) {
        this.sink = sink;
        this.maxQueueDepth = maxQueueDepth;
        if (maxQueueDepth < 0) {
            throw new IllegalArgumentException("Max queue depth must not be negative");
        }
        this.pending = new ArrayDeque<>(maxQueueDepth);
    }

    public void submit(Consumer<FileSink> producer) throws IOException {
        if (pending.isEmpty()) {
            // Can write directly if nothing else is pending
            producer.accept(sink);
        } else {
            // Needs to be queued behind currently queued async work
            submitAsync(producer);
        }
    }

    public void submitAsync(Consumer<FileSink> producer) {
        try {
            if (maxQueueDepth <= 0) {
                // Forced into synchronous mode
                submit(producer);
                return;
            }
            drainTo(maxQueueDepth - 1);
            pending.add(CompletableFuture.supplyAsync(() -> {
                try (var parallelSink = new ParallelSink()) {
                    producer.accept(parallelSink);
                    return parallelSink.workResults;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ParallelSink implements FileSink {
        private final List<WorkResult> workResults = new ArrayList<>();

        @Override
        public boolean isOrdered() {
            return false;
        }

        @Override
        public void put(FileEntry entry, byte[] content) {
            workResults.add(new WorkResult(entry, content));
        }
    }

    private void drainTo(int drainTo) throws InterruptedException, IOException {
        while (pending.size() > drainTo) {
            List<WorkResult> workResults;
            try {
                workResults = pending.removeFirst().get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException ioe) {
                    throw ioe;
                }
                throw new RuntimeException(e.getCause());
            }
            for (var workResult : workResults) {
                sink.put(workResult.entry, workResult.content);
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            drainTo(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sink.close();
        }
    }

    private record WorkResult(FileEntry entry, byte[] content) {
    }
}
